# VoTuibe mobile UI 1.5.0

The user-provided After image is the collection/playlist layout reference.
The broader direction follows YouTube's official guidance to reduce visual
noise and put videos first:
https://blog.youtube/inside-youtube/design-principles-use-put-creators-center-stage/
The official mobile miniplayer reference describes moving the player and
returning to the watch page by tapping it:
https://support.google.com/youtube/answer/9162927?co=GENIE.Platform%3DAndroid&hl=en

Implemented:

- Home/Shorts/Subscriptions/You icon navigation with a working quick-actions
  button. The plus opens app actions, not an unsupported upload screen.
- Search is opened from its icon; submitting hides the keyboard/search input.
- Library collections use a 16:9 cover, gradient header, counts and rounded
  Play all/Shuffle buttons. Video rows show rounded thumbnails, title/metadata
  and an overflow menu preserving queue, favorites, hide and related actions.
- Play all/Shuffle prioritizes up to 50 visible matching videos, starts the
  first and atomically queues the rest while retaining unrelated queue items.
- Thumbnails use a 4MB memory cache and a bounded two-thread worker. Invalid
  IDs, failed requests, excessive payloads and recycled-row results are rejected.
- Floating mini-player has rounded corners/shadow, retains native transport
  controls, can be dragged within the browser surface, and expands on video tap.
- Existing watch-page swipe minimize, filtering, background playback and PiP
  remain in place. VoTuibe keeps its own brand and local library; this does not
  claim YouTube account/upload/Cast integration or a pixel-identical app clone.

Builds and lint passed. 23 JVM tests (including collection queue ordering) and
25 player JavaScript fixtures passed. Initial emulator visual verification
confirmed live thumbnails and the collection header. Final emulator checks:

- Video overflow opened Play next/Add to queue/Save to favorites/Find similar.
- The floating card moved from [295,1128][700,1450] to
  [14,592][419,914] while the same video remained playing (25.880997s).
- Tapping the card expanded it, retained the same paused position (26.047702s)
  and removed the mini-player stylesheet.
- Search submission restored the brand header and hid the input; Gboard's
  separate stylus onboarding was dismissed during this check.
- Captures: branding/ui-library-1.5.0.png and branding/ui-floating-1.5.0.png.

Test playback was left paused, the browse destination restored to Home, and
the local WebView debug forward removed.

APK files: dist/VoTuibe-v1.5.0-debug.apk and
dist/VoTuibe-v1.5.0-release-unsigned.apk (the latter requires signing).
