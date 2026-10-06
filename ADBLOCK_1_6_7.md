# VoTuibe 1.6.7 — early ad transport and scan improvements

## Changes

- Add document-start XMLHttpRequest responseText/response filtering for complete,
  successful, same-origin `/youtubei/v1/player` and `/youtubei/v1/next` responses.
  Only structurally recognized player objects lose explicit playerAds,
  adPlacements, adSlots and adBreakHeartbeatParams. Stream URLs, title, video ID,
  formats and playability data remain intact.
- Text is parsed at most once per response body, capped at 4 MiB, with a WeakMap
  cache. Reusing an XHR refreshes the cache when its body changes. Disabling
  blocking, safe mode or whitelisting returns the original text immediately.
  Native responseText exceptions, partial/error responses, unrelated endpoints
  and binary data retain their behavior. Disabled/safe requests skip parsing.
- Fetch filtering checks both requested and final response origin/path, keeps
  URL/redirect/type metadata and removes stale length/encoding headers after
  creating the decoded, modified response body.
- Count fields only when deletion succeeds, so frozen player objects do not
  produce false removal statistics.
- Cache mobile ad-skip detection within one synchronous sweep; invalidate after
  clicking Skip and between sweeps. Normal content now needs one mobile-button
  query per sweep, while ad transitions remain fresh. Mobile-only ad evidence
  still cannot seek/accelerate the shared content video.

## Validation

- `tests/early-ad-script.cjs`: initial response, JSON.parse, fetch and XHR;
  stream preservation, metadata, response reuse, dynamic policy, binary/native
  exceptions, partial/error/oversized text, frozen objects and origin isolation.
- `tests/adblock-script.cjs`: 31 player regressions passed, including synchronous
  Skip, midroll state restoration, content progress, repeat, PiP, system audio,
  community skips and maximum quality selection.
- Discovery, search-preview, browse-navigation and Home script fixtures passed.
- Updated debug APK installed with `adb install -r`, preserving app data.
- `tests/early-ad-webview.cjs` passed against real API 37 Chromium WebView:
  real XHR text/JSON and fetch getter behavior, consistent repeated reads,
  stream URL preservation, native InvalidStateError, disabled/safe/whitelisted
  text restoration and unrelated-endpoint isolation. Twelve explicit ad fields
  were removed from three controlled fixture responses. DevTools fulfilled only
  QA-marked requests; interception and temporary JS policy were restored after
  the check. This is a browser integration test, not a claim about live source ads.
- Live Rick Astley watch page: document-start filter installed, three fields
  removed, initial player response had no playerAds/adPlacements/adSlots.
  No legacy ad episode was observed in the bounded sample and script diagnostics
  reported zero errors. Content advanced to about 20 seconds of 213 seconds;
  some intervals buffered (readyState 2), so smooth streaming is not established.
  Playback was paused and the debug port removed after QA. No AndroidRuntime
  fatal exception was found in the final log check.
- Debug/release/unit/lint build succeeded in 10m 4s; 40 unit tests passed with
  zero failures/errors; lint has zero errors.
- Release APK: `dist/VoTuibe-v1.6.7-release-arm64-v8a.apk`, 54,148,124 bytes,
  versionCode 34, ARM64 only, not debuggable; v2/v3 signatures and 16 KiB alignment
  verified. Existing development/test signing certificate retained.
  SHA-256: `260EE6599F716125E42FC862F5AF8744A7FC58EF13326CC5EAEC6691DCEFAEFA`.

## API references

- [XMLHttpRequest response](https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest/response)
  and [responseType](https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest/responseType):
  response format and native text/JSON/binary semantics.
- [WebViewCompat document-start injection](https://developer.android.com/reference/androidx/webkit/WebViewCompat):
  early execution scoped to allowed origins, guarded by feature support.

These changes cover client-visible player ad metadata and existing DOM/network
filtering. A live observation cannot prove that every source ad variant is
blocked, including ads inserted into the media stream itself. Physical phone
performance has not been verified in this session.
