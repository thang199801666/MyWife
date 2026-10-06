$adb = 'C:\Users\Thang\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$package = (& $adb -s emulator-5554 shell pm path com.example.videoshield).Trim().Replace('package:', '')
$native = $package.Substring(0, $package.LastIndexOf('/')) + '/lib/x86_64'
$base = '/data/user/0/com.example.videoshield'
$python = "$base/no_backup/youtubedl-android/packages/python/usr"
$ffmpeg = "$base/no_backup/youtubedl-android/packages/ffmpeg/usr"
$engine = "$base/no_backup/youtubedl-android/yt-dlp/yt-dlp"
& $adb -s emulator-5554 reverse tcp:8889 tcp:8889
$prefix = "LD_LIBRARY_PATH=$python/lib`:$ffmpeg/lib PYTHONHOME=$python SSL_CERT_FILE=$python/etc/tls/cert.pem TMPDIR=$base/cache $native/libpython.so $engine --verbose --no-playlist --socket-timeout 15 --retries 2 --extractor-retries 1 --fragment-retries 3 --js-runtimes quickjs:$native/libqjs.so --ffmpeg-location $native/libffmpeg.so --proxy http://127.0.0.1:8889"
& $adb -s emulator-5554 shell "run-as com.example.videoshield mkdir -p cache/download-qa-1.6.5"
$options = if ($args[0] -eq 'mp3') { "-f bestaudio/best -x --audio-format mp3 --audio-quality 0 -o `"$base/cache/download-qa-1.6.5/audio.%(ext)s`"" } else { "-f `"bestvideo[height<=144]+bestaudio/best[height<=144]`" --merge-output-format mkv --remux-video mkv -o `"$base/cache/download-qa-1.6.5/video.%(ext)s`"" }
& $adb -s emulator-5554 shell "run-as com.example.videoshield sh -c '$prefix $options https://m.youtube.com/watch?v=dQw4w9WgXcQ'"
