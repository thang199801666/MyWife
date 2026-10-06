# YouTooBee 1.2.0 — local recommendations

- Learn from measured viewing time, favorites and local channel subscriptions. Elapsed viewing is bounded; seeks, paused intervals, stalled playback and long reporting gaps cannot inflate it from a large playback position alone. Playback speed is accounted for.
- Library → For you ranks previously unseen videos discovered on YouTube Home/Search/related cards. Reasons are shown, with Not interested and Add to Queue actions. This is local title/channel matching with recency and channel diversity, not a guarantee of semantic interest detection. The pool is limited to pages actually visited, not a global catalog search.
- Settings → Learn viewing habits locally controls collection/learning. Watch history must also be enabled. Clear learned preferences deletes learned viewing time, discovered candidates and feedback; explicit favorites and subscriptions stay. Clearing History also clears learned data and prevents old queued history writes from reappearing.
- The preference profile stays in the app's local SQLite database. No new profile upload service or account requirement is introduced. YouTube browsing retains its normal network behavior.
- History, viewing-signal writes, candidate parsing/storage and recommendation ranking run outside the UI thread. SQLite WAL allows readers alongside writes. Discovery scans are bounded to 100 cards / 50 videos every 20 seconds and exclude recognized ad containers. Stored candidate and interest pools are limited to 1000 rows.
- Network path rules now match endpoints rather than arbitrary URL/search values. Explicit query rules match query tokens independently of their order. Media-host protection checks the actual googlevideo.com domain boundary. No media extraction or blanket media-host blocking was introduced.

## Validation

`build-personalization-release.log`: debug/release build, JVM tests and Lint succeeded. Ten JVM tests cover community parsing, viewing-time accounting, ranking/diversity/dismissed exclusions, payload canonicalization and URL matching. `node tests/adblock-script.cjs`: 21 embedded-player fixtures pass. `node tests/discovery-script.cjs`: collector exclusions, duplicate handling, opt-out, interval retry and single installation pass.

Real emulator update: database schema 2 → 3, quick_check=ok, all prior history/favorites/queue/subscription keys preserved. Native writes recorded 78,733 ms of measured viewing and 28 discovered candidates in the captured check. `dist/personalization-database-check.json` records counts/preservation only; private database QA copies were removed afterward. These are test-playback signals; normal viewing and explicit feedback will change rankings.

`dist/personalization-for-you-final.xml` and `.png` show actual native results with interest explanations. Screenshot layout was visually inspected. The new native view returned suggestions from real discovered videos, without fixture injection.

A controlled WebView fetch to googleads.g.doubleclick.net raised the blocked network counter from 381 to 382; this proves interception for that test request, not suppression of a naturally served YouTube ad. The final APK was installed successfully after an Android emulator management-command stall required Cold Boot. The cause of that stall was not established; it cannot be attributed to this app. Physical-device stability, notification/background behavior for this version, and naturally served ad comparisons remain to be checked. Residual emulator jank documented in EMULATOR_PERFORMANCE.md is not claimed resolved.

Debug install: `dist/YouTooBee-v1.2.0-debug.apk`. Release output: `dist/YouTooBee-v1.2.0-release-unsigned.apk` (requires a signing key).
