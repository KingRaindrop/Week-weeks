Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$javaHome = "D:\Android Studio\jbr"
$androidHome = "D:\Android\Sdk"
$gradleUserHome = "D:\Android\Gradle"
$gradle = Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-9.2.0-bin\11i5gvueggl8a5cioxuftxrik\gradle-9.2.0\bin\gradle.bat"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$lintHtml = Join-Path $root "app\build\reports\lint-results-debug.html"

foreach ($required in @(
    (Join-Path $javaHome "bin\java.exe"),
    (Join-Path $androidHome "platforms\android-36.1\android.jar"),
    $gradle
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing build dependency: $required"
    }
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidHome
$env:ANDROID_SDK_ROOT = $androidHome
$env:GRADLE_USER_HOME = $gradleUserHome

Push-Location $root
try {
    & $gradle --no-daemon clean lintDebug assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code: $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Gradle finished but APK was not found: $apk"
}

$apkFile = Get-Item -LiteralPath $apk
$hash = Get-FileHash -LiteralPath $apk -Algorithm SHA256

Write-Host ""
Write-Host "Build and lint passed." -ForegroundColor Green
Write-Host "APK: $($apkFile.FullName)"
Write-Host "Size: $($apkFile.Length) bytes"
Write-Host "SHA-256: $($hash.Hash)"
if (Test-Path -LiteralPath $lintHtml) {
    Write-Host "Lint report: $lintHtml"
}
