# 交互复查结果

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

本轮为检查，不是修复：未修改生产逻辑，未生成新版交付 APK，未发布 release。新增隔离设备测试和测试构建入口。

## 总结

确认 4 个交互缺陷（可归为 3 类），另提出 2 项可访问性/触摸优化和 1 项待验证风险。四项缺陷均在 MuMu Android 12 / API32 的 `com.weekweeks.app.audit` 隔离应用中复现。没有调用课程/日程保存或设置确认；没有覆盖正式应用或清除数据。

定向复现原始日志（本地 `artifacts/interaction-audit-20261115/focused-reconnected-instrumentation.txt`，未入库）以 `INTERACTION AUDIT COMPLETE`、`INSTRUMENTATION_CODE: -1` 结束。这表示复现检查完成，**不是这些 BUG 已修复或应用无 BUG**。

## 已确认问题

### 1. 中优先级、低频：特殊文本组合绕过课程放弃修改确认

- 原教室为 `A|B`、教师为 `C`；改为教室 `A`、教师 `B|C`，然后关闭编辑器。
- 结果：直接关闭，没有“放弃修改”确认。未保存的真实修改被丢弃；已有持久化课程不受影响。
- 对照：普通教室文字修改后取消，确认框正常出现。
- 原因：[课程 snapshot](<../app/src/main/java/com/weekweeks/app/PastelCourseSheet.java#L1821-L1830>)用未转义的 `|` 连接字段，两组不同字段产生相同字符串；[取消判断](<../app/src/main/java/com/weekweeks/app/PastelCourseSheet.java#L232-L240>)误判为未修改。
- 建议：逐字段比较或长度编码，不使用可与用户文本冲突的裸分隔符。保持现有丢弃确认交互，不扩大到存储格式迁移。
- 证据：设备日志:1–4（本地 `artifacts/interaction-audit-20261115/focused-reconnected-instrumentation.txt#L1-L4`，未入库）。测试使用真实编辑窗口及控件输入、程序化取消，并非手指输入录像。

### 2. 中优先级：语言暂选在旋转后丢失

- 打开语言弹窗，暂选另一语言但不确认，切换横竖屏。
- 结果：弹窗消失，暂选状态丢失；已保存语言保持不变。
- 原因：[语言对话框](<../app/src/main/java/com/weekweeks/app/MainActivity.java#L3536-L3562>)仅用局部数组保存暂选，没有接入 settings draft 捕获和恢复。
- 建议：复用已用于主题模式的草稿机制，保存暂选值，确认后才持久化，取消后清除草稿。
- 证据：设备日志:7–10（本地 `artifacts/interaction-audit-20261115/focused-reconnected-instrumentation.txt#L7-L10`，未入库），断言实际配置方向已变为横屏。

### 3. 中优先级：强调色暂选在旋转后丢失

- 打开强调色弹窗，选择不同色块但不确认，切换横竖屏。
- 结果：弹窗及暂选消失；已保存强调色没有被提前修改。
- 原因：[强调色对话框](<../app/src/main/java/com/weekweeks/app/MainActivity.java#L3613-L3651>)同样只保存局部暂选，未注册草稿；[色块点击](<../app/src/main/java/com/weekweeks/app/MainActivity.java#L3691-L3694>)仅更新局部选择。
- 建议：恢复暂选索引，必要时同时恢复滚动位置，与语言/主题行为保持一致。
- 证据：设备日志:13–16（本地 `artifacts/interaction-audit-20261115/focused-reconnected-instrumentation.txt#L13-L16`，未入库），实际切回竖屏。

### 4. 中优先级：日程日期选择器旋转后丢失未确认日期

- 新增日程并输入标题，打开“选择日期”，选择另一日期但不按确定，切换横竖屏。
- 结果：父级日程编辑器及标题恢复，但日期选择器消失；暂选日期未恢复，日程仍是此前日期。
- 原因：[日期选择器](<../app/src/main/java/com/weekweeks/app/PastelCourseSheet.java#L1059-L1064>)是局部对象，没有纳入子窗口生命周期；[日程状态捕获](<../app/src/main/java/com/weekweeks/app/PastelCourseSheet.java#L854-L867>)只保存已确认到 draft 的日期。[销毁清理](<../app/src/main/java/com/weekweeks/app/PastelCourseSheet.java#L1614-L1617>)也未显式持有并处理这个 picker。
- 建议：记录子选择器是否打开及其暂选年月日，重建时恢复；父窗口销毁时显式关闭子窗口。用户取消日期选择时仍保持原日期。
- 证据：设备日志:19–25（本地 `artifacts/interaction-audit-20261115/focused-reconnected-instrumentation.txt#L19-L25`，未入库）。测试操作真实附着的 DatePicker，未调用日期确认或日程保存。未将可能的 WindowLeaked 风险当作本轮已证实的崩溃。

## 可优化处（不混同为普通触摸逻辑故障）

### 周标题的无障碍名称应包含当前周/日期

[周标题](<../app/src/main/java/com/weekweeks/app/PastelTimetableView.java#L227-L234>)设置了固定 contentDescription；[数据绑定](<../app/src/main/java/com/weekweeks/app/PastelTimetableView.java#L331-L334>)更新可见文本却未同步描述。建议让描述包含当前周与日期范围，再附加选择周的操作提示。静态语义问题明确，但本轮未开启 TalkBack 验证实际播报，不声称完成读屏验收。

### 保持视觉尺寸，扩大常用按钮实际点击区域

[上/下周按钮](<../app/src/main/java/com/weekweeks/app/PastelTimetableView.java#L233-L241>)仅 38×40dp，本周按钮 43×40dp；[课程编辑关闭按钮](<../app/src/main/java/com/weekweeks/app/PastelCourseSheet.java#L251-L255>)36×36dp。建议在不重做 UI 的前提下扩展至至少 48dp 的触摸目标，并防止相邻扩展区域重叠。现有按钮可点击，这是容错优化，不是已证实的点击失效。

## 待验证风险

设置页使用同一 Activity 中的 overlay。[挂载逻辑](<../app/src/main/java/com/weekweeks/app/MainActivity.java#L3369-L3376>)未显式隔离底层无障碍节点；可能出现 TalkBack 焦点进入底层周导航/快捷操作的情况。应先用 TalkBack 实测焦点遍历，再决定是否隐藏底层 accessibility descendants 并在关闭时恢复。**本轮没有确认无障碍穿透，更没有证据表明普通屏幕触摸会穿透。**

## 已通过的既有回归

广泛回归日志（本地 `artifacts/interaction-audit-20261115/baseline-instrumentation.txt`，未入库）为明确 PASS、result -1。覆盖：

- 粘贴导入文字及选区的 Activity 重建恢复、丢弃保护与关闭清理。
- 课程/日程编辑草稿恢复、重复入口拦截、详情到编辑的窗口衔接、动画关闭期间的 ownership。
- 导入结果等待已有窗口关闭、取消后不复活、失败保留审核数据及重建恢复。
- 周选择器布局与重复入口保护。
- 648 组课程卡片布局/操作配置和徽标缩放；72 组旧布局裁切复现是测试对照，不代表当前布局失败。

这些既有测试包含真实窗口与程序化操作，也包含离屏布局测量，不等于 648 次物理屏幕触摸测试。没有穷尽所有设备、Android 版本、后台杀进程、提醒权限及读屏路径。

## 测试资产与边界

- [定向设备测试](<../app/src/androidTest/java/com/weekweeks/app/InteractionAuditDeviceTest.java>)。
- [隔离测试构建入口](<../tools/interaction-audit.init.gradle>)。
- 定向测试中曾修正测试专用清理方法名；ADB 服务重启造成设备未连接后，重新连接指定 MuMu 地址才运行成功。没有把编译失败或 ADB 退出码当作交互结论。
- 生产源代码本轮未修改；这四项问题仍待修复。建议先统一语言/强调色及日期子窗口恢复，再修复特殊文本脏状态比较；无障碍和触摸区域优化可作为后续小批次。
