# Zebra and focus peaking

## Behavior

- The Scopes text button is now a chart icon immediately beside Capture settings.
  Both are at the right of the transparent, full-width landscape bottom bar.
- Zebra marks only pixels whose Rec.709 weighted brightness reaches the selected
  70-100% threshold (95% default). The previous blanket stripe canvas was removed.
  This threshold describes decoded image brightness, not scene exposure or HDR IRE.
- Focus peaking uses a 3 x 3 Sobel luminance gradient. Sensitivity is Low, Medium,
  or High; color is Red, Green, Cyan, or Yellow. Flat regions are not highlighted.
  The sampling footprint is at least one output pixel to keep markers visible on
  a reduced preview. High sensitivity can highlight noise as well as detail.
- Both tools have independent Show in preview and Include in recording switches.
  All start off; enabling preview never enables recording. A recording switch
  burns that aid into the encoder output and can be changed during recording.
- The controls are shared between Capture settings and Configure scopes.
- Preview and encoder use the same GPU shader with separately selected flags.
  Compose UI, guides, and scope panels remain outside the encoder render pass.
- Scope analysis reads a clean 320 x 180 framebuffer at about 6 Hz, with both
  tools forcibly off, so enabling preview aids cannot contaminate measurements.
  Analysis work stays bounded, and the readback is disabled when scopes are hidden.

## Verification

- Added output-routing tests before implementation; the initial test build failed
  because the new assist model did not exist.
- 24 JVM tests pass, including independent output selection, clean analysis, and
  threshold range handling. Debug app and instrumentation APKs compile.
- Seven instrumentation tests render the actual production shader into a GLES2
  pbuffer: disabled pixel preservation, dark/midtone zebra preservation, highlight
  stripes, luminance versus clipped color channels, flat-field peaking, sharp-edge
  markers, and rejection of a gradual blurred edge.
- An independent code review found no defects in new GL state/output routing.
- GPU tests, live HDMI performance, and playback of recorded aids have not been
  verified on-device. Device actions were previously blocked by the automatic
  approval service usage limit, so no further installation was attempted.

## Build

Use the bundled Android Studio JBR, workspace Gradle/Android caches, and in-process
Kotlin compilation in the restricted environment. The debug APK is signed using
this machine's existing Android debug key, preserving update compatibility.
