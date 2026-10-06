# YouTooBee 1.3.0 — library and recommendation controls

## Changes

- Continue watching: recent history with at least 30 seconds watched, more than 30 seconds remaining and less than 95% completed. Unknown/live duration and completed videos are excluded. Opening an item uses the existing player resume path.
- Search titles and channels within each library section. Case, Vietnamese accents and `đ` are normalized; every query word must match. Search filters loaded rows without database work on each keystroke.
- Suggestion feedback: hide one video or its channel. A hidden channel overrides follow/favorite/viewing signals for candidate ranking. Channel identity currently follows the exact normalized channel text/handle supplied by discovery; differently named aliases are not merged automatically.
- Tune: uncheck hidden channels and Save to restore them; Restore videos removes individual-video exclusions. Reset learned preferences also clears both types of exclusion. Hidden videos are bounded to 2,000 and channels to 500, oldest first.
- Library loading and mutations use one background executor. One favorite-ID query replaces per-row favorite lookups. Generation checks discard stale loads when switching tabs. Errors are shown rather than silently treated as empty data.
- The mini player avoids redundant text/progress updates while paused.
- List rows reuse their container and text views. Binding clears prior drag/click state and action views before applying the new row. Queue arrows and drag targets use full queue positions while search is active.
- Clear actions now explain their effect and require confirmation inside the app. Continue's Clear removes unfinished entries from local watch history.
- History retains known title/channel metadata when a loading page reports a generic title or blank channel; genuine title/progress changes still update normally. A JVM regression verifies no metadata crosses video IDs. This does not reconstruct metadata already overwritten before the fix.
- SQLite schema 4 adds `blocked_channels` without deleting existing tables. Version 13 / version name 1.3.0.

## Verification

`build-library-final.log`: `testDebugUnitTest assembleDebug assembleRelease lintDebug` succeeded. 14 JVM tests (including resume boundaries, accent-insensitive multiword search and channel exclusion overriding positive signals), 21 embedded player-script fixtures, and discovery collector fixtures passed. Lint: 0 errors, 198 warnings; existing and additional UI/style warnings remain.

Actual API 37 / 4 KB emulator checks:

- Schema 3 → 4, `quick_check=ok`, all prior section counts preserved. Candidate count grew from 29 to 30 through ongoing discovery. See `dist/library-upgrade-before.json` and `dist/library-upgrade-after.json`.
- Continue displayed Billie Jean and Careless Whisper, excluded the completed/near-end Rick Astley and Beat It history entries: `dist/continue-new.xml`.
- Search `Billie` displayed only the matching library row: `dist/search-checked.xml`.
- Hide channel removed the first candidate; Tune showed its saved exclusion; uncheck and Save restored it: `dist/feedback-hidden.xml`, `dist/tune-dialog.xml`, `dist/feedback-restored.xml`.
- Hide video and Restore videos removed/restored the same candidate: `dist/video-hidden-checked.xml`, `dist/video-restored.xml`.
- Two temporary queue entries were added through native buttons. Filter `Billie` retained the correct up/down availability; moving it up changed the full queue order: `dist/queue-filtered.xml`, `dist/queue-moved-filter.xml`, `dist/queue-reordered.xml`. Both temporary entries were then removed; the prior empty queue was restored.
- The exact final debug APK was installed after the final title/drag-target/click-state safety changes and mini-player update guard and history metadata preservation. Earlier runtime UI evidence covers the same behavior, before those small final changes.

Raw QA database copies are temporary and removed after validation. Counts-only evidence is retained.

## Outputs and limits

- `dist/YouTooBee-v1.3.0-debug.apk`
- `dist/YouTooBee-v1.3.0-release-unsigned.apk` (must be signed before distribution)

These changes reduce work performed by the library UI; they do not establish a measured reduction in whole-emulator jank. Recommendations remain local ranking of discovered candidates, not a global catalog or semantic AI service. A naturally served YouTube ad positive control, long-duration physical-device playback and phone performance are still unverified. This release does not establish complete Vanced equivalence or process-independent native playback.
