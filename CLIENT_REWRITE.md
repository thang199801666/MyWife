# YouTooBee enhanced-client rewrite

## Product target

YouTooBee is being rewritten as a dedicated YouTube-focused client with an app-first navigation and playback experience: Home, Shorts, Subscriptions, Search, Watch and Library should feel like first-class application surfaces rather than browser controls around a web page.

The codebase keeps YouTooBee branding and its own UI/assets. Playback source implementations must use authorized media/player interfaces and must not depend on bypassing DRM, signatures or other access controls.

## Phase 1 — client shell and playback boundary (implemented)

- Added `YouTubeRoute` classification for Home, Watch, Shorts, Search, Subscriptions, Playlist and Channel routes.
- Added `ClientChromePolicy` so app-bar/action visibility comes from client state rather than scattered URL checks.
- Reworked the main layout into an application shell: YouTooBee app bar, search, playback action strip and Home/Shorts/Subscriptions/Library navigation.
- Removed browser back/forward/reload controls from the visible UI while retaining hidden compatibility anchors during migration.
- Playback actions now appear only on Watch/Shorts routes.
- Repeat is a first-class player action.
- Added one-tap enhanced-client defaults.
- Added `PlaybackBackend`; gestures, PiP, MediaSession commands, sleep timer and queue controls no longer need to know the concrete playback implementation.
- `WebViewPlaybackBackend` is the compatibility backend for the current player during migration.

## Phase 2 — independent browse/player surfaces (implemented)

- Added a dedicated Browse WebView and a separate Player Surface instead of using one WebView for both jobs.
- Watch links and individual Shorts are routed into the Player Surface; Home, Search, Shorts feed, Subscriptions and channel/navigation pages stay in Browse.
- Added `PlayerSurfaceController` with `HIDDEN`, `EXPANDED` and `MINI` states.
- Back from an expanded video collapses to the mini-player before leaving the browse shell.
- Home/Search/Subscriptions navigation can continue behind the mini-player without replacing the active playback page.
- Added mini-player play/pause, expand and close controls plus current title/channel.
- Player-to-channel/non-playback navigation is redirected back to Browse instead of consuming the player WebView.
- Added `ClientSurfaceScript` to remove duplicate YouTube website chrome from the embedded player/browse surfaces without changing the media transport boundary.
- Browse and Player now use separate `FilterEngine` instances so a per-channel playback allowlist cannot leak into Home/Search filtering.
- Browse WebView state, Player WebView state and mini/expanded surface state are restored independently.
- `lastBrowseUrl` is stored independently from the legacy playback URL while remaining backward-compatible with existing preferences.
- Mini-player now includes a native progress indicator and horizontal swipe-to-dismiss behavior.
- Preferred video quality is split into Wi-Fi/unmetered and mobile/metered profiles; the active profile follows connectivity changes automatically.

## Phase 3 — watch surface decomposition

- Move watch-specific state and actions out of `MainActivity` into a dedicated watch coordinator.
- Separate video metadata, channel state, playback state and action state.
- Add a persistent mini-player contract so navigation does not own playback lifetime.
- Move quality, speed, repeat, SponsorBlock-compatible segments and gestures into backend-neutral playback policies.
- Reduce `MainActivity` to shell/navigation/lifecycle responsibilities.

## Phase 4 — discovery and account surfaces

- Define provider contracts for search/home/channel/playlist metadata.
- Keep local History/Favorites/Queue as offline-capable features.
- Make account-backed subscriptions optional and provider-driven instead of coupling them to the local library.
- Add deterministic caching and pagination boundaries.

## Phase 5 — playback backend migration

- Keep `PlaybackBackend` as the stable application contract.
- Replace the compatibility WebView backend only where an authorized media/player interface is available.
- Keep PiP, background controls, MediaSession, queue, resume, sleep timer and gestures unchanged across backend swaps.

## Feature target

The enhanced-client target includes background playback, PiP, AMOLED mode, custom playback speed, preferred quality, repeat, queue, local resume/history, fullscreen gestures, distraction controls and optional community segment skipping. These features should be implemented as YouTooBee capabilities, not by copying Vanced branding or proprietary assets.
