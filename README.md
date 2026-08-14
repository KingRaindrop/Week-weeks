# 我的课表

一个基于 Android 16 / API 36 的原生 Android 课表应用，采用现代化 UI 设计。

## 当前功能

- 周一到周日切换查看课程
- 添加、编辑、删除课程
- 支持开始节次、结束节次、上课周、课程名称、地点、老师和颜色
- 支持从学校 PDF 课表导入课程名称、上课周、上课地点、教师名称和节次
- 支持在线导入嘉应学院官网校历（仅 HTTPS、域名白名单），自动计算当前教学周
- 打开应用自动定位到当天
- 同一天、同节次、同周次的课程会阻止重复添加
- 课程状态统一区分未到提醒窗口、课前提醒、进行中和已结束
- 课程提醒支持后续周次续排、重启恢复、通知/精确闹钟状态检查
- 使用 Android Keystore + AES/GCM 加密保存课程数据
- 禁用应用数据备份、设备迁移导出和明文网络流量
- 目标 Android 16：`compileSdk 36.1`、`targetSdk 36`

## UI 特性

- **手动主题切换**：点击右上角主题按钮，支持三种模式
  - ⚙️ 自动：跟随系统主题
  - ☀️ 浅色：固定使用浅色模式
  - 🌙 深色：固定使用深色模式
- **现代化设计**：
  - 优雅的配色方案和圆角设计
  - 提升的卡片阴影效果
  - 更大的标题字体和合理的间距
  - 精致的节次徽章设计
- **智能高亮**：
  - 今天的日期用紫色标记并带有圆点指示器
  - 选中的日期用蓝色高亮
- **统一布局**：所有元素视觉对齐，层次分明

## 配色方案

### 浅色模式
- 背景：`#F8F8FA` 浅灰白
- 卡片：`#FFFFFF` 纯白
- 主要文字：`#141416` 深灰黑
- 次要文字：`#64696E` 中灰
- 强调色：`#2563EB` 蓝色
- 今天标记：`#6366F1` 紫色

### 深色模式
- 背景：`#121214` 深黑灰
- 卡片：`#202225` 深灰
- 主要文字：`#F0F0F2` 浅白
- 次要文字：`#9BA0A6` 浅灰
- 强调色：`#58A6FF` 亮蓝
- 今天标记：`#4338CA` 深紫

## PDF 导入

点击右上角“导入PDF”，选择学校导出的课表 PDF。

当前导入逻辑针对学校 iText 生成的文字版课表做了解析，已用桌面的 `(2025-2026-2)课表.pdf` 样例验证，可提取：

- 课程名称
- 上课周，例如 `1-6周,9-17周`
- 上课地点
- 上课教师
- 星期和节次范围，例如 `第9-10节`

导入不会申请读取外部存储权限，只通过 Android 系统文件选择器读取用户选中的 PDF。单个 PDF 限制为 5MB，压缩文本流也有限制，避免异常文件消耗过多内存。


## 校历在线导入

设置中可导入嘉应学院官网校历，用于计算当前教学周与学期进度。请求约束：

- 仅接受 `https://` 链接，且域名必须是 `jwc.jyu.edu.cn` 或 `*.jyu.edu.cn`。
- 关闭自动重定向，HTTP 非 2xx 直接报错。
- 响应体上限 512KB，超时 8 秒。
- 解析时会从页面文本中按"报到/开学/第一周"与"散学/放假/暑假"等关键词附近
  提取开学日与散学日，自动计算总周数；无法解析出合法日期时导入失败，
  不会写入数据，并引导在「校历设置」中手动填写开学日/散学日。
- 校历设置中可直接编辑校历网页链接，换学期后无需改代码。

## 安全设计

- 课程数据不会以明文 JSON 写入 `SharedPreferences`。
- 首次运行新版本时，如果发现旧的明文 `courses` 数据，会自动迁移到加密字段 `courses_encrypted_v2`，并删除旧字段。
- 如果密钥丢失、密文损坏或数据被篡改，应用会暂停保存，避免用空数据覆盖原始密文。
- 课程名称、上课周、地点、老师字段有长度限制；课程总数有上限；颜色只接受应用内置色板。
- PDF 导入使用大小限制、字段清洗和课程数量上限。
- `android:allowBackup="false"`、`android:dataExtractionRules` 和 `android:usesCleartextTraffic="false"` 已启用。
- 应用仅持有 `INTERNET` 权限用于在线校历导入：仅允许 HTTPS、仅限 `jyu.edu.cn` 官方域名、禁止自动重定向、响应体限制 512KB；没有外部存储读取/写入权限。

## 构建方式

项目自带 Gradle Wrapper（Gradle 9.2.0），推荐直接用：

```powershell
.\gradlew.bat --no-daemon assembleDebug
```

首次运行时 wrapper 会按 `GRADLE_USER_HOME`（默认 `~/.gradle`）查找/下载 Gradle 发行版。当前机器建议使用 D 盘 Android 数据目录（可先设置环境变量，再运行 wrapper）：

- SDK：`D:\Android\Sdk`
- Gradle 缓存：`D:\Android\Gradle`
- JDK：`D:\Android Studio\jbr`

```powershell
$env:JAVA_HOME="D:\Android Studio\jbr"
$env:ANDROID_HOME="D:\Android\Sdk"
$env:ANDROID_SDK_ROOT="D:\Android\Sdk"
$env:GRADLE_USER_HOME="D:\Android\Gradle"
.\gradlew.bat --no-daemon assembleDebug
```

生成的调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以在项目目录运行一键构建与 lint 脚本（自动使用 wrapper）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build-verify.ps1
```

## 发布签名

Release 签名凭据按以下顺序解析（`app/build.gradle`）：

1. 环境变量：`MEIN_STORE_FILE`、`MEIN_STORE_PASSWORD`、`MEIN_KEY_ALIAS`、`MEIN_KEY_PASSWORD`（推荐 CI 使用）。
2. 项目内 `secrets/release-signing.properties`（模板见 `secrets/release-signing.properties.template`）。
3. 用户目录 `~/.gradle/mein-stundenplan-signing.properties`。

`secrets/` 目录整体被 `.gitignore` 忽略（仅保留模板），keystore 与密码不会进入版本库。

## 本地安全审计

在 PowerShell 中运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\security-audit.ps1
```

该脚本会检查备份策略、明文流量、危险权限、Keystore 加密保存、明文写回风险、输入边界、PDF 导入边界、动态代码加载、命令执行、WebView JS 桥、Java 反序列化和不安全 TLS 入口。

## 逻辑与源码编译检查

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-logic.ps1
```

该脚本会验证状态机边界、周次/节次匹配、提醒续排时间，并使用 Android 36.1 `android.jar` 对全部 Java 源码执行 Java 17 编译检查。
