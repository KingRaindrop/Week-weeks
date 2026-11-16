Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $root "app/src/main/AndroidManifest.xml"
$activityPath = Join-Path $root "app/src/main/java/com/weekweeks/app/MainActivity.java"
$secureStoragePath = Join-Path $root "app/src/main/java/com/weekweeks/app/SecureStorage.java"
$reminderReceiverPath = Join-Path $root "app/src/main/java/com/weekweeks/app/ClassReminderReceiver.java"
$reminderSchedulerPath = Join-Path $root "app/src/main/java/com/weekweeks/app/ReminderScheduler.java"
$bootReceiverPath = Join-Path $root "app/src/main/java/com/weekweeks/app/ReminderBootReceiver.java"
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

foreach ($path in @($manifestPath, $activityPath, $secureStoragePath, $reminderReceiverPath, $reminderSchedulerPath, $bootReceiverPath, $rulesPath, $appBuildPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        $failures.Add("Required file is missing: $path")
    }
}

if ($failures.Count -eq 0) {
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8
    $activity = Get-Content -LiteralPath $activityPath -Raw -Encoding UTF8
    $secureStorage = Get-Content -LiteralPath $secureStoragePath -Raw -Encoding UTF8
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
    Assert-NotContains "AndroidManifest.xml" $manifest '<uses-permission\s+android:name="android\.permission\.INTERNET"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.permission\.RECEIVE_BOOT_COMPLETED'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.permission\.SCHEDULE_EXACT_ALARM'
    Assert-Contains "AndroidManifest.xml" $manifest 'android:name="\.ReminderBootReceiver"'
    Assert-Contains "AndroidManifest.xml" $manifest 'android\.intent\.action\.BOOT_COMPLETED'

    Assert-Contains "data_extraction_rules.xml" $rules '<exclude\s+domain="sharedpref"\s+path="\."\s*/>'

    # 加密实现已抽到 SecureStorage.java；断言随之迁移
    Assert-Contains "SecureStorage.java" $secureStorage 'AES/GCM/NoPadding'
    Assert-Contains "SecureStorage.java" $secureStorage 'AndroidKeyStore'
    Assert-Contains "SecureStorage.java" $secureStorage 'KeyGenParameterSpec'
    Assert-Contains "SecureStorage.java" $secureStorage 'setRandomizedEncryptionRequired\(true\)'
    Assert-Contains "MainActivity.java" $activity 'new SecureStorage\('
    Assert-Contains "MainActivity.java" $activity 'courses_encrypted_v2'
    Assert-Contains "MainActivity.java" $activity 'SafePreferences\.commit\('
    $safePreferences = Get-Content -LiteralPath (Join-Path $root "app/src/main/java/com/weekweeks/app/SafePreferences.java") -Raw -Encoding UTF8
    Assert-Contains "SafePreferences.java" $safePreferences 'return editor\.commit\(\)'
    Assert-Contains "MainActivity.java" $activity 'storageLocked'
    Assert-Contains "MainActivity.java" $activity 'InputFilter\.LengthFilter'
    Assert-Contains "MainActivity.java" $activity 'MAX_COURSES'
    Assert-Contains "MainActivity.java" $activity 'ACTION_OPEN_DOCUMENT'
    Assert-Contains "MainActivity.java" $activity 'MAX_PDF_BYTES'
    # 在线校历导入已移除：不允许再出现任何联网代码
    Assert-NotContains "MainActivity.java" $activity 'HttpURLConnection|java\.net\.URL|importAcademicCalendarOnline|fetchAcademicCalendar'
    Assert-Contains "MainActivity.java" $activity 'TimetableFileParser\.parse'
    $fileParser = Get-Content -LiteralPath (Join-Path $root "app/src/main/java/com/weekweeks/app/TimetableFileParser.java") -Raw -Encoding UTF8
    $xlsxParser = Get-Content -LiteralPath (Join-Path $root "app/src/main/java/com/weekweeks/app/XlsxTimetableParser.java") -Raw -Encoding UTF8
    Assert-Contains "TimetableFileParser.java" $fileParser 'PdfCourseParser\.parseCourses'
    Assert-Contains "TimetableFileParser.java" $fileParser 'XlsxTimetableParser\.parse'
    Assert-Contains "XlsxTimetableParser.java" $xlsxParser 'MAX_TOTAL_BYTES'
    Assert-Contains "XlsxTimetableParser.java" $xlsxParser 'CodingErrorAction\.REPORT'
    Assert-Contains "XlsxTimetableParser.java" $xlsxParser 'StringReader\(raw\)'
    Assert-Contains "XlsxTimetableParser.java" $xlsxParser 'external-general-entities", false'
    Assert-Contains "XlsxTimetableParser.java" $xlsxParser 'external-parameter-entities", false'
    Assert-Contains "MainActivity.java" $activity 'readWithLimit'
    Assert-NotContains "MainActivity.java" $activity 'putString\(COURSES_KEY[^;]*?\.apply\(\)'
    Assert-NotContains "MainActivity.java" $activity 'putString\(COURSES_KEY,\s*array\.toString\(\)\)'
    Assert-Contains "app/build.gradle" $appBuild 'minifyEnabled\s+true'
    Assert-Contains "app/build.gradle" $appBuild 'shrinkResources\s+true'
    Assert-Contains "app/build.gradle" $appBuild 'debuggable\s+false'
    Assert-Contains "app/build.gradle" $appBuild 'proguard-android-optimize\.txt'
    Assert-Contains "ClassReminderReceiver.java" $reminderReceiver 'ReminderScheduler\.scheduleNextFromBroadcast'
    Assert-Contains "ClassReminderReceiver.java" $reminderReceiver 'ReminderScheduler\.EXTRA_REQUEST_CODE'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'class_reminder_schedule_encrypted_v1'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'new SecureStorage\('
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'STORAGE\.encrypt'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'STORAGE\.decrypt'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'setAndAllowWhileIdle'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'setExactAndAllowWhileIdle'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'canScheduleExactAlarms'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler 'reminder_schedule_generation'
    Assert-Contains "ReminderScheduler.java" $reminderScheduler '\.commit\(\)'
    Assert-NotContains "ReminderScheduler.java" $reminderScheduler '\.apply\(\)'
    # Permission diagnostics now live behind postNotification; inspect the actual receiver
    # dispatch order instead of comparing against a removed POST_NOTIFICATIONS token (-1).
    $onReceive = [regex]::Match($reminderReceiver, '(?s)public void onReceive\(.*?\n    \}').Value
    Assert-Contains "ClassReminderReceiver.onReceive" $onReceive 'ReminderScheduler\.scheduleNextFromBroadcast'
    Assert-Contains "ClassReminderReceiver.onReceive" $onReceive 'postNotification\(context, intent\)'
    Assert-Contains "ClassReminderReceiver.java" $reminderReceiver 'ReminderDiagnostics\.postBlocker'
    if ($onReceive.IndexOf('ReminderScheduler.scheduleNextFromBroadcast') -gt
            $onReceive.IndexOf('postNotification(context, intent)')) {
        $failures.Add("ClassReminderReceiver.java must continue the reminder chain before notification posting")
    }

    Assert-NotContains "project" $allProjectText 'android\.permission\.(READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|QUERY_ALL_PACKAGES|REQUEST_INSTALL_PACKAGES|SYSTEM_ALERT_WINDOW|READ_SMS|SEND_SMS|RECORD_AUDIO|CAMERA|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION)'
    Assert-NotContains "project" $allProjectText 'WebView|addJavascriptInterface|setJavaScriptEnabled\(true\)'
    Assert-NotContains "project" $allProjectText 'DexClassLoader|PathClassLoader|loadClass\('
    Assert-NotContains "project" $allProjectText 'Runtime\.getRuntime\(\)\.exec|ProcessBuilder'
    Assert-NotContains "project" $allProjectText 'ObjectInputStream|Serializable'
    Assert-NotContains "project" $allProjectText 'TrustManager|HostnameVerifier|setHostnameVerifier'
    Assert-NotContains "project" $allProjectText 'MODE_WORLD_READABLE|MODE_WORLD_WRITEABLE'
    # XML namespaces and SAX feature identifiers are not network requests. Keep a narrow
    # literal allowlist, and independently forbid networking APIs and INTERNET permission.
    $networkScanText = $javaKotlinText
    foreach ($xmlIdentifier in @(
            'http://schemas.openxmlformats.org/spreadsheetml/2006/main',
            'http://schemas.openxmlformats.org/officeDocument/2006/relationships',
            'http://schemas.openxmlformats.org/package/2006/relationships',
            'http://xml.org/sax/features/external-general-entities',
            'http://xml.org/sax/features/external-parameter-entities')) {
        $networkScanText = $networkScanText.Replace($xmlIdentifier, 'urn:local-xml-identifier')
    }
    Assert-NotContains "Java/Kotlin source" $javaKotlinText 'java\.net\.(URL|Socket|HttpURLConnection)|okhttp|HttpClient|openConnection\('
    Assert-NotContains "Java/Kotlin source" $networkScanText 'http://'
    Assert-NotContains "Java/Kotlin source" $networkScanText 'https://'
}

if ($failures.Count -gt 0) {
    Write-Host "Security audit failed:" -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "Security audit passed." -ForegroundColor Green
