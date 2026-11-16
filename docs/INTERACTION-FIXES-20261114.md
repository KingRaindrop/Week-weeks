# 交互修复验证（2026-11-14）

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

## 基线与范围

本轮开始前的 app/src、构建配置、起始 diff/status 保存在 源码基线（本地 `artifacts/backups/interaction-fixes-20261114-143532/`，未入库）。旧主题备份、旧 APK 均保留。首次修复交付阶段没有安装；之后应用户明确要求完成下述 MuMu 覆盖安装和隔离测试。始终未卸载、清数据、提交 Git 或发布。

## 已修复

1. **首次异步结果覆盖设置**：首次 PDF 结果与恢复结果共用前台/恢复/设置浮层准入。提醒弹窗采用相同保护。设置行先移除浮层后打开对应窗口，主题色行也已同步。
2. **导入恢复**：[ImportFlowState](<../app/src/main/java/com/weekweeks/app/ImportFlowState.java>) 明确区分 REVIEW、PERIODS、RESULT、CALENDAR、DONE；[Bundle codec](<../app/src/main/java/com/weekweeks/app/ImportFlowStateCodec.java>) 保存解析数据而非仅保存 URI，因此可见预览、等待中的文本结果可恢复。恢复已有编辑器/设置草稿后再处理导入，避免抢占其令牌。
3. **提交后不重放**：持久化成功后先转 RESULT 并清除可提交数据，再渲染/关闭。结果及校历阶段恢复不会回到提交分支。
4. **保存失败保留**：导入确认按钮改为 show 后自定义点击，失败不关闭；每次尝试复制解析课程，防止失败后重试引用已变更。校历保存返回 boolean，失败保留日期和窗口。
5. **节次续接**：不足提示转 PERIODS；保存节次后回 REVIEW。取消提示或节次设置视为取消本次导入，不反复弹不足提示。节次仍不足时再次显示说明。
6. **校历草稿**：[CalendarSetupDialog](<../app/src/main/java/com/weekweeks/app/CalendarSetupDialog.java>) 独立管理日期输入、子日期选择器、未确认的年月日及销毁清理。首次校历和手动校历均保存草稿。
7. **容量上限**：[ImportPlanner](<../app/src/main/java/com/weekweeks/app/ImportPlanner.java>) 先匹配已有课程，再对新增检查上限。已满时仍可更新教室/老师，相同课程仍准确标记“已存在”。
8. **设备测试时序**：关闭后等待主线程空闲，再检查所有权，避免 OnDismiss 尚未执行时误判。同步旧字段断言并新增恢复/失败路径设备检查。

未改加密存储格式、课程解析算法或配色规则，未新增 Gradle 模块。

## 验证证据

最终执行：

```powershell
$env:JAVA_HOME='<JDK 17 目录>'
$env:ANDROID_HOME='<Android SDK 目录>'
& .\tools\verify-reminders.ps1
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintRelease assemblePastel compileDebugAndroidTestJavaWithJavac --console=plain
```

| 项目 | 结果 |
| --- | --- |
| Debug JUnit | 205 项，0 失败、0 错误 |
| Release JUnit | 205 项，0 失败、0 错误 |
| 提醒服务替身回归 | 65 + 54 项通过 |
| Release lint | 0 错误、8 警告，任务成功 |
| Pastel APK | 构建成功，主源码无文件晚于 APK |
| Android 设备检查源码 | 编译成功；后续应用户要求在 MuMu/API 32 隔离包实际执行，返回 PASS（见下文） |
| git diff --check | 通过，仅行尾转换提示 |

新增 7 项纯导入生命周期行为测试和 1 项已满容量行为测试。原接线测试随拆分更新；不能把源码字符串断言等同设备交互证明。

验证过程中首次因 Gradle 外部缓存锁访问被拒，获批准后执行；随后 6 个旧方法/字段接线断言失败，更新到显式状态流程后全部通过。Gradle 10 弃用提示和原有 lint 警告仍保留，不宣称零警告。

设备源码 [ImportRecoveryDeviceChecks](<../app/src/androidTest/java/com/weekweeks/app/ImportRecoveryDeviceChecks.java>) 包括首次结果不覆盖设置、Bundle 往返、失败保留预览、Activity 重建恢复及校历失败保留，限定隔离 audit 包。课程失败测试使用 storageLocked 防护路径、校历使用 Saver 返回 false；并未模拟真实磁盘损坏或加密器故障。首次修复交付时只编译，后续隔离包实测见末节。

## 安装包

修复版 APK（本地 `artifacts/week-weeks-mist-interaction-fixes-20261114.apk`，未入库）：21,535,050 字节。

- applicationId：`com.weekweeks.app.pastel`
- versionName：`1.2.7-pastel`，versionCode：`10`，debug 签名
- SHA-256：`1D91968A2E8B813469018A3AB731DF5287684AF12D932AC9A64F768289C251E1`

## 验收边界

- 初次交付仅完成编译；之后已执行下述 MuMu/API 32 自动化检查。没有进行全应用人工交互或完整前后台/息屏/通知矩阵验收。
- Android 16/API 36 通知实际送达、静音/横幅/锁屏表现仍需实际设备验证。
- 保存的状态针对 Android saved-instance-state 恢复；强制停止、主动清任务等不保证恢复未提交草稿。进程在持久化写入与系统保存状态之间被强杀的跨进程事务一致性不属于这里的配置重建保证。
- PDF 选择器尝试保留 provider 提供的持久读取授权；不支持时保留原有临时读取回退，不保证外部文档撤权或删除后仍能重新解析。

## 用户授权后的 MuMu 实测（2026-11-14）

### 安装与设备

- 连接 `127.0.0.1:16384`，系统 API 32。ADB 的 `SM_A546E` 是模拟器属性，不代表物理手机。
- 核对交付 APK SHA-256 后 `adb install -r` 成功；未卸载、清数据或迁移数据。
- 已安装预览 `com.weekweeks.app.pastel`，versionName `1.2.7-pastel`、versionCode `10`。
- 通过 [隔离构建脚本](<../tools/course-audit.init.gradle>) 构建并覆盖安装 `com.weekweeks.app.audit` 和 `.audit.test`，测试数据不进入预览版/正式版数据目录。没有运行清数据命令。

### 实际执行

```powershell
.\gradlew.bat -I tools/course-audit.init.gradle assembleDebug assembleDebugAndroidTest --console=plain
adb -s DEVICE shell am instrument -w com.weekweeks.app.audit.test/com.weekweeks.app.CourseCardDeviceTest
```

完整测试执行入口依次调用 PasteImportDeviceChecks、WeekPickerDeviceChecks、ModalDeviceChecks、ImportChainDeviceChecks、ImportRecoveryDeviceChecks，最后进行布局/动作配置遍历。任何一处抛出异常都不会打印 PASS。本次返回明确 **PASS**，不仅仅是 adb 的退出码 0。

- 粘贴输入原文、选择范围、Activity recreate 恢复、放弃确认和关闭清理。
- 数字周选择器：三种语言、深浅色、窄宽屏和字体配置的 108 组检查及重复点击入口检查。
- 课程/事件快速编辑与详情入口互斥、关闭动画期间拒绝底层点击、详情到编辑交接、两类草稿 Activity 重建恢复。
- 节次设置窗口打开时导入结果等待，关闭后出现预览；预览不被其他设置窗口叠加，取消后释放令牌。
- 设置浮层打开时首次解析结果不覆盖；关闭后出现预览。
- 真机框架 Bundle 对象保存/恢复无 URI 文本数据；已提交 RESULT 不再含可提交 payload。
- 模拟 storageLocked 导入失败保留预览及数据；可见导入预览经 Activity recreate 后恢复。
- 校历 Saver 返回 false 时窗口与日期保留，草稿 State Bundle 往返正确。
- 648 组课程卡布局/动作配置、徽标缩放、22 项主题/对比角色检查。旧裁剪对照仅在脱离窗口的测试树中复现 72 组，不是当前界面出现裁剪。

记录：本次测试输出（本地 `artifacts/mumu-interaction-run1-20261114.txt`，未入库）。测试入口的末尾摘要仍是旧短文，但新增检查在其 PASS 之前依次执行。

### 最后状态与边界

- 已将预览版切回前台，`am start -W` 返回 `Status: ok`，`topResumedActivity` 为预览 MainActivity。
- 屏幕截图（本地 `artifacts/mumu-preview-20261114.png`，未入库） 已从设备保存；当前模型不支持读取图片，所以未宣称人工视觉审查通过。
- 读取的 crash buffer（本地 `artifacts/mumu-crash-buffer-20261114.txt`，未入库） 只见 Google Play 服务的既有异常，未见本次应用崩溃。未清空系统日志。
- 隔离测试基于 debug 配置，未加载字体 assets；不能把这些结果等同预览包的全部字体/视觉验收。
- 未进行真实文件选择器 PDF 解析到最终持久化的端到端导入、真实磁盘写满/加密故障、完整节次编辑续接与校历 picker 跨重建的设备矩阵；相关状态逻辑已有单测/接线验证，后续可继续扩展实际设备测试。
- API 32 结果不能代替 Android 16/API 36 通知权限和送达验证。
