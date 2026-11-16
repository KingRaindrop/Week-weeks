# 暗色 UI 审查后的最小修复 — 2026-11-15

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

## 范围

用户确认只修复两项确定问题，保留设计，不处理尚未实测的风险项。

1. 课程编辑颜色选择器：浅色模式的选中色名恢复正文色；暗色选中主题色、勾号、描边及加粗不变。
2. 外观选择弹窗：接入现有设置草稿保存/恢复流程，保存暂选值并在重建后重新打开。仅确认后保存主题；取消、正常关闭与显示失败会清理捕获器。确认时与已保存的主题比较，不能把恢复的暂选值误当作已保存值。

没有更改课程数据格式、提醒、解析器、主题配色或 Material 弹窗背景处理，也没有更改现有主题切换的重建策略。

## 本轮源码及测试

- [MainActivity.java](<../app/src/main/java/com/weekweeks/app/MainActivity.java>)：增加 `theme_mode` 恢复分派、草稿捕获及关闭清理。
- [PastelCourseSheet.java](<../app/src/main/java/com/weekweeks/app/PastelCourseSheet.java>)：仅暗色模式选中色名使用主题色。
- [DarkUiPaletteTest.java](<../app/src/test/java/com/weekweeks/app/DarkUiPaletteTest.java>)：增加浅色色名接线及对比度检查，恢复色 `#25314D` 在背景 `#F7F8FE` 上约 12.19:1。
- [ThemeModeFlowTest.java](<../app/src/test/java/com/weekweeks/app/ThemeModeFlowTest.java>)：新增恢复、清理及只在确认后保存的三项接线检查。

精确修复前备份在 `artifacts/backups/dark-ui-fixes-20261115-1235/`。

## 验证

- 先添加回归测试：定向 10 项中 3 项在修复前失败。
- 修复后全量：Debug 313 项、Release 313 项，失败/错误/跳过均为 0。
- Lint Release：0 错误、7 个警告。
- `assemblePastel` 与 `compileDebugAndroidTestJavaWithJavac` 成功。
- 对照本轮备份核对最终补丁；`git diff --check` 通过。
- 独立只读复核未发现本轮修复的确定缺陷。
- 最终预览复制后 SHA256 与构建产物一致，无生产源码晚于构建产物。

构建命令（JDK 17）：

```powershell
$env:JAVA_HOME='<JDK 17 目录>'
$env:ANDROID_HOME='<Android SDK 目录>'
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintRelease assemblePastel compileDebugAndroidTestJavaWithJavac --console=plain
```

## 验证边界与设备复核步骤

新增测试是**源码接线断言及颜色对比计算**，不是 Android 生命周期行为测试。ADB 当前无在线设备，未安装 APK，未实测旋转、触摸、TalkBack、小屏或大字体。测试代码编译成功不等于设备测试通过。

建议设备上验证：

1. 浅色模式，选择珊瑚/橙/青绿界面主题，进入课程编辑，选择颜色，确认色名清晰且保存正常。
2. 打开外观弹窗，暂选另一模式但不确认，旋转；弹窗应重开并保留暂选项。
3. 恢复后取消；重新打开，应显示原来已保存的模式。恢复后确认，应实际保存暂选模式。
4. 反复旋转及使用返回/点击外部取消，确认不会残留弹窗或阻塞后续设置。

此前发现的 Material 背景 inset 与边缘触摸风险、潜在冗余重建请求仍保留为待设备验证风险，本轮未修改。

## 预览产物

- 修复预览 APK（本地 `artifacts/week-weeks-mist-dark-ui-fixes-20261115.apk`，未入库）
- 字节数：21,561,844
- SHA256：`F57994DB533913A4F524736DFCB79DDA5083D36C0CBE6C695E7922AAAF670991`
- 保持既有 pastel 预览变体与调试签名；未发布 GitHub Release，未执行卸载或清数据。
