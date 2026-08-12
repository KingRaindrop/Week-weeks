Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$javaHome = if ($env:JAVA_HOME -and (Test-Path -LiteralPath $env:JAVA_HOME)) { $env:JAVA_HOME } else { "D:\Android Studio\jbr" }
$androidHome = if ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME)) { $env:ANDROID_HOME } else { "D:\Android\Sdk" }
$gradleUserHome = if ($env:GRADLE_USER_HOME -and (Test-Path -LiteralPath $env:GRADLE_USER_HOME)) { $env:GRADLE_USER_HOME } else { "D:\Android\Gradle" }

$wrapper = Join-Path $root "gradlew.bat"
if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "Gradle wrapper not found: $wrapper"
}

foreach ($required in @(
    (Join-Path $javaHome "bin\java.exe"),
    (Join-Path $androidHome "platforms\android-36.1\android.jar")
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
    & $wrapper --no-daemon clean lintDebug assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code: $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Gradle finished but APK was not found: $apk"
}

Write-Host "Build verified: $apk"