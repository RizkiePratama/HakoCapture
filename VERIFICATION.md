# USB connection repair verification

## Diagnosis

Device logcat recorded `IllegalArgumentException` at `USBMonitor.register` because
AndroidUSBCamera 2.3.4 creates a PendingIntent with flags=0. The source confirms
that `requestPermission` calls `onCancel` when the monitor is not registered.
The application's catch block hid registration failure; the global uncaught
exception handler did not repair registration.

## Changes checked

- App-owned, non-exported USB receiver and immutable, package-scoped permission
  PendingIntent. Its original extras identify the requested device; permission is
  verified using UsbManager.hasPermission rather than callback fill-in extras.
- Camera permission precedes USB video access. No recurring permission prompt loop.
- Single-top USB chooser delivery triggers enumeration; UVC interfaces on composite
  devices are recognized. Other capture-device detach events do not close the active device.
- Native open/preview/close operations are serialized on a worker. UI callbacks
  return to the main thread and stale session callbacks are ignored.
- Surface arrival before camera open is handled; surface destruction stops preview.
- Disposal releases camera, USB control block, receiver, pending intent and worker.
- Existing UVC dependency repository restored; no native driver upgrade in this patch.

## Executed checks

- Before fix: UsbRegistrationTest failed on connected Xiaomi 2406APNFAG, Android 16,
  because no receiver was installed.
- After core fix: both instrumentation tests passed (registration regression and
  existing application-context test).
- Final source: debug APK assembled; existing JVM unit test passed.
- Final device rerun: blocked during APK installation with
  `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. The expanded receiver
  cleanup assertions and native-driver smoke test have not yet executed.
- Final lint: two errors and fifteen warnings. No lint baseline or suppression added.

## Follow-up issues

- NotificationPermission error originates in bundled Serenegiant NotificationBuilder;
  Hako does not currently call that notification path. Revisit when implementing
  recording-service notifications or updating/removing the legacy dependency.
- InvalidFragmentVersionForActivityResult flags the old transitive Fragment dependency.
  Hako directly uses ComponentActivity, but the dependency graph still needs modernization.
- Aligned16KB warns about the bundled x86_64 JPEG native library. Validate/rebuild
  native dependencies before claiming compatibility across 16 KB page-size devices.
- Actual HDMI frames, permission denial/grant, cold/warm attach, and unplug/replug
  still require a capture card connected to the phone. ADB inspection showed the
  phone in USB device mode with no capture card attached during this session.

## Useful log command

```powershell
& "D:\DevTools\sdk\platform-tools\adb.exe" logcat -v time Hako.USB:V USBMonitor:V UVCCamera:V AndroidRuntime:E "*:S"
```

Filtering only by application package name omits most camera log messages because
they use class/component tags instead.
