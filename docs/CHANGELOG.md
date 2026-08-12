# 变更日志

记录每次改进的发现、处理结果、遗留问题与后续方向。与 git 提交一一对应。

---

## 2026-08-12 · 批次 1（P0）：基线建立与签名安全加固

**Git 提交**：`dd8f1fd chore: baseline with hardened signing layout`

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
   - 删除空 `.git`，`git init -b main`，提交基线 `dd8f1fd`。
   - `artifacts/`、`build/`、`app/build/`、`secrets/*`（模板除外）全部排除在版本库外。
3. **README 修正**
   - 安全设计一节如实说明 `INTERNET` 权限用途与约束（仅 HTTPS、`jyu.edu.cn` 域名白名单、禁止重定向、512KB 上限）。
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

**Git 提交**：`d9bd828 build: add offline Gradle wrapper 9.2.0 and portable build config`

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
   将 `gradle-9.2.0-bin` 发行版复制到 `D:\Android\Gradle\wrapper\dists`，使 `GRADLE_USER_HOME=D:\Android\Gradle` 时 wrapper 离线可用。

### 待解决的问题

- `MainActivity`/`ReminderScheduler` 重复加密代码（批次 3）。
- PDF 解析逻辑拆分与测试缺失（批次 4）。
- Lint 8 个警告：5 处 `commit()`、3 个未使用资源（批次 5）。

### 改进方向

- 构建入口统一为 `.\gradlew.bat`；后续脚本与文档都以 wrapper 为准。
- 可考虑后续开启 configuration cache（Gradle 已提示）。

---

## 2026-08-12 · 批次 3（短期）：抽取 SecureStorage 消除重复加密代码

**Git 提交**：`a4e1c3a refactor: extract SecureStorage for shared Keystore AES/GCM encryption`

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

**Git 提交**：`3e0bbc4 refactor: extract PDF course parsing into pure-Java PdfCourseParser`

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
