# Emulator performance

The observed AVD was Pixel_10_Pro, API 37 / x86_64 / 16 KB pages, with a 1280x2856 display, density 480, four CPU cores and 2048 MB guest RAM. Windows reported WHPX installed and usable. General emulator UI lag was reported; no reliable before/after frame benchmark was captured.

`start-light-emulator.ps1` reuses one running emulator, or starts YouTooBee_Light with host GPU acceleration and normal Quick Boot when none is running. The separate YouTooBee_Light AVD was created from the already installed standard 4 KB Android 37.0 image; the original Pixel_10_Pro data was preserved. Its native display is 720x1606 / density 270. The script also applies those reversible Android display overrides, reducing rendered pixels by about 68% compared with the original display while preserving the approximate layout width in dp. A running emulator retains its current graphics backend.

Run from this directory:

```powershell
.\start-light-emulator.ps1
```

Restore the original display on the connected emulator:

```powershell
adb -s emulator-5554 shell wm size reset
adb -s emulator-5554 shell wm density reset
```

The launched emulator log confirmed WHPX operational and an AMD Radeon 860M OpenGL ES translator. Hardware acceleration configuration is documented at https://developer.android.com/studio/run/emulator-acceleration. The initial 16 KB system remained slow despite the display override: a live `top` snapshot showed 343% system CPU out of four cores, alongside substantial Play Store/Google Services load. A later 94-frame app sample reported all frames as janky. Thus the display change alone did not resolve the reported issue. These observations motivated the separate 4 KB AVD comparison; they do not establish that every 16 KB image is slow.

The app previously scheduled a full document filter sweep on the next animation frame for every burst of YouTube class/style mutations. The player can mutate progress/control styles every frame. Those bursts now share one fixed 200 ms deadline, avoiding frame-rate full-document scans without allowing continuous mutations to postpone filtering indefinitely. The periodic 800 ms playback/filter fallback and direct navigation/preferences updates remain. A regression fixture verifies batching and a newly active ad transition.

Build/test evidence: `build-performance.log` (debug build, JVM tests and Lint) and `node tests/adblock-script.cjs` (21 fixtures). Actual YouTube advertising behavior still needs live device verification. No measured FPS improvement is claimed.

The separate 4 KB AVD booted and `getconf PAGE_SIZE` confirmed 4096. The updated debug APK installed successfully. First-boot system CPU was still high (a later snapshot showed 285% system CPU out of four cores and system_server at 138%). A small four-frame post-reset sample had median 121 ms, with all four frames marked janky; this is too small and workload-dependent to compare directly with the earlier 16 KB sample. At the final page observation the video media was not loaded (readyState 0); `dist/performance-4k-page.json` records this limitation. The 4 KB comparison therefore did not verify resolution of the reported lag or successful playback on that new AVD. Host hardware inspection confirmed an 8-core/16-thread Ryzen AI 7 H 350, 32 GB RAM with about 6 GB free, and firmware virtualization enabled. No system virtualization/security setting was changed. The updated debug/release APKs were exported, and the temporary CDP port forward was removed.


On the next resumed pass, no emulator process/device was initially running. The launcher was corrected to allow Quick Boot by default instead of forcing a cold boot every time; use `-ColdBoot` explicitly when required. It returned Ready in about eight seconds on this machine. The live Rick Astley document then progressed from 12.98 to 46.05 seconds with readyState 4, unpaused and zero script errors (`dist/performance-quickboot-page.json`, `dist/performance-quickboot-page-later.json`). A Google Play `dex2oat64` process (PID 6628) was live at the first observation and absent at the later check; no process was killed to obtain that result. The intervening 490-frame sample still reported 445 janky frames (90.82%) and median 113 ms. This proves successful loaded-media playback on the 4 KB AVD and a faster startup observation, but does not prove that interaction lag has been resolved. The temporary debug forward was removed.

A subsequent baseline force-stopped YouTooBee and scrolled Android Settings. Its 309-frame sample contained 21.36% janky frames (median 29 ms). With the app stopped, Google Play Services compilation (dex2oat64 PID 7869) consumed 358% of the guest's four-core 400% total CPU. That PID was rechecked and confirmed absent before reopening YouTooBee; no compiler process was killed. The live filter sweep then averaged 0.99 ms across ten synchronous calls on a 1196-node page (`dist/performance-sweep-cost.json`), which is a narrow cost measurement, not a complete CPU profile.

A fresh settled playback sample reset graphics counters and started the actual Rick Astley media at 30 seconds. The loaded video advanced 31.33 to 57.58 seconds at 640x360 / readyState 4, unpaused with Shield enabled. The sample contained 1216 rendered frames, 242 janky frames (19.90%), and median 34 ms (`dist/performance-settled-playing-gfx.txt`, `dist/performance-settled-start.json`, `dist/performance-settled-playing.json`). This is substantially less jank than the earlier startup samples, but the workloads/time windows are not identical and the result cannot isolate the effect of the app patch, GPU settings, or compilation finishing. Residual emulator lag remains measurable. The temporary CDP forward was removed.
