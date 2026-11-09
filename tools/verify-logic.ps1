Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "local-env.ps1")
$javaHome = Get-WeekWeeksJavaHome
$androidHome = Get-WeekWeeksAndroidHome
$gradleUserHome = Get-WeekWeeksGradleUserHome
$javac = Join-Path $javaHome "bin\javac.exe"
$androidJar = Join-Path $androidHome "platforms\android-36.1\android.jar"
$wrapper = Join-Path $root "gradlew.bat"

foreach ($required in @($javac, $androidJar, $wrapper)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required verification dependency is missing: $required"
    }
}

# Unit tests live in app/src/test (JUnit 4) and run through Gradle, the same as Android Studio.
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidHome
$env:ANDROID_SDK_ROOT = $androidHome
$env:GRADLE_USER_HOME = $gradleUserHome
Push-Location $root
try {
    & $wrapper --no-daemon testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw "Unit tests failed with exit code: $LASTEXITCODE" }
} finally {
    Pop-Location
}

$androidOut = Join-Path $root "build\android-java-check"
New-Item -ItemType Directory -Force -Path $androidOut | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $root "app\src\main\java") -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName
$stubs = Get-ChildItem -LiteralPath (Join-Path $root "tools\compile-stubs") -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName

& $javac -encoding UTF-8 --release 17 -cp $androidJar -d $androidOut @sources @stubs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Android Java compile check passed." -ForegroundColor Green
