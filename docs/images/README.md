# README 图片 / Image notes

返回 / Back: [简体中文](<../../README.md>) · [繁體中文](<../../README.zh-TW.md>) · [English](<../../README.en.md>)

## 素材 / Assets

| File | Purpose |
| --- | --- |
| [hero.svg](<hero.svg>) | Original vector artwork: ad-free, offline, no account required. The small grid is an illustration, not an app screenshot. |
| [timetable.png](<timetable.png>) | Real app screenshot of the weekly timetable, using fictional demo courses. |
| [agenda.png](<agenda.png>) | Real app screenshot of the agenda, using fictional demo courses and personal events. |
| [appearance.png](<appearance.png>) | Real app screenshot of the interface tone picker with 16 accent choices. |

三份 README 共用同一套图片，正文、替代文字和说明分别本地化。英文截图用于跨语言展示，并不表示应用只支持英文。应用支持简体中文、繁體中文和 English。

All three READMEs share these images, with localized text, alternative text and captions. English screenshots are used for a common visual reference; the app also supports Simplified and Traditional Chinese.

## 隐私 / Privacy

- 截图应来自独立的 `com.weekweeks.app.readme` 演示安装，不能从真实用户的课表中直接取图。
- 课程名称、教室、校历和个人安排均为虚构示例。图片不是应用内置数据，也不是首次启动时自动添加的课程。
- 演示包提醒关闭，不申请通知权限；不修改正式包、兼容测试包或更名前旧应用的数据。
- 禁止把真实课表 PDF、签名配置、密码或教师／学生个人资料放入本目录。

Screenshots are captured from an isolated demo installation, never a real user's timetable. Demo data is not bundled into production or injected on first launch. Keep reminders off in the demo and leave existing app installations untouched.

## 如何重新截图 / Reproducing screenshots

1. 按 [构建说明](<../BUILDING.md>) 配置环境。
2. 创建一个**仅用于本地截图**的构建变体：从 release 继承，应用 ID 后缀设为 `.readme`，使用 debug 签名，`PASTEL_UI=true`、`ENABLE_TEST_COURSE=false`。不要将其安装为正式包名。
3. 在该演示安装中设置虚构校历，添加虚构课程及个人安排；使用英文、浅色主题，保持提醒关闭。
4. 使用 Android 系统截图功能采集周课表、日程和设置界面，分别保存为上表中的 PNG 文件名。不要拼接伪造功能，也不要把设计稿称为实机截图。
5. 检查图片内容、可读性与个人信息；退出或停用独立演示包，再回到原先的正式应用。不要清除正式应用数据。

Build an isolated `.readme` variant, add only fictional data, capture the actual interface, and check the resulting images before publishing. Do not replace real screenshots with fabricated UI or overwrite a production installation to create demo content.

## 素材与授权 / Asset and licensing notes

The banner is original artwork created for this repository. Screenshots show the app running with fictional data. No third-party stock photos, remote image services or externally hosted badges are used. These notes do not grant a separate license: the banner and screenshots are part of this repository and are covered by the [PolyForm Noncommercial License 1.0.0](<../../LICENSE>), while any third-party fonts or components remain subject to their respective terms.
