# Sony USB Tether Protocol Flow

## Scope and tested configuration

This document describes the USB/PTP flow currently implemented by HakoCapture and
verified against a Sony ILCE-7RM2 (A7R II) in **PC Remote** mode. The feature is
for still-image tethering and preview. It does not start or consume Sony live view.

The verified camera configuration used RAW-only capture. The implementation must
not assume that every exposure produces a RAW+JPEG pair. It reads the object format
and size reported by the camera for every object.

The main implementation is in:

- `SonyTetherManager.kt`: USB lifecycle, transactions, event loop, and downloads.
- `SonyInitialization.kt`: ordered Sony session handshake.
- `PtpPacket.kt`: little-endian PTP container encoding and decoding.
- `SonyTetherEvent.kt`: event-to-action mapping.
- `SonyAllProperties.kt`: `D215` extraction from Sony's aggregate property blob.
- `LargeObjectReadPolicy.kt`: USB read negotiation and retry policy.
- `TetherImageStore.kt`: Android MediaStore persistence.

## USB topology

The phone is the USB host and the camera is the USB device. HakoCapture selects a
PTP-capable interface with:

- Bulk OUT: PTP command containers sent to the camera.
- Bulk IN: PTP data and response containers read from the camera.
- Interrupt IN: asynchronous PTP/Sony event containers.

The tested A7R II exposes interface class `6`, subclass `1`, protocol `1`, with one
Bulk IN endpoint, one Bulk OUT endpoint, and one Interrupt IN endpoint.

All USB I/O is serialized on `SonyTetherWorker`. This is important: PTP transaction
responses must not be consumed by a competing reader.

## PTP container format

Every PTP container starts with a 12-byte little-endian header:

| Offset | Width | Field |
|---:|---:|---|
| 0 | 4 | Total container length |
| 4 | 2 | Container type |
| 6 | 2 | Operation, response, or event code |
| 8 | 4 | Transaction ID |
| 12 | variable | Parameters or data payload |

Container types used by the app:

| Value | Meaning |
|---:|---|
| `1` | Command |
| `2` | Data |
| `3` | Response |
| `4` | Event |

Command parameters and event parameters are 32-bit little-endian values. Sony's
special object handle `0xFFFFC001` appears as `-16383` when stored in a Kotlin
signed `Int`; it must be compared by bit pattern, not by signed magnitude.

## Connection and initialization

After Android grants USB permission, the app opens the device, claims the PTP
interface, initializes the transaction counter, and executes this exact sequence:

| Step | Opcode | Parameters | Expected phase | Purpose |
|---:|---:|---|---|---|
| 1 | `0x1001` GetDeviceInfo | none | Data + Response | Confirm PTP communication and read device identity/capabilities. |
| 2 | `0x1002` OpenSession | `1` | Response | Open the PTP session. `0x2001` and Sony's already-open `0x201E` are accepted. |
| 3 | `0x9201` Sony SDIO Connect | `1, 0, 0` | Data + Response | Begin Sony PC Remote negotiation. |
| 4 | `0x9201` Sony SDIO Connect | `2, 0, 0` | Data + Response | Continue Sony negotiation. |
| 5 | `0x9202` GetExtDeviceInfo | `0xC8` | Data + Response | Retrieve Sony property/control capability lists. |
| 6 | `0x9201` Sony SDIO Connect | `3, 0, 0` | Data + Response | Enter the ready PC Remote state. |

The app does not report the camera as connected until every step succeeds. Each
transaction sends one command and then reads containers until it receives the
matching response. For commands with a data phase, the usual order is:

```text
Host   -> COMMAND(opcode, transactionId, parameters)
Camera -> DATA(opcode, transactionId, payload)
Camera -> RESPONSE(0x2001, transactionId)
```

Stale packets from an earlier failed session are ignored when their transaction ID
does not match. If the camera firmware is already wedged, failures may appear at
different handshake steps because stale replies can survive a reconnect. A camera
that does not answer `0x1001` is not in a usable PTP state; on the tested A7R II a
battery removal may be required.

## Event-driven capture flow

The interrupt endpoint is polled with a bounded timeout. Each valid event is logged
with its raw bytes, code, transaction ID, and parameters.

### Events that start a download

| Event | Meaning | Action |
|---:|---|---|
| `0x4002` | Standard ObjectAdded | Download the nonzero handle in parameter 1. |
| `0xC001` | Sony ObjectAdded variant | Download the nonzero handle in parameter 1. |
| `0xC201` | Sony ObjectAdded | Download the nonzero handle in parameter 1. The A7R II normally reports `0xFFFFC001`. |

The A7R II uses `0xFFFFC001` as a reusable in-memory object handle. Reuse is
expected, so this handle must not be permanently deduplicated after the first shot.

### Events that must not start a download

| Event | Observed meaning | Action |
|---:|---|---|
| `0xC202` | Current object information changed/invalidated | Ignore. It does not prove that a second RAW or JPEG exists. |
| `0xC203` | Device property changed | Ignore as an object event. Parameter 1 is a property code such as `0xD21D`, `0x5004`, or `0x5005`, not an object handle. |

Treating `0xC202` or `0xC203` parameters as object handles caused invalid reads and
camera resets during development.

## Missing-event and burst fallback

The tested camera can stop emitting `0xC201` after several captures even though
more images remain queued in its PC Remote RAM. This was reproducible after six
successful RAW transfers. Burst capture can also queue multiple images behind the
same reusable object handle.

HakoCapture therefore polls Sony opcode `0x9209` every three seconds:

| Opcode | Name | Parameters | Result |
|---:|---|---|---|
| `0x9209` | GetAllExtDevicePropInfo | none | A data blob containing concatenated Sony property descriptors. |

The app scans the blob for property `0xD215` with PTP type `0x0004` (`UINT16`). A
Sony property descriptor begins with:

```text
UInt16 propertyCode
UInt16 dataType
UInt8  getSet
UInt8  isEnabled
typed  defaultValue
typed  currentValue
...
```

For `D215`/`UINT16`, the current value is at descriptor offset 8. Its observed
states are:

| Value | Interpretation |
|---:|---|
| Below `0x8000` | No object is safe to read yet. Do not touch `0xFFFFC001`. |
| `0x8001` | One in-memory object is ready. |
| `0x8002` and above | Multiple/queued objects are ready or pending in Sony RAM. |

When `D215 >= 0x8000` and no download just completed within the four-second settle
window, the app downloads `0xFFFFC001`. Subsequent polls drain additional queued
objects. This is why burst now works even when individual `0xC201` events are lost.

The settle window prevents an immediately stale `D215` value from causing the same
just-consumed object to be downloaded twice.

### Important unsafe alternatives

Do not replace `0x9209` with either of these on this camera/firmware:

- `0x9204` GetDevicePropertyValue: stalled the Bulk OUT endpoint.
- `0x9203` GetDevicePropDesc for `D215`: also stalled Bulk OUT during capture,
  causing all later commands—including `GetObjectInfo`—to return `-1`.

Once one of these calls poisoned the transport, the camera sometimes stopped
responding to its power switch and required battery removal. `0x9209` is the
verified aggregate-property operation for this A7R II flow.

## Object download flow

For every event or fallback handle, the app performs:

```text
0x1008 GetObjectInfo(handle)
    -> parse StorageID, ObjectFormat, ProtectionStatus, ObjectCompressedSize

0x1009 GetObject(handle)
    -> read one PTP DATA container header
    -> stream exactly containerLength - 12 payload bytes
    -> read final PTP RESPONSE and require 0x2001

save bytes to Android MediaStore
decode/extract a preview bitmap
publish TetheredImage to the UI
```

The reported object format determines the filename extension and decoding path.
Known formats include:

| Format | Code |
|---|---:|
| EXIF JPEG | `0x3801` |
| Sony RAW | `0xB101` |
| ARW/undefined RAW variant handled by the app | `0x3005` |
| TIFF | `0x3802` |

RAW-only capture is valid. There is no assumption that a JPEG companion follows.

## Android USB read negotiation

The tested RAW files are approximately 85.6 MB. Keeping the camera inside a long
`GetObject` transaction increases the chance that another exposure overlaps the
transfer and stresses old Sony firmware.

Modern Android initially requests a 4 MiB Bulk IN read, then retries the same
pending data phase with smaller lengths when the phone's USB host driver rejects
the request:

```text
4 MiB -> 1 MiB -> 256 KiB -> 64 KiB -> 16 KiB
```

The tested phone negotiated 64 KiB. A size rejection happens before bytes are
consumed, so stepping down does not restart or abandon the PTP transaction. Once a
size works, it is used for the rest of that object. Up to three consecutive
transient read failures are retried.

The reader considers a transfer complete only after it has received at least the
payload length declared by the PTP data container. It then requires the final PTP
response. Partial objects are never displayed as successful.

If an object transaction fails, HakoCapture:

1. Clears UI download progress.
2. Releases and closes the USB session.
3. Reports that the camera must be reconnected.
4. Does not send more commands on the misaligned session.

Continuing after a partial `GetObject` can make response boundaries ambiguous and
was associated with firmware lockups on this generation.

## Persistence and memory behavior

The complete object is saved through Android MediaStore under
`Pictures/HakoCapture`. Filenames include the unsigned handle and an app-side
sequence so repeated use of `0xFFFFC001` does not overwrite earlier captures.

After saving, the UI retains metadata and the decoded preview but not the original
85 MB RAW byte array. This prevents repeated or burst capture from exhausting the
phone heap.

Saving to the phone does not imply saving to the camera's SD card. On Sony cameras
that expose a PC Remote save-destination menu, camera-side storage is controlled by
that camera setting. The app does not force a RAW+JPEG mode or assume PC+Camera.

## Operational state summary

```text
USB attached
  -> permission granted
  -> endpoints claimed
  -> standard PTP + Sony SDIO handshake
  -> READY
       |-- C201/C001/4002 ------------------------|
       |                                          v
       |-- periodic 9209 -> D215 >= 0x8000 --> GetObjectInfo
                                                   -> GetObject
                                                   -> save
                                                   -> preview
                                                   -> READY

Any incomplete object transaction
  -> clear progress
  -> close session
  -> DISCONNECTED / explicit reconnect required
```

## Diagnostic log signatures

Healthy connection:

```text
PTP opcode 0x1001 response=0x2001
PTP opcode 0x1002 response=0x2001 or 0x201e
PTP opcode 0x9201 response=0x2001
Sony capability payload (... bytes): ...
PTP opcode 0x9202 response=0x2001
PTP opcode 0x9201 response=0x2001
```

Healthy event-driven RAW transfer:

```text
Event caught: code=0xc201 ... params=[0xffffc001]
Downloading object handle=-16383...
PTP opcode 0x1008 response=0x2001
USB object read negotiated chunkSize=65536
Successfully downloaded object ... format=0xb101
Saved tethered image to content://...
```

Healthy missing-event/burst recovery:

```text
Sony 0x9209 ObjectInMemory=0x8001   # or 0x8002+
Sony RAM contains an object without C201; downloading fallback handle
```

Firmware/transport already locked:

```text
bulkTransfer OUT failed for code 0x1001, transferred=-1/12
No PTP response for opcode 0x1001
```

When even `GetDeviceInfo` fails, repeated initialization attempts normally cannot
repair the camera. Disconnect USB first; if the body does not recover after its
power cycle, remove and reinsert the battery.

## Opcode and event reference

### Standard operations

| Code | Name | Used? |
|---:|---|---|
| `0x1001` | GetDeviceInfo | Yes, initialization. |
| `0x1002` | OpenSession | Yes, initialization. |
| `0x1003` | CloseSession | Defined; physical/session teardown currently closes USB directly. |
| `0x1004` | GetStorageIDs | No in PC Remote RAM flow. |
| `0x1005` | GetStorageInfo | No in PC Remote RAM flow. |
| `0x1006` | GetNumObjects | No. |
| `0x1007` | GetObjectHandles | Do not use for the A7R II PC Remote RAM object. |
| `0x1008` | GetObjectInfo | Yes, before every download. |
| `0x1009` | GetObject | Yes, consumes/downloads the in-memory object. |
| `0x100A` | GetThumb | Defined but not used; it would not replace consuming the full RAM object. |

### Sony operations

| Code | Name | Status |
|---:|---|---|
| `0x9201` | SDIO Connect | Required handshake, modes 1/2/3. |
| `0x9202` | GetExtDeviceInfo | Required handshake with parameter `0xC8`. |
| `0x9203` | GetDevicePropDesc | Unsafe for live `D215` polling on tested A7R II. |
| `0x9204` | GetDevicePropertyValue | Unsafe for this flow; not implemented. |
| `0x9209` | GetAllExtDevicePropInfo | Verified queue/missing-event fallback. |

### Responses

| Code | Name |
|---:|---|
| `0x2001` | OK |
| `0x2002` | GeneralError |
| `0x2003` | SessionNotOpen |
| `0x2004` | InvalidTransactionID |
| `0x2005` | OperationNotSupported |
| `0x2006` | ParameterNotSupported |
| `0x201E` | SessionAlreadyOpen/accepted only for OpenSession in this implementation |

## Regression coverage

The unit tests protect the behavior most likely to damage the camera or regress
burst support:

- Exact initialization order and parameters.
- Correct standard opcode values.
- Signed/unsigned `0xFFFFC001` event handling.
- Reusable Sony handle download behavior.
- `0xC202` and `0xC203` ignored as object announcements.
- `D215` extraction from a `0x9209` aggregate payload.
- Adaptive Android USB read-size fallback.
- Incomplete-transfer detection and retry limits.
- Download-state clearing, filename uniqueness, and responsive tether UI helpers.

## External protocol references

- libgphoto2 Sony PTP implementation:
  <https://github.com/gphoto/libgphoto2/tree/master/camlibs/ptp2>
- A7R II high-throughput and `D215` observations:
  <https://github.com/gphoto/libgphoto2/issues/158>
- Sony A7-series freeze/recovery discussion:
  <https://github.com/gphoto/libgphoto2/issues/981>

These are reverse-engineered vendor extensions. Treat behavior observed on another
Sony generation or firmware as unverified until captured in logs and tested on the
target body.
