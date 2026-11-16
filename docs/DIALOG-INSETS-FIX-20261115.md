# 深色弹窗误关闭修复与 MuMu 回归

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

日期：2026-11-15（Asia/Shanghai）。

## 结论

已修复此前在 MuMu 确认的“点击深色弹窗内部标题却取消弹窗”问题。保留深色颜色、24dp 圆角、边框、字体和按钮样式；恢复 Material 原有的外部留白及触摸几何关系，正常点外取消仍有效。

## 最终生产改动

- [DialogSurfaceStyle.java](<../app/src/main/java/com/weekweeks/app/DialogSurfaceStyle.java>)：保留 Material 的 InsetDrawable 外层，只替换内层绘制表面；复制并恢复旧内层 bounds，防止 setDrawable 暂时把内层扩展到窗口外框。非 inset 窗口保持原样。不修改触摸监听器、取消策略或 dismiss 监听器。
- [MainActivity.java](<../app/src/main/java/com/weekweeks/app/MainActivity.java#L3943-L3947>)：深色 Material 弹窗调用共享方法。
- [ReminderStatusDialog.java](<../app/src/main/java/com/weekweeks/app/ReminderStatusDialog.java#L103-L107>)：相同问题同步修复。刷新、关闭、Handler 清理逻辑不变。
- 经典课程编辑器是特殊 DirtyConfirmDialog 路径，最终保留原来的背景设置，没有将其误当作 Material builder 弹窗处理。
- 没有修改课表解析、课程数据、加密存储、备份、提醒调度或主题切换策略。

## MuMu 实测

环境：MuMu，Android 12 / API32，ADB `127.0.0.1:16384`。仅安装隔离应用 `com.weekweeks.app.audit` 及测试包；没有覆盖正式或 pastel 应用，没有清除应用数据。测试结束恢复隔离应用原有主题和强调色值及键存在状态。

最终设备测试原始日志（本地 `artifacts/dialog-insets-mumu-20261115/final-instrumentation.txt`，未入库）末尾为 `DARK UI PASS`、`INSTRUMENTATION_CODE: -1`。

| 检查 | 结果 |
|---|---|
| 浅色、深色主题弹窗：注入屏幕触摸点击标题 | 弹窗保持 |
| 注入触摸选择“跟随系统” | 到达原始选项回调，选中项更新，尚未保存 |
| 注入触摸点击窗口外围透明 inset | 正常取消；草稿、owned dialog、modal token 释放 |
| 浅色、深色提醒状态弹窗：标题点击 | 弹窗保持 |
| 提醒状态弹窗刷新按钮触摸 | 不关闭弹窗 |
| 提醒状态弹窗外围触摸 | 关闭并调用清理回调 |
| 深色背景实际 bounds、重复样式应用 | 内层保持内缩，无 inset 叠加或扩展到外框 |
| 实际 Activity 横屏→竖屏，以及额外 recreate | 暂选主题恢复 |
| 取消及取消按钮 | 不保存暂选值、不复活已关闭草稿 |
| 恢复后确认浅色↔深色 | 保存并加载正确主题 |
| 确认已保存的相同主题 | 不多余重建 Activity |
| 22 浅色 + 22 深色色板 | 文本对比度与选择更新检查通过（离屏控件树，非屏幕触摸） |

主题与提醒弹窗新检查使用系统屏幕触摸事件注入，不是用 performClick 冒充屏幕测试；既有生命周期用例仍含程序化控件点击。不是物理触摸屏硬件或所有 Android 版本认证，也不是全应用所有路径回归。未声称进行逐像素截图视觉验收。

## 测试过程中的失败与修正

早期设备测试在深色选项断言处失败，相关日志保留在同一证据目录，没有覆盖。诊断确认 DOWN/UP 到达列表；列表实际点击回调可延迟执行，主线程短暂 idle 不代表回调已完成。最终测试通过 CountDownLatch 等待真实触摸触发的原始 OnItemClickListener（完整转发原回调），未修改产品选择逻辑，未以程序化点击绕过失败。

独立代码复核另发现 setDrawable 的 child bounds 初始化问题，最终实现已明确保留旧内层 bounds；以及经典编辑器非 Material-builder 路径应保持原样，已收回该处扩大的修改。最终复核未发现本轮新增阻断问题。

## 构建与静态检查

最终构建日志（本地 `artifacts/dialog-insets-mumu-20261115/final-build.txt`，未入库）：

- Debug 单测 315，通过；失败/错误/跳过均为 0。
- Release 单测 315，通过；失败/错误/跳过均为 0。
- lintPastel：0 error、7 warning。
- assemblePastel：成功。
- 新增 2 个源码接线守卫测试；它们不替代设备行为测试。

## 安装包

新版 pastel 预览安装包（本地 `artifacts/week-weeks-mist-dialog-insets-fixed-20261115.apk`，未入库）

- 包名：`com.weekweeks.app.pastel`，不是 audit 包。
- 版本：`1.2.7-pastel`，versionCode `10`。
- 大小：21,561,756 bytes。
- SHA256：`3B5A295DA920C9C9ADA156D5EE2E0EDFF53150DB66D5A8EB06C0618D534C3F2A`。
- 未上传 GitHub 或发布 release。设备运行验证使用同源隔离 Debug 构建；交付 pastel 包另行构建并校验包名，没有覆盖安装到用户原应用。
