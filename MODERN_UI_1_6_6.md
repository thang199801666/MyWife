# VoTuibe 1.6.6 — native interface refresh

## Changes

- You: VoTuibe avatar/header, Downloads/EQ/About shortcuts, visible You title,
  pill search field and compact history/recommendation headers. Favorites/Queue
  keep their playlist cover and Play all/Shuffle controls.
- Downloads: thumbnail rows with VIDEO/MP3 badges, source quality, destination,
  readable status and progress. All/Video/MP3/Temporary filters persist across
  activity recreation. Completed rows open offline playback directly; the
  overflow button provides actions. Permanent-file removal still only removes
  the list record; temporary-file removal deletes the temporary content.
- Action menus: a shared native bottom sheet with rounded corners, vector icons,
  descriptive rows, accessible labels, close/outside/back dismissal, bounded
  scrolling and animations that respect Android's animator setting. Owners
  dismiss sheets when their activity is destroyed.
- Offline player: compact back/title/EQ toolbar, MP3 cover artwork and title.
  Native audio/video playback and the existing equalizer session remain in use.
- Reuse the download adapter when progress changes, cache unchanged labels and
  chip backgrounds, and skip the hidden collection cover request for history.
  Thumbnail requests use the existing bounded/coalesced image loader.

## Validation

- Android API 37 x86_64 emulator: You header, shortcuts, watch-history thumbnails,
  Downloads thumbnails, MP3 filter and overflow sheet visually inspected.
- At 130% font scale, download rows and the action sheet remained readable with
  no overlaps; long titles ellipsize. Changing font scale recreated the activity
  safely and retained the selected MP3 filter. Restored original font_scale 1.0.
- Main quick-action sheet shows all six destinations and descriptions.
- Final debug/release build succeeded in 7m 56s. All 40 unit tests passed and lint
  has zero errors. GUI changes are validated through actual UI checks rather
  than unit tests that duplicate layout implementation.
- Screenshots: `branding/ui-you-1.6.6.png`, `ui-downloads-1.6.6.png`,
  `ui-sheet-1.6.6.png`, `ui-sheet-font-1.6.6.png`, `ui-quick-sheet-1.6.6.png`.
- The watch-page Download button opened Save To Device/Temporary Save, then
  Video/MP3 and the MP3 quality sheet with Best source VBR, 320/192/128 kbps.
  Dismissed the sheet without enqueueing another download.
- Final debug build installed with `adb install -r`, preserving all three saved
  download records. The saved MP3 opened with cover artwork and title; tapping
  the playback area displayed pause/seek controls and advancing progress
  (1:19 / 3:33). The toolbar EQ button opened the existing five-band EQ dialog.
  Screenshots: `branding/ui-offline-mp3-1.6.6.png` and
  `branding/ui-offline-controls-1.6.6.png`.
  An earlier automated navigation sequence unexpectedly returned to Main; this
  was not reproduced when each screen was inspected before the next tap.
  The cause is unconfirmed. No AndroidRuntime crash appeared in the final check.
- Release: `dist/VoTuibe-v1.6.6-release-arm64-v8a.apk`, 54,144,028 bytes,
  versionCode 33, ARM64 only, not debuggable. Alignment check passed and v2/v3
  signatures verified. Existing development/test signing key retained.
  SHA-256: `F4B258089DC34A8AD85255B29D3380FB0F5C6056AE6EA976EA5D1A85A3D3DBB0`.
  Phone hardware is not connected.

The YouTube web content and native VoTuibe surfaces have different rendering
systems. This refresh covers the native screens and menus listed above; it
does not claim a pixel-identical copy of every current YouTube experiment.
