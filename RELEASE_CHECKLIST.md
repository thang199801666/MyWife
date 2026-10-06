# YouTooBee v1.1.0 Release Checklist

## Build

- Install Android SDK/API 37 and the JDK required by the configured Android Gradle Plugin.
- Run `bootstrap-build.bat` or Gradle wrapper generation/bootstrap as documented.
- Run debug build and resolve all compile/lint errors.
- Configure a private release signing key outside source control.
- Build the minified release variant and verify the WebView JavaScript bridge after R8.

## Functional smoke test

- YouTube Home/Search/Watch navigation.
- Open a Watch/Shorts video from Home/Search, minimize it, continue browsing, then expand it again without losing the playback session.
- Verify Back from expanded Watch collapses to mini-player before leaving the browse shell; swipe the mini-player horizontally to dismiss it.
- Rotate/recreate the Activity with Browse open and Player expanded/mini; verify both surface states restore independently.
- Login/cookies survive Activity recreation.
- Ad/network filters do not block normal video segments.
- Shield ON/OFF, Safe Mode, channel allowlist and rule rollback.
- Fullscreen, double-tap seek, brightness/volume gestures.
- PiP actions and notification controls.
- Background/screen-off playback.
- History/Favorites/Subscriptions/Queue persistence.
- Queue drag reorder, Play Next and auto-advance.
- Resume timestamp, playback speed and sleep timer.
- Verify 1× / 1.25× / 1.5× / 2× fullscreen progress/seek estimation stays synchronized and clamps at duration.
- From a trusted YouTube page, verify supported external links can open; from an arbitrary web page, verify custom-scheme and `intent:` launches are blocked.
- Verify `javascript:`, `file:`, `content:`, `data:` and `blob:` top-level navigation attempts are blocked.
- Enable AMOLED mode and verify the native shell plus YouTube/player backgrounds are true black without making controls unreadable.
- Enable compact YouTube chrome and verify the browser toolbar hides on trusted YouTube pages; tap the status line to reveal it and verify non-YouTube pages keep browser chrome available.
- Test Wi-Fi/unmetered and mobile/metered quality profiles independently at Auto, Highest, 1080p and 720p; switch network type during playback and verify the active profile changes without interrupting playback.
- Enable Auto repeat and verify the current video loops without advancing Queue; disable repeat and verify normal Queue auto-advance returns.
- Enable community sponsor skip and intro/outro skip independently. Verify matching segments seek once, nonmatching categories are ignored, and playback continues normally when the segment service is unavailable.
- Enter Shield Safe Mode and verify no community-segment network lookup/skip is active.

## Recovery stress

- Run **Runtime / database self-test** in Shield Dashboard.
- Run **Release stress test** and require PASS.
- Toggle airplane mode during playback and restore connectivity.
- Kill/restart Activity while a playback snapshot exists.
- Remove app task while playback notification is visible.
- Simulate WebView renderer termination if developer tooling permits.
- Confirm repeated renderer exits do not create an infinite recreate loop.

## Device matrix

Minimum recommended physical-device pass:

- Google/Pixel-like Android build.
- Samsung device.
- Xiaomi/Redmi/POCO or another aggressively customized Android build.
- One additional OEM device.

For each device test foreground playback, screen-off playback, PiP, notification actions, process recreation and network recovery.

## Release privacy/security

- `usesCleartextTraffic=false` remains enabled.
- `allowBackup=false` remains enabled unless explicitly reconsidered.
- No debug WebView flags or test endpoints in release.
- Remote rule URL must be HTTPS.
- Community segment skipping remains opt-in; verify the lookup uses the SHA-256 hash-prefix endpoint and locally rejects candidates whose full hash/video id do not match.
- Community lookup must remain HTTPS-only, response-size/time bounded, and nonessential to playback.
- Keep the SponsorBlock attribution/CC BY-NC-SA 4.0 notice visible in the distribution and review licensing before any commercial release.
- Verify R8 keep rule for `@JavascriptInterface` methods.
- Do not ship signing keys, credentials, cookies, local databases or test account data.

## Distribution note

YouTube can change its player, DOM and anti-ad-block behavior independently of YouTooBee. Treat the remote rule-pack/rollback path as a compatibility maintenance mechanism, and test updates before broad distribution.
