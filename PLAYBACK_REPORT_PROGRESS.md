# YouTooBee 1.3.1 — paused seek reporting and resume safety

## Changes

Paused content position changes of at least 250 ms now cause a bridge report at the next existing bounded sweep. Unchanged paused media generates no repeated position reports. The unloaded-media and ad guards still run before content reporting, so ad seeks cannot overwrite the content position.

A delayed automatic resume checks its navigation generation, active session, current video ID and current WebView route before issuing the seek. Navigation, native manual seek, fullscreen gesture seek and Stop invalidate the pending generation. The original saved-position eligibility and 450 ms scheduling remain unchanged. This prevents an old seek from applying to a different clip or overriding a native manual seek; site-owned controls are not explicitly wired to this generation counter.

Version code 14 / version name 1.3.1. All 1.3.0 library/recommendation controls remain present.

## Verification

- `build-resume-report-final.log`: debug/release builds, 14 JVM tests and Lint completed successfully; Lint has 0 errors / 198 warnings.
- `node tests/adblock-script.cjs`: 23 embedded-player fixtures passed, including paused seeks, idle-report suppression and paused ad progress exclusion.
- Real API 37 / 4 KB emulator: the installed 1.3.1 player API paused Rick Astley and sought to 30 seconds. Loaded media reported 30 seconds, readyState 4, paused=true and zero script errors (`dist/paused-seek-live.json`). The app's persisted snapshot independently reported position_ms=30000 and playing=false (`dist/paused-seek-native.xml`, asserted in `dist/paused-seek-check.json`).
- The probe returned to its immediately prior position, 9.058967 seconds, and left playback paused (`dist/paused-seek-restored.json`). The brief app startup playback may contribute local habit/history signals. No profile reset was performed.
- Package inspection confirmed version 14 / 1.3.1. Temporary CDP forwarding was removed.

The live check proves this paused seek reached native persistence for one loaded clip. Rapid-navigation resume races and site-owned manual seeks were not exercised live. It does not establish long-running playback, natural YouTube ad suppression or physical-device responsiveness.

## Outputs

- `dist/YouTooBee-v1.3.1-debug.apk`
- `dist/YouTooBee-v1.3.1-release-unsigned.apk` (requires release signing)
- Hashes: `dist/playback-report-release-hashes.json`
