# Project preferences

- The user requests that future phone builds target their Snapdragon phone.
  Deliver the `arm64-v8a` release APK by default, rather than the universal APK.
  Keep video/MP3 download support and its matching Python/FFmpeg/QuickJS libraries.
- With the current build configuration, use `assembleRelease -PsplitApks=true`
  and select the `app-arm64-v8a-release-unsigned.apk` output for phone delivery.
  Emulator builds may use x86_64 or the normal universal debug build when needed.
- Use the existing signing certificate for updates that preserve local data.
  Clearly identify the current development signing key; do not describe it as
  a production publishing key. Do not create or replace a signing key silently.
- Prefer Vietnamese for progress updates and final responses.
