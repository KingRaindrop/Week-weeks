# 构建、签名与验证 / Building Week weeks

返回 / Back: [简体中文](<../README.md>) · [繁體中文](<../README.zh-TraditionalChinese.md>) · [English](<../README.en.md>)

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
.\gradlew.bat lintDebug lintRelease testDebugUnitTest testReleaseUnitTest assembleRelease

# Behavioral tests using deterministic Android service doubles
.\tools\verify-reminders.ps1
```

提醒脚本要求 `JAVA_HOME`。它验证真实调度器与接收器的逻辑，但不验证 Android IPC、真实 Keystore 或设备通知送达。JUnit 同样不替代设备交互和视觉测试。

The reminder harness requires `JAVA_HOME`. It tests scheduler and receiver behavior with service doubles, not Android IPC, the real Keystore or notification delivery. Unit tests are not device or visual tests.

2026-11-16 验证结果：Debug／Release 各 368 项单元测试通过；Lint 各 0 错误、7 项既有警告。Android 12／API 32 上的独立 audit 安装通过界面 993 项、时间轴 233 项、模式切换 456 项、课程布局 648 组及 22 组深色角色检查，并通过提醒与弹窗回归。这些是已执行的验证记录，不是实时 CI 状态；详见[变更日志](<CHANGELOG.md>)与[界面优化记录](<UI-REFINEMENT-20261116.md>)。

The November 16, 2026 checks passed 368 unit tests for each of debug and release. Lint reported 0 errors and 7 existing warnings for each. Device checks used an isolated audit installation on Android 12 / API 32; they do not establish coverage of every Android version or vendor.

### 隔离设备测试 / Isolated device tests

设备回归工具将目标包名改为 `com.weekweeks.app.audit`，测试包为 `com.weekweeks.app.audit.test`。安装前核验 APK 包名，勿将常规 debug APK 覆盖到已有正式安装。以下使用虚构课表夹具，只检查解析器：

```powershell
.\gradlew.bat -I tools/course-audit.init.gradle -PparserAudit=true assembleDebug assembleDebugAndroidTest
# Set $device to the ID reported by adb devices; verify the generated APK IDs first.
adb -s $device install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s $device shell am instrument -w com.weekweeks.app.audit.test/com.weekweeks.app.ParserDeviceTest
# Restore ordinary build outputs after using an audit init script.
.\gradlew.bat assembleDebug
```

必须核对输出中的 `PARSER PASS`，不能仅依据 ADB 的退出码。其余入口见 `tools/*-audit.init.gradle`；界面优化测试需要真实 OPPO Sans，时间轴测试仅在传入 `-e font oppo_sans` 时要求该字体。字体需自行取得，不包含在仓库中。课程／解析器测试入口会移除主应用字体资源，可在没有该字体的克隆中运行。

Require the explicit `PARSER PASS` marker, not only a successful ADB exit code. The UI refinement audit requires the separately obtained OPPO Sans font; the agenda timeline audit only requires it when invoked with `-e font oppo_sans`. The course/parser audit removes main font assets. Normal application builds fall back to the system font if the optional file is absent.

公开测试夹具位于 `app/src/test/resources` 和 `app/src/androidTest/assets/parser-fixtures`，均使用虚构内容，不依赖忽略的 `samples/` 目录或私人课表。导入模板可在[模板目录](<templates/>)下载；重新生成方法见[夹具生成器](<../tools/create-import-samples.py>)。

发布前替换合成 PDF 后，Debug／Release 各 368 项单元测试、Android 解析器 `PARSER PASS` 与常规 Debug 构建已重新验证通过。

## 项目结构 / Source map

```text
app/src/main/java/com/weekweeks/app/
  MainActivity.java          Navigation and feature integration
  PastelTimetableView.java   Current timetable interface
  PastelCourseSheet.java     Course and event sheets
  PdfCourseParser.java       Text-based PDF extraction
  TextTimetableParser.java   Pasted timetable parsing
  TimetableFileParser.java   PDF / XLSX / CSV / TSV / TXT dispatch
  XlsxTimetableParser.java   Spreadsheet list parsing
  DisplayMode.java           Course / agenda / combined content filters
  AgendaTimelineLayout.java  Event duration and overlap layout
  AcademicCalendarStore.java Calendar persistence and validation
  ModalCoordinator.java      Dialog ordering and recovery
  UiSemanticPalette.java    Theme surface, accent and error roles
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
- Spreadsheet and text imports expect one course per row; common Simplified Chinese, Traditional Chinese and English headers are supported. Legacy `.xls` files must be converted to XLSX or CSV.
- Data stays local. There is no complete backup/restore or cross-device sync feature; keep original import files.
- Renamed and compatibility packages have separate data directories. They do not automatically migrate data or uninstall older packages.
- The legacy helper `tools/verify-logic.ps1` also attempts a standalone `javac` check using compile stubs; those stubs are not a full Android SDK replacement. Prefer Gradle tasks for the complete application build.

历史发布快照不代表当前源码。发行脚本使用显式清单复制文件；打包前应核对新增源码、文档和测试资源是否在清单覆盖范围内。本次更新不生成发行归档。

Historical distribution snapshots are not the current source tree. The release packaging script uses an explicit file list; ensure new documentation and images are included before packaging. No release archive is generated as part of this documentation update.
