# VoTuibe 1.4.2: swipe down to mini-player

Swipe down on the video image in a regular watch page and release after at least
64dp of predominantly vertical motion. The player follows the drag, then uses
the existing mini-player without reloading the video. Short/canceled gestures
do not minimize. Comments, suggested-video lists, horizontal movements, the
bottom seek-control strip and Shorts retain their existing touch behavior.
Additional pointers cancel the pending minimize gesture. The Player gestures
switch in Settings controls this behavior.

In fullscreen, a center downward swipe now exits fullscreen and minimizes the
player. Left/right vertical gestures retain brightness/volume controls. Native
Back still exits fullscreen normally.

The mini-player has a dedicated video layout above its native controls. YouTube
can pause when resized into a small viewport; the existing playback intent guard
now also covers mini-player mode. Native Pause/Toggle remain authoritative.
The guard does not block ad or ended-media pause transitions and is removed
when expanding the player. The original WebView/video remains alive.

Validation: debug and minified unsigned release builds, lint, 21 JVM tests and
25 player JavaScript fixtures passed. On emulator-5554:

- Short swipe retained the expanded player.
- Long swipe on the video created miniPlayerChrome and a 128dp player surface.
- A swipe outside the video retained the expanded player.
- After the viewport settled, the same video element was still playing at
  158.582205s with a 72px-tall image above the native mini-player controls.
- Native mini-player Pause changed paused to true and cleared playback intent.
- Fullscreen center swipe returned to the mini-player; the same video continued
  playing at 4.983605s after the fullscreen transition.

Artifacts: `dist/VoTuibe-v1.4.2-debug.apk` (installed on the emulator) and
`dist/VoTuibe-v1.4.2-release-unsigned.apk` (requires signing).
Screenshot: `branding/mini-player-1.4.2.png`.
