# Hako monitor roadmap

## Immediate repair

Fix modern Android USB registration and permission handling without replacing the
native UVC driver. Remove exception suppression, wait for Camera permission, reuse
access granted by the USB chooser, avoid repeated permission prompts, match UVC
interfaces on composite devices, and attach the preview surface in either arrival
order. Add an on-device regression test and build/lint the result.

## Product milestones

1. Reliable monitor: cold/warm USB launch, disconnect/reconnect, supported UVC
   format/resolution/frame-rate negotiation, aspect-correct preview, keep-screen-on,
   explicit permission/no-device/no-signal/streaming/error states. Measure latency
   with a camera viewing a timer; measure delivered FPS separately from screen FPS.
2. Shared frame pipeline: UVC -> SurfaceTexture -> EGL renderer. Fan out the same
   source image to preview, scope analysis and encoder surfaces. Keep UI overlays
   outside the encoder render pass. Bound queues so scopes cannot stall preview.
3. Accurate scopes: RGB and luma histograms with 256 bins, clipping indicators,
   and a Cb/Cr vectorscope with graticule and color targets. Declare color matrix
   and full/limited range; validate with known ramps and color bars. Analyze a
   reduced image at a configurable cadence; label sampled scopes accurately.
4. Clean recording: enumerate MediaCodecList encoders and validate size/rate,
   bitrate, profile and Surface input support. Start with AVC/MP4; offer HEVC and
   other codecs only when encoder/container combinations are supported and tested.
   Implement encoder draining, timestamps, muxer track startup and EOS finalization,
   MediaStore output, storage/thermal errors, and unplug handling. Add USB audio
   routing and A/V sync separately; do not silently substitute phone microphone audio.
5. Compose UI: dark landscape monitor, collapsible scopes, compact input status,
   recording timer and a clear record control. Settings sheet exposes actual capture
   and encoder capabilities. Disable unavailable actions; preserve aspect ratio
   and accommodate display cutouts/insets.

## Existing limitations

Histogram/vectorscope UI currently contains placeholders. VideoProcessor has no
compiled compute program or connected frame input. Recorder is not wired to the
screen and does not drain encoded output or write muxer tracks. The Record button
currently changes appearance only. These require the milestones above.

Android controls the USB chooser and saved defaults; the app declares compatible
devices but cannot force a chooser every time or override the user's defaults.
Clean recording means no Hako overlays; it cannot remove overlays already present
in the camera's HDMI output. Quality is limited by the capture card's USB output.

## Validation

- On-device regression: registration must actually install a receiver on modern Android.
- Unit tests, debug APK build, lint, and instrumentation tests.
- Hardware follow-up: attach with app closed/open, deny/allow permissions, reconnect,
  rotate/background/foreground, verify moving HDMI picture, and distinguish HDMI loss
  from USB removal. Test scope accuracy and recording output in their milestones.

## References

- [Android USB host](https://developer.android.com/develop/connectivity/usb/host)
- [Codec discovery](https://developer.android.com/reference/android/media/MediaCodecList)
- [Video capabilities](https://developer.android.com/reference/android/media/MediaCodecInfo.VideoCapabilities)
