# 构建、签名与验证 / Building Week weeks

返回 / Back: [简体中文](<../README.md>) · [繁體中文](<../README.zh-TW.md>) · [English](<../README.en.md>)

## 环境 / Requirements

| Component | Version |
| --- | --- |
| JDK | 17 |
| Android Gradle Plugin | 8.13.0 |
| Gradle Wrapper | 9.2.0 |
| Android SDK Platform | 36.1 (`android-36.1`) |
| targetSdk / minSdk | 36 / 23 |
| Build Tools | 36.1.0 |

用 Android Studio 打开仓库，或安装以上工具后使用 Wrapper。`local.properties` 可以保存本机 `sdk.dir`，不要提交本机路径。

Open the repository in Android Studio, or install the tools above and use the wrapper. Keep machine-specific SDK paths in the ignored `local.properties` file, not in source control.

```powershell
$env:JAVA_HOME="<JDK 17 directory>"
$env:ANDROID_HOME="<Android SDK directory>"
.\gradlew.bat assembleDebug
```

macOS / Linux: set the equivalent environment variables, then use `./gradlew`.

## 构建类型 / Build types

| Type | Application ID | Notes |
| --- | --- | --- |
| `debug` | `com.weekweeks.app` | Development build, debug signing, debug-only test hooks |
| `release` | `com.weekweeks.app` | Production build, release signing when configured, R8 and resource shrinking |
| `pastel` | `com.weekweeks.app.pastel` | Internal compatibility test entry, debug signing, not a separate product |

所有构建均默认使用同一套最新界面。`Pastel*` 类名和 `PASTEL_UI` 开关是内部实现名称，不是“清新版”产品定位。

All build types use the current interface by default. Internal `Pastel*` names and the `PASTEL_UI` flag do not identify a separate consumer edition.

`debug` and `release` share an application ID but normally have different signing keys. Android will not install one over the other when certificates differ. Do not uninstall an existing installation without backing up anything you need; uninstalling removes its local data.

```powershell
.\gradlew.bat assembleRelease
```

Output with signing configured:

```text
app/build/outputs/apk/release/app-release.apk
```

Without release signing, the output is `app-release-unsigned.apk`, which cannot be installed directly. Debug output is `app/build/outputs/apk/debug/app-debug.apk`.

## 发布签名 / Release signing

不要把私钥、密码或真实课表提交到仓库。自行构建时应配置自己的签名，不要依赖维护者本机的密钥。

Never commit signing keys, passwords or real timetable exports. Configure your own signing credentials when building independently.

Supported environment variables:

```text
WEEK_WEEKS_STORE_FILE
WEEK_WEEKS_STORE_PASSWORD
WEEK_WEEKS_KEY_ALIAS
WEEK_WEEKS_KEY_PASSWORD
```

Alternatively, copy the [template](<../secrets/release-signing.properties.template>) to `secrets/release-signing.properties` and fill it in locally. A user-level `~/.gradle/week-weeks-signing.properties` file is also supported. Environment variables override file values; if both properties files exist, the user-level file is loaded after the project-level file and can override it.

签名文件可命名为 `secrets/week-weeks-release.jks`。`keyAlias` 必须填写密钥库中实际存在的条目名；更名不意味着重建密钥或随意改 alias。现有维护者密钥在本轮品牌更名中被保留，复制后的文件经过一致性校验。

Use an alias that actually exists in your keystore. Renaming a product is not a reason to replace its signing key. The maintainer's existing key was preserved during the rename.

## 验证 / Verification

```powershell
.\gradlew.bat lintRelease testDebugUnitTest testReleaseUnitTest assembleRelease

# Behavioral tests using deterministic Android service doubles
.\tools\verify-reminders.ps1
```

提醒脚本要求 `JAVA_HOME`。它验证真实调度器与接收器的逻辑，但不验证 Android IPC、真实 Keystore 或设备通知送达。JUnit 同样不替代设备交互和视觉测试。

The reminder harness requires `JAVA_HOME`. It tests scheduler and receiver behavior with service doubles, not Android IPC, the real Keystore or notification delivery. Unit tests are not device or visual tests.

The latest interface-unification check passed 128 tests for each of debug and release, with no release lint issues. These are recorded results, not a live CI badge; see the [change log](<CHANGELOG.md>).

## 项目结构 / Source map

```text
app/src/main/java/com/weekweeks/app/
  MainActivity.java          Navigation, forms and persistence
  PastelTimetableView.java   Current timetable interface
  PastelCourseSheet.java     Course and event sheets
  PdfCourseParser.java       Text-based PDF extraction
  TextTimetableParser.java   Pasted timetable parsing
  SecureStorage.java        Android Keystore + AES/GCM
  ReminderScheduler.java    Local reminder scheduling
app/src/main/res/            Strings, icons and themes
app/src/test/               JUnit regression tests
docs/images/                README artwork and fictional-demo screenshots
```

## 可选字体 / Optional font

`app/src/main/assets/fonts/oppo_sans.ttf`（OPPO Sans 4.0 可变字体，约 21.7 MB）是专有字体，**不随本仓库分发**，已被 `.gitignore` 排除。需要该字体预设时，请自行取得授权后把文件放到该路径再构建。文件缺失时应用不会崩溃：`AppFonts` 读取失败会回退到系统字体，其余功能不受影响。

The OPPO Sans file is proprietary, is roughly 21.7 MB, and is **not redistributed here** (it is git-ignored). Obtain it under its own terms, then place it at that path before building. A missing file does not crash the app: `AppFonts` catches the failed load and falls back to the system font.

## 已知边界 / Known limits

- PDF parsing targets supported text-based timetable layouts; it does not perform OCR or promise universal school compatibility.
- Text import keywords remain Chinese in all UI languages.
- Data stays local. There is no complete backup/restore or cross-device sync feature; keep original import files.
- Renamed and compatibility packages have separate data directories. They do not automatically migrate data or uninstall older packages.
- The legacy helper `tools/verify-logic.ps1` also attempts a standalone `javac` check using compile stubs; those stubs are not a full Android SDK replacement. Prefer Gradle tasks for the complete application build.

历史发布快照不代表当前源码。发行脚本使用显式清单复制文件；修改文档或添加图片后，应先检查清单是否包含它们。本轮只更新清单，不运行会重建发行目录的打包过程。

Historical distribution snapshots are not the current source tree. The release packaging script uses an explicit file list; ensure new documentation and images are included before packaging. No release archive is generated as part of this documentation update.
