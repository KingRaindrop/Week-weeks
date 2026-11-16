# 渐进式模块化：课程模型与提醒计划

> 发布说明：本文记录历史验证。`artifacts/`、`build/` 下的日志、截图、备份与预览 APK 等本地产物未入库，所列路径不是下载链接；复现命令中的环境路径和设备标识需替换为本机配置。

## 范围

本轮延续单 `:app` 模块、Java + Android Views 架构，不引入新的架构框架，不修改课程 JSON 字段、加密密钥、SharedPreferences 键、主题或提醒渠道。

## 抽取后的职责

- `Course`：同包独立课程模型，保留原来的字段、默认值、复制、导入合并接口、颜色规则接口和展示文案。为降低迁移风险，展示辅助方法暂时仍随模型保留；不是严格的纯领域模型。
- `ReminderPlanBuilder`：生成课程与个人日程的提醒条目，负责过滤、请求码计算、跨类型请求码去重及碰撞回绕。没有 Android 服务调用或存储副作用；输出类型仍复用 `ReminderScheduler.Entry`，没有额外引入重复 DTO。
- `MainActivity`：保留通知开关/存储锁定检查、旧提醒清理、当前学期和节次适配、系统调度调用、生命周期与界面编排。
- `ReminderScheduler`：保留加密提醒计划持久化、代际校验与系统 AlarmManager 调度。

```text
MainActivity（当前数据 + 校历/节次适配）
    └─ ReminderPlanBuilder
         ├─ Course
         ├─ PersonalEvent
         └─ ReminderScheduler.Entry
    └─ ReminderScheduler.replaceSchedule（原有系统调度链）
```

## 行为保持约束

1. 课程临时占位、空名称、无未来出现时间不进入提醒计划。
2. 个人日程是否显示在课表中不影响提醒；无提醒或触发时间不晚于当前时间则跳过。
3. 课程排在日程之前，共享请求码命名空间，碰撞时递增，最大值后回到 1。
4. 保留原有 ID 哈希与零 ID 的课程字段哈希；非零 ID 哈希得到 0 时也保持原值（既有调度支持显式请求码 0）。
5. 课程数据只在原有事务保存成功后发布；本轮不改变存储提交或失败提示。

## 仍未抽取

- 课程 JSON 编解码、存储适配和迁移仍在主页面中。
- 当前校历和节次时间计算仍通过主页面适配给提醒计划生成器。
- 大型课程编辑弹层尚未按功能拆分。
- 生产类仍处于 `com.weekweeks.app` 平包；没有创建虚假的 feature 子包或多模块结构。

## 验证

- 提醒调度服务替身回归：65 项通过。
- 提醒诊断服务替身回归：54 项通过。
- Debug JVM 回归：225 个测试，0 失败、0 错误。
- Release JVM 回归：225 个测试，0 失败、0 错误。
- 本轮新增 `CourseTest` 10 个测试、`ReminderPlanBuilderTest` 10 个测试。
- Release Lint：0 错误、8 个既有警告。
- `compileDebugAndroidTestJavaWithJavac`：通过，包括独立课程模型后的设备测试接线。
- `assemblePastel`：通过。
- `git diff --check`：通过，存在既有换行格式提示。
- Gradle 10 兼容性的弃用警告仍存在，本轮没有顺带升级构建工具。

验证命令：

```powershell
$env:JAVA_HOME='<JDK 17 目录>'
$env:ANDROID_HOME='<Android SDK 目录>'
.\tools\verify-reminders.ps1
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintRelease assemblePastel compileDebugAndroidTestJavaWithJavac --console=plain
```

主页面总行数由 6596 减少到 6443（相同口径，减少 153 行）。生产 Java 文件从 45 增加到 47，JVM 测试源文件从 32 增加到 34。

预览 APK：`artifacts/week-weeks-mist-modularization-20261114.apk`，21,535,383 字节。SHA256：`26BEDD0BBF25519381C5249F5681D45DCE22A84CB0DDFE63BB987D678562D1FE`。仍为 debug 签名的 `.pastel` 预览，没有覆盖历史 APK，也没有创建 GitHub 发布。

本轮只编译设备测试，没有重新安装或运行 MuMu。之前版本的 MuMu 结果不能替代本轮设备运行。服务替身测试也不是设备测试或 Android 16/API 36 验收。
