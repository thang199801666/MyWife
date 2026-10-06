# VoTuibe launcher artwork

The original image supplied by the user is preserved in `votuibe-original.png`.
Version 1.4.1 uses the newer glossy ribbon-heart artwork supplied by the user.
Transparent pixels are preserved; non-square artwork is fitted without stretching.
Launcher PNGs are resized from this artwork without redrawing it. Adaptive
icons center the artwork in a 72dp area of a 108dp layer with a dark background.

Regenerate resources from the repository root with Python and Pillow:

```powershell
python build_branding.py
```

Version 1.3.2 (15) uses the VoTuibe display name. The application ID remains
`com.example.videoshield` so installing an update retains existing app data.
The monochrome shield remains the notification small icon, as required for
Android status bar rendering.

Verified: debug and minified unsigned release builds plus `lintDebug` passed.
The debug APK was installed as an update on emulator-5554; the launcher displays
VoTuibe and the supplied artwork. See `launcher.png` for the captured result.
APK outputs: `dist/VoTuibe-v1.3.2-debug.apk` and
`dist/VoTuibe-v1.3.2-release-unsigned.apk`.
