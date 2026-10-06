# Vợ Tui 1.6.11 — player restoration, downloads and playback choices

- Expanded player CSS fills the player container, including stale inline video
  dimensions left by a small viewport. Mini/PiP/expanded modes have explicit
  document state; expanding removes mini CSS and requests a resize after layout.
  A short resize guard preserves playback intent, synchronizes the player play
  state and is cleared by explicit Pause or website pointer/touch interaction.
- Restored a visible Auto next button next to Minimize. Native next-video handling
  uses Queue first, then one related video when Queue is empty. The website's
  independent countdown is disabled to avoid double navigation and to honor Off.
  Related links are bounded, HTTPS YouTube-only, exclude current/Shorts/promoted
  links, and are revalidated against the current completed session before opening.
- Auto requests the source maximum, lowers one step after a sustained empty-buffer
  stall, and raises one step after 30 seconds of uninterrupted buffered progress.
  Pauses, seeks and long timer gaps break the stable interval. Changes have
  cooldowns. Fixed manual choices never participate in automatic up/down changes.
- Manual choices from either the app selector or website quality menu are persisted
  for subsequent videos and restarts. Choosing Auto restores adaptive behavior.
  Legacy stored Auto is mapped to adaptive startup. Unsupported resolutions wait
  for source metadata and select the nearest lower supported quality.

## Release download failure

The previous minified release worked if Python/FFmpeg was already extracted.
After backing up only generated runtime packages, a first-time release download
reproduced “class w0 is not a concrete class”. The release mapping identified w0
as org.apache.commons.compress.archivers.zip.AsiExtraField. ExtraFieldUtils uses
getConstructor().newInstance() for ZIP fields, which R8 cannot infer as normal
constructor calls. A narrow keep rule retains public constructors of ZipExtraField
implementations without disabling app shrinking or keeping all downloader code.
The provided phone screenshot has the same failure shape with another obfuscated
class name. History/download records and saved media were not cleared in this QA.

## Verification

- 45 JavaScript player fixtures passed, including staged recovery, manual persistence,
  source metadata readiness and native countdown control.
- Surface-mode/PiP restoration fixtures and related-video security/bounds fixtures passed.
- Locale resource parity: 371 paired strings passed.
- Final frozen-source build passed in 6m 24s: 49 unit tests and zero lint errors.
- Fresh minified-release runtime extraction progressed through Python/FFmpeg init
  into native Python/QuickJS execution with no concrete-class error. The native
  inspection afterward returned source maximum 2160p (401+251). The release UI
  picker/full-file export were not completed in this pass; emulator rendering and
  System UI ANRs interrupted that check. Main-thread ANR trace was waiting in
  HardwareRenderer drawFrame, not in download extraction. QA switched the emulator
  to software OpenGL without Vulkan; no APK rendering policy was changed.
- Live expanded -> mini -> expanded: video and container matched the player
  dimensions after restoration (426.6667 x 240 CSS px). Final restoration stayed
  playing, paused=false. Evidence: build/qa-final-expanded.json.
- Website manual 720p was captured by the native bridge, decoded at 1280x720 and
  persisted on TicIH5VBSqM plus subsequent automatic navigation to vx2u5uUu3DE.
  Evidence: build/qa-manual-persist-1.6.11.json, qa-manual-next-1.6.11.json and
  qa-auto-on-result.json.
- Auto next Off left TicIH5VBSqM ended on the same URL. On selected the related
  Bon Jovi video vx2u5uUu3DE with the same manual 720p choice. Its navigation
  destroyed the pending CDP evaluation context; the following status read confirmed
  the actual new video and active playback. Queue was empty for this test.
- Restored the emulator quality preference to Auto after QA, paused playback,
  removed the debugger forward and generated runtime backup folders. All three
  existing offline records remained. No physical Snapdragon phone was connected.
- Release: dist/VoTui-v1.6.11-release-arm64-v8a.apk, 54,344,788 bytes.
  SHA256: 105688A6C0CF997020A430A3A372D11E0C6EB68464D196443789DCC2D964170F.
  ARM64 only, label Vợ Tui, version 1.6.11 (38), non-debuggable. 16 KiB zip
  alignment and v2/v3 signatures verified. Existing development/test signing key.

References: [Apache ExtraFieldUtils](https://github.com/apache/commons-compress/blob/rel/commons-compress-1.27.1/src/main/java/org/apache/commons/compress/archivers/zip/ExtraFieldUtils.java).
