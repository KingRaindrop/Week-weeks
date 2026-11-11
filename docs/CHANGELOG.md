# 变更日志

记录每次改进的发现、处理结果、遗留问题与后续方向。历史批次附对应 git 提交；未提交改动会单独注明。

---

## v1.2.7 · 2026-11-11

- 已结束课程在暗色模式下改为低饱和同色系灰底，保留细课程色条；标题与辅助信息独立配色，不降低整张卡片透明度，文字对比度至少 4.5:1。
- 周视图与日程列表增加“✓ 已结束”状态；单节紧凑卡片使用独立勾号，多节卡片在底部显示状态，宽度不足时退化为勾号；无障碍描述保留完整状态，支持简体、繁體及 English。
- 浅色模式保留原有底色和文字颜色，补充完成标记；课程结束判断、点击/长按操作及时间线规则不变。
- 修复周视图课程卡片文字裁切：单节课教室限一行省略；课程卡片按实际字体测量，空间不足时先减少课名行数，保证教室、教师和完成标记完整显示。
- 新增隔离设备测试（`tools/course-audit.init.gradle`，独立包名 `com.weekweeks.app.audit`，不覆盖正式应用数据）：MuMu Android 12 上 648 种组合（明暗 × 字号 100/150/200% × 简/繁/英 × 宽 280/360/600dp × 1/2/4 节 × 有无教师 × 是否结束）检查文字不裁切、点击/长按各触发一次、无障碍描述、完成标记宽度缩放与恢复，全部通过；同环境下旧布局复现 72 种裁切。
- 验证：Release 构建、Lint 和 159 项单元测试通过；MuMu Android 12 原签名覆盖安装后课表数据保留。实际 TalkBack 朗读、Android 13+ 设备仍需验收。发布 APK 保留原签名，不包含本地 OPPO Sans 字体。

## v1.2.6 · 2026-11-10

- 周视图在星期标题下新增独立日程行，按日期显示时间和标题，点击打开详情；每天默认展示前 2 项，可展开、收起更多项目，不占用课程节次。
- 仅展示开启“显示在日程表”的日程；当前周没有可显示日程时整行隐藏，不留额外空白。时间线显示规则保持不变。
- 系统返回优先关闭设置，再处理“我的”页返回主页；Android 13+ 的课程、粘贴导入和日程编辑器在 Material 隐藏面板前执行草稿取消保护，并保留先关闭键盘的行为。
- 设置卡片按当前窗口和系统安全区自适应，最高约占扣除安全区及外边距后可用高度的 80%，限制最大宽度；标题及关闭按钮固定，选项单独滚动。
- 验证：Release 构建、Lint、153 项单元测试通过，MuMu Android 12 覆盖安装及设置布局、返回键路径检查通过；Android 13+ 真机边缘手势和横屏、分屏仍需实机验收。发布 APK 保留原签名，不包含本地 OPPO Sans 字体。

## v1.2.5 · 2026-11-10

- 导入课表后的开学日、散学日改为点击日历选择，校历设置页同步采用相同方式；保留首次成功导入后才提示和日期先后校验。
- 新建和编辑日程新增“显示在日程表”开关；关闭后仍可在“我的”中查看、编辑，提醒不受影响。已有日程默认继续显示。
- 显示设置随日程加密保存，编辑恢复和未保存修改提示同时覆盖此选项。
- 主页左上角标题由“课表”改为“日程表”，同步简体中文、繁體中文和 English。
- 新增日期选择与日程显示回归检查；138 项单元测试通过，并在 MuMu 验证日历弹窗、显示开关及主页标题。

## v1.2.4 · 2026-11-09

- 首次启动直接进入课表，不再强制填写开学日和散学日；未导入课表时不主动弹出校历设置。
- PDF 或粘贴文本导入成功并保存课程后，未设置校历的用户才会收到填写提示；已设置校历不重复提示。
- 取消、失败或全部重复且未更新课程的导入不触发提示；有跳过项目时，在导入结果关闭后再显示校历设置，避免弹窗重叠。
- 同步简体中文、繁體中文和 English 提示文案，并新增流程回归检查。

## 2026-11-09 · 采用 PolyForm Noncommercial 1.0.0 许可证（工作区改动，未提交）

- 新增根目录 `LICENSE`，全文采用 PolyForm Noncommercial License 1.0.0，并加入 `Required Notice:` 版权行。
- 该许可证允许修改与二次开发，禁止商业使用；再分发时必须随附许可证与 `Required Notice:` 行，衍生版本因此保持同样的非商业条款。
- 三语首页的许可证章节改为具体权利／义务说明，并说明本项目属于“源码公开（source-available）”，不是 OSI 认证的开源许可，GitHub 可能识别为 `Other`。
- 说明 AndroidX／Material Components（Apache-2.0）、Gradle Wrapper（Apache-2.0）等第三方组件仍遵循各自条款，OPPO Sans 字体不随仓库分发。
- 补充贡献条款：提交即同意按本许可证授权。许可证文件已加入源码发行清单；未执行打包。

## 2026-11-09 · 多语言产品 README（工作区改动，未提交）

- 首页重写为简体中文产品介绍，并新增繁體中文、English 对等版本，提供语言切换链接。
- 突出“纯净、无广告、不打扰”，依据当前依赖和权限说明无需账号、离线运行、无广告／统计追踪 SDK；不再使用“无任何第三方 SDK”等过宽表述。
- 新增原创矢量横幅与独立演示安装的截图，使用虚构课程／个人安排，正式应用和既有用户数据不参与截图。
- 将构建、签名与验证细节整理至 `docs/BUILDING.md`，补充图片来源说明，并将三语首页、构建文档与图片加入源码发行清单；未执行发行打包。
- 明确权限、加密范围、备份／提醒／PDF 导入限制，并说明仓库尚无开源许可证，未擅自选择许可证或添加下载地址。
- 验证：三语首页及配套文档共 43 处本地引用检查通过；横幅 SVG XML 有效，三张原始 1080×1920 PNG 均可解码，实机 UI 层级确认虚构课程／日程及 16 色选择器，未进行图像模型视觉审阅。发行脚本静态语法与 `git diff --check` 通过，未运行打包过程。
- 截图演示包完成后已停用，模拟器返回正式 `com.weekweeks.app` 的首次校历设置向导，未填写正式包校历。没有提交代码。

## 2026-11-09 · 默认界面统一（工作区改动，未提交）

- 对外只使用 Week weeks，不再区分“原版／清新版”。debug 和 release 默认启用与 pastel 相同的最新界面。
- 正式包名保持 `com.weekweeks.app`；`pastel` 和 `.pastel` 包名仅保留为内部兼容测试入口，不卸载已有应用、不迁移或清除数据。
- 旧界面代码、内部 `PASTEL_UI` 开关及 `Pastel*` 类名暂保留；编译桩同步启用当前界面。
- 新增 `DefaultInterfaceTest`，防止默认构建退回旧界面。下方更名批次中的原版／清新版描述属于当时状态，现以本节为准。
- 验证：`assembleDebug assembleRelease assemblePastel lintRelease testDebugUnitTest testReleaseUnitTest` 全部通过；debug 和 release 各 128 项测试、0 失败，release lint 0 个问题，`git diff --check` 通过。
- MuMu 已安装正式签名包 `com.weekweeks.app`，冷启动成功；生成的 release `BuildConfig` 确认 `PASTEL_UI=true`、`ENABLE_TEST_COURSE=false`。设备停留在首次校历设置向导，未填写测试校历；兼容测试包及两个旧应用仍保留。本轮未在首次向导之后逐屏验证。

## 2026-11-09 · Week weeks 全面更名（工作区改动，未提交）

- 品牌名称统一为 `Week weeks`，不区分简体中文、繁體中文或英文；“清新版”保留为 Pastel 界面样式描述。
- 应用包名与 Java 命名空间改为 `com.weekweeks.app`，清新版包名为 `com.weekweeks.app.pastel`；源码与工具中的包名引用同步调整。
- Gradle 工程名改为 `WeekWeeks`，源码发行包前缀改为 `week-weeks-v<版本>`。
- 新应用使用私有偏好文件 `week_weeks`、Android Keystore 前缀 `week_weeks_` 和内部提醒 URI `weekweeks://reminder/…`。
- 用户选择新应用从空数据开始，不自动迁移课程、日程或设置；旧应用及其数据保留，不卸载或清空。
- 签名环境变量改为 `WEEK_WEEKS_STORE_FILE`、`WEEK_WEEKS_STORE_PASSWORD`、`WEEK_WEEKS_KEY_ALIAS`、`WEEK_WEEKS_KEY_PASSWORD`，用户级配置名改为 `week-weeks-signing.properties`，JKS 文件命名改为 `week-weeks-release.jks`。
- **保留原有实际签名密钥、证书与 JKS 内部 alias**；项目内配置文件仍为 `secrets/release-signing.properties`。下方历史批次中的旧名称、旧配置与旧 alias 属于当时事实，不批量改写。
- 更名验证：`assemblePastel assembleRelease lintPastel testDebugUnitTest` 通过；127 项单元测试、65 项提醒行为回归检查通过，lint 0 个问题，`git diff --check` 通过。
- MuMu 已独立安装新清新版 `com.weekweeks.app.pastel`，冷启动成功并显示首次校历设置向导；未填写测试校历或迁移数据。旧的两个应用仍保留。正式版 APK 的包名已核对为 `com.weekweeks.app`，应用名均为 `Week weeks`。
- 本轮没有执行发行打包脚本、提交代码或逐屏复测多语言；真实提醒推送未在新空数据应用上触发。

---

## 2026-08-12 · 批次 1（P0）：基线建立与签名安全加固

**Git 提交**：`fb6dcee chore: baseline with hardened signing layout`

### 发现的问题

1. **签名密码明文存放在仓库目录**
   `keystore/release-signing.properties` 明文保存 keystore 密码，且 store 密码与 key 密码相同。虽然旧 `.gitignore` 忽略了 `keystore/`，但目录整体位于项目内，容易随复制、打包、备份扩散。
2. **`.git` 是空目录，项目实际无版本控制**
   `git log` 报 not a git repository，5000+ 行代码没有任何历史保护。
3. **README 安全声明失真**
   README 声称"应用没有 INTERNET 权限"，但 `AndroidManifest.xml` 声明了 `android.permission.INTERNET`（用于在线校历导入）。
4. **`artifacts/` 调试产物（36.7MB 截图/录屏）未隔离**
   若直接提交会进入版本库，拖慢 clone 与历史体积。

### 解决的问题

1. **签名凭据迁移到专用目录 `secrets/`**
   - `mein-stundenplan-release.jks`、`release-signing.properties` 移入 `secrets/`，目录整体 git-ignored。
   - 新增 `secrets/release-signing.properties.template`（唯一入库文件），说明填写方式。
   - `app/build.gradle` 签名解析改为三级回退：环境变量 `MEIN_STORE_FILE`/`MEIN_STORE_PASSWORD`/`MEIN_KEY_ALIAS`/`MEIN_KEY_PASSWORD` → `secrets/release-signing.properties` → `~/.gradle/mein-stundenplan-signing.properties`。CI 可用环境变量，本地不依赖仓库内文件。
2. **建立版本控制基线**
   - 删除空 `.git`，`git init -b main`，提交基线 `fb6dcee`。
   - `artifacts/`、`build/`、`app/build/`、`secrets/*`（模板除外）全部排除在版本库外。
3. **README 修正**
   - 安全设计一节如实说明 `INTERNET` 权限用途与约束（仅 HTTPS、校方域名白名单、禁止重定向、512KB 上限）。
   - 新增"校历在线导入"功能说明与请求约束小节。
   - 新增"发布签名"小节说明凭据解析顺序。

### 待解决的问题

- Gradle Wrapper 缺失，构建仍依赖 `tools/build-verify.ps1` 中的本机硬编码路径（批次 2）。
- `MainActivity` 与 `ReminderScheduler` 的重复加密代码未合并（批次 3）。
- PDF 解析逻辑仍在 `MainActivity` 内且无测试（批次 4）。
- Lint 8 个警告（5 处 `commit()`、3 个未使用资源）未处理（批次 5）。

### 改进方向

- 每个批次完成后提交一次 git，保持可回退粒度。
- 后续批次顺序：构建可移植性 → 存储层去重 → 解析层拆分+测试 → lint 清理。

---

## 2026-08-12 · 批次 2（短期）：Gradle Wrapper 与构建可移植性

**Git 提交**：`5485570 build: add offline Gradle wrapper 9.2.0 and portable build config`

### 发现的问题

1. **缺少 Gradle Wrapper**
   仓库没有 `gradlew`/`gradlew.bat`/`gradle/wrapper/`，构建依赖 `tools/build-verify.ps1` 里写死的本机路径（含 `gradle-wrapper\dists\...\<hash>\...` 这种随版本变化的 hash 目录）。换机器或 Gradle 升级小版本都会直接失败。
2. **缺少 `gradle.properties`**
   没有 JVM 内存、并行、缓存等常规配置。
3. **`app/build.gradle` 签名块变量遮蔽 DSL**
   批次 1 引入的局部变量 `storeFile`/`keyAlias` 等与 Gradle DSL 方法同名，导致 `No signature of method: java.lang.String.call()` 构建失败。
4. **离线环境无法生成 wrapper**
   Gradle 9 的 `wrapper` 任务需联网校验发行版 URL（`services.gradle.org` 不可达），标准命令失败。

### 解决的问题

1. **手动离线生成 Gradle Wrapper（9.2.0）**
   - 从本机已缓存的 Gradle 发行版中提取官方 `gradle-wrapper.jar`（`lib/plugins/gradle-wrapper-main-9.2.0.jar` 内嵌）。
   - 手写标准 `gradlew.bat`（Windows）与 `gradlew`（POSIX）启动脚本。
   - 编写 `gradle-wrapper.properties`，并设置 `validateDistributionUrl=false` 以适配离线环境。
2. **修复签名变量遮蔽**
   局部变量重命名为 `resolvedStoreFile` 等，避免与 DSL 冲突；`assembleDebug` 与 `assembleRelease` 均构建成功（release 走 `secrets/` 签名）。
3. **新增 `gradle.properties`**
   `-Xmx2048m -Dfile.encoding=UTF-8`、`parallel`、`caching`。
4. **`tools/build-verify.ps1` 改用 wrapper**
   不再硬编码 Gradle dist hash 路径；JDK/SDK/GRADLE_USER_HOME 支持环境变量覆盖，默认回退 D 盘。
5. **README 构建章节重写**
   改为以 wrapper 为主入口，保留 D 盘环境变量说明。
6. **发行版缓存对齐**
   将 `gradle-9.2.0-bin` 发行版复制到 `<Gradle 用户目录>\wrapper\dists`，使 `GRADLE_USER_HOME=<Gradle 用户目录>` 时 wrapper 离线可用。

### 待解决的问题

- `MainActivity`/`ReminderScheduler` 重复加密代码（批次 3）。
- PDF 解析逻辑拆分与测试缺失（批次 4）。
- Lint 8 个警告：5 处 `commit()`、3 个未使用资源（批次 5）。

### 改进方向

- 构建入口统一为 `.\gradlew.bat`；后续脚本与文档都以 wrapper 为准。
- 可考虑后续开启 configuration cache（Gradle 已提示）。

---

## 2026-08-12 · 批次 3（短期）：抽取 SecureStorage 消除重复加密代码

**Git 提交**：`63a3c1e refactor: extract SecureStorage for shared Keystore AES/GCM encryption`

### 发现的问题

1. **两套几乎相同的 AES/GCM 实现**
   `MainActivity`（课程数据，alias `mein_stundenplan_courses_key`）与 `ReminderScheduler`（提醒计划，alias `mein_stundenplan_reminder_schedule_key`）各自实现了 `encrypt`/`decrypt`/`getOrCreateSecretKey`，逻辑完全相同（JSON envelope：`{"iv","data"}` Base64、128-bit GCM tag、`setRandomizedEncryptionRequired(true)`），仅 alias 不同。重复约 80 行。
2. **维护风险**
   将来升级加密方案（如换算法、加版本号、密钥轮换）需要同时改两处，容易漏改导致数据不兼容。

### 解决的问题

1. **新增 `SecureStorage.java`**
   - 包私有 final 类，构造时传入 key alias。
   - `encrypt(String)`/`decrypt(String)` 实例方法，envelope 格式与原实现逐字节兼容（已有密文无需迁移）。
   - `getOrCreateSecretKey()` 私有化。
2. **`ReminderScheduler` 改造**
   - 删除私有加密三方法与 4 个加密常量，新增 `private static final SecureStorage STORAGE = new SecureStorage(KEY_ALIAS)`。
   - 调用点改为 `STORAGE.encrypt/STORAGE.decrypt`。
   - 清理 9 个不再使用的 import。
3. **`MainActivity` 改造**
   - 删除私有加密三方法与 4 个加密常量，新增实例字段 `secureStorage`（alias `mein_stundenplan_courses_key`）。
   - 调用点改为 `secureStorage.encrypt/decrypt`。
   - 清理 8 个不再使用的 import。
4. **编译验证通过**：`gradlew compileDebugJavaWithJavac` BUILD SUCCESSFUL。

### 兼容性说明

- 密文格式未变（相同 envelope、相同 GCM 参数），仅密钥管理代码搬家，用户已有数据不受影响。
- 两个 alias 保持不变，Android Keystore 中已有密钥继续复用。

### 待解决的问题

- PDF 解析逻辑仍在 `MainActivity`，无测试（批次 4）。
- Lint 8 个警告（批次 5）。
- `MainActivity` 其余职责（UI、校历、倒计时）尚未拆分——本批次只处理了加密重复这一最安全的切入点。

### 改进方向

- 后续若加密方案升级（例如增加 envelope 版本字段），只需改 `SecureStorage` 一处。
- `TimetableRulesTest` 的测试基建可复用同样的"纯 Java + android.jar 编译检查"模式扩展到 `SecureStorage` 的 envelope 格式测试（需设备/模拟器跑 Keystore，单测只能覆盖 JSON envelope 结构）。

---

## 2026-08-12 · 批次 4（短期）：拆分 PDF 解析为纯 Java 并补充测试

**Git 提交**：`71209a6 refactor: extract PDF course parsing into pure-Java PdfCourseParser`

### 发现的问题

1. **PDF 解析逻辑全部堆在 `MainActivity`**
   PDF 头部校验、stream 解压、文本项提取、课程识别等约 500 行代码都以实例方法放在 `MainActivity` 内，与 UI、校历、倒计时职责混在一起，难以单独验证。
2. **无测试**
   解析逻辑只能靠手工导入 PDF 验证；`TimetableRulesTest` 建立起的"纯 Java + 命令行断言"测试基建没有扩展到 PDF 解析。

### 解决的问题

1. **新增纯 Java 的 `PdfCourseParser.java`（无 Android 依赖）**
   - 公开入口 `parseCourses(byte[] pdfBytes, int maxPeriods, int maxStreamBytes, int maxCourses)`，返回 `ParseResult(courses, debugLog)`。
   - 内聚 `PdfTextItem` / `PdfLiteral` / `ParsedCourse` 数据类与全部解析辅助方法；`MainActivity` 不再持有任何 PDF 解析代码。
   - 调试日志改为写入 `debugLog` 由调用方输出，不再在解析器中直接调用 `android.util.Log`。
2. **`ImportException` 提升为顶层共享异常**
   原 `MainActivity` 内部类移至 `ImportException.java`，`MainActivity` 与 `PdfCourseParser` 共用同一个异常类型，`importPdf` 的 catch 行为不变。
3. **`MainActivity` 瘦身**
   - `importPdf` 改为调用 `PdfCourseParser.parseCourses(...)` 并把 `debugLog` 交给 `android.util.Log` 输出。
   - `parsedAndShowCourses` 负责把 `ParsedCourse` 映射回 `Course`，配色沿用原有 `previewCourseColorForDay` 累积逻辑，导入行为不变。
   - 删除未被调用的 `parsePdfCourses` 死代码路径。
   - 删除 2 个不再使用的 import（`ByteArrayInputStream`、`InflaterInputStream`）。
4. **新增 `tools/PdfCourseParserTest.java` 并接入 `tools/verify-logic.ps1`**
   - 覆盖：非法文件头、空 PDF、单课程（列轴/行轴）、周次时间后缀清理、非法周次跳过、超范围节次跳过、多 stream、stream 大小上限、课程数上限、字面量转义、十六进制字符串、越界坐标拒绝、debugLog 非空。
   - `verify-logic.ps1` 现在依次编译并运行 `TimetableRulesTest` + `PdfCourseParserTest`，再做全量 Android java 编译检查。

### 兼容性说明

- 解析字段与原先逐项对应（day/period/endPeriod/name/weeks/room/teacher），配色仍在 `MainActivity` 内按原顺序分配，用户导入结果不变。
- 删除的 `parsePdfCourses` 是未被调用的死代码，无行为影响。

### 待解决的问题

- Lint 8 个警告（5 处 `commit()`、3 个未使用资源）（批次 5）。
- `MainActivity` 其余职责（UI、校历、倒计时）尚未拆分。

### 改进方向

- 可复用同一纯 Java 测试基建为 `SecureStorage` 的 envelope 格式补充测试（单测仅覆盖 JSON envelope 结构）。
- 后续批次按计划处理 Lint 清理。

---

## 2026-08-12 · 批次 5（进行中 · 未提交）：UI 优化 preview + 自定义色调

**Git 提交**：`未提交`（工作区改动，待确认后提交）

### 背景

用户打开 MUMU 模拟器体验应用后，提出两个方向：① UI 朝 Play 商店 / Material 3 风格改进；② 允许用户自行选择界面色调。本批次为 preview 迭代，改动集中在 `MainActivity.java`（全部程序化 UI）。

### 已完成的改动（工作区，未提交）

1. **P0 三项基础优化**
   - 课程卡片阴影由 `elevation 4/11dp` 降为 `1/5dp`，更接近"细边框 + 浅影"的现代卡片。
   - 摘要行拆分为"主行（第X周 · 周X · N门课）" + "状态 chip（正在上/下一节）"，新增 `statusChip` 与 `updateStatusChip()`。
   - 空状态增加「＋ 添加课程」「导入 PDF 课表」两个 CTA 按钮（替代纯文字提示）。
2. **Play 商店风格化**
   - 课程卡片节次徽章改为 56/64dp 圆角方块（应用图标感），圆角 16/20dp。
   - 时间从 accent 大字改为 tonal chip（容器色小胶囊）。
   - 普通卡片圆角 18 → 24dp；日期 tab 选中从"实心紫药丸"改为 tonal 容器色 + accent 文字。
   - 新增 `tonalContainerColor()`（主色 10% 混入卡片色）。
3. **自定义界面色调（用户可选）**
   - 新增 `ACCENT_KEY`（SharedPreferences 持久化）与 7 组 `ACCENT_PRESETS`：蓝/青绿/绿/紫/橙/红/靛，各含浅色/深色两套值。
   - `accentColor()` 按预设 + 深浅模式返回；`todayColor()` 用 HSV 色相偏移（+42°）从主色派生，避免与 accent 混淆。
   - 设置面板新增「外观 → 界面色调」入口（`showAccentColorDialog()` + `renderAccentChoices()`），点击色块保存并 `recreate()` 全局生效。
   - 硬编码 accent 容器色（状态 chip、空状态导入按钮）统一改为 `accentContainerColor()`。
   - 新增 drawable `ic_palette_outline.xml`；同步更新 `tools/compile-stubs/R.java` 存根。

### 验证状态（已通过）

- `tools/verify-logic.ps1`：TimetableRulesTest + PdfCourseParserTest + Android 编译检查全过。
- `assembleDebug`：BUILD SUCCESSFUL；已安装到 MUMU（127.0.0.1:16384）。
- 无崩溃（logcat crash buffer 无本应用记录）。
- 自定义色调**像素级验证**：注入绿色预设（accent_preset=2）重启后，右上角添加按钮由 `#69B2FF`(105,178,255) 变为 `#6FD08A`(111,208,138)，日期 tab 选中区同步变绿，课程徽章不变；恢复默认后回蓝。
- 注意：MUMU 上 `input tap` 坐标与视图树有偏移（content 起点 y=42，且旋转坐标映射异常），自动化点击不可靠；`uiautomator dump` 因应用持续刷新拿不到 idle。后续如需 UI 自动化，需先解决这两点。

### 截图（build/emu-shots/）

- `preview-play-style.png`：Play 风格（有课程状态）
- `preview-empty-state.png`：空状态 CTA
- `preview-accent-green.png`：绿色色调效果
- `preview-restored-blue.png`：当前默认蓝（交付状态）

### 待办 / 下一步

- 等用户确认效果后提交本批次（建议拆两个 commit：UI 优化+Play 风格 / 自定义色调）。
- 可选继续调整：卡片圆角/徽章渐变、日期 tab 药丸化、header 按钮加文字标签、`toggleTheme` 图标随 accent 换色。
- 长期：`MainActivity` 其余职责（UI/校历/倒计时）尚未拆分。

---

## 2026-08-13 · 批次 5 后续（未提交）：UI 规范对齐与视觉改进

**Git 提交**：`未提交`（工作区改动，待确认后提交）

### 已完成改动

1. **修复节次徽章“节”字被裁**
   - 徽章高度由固定 56/64dp 改回 `WRAP_CONTENT`，垂直 padding 收窄到 8dp，保证“第 11 节”等两行内容完整显示。
2. **色板无障碍与按压反馈**
   - 新增 `ACCENT_PRESET_NAMES`、`COURSE_COLOR_NAMES`，为界面色调 7 个、课程颜色 10 个 swatch 补 `contentDescription`（选中态带“，已选”）。
   - 新增 `swatchStateListBackground()`，色板按下变深。
3. **空状态视觉与交互**
   - 空状态由“大号日期文字”改为“日历矢量图标（accent 容器色底板）+ 标题 + 日期副标题 + CTA”。
   - 两个 CTA 按钮补按压反馈：`addCta` 用 `interactiveButtonBackground`，`importCta` 用新增 `interactiveTranslucentBackground`（保留半透明容器色）。
4. **头部图标风格统一**
   - 主题切换按钮改用 outline 矢量图标 `ic_sun_outline` / `ic_moon_outline`，并用 `accentColor()` 动态着色，与设置/加号按钮风格一致。
   - 删除不再使用的 `ic_sun_10_white.xml`、`ic_moon_deep_blue.xml`。
5. **节次数字自适应缩放**
   - 跨节次（如 `11-12`）或长数字时自动缩小 `periodNum` 字号，避免横向被裁。

### 验证状态（已通过）

- `tools/verify-logic.ps1`：逻辑测试 + Android 编译检查全过。
- `gradlew assembleDebug`：BUILD SUCCESSFUL。
- MUMU 重装后启动无崩溃。
- 像素复核：header 旧深蓝月亮色（#123A63）像素数归零，accent 轮廓图标生效；徽章“节”字完整渲染。

### 待办 / 下一步

- 可继续：日期栏选中药丸化、卡片圆角/阴影层级统一、倒计时区域动效。
- MUMU `input tap` 坐标与画面存在旋转映射偏移，UI 自动化点击仍不可靠，后续需先解决。

### 补丁（2026-08-13，同日）

- 修复切换界面色调后色盘短暂残留：选中色块后先 `dialog.dismiss()` 再 `recreate()`，避免对话框在 Activity 重建过程中多显示一会。

### 风格方向（2026-08-13，同日）

- 为贴近原生 Android，将全应用字体从自定义 `DengXian` 改为系统原生字体栈 `sans-serif`（Roboto + Noto Sans CJK），方法重命名为 `appTypeface()`。
- 后续可选：Material 3 权重（标题/按钮用 `sans-serif-medium`）、给按钮补 ripple 按压波纹、统一 Material 形状与色板角色；iOS 风格不建议整体套用（平台不一致），但日期栏分段控件已具 iOS 观感。

### 原生 Android 化补丁（2026-08-13，同日）

- 交互：把 `interactiveSurfaceBackground` / `interactiveButtonBackground` / `interactiveGlassSurfaceBackground` 从自定义 `StateListDrawable` 换成框架 `RippleDrawable`，按钮/设置行/CTA 点击出现原生水波纹。
- 语义色：删除按钮、警告区、状态 chip 的散落硬编码红/灰改为 `dangerColor()`、`dangerContainerColor()`、`withAlpha(secondaryTextColor())` 等语义角色；移除失效的 `pressedSurfaceColor`。
- 组件语义（依赖说明）：`MaterialButton`/`CardView` 需要引入 `com.google.android.material:material` 与 AndroidX，而当前本地 Gradle 缓存无该依赖且环境离线，故本轮未引入；已用框架 Ripple + 语义色达到近似原生 Material 效果。

### Material 组件化（2026-08-13，同日）

- 引入依赖 `com.google.android.material:material:1.12.0`，并开启 `android.useAndroidX=true`。
- 主题从框架 `Theme.Material.*` 迁移到 `Theme.Material3.Light/Dark.NoActionBar`（values / values-night / v27 / night-v27）。
- `MainActivity` 由 `android.app.Activity` 迁移到 `androidx.appcompat.app.AppCompatActivity`。
- 设置面板动作按钮由 `Button` 改为 `MaterialButton`（cornerRadius / stroke / ripple / inset）。
- 空状态卡片由普通 `LinearLayout` 改为 `MaterialCardView`（cardBackgroundColor / radius / elevation / stroke）。
- 为离线 `verify-logic.ps1` 增加 `AppCompatActivity`、`MaterialButton`、`MaterialCardView` 的 compile-stub，保证离线编译检查仍可运行。
- 验证：`verify-logic.ps1` 通过，`assembleDebug` BUILD SUCCESSFUL，MUMU 重装启动无崩溃。

---

## 交接备忘（给下一个会话 / 明早继续）

**状态**：本会话所有改动都还在**工作区未提交**。基线是之前 4 个 batch 的 git 提交，UI / Material 相关改动均未 commit。

### 本会话已完成（可据此继续）
1. 修复节次徽章“节”字被裁（高度 WRAP_CONTENT + padding 8）。
2. 色板无障碍标签 + 按压态。
3. 空状态升级：日历图标 + 标题 + 日期副标题 + 两个 CTA。
4. 头部主题图标 outline + accent 着色。
5. 节次数字自适应缩放。
6. 全应用字体改系统原生 `sans-serif`（`appTypeface()`）。
7. 交互背景改 `RippleDrawable`（涟漪按压）。
8. 语义色整理（`dangerColor()` / `dangerContainerColor()` 等）。
9. 引入 Material 1.12.0 + AndroidX：`AppCompatActivity` + `Theme.Material3.*`。
10. 设置按钮 `MaterialButton`、空状态卡片 `MaterialCardView`。

### 明早继续的优先项
- 课程卡片换 `MaterialCardView`：注意 `createCourseCard` 现在用自定义 `BoundedFrameLayout` + `CourseProgressView` + foreground，替换时必须保留“进行中”进度层。
- 对话框换 `MaterialAlertDialog`。
- 继续语义色统一：`ClassReminderReceiver` 里的硬编码通知蓝可改为共享 accent。
- 可选：日期栏选中态药丸化、倒计时区域动效。

### 验证命令
- 快速编译 + 逻辑测试：
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-logic.ps1`
- 构建（首次需联网，之后可离线）：
  `$env:JAVA_HOME='<JDK 目录>'; $env:ANDROID_HOME='<Android SDK 目录>'; $env:ANDROID_SDK_ROOT='<Android SDK 目录>'; $env:GRADLE_USER_HOME='<Gradle 用户目录>'; .\gradlew.bat --no-daemon assembleDebug`
- 离线构建加 `--offline`。

### 注意事项
- MUMU（`127.0.0.1:16384`）的 `input tap` 坐标与截图有旋转映射偏移，自动化点击不可靠；`uiautomator dump` 常因应用持续刷新拿不到 idle。
- 已给 `tools/compile-stubs` 增加 androidx / material 的 compile-stub；若 MainActivity 再引用新的 Material 类，需同步补 stub，否则 `verify-logic.ps1` 离线编译检查会失败。
- 全部改动未提交，建议确认效果后按主题拆分 commit。

---

## 2026-08-13 · 课程卡片 MaterialCardView 化 / 对话框 MaterialAlertDialog / 通知色共享

**Git 提交**：`未提交`（工作区改动，随批次 5 一并提交）

### 已完成的改动

1. **课程卡片换 MaterialCardView**
   - 自定义 `BoundedFrameLayout` 升级为 `BoundedMaterialCardView`（`MaterialCardView` 子类），保留 `onMeasure` 最大高度封顶逻辑；"进行中"进度层 `CourseProgressView` 仍作为子视图叠加，`COMPLETED` 状态不添加。
   - 圆角/阴影/描边改用 Material API：`setRadius(28/24dp)`、`setCardElevation(5/1dp)`、`setStrokeWidth(1dp)`、`setStrokeColor`（进行中为半透明白，普通卡片为卡片色混合课程色）。
   - 卡片背景统一走语义色：进行中用课程色实底，普通卡片为卡片色 + 课程色混色（深色 8% / 浅色 4%）。
2. **对话框换 MaterialAlertDialogBuilder**
   - 全部对话框构造改为 `MaterialAlertDialogBuilder`，适配 `Theme.Material3`。
   - import 由 `android.app.AlertDialog` 切换为 `androidx.appcompat.app.AlertDialog`。
   - 保留 `styleDialogWindow` 对确定/取消按钮的 accent 着色与字重设置。
3. **通知色共享 accent**
   - `ClassReminderReceiver` 通知 `setColor` 由硬编码 `Color.rgb(37, 99, 235)` 改为 `MainActivity.accentColorFor(context)`，跟随用户选择的界面色调（浅色/深色各自取值）。
4. **补离线编译 stub**
   - `tools/compile-stubs` 增加 `androidx.appcompat.app.AlertDialog` 与 `com.google.android.material.dialog.MaterialAlertDialogBuilder`。

### 验证状态（已通过）

- `verify-logic.ps1`：逻辑测试 + 全量 Android Java 编译检查通过。
- `gradlew.bat --no-daemon --offline assembleDebug`：BUILD SUCCESSFUL。

### 待办 / 下一步

- 连同批次 5 其余 UI 改动，确认效果后按主题拆分 commit。

---

## 2026-08-13 · APP 图标（adaptive icon 完整化 + legacy 密度适配）

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

1. **manifest 图标引用修正**
   - `AndroidManifest.xml` 的 `icon` / `roundIcon` 由 `@drawable/ic_launcher_foreground` 改回 `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`，adaptive icon 完整生效（含圆形图标）。
2. **重绘 `ic_launcher_foreground.xml`**
   - 前景重新设计：深青铃铛 ×2 + 米白日历卡片 + 黄色日历头 + 青色表格行，全部矢量 path 手绘。
3. **重绘 `ic_launcher_background.xml`**
   - 纯色背景与前景协调。
4. **补充 legacy PNG**
   - 新增 `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher(.round).png`（48/72/96/144/192），覆盖 API 23-25。
5. **验证产物**
   - `assembleDebug` BUILD SUCCESSFUL；生成 `artifacts/icon-preview.png` 与 `artifacts/icon-preview-round.png` 预览图。

---

## 2026-08-13 · 对话框窗口样式统一

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- 修复界面色调切换后对话框短暂残留：选中色块后先 `dialog.dismiss()` 再 `recreate()`（批次 5 补丁）。
- `styleDialogWindow` 与 MaterialAlertDialog 的 Material3 按钮样式对齐，去掉自定义 `dp(2)` 边距带来的观感不一致。

### 验证状态（已通过）

- `verify-logic.ps1` 与 `assembleDebug` BUILD SUCCESSFUL，MUMU 实测对话框样式与页面主题一致。

---

## 2026-08-13 · 节次设置对话框滚动优化

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- `showPeriodSettingsDialog` 的规则列表放入 `ScrollView`，高度上限约 `dp(300)` 并以 `weight=1` 占满剩余空间，规则多时不再溢出屏幕。
- 节次按钮（恢复默认 1-11 节等）统一为 MaterialButton 50dp 高度。
- MUMU 上 uiautomator 校验对话框 bounds（88px 高度对齐）。

### 验证状态（已通过）

- `verify-logic.ps1` 与 `assembleDebug` BUILD SUCCESSFUL。

---

## 2026-08-13 · 界面元素对齐微调

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- 修复紧凑宽度下元素对齐：uiautomator 实测某行 y 坐标由 693 调整到 714（约 21px ≈ 12dp），使与相邻元素间距一致。
- 间距统一为 12dp 级。

### 验证状态（已通过）

- `assembleDebug` BUILD SUCCESSFUL。

---

## 2026-08-13 · 通知总开关（SwitchCompat）

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- 设置面板新增「课程提醒」总开关，使用 `androidx.appcompat.widget.SwitchCompat`。
- 关闭（`notifications_enabled=false`）：先 `cancelScheduledClassReminders` 取消已排闹钟，再 `ReminderScheduler.clear` 清空提醒计划，并清除 `REMINDER_CODES_KEY` 记录；开启时重新 `scheduleClassReminderNotifications` 排程。
- `scheduleClassReminderNotifications` 同步刷新设置里的 `notificationStatusText`。
- 补离线编译 stub：`androidx.appcompat.widget.SwitchCompat`。

### 验证状态（已通过）

- `verify-logic.ps1` 与 `assembleDebug` BUILD SUCCESSFUL，MUMU 实测开关 true/false 生效。

---

## 2026-08-13 · MUMU 旋转/分辨率适配（compact 宽度）

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- MUMU 模拟器处于 `rotation=1`（横屏），通过 adb `wm size 1080x720` 使宽度 411dp，落入紧凑模式（compact）。
- 为 compact 模式收紧卡片内边距/字号/徽章尺寸，按钮高度 88px 级对齐。
- 发现 MaterialAlertDialog 在 `values-night` 下按系统深色渲染，但应用 `isDarkMode=false` 时对话框与页面主题不一致（见下一条）。

---

## 2026-08-13 · 修正 App 深色模式与对话框主题不一致

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- 根因：`values-night` 资源按系统深色生效，而应用强制浅色时 `isDarkMode=false`，MaterialAlertDialog 仍取深色资源。
- 修复：`onCreate` 中调用 `AppCompatDelegate.setDefaultNightMode(...)`，将 `theme_mode`（0=跟随系统 / 1=MODE_NIGHT_NO / 2=MODE_NIGHT_YES）同步给 AppCompat，使对话框与应用主题一致。
- 补离线编译 stub：`androidx.appcompat.app.AppCompatDelegate`。

### 验证状态（已通过）

- `verify-logic.ps1` 与 `assembleDebug` BUILD SUCCESSFUL，MUMU 实测背景色由深色 `(43,41,48)` 切回浅色 `(~238-248)`。

---

## 2026-08-13 · 移除"校历在线导入"功能（v1.2 / versionCode 3）

**Git 提交**：`未提交`（工作区改动）

### 动机

- 在线导入实为"联网验证页面标题 + 返回内置常量日期"的半假功能（见早期分析报告 #4），换学期即失效；
- 移除后应用可彻底去掉 `INTERNET` 权限，回到完全离线，与 README 安全定位一致（早期分析报告 #3 的根因消除）。

### 已完成的改动

- `MainActivity.java`：删除校历对话框 URL 输入与"在线导入"按钮；删除 `importAcademicCalendarOnline`/`fetchAcademicCalendar`/`decodeCalendarText`/`parseAcademicCalendar`/`inferCalendarName`/`applyAcademicCalendar`/`AcademicCalendar`；删除 `DEFAULT_CALENDAR_URL`/`CALENDAR_YEAR_PATTERN`/`MAX_CALENDAR_BYTES` 与 `java.net.*`/`Charset` 导入。PDF 导入共用的 `readWithLimit`/`ImportException` 保留。
- 删除 `CalendarDateExtractor.java` 与 `tools/CalendarDateExtractorTest.java`（移入回收站）。
- `AndroidManifest.xml`：移除 `INTERNET` 权限（保留 `usesCleartextTraffic="false"` 作为纵深防御）。
- `tools/security-audit.ps1`：断言反转——禁止 INTERNET 权限、禁止 Java 源码出现任何 http(s) URL 与网络类；`tools/verify-logic.ps1`：移除对应编译/测试条目。
- README：删"校历在线导入"章节，新增"校历设置"说明与安全声明更新。
- 版本：`versionCode 1 → 3`，`versionName 1.0 → 1.2`（git HEAD 基线为 1 / 1.0）。

### 验证状态

- 校历仅支持手动设置：设置 → 校历设置 → 填写开学日/散学日。

---

## 2026-08-31 · PDF 跨页课程名解析修复（v1.2.1 / versionCode 4）

**Git 提交**：`未提交`（工作区改动）

### 改动

- `PdfCourseParser`：课程跨 PDF 分页时（课程名留在上一页底部、节次元数据出现在下一页顶部，中间夹杂其他列文本），原逻辑遇到非课程名行即 `break` 导致课程名关联失败；改为跳过干扰行、沿同一列/行轴继续回溯（`continue`），仅轴向不匹配时才中断。
- `tools/PdfCourseParserTest.java`：新增跨页关联回归用例。
- 新增命令行复现工具 `tools/ReproRun.java`（未入库；读取样例 PDF 输出解析结果与 debugLog）。
- 版本：`versionCode 3 → 4`，`versionName 1.2 → 1.2.1`。

### 验证状态

- `PdfCourseParserTest` 全部通过；`verify-logic.ps1` 通过。

---

## 2026-09-05 · 周切换浏览与状态保留（v1.2.2 / versionCode 5）

**Git 提交**：`未提交`（工作区改动）

### 改动

- 周切换浏览：主界面顶部新增周导航条（`‹ 第N周 · 日期范围 › [回到本周]`），支持浏览任意教学周课表；点击周标签打开周选择对话框；"回到本周"恢复跟随真实当前周。
- 状态保留：新增 `onSaveInstanceState`，主题切换 `recreate()` / 进程重建后保留当前查看的星期与周次（`viewingWeek`），修复切换后视图跳变到其它星期。
- 切换星期/周后自动滚动使选中星期进入视野（`scrollSelectedDayIntoView`）。
- 校历引导：首次运行且未设置校历时主动弹出校历设置对话框（`maybePromptCalendarSetup` / `showCalendarImportDialog(firstRun)`），并兼容检测旧版校历数据（`containsLegacyCalendar`）。
- 强调色选择面板重构（`renderAccentChoices`）；状态标签统一为 `updateStatusChip`（区分预览周）。
- README：新增"周切换浏览"功能点与说明。
- 版本：`versionCode 4 → 5`，`versionName 1.2.1 → 1.2.2`。

### 验证状态

- `security-audit.ps1`、`verify-logic.ps1`、`assembleDebug`、`assembleRelease` 全部通过（2026-09-07 全面检测复核）。

---

## 2026-09-07 · Lint 清理：adaptive icon monochrome 层 + legacy 图标圆角

### 改动

- 新增 `drawable/ic_launcher_monochrome.xml`：单色剪影（挂环 + 日历主体，内部三条线以 `fillType="evenOdd"` 挖空），供 Android 13+ 主题图标使用。
- `mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml` 补 `<monochrome>` 标签，消除 2 个 `MonochromeLauncherIcon` 警告。
- `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi` 五个方形 `ic_launcher.png` 做圆角透明处理（半径 = 边长 25%，抗锯齿），消除 5 个 `IconLauncherShape` 警告；风格与圆形版图标一致。
- CHANGELOG：修正 v1.2 条目版本行为 `1 → 3`（git HEAD 基线为 1 / 1.0），并补 v1.2.1 / v1.2.2 两条记录与本条目。

### 验证状态

- `lintDebug`：0 errors / 0 warnings（原 0 errors / 7 warnings）；`security-audit.ps1`、`verify-logic.ps1`、`assembleDebug`、`assembleRelease` 全部通过。

---

## 2026-09-17 · 课程冲突检查修复：改地点/老师不再误报"已有课程"（v1.2.3 / versionCode 6）

**Git 提交**：`未提交`（工作区改动）

### 发现的问题

1. **仅修改地点/老师也会被冲突检查拦下**
   编辑已有课程时，保存前无条件调用 `hasCourseConflict(draft)`。该方法按 `id` 排除自身、按 `day + 节次 + 周次` 判重叠。但当同一时段本就有两条记录时（例如老师永久换教室后 PDF 重导入产生的重复条目，或同一门课按周次拆分成多条），用户即使只想改「地点/老师/名称/颜色」这类与时间无关的字段，也会被判定与"另一条自己"冲突，弹出"该节次和周次已有课程"而无法保存。冲突提示还不点名到底和哪门课冲突，排查困难。
2. **PDF 重导入把"改地点"当成新课，制造重复**
   `hasImportedCourseDuplicate` 要求名称/周次/节次/地点/老师**全部**一致才算重复。老师永久换教室后，同一门课因 `room`/`teacher` 变化不再被判为重复，重导入会新增一条与原记录同 `day + 节次 + 周次` 的课程，直接造成问题 1 的冲突场景。

### 解决的问题

1. **冲突检查只在时间字段真正变化时触发**（`MainActivity.showCourseDialog` 保存逻辑）
   - 新增 `isScheduleChanged(draft, editing)`：仅当 `day`/`period`/`endPeriod` 变化，或周次表达式语义变化（用 `TimetableRules.sameWeekExpression` 比较，"1-8周"与"1-8"视为相同）时才返回 true。
   - 保存时改为 `if (isScheduleChanged(...)) { 冲突检查 }`；只改地点/老师/名称/颜色不再触发冲突拦截。
2. **冲突提示点名具体课程**
   - `hasCourseConflict` 改为返回冲突对象的 `findCourseConflict`；新增 `courseConflictMessage`，提示形如「与《高等数学》冲突：周一 第1-2节 1-16周」，便于定位是哪条记录占用时段。
3. **PDF 导入原地更新而非新增重复**（`parsedAndShowCourses` 确认导入逻辑）
   - `hasImportedCourseDuplicate` 改为 `findImportedCourseMatch`：以「同星期 + 同节次 + 同名称 + 同周次」判定为同一门课（不再要求地点/老师一致）。
   - 命中同一门课时，若地点/老师有变化则**原地更新** `room`/`teacher` 并计入"更新 N 门"，完全一致才跳过；只有真正的新课才分配 id 并新增。导入结果提示扩展为「导入 X 门课程，更新 Y 门，跳过 Z 门」。

### 验证状态（已通过）

- `verify-logic.ps1`：`TimetableRulesTest`、`PdfCourseParserTest` 全部通过，Android Java 编译检查通过。
- `assembleDebug`：BUILD SUCCESSFUL，产出 `app/build/outputs/apk/debug/app-debug.apk`。
- 版本：`versionCode 5 → 6`，`versionName 1.2.2 → 1.2.3`。

---

## 2026-10-28 · 补完未完成的重构 + 隐私加固

**Git 提交**：`未提交`（工作区改动）

### 发现的问题

1. 工作区无法编译（8 处错误）：`saveCourses()` 仍为 `void` 却被当作 `boolean` 使用、`saveCourses(List)` / `deliverPendingPdfResult()` / `cancelPdfImport()` / `hasUtf16TimetableEvidence()` / `decodePdfText(byte[], boolean)` 缺失，导入流程仍调用已删除的 `removeExpiredCoursesIfNeeded`。上一节"验证已通过"的记录与实际不符。
2. 课程提醒使用 `VISIBILITY_PUBLIC`，锁屏直接显示课程名与教室。
3. release 包通过 `Log.d` 输出完整 PDF 解析日志（课程名/教室/教师）。
4. PDF 导入线程不可取消，Activity 销毁后仍回调 UI；`pendingImportUri` 从未赋值，重建恢复导入不生效。

### 解决的问题

1. `saveCourses(List)` 通过 `ListTransaction` 先落盘、成功后才替换 `courses`；课程编辑、删除、PDF 导入都改为在副本上修改，写入失败时内存数据不被污染。
2. `PdfCourseParser` 补齐严格解码：BOM 优先，同一 stream 内存在 UTF-16 证据时整体按 UTF-16BE 解码，否则依次尝试严格 UTF-8 / UTF-16BE / ISO-8859-1；`hasTitleOnAxis` 忽略与元数据同点的标题（不能证明任一方向），修复 `testDayMappingRejected`。
3. PDF 导入结果经 `DeferredResult` 投递，仅在 resumed 状态显示；新增 `cancelPdfImport()`，销毁或重新导入时作废旧结果；读取权限失效给出明确提示。
4. 提醒通知改为 `VISIBILITY_PRIVATE`，并提供不含课程细节的 `publicVersion`。
5. 解析日志仅在 `BuildConfig.DEBUG` 下输出；错误 Toast 不再显示异常 message。

### 验证状态

- `TimetableRulesTest`、`PdfCourseParserTest` 全部通过；Android Java 编译检查通过。
- `assembleDebug`、`assembleRelease`：BUILD SUCCESSFUL（离线构建，跳过 lint：本机缓存缺少 `lint-gradle:31.13.0`）。
- 未在真机上验证通知锁屏显示和导入流程。

---

## 2026-10-28 · 单测迁移到 JUnit + 课程提醒关闭提示

**Git 提交**：`未提交`（工作区改动）

### 发现的问题

1. 单测是 `tools/*Test.java` 里的 `main` 方法，只能靠 `verify-logic.ps1` 手动 javac 运行；`gradlew test` 和 Android Studio 都跑不到，导致"验证已通过"的记录与实际编译状态脱节。
2. 应用内「课程提醒」开关关闭时，主界面没有任何提示；debug「测试课前通知」静默失败，「通知状态」行点进去跳到系统设置，但系统设置解决不了应用内开关的问题。

### 解决的问题

1. **JUnit 4 迁移**
   - `app/build.gradle` 新增 `testImplementation "junit:junit:4.13.2"`。
   - `TimetableRulesTest`（5）、`PdfCourseParserTest`（23）迁到 `app/src/test/java`，每个用例改为 `@Test`，断言逻辑不变；删除 `tools/` 下的旧副本。
   - 新增 `ListTransactionTest`（4）、`DeferredResultTest`（5），覆盖写入失败回滚、过期 token、取消/销毁后丢弃结果。
   - `verify-logic.ps1` 改为调用 `gradlew testDebugUnitTest`，再做 android.jar 编译检查；`package-release.ps1`、README 同步。
2. **提醒关闭提示**
   - 主界面状态胶囊：提醒关闭时显示「下一节 … · 提醒已关闭」（灰色），点按弹出开启确认。
   - 设置「通知状态」行文案改为「课程提醒已关闭，不会发送任何通知；点按开启」，点按弹出同一对话框而不是跳系统设置。
   - debug「测试课前通知」在提醒关闭时先提示，开启后自动继续发送。

### 验证状态

- `verify-logic.ps1`：JUnit 37 个用例全部通过（0 失败），Android Java 编译检查通过。
- `assembleDebug`：BUILD SUCCESSFUL。
- 未在模拟器上验证新提示的显示与交互：安装时 MuMu 虚拟机已停止（16384 端口拒绝连接）。
