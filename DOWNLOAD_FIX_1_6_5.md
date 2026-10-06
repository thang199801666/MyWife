# VoTuibe 1.6.5 — download reliability

## Findings

- The library 0.18.1 includes yt-dlp 2025.11.12. Previously, a failed online
  update was silently ignored, so a fresh installation could use that old engine.
- The library update metadata request has no explicit connection/read timeout.
  It ran before both quality inspection and downloads, delaying them and making
  the source dialog's cancellation ineffective during that network request.
- The project emulator failed DNS lookup for both YouTube and Google. This is
  evidence about the emulator only; the phone's download error has not yet been
  provided.

## Changes

- Bundle the official stable yt-dlp 2026.08.19 executable, SHA-256
  `1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6`.
- Existing installations receive the bundled engine if their recorded version
  is older. Normalize the library's `yt-dlp YYYY.MM.DD` version string before
  comparing dates, including a regression test for the old packaged engine.
  Python, FFmpeg and QuickJS stay available for video merge and MP3.
- Optional daily update checks happen in the background. Failed checks retry
  no sooner than an hour. HTTP connect timeout is at most 5 seconds, read timeout
  at most 10 seconds, with a 30-second deadline checked while reading. Metadata
  and engine responses have size caps; downloads require the release SHA-256.
- Keep the existing engine on update failure, log the error and retain update
  diagnostics. Atomic file replacement never happens while a downloader process
  is running; a busy engine defers the update until a later check.
- Reduce extractor retries to one, preserve stream/fragment retries, and explain
  common errors with up to 4 KB of underlying diagnostics in Downloads → Details.
- Preserve Save To Device, Temporary Save for 30 days, source quality selection,
  MP3 quality selection, offline EQ and the existing signing identity.

## Sources

- [Official engine release](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.08.19)
- [Library initialization and execution](https://github.com/yausername/youtubedl-android/blob/master/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt)
- [Library updater](https://github.com/yausername/youtubedl-android/blob/master/library/src/main/java/com/yausername/youtubedl_android/YoutubeDLUpdater.kt)

## Verification

- Final build: `assembleRelease assembleDebug testDebugUnitTest lintDebug
  -PsplitApks=true`, successful in 9m 44s. All 40 unit tests passed; lint has zero
  errors. A runtime check exposed the prefixed library version string; the
  fortieth test covers that upgrade path.
- Android x86_64 native engine downloaded and merged video into MKV (4,919,389
  bytes), and FFmpeg extracted MP3 VBR (7,045,580 bytes) from the public sample
  `dQw4w9WgXcQ`. See `download-native-video-1.6.5.log` and
  `download-native-mp3-1.6.5.log`. These CLI tests used a temporary loopback QA
  proxy to bypass emulator DNS. The helper and ADB forwarding were removed
  after testing; the app does not use this proxy.
- The native Save dialog retrieved the source and an in-app Save To Device job
  selected Highest source (2160p), completed at 100% and exported successfully
  to `content://media/external/downloads/159`. Record:
  `fd4557af-607c-4091-9546-450e92a147e5`. This runtime test used the first 1.6.5
  debug build without the QA proxy. The final build subsequently corrects the
  prefixed version migration. Previous saved MP3 and temporary video records
  remained intact.
- The physical phone is not connected. Its exact original error and affected
  source have not been provided; do not infer them from the emulator DNS error.
- Release signing retains the existing Android development/test certificate,
  not a production publishing key. Deliver the ARM64 split for the user's phone.
- `dist/VoTuibe-v1.6.5-release-arm64-v8a.apk`: versionCode 32, only `arm64-v8a`,
  not debuggable, 54,135,556 bytes. Alignment passes, v2/v3 signatures verify.
  SHA-256: `9E7AB768A07C7616C6DB14B20C5B95350262C9ECB6B28C18C3025648D04CA679`.
