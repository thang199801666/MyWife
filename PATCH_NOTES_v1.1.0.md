# YouTooBee v1.1.0 patch notes

- Added `PlaybackSessionCoordinator`.
- Added `WebViewLifecycleCoordinator`.
- Added `RegressionFixtureHarness` and integrated it into release stress tests.
- Refactored `MainActivity` to remove duplicate playback/session and WebView lifecycle state.
- Preserved Shield rules, ad-block script, Library data and SQLite schema v2.
- Version bumped to 1.1.0 / code 11.

## Continued hardening pass

- Added `NavigationTargetResolver` for deterministic address-bar/search and shared-text URL handling.
- Added `NavigationSecurityPolicy`; executable/local-content schemes are blocked and external app launches require a trusted YouTube source page.
- Hardened `intent:` dispatch by clearing explicit component/package/selector targeting and using browsable intents only.
- Added redacted blocked-navigation diagnostics without storing full browsing URLs.
- Added `PlaybackProgressPolicy` to clamp invalid positions and honor playback speed during progress extrapolation.
- Applied playback-position normalization at the session, snapshot, service publisher and playback service boundaries.
- Expanded `RegressionFixtureHarness` with navigation-safety, input-resolution and playback-progress fixtures.
- Kept application version at 1.1.0 / code 11; SQLite remains schema v2.

## Vanced-style playback continuation

- Added true-black AMOLED surfaces for the native shell and supported YouTube page/player backgrounds.
- Added compact YouTube chrome; trusted YouTube pages can run without the browser toolbar, with status-line tap-to-reveal.
- Added preferred quality presets: Auto, Highest, 2160p, 1440p, 1080p, 720p, 480p, 360p and 240p.
- Added auto-repeat with queue auto-advance suppression while repeat is active.
- Added opt-in SponsorBlock-compatible community sponsor/self-promo/interaction and intro/outro skipping.
- Community lookup uses a four-character SHA-256 prefix request, validates the full hash/video id locally, bounds response/time/segment sizes, and stays disabled in Safe Mode.
- Added community-segment skip statistics to Shield stats/dashboard.
- Added quality control to the player action row and new playback/privacy controls to Settings.
- Extended regression fixtures for community-segment candidate filtering and seconds-to-millisecond conversion.
- Preserved version 1.1.0 / code 11 and SQLite schema v2.

## Enhanced-client rewrite — phase 1

- Pivoted the primary UX from a browser/shield shell to a dedicated YouTooBee client shell.
- Added `YouTubeRoute` for Home/Watch/Shorts/Search/Subscriptions/Playlist/Channel route classification.
- Added `ClientChromePolicy` to drive app-bar and playback-action visibility from route state.
- Rebuilt the main layout with YouTooBee app bar/search and Home/Shorts/Subscriptions/Library bottom navigation.
- Browser back/forward/reload controls are no longer visible in the primary UI.
- Playback actions are route-aware and Repeat is now directly accessible while watching.
- Added `EnhancedClientDefaults` for a one-tap baseline of background playback, PiP, gestures, AMOLED, resume/history and recovery behavior; third-party community lookups remain opt-in.
- Added `PlaybackBackend` plus `WebViewPlaybackBackend` as the migration seam for future playback-engine replacement.
- Extended regression fixtures for route parsing and client-chrome behavior.
- Version remains 1.1.0 / code 11; SQLite remains schema v2.

## Enhanced-client rewrite — phase 2

- Renamed the user-facing app/project branding to **YouTooBee** while preserving the existing application ID and storage compatibility.
- Split the single browser/player WebView into independent Browse and Player surfaces.
- Added persistent expanded/mini/hidden player surface state.
- Video links route into the Player Surface; non-playback navigation routes back to Browse.
- Added mini-player play/pause, expand and close controls with title/channel metadata.
- Added independent Browse/Player WebView state restoration and `lastBrowseUrl`.
- Added separate filtering context for Browse and Player.
- Added presentation-only `ClientSurfaceScript` to remove duplicated mobile-site chrome.
- Shorts feed remains a browse destination while an individual Shorts URL is treated as playback.
- Added native mini-player progress plus swipe-to-dismiss.
- Added independent Wi-Fi/unmetered and mobile/metered quality presets with automatic profile switching when network metering changes.
## Build-fix follow-up
- Fixed `PlayerController.kt` compile failure caused by the non-existent `WebView.isDestroyed` property.
- Added an explicit `PlayerController.release()` lifecycle guard so queued JavaScript callbacks are ignored after Activity teardown.
- Release the controller before destroying the player WebView.
- Removed deprecated `android.useAndroidX=false` from `gradle.properties`.
- Normalized horizontal ProgressBar styles to the concrete framework style to reduce AAPT theme-resolution ambiguity.
- Version remains 1.1.0 / versionCode 11.

