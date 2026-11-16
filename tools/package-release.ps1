# Build a clean, publish-ready source distribution for GitHub.
#
# Copies only the publishable source (explicit allowlist and asset exclusions), refuses to run if any
# credential or build junk slipped in, then writes a spec-compliant zip whose
# entry names use forward slashes so Linux/macOS unzip restores the tree.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\package-release.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\package-release.ps1 -Version 1.3.0
[CmdletBinding()]
param(
    [string] $Version,
    [string] $OutDir = "dist"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

# The source archive also contains public, synthetic parser fixtures. Private
# timetable exports and locally supplied fonts must never enter the archive.
function Test-PublishableSourcePath {
    param([Parameter(Mandatory = $true)][string] $RelativePath)
    $path = $RelativePath.Replace([char]92, [char]47)
    if ($path -match '(^|/)(\.git|\.gradle|\.idea|build|outputs|artifacts|dist|samples|\.aws)(/|$)') {
        return $false
    }
    if ($path -match '^app/src/main/assets/fonts/.*\.(ttf|otf|ttc)$') { return $false }
    $name = [System.IO.Path]::GetFileName($path)
    foreach ($pattern in @('*.jks', '*.keystore', '*.p12', '*.pfx', '*.jks.properties',
            'release-signing.properties', 'local.properties', '.env', '.env.*')) {
        if ($name -like $pattern) { return $false }
    }
    if ([System.IO.Path]::GetExtension($path) -ieq '.pdf') {
        return $path -in @(
            'app/src/test/resources/synthetic-timetable.pdf',
            'app/src/androidTest/assets/parser-fixtures/synthetic-timetable.pdf'
        )
    }
    return $true
}

if (-not $Version) {
    $versionLine = Select-String -LiteralPath (Join-Path $root "app\build.gradle") `
        -Pattern 'versionName\s+"([^"]+)"' | Select-Object -First 1
    if (-not $versionLine) { throw "Could not read versionName from app\build.gradle" }
    $Version = $versionLine.Matches[0].Groups[1].Value
}
if ($Version -notmatch '^[0-9A-Za-z][0-9A-Za-z._+-]*$') {
    throw "Version must be a filename-safe version identifier."
}

$stagingName = "week-weeks-v$Version"
$distDir = [System.IO.Path]::GetFullPath((Join-Path $root $OutDir))
$rootPrefix = [System.IO.Path]::GetFullPath($root).TrimEnd([char]92, [char]47) + [System.IO.Path]::DirectorySeparatorChar
if (-not $distDir.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutDir must be a subdirectory of the project root."
}
$staging = Join-Path $distDir $stagingName

# Single files, addressed relative to the project root.
$files = @(
    ".gitignore",
    ".gitattributes",
    "LICENSE",
    "README.md",
    "README.en.md",
    "README.zh-TraditionalChinese.md",
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "app\build.gradle",
    "app\proguard-rules.pro",
    "secrets\release-signing.properties.template",
    "tools\build-verify.ps1",
    "tools\verify-logic.ps1",
    "tools\verify-reminders.ps1",
    "tools\reminder-diagnostic-test-doubles.ps1",
    "tools\verify-stored-data.ps1",
    "tools\security-audit.ps1",
    "tools\local-env.ps1",
    "tools\package-release.ps1",
    "tools\create-import-samples.py",
    "tools\course-audit.init.gradle",
    "tools\agenda-timeline-audit.init.gradle",
    "tools\display-mode-audit.init.gradle",
    "tools\interaction-audit.init.gradle",
    "tools\interaction-fixes.init.gradle",
    "tools\navigation-audit.init.gradle",
    "tools\round-two-audit.init.gradle",
    "tools\ui-refinement-audit.init.gradle"
)

# Directories copied recursively, subject to Test-PublishableSourcePath. Keeping
# docs together includes linked audit reports, screenshots, and public templates.
$treeDirs = @(
    "app\src",
    "gradle\wrapper",
    "tools\compile-stubs",
    "docs"
)

# Resolve the complete copy plan before touching an existing distribution.
$copyPlan = @()
foreach ($file in $files) {
    $source = Join-Path $root $file
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Expected release file not found: $file" }
    if (-not (Test-PublishableSourcePath $file)) { throw "Blocked file in release allowlist: $file" }
    $copyPlan += [pscustomobject]@{ Source = $source; Relative = $file }
}

foreach ($tree in $treeDirs) {
    $sourceDir = Join-Path $root $tree
    if (-not (Test-Path -LiteralPath $sourceDir)) { throw "Expected release directory not found: $tree" }
    foreach ($item in Get-ChildItem -LiteralPath $sourceDir -Recurse -Force -File) {
        $relative = Join-Path $tree $item.FullName.Substring($sourceDir.Length).TrimStart([char]92, [char]47)
        if (Test-PublishableSourcePath $relative) {
            $copyPlan += [pscustomobject]@{ Source = $item.FullName; Relative = $relative }
        }
    }
}

$requiredFiles = @(
    "gradlew", "gradle\wrapper\gradle-wrapper.jar", "app\src\main\AndroidManifest.xml",
    "app\src\test\resources\synthetic-timetable.pdf",
    "app\src\test\resources\timetable-list-template.xlsx",
    "app\src\androidTest\assets\parser-fixtures\synthetic-timetable.pdf",
    "app\src\androidTest\assets\parser-fixtures\timetable-list-template.xlsx",
    "app\src\androidTest\assets\parser-fixtures\timetable-list-template.csv",
    "docs\templates\timetable-list-template.xlsx", "docs\templates\timetable-list-template.csv"
)
foreach ($required in $requiredFiles) {
    if ($copyPlan.Relative -notcontains $required) { throw "Distribution plan is missing: $required" }
}

# Republishing from the same folder is the normal workflow, so an existing nested git
# repository (the one you push to GitHub) survives a rebuild. All recursive targets
# are confined to the validated project subdirectory and filename-safe version.
$gitBackup = Join-Path $distDir ".$stagingName.git-backup"
$stagingGit = Join-Path $staging ".git"
if (Test-Path -LiteralPath $gitBackup) { Remove-Item -LiteralPath $gitBackup -Recurse -Force }
if ((Test-Path -LiteralPath $staging) -and (Test-Path -LiteralPath $stagingGit)) {
    Move-Item -LiteralPath $stagingGit -Destination $gitBackup
}
if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Force -Path $staging | Out-Null
if (Test-Path -LiteralPath $gitBackup) {
    Move-Item -LiteralPath $gitBackup -Destination $stagingGit
}

$stagedFiles = foreach ($entry in $copyPlan) {
    $target = Join-Path $staging $entry.Relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $entry.Source -Destination $target -Force
    Get-Item -LiteralPath $target
}

$secretHits = $stagedFiles |
    # The packager itself declares these patterns, so it always self-matches.
    Where-Object { $_.Name -ne "package-release.ps1" } |
    Where-Object { $_.Extension -in @(".java", ".xml", ".gradle", ".ps1", ".py", ".md", ".properties", ".pro") } |
    Select-String -Pattern 'storePassword=\S|keyPassword=\S|C:\\Users\\|D:\\|\.edu(\.cn)?\b'
if ($secretHits) {
    foreach ($hit in $secretHits) { Write-Warning "$($hit.Filename):$($hit.LineNumber): review a blocked publication pattern (value hidden)." }
    throw "Possible hardcoded credentials in the distribution."
}

foreach ($required in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $staging $required))) { throw "Distribution is missing: $required" }
}

$zipPath = Join-Path $distDir "$stagingName-source.zip"
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
Add-Type -AssemblyName System.IO.Compression | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

$archive = [System.IO.Compression.ZipFile]::Open($zipPath, "Create")
try {
    foreach ($item in Get-ChildItem -LiteralPath $staging -Recurse -Force -File |
            Where-Object { $_.FullName -notmatch ([regex]::Escape("$stagingGit" + [char]92)) }) {
        $relative = $item.FullName.Substring($staging.Length).TrimStart([char]92).Replace([char]92, "/")
        $entry = $archive.CreateEntry("$stagingName/$relative", [System.IO.Compression.CompressionLevel]::Optimal)
        $target = $entry.Open()
        $source = $item.OpenRead()
        try { $source.CopyTo($target) } finally { $source.Dispose(); $target.Dispose() }
    }
} finally {
    $archive.Dispose()
}

$summary = Get-ChildItem -LiteralPath $staging -Recurse -Force -File |
    Where-Object { $_.FullName -notmatch ([regex]::Escape("$stagingGit" + [char]92)) } |
    Measure-Object -Property Length -Sum
Write-Host "Version:              v$Version"
Write-Host "Distribution folder:  $staging"
Write-Host ("Distribution zip:     {0} ({1:N0} files, {2:N2} MB)" -f $zipPath, $summary.Count, ($summary.Sum / 1MB))
