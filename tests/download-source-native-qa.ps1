param(
    [string]$Url = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
    [ValidateSet('inspect','video','mp3')][string]$Mode = 'inspect',
    [switch]$Alternate
)
# Development emulator only; no proxy, cookies or account state are used.
if ($Url -notmatch '^https://www\.youtube\.com/watch\?v=[A-Za-z0-9_-]+$') { throw 'Use a canonical video URL' }
$adb = 'C:\Users\Thang\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apk = (& $adb -s emulator-5554 shell pm path com.example.videoshield).Trim().Replace('package:', '')
$native = $apk.Substring(0, $apk.LastIndexOf('/')) + '/lib/x86_64'
$base = '/data/user/0/com.example.videoshield'
$pythonRoot = "$base/no_backup/youtubedl-android/packages/python/usr"
$ffmpegRoot = "$base/no_backup/youtubedl-android/packages/ffmpeg/usr"
$engine = "$base/no_backup/youtubedl-android/yt-dlp/yt-dlp"
$prefix = "LD_LIBRARY_PATH=$pythonRoot/lib`:$ffmpegRoot/lib PYTHONHOME=$pythonRoot SSL_CERT_FILE=$pythonRoot/etc/tls/cert.pem TMPDIR=$base/cache $native/libpython.so $engine --verbose --no-playlist --socket-timeout 15 --retries 2 --extractor-retries 1 --fragment-retries 3 --js-runtimes quickjs:$native/libqjs.so --ffmpeg-location $native/libffmpeg.so"
if ($Alternate) { $prefix += ' --extractor-args "youtube:player_client=default,web_safari;webpage_client=web_safari"' }
$folder = "$base/cache/source-qa-1.6.9"
& $adb -s emulator-5554 shell "run-as com.example.videoshield mkdir -p $folder"
$options = switch ($Mode) {
    'inspect' { '--skip-download --ignore-no-formats-error --print "%(title)s | %(height)s | %(format_id)s"' }
    'video' { "-f `"bestvideo*[height<=144]+bestaudio/best[height<=144][vcodec!=none]/bestvideo[height<=144]`" --merge-output-format mkv --remux-video mkv -o `"$folder/video.%(ext)s`"" }
    'mp3' { "-f bestaudio/best -x --audio-format mp3 --audio-quality 0 -o `"$folder/audio.%(ext)s`"" }
}
& $adb -s emulator-5554 shell "run-as com.example.videoshield sh -c '$prefix $options $Url'"
if ($LASTEXITCODE -ne 0) { throw "Native source QA failed: $LASTEXITCODE" }
