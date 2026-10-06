# VoTuibe mobile UI 1.5.1

Continues the YouTube-inspired mobile layout with VoTuibe branding.

- Centered vector-only toolbar, navigation-create, transport and overflow
  buttons replace font symbols. Play/pause reflects the actual player state.
- Watch actions are rounded, labeled pills with consistent inline vectors.
  Saved state uses a filled bookmark; enabled Repeat has a blue icon.
- Collection Play all/Shuffle use properly sized, contrasting vector icons.
- Library transport is 63dp with a 3dp progress line, leaving more room for
  rows. Seek buttons and playback controls have accessibility descriptions.
- Floating transport uses an opaque background to keep text and icons clear.

Validation: debug/release builds, JVM tests and lint passed (see
build-votuibe-1.5.1.log). Installed debug version 20 on emulator-5554.
Visual checks confirmed toolbar alignment, collection actions, row menus,
watch pills, floating controls and compact library playback controls.
The row menu still opens Play next/Add to queue/Save to favorites/Find similar.
Downward swipe minimized the paused video at 47.297437 seconds. Native Play
resumed playback, changed its accessibility label to Pause and reported
paused=false; final playback was paused at 63.382076 seconds. The temporary
WebView debug forward was removed.

Screenshots: branding/ui-library-1.5.1.png, branding/ui-watch-1.5.1.png and
branding/ui-floating-1.5.1.png.

Installable APK: dist/VoTuibe-v1.5.1-debug.apk.
SHA256: D45CA92AC8FD47464DCB469552C152837B15F9BA219902F5D342D47A335DD7A1
Release artifact: dist/VoTuibe-v1.5.1-release-unsigned.apk (requires signing).
