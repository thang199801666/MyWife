# Vợ Tui 1.6.12 — watch title visibility

The mobile site's topbar is hidden because the app provides native navigation.
Its fixed sticky player still retained `top: 48px`, while the in-flow placeholder
reserved only the video height. This put the player bottom at 288 CSS px and the
title at 252–278 px, completely covering the heading.

The expanded surface now resets the sticky player container's top offset to zero.
Video dimensions and the website's placeholder remain unchanged. The rule applies
only in expanded mode, preserving the separate mini-player and PiP styles.

Verification:

- Live debug WebView, Rick Astley watch page: player/video bounds 0–240 px,
  title bounds 252–278 px; 12 px clearance.
- Screenshot with actual video frames shows the title below the player.
- Applying mini then expanded scripts retained those bounds and clearance.
- `tests/player-title-layout.js` checks actual DOM bounds and fails on overlap
  or video extending outside the player; requires an expanded, loaded watch page.
- Existing surface-transition fixtures and all 45 player regression fixtures pass.

The installed debug build also passes the DOM bounds check after using the native
Minimize and Expand controls: player/video bottom 240 px, title top 252 px.
The website initially loaded the watch-content container slowly while release
optimization was running; the check was repeated successfully after it loaded.

Release build and lint-vital checks succeeded. The ARM64-only APK is aligned for
16 KiB pages and verified with APK signature schemes v2/v3, using the existing
development certificate (not a production publishing key).

Artifact: `dist/VoTui-v1.6.12-release-arm64-v8a.apk`, 54,344,788 bytes.
SHA-256: `470C8F73AAEE23AD03A66B4C5D65D582BC03DA03A62826B7447387BEB75E4297`.
