# Run from Windows PowerShell. Default is read-only preflight + APK backup.
param(
    [string]$Serial = 'MJPVXCSG9HYL65YL',
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [switch]$InstallAndTest
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$Out = Join-Path $env:TEMP ('itantra-ui-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $Out | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    # Windows PowerShell wraps native stderr (including adb progress) as ErrorRecord.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $Adb -s $Serial @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($code -ne 0) { throw "ADB failed: $($Arguments -join ' ')`n$result" }
    return ($result -join "`n")
}
$App = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
$Test = Join-Path $Root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
foreach ($file in @($App, $Test)) {
    if (!(Test-Path $file)) { throw "Missing $file. Build assembleDebug assembleDebugAndroidTest first." }
}
if ((Invoke-Adb @('get-state')).Trim() -ne 'device') { throw 'Device is not authorized.' }
$Paths = Invoke-Adb @('shell', 'pm', 'path', 'com.vokie')
$ApkPaths = @($Paths -split "`n" | Where-Object { $_ -match '^package:' })
if ($ApkPaths.Count -ne 1) { throw 'Expected one installed APK; split APK backup requires manual handling.' }
$Installed = ($ApkPaths[0] -replace '^package:', '').Trim()
$BeforeHash = ((Invoke-Adb @('shell', 'sha256sum', $Installed)).Trim() -split '\s+')[0]
$Backup = Join-Path $Out 'before-ui-test.apk'
Invoke-Adb @('pull', $Installed, $Backup) | Out-File (Join-Path $Out 'backup.log')
if ((Get-FileHash $Backup -Algorithm SHA256).Hash.ToLowerInvariant() -ne $BeforeHash) { throw 'Backup SHA mismatch; installation prohibited.' }
$Thermal = Invoke-Adb @('shell', 'dumpsys', 'thermalservice')
$Thermal | Out-File (Join-Path $Out 'thermal-before.txt')
$Match = [regex]::Match($Thermal, '(?m)^Thermal Status:\s*(\d+)')
$Info = [ordered]@{
    serial = $Serial
    model = (Invoke-Adb @('shell', 'getprop', 'ro.product.model')).Trim()
    api = (Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
    abi = (Invoke-Adb @('shell', 'getprop', 'ro.product.cpu.abi')).Trim()
    current_apk_sha256 = $BeforeHash
    backup_apk = $Backup
    validated_production_sha256 = '072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9'
    backup_is_validated_production = ($BeforeHash -eq '072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9')
    test_app_sha256 = (Get-FileHash $App -Algorithm SHA256).Hash.ToLowerInvariant()
    instrumentation_sha256 = (Get-FileHash $Test -Algorithm SHA256).Hash.ToLowerInvariant()
}
$Info | ConvertTo-Json | Out-File (Join-Path $Out 'preflight.json')
Write-Host "Evidence/rollback APK: $Out"
Write-Host "Restore command: & '$Adb' -s '$Serial' install -r '$Backup'"
Write-Host 'Backup is the pre-test installation, NOT necessarily the validated production APK.'
if (!$InstallAndTest) { Write-Host 'Preflight only. Nothing installed or launched.'; exit 0 }
if (!$Match.Success -or [int]$Match.Groups[1].Value -ge 2) {
    throw 'Device must cool below thermal status 2 for this UI session. Nothing installed.'
}
# No uninstall, pm clear, model changes, device settings changes or logcat clearing.
Invoke-Adb @('install', '-r', $App) | Out-File (Join-Path $Out 'install-app.log')
$NewPath = (Invoke-Adb @('shell', 'pm', 'path', 'com.vokie')).Trim() -replace '^package:', ''
$NewHash = ((Invoke-Adb @('shell', 'sha256sum', $NewPath)).Trim() -split '\s+')[0]
if ($NewHash -ne $Info.test_app_sha256) { throw 'Installed test APK hash mismatch. Stop and use the printed restore command.' }
Invoke-Adb @('install', '-r', $Test) | Out-File (Join-Path $Out 'install-tests.log')
try {
    $Result = Invoke-Adb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'com.vokie.ui.ITantraDesignTest', 'com.vokie.test/androidx.test.runner.AndroidJUnitRunner')
    $Result | Out-File (Join-Path $Out 'instrumentation.txt')
    if ($Result -notmatch 'OK \(3 tests\)' -or $Result -match 'FAILURES|INSTRUMENTATION_FAILED|Process crashed') {
        throw 'Instrumentation did not pass all three tests. Inspect evidence; do not approve the build.'
    }
    Write-Host 'Three UI instrumentation tests passed. Manual visual checks are still required.'
} finally {
    Invoke-Adb @('logcat', '-d', '-t', '4000') | Out-File (Join-Path $Out 'logcat.txt')
    Invoke-Adb @('shell', 'dumpsys', 'thermalservice') | Out-File (Join-Path $Out 'thermal-after.txt')
    Invoke-Adb @('shell', 'am', 'force-stop', 'com.vokie') | Out-Null
    Write-Host 'App stopped. Test APK remains installed for manual inspection; restore with the printed command afterward.'
}
