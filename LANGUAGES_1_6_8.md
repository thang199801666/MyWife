# Vợ Tui 1.6.8 — Vietnamese and English

- Launcher/application label, native brand header, Settings, dashboard and About
  now use Vợ Tui in both languages. Application ID and signing certificate remain
  compatible with installed VoTuibe updates.
- 365 paired Android strings, including settings, navigation, action menus,
  library, save/download options, offline playback/EQ, gesture feedback and
  notification controls. Dynamic display labels use formatted resources.
- AppLanguage selects Vietnamese initially, persists the in-app choice and uses
  Android 13+ LocaleManager plus declared locale_config. Localized Activity
  contexts support Android 8–12 without changing the phone's system language.
- Returning Activities recreate after a language change. Main's WebView saved
  state records the language of its original context, so switching languages
  loads newly localized pages rather than restoring the previous page language.
  Trusted YouTube loads include the chosen hl parameter. Search completions
  request the selected language. Website/account experiments may still control
  source-owned content; video titles and channel names retain their source text.
- Home heading/hint, recommendation reasons and the injected Download action are
  localized. JSON quoting protects script labels, and Home's render fingerprint
  includes language-dependent labels.
- Stored EQ preset keys, download selectors/quality records and video IDs retain
  their behavior; their display labels are translated at rendering time.
- About keeps: “This App I wrote for my wife and my unborn child”.
- Existing Downloads/VoTuibe paths are retained for compatibility with saved
  files. Internal JavaScript bridge names and persisted protocol keys are not
  product labels.

## Verification

- `tests/localization.py`: 365 paired strings, nonempty values, placeholder
  contracts, branding, dedication and supported languages passed.
- Home script language-change rendering fixture passed. Early ad, player and
  other existing regression fixtures remain applicable.
- API 37 emulator with English system UI: first updated launch selected vi;
  Settings showed Cài đặt Vợ Tui / Ngôn ngữ • Tiếng Việt. Selecting English via
  the actual picker switched Settings immediately. English persisted after
  force-stop/relaunch and the picker retained its checked English option.
- You and About inspected in both languages; brand remained Vợ Tui and the exact
  dedication remained present. Returned to Vietnamese via the picker. History
  retained 17 entries and Downloads retained all three existing video/MP3 files.
- Initial complete build passed in 9m 51s with 40 unit tests and zero lint errors.
  A final review corrected the temporary-file label, localized download error
  explanations/subscription feedback/health labels, and made an empty Android
  app-locale selection fall back to Vietnamese. Final build passed in 9m 12s,
  with all 40 unit tests passing and zero lint errors. Final installed debug UI
  showed “Lưu tạm • 30 ngày” and all three offline files remained available.
- Release: dist/VoTui-v1.6.8-release-arm64-v8a.apk, 54,328,404 bytes.
  Application label Vợ Tui, version 1.6.8 (35), ARM64 only, non-debuggable.
  16 KiB zip alignment and APK v2/v3 signatures verified. Signed using the
  existing development/test certificate, not a production signing key.
  SHA256: 0857A2461D7AD9CF7CA774757C9F91FB19CFB4AE752D4E1D751D0B97D28F2973.
- Runtime checks used the API 37 emulator; Android 8–12 compatibility compiled
  and passed lint but was not tested on an older device. No physical phone was
  connected for this verification.
- Screenshots: branding/language-settings-vi-1.6.8.png,
  language-settings-en-1.6.8.png, language-you-en-1.6.8.png and
  language-you-vi-1.6.8.png.

API reference: [Android per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages).

The three scripts in tools/localization_*.py and tools/migrate_localization.py
record the one-off resource migration. Do not rerun them over manually edited
resources; maintain strings directly in values/ and values-en/.
