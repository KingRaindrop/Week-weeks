# MuMu 暗色 UI 实测 — 2026-11-15

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

## 结论

两项已修复问题在 MuMu 设备侧检查通过；但原先的 Material 弹窗触摸风险已通过屏幕点击确认为缺陷：**暗色外观弹窗的标题内部点击会意外取消弹窗，浅色同位置对照不会。** 此问题本轮仅测试、定位，尚未修改生产源码。

## 环境与隔离

- MuMu，ADB `127.0.0.1:16384`，Android 12 / API 32。
- 1080×1920，density 280。
- 新装/使用独立目标包 `com.weekweeks.app.audit` 及其测试包；未覆盖 `com.weekweeks.app` 或 `com.weekweeks.app.pastel`，未清数据或卸载。
- 本轮测试使用当前修复源码的隔离 debug 构建，不是对原预览 APK 原字节安装验证。审查构建移除主 assets，因此不能据此认证预览字体；debug 的测试课程开关也与 pastel 预览不同。
- 自动测试仅修改独立包的 theme/accent 键，并恢复原值和存在性。屏幕对照结束后也已将独立包外观恢复到原来的“跟随系统”。
- 实际横竖屏配置变化通过 Activity 的 `setRequestedOrientation` 触发，没有修改 MuMu 全局设置；测试前后 `accelerometer_rotation=1`、`user_rotation=1` 不变。

## 自动设备回归：通过

[测试源码](<../app/src/androidTest/java/com/weekweeks/app/DarkUiDeviceTest.java>)使用框架 Instrumentation 和真实附着窗口，不是手动 Bundle 重放。

最终日志（本地 `artifacts/dark-ui-mumu-20261115/orientation-instrumentation.txt`，未入库）明确输出 `DARK UI PASS`、`INSTRUMENTATION_CODE: -1`；不以 ADB 退出码代替断言结果。

1. 打开真实外观弹窗并通过 ListView 分派暂选暗色。
2. Activity 实际切到横屏、再切回竖屏；两次新 Activity 均恢复暂选项，已保存主题不变。
3. 恢复后 cancel、负按钮取消：不保存，草稿捕获器、所属弹窗和 modal token 均释放，后续保存状态不会复活已关闭弹窗。
4. 恢复后确认暗色：偏好更新，新 Activity 实际加载暗色。
5. 已保存暗色再次确认：关闭弹窗，无重建（1.2 秒 ActivityMonitor 观察窗口），无残留 token。
6. 恢复后确认浅色：偏好更新，新 Activity 实际加载浅色。
7. 22 个浅色 + 22 个暗色界面预设：设备端构造真实颜色控件，检查父颜色 cell 的 `performClick` 会更新 draft 与手动选择标志；所有被测色名对比度至少 4.5，暗色选中色名仍使用原主题色。

第 7 项是**设备端 detached 控件树的程序化点击**，不是 44 次屏幕触摸；前述 ListView/按钮自动测试也使用控件事件分派。另有下一节的独立 ADB 屏幕触摸证据。没有测试物理旋转传感器。

## 真实屏幕输入：发现暗色标题误关闭

通过 `adb shell input tap/swipe` 操作真实设置入口，并用 UIAutomator 层级定位/确认状态。

### 可复现步骤

1. 暗色界面，设置 → 滚动到“外观模式” → 打开。
2. 点击浅色选项 `(540,972)`：层级中的“淺色模式”已选中，弹窗尚未确认。
3. 点击标题内部 `(150,789)`。
4. 弹窗意外消失并回到课表；重新进入设置仍为原来的“跟随系统”，即本次是错误取消，不是误保存。
5. 正常确认浅色后重新打开外观弹窗，在同一屏幕点 `(150,789)` 点击标题：弹窗仍保持打开，构成浅色对照。

### 证据

- 暗色标题与暂选状态（本地 `artifacts/dark-ui-mumu-20261115/theme-selected.nodes.json`，未入库）：标题 bounds `[74,761][242,818]`，浅色项 `checked=true`。
- 暗色点击后（本地 `artifacts/dark-ui-mumu-20261115/title-touch.nodes.json`，未入库）：无 `alertTitle`，回到课表主界面。
- 浅色点击后（本地 `artifacts/dark-ui-mumu-20261115/light-title-touch.nodes.json`，未入库）：标题 bounds `[116,761][284,818]`，弹窗和选项仍在。
- 点击点 `(150,789)` 严格位于上述两个标题矩形内部。
- 原始层级断言结果（本地 `artifacts/dark-ui-mumu-20261115/touch-verification.json`，未入库）及证据校验脚本（本地 `artifacts/dark-ui-mumu-20261115/verify-touch.py`，未入库）：已运行并输出 `TOUCH REGRESSION CONFIRMED; light control passed`。
- 截图：暗色弹窗（本地 `artifacts/dark-ui-mumu-20261115/theme.png`，未入库）、暗色点击后（本地 `artifacts/dark-ui-mumu-20261115/title-touch.png`，未入库）、浅色对照点击后（本地 `artifacts/dark-ui-mumu-20261115/light-title-touch.png`，未入库）。截图保留供复核，本结论依据实际输入和控件层级状态断言。

### 定位与影响

[MainActivity.java](<../app/src/main/java/com/weekweeks/app/MainActivity.java>)的 `styleDialogWindow` 用无 inset 的 GradientDrawable 替换 Material window background，原 Material outside listener 的 inset 仍保留，与显示内容位置不一致。此次暗/浅对照符合该风险预测。

影响：标题内部本应无动作的点击变成取消，丢失未确认的暂选项；不能据此推断所有按钮失效，也没有证据表明已保存课程或设置损坏。相同背景替换路径的其他暗色弹窗需要在修复时增加回归，不宣称已逐一触摸复现。

建议后续保留 Material 原 inset/触摸几何，只替换内部背景样式，再回归标题内点击、选项/确认/取消、窗口外点击与旋转。

## 复现命令

```powershell
$env:JAVA_HOME='<JDK 17 目录>'
$env:ANDROID_HOME='<Android SDK 目录>'
.\gradlew.bat -I tools/course-audit.init.gradle -PdarkUiAudit=true assembleDebug assembleDebugAndroidTest --console=plain
adb -s DEVICE install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE shell am instrument -w -r com.weekweeks.app.audit.test/com.weekweeks.app.DarkUiDeviceTest
```

本轮新增测试和隔离 runner 选择；未改业务源码、未发布新 APK。既有修复预览仍包含此处新确认的标题误关闭缺陷，不能视为“全部交互通过”。其他 Android/OEM 版本、大字体、小屏和 TalkBack 尚未认证。
