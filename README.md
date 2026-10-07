# Vợ Tui v0.1.57 — Base

[Tải bản mới nhất](https://github.com/thang199801666/MyWife/releases/latest) ·
[Source code](https://github.com/thang199801666/MyWife)

This is the base release series, numbered **0.x** while the app is still under
development. Earlier 1.x entries below record development iterations, not a
production 1.0 release. Android versionCode remains monotonic so existing installs
can update without removing their data.

Version 0.1.57 adds native search filters and a library overview, updates app
chrome and player gestures, and extends playback and memory-management policies.

Version 0.1.7 reconciles YouTube's paused player state with still-playing media
after returning from the mini-player, restoring the website Play/Pause control.

Version 0.1.6 bounds resize playback protection and gives website Play/Pause
clicks and keyboard controls priority over automatic playback restoration.

Version 0.1.5 improves Shorts resource management, background task lifecycles,
library persistence and Home recommendations, and keeps update prompts minimal.

Version 0.1.4 keeps Shorts in the browse feed, selects the visible Shorts media,
synchronizes player Repeat controls, and simplifies update messages.

Version 0.1.3 updates navigation and player gestures, adds local search history
and Home pull-to-refresh, and uses recent searches in local recommendations.

Version 0.1.2 fixes an Android 11+ startup crash by creating the window decor
before accessing its system bar controller. It preserves existing app data.

Version 0.1.1 preserves playing/paused state when switching quality, including
stream reload pauses, and avoids applying a new native quality selection twice.

The base includes an in-app update screen backed by this repository's GitHub
Releases. Open Settings → Check for updates, or You → About → Check for updates.
Downloads are verified against SHA-256, package/version and the installed signing
certificate before handing installation to Android. See [release publishing](GITHUB_RELEASES.md).

Version 1.6.12 removes the hidden mobile topbar's leftover sticky-player offset.
The video frame now aligns with its reserved space, keeping the watch title visible
after opening a video and returning from minimize.

Version 1.6.11 restores expanded player geometry after minimize, repairs first-time
Python/FFmpeg extraction in minified releases, restores Auto next for Queue/related
videos, and adds staged quality recovery while persisting manual resolution choices.
See [fix verification](FIXES_1_6_11.md).

Version 1.6.10 starts new videos at the highest available source quality with
adaptive downgrade enabled, clears previous quality constraints on manual
selection and respects website quality choices. See
[quality verification](QUALITY_1_6_10.md).
It also keeps the foreground screen awake during active online/offline video,
and releases that request when playback pauses or the activity leaves foreground.

Version 1.6.9 adds a bounded alternate source extraction for recoverable format
and HTTP 403 failures. Video selectors preserve the resolution ceiling, handle
already-muxed and silent video, and reject audio-only fallback selections.
Source inspection accepts real video formats without height metadata. See
[download-source verification](DOWNLOAD_SOURCE_1_6_9.md).

Version 1.6.8 renames the app to Vợ Tui and adds Vietnamese/English UI resources.
Vietnamese is the initial default regardless of the phone language. Choose
Settings → Language (Cài đặt → Ngôn ngữ) to change it; the selection is retained.
Android 13+ also exposes the supported app languages in system settings; older
versions use the app's stored selection. See [language verification](LANGUAGES_1_6_8.md).

Version 1.6.7 extends early player ad filtering to XMLHttpRequest text/JSON
responses, with a bounded per-request cache and preserved native error/binary
behavior. Fetch filtering retains response metadata and rejects unrelated
endpoints. Mobile ad-button detection reuses one scan per content sweep;
successful Skip actions invalidate the cached signal before content handling.
See [ad-filter verification](ADBLOCK_1_6_7.md).

Version 1.6.6 modernizes the native You page, Downloads and action menus.
You has a branded header and Downloads/EQ/About shortcuts; Downloads uses
recycled thumbnail rows, format badges, readable status/progress and filters.
Quick actions, library options and Save/quality choices use rounded bottom
sheets with vector icons and bounded scrolling. The offline player has a
compact back/title/EQ toolbar and cover artwork for MP3. See [UI verification](MODERN_UI_1_6_6.md).

Version 1.6.5 bundles yt-dlp 2026.08.19 instead of relying on the library's
2025.11.12 engine when an online update fails. Update checks run in the
background with connection/read timeouts, a total read deadline, size limits
and SHA-256 verification. Atomic replacement only happens while the engine is
idle. Download errors retain diagnostics and explain DNS, timeout, storage,
unavailable format and restricted-source failures. See [download fix](DOWNLOAD_FIX_1_6_5.md).

Version 1.6.4 adds native EQ for downloaded video/MP3, available from + → EQ,
You → EQ and the offline player. Five bands, presets and custom levels are
saved locally. Effects attach only to the offline player's audio session;
YouTube WebView playback is not equalized in this version.

Version 1.6.3 adds optional APK splits by CPU architecture to avoid bundling
four copies of Python/FFmpeg on one phone. Build with
`gradlew.bat assembleRelease -PsplitApks=true`; choose arm64-v8a for a compatible
64-bit ARM phone. The universal APK remains available; normal debug builds
keep their existing universal packaging.

Version 1.6.2 places an icon + Download action directly after Share on the video
page. Each new video tries the highest quality exposed by the source; manual
quality changes remain available for the current video.

Version 1.6.1 enables player sound by default, removes the Tap to unmute and
mute/volume controls, and uses Android's media volume. You → About describes
the app and includes the dedication: "This App I wrote for my wife and my unborn child".

Version 1.6.0 adds Save To Device and Temporary Save (30 days) for video and MP3.
Video quality comes from each source, including its highest available resolution;
separate video/audio tracks are merged without a resolution cap. Downloads run
in a foreground service with progress, cancellation, retry and offline playback.
Open the manager from + → Downloads. Long-press Save retains the Favorites action.
See [download verification and limits](DOWNLOADS_1_6_0.md).

Version 1.5.7 adds keyword autocomplete below the native search field using
YouTube completions. Suggestions load after a 250ms debounce; tapping a row
opens that search. Stale responses are discarded, with bounded requests and
session caching. See [autocomplete verification](SEARCH_AUTOCOMPLETE_1_5_7.md).

Version 1.5.6 asks for notification permission once, remembering dismissal or
denial across launches. Home now displays local recommendations previously
available only in Library, with titles, lazy thumbnails and ranking reasons.
See [Home and permission verification](HOME_NOTIFICATIONS_1_5_6.md).

Version 1.5.5 makes native keyword search request video results, bringing titled
thumbnail cards ahead of channel/music panels. Missing/broken search thumbnails
get a lazy fallback near the viewport. Library thumbnail requests are coalesced
per video and use weak view references. See [search verification](SEARCH_PERFORMANCE_1_5_5.md).

Version 1.5.4 moves startup database checks, playback library reads and
favorite/subscription writes off the UI thread. Stale reads are rejected across
navigation; saved playback position is protected until resume finishes.
Background UI refresh and unfocused gesture polling stop, with immediate
gesture warmup when focus returns. See [performance verification](PERFORMANCE_1_5_4.md)
and the [optimization plan](PERFORMANCE_PLAN.md).

Version 1.5.3 coalesces UI refresh per frame, avoids unchanged text/icon updates,
caches document-start script policy and debounces library search by 150ms.

Version 1.5.2 fixes watch-page SPA routing and delayed swipe hit-testing. A
warm video rectangle allows fast downward gestures to minimize even on a busy
renderer; normal minimize no longer depends on the fullscreen gesture setting.
Ad handling adds document-start player-response filtering and Vietnamese mobile
Skip recognition. See [verification and limits](PLAYBACK_FIX_1_5_2.md).

Version 1.5.1 standardizes toolbar, player and menu vector icons, adds labeled
rounded watch actions, and compacts the library transport bar.

Version 1.5.0 refreshes the mobile interface: icon navigation with You/quick
actions, compact search, a draggable floating mini-player, thumbnail rows,
playlist-style collection covers, Play all/Shuffle, and per-video overflow
menus. Collection playback prepares the queue atomically and respects search.
The native app retains VoTuibe branding and its existing playback/filtering.

Version 1.4.2 adds swipe down on the video image to minimize playback into the
existing mini-player. Fullscreen center swipe down also minimizes. Playback and
the current video remain in the same WebView; comments and Shorts keep scrolling.

Version 1.4.1 replaces the launcher artwork with the user's latest glossy
ribbon-heart PNG, preserving its transparency and aspect ratio.

Previously named YouTooBee. Version 1.3.2 updates the launcher icon to the
user-supplied ribbon-heart artwork and renames visible app branding to VoTuibe.
The application ID is preserved for updates retaining existing user data.
See [launcher artwork](branding/README.md) for source assets and regeneration.

Version 1.4.0 adds a center swipe down to exit fullscreen and a Similar tab in
Library. Long-press a non-queue history/favorite title to find related videos.
Local recommendations prioritize meaningful viewing and recent interests,
interleave channels and respect hidden videos/channels. Candidate videos are
collected from YouTube pages already browsed; this is a local ranking engine,
not a connection to YouTube's private recommendation service.

Android YouTube-focused enhanced client with app-first navigation, layered filtering, PiP/background playback, a local library, playback customization and runtime hardening. The current rewrite is progressively removing browser-shell coupling while preserving working playback and user data.



## Enhanced-client rewrite — phase 1

The product direction is now a dedicated YouTooBee client rather than a browser-style wrapper. Phase 1 introduces route-aware app chrome and a playback-backend abstraction while preserving the working player underneath.

- `YouTubeRoute` classifies Home, Watch, Shorts, Search, Subscriptions, Playlist and Channel routes.
- `ClientChromePolicy` decides app-bar and playback-action visibility from route/playback state.
- The primary UI now exposes YouTooBee search plus Home / Shorts / Subscriptions / Library navigation; browser back/forward/reload controls are hidden from the user experience.
- `PlaybackBackend` is the stable command boundary for gestures, PiP, MediaSession, queue and sleep-timer actions. `WebViewPlaybackBackend` is the compatibility backend during migration.
- `EnhancedClientDefaults` provides a one-tap baseline without automatically enabling third-party community services.
- See `CLIENT_REWRITE.md` for the decomposition roadmap.

## Enhanced-client rewrite — phase 2

Phase 2 separates browsing from playback so YouTooBee behaves like a dedicated video client instead of replacing the whole page every time a video opens.

- Browse and Player are independent surfaces.
- Watch links and individual Shorts open in the Player Surface.
- Back collapses an expanded player into a persistent mini-player.
- Home/Search/Subscriptions can continue while the current player remains alive.
- Mini-player includes play/pause, expand and close controls plus title/channel metadata.
- Browse and Player have separate filter contexts.
- Browse/player/surface state survive Activity state restoration independently.
- Duplicate mobile-site chrome is hidden by a presentation-only client surface script.
- The mini-player exposes native playback progress and can be dismissed with a horizontal swipe.
- Quality preferences are independent for Wi-Fi/unmetered and mobile/metered connections, and YouTooBee switches the active profile automatically.
- User-facing branding is now **YouTooBee**; package/application ID remains unchanged for compatibility.

## v1.1 focus — session ownership and lifecycle coordination

v1.1 keeps the Shield engine and SQLite schema unchanged while moving two high-risk responsibilities out of `MainActivity`.

- `PlaybackSessionCoordinator` now owns the in-memory playback session, recovery snapshot, estimated position and publication to `PlaybackService`.
- `WebViewLifecycleCoordinator` now owns foreground/background WebView pausing, memory-pressure reactions and playback wake-lock policy.
- `MainActivity` no longer carries duplicate title/channel/video/position/session fields.
- Release stress diagnostics now include `RegressionFixtureHarness` for resume policy, hostile host fixtures, URL normalization and 2,000 deterministic state transitions.
- Process/session restoration still uses the existing snapshot format, so v1.0 data remains compatible.
- SQLite remains schema v2; no migration or user-library reset is required.

### v1.1 continuation — navigation and playback-state hardening

This development pass keeps version `1.1.0` / code `11` unchanged while hardening two boundaries that are easy to regress:

- `NavigationTargetResolver` now owns address-bar/search and shared-text URL resolution.
- `NavigationSecurityPolicy` keeps HTTP(S) inside WebView, blocks executable/local-content schemes, and only delegates supported external schemes when the current page is a trusted YouTube origin.
- `ShieldWebViewClient` sanitizes `intent:` launches by forcing browsable behavior and removing explicit component/package/selector targeting before dispatch.
- blocked navigation diagnostics store only a redacted scheme/host summary, not the full browsing URL.
- `PlaybackProgressPolicy` centralizes position clamping and rate-aware progress prediction for the live session, persisted snapshot and playback service boundary.
- regression fixtures now cover unsafe navigation, address resolution, shared URL extraction, duration clamping and non-1× playback progress.

No SQLite schema, rule-pack schema, library format, preference keys or recovery-snapshot keys were removed.

### v1.1 continuation — Vanced-style playback experience

This pass keeps YouTooBee branding and existing storage formats, but moves the day-to-day playback experience closer to the feature set users expect from enhanced YouTube clients.

- AMOLED mode now applies true-black native surfaces and injects a black player/page surface into supported YouTube pages.
- Compact YouTube chrome hides the browser/address toolbar on trusted YouTube pages by default; tap the status line to reveal it when needed.
- Preferred quality adds Auto, Highest, 2160p, 1440p, 1080p, 720p, 480p, 360p and 240p presets. Quality application is best-effort and fails open if the current YouTube player no longer exposes compatible controls.
- Auto-repeat loops the current video and suppresses queue auto-advance while repeat is enabled.
- Community segment skipping is opt-in and supports sponsor/self-promo/interaction plus intro/outro categories. The client uses the SponsorBlock-compatible SHA-256 hash-prefix lookup and filters returned candidates locally before injection.
- Community segment requests are disabled in Shield Safe Mode, cancelled logically on navigation/settings changes, bounded by time/size limits, and never become required for normal playback.
- The player action row now exposes the active quality preset alongside playback speed, sleep timer and existing YouTooBee controls.
- Shield Dashboard includes community-segment skip counts in session/lifetime Shield statistics.

The application remains `1.1.0` / code `11`, SQLite remains schema v2, and no existing preference/storage keys were removed.

SponsorBlock community data is optional and carries its own data/API license; see `THIRD_PARTY_NOTICES.md` before redistribution.

## v1.0 focus — release architecture and resilience

v1.0 keeps the v0.9 Shield engine, media features and SQLite schema intact while reducing coupling inside `MainActivity` and adding release-oriented diagnostics.

### Player command boundary

`PlayerCommandRouter` now owns registration/unregistration of the `PlaybackService` command broadcast and converts it into a small `(command, position)` callback. `MainActivity` no longer contains BroadcastReceiver plumbing.

### Playback service boundary

`PlaybackServicePublisher` is the only component that publishes WebView playback state into the foreground media service. It centralizes:

- service start/stop policy;
- API-level foreground-service startup;
- metadata extras;
- playback position/duration/rate validation.

This makes future migration toward a service-owned media engine substantially easier because the Activity no longer constructs service intents directly.

### Renderer crash-loop protection

`RendererCrashLoopGuard` persists a bounded 10-minute renderer-exit window.

- Fewer than three renderer exits continue to use normal automatic Activity recreation.
- Three exits inside the window activate a crash-loop guard.
- Automatic playback reload recovery is paused while the guard is active.
- Shield Safe Mode is enabled with a diagnostic reason.
- The native error overlay remains available and the user can explicitly Retry/Home.
- A manual retry clears the renderer guard before recreating the Activity.
- A sufficiently stable session clears stale renderer-exit history.

This prevents a broken WebView/runtime combination from entering an uncontrolled recreate loop.

### Device compatibility policy

`DeviceCompatibilityPolicy` selects a conservative runtime profile from the Android manufacturer/model information. The policy never bypasses Android battery management; it only tunes YouTooBee's own timing:

- foreground WebView resume delay;
- playback-recovery grace padding;
- diagnostic background-caution profile.

The detected profile is stored only in local runtime diagnostics.

### Release stress harness

The Shield Dashboard now exposes **Run release stress test**. `ReleaseStressHarness` is read-only with respect to user library data and exercises:

- 1,000 deterministic playback-health state transitions;
- offline/recovery cycles;
- trusted and hostile URL fixtures;
- queue uniqueness/population invariants;
- SQLite `quick_check` health;
- playback snapshot position clamping.

The result is stored in local runtime diagnostics so repeated device tests can be compared without external telemetry.

### Release build hardening

Release builds now enable:

- R8 minification;
- Android resource shrinking;
- explicit keep rules for `@JavascriptInterface` methods and `VideoShieldBridge`;
- `allowBackup=false` by default for the app's local media/session data.

Debug builds remain straightforward for development and WebView troubleshooting.

## Features retained

### Playback

- Native → HTML5 `PlayerController` command gateway.
- Local resume position.
- Persistent playback speed.
- Sleep Timer.
- Queue / Up Next auto-advance.
- Queue drag reorder, Play Next and append semantics.
- Fullscreen seek/brightness/volume gestures and double-tap seek.
- PiP actions: −10 / Play-Pause / +10.
- MediaSession + rich notification metadata/artwork.
- Network-aware playback recovery and native offline/error overlay.
- Renderer-process recovery and crash-loop guard.

### Native shell & local library

- Home / Subscriptions / Library / Shield navigation.
- Local History with playback position/duration.
- Local Favorites.
- Local Subscriptions independent of Google account state.
- Local Queue.
- Swipe-dismiss mini-player.
- Shield Dashboard.

### Shield Engine 2.0

- Versioned declarative rule packs.
- HTTPS rule-pack updates with validation/size limits.
- Optional automatic rule checks.
- Active + previous rules and rollback.
- Bundled-rule recovery.
- Safe Mode + Compatibility Guard.
- Per-channel allowlist.
- DOM restoration for optional distraction filters.
- Network filtering without blanket blocking `googlevideo.com`.

## Local data model

`videoshield_library.db` remains **database version 2**:

- `history`
- `favorites`
- `subscriptions`
- `play_queue`

v1.0 requires **no SQLite migration** from v0.9.

## Key architecture

- `MainActivity.kt` — WebView shell and UI orchestration.
- `PlayerCommandRouter.kt` — PlaybackService → Activity command boundary.
- `PlaybackServicePublisher.kt` — Activity → PlaybackService state boundary.
- `DeviceCompatibilityPolicy.kt` — conservative device timing profile.
- `RendererCrashLoopGuard.kt` — persistent renderer crash-loop protection.
- `ReleaseStressHarness.kt` — deterministic release-readiness stress checks.
- `PlaybackHealthStateMachine.kt` — deterministic playback/offline/recovery UI state.
- `PlaybackRecoveryController.kt` — bounded recovery watchdog/backoff with device grace padding.
- `DeviceRuntimeMonitor.kt` — screen/power-save/device-idle signals.
- `PlaybackWakeLockController.kt` — bounded screen-off/background playback wake lock.
- `RuntimeDiagnosticsStore.kt` — local lifecycle/device/stress diagnostics.
- `PlaybackService.kt` — MediaSession, notification controls/artwork and stale-session guard.
- `ShieldWebViewClient.kt` — filtering, main-frame errors and renderer handling.
- `LibraryStore.kt` — SQLite library + transactional Queue + integrity checks.

## Build on Windows

Use JDK 25 (as specified in `gradle/gradle-daemon-jvm.properties`) and Android SDK API 37, then run `bootstrap-build.bat` from the project root. The script uses `JAVA_HOME`, or detects Android Studio's bundled JDK when `JAVA_HOME` is unset. The included Gradle Wrapper downloads Gradle 9.6.0 when needed, builds `assembleDebug`, and copies the result to `dist\YouTooBee-v1.1.0-debug.apk`. Configure the SDK path in `local.properties` when building outside Android Studio.

The Android `applicationId` intentionally remains `com.example.videoshield` in v1.1.0 so existing installations and local data remain compatible after the YouTooBee rename.

## Android permissions

YouTooBee v1.0 uses:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS`
- `WAKE_LOCK`

No root, VPN, accessibility service, traffic-capture permission or device-admin permission is required.

## Build

Open the project in Android Studio, or on Windows run:

```bat
bootstrap-build.bat
```

Debug APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Unsigned release APK output (configure a release key before distribution):

```text
app\build\outputs\apk\release\app-release-unsigned.apk
```

Requires Android SDK/API 37; minimum API 26.

## Validation note

`gradlew.bat testDebugUnitTest assembleDebug assembleRelease lintDebug --console=plain --max-workers=2` succeeds with the installed Android API 37 SDK and Android Studio JBR. The latest 1.3.0 build reports 0 Lint errors and 198 warnings. Fourteen JVM tests cover SponsorBlock parsing, recommendations, resume filtering and search; `node tests/adblock-script.cjs` runs twenty-one regression fixtures against the JavaScript actually embedded in the app.

An API 37 emulator has verified real YouTube playback, PiP, a bounded 85-second screen-off run, targeted media-session Pause/Play, speed 1.25×, 720p selection, return to Auto, Repeat across the end of a video, local history/favorites/queue persistence across an app update, automatic advance to a queued video, and one real community outro skip through native SponsorBlock lookup. See [VANCED_PROGRESS.md](VANCED_PROGRESS.md) for evidence and remaining checks. Actual YouTube ad suppression and longer playback on physical devices remain unverified.

## Remaining architectural limitation

The actual media element is still HTML5 `<video>` hosted by WebView in `MainActivity`; `PlaybackService` mirrors and controls that player rather than owning a native media engine. v1.0 makes the boundary cleaner and more resilient, but true process-independent playback would require a later service-owned native playback/extraction layer.


## Emulator UI lag

A separate `YouTooBee Light (4 KB)` device is available in Android Studio Device Manager on this machine. It uses the installed standard Android 37.0 image, Hardware GPU and a 720x1606 display. Select it in the Run device list. The existing Pixel 10 Pro device/data remains available for 16 KB compatibility checks. `start-light-emulator.ps1` starts the lighter AVD when no emulator is running, or applies the smaller display to one running emulator. See `EMULATOR_PERFORMANCE.md` for changes, restoration and evidence limits.

## 1.2.0: personal recommendations

Open Library → For you for local recommendations based on viewing time, favorites and followed channels. Settings provides learning opt-out and a reset control. Candidate videos come from the YouTube pages you browse; this is local ranking rather than a global catalog/AI service. History writes and ranking now run off the UI thread. The network filter also avoids treating search values as ad endpoints. See PERSONALIZATION_PROGRESS.md for evidence and limits.

1.2.0 outputs and verification are recorded in PERSONALIZATION_PROGRESS.md.

## 1.3.0: Continue watching and library controls

Library now provides Continue watching, accent-insensitive title/channel search, video/channel exclusions with restoration through Tune, asynchronous library reads/writes and reusable list rows. Queue ordering remains correct while filtering. See LIBRARY_PROGRESS.md for migration, runtime evidence and limits.

Latest outputs: `dist/YouTooBee-v1.3.0-debug.apk` and `dist/YouTooBee-v1.3.0-release-unsigned.apk`. Final verification: `build-library-final.log`, 14 JVM tests, 21 player fixtures and discovery collector fixtures; Lint 0 errors / 198 warnings. Natural YouTube ad suppression, elimination of emulator lag and physical-device stability remain unverified.

## 1.3.1: paused seek reporting

Seeking paused content now updates native progress without repeatedly reporting idle media. Delayed resume seeks are guarded against navigation, native manual seek and stopped sessions. A live loaded-video check independently matched WebView 30 seconds to persisted native 30,000 ms. See PLAYBACK_REPORT_PROGRESS.md for scope and limits.

Current outputs: `dist/YouTooBee-v1.3.1-debug.apk` and `dist/YouTooBee-v1.3.1-release-unsigned.apk`. `build-resume-report-final.log` passes with 14 JVM tests, 23 player fixtures and Lint 0 errors / 198 warnings. The broader ad/device verification limits remain.

The current 1.3.1 emulator also passed bounded screen-off and loaded-player PiP checks, with independent Android power/service/window evidence. See RUNTIME_1_3_1.md for the transient unloaded-media observation, test cleanup and exact limits. Controlled ad-host filtering remains distinct from unverified natural YouTube ad suppression.

CONTROL_RELEASE_AUDIT_1_3_1.md records current native 720p/1.25×/Repeat checks, an actual non-debuggable R8 release playback/Pause test and a fresh disabled-Shield negative ad control. The goal remains incomplete at natural-ad and real-device verification. `dist/YouTooBee-v1.3.1-release-qa.apk` uses a debug certificate for testing; production distribution requires a release certificate. The emulator was restored to the normal debug APK with Shield enabled.
