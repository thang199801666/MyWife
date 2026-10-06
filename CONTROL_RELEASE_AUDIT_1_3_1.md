# Controls, release R8 and completion audit — 1.3.1

The previous goal turn is progress: it produced independent current-version screen-off/PiP evidence. This turn revalidates additional playback paths and the minified release; app source is unchanged.

## Current-version controls

Native UI selected Wi-Fi 720p, 1.25× and Repeat. After the quality-change loading interval, actual media was 1280×720, rate=1.25, loop=true and readyState=4. Repeat crossed 211.568823 / 213.061 seconds to 2.765342 seconds, playing with loop still true. Native controls were restored to Auto, 1× and Repeat off; the paused position was restored to 9.058967 seconds. Auto released the preferred quality constraint; it does not imply the currently buffered 720p representation immediately changes.

Evidence: `dist/controls-1.3.1-loaded.json`, `dist/controls-1.3.1-repeat.json`, `dist/controls-1.3.1-final.json`, assertions in `dist/controls-1.3.1-check.json`.

## R8 release test

The existing unsigned minified release was signed with the existing Android debug key solely for emulator QA, creating `dist/YouTooBee-v1.3.1-release-qa.apk`. Its v2/v3 signature verified. No new signing key was created or copied into the project. This APK is not a production-signed distribution.

After installation, package flags and failed `run-as` confirmed non-debuggable release behavior. No WebView debug socket for the release app PID was present. The app's MediaSession reported the correct Rick Astley title and progressed from 20,295 to 59,120 ms while PLAYING. Targeted `media_session monitor YouTooBeePlayback` Pause reached PAUSED at 165,051 ms. These native updates prove an exercised bridge/control path survives R8, rather than relying only on a successful release compilation.

A global media-key dispatch did not pause it: Android reported no selected media-button session. This was not treated as passing control evidence. The targeted transport command passed; general headset/media-key routing remains a separate device/environment check.

Evidence: `dist/release-1.3.1-package.txt`, `dist/release-1.3.1-flags-check.json`, native media dumps and `dist/release-1.3.1-runtime-check.json`. Debug 1.3.1 was reinstalled without data clearing afterward.

## Fresh natural-ad control

Shield was disabled through native Settings, confirmed in preferences and the page configuration. A loaded `iphone` search had 57 watch links and no populated ad nodes matching the checked selectors. Its first real watch link, `5TYCH8nrmLE`, was opened; 20 samples spanning startup into loaded content exposed no player-ad class or visible Skip button. The page subsequently showed loaded content at 26.73 seconds with Shield still disabled. These are bounded negative observations, not proof that the environment never serves ads and not proof of suppression.

Evidence: `dist/audit-1.3.1-feed-disabled.json`, `dist/audit-1.3.1-watch-disabled.json`, `dist/audit-1.3.1-watch-loaded-disabled.json` and native preference evidence. There is still no positive naturally served ad episode to compare with Shield enabled.

## Requirement audit

| Requirement | Authoritative evidence | Outcome / limit |
| --- | --- | --- |
| Build debug and minified release | `build-resume-report-final.log`, 14 JVM tests, Lint 0 errors / 198 warnings | Pass for current source/build; production signing remains user-owned distribution work |
| Preserve JS bridge after R8 | Non-debuggable release MediaSession title/advancing state and targeted Pause | Pass for exercised playback bridge; not every release callback or stress scenario |
| Network/DOM/player ad filtering | Current ad-host count 735→736; 23 embedded player fixtures | Controlled interception and selected transitions pass; natural ad suppression unproven |
| Stable normal playback | Loaded real media, release native progression, paused native seek match | Bounded observations pass; long-term and physical-device stability missing |
| Background/screen off | Asleep + foreground service + wake lock, 31.13→84.97 seconds (`RUNTIME_1_3_1.md`) | Bounded emulator pass; OEM battery/process restrictions missing |
| PiP | Android pinned state + six loaded playing samples (`RUNTIME_1_3_1.md`) | Loaded-player bounded pass; first transient unload and buffering-entry limitation retained |
| Quality / speed / repeat | Native settings, decoded 720p, 1.25×, real end-to-start loop | Current-version bounded pass; network/OEM matrix not established |
| Library / habit memory / suggestions | Schema 3→4 quick check/count preservation, native search/feedback/queue checks and ranking tests (`LIBRARY_PROGRESS.md`) | Selected local behaviors pass; relevance needs user feedback, channel aliases are not merged |
| Resolve whole-emulator lag | `EMULATOR_PERFORMANCE.md` baseline and settled samples | Residual jank remains; no claim of elimination or physical-phone performance |
| Full release/device checklist | `RELEASE_CHECKLIST.md` recommended OEM matrix, account/cookie, recovery and long-run gates | Not fully verified; no release-ready or broad Vanced-equivalence claim |

## Cleanup and blocked audit

Shield was restored through native Settings and independently confirmed as true in persisted preferences and the player configuration. Final state is Rick Astley at 9.058967 seconds, paused, 1× and Repeat off (`dist/audit-1.3.1-final-state.json`). Home was restored as the browse destination. Test playback/search may add local history/habit/candidate signals; the profile was not reset. CDP forwarding and interactive media monitor were closed. The installed build is again debug 1.3.1. Export hashes are in `dist/final-audit-1.3.1-hashes.json`.

The same missing natural-ad positive control persisted in the 1.3.0 development turn, 1.3.1 resume turn, current-version background/PiP turn and this audit. Independent implementation and useful available verification have been completed; more synthetic fixtures or empty-ad snapshots would not prove the outstanding real-ad requirement. Only the emulator is connected; no physical-device matrix is available. Completion is unproven and the goal is at an external verification impasse. Continue when a device/environment actually supplies a naturally served ad with Shield off, enabling the corresponding enabled comparison, and when real-device performance/stability tests can be run. No scope is removed from the goal.
