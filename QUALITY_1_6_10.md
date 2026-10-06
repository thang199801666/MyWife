# Vợ Tui 1.6.10 — adaptive startup and manual resolution

- Each newly opened video starts in “Auto • start highest” mode. The source's
  highest listed quality is requested once; the lower bound remains the lowest
  available quality so YouTube's adaptive player can respond to bandwidth.
- If playback makes no progress for at least four seconds, readyState <= 2
  and less than one second is buffered, the controller lowers the ceiling one
  source step. Further reductions have a 15-second cooldown. Pause, seek,
  ended video and long background scheduling gaps do not count as network stalls.
- Manual app choices clear the previous quality range before applying a fixed
  resolution. Unsupported resolutions select the nearest available lower one.
  144p and adaptive choices are included in the app selector and Settings.
- Website manual choices (including Auto) suspend the app's adaptive overrides.
  Selecting the app's adaptive option again explicitly resets that session.
  Unrelated preference changes preserve manual choices and adaptive reductions.
- Video switching resets the adaptive session. Quality commands do not reload,
  seek, pause, mute or replace the source video. Decoder frames already buffered
  at the previous resolution can still appear briefly during a switch.

## Verification

- 39 deterministic fixtures execute the embedded JavaScript, including adaptive
  startup, stalls/cooldown, manual 720p, website manual/Auto, unrelated settings,
  reselecting adaptive and source fallback. All passed.
- Locale parity: 369 paired strings passed.
- Final build passed in 5m 37s: 49 unit tests, zero lint errors.
- Release dist/VoTui-v1.6.10-release-arm64-v8a.apk: 54,332,500 bytes;
  SHA256 869E141610CB2B1F5ED4BEDAF2942735C6C26F682883ADA322CE2FD28EBDF6F8.
  ARM64 only, label Vợ Tui, version 1.6.10 (37), non-debuggable. 16 KiB alignment
  and v2/v3 signatures verified; signed with the existing development/test key.
- Live API 37 WebView check on dQw4w9WgXcQ: highest startup requested hd2160;
  adaptive target fell to hd1440 during loading, with manual=false. An explicit
  hd1080 selection decoded at 1920x1080. Selecting hd720 then decoded at
  1280x720; playback progressed from 56.591713 s to 61.388698 s without pausing.
  Evidence: quality-live-1080-1.6.10.json and quality-live-720-1.6.10.json.
- Recovery of the QA emulator was needed: a streaming install stayed pending,
  then a duplicate pending-install error occurred. Restart preserved AVD data
  and a non-streaming install succeeded. The AVD's old DNS 192.168.2.253 timed
  out; using the current host connection's DNS restored YouTube connectivity.
  An earlier stalled live page was not accepted as proof of a quality switch.

## Screen stays on during video

- The foreground Main video activity now sets FLAG_KEEP_SCREEN_ON while its
  video session is playing, including its mini-player and fullscreen surface.
  Pause, closing the player, leaving the activity and destruction clear the flag.
  Background/PiP does not hold the screen awake.
- Offline VideoView start/pause/completion/error use the same window controller.
  Audio-only playback does not request this activity flag. No system screen
  timeout or brightness preference is changed.
- Runtime Main activity checks passed: KEEP_SCREEN_ON and mHoldScreenWindow
  were present during playback; after the app pause control, the flag was
  absent and mHoldScreenWindow was null. Evidence: screen-awake-playing-1.6.10.txt
  and screen-awake-paused-1.6.10.txt. Offline lifecycle code compiled/linted;
  offline runtime and a physical phone were not separately tested in this pass.
  Reference:
  [Android keep-screen-on guidance](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).

The app uses the mobile website player capabilities, not the public iframe
quality API. [Google's revision history](https://developers.google.com/youtube/iframe_api_revision_history)
documents that the public iframe quality setters are no longer supported.
Live checks therefore inspect both requested quality and decoded video dimensions.
