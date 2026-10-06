param(
    [switch]$Publish,
    [switch]$SkipBuild,
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$KeystorePath = (Join-Path $env:USERPROFILE '.android/debug.keystore'),
    [string]$KeyAlias = 'androiddebugkey'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$repo = 'thang199801666/MyWife'
$config = Get-Content 'app/build.gradle.kts' -Raw
$version = [regex]::Match($config, 'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"').Groups[1].Value
$code = [long][regex]::Match($config, 'versionCode\s*=\s*(\d+)').Groups[1].Value
$minSdk = [int][regex]::Match($config, 'minSdk\s*=\s*(\d+)').Groups[1].Value
if (!$version -or $code -le 0) { throw 'Missing app version' }
$tag = "v$version"
if (!$SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$buildTools = Join-Path $SdkPath 'build-tools/36.0.0'
if (!(Test-Path -LiteralPath $KeystorePath)) { throw 'Existing signing key not found; do not create a replacement' }
if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
if (!$env:VOTUI_STORE_PASSWORD) {
    if ($KeyAlias -eq 'androiddebugkey') { $env:VOTUI_STORE_PASSWORD = 'android' }
    else { throw 'Set VOTUI_STORE_PASSWORD in the environment' }
}
if (!$env:VOTUI_KEY_PASSWORD) { $env:VOTUI_KEY_PASSWORD = $env:VOTUI_STORE_PASSWORD }
function Check-Exit([string]$step) { if ($LASTEXITCODE -ne 0) { throw "$step failed (exit $LASTEXITCODE)" } }
if ($Publish) {
    $changes = git status --porcelain
    Check-Exit 'Git status'
    if ($changes) { throw 'Commit and push all source changes before publishing' }
    $commit = (git rev-parse HEAD).Trim()
    Check-Exit 'Git HEAD'
    $remote = git ls-remote "https://github.com/$repo.git" refs/heads/main
    Check-Exit 'Remote main'
    if (!$remote -or !$remote.StartsWith($commit)) { throw 'Push this commit to main before publishing' }
    gh release view $tag --repo $repo *> $null
    if ($LASTEXITCODE -eq 0) { throw 'This version already has a release; increment the app version instead' }
    if (!(Test-Path "releases/$tag.md")) { throw "Add release notes at releases/$tag.md" }
}
if (!$SkipBuild) {
    & './gradlew.bat' assembleRelease testDebugUnitTest lintDebug '-PsplitApks=true' --no-daemon --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
    Check-Exit 'Release build'
}
$output = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$name = "VoTui-v$version-release-arm64-v8a.apk"
$apk = Join-Path $output $name
$aligned = Join-Path $output "VoTui-v$version-aligned.apk"
$unsigned = Join-Path $projectRoot 'app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk'
& "$buildTools/zipalign.exe" -P 16 -f 4 $unsigned $aligned
Check-Exit 'Alignment'
& "$buildTools/apksigner.bat" sign --ks $KeystorePath --ks-key-alias $KeyAlias --ks-pass env:VOTUI_STORE_PASSWORD --key-pass env:VOTUI_KEY_PASSWORD --out $apk $aligned
Check-Exit 'Signing'
& "$buildTools/apksigner.bat" verify --verbose --print-certs $apk
Check-Exit 'Signature verification'
& "$buildTools/zipalign.exe" -c -P 16 4 $apk
Check-Exit 'Alignment verification'
$badging = & "$buildTools/aapt.exe" dump badging $apk
Check-Exit 'APK inspection'
if (!($badging | Select-String -SimpleMatch "name='com.example.videoshield' versionCode='$code' versionName='$version'")) { throw 'APK version does not match source' }
if (!($badging | Select-String -SimpleMatch "native-code: 'arm64-v8a'")) { throw 'APK architecture mismatch' }
if ($badging | Select-String 'application-debuggable') { throw 'Refusing to publish a debug APK' }
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$metadata = [ordered]@{
    schemaVersion = 1
    packageName = 'com.example.videoshield'
    versionCode = $code
    versionName = $version
    minSdk = $minSdk
    abi = 'arm64-v8a'
    assetName = $name
    size = (Get-Item -LiteralPath $apk).Length
    sha256 = $hash
}
$manifest = Join-Path $output 'update.json'
$checksums = Join-Path $output 'SHA256SUMS.txt'
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($manifest, ($metadata | ConvertTo-Json) + "`n", $utf8)
[System.IO.File]::WriteAllText($checksums, "$hash  $name`n", $utf8)
# Exact workspace path only; no recursive cleanup.
if ((Resolve-Path -LiteralPath $aligned).Path -eq $aligned) { Remove-Item -LiteralPath $aligned }
Write-Output "Prepared $apk"
if ($Publish) {
    # A draft keeps incomplete uploads out of the app's /releases/latest endpoint.
    gh release create $tag $apk $manifest $checksums --repo $repo --target $commit --title "Vợ Tui $version" --notes-file "releases/$tag.md" --draft
    Check-Exit 'Draft release and assets'
    gh release edit $tag --repo $repo --draft=false --latest
    Check-Exit 'Publish release'
    gh release view $tag --repo $repo --json url,tagName,assets
    Check-Exit 'Published release verification'
}
