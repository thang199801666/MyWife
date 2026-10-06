param([string]$Avd = 'YouTooBee_Light', [switch]$ColdBoot)
$ErrorActionPreference = 'Stop'
$sdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
$emulatorPath = Join-Path $sdkPath 'emulator\emulator.exe'
$deviceLines = @(& $adbPath devices | Select-String '^emulator-\d+\s+device$')
if ($deviceLines.Count -gt 1) { throw 'More than one emulator is running; close the extra emulator first.' }
if ($deviceLines.Count -eq 0) {
    $launchArguments = @('-avd', $Avd, '-gpu', 'host')
    if ($ColdBoot) { $launchArguments += '-no-snapshot-load' }
    $emulatorProcess = Start-Process -FilePath $emulatorPath -ArgumentList $launchArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $PSScriptRoot 'emulator-launch.log') -RedirectStandardError (Join-Path $PSScriptRoot 'emulator-launch-errors.log')
    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 2
        $deviceLines = @(& $adbPath devices | Select-String '^emulator-\d+\s+device$')
        if ($emulatorProcess.HasExited -and $deviceLines.Count -eq 0) {
            throw 'Emulator exited before connecting. See emulator-launch.log and emulator-launch-errors.log.'
        }
    } until ($deviceLines.Count -gt 0 -or (Get-Date) -gt $deadline)
    if ($deviceLines.Count -ne 1) { throw 'Emulator did not connect. Check Android Studio Device Manager.' }
}
$serial = ($deviceLines[0].Line -split '\s+')[0]
$deadline = (Get-Date).AddMinutes(3)
do {
    $booted = ([string](& $adbPath -s $serial shell getprop sys.boot_completed)).Trim()
    if ($booted -ne '1') { Start-Sleep -Seconds 2 }
} until ($booted -eq '1' -or (Get-Date) -gt $deadline)
if ($booted -ne '1') { throw 'Emulator did not finish booting.' }
& $adbPath -s $serial shell wm size 720x1606
if ($LASTEXITCODE -ne 0) { throw 'Cannot change emulator display size.' }
& $adbPath -s $serial shell wm density 270
if ($LASTEXITCODE -ne 0) { throw 'Cannot change emulator display density.' }
Write-Host "Ready: $serial, 720x1606 / density 270. Run app from Android Studio."
Write-Host "Restore: adb -s $serial shell wm size reset; adb -s $serial shell wm density reset"
