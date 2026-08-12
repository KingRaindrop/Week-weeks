Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $root "app/src/main/AndroidManifest.xml"
$activityPath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/MainActivity.java"
$reminderReceiverPath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/ClassReminderReceiver.java"
$reminderSchedulerPath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/ReminderScheduler.java"
$bootReceiverPath = Join-Path $root "app/src/main/java/com/example/meinstundenplan/ReminderBootReceiver.java"
$rulesPath = Join-Path $root "app/src/main/res/xml/data_extraction_rules.xml"
$appBuildPath = Join-Path $root "app/build.gradle"

$failures = New-Object System.Collections.Generic.List[string]

function Assert-Contains {
    param(
        [string] $Name,
        [string] $Text,
        [string] $Pattern
    )
    if ($Text -notmatch $Pattern) {
        $failures.Add("$Name missing required pattern: $Pattern")
    }
}

function Assert-NotContains {
    param(
        [string] $Name,
        [string] $Text,
        [string] $Pattern
    )
    if ($Text -match $Pattern) {
        $failures.Add("$Name contains forbidden pattern: $Pattern")
    }
}

foreach ($path in @($manifestPath, $activityPath, $reminderReceiverPath, $reminderSchedulerPath, $bootReceiverPath, $rulesPath, $appBuildPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        $failures.Add("Required file is missing: $path")
    }
}

if ($failures.Count -eq 0) {
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8
    $activity = Get-Content -LiteralPath $activityPath -Raw -Encoding UTF8
    $reminderReceiver = Get-Content -LiteralPath $reminderReceiverPath -Raw -Encoding UTF8
    $reminderScheduler = Get-Content -LiteralPath $reminderSchedulerPath -Raw -Encoding UTF8
    $rules = Get-Content -LiteralPath $rulesPath -Raw -Encoding UTF8
    $appBuild = Get-Content -LiteralPath $appBuildPath -Raw -Encoding UTF8
    $scanExtensions = @(".java", ".kt", ".xml", ".gradle", ".properties")
    $appSourceFiles = Get-ChildItem -LiteralPath (Join-Path $root "app") -Recurse -File |
        Where-Object { $scanExtensions.Contains($_.Extension) -and $_.FullName -notmatch '[\\/](build|\.gradle)[\\/]' }
    $rootConfigFiles = @("build.gradle", "settings.gradle", "gradle.properties") |
        ForEach-Object { Join-Path $root $_ } |
        Where-Object { Test-Path -LiteralPath $_ }
    $scanFiles = @($appSourceFiles.FullName) + @($rootConfigFiles)
    $allProjectText = ($scanFiles | ForEach-Object { Get-Content -LiteralPath $_ -Raw -Encoding UTF8 }) -join "`n"
    $javaKotlinText = ($appSourceFiles | Where-Object { $_.Extension -in @(".java", ".kt") } |
        ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 }) -join "`n"

    Assert-Contains "AndroidManifest.xml" $manifest 'android:allowBackup="false"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:fullBackupContent="false"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:dataExtractionRules="@xml/data_extraction_rules"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:usesCleartextTraffic="false"'
    Assert-Contains "AndroidManifest.xml" $manifest '<uses-permission\s+android:name="android\.permission\.INTERNET"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.permission\.RECEIVE_BOOT_COMPLETED'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.permission\.SCHEDULE_EXACT_ALARM'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:name="\.ReminderBootReceiver"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.intent\.action\.BOOT_COMPLETED'

    Assert-Contains "data_extraction_rules.xml" $rules '<exclude\s+domain="sharedpref"\s+path="\."\s*/>'

    Assert-Contains "MainActivity.java" $activity 'AES/GCM/NoPadding'
    Assert-Contains "MainActivity.java" $activity 'AndroidKeyStore'
    Assert-Contains "MainActivity.java" $activity 'KeyGenParameterSpec'
    Assert-Contains "MainActivity.java" $activity 'setRandomizedEncryptionRequired\(true\)'
    Assert-Contains "MainActivity.java" $activity 'courses_encrypted_v2'
    Assert-Contains "MainActivity.java" $activity '\.commit\(\)'
    Assert-Contains "MainActivity.java" $activity 'storageLocked'
    Assert-Contains "MainActivity.java" $activity 'InputFilter\.LengthFilter'
    Assert-Contains "MainActivity.java" $activity 'MAX_COURSES'
    Assert-Contains "MainActivity.java" $activity 'ACTION_OPEN_DOCUMENT'
    Assert-Contains "MainActivity.java" $activity 'MAX_PDF_BYTES'
    Assert-Contains "MainActivity.java" $activity 'DEFAULT_CALENDAR_URL\s*=\s*"https://jwc\.jyu\.edu\.cn/info/1102/4690\.htm"'
    Assert-Contains "MainActivity.java" $activity 'HttpURLConnection'
    Assert-Contains "MainActivity.java" $activity '"https"\.equalsIgnoreCase\(url\.getProtocol\(\)\)'
    Assert-Contains "MainActivity.java" $activity 'host\.equals\("jwc\.jyu\.edu\.cn"\)'
    Assert-Contains "MainActivity.java" $activity 'host\.endsWith\("\.jyu\.edu\.cn"\)'
    Assert-Contains "MainActivity.java" $activity 'readWithLimit'
    Assert-Contains "MainActivity.java" $activity 'parsePdfCourses'
    Assert-NotContains "MainActivity.java" $activity '\.apply\(\)'
    Assert-NotContains "MainActivity.java" $activity 'putString\(COURSES_KEY,\s*array\.toString\(\)\)'
    Assert-Contains "app/build.gradle" $appBuild 'minifyEnabled\s+true'
    Assert-Contains "app/build.gradle" $appBuild 'shrinkResources\s+true'
    Assert-Contains "app/build.gradle" $appBuild 'debuggable\s+false'
    Assert-Contains "app/build.gradle" $appBuild 'proguard-android-optimize\.txt'
    Assert-Contains "ClassReminderReceiver.java" $reminderReceiver 'ReminderScheduler\.scheduleNextFromBroadcast'
    Assert-Contains "ClassReminderReceiver.java" $reminderReceiver 'ReminderScheduler\.EXTRA_REQUEST_CODE'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'class_reminder_schedule_encrypted_v1'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'AES/GCM/NoPadding'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'setAndAllowWhileIdle'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'setExactAndAllowWhileIdle'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'canScheduleExactAlarms'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'reminder_schedule_generation'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler '\.commit\(\)'
    Assert-NotContains "ReminderScheduler.java" $reminderScheduler '\.apply\(\)'
    if ($reminderReceiver.IndexOf('ReminderScheduler.scheduleNextFromBroadcast') -gt
            $reminderReceiver.IndexOf('POST_NOTIFICATIONS')) {
        $failures.Add("ClassReminderReceiver.java must continue the reminder chain before notification permission checks")
    }

    Assert-NotContains "project" $allProjectText 'android\.permission\.(READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|QUERY_ALL_PACKAGES|REQUEST_INSTALL_PACKAGES|SYSTEM_ALERT_WINDOW|READ_SMS|SEND_SMS|RECORD_AUDIO|CAMERA|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION)'
    Assert-NotContains "project" $allProjectText 'WebView|addJavascriptInterface|setJavaScriptEnabled\(true\)'
    Assert-NotContains "project" $allProjectText 'DexClassLoader|PathClassLoader|loadClass\('
    Assert-NotContains "project" $allProjectText 'Runtime\.getRuntime\(\)\.exec|ProcessBuilder'
    Assert-NotContains "project" $allProjectText 'ObjectInputStream|Serializable'
    Assert-NotContains "project" $allProjectText 'TrustManager|HostnameVerifier|setHostnameVerifier'
    Assert-NotContains "project" $allProjectText 'MODE_WORLD_READABLE|MODE_WORLD_WRITEABLE'
    Assert-NotContains "Java/Kotlin source" $javaKotlinText 'http://'
    Assert-NotContains "Java/Kotlin source" $javaKotlinText 'https://(?!jwc\.jyu\.edu\.cn/info/1102/4690\.htm)'
}

if ($failures.Count -gt 0) {
    Write-Host "Security audit failed:" -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "Security audit passed." -ForegroundColor Green
