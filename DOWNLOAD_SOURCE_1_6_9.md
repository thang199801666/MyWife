# Vợ Tui 1.6.9 — download source recovery

- Canonical watch URLs isolate extraction from mobile/Shorts/share parameters.
- Inspection uses `--ignore-no-formats-error` so a missing mergeable pair does
  not prevent reading real video formats. It still rejects live streams,
  DRM formats, audio-only sources and storyboards. Unknown height is offered
  as highest source quality rather than rejected or given an invented height.
- A recoverable extraction/format/HTTP 403 failure gets at most one fresh
  attempt using `default,web_safari` and the Safari webpage client. Successful
  initial requests make no extra attempt. Login/private/region restrictions,
  HTTP 429, DNS failures, timeouts and storage failures do not trigger it.
- Inspection and video/MP3 download share the recovery policy. Cancellation
  stops recovery. New-client downloads remove only generated `media.*` partial
  files before selecting new streams, so old fragments cannot be mixed in.
- Video selectors prefer the best video-containing stream, support silent
  sources and require a video codec on combined-stream fallback. Fixed-height
  selectors retain their ceiling. Old saved selectors are upgraded at execution;
  persisted download records, retention rules and output paths remain compatible.
- Source-empty and live-stream explanations are translated in Vietnamese and
  English. Existing source diagnostics remain available below the explanation.

## Verification

- Packaged yt-dlp selector fixtures: highest muxed source, silent source,
  resolution ceiling and rejection of audio-only source passed offline.
- Locale parity: 368 paired resources and placeholder contracts passed.
- API 37 x86_64 emulator, existing packaged yt-dlp 2026.08.19, QuickJS and
  FFmpeg: default extraction of dQw4w9WgXcQ returned formats up to 2160p.
  The alternate client fetched video/audio and merged a 144p MKV successfully,
  without a QA proxy or account cookies.
- Alternate MP3 extraction/conversion passed. FFprobe verified MKV with AV1
  video (256x144) and Opus audio, 213.068 s, 4,919,389 bytes; MP3 with an MP3
  audio stream, 213.072 s, 7,045,580 bytes. Both are complete media files.
- Final build: successful in 7m 15s; 49 unit tests passed, zero lint errors.
  The initial build overlapped further selector/error edits; the final build
  recompiled the completed sources and all checks passed.
- Debug 1.6.9 (36) installed with `adb install -r` and launched on the emulator;
  no application fatal exception appeared during the startup check.
- Release: dist/VoTui-v1.6.9-release-arm64-v8a.apk, 54,332,500 bytes;
  SHA256 70A661165FAE6451ADD50FDD4A94D9255AD5DF06369FDCCAC5C3A3323D2BD8C7.
  ARM64 only, Vợ Tui label, non-debuggable; 16 KiB alignment and v2/v3 signatures
  verified. Certificate matches 1.6.8 and is the existing development/test key.
- QA media files were removed from the isolated emulator cache directory;
  user download records/files were not modified. No physical phone was tested.
- The user has not supplied a failing video URL yet. This validates recovery
  mechanics and tested public sources, not every YouTube video. Account or
  region restrictions and ongoing livestreams retain their existing limits.

References: [yt-dlp extractor arguments](https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/README.md#extractor-arguments),
[EJS setup](https://github.com/yt-dlp/yt-dlp/wiki/EJS).
