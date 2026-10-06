# Current-version runtime verification: YouTooBee 1.3.1

This pass revalidated the installed 1.3.1 emulator build. It adds runtime evidence and safer test helpers; the app source and APK are unchanged from `build-resume-report-final.log`.

## Screen off

Loaded Rick Astley content started at 31.127655 seconds, playing=true and readyState=4. After a 30-second explicit wait plus orchestration time, it was at 84.970183 seconds, still playing and loaded. Android's subsequent power dump reported `mWakefulness=Asleep`, a live `VideoShield:Playback` partial wake lock and foreground PlaybackService. The power transition began in Dozing; the observation establishes bounded playback through a screen-off interval, not a controlled exactly-30-second duration or long-term battery/process survival.

Evidence: `dist/background-1.3.1-before.json`, `dist/background-1.3.1-after.json`, `dist/background-1.3.1-power-before.txt`, `dist/background-1.3.1-power-after.txt`, `dist/background-1.3.1-service.txt`.

## PiP

The initial PiP check reached Android pinned mode but caught media with readyState=0 / unknown duration. That single snapshot cannot prove continuous playback. A subsequent Home action during seek buffering did not enter PiP; native automatic entry requires its session's playing flag. Playback continued in the background in that run, but it is not PiP evidence.

After bringing the loaded player forward and independently confirming native playing=true, a new Home transition yielded six successive samples with PiP style present, playback intent true, readyState=4, paused=false and no PiP resume error. The video advanced from 120.478316 to 127.454083 seconds. Android independently reported `mLastReportedPictureInPictureMode=true` and pinned window mode.

Evidence: `dist/pip-1.3.1-confirmed-samples.json`, `dist/pip-1.3.1-confirmed-window.txt`, `dist/pip-1.3.1-foreground-native.xml`. Earlier observations are retained with `before`, `after`, `repeat` and `foreground` filenames rather than treated as passing controls.

## Cleanup and helper repair

The initial restore helper requested 9.058967 seconds during media replacement but returned 132.040444 seconds. That result did not prove restoration. The helper now waits for loaded media, verifies the requested position and keeps its saved state when verification fails. The original position was explicitly recovered, then the repeated test's final restore independently confirmed 9.058967 seconds / paused=true. Temporary CDP forwarding was removed. Actual test playback can contribute watch-history/habit signals; no user's local profile was erased.

Evidence: `dist/background-1.3.1-recovered-position.json`, `dist/background-1.3.1-restored-confirmed.json`. Assertions combining background and PiP results are in `dist/background-pip-1.3.1-check.json`.

## Network filtering

A controlled public `googleads.g.doubleclick.net/pagead/id` fetch completed with an opaque response, and native persisted blocked-request count increased from 735 to 736. The test polls briefly for asynchronous preference persistence; an immediate disk read did not establish the first request's increment. This validates ad-host interception for the current WebView build, not a naturally served YouTube ad episode.

Evidence: `dist/network-1.3.1-check.json`.

## Remaining goal gaps

No natural YouTube ad positive control with Shield disabled and corresponding enabled comparison was obtained. Physical-device performance, long-running playback and process/battery restrictions remain unverified. The original emulator jank measurements are not superseded by these playback checks. Current automatic PiP entry while seeking/buffering is limited by native playing-state eligibility. These gaps prevent a broad Vanced-equivalence or fully-stable claim.
