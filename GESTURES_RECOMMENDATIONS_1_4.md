# VoTuibe 1.4.0: gestures and local suggestions

## Controls

Fullscreen: horizontal swipe previews a seek and commits on release; left-side
vertical swipe adjusts brightness; right-side vertical swipe adjusts volume.
Center downward swipe exits fullscreen after 72dp or 22% of height, whichever
is larger. Short swipes stay in fullscreen. Center taps remain with the player;
double-tap seeking uses the outer sides. Cancellation and additional pointers
discard pending seeks and exit gestures.

Library: For you uses local viewing interests. Similar uses the current clip,
or latest history item when there is no current clip. Long-press a non-queue
history/favorite title to select that clip as the similarity seed. The selection
survives activity recreation. Similar does not expose the destructive Clear
button. Existing Tune controls hide videos/channels and restore suggestions.

## Ranking

Use actual viewing time, favorites and followed channels. Ignore accidental
opens under 30 seconds unless a short clip was substantially watched (at least
5 seconds and half its duration). Recent interests and higher watched fractions
receive more weight. Interleave available channels while retaining the existing
four-items-per-channel cap. Similar requires a shared channel or multiple title
terms (one is sufficient only for a single-term seed); exclude seen, dismissed
and blocked items.

This is a local text/channel ranking baseline. It ranks candidates collected
from already-browsed YouTube pages, does not query a global video catalog, and
does not reproduce YouTube's private recommendation model. Recommendations need
candidate pages and viewing evidence to become useful. Viewing fraction uses
wall-clock watch time and is approximate when playback speed differs from 1x.

## Validation

Debug/release builds and lint passed; 19 JVM tests, 23 existing player fixtures
and the discovery fixture passed. On emulator-5554, a short center swipe retained fullscreen, a longer
swipe exited, and the paused video remained at 136.208918 seconds. Long-pressing
Billie Jean in History opened Similar with live candidates and reasons naming
that seed; Clear was absent. That check exposed a one-word artist collision,
fixed with a stronger similarity threshold and a regression test.
For you also rendered live Rick Astley candidates with explanations drawn from
the existing viewing profile. Test playback was left paused.

Artifacts: `dist/VoTuibe-v1.4.0-debug.apk` and
`dist/VoTuibe-v1.4.0-release-unsigned.apk`. The latter requires signing before
installation. No library reset or application ID change.
