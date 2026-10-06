# VoTuibe 1.5.2: minimize and mobile ads

The phone screenshot shows a sponsored video still playing, with the native
watch action strip absent. Two routing/hit-testing gaps were addressed:

- WebView history changes now update native routes. Browse navigation also
  captures playback links and observes pushState/replaceState/popstate, with
  a lightweight URL fallback. Playback URLs enter the native player; duplicate
  callbacks do not reload the same requested video.
- Downward minimize is independent of the fullscreen gesture preference.
  Visible video bounds are warmed every 500ms only while an expanded watch
  page is eligible. URL, width, age and native scroll offset validate the
  snapshot. This fixes a reproduced failed swipe caused by delayed JavaScript
  hit-testing. Controls, comments, horizontal scrubbing and multitouch retain
  their own interactions.

Ad changes:

- AndroidX WebKit document-start injection installs a small response filter
  before website JavaScript. Only explicit player ad fields are removed from
  identifiable player responses; content formats and metadata are preserved.
  Initial player response assignment, parsed responses and same-origin
  player/next fetch responses are covered. Other origins and unrelated JSON
  are excluded. Disabled protection, safe mode and channel exceptions apply.
- A visible localized Skip ad/Bỏ qua button inside the video rectangle can
  trigger skipping without the old desktop ad class. This mobile signal never
  permits seeking shared media to its end. Existing confirmed desktop handling
  and network filtering remain active.
- Document-start support is feature checked. Older WebViews retain the late
  injection/DOM fallback and cannot offer the same early filtering.

API reference:
https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E)

Validation:

- Debug/release builds and lint passed; 23 JVM tests passed, lint errors: 0.
- 28 player JavaScript regression fixtures passed, including Vietnamese mobile
  Skip, out-of-video false positives and safe mode. Separate early-filter and
  SPA navigation fixtures passed.
- A live Home WebView pushState to watch?v=9bZkp7q19f0&hl=vi produced a native
  watch player and returned the browse WebView to Home.
- On that live player, earlyInstalled=true, removedFields=3 and no initial ad
  fields remained. This confirms filtering ran; it is not a natural-ad positive
  control proving all ad delivery variants are eliminated.
- Fast 300ms downward swipes minimized both paused and playing media after
  warming bounds. The playing test preserved the same video element; after
  pause the mini viewport was 240x191 at 114.134378s. Expansion retained that
  position. A swipe starting in comments left miniStyle=false.
- Screenshot: branding/ui-minimize-1.5.2.png. Final playback was left paused.
  The debug forward was removed. Only emulator-5554 was connected; the phone
  in the screenshot and its exact video were not available for verification.

Install dist/VoTuibe-v1.5.2-debug.apk over the existing app on the phone.
SHA256: CF48FE52AFF4977EC1B9386528ED9E1D208B3FC2C98A26CEB390678C3350363B
Release APK is unsigned: dist/VoTuibe-v1.5.2-release-unsigned.apk.
