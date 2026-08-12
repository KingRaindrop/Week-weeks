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