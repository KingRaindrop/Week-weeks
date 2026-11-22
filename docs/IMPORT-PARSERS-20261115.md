# 课表解析兼容性后续增强（2026-11-15）

> 当前版本已支持带星期表头及节次的周课表与同星期纵向合并，见[周课表导入说明](IMPORT-PARSERS-20261119.md)。下文是当时的格式边界与验证记录。

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

本轮延续 [上一轮解析增强](<IMPORT-PARSERS-20261114.md>)，只修改解析及测试，不改变主题、加密存储、提醒或导入确认流程。

## 设备验证入口

新增 [ParserDeviceTest](<../app/src/androidTest/java/com/weekweeks/app/ParserDeviceTest.java>)，用于 Android 自带 SAX/ZIP/字符集实现的独立回归。测试仅读取测试包中的样本，不启动 Activity，不写用户课表存储。真实 XLSX/CSV 模板逐字段比较，PDF 样本计数，以及 GB18030、UTF-16、整份拒绝错误行、DTD/非 UTF-8 XML 拒绝检查。

仅将样本打包到 androidTest，不增加生产 APK 的课程样本或资源。

隔离构建与运行（不覆盖用户正式包）：

```powershell
$env:JAVA_HOME='<JDK 17 目录>'
$env:ANDROID_HOME='<Android SDK 目录>'
.\gradlew.bat -I tools/course-audit.init.gradle -PparserAudit=true assembleDebug assembleDebugAndroidTest --console=plain
# 仅安装上一步产生的 audit 应用与测试 APK，然后选择实际设备：
adb -s DEVICE shell am instrument -w com.weekweeks.app.audit.test/com.weekweeks.app.ParserDeviceTest
```

必须见到 `PARSER PASS` 才算运行通过；adb 进程退出成功不代表测试成功。此入口不覆盖文件选择器、内容提供方或真实窗口导入链。

本轮连接检查：ADB 无在线设备，历史 MuMu 端口 `127.0.0.1:16384` 拒绝连接；不将编译通过当作设备运行通过。

## PDF 修复

- 无 ToUnicode 字体的 UTF-16 编码证据现在按当前页的 `Tf` 字体资源隔离。此前同页的中文字体会使另一拉丁字体的 `Math` 等偶数字节课程名误译；`q/Q` 同时恢复字体映射与资源名，防止恢复状态后再次串扰。
- ToUnicode 的多码点映射现在在追加前检查剩余字符预算，跨文本项、跨页共享累计上限，数值沿用传入的 `maxStreamBytes`。合法多码点映射在精确边界仍可通过；不限制成“一字节只能一字符”。
- CMap `bfchar/bfrange` 条目计数必须是 0–65536 的整数，拒绝小数与整数截断溢出。
- 新增 7 项 PDF 回归。先在旧实现复现 5 项失败，再用独立 JDK17 + 真实 JUnit 验证 55 项通过。23 条真实样本完整字段快照不变：`7E35082D520879A0B1772BF8848FC4E573F8A1AAF366B2B97F31FE8C54BE0B69`。
- 无 ToUnicode 字体仍是启发式，不代表新增 WinAnsi/MacRoman 或任意字体编码的完整实现。OCR、Form、ObjStm、usecmap 等边界与上一轮相同。

## 列表文件兼容性

- 增加分号分隔 CSV，包括引号转义、跨行字段及包含逗号的周次；支持 Excel 常见的首部 `sep=` 声明（逗号、分号、Tab、半角/全角竖线，大小写不敏感）。
- 声明只在首个非空、非注释记录前识别，保留物理行号。重复、延后、无效或与内容不符的声明会报错，不静默忽略。
- 自动检测根据表头与必需字段证据选择，而非单纯统计标点，修复 `Math|Logic|Lab,Mon,1` 被错当竖线文本的问题。有效旧管道/TSV/逗号解释优先保留；复杂歧义建议写明确的 `sep=`。
- 新增 14 项列表回归（文本 10、文件入口 3、XLSX 1），独立 JDK17 + 真实 JUnit 的 59 项全部通过。三个正向样本先在旧实现复现失败。
- XLSX 的已分列单元格不重新按 CSV 解释。未改文件路由与 XLSX 生产实现，未放宽 XML、ZIP、字段/课程预算或文件全有或全无策略。

## 最终验证

- 集成 Gradle：Debug 307 项、Release 307 项，均 0 失败、0 错误、0 跳过；本轮新增 21 项。解析相关 114 项（PDF 55、文本 36、文件入口 10、XLSX 13）。
- `lintRelease`：0 错误、8 个既有警告。`assemblePastel`、`compileDebugAndroidTestJavaWithJavac` 均成功。
- 隔离 `-PparserAudit=true` 的 debug 应用及 androidTest APK 构建成功；合并测试清单确认 runner 为 `ParserDeviceTest`、目标包为 `com.weekweeks.app.audit`。三个解析样本均在测试 APK，生产预览 APK 不含 `parser-fixtures`。
- 静态安全审计、`git diff --check` 通过；提醒服务替身回归 65 + 54 项通过，权限广播注册检查通过。首次提醒检查受主机原 `JAVA_HOME` 值影响未启动 javac，显式指定 JDK17 后完成验证。
- Gradle 首次在受限模式无法写入用户缓存锁；批准相同命令访问后，全量验证和隔离测试构建分别成功（43 秒、8 秒）。未提高项目文件或解析安全限额。
- 构建仍有原有 SDK XML 版本提示、Java deprecated API 与 Gradle10 弃用提示；不是零警告构建。
- 无在线设备，未安装、未运行上述 Instrumentation，不宣称 Android SAX 或文件选择器实测通过。

集成验证命令：

```powershell
$env:JAVA_HOME='<JDK 17 目录>'
$env:ANDROID_HOME='<Android SDK 目录>'
.\tools\security-audit.ps1
.\tools\verify-reminders.ps1
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintRelease assemblePastel compileDebugAndroidTestJavaWithJavac --console=plain
```

## 交付与回退

本轮预览 APK（本地 `artifacts/week-weeks-mist-parser-followup-20261115.apk`，未入库）：21,555,133 字节，SHA256：

```text
1F2CBCCF148AD1EE9D057DE471F95E570101EEBE976658479A032DD9233F143B
```

校验文件（本地 `artifacts/week-weeks-mist-parser-followup-20261115.sha256`，未入库） 与构建输出哈希一致，复制后确认生产源文件无晚于 APK 的修改。

本轮修改前的 PDF、列表解析器与设备构建脚本已保存至 本轮备份目录（本地 `artifacts/backups/parser-followup-20261115/`，未入库）。未做 Git 提交、推送、GitHub release、清除数据或存储迁移。上一轮 [XLSX 模板](<templates/timetable-list-template.xlsx>) 与 [CSV 模板](<templates/timetable-list-template.csv>) 继续适用。

实际学校兼容性仍需更多脱敏样本验证：当前两个 PDF 样本字节相同，只代表一种真实版式；新功能有合成回归，但不据此承诺任意学校文件均可导入。
