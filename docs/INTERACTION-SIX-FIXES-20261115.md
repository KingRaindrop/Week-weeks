# 六项交互修复与验证

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

对应 [修复前检查报告](PROJECT-REVIEW-20261115.md) 的六项问题。本轮从 2026-11-15 开始，完成了源码修复、单元测试、隔离设备回归及预览 APK 构建。没有安装或覆盖正式应用/预览应用，没有卸载、清数据、提交 Git 或发布。

修改前的主 Java 源码、资源、设备测试及起始 diff 已保存在 `artifacts/backups/interaction-six-fixes-20261115-234153/`。仓库原有未提交修改继续保留。

**最终行为**

1. **重叠课程可分别查看、编辑。** 周视图将同一星期互相重叠的节次合并为“2 门课／点按选择”入口，点击后列出课程名、节次、时间和教室，再进入所选课程详情。连接重叠、包含关系以及完全相同的时段均纳入分组；相邻但不重叠的节次、不同星期保持独立。课程没有被删除或合并存储。
2. **导入前提示时间冲突。** 预览同时检查导入课程之间及与已保存课程的冲突，区分单双周、周次交集、重复课程、地点更新及容量限制。显示总冲突对数和前 12 对示例；超过 12 对会明确提示余量。用户可取消修正文件，或确认保留课程后通过多课程入口处理。预览中原先只列前五门课程的限制已移除，完整课程清单可滚动查看。
3. **学期外空白格带入准确日期。** 格子长按动作传递完整日期。测试验证周二/周三等非当前星期的格子均能使用所点日期；通用新增入口继续使用原来的默认日期规则。
4. **课程放弃修改确认不再被分隔符混淆。** 草稿快照的文本字段改用长度编码。教室 `A|B`、教师 `C` 与教室 `A`、教师 `B|C` 能正确识别为不同；取消放弃确认后仍保留表单。
5. **语言和强调色暂选随配置重建恢复。** 两类弹窗接入现有草稿捕获/恢复机制；强调色也保存滚动位置。仅确认才应用设置，取消清理草稿，再次旋转不会让已取消弹窗重新出现。新的重叠课程选择窗也保存待选课程 ID，并通过现有窗口所有权机制衔接详情。
6. **日程日期子窗口恢复与清理完整。** 保存 DatePickerDialog 的状态，旋转后恢复选择器和未确认日期。取消保持父草稿原日期，确认才应用选择；父编辑器销毁或关闭时显式关闭子选择器。

其中第 1、2 条共同修复“导入后课程遮挡”；第 5 条分别修复语言和强调色两个问题，总计对应原报告的六个缺陷。新增文案提供简体中文、繁体中文和英文。

**实现位置**

- `CourseOverlapLayout.java`：不依赖 Android 的重叠分组，使用兼容 API 23 的排序方式。
- `ImportPlanner.java`：不修改输入数据的导入冲突预览计算。
- `PastelTimetableView.java`：多课程入口、将格子日期传给新增日程。
- `MainActivity.java`：课程选择窗口、语言/强调色草稿、完整导入预览及日程默认日期。
- `PastelCourseSheet.java`：无歧义草稿比较及日期子窗口生命周期。
- `strings_overlap.xml`：三种语言的冲突与多课程入口文案。

**验证结果**

| 检查 | 结果 |
| --- | --- |
| Debug JUnit | 324 项，0 失败、0 错误、0 跳过 |
| Release JUnit | 324 项，0 失败、0 错误、0 跳过 |
| Debug / Release Lint | 均为 0 错误、7 个已有警告 |
| 六项设备回归 | `INTERACTION FIXES PASS` |
| 已有全面设备回归 | 明确 `PASS`，包括导入、草稿、弹窗衔接、周选择器，以及 648 组课程卡布局/动作配置 |
| 正常 debug、pastel 构建 | 均成功；默认 debug APK 包名已恢复为 `com.weekweeks.app` |
| `git diff --check` | 通过，仅既有行尾转换提示 |

新增 9 项纯逻辑测试：5 项重叠分组、4 项导入冲突测试。设备入口为 `InteractionFixDeviceTest`，使用真实窗口控件、实际方向切换、附着视图的触摸事件分发；选择窗口逐一打开了两门重叠课程的详情。测试课程只临时加入隔离 Activity 内存，未保存课程或日程。

设备为 MuMu Android 12 / API 32，目标包 `com.weekweeks.app.audit`。已有全面回归通过 `CourseCardDeviceTest` 运行，其中包括离屏布局测量；648 组配置不等于 648 次物理触摸。测试构建不加载可选字体 assets，不能替代预览包所有字体、Android 13+ 返回手势、API 36、TalkBack 和后台杀进程场景的验收。

核心证据在 `artifacts/interaction-six-fixes-20261115/`：

- `focused-device.log`：六项最终通过记录。
- `broad-device.log`：已有全面回归通过记录。
- `unit-Debug.json`、`unit-Release.json`：测试结果汇总。
- `lint-debug.txt`、`lint-release.txt`、`final-build.log`：静态检查及构建记录。
- `overlap-grid.png`：虚构重叠课程的实际视图绘制图，已人工查看，数量与操作提示完整可见；它是课格渲染图，不是整机截图。
- `final-source-hashes.json`、`preview-sha256.txt`、`preview-metadata.json`：源码及交付包记录。

初次测试中修正了测试方法拼写、API 23 不支持的 Java 排序 API，以及日期窗口刚打开就查询已附着控件的测试时序。最终结果以上述通过日志为准；`first-focused-attempt.log` 保留了初次测试时序失败记录。

**预览安装包**

week-weeks-interaction-fixed.apk（本地 `artifacts/week-weeks-interaction-fixed.apk`，未入库）

- applicationId：`com.weekweeks.app.pastel`
- versionName：`1.2.7-pastel`，versionCode：`10`，debug 签名
- 大小：21,571,559 字节
- SHA-256：`5A85AA76A397249B8A9A27D826962116A5412DB3715987C955D09EE9D9D5AAE5`
- 已构建，未安装到正式版或现有预览版；版本号未做发布递增。

本轮最终构建命令为：

```powershell
$env:JAVA_HOME='<JDK 17 目录>'
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug assemblePastel --offline --console=plain
```

设备回归分别使用 `tools/interaction-fixes.init.gradle` 与 `tools/course-audit.init.gradle` 构建隔离包，安装前核对 applicationId。运行后需要检查明确的 PASS 文本，不能只依赖 ADB 返回码。

点击区域、读屏语义、渲染合并和大型类拆分等建议保留为后续优化，本轮完成了六项缺陷修复及直接相关的导入审核改善。
