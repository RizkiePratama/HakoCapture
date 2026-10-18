# Monitoring rendering update

Requested: Resolve-inspired density clouds, joined and optional split channels,
RGB split/combined and luminance histograms, and space for the live preview.

## Implementation

- Replaced the 48 x 64 block plots and average/maximum curves with 320 x 256
  density rasters. A narrow reconstruction kernel and exponential exposure retain
  faint samples; additive channel blending brightens overlapping traces.
- Added full-distribution luminance waveform and a 256 x 256 color vectorscope.
  Fixed the duplicate chroma offset in the YUV parade. Vector targets and samples
  use the same Rec.709 chroma mapping, with positive Cr upward.
- Joined RGB/YUV remains the default; each panel menu offers Combined and Split.
- RGB histogram offers vertically split or combined channels. Luminance histogram
  can be enabled separately. All 256 bins are drawn, with a shared RGB population
  scale and no artificial clipping marker or per-channel height multiplier.
- Opaque scope dock sits beside the preview in landscape and below it in portrait.
  ALL fits enabled scopes; STRIP scrolls larger panels. Close/SCOPES hides/reopens
  the dock. The preview keeps its source aspect ratio and the controls have their
  own space in portrait. Settings remain accessible when every scope is disabled.
- Analysis stays on its worker, drops snapshots while busy, and pauses when the
  dock is hidden. No dependencies added.

## Full-height landscape and pinning follow-up

- Landscape preview canvas now extends behind the top bar and to the bottom edge.
  The full-width bottom bar has a transparent background in landscape, with the
  record button centered and scope/settings icons together at the right. With the dock hidden,
  the source-aspect-ratio image can use the complete screen height; opening the
  dock reserves half the width for all scopes.
- Every scope menu offers Pin over preview. Pinning collapses the dock and shows
  one scope at the lower left on a 58% opaque black background. Raster backgrounds
  become transparent while preserving trace brightness. Pinning another scope
  replaces the previous selection; the overlay close button unpins it.
- A pinned scope keeps the analysis worker active even with the dock closed.
- Added model tests for single-scope replacement and analysis lifecycle, plus
  compiled instrumentation checks for full-height preview and pin/unpin behavior.

## Verification

- Reproduced neutral-chroma bug with a failing JUnit test before fixing it.
- 20 JVM tests pass, including full-column luma distribution, neutral chroma,
  additive RGB overlap, split-lane isolation, and shared histogram scaling.
- Debug APK and instrumentation APK build successfully.
- On-device synthetic-signal rendering/menu test passed. Screenshot inspected:
  `build/scope-density-preview.png`. This is a test fixture, not a live HDMI frame.
- The latest full-monitor, full-height, and pinning instrumentation checks have
  compiled but have not run on-device. Final installation was blocked when the
  automatic approval service hit its usage limit; no workaround was attempted.
- Lint still reports the two pre-existing errors documented in VERIFICATION.md:
  NotificationPermission in the bundled USB dependency and
  InvalidFragmentVersionForActivityResult. No suppressions or baseline added.

## Limits

These scopes analyze reduced 320 x 180, 8-bit clean GPU readbacks at approximately
6 Hz; axis labels remain 0-255. They are inspired by Resolve's density presentation,
not an implementation of its proprietary renderer or a claim of 10-bit analysis.
Live HDMI comparison and sustained on-device performance need a capture card.


