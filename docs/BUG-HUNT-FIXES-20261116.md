# 后续缺陷修复与验证（2026-11-16）

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

继续检查上一轮六项交互修复后的当前工作树。本轮修复以下 12 类问题，保留项目原有未提交修改；没有提交 Git、发布、覆盖安装正式版或现有预览版。修改前快照位于 `artifacts/backups/bug-hunt-20261116-start/`。

| # | 触发条件与原问题 | 修复后的行为 |
| --- | --- | --- |
| 1 | CSV/TSV 引号内或 XLSX 单元格中的课程名以 `#` 开头，被误当注释，课程静默漏掉；坏行也可能绕过校验 | 分开识别原始注释与单元格内容；保留课程并正常校验整行 |
| 2 | Excel 在有效内容右侧保留仅格式空单元格，解析器报列数超过表头 | 去掉行尾无值单元格；中间空位和尾部非空额外列仍保留并校验 |
| 3 | PDF 上课周表达式超过 60 字，先截断再校验会丢失末尾周次或隐藏非法第 31 周 | 校验完整表达式；过长合法表达式压缩为等价周次区间 |
| 4 | 周选择器打开时旋转屏幕，窗口及滚动位置丢失 | 保存窗口草稿和滚动位置；重建后恢复，选择/取消后不会重新弹出 |
| 5 | 日期定位窗口暂选日期后旋转，窗口及暂选日期丢失 | 保存系统选择器状态；确认后才跳转，取消保留当前浏览日期 |
| 6 | 课程数据无法解密、个人日程正常时，日程页新增及学期外格子长按被错误禁止 | 日程入口由独立的日程存储锁判断；课程入口继续受课程存储锁保护 |
| 7 | 日程编辑表单滚动后重建，页面跳回顶部 | 保存偏移，在有效布局后恢复；关闭时释放滚动控件引用 |
| 8 | 用户正在等待测试提醒，授予精确闹钟权限广播被当成重启，测试提醒遭取消 | 仅重启/应用更新清理短期测试，授权事件保留测试并恢复正式提醒 |
| 9 | 一类数据不可读时，另一类的提醒不能更新或覆盖未知旧计划；启动迁移及删除课表也可能丢掉独立日程提醒 | 按课程/日程分区替换；保留不可读分区，处理请求码冲突；先加载日程再迁移课程，删除课表只清课程提醒 |
| 10 | SharedPreferences 写盘失败但内存已改变，旧闹钟因 generation 不匹配而被拒绝 | 提醒计划通过事务保存；失败回滚旧缓存和 generation，原计划及后续提醒继续有效 |
| 11 | 上海保存的周一开学日切换柏林后变成周日，教学周错位；切东京则首日课程被排除 | 保存校历时区身份，按原年月日重建当前当地零点；浏览日期及重建草稿也保留本地日期 |
| 12 | 切换时区后提醒仍使用旧绝对时间；时间向后调整时，已过滤日程无法恢复 | 系统时区/时间变化后从完整加密课程、日程源重算；UI 与后台共用解码和时间规则，不改写源密文 |

## 实现与数据保护

- `AcademicCalendarStore` 为旧的校历毫秒值增加保存时区。读取时转换年月日，不用统一毫秒偏移处理夏令时。
- `StoredTimetableSnapshot` 统一前台与后台的只读加载；`ReminderSourceRebuilder` 从完整源重新生成提醒。`TIMEZONE_CHANGED`、`TIME_SET` 已注册到现有接收器。
- `ReminderScheduler.replaceSchedule(context, entries, coursesReadable, eventsReadable)` 保留不可读分区；冲突请求码优先让给保留项。两分区都不可读时不变更；局部更新且混合提醒计划本身不可读时拒绝覆盖。
- 写入失败使用已有 `SafePreferences`/`PreferenceTransaction` 回滚，而不是仅检查 `commit()` 返回值。
- 本轮没有课程/日程 JSON 格式迁移；正常用户数据没有被测试读取或改写。设备数据夹具仅使用 `com.weekweeks.app.audit`，涉及偏好的测试会备份并恢复该测试包的偏好和提醒计划。

旧版本未记录校历原时区。如果用户已经跨区后才首次运行此版本，只能固定首次观察到的本地日期，不能无歧义推断原先保存的年月日；这类历史校历需要核对一次日期。之后的时区切换可按保存身份恢复。

## 验证结果

| 验证 | 最终结果 |
| --- | --- |
| Debug JUnit | 345 项，0 失败、0 错误、0 跳过 |
| Release JUnit | 345 项，0 失败、0 错误、0 跳过 |
| 提醒行为脚本 | 101 项调度/接收器检查 + 61 项诊断检查通过；Manifest 授权广播检查通过 |
| 新增设备整合回归 | `NAVIGATION FIXES PASS`，含存储隔离、迁移、删除、真实旋转及实际滚动偏移 |
| 后台时区设备回归 | `TIMEZONE CHECKS PASS`；直接调用接收器并检查实际加密计划，不启动 Activity |
| 上轮六项交互回归 | `INTERACTION FIXES PASS` |
| 原有完整设备回归 | 明确 `PASS`；包含导入恢复、模态衔接、周选择器及 648 组卡片布局/动作配置 |
| Android 解析器回归 | `PARSER PASS`；覆盖 XLSX SAX/ZIP、CSV 编码、PDF 完整字段快照等 |
| Debug / Release Lint | 各 0 错误、7 项原有警告 |
| 正常 Debug / Pastel 构建 | 成功，正常 Debug 包名恢复为 `com.weekweeks.app` |
| 最终源哈希与差异检查 | 构建后源码哈希未变化；`git diff --check` 通过，仅原有行尾转换提示 |

本轮新增 21 项 JUnit：10 项解析回归、5 项校历日期测试、6 项后台时间规则测试。原有一个读取源码的结构测试随加载逻辑抽取更新了定位；对应运行行为由设备测试补充验证。

修复前证据包括：解析新增用例的 8 项失败、授权时测试提醒取消、提醒保存失败后 generation 变化，以及真实旋转后两个导航窗口消失。时区复现直接调用旧日期规则，确认向西教学周偏移、向东首日提醒缺失及日程提醒提前/延后 7 小时。

初次整合中修正了测试对屏幕高度的假设：表单和周选择器必须先具备实际滚动空间，再断言恢复偏移。最终保留严格偏移校验，未通过删掉断言取得通过。周选择器覆盖同尺寸重建的精确偏移，以及旋转后按新窗口范围限制偏移。

设备为 MuMu Android 12 / API 32。时区检查仅改变 audit 测试进程的 `TimeZone`，没有修改设备全局时区；没有宣称完成真实系统时区设置 UI、全部厂商后台限制、API 36 或 TalkBack 验证。调度脚本使用 Android 服务替身，不等于实际通知必达。旧布局的 72 个裁切结果是历史对照，不是当前布局失败。测试构建省略可选字体 assets，预览包正常包含本机已有 assets。

## 交付与证据

预览安装包（本地 `artifacts/week-weeks-bugfix-20261116.apk`，未入库）

- 包名：`com.weekweeks.app.pastel`
- 版本：`1.2.7-pastel`，versionCode `10`，debug 签名；未递增发布版本。
- 文件大小：21,574,757 字节。
- SHA-256：`5EA589EE2CC23D7976B3BBD6FA96FA780A6C8076374664B2A7EA1B45240C332F`
- 仅生成预览包，没有安装或覆盖用户现有正式版/预览版；结束时已恢复现有正式应用到前台。

主验证目录：`artifacts/bug-hunt-20261116/`。

- `integrated-device.log`：本轮整合回归与时区回归。
- `six-fixes-device.log`、`broad-device.log`、`parser-device.log`：原有行为回归。
- `reminder-harness.log`、`unit-debug.json`、`unit-release.json`：逻辑结果。
- `final-build.log`、`lint-debug.txt`、`lint-release.txt`：最终构建及静态检查。
- `changed-files.txt`：相对本轮开始备份的修改文件清单。
- `final-source-hashes.json`、`preview-metadata.json`、`preview-sha256.txt`：构建追踪信息。
- `navigation-before-device.log`：导航窗口修复前的实际复现。
- `integrated-first-attempt.log` 等初次运行记录保留，用于解释测试夹具修正。

补充证据：`artifacts/parser-bugfix-20261116/`、`artifacts/reminder-commit-fix-20261116/`。旧时区复现程序位于 `build/timezone-audit-20261116/TimezoneAudit.java`。

最终正常构建命令：

```powershell
$env:JAVA_HOME='<JDK 17 目录>'
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug assemblePastel --offline --console=plain
```

新增设备入口使用 `tools/navigation-audit.init.gradle`；执行后必须检查明确的 PASS 文本，不能只依赖 ADB 返回码。
