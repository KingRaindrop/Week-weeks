# 「我的课表」项目分析与改进建议

分析日期：2026-08-12
分析范围：项目结构、构建配置、源码架构、安全配置、测试与工程卫生

---

## 一、项目概况

- 原生 Android 课表应用（嘉应学院场景），纯 Java，无任何第三方依赖（连 AndroidX 都没有）。
- 目标 Android 16：`compileSdk 36.1`、`targetSdk 36`、`minSdk 23`，AGP 8.13.0。
- 功能：课程 CRUD、PDF 课表导入（自研解析器）、在线校历导入、课程提醒（精确闹钟 + 开机重排）、倒计时/进度 UI、三态主题切换、Keystore+AES/GCM 加密存储。
- 代码规模：5 个 Java 文件，其中 `MainActivity.java` 5122 行、259 个方法、79 个字段、13 个内部类。
- UI 全部用 Java 手写（无 XML 布局），字符串大量硬编码（约 200+ 处含中文文案）。

## 二、值得肯定的地方

- **安全意识超出一般个人项目**：Android Keystore + AES/GCM 加密课程数据、明文数据自动迁移、密钥丢失/密文损坏时"暂停保存"防止空数据覆盖、输入长度与数量上限、PDF 大小/流上限、`allowBackup=false`、`usesCleartextTraffic=false`、无外部存储权限。
- **提醒调度设计稳健**：`ReminderScheduler` 用 generation 代际号防止过期闹钟误触发，精确闹钟权限被拒时自动降级为非精确闹钟，通知权限被拒不阻断后续提醒链，开机/升级后重排。
- **`TimetableRules` 是纯逻辑类**：周次/节次解析、时间状态机与 Android 解耦，可独立测试，且已有 `tools/TimetableRulesTest.java` 覆盖。
- **发布构建规范**：release 开启 `minifyEnabled` + `shrinkResources`，签名配置外置 properties。
- **文档与验证脚本齐全**：README 详尽，`tools/` 提供构建、安全审计、逻辑验证三套脚本。
- Lint 干净：0 errors，仅 8 warnings。

## 三、问题清单

### P0 — 需要立即处理

1. **签名密码明文落盘**
   `keystore/release-signing.properties` 以明文保存 keystore 密码（store 与 key 密码相同）。虽然 `.gitignore` 忽略了 `keystore/`，但任何拿到目录的人都能直接签名发布。
   建议：改用环境变量或 `~/.gradle/gradle.properties`（用户级）注入，properties 文件只留模板；store 密码与 key 密码分开。

2. **`.git` 是空目录**
   项目实际上没有版本控制（`git log` 报 not a git repository）。5000+ 行代码没有任何历史保护，一次误改就无法回退。
   建议：立即 `git init` 并提交基线；或删掉空 `.git` 目录避免误导。

3. **README 与实际不符（安全声明失真）**
   README 声称"应用没有 INTERNET 权限"，但 `AndroidManifest.xml` 明确声明了 `android.permission.INTERNET`（用于在线校历导入）。安全文档失真比没有文档更危险。
   建议：更新 README，如实说明网络权限仅用于 HTTPS 校历获取，且已做域名白名单（`jyu.edu.cn`）、禁止重定向、512KB 上限。

### P1 — 架构与维护性

4. **`MainActivity` 是典型的上帝类**
   5122 行、259 个方法承担了 UI 构建、主题、PDF 解析、HTTP 校历抓取、加密存储、提醒调度触发、倒计时动画、手势处理等全部职责。可读性与可测试性都受限，任何改动都要在这个文件里穿行。
   建议按职责拆分（不引入框架也能做）：
   - `CourseRepository`：`loadCourses/saveCourses/encrypt/decrypt/getOrCreateSecretKey` 与迁移逻辑（与 `ReminderScheduler` 中的加密代码高度重复，可合并为共享加解密工具）。
   - `PdfTimetableImporter`：约 20 个 PDF 解析方法（`readPdfLiteral`、`parsePdfTextItems` 等）独立成类，纯字节操作、天然可单测。
   - `AcademicCalendarLoader`：`fetchAcademicCalendar/parseAcademicCalendar`。
   - `ThemeController`、`CountdownRenderer` 等 UI 辅助类。
   拆分后 `MainActivity` 只保留编排逻辑，单测可直接覆盖解析与存储。

5. **两套几乎相同的 AES/GCM 实现**
   `MainActivity`（课程数据）与 `ReminderScheduler`（提醒计划）各自实现了一套 Keystore 加密/解密/建钥代码，仅 alias 不同。重复代码约 80 行，且未来升级加密方案时要改两处。
   建议：抽出 `SecureStorage`（alias 作参数），两处复用。

6. **`parseAcademicCalendar` 实际是硬编码**
   校历解析并未真正读取 HTML 内容，只校验页面包含已知标题，然后写死 2026-03-02 ~ 2026-07-19、20 周。也就是说"在线导入"实质是联网验证后返回内置常量——换学期即失效，且与代码中 `DEFAULT_CALENDAR_URL` 绑死。
   建议：要么实现真正的解析（学期起止日期通常在校历表格中，可用正则/表格结构提取），要么改为本地设置界面手动填写学期起止，去掉网络权限；后者还能让应用回到完全离线，与 README 的安全定位一致。

7. **UI 全部手写 Java，无 XML 布局与资源体系**
   所有视图用代码创建，颜色/尺寸/字符串散落在 5000 行代码中（硬编码中文文案约 200+ 处）。这使多语言、深色模式资源分离、设计走查都很困难，`strings.xml` 里只有 1 个 `app_name`。
   建议：至少把文案迁入 `strings.xml`、颜色迁入 `colors.xml`；新界面用 XML 布局。当前规模下不必一次性重构，但新增功能应遵循。

8. **使用了已废弃 API**
   `startActivityForResult`（MainActivity.java:2813）与 `SYSTEM_UI_FLAG_*` 均已废弃。`minSdk 23` 可用 `ActivityResultContracts` 需要 AndroidX；若不引入 AndroidX，至少用 `WindowInsetsController`（API 30+）分支处理系统栏外观。
   另外 `app/build.gradle` 完全没有 `dependencies` 块——零依赖是优点，但也意味着没有 AndroidX、没有 `desugaring`，后续引入 Material 组件或 `ActivityResult` API 需要先做基础设施决策。

### P2 — 工程卫生与可移植性

9. **没有 Gradle Wrapper**
   仓库缺少 `gradlew`/`gradle/wrapper`，构建依赖 `build-verify.ps1` 里写死的本机路径（`D:\Android\Sdk`、`D:\Android Studio\jbr`、`%USERPROFILE%\.gradle\wrapper\dists\gradle-9.2.0-bin\11i5gvueggl8a5cioxuftxrik\...`）。换一台机器、升级 Gradle 小版本（hash 目录变化）都会构建失败。
   建议：`gradle wrapper --gradle-version 9.2.0` 提交 wrapper；脚本用 `./gradlew` 并支持环境变量覆盖路径。

10. **无 `gradle.properties`**
    缺少 `org.gradle.jvmargs`、`android.useAndroidX`、`android.enableJetifier` 等常规项。当前无依赖所以没暴露问题，但加依赖前应先补上（建议同时开 `org.gradle.caching=true`、`org.gradle.configuration-cache=true`）。

11. **测试只覆盖 `TimetableRules`，且游离于标准测试体系之外**
    `tools/TimetableRulesTest.java` 是自写 main 方法断言，由 `verify-logic.ps1` 手工 javac 运行；`app/src` 下没有 `test/`、`androidTest/` 目录。PDF 解析（最容易出回归的自研代码）与加密存储迁移逻辑完全没有测试。
    建议：把 `TimetableRulesTest` 迁到 `app/src/test/`（引入 JUnit 4 一个依赖即可），并为 PDF 解析补充用 `samples/` 中样例驱动的测试——samples 里已有两个 PDF，正好做黄金样本测试。

12. **`artifacts/` 与构建产物混在仓库目录**
    `artifacts/` 存了 36.7MB 调试截图/录屏，`build/`、`app/build/` 也在目录中（虽被 .gitignore 覆盖）。初始化 git 前应确认这些大文件不入库；录屏建议移到仓库外或按需保留。

13. **Lint 遗留的 8 个警告**
    - 5 处 `commit()` 建议改 `apply()`（注意 `ReminderScheduler.clear` 里的 commit 是有意同步写，改前需确认时序）。
    - 3 个未使用资源：`ic_launcher.xml`、`ic_launcher_round.xml`、`ic_launcher_background.xml`——因为 manifest 的 icon 直接引用了 `@drawable/ic_launcher_foreground`。建议改回引用 `@mipmap/ic_launcher`（adaptive icon 才完整生效，圆形图标也正常），而不是删资源。

### P3 — 体验与健壮性细节

14. **可访问性覆盖不足**：仅 7 处 `setContentDescription`，大量 ImageButton/色板按钮无无障碍标签；倒计时用动画数字切换，建议关闭动画时降级为静态文本（已有 `animationsEnabled()` 可依托）。
15. **`reminderRequestCode` 用 `name.hashCode()` 兜底**：`course.id == 0` 时同名课程会碰撞出相同 requestCode，导致提醒互相覆盖。建议保证 `id` 恒非零（导入课程也分配 id），或 requestCode 组合"日+节次+周"哈希。
16. **PDF 解析在 `onActivityResult` 同步执行**：5MB PDF 的字节级解析在主线程进行，可能卡顿。校历抓取已用子线程，PDF 导入建议同样放到后台线程（拆分 `PdfTimetableImporter` 后很容易做）。
17. **`parseAcademicCalendar` 的换学期失效**会在 2026-07-19 后表现为"当前教学周"计算错误（`currentTeachingWeek` 依赖校历起点），且没有 UI 提示。至少应在设置里显示当前校历来源并允许手动修正。

## 四、建议的落地顺序

| 优先级 | 事项 | 工作量 |
| --- | --- | --- |
| 立即 | 初始化 git 并提交基线（#2） | 10 分钟 |
| 立即 | 签名密码移出仓库目录/改环境变量（#1） | 30 分钟 |
| 立即 | 修正 README 网络权限描述（#3） | 5 分钟 |
| 短期 | 添加 Gradle Wrapper，脚本去硬编码路径（#9） | 1 小时 |
| 短期 | 抽出 `SecureStorage` 消除重复加密代码（#5） | 2 小时 |
| 短期 | 测试迁入 `app/src/test/`，补 PDF 黄金样本测试（#11） | 半天 |
| 中期 | 拆分 `MainActivity`：先 PDF 解析与存储（#4、#16） | 1-2 天 |
| 中期 | 校历导入改为真解析或本地配置（#6、#17） | 半天-1 天 |
| 长期 | 文案/颜色资源化，评估引入 AndroidX（#7、#8、#10） | 视重构节奏 |

## 五、总体评价

这是一个完成度和安全性都明显高于平均水平的个人/小团队 Android 项目：加密存储、闹钟代际管理、输入边界控制这些通常是商业应用才会考虑的防护都已就位，且有配套的安全审计脚本与样例验证。当前最大的短板不在功能，而在工程基础设施——没有真正生效的版本控制、没有 Wrapper 导致构建不可复现、5000 行上帝类让后续迭代风险集中。按上表"立即/短期"项处理完，项目的可维护性会有质的提升。