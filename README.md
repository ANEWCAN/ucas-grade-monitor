<div align="center">
  <img src="docs/images/app-icon.png" width="112" alt="国科大成绩助手图标">
  <h1>国科大成绩助手</h1>
  <p>面向 Android 的 UCAS 成绩查询、定时检查与本地通知工具。</p>

  [English](README.en.md) · [构建说明](docs/BUILD.md) · [安全政策](SECURITY.md) · [隐私说明](PRIVACY.md)

  ![Android CI](https://github.com/ANEWCAN/ucas-score-query-android/actions/workflows/android-ci.yml/badge.svg)
  ![License](https://img.shields.io/badge/license-MPL--2.0-blue)
  ![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
</div>

> [!IMPORTANT]
> 本项目是非官方社区工具，与中国科学院大学、教务系统或 CSTCloud 不存在隶属或授权关系。使用前请阅读 [免责声明](DISCLAIMER.md)。

## 功能

- 手动查询研究生成绩；
- 以课程卡片和学期分组展示成绩；
- 学位课黄色描边高亮；
- 定时自动查询，可选择 15 分钟至 24 小时的间隔；
- 可选择“仅成绩变化时通知”或“每次查询都通知”；
- 保存最近一次手动查询、自动查询与成功查询结果；
- 支持 0、1、2、3、5 次整轮失败重试；
- 对网络超时、HTTP 422 stream timeout、429 和常见 5xx 错误进行重试；
- 手机重启或应用升级后恢复自动查询；
- 账号、密码与 Token 使用 Android Keystore + AES-256-GCM 加密保存；
- 全中文应用界面，无广告、无统计 SDK、无项目自建服务器。

## 界面结构

| 页面 | 内容 |
|---|---|
| 成绩 | 查询状态、最近更新时间、按学期分组的课程卡片 |
| 查询 | “查询最新成绩”按钮、运行状态、手动和自动查询记录 |
| 设置 | 账号、Token、模型、自动查询间隔、重试次数和通知策略 |

## 环境要求

- Android Studio 或兼容的 Android SDK 命令行环境；
- JDK 17；
- Android SDK 36；
- Android 8.0（API 26）或更高版本的设备。

## 快速开始

```bash
git clone https://github.com/ANEWCAN/ucas-score-query-android.git
cd ucas-score-query-android
```

在 Windows 中：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

在 macOS/Linux 中：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

完整环境配置、Android Studio 和 VS Code 操作见 [docs/BUILD.md](docs/BUILD.md)。

## 发布版本

仓库包含标签触发的 GitHub Release 工作流。发布签名必须通过 GitHub Secrets 提供，仓库中不包含签名密钥。配置方法见 [docs/RELEASING.md](docs/RELEASING.md)。

## 项目结构

```text
app/src/main/java/io/github/ucasscorequery/android/
├── MainActivity.java          # 三页面原生 Android UI
├── ScoreQueryClient.java      # SEP/JWXK 登录、验证码和成绩解析
├── QueryRunner.java           # 查询重试策略
├── QueryJobService.java       # 后台定时查询
├── Scheduler.java             # JobScheduler 调度
├── AppPrefs.java              # 设置、记录和成绩基线
├── SecureStore.java           # Android Keystore 加密
└── NotificationHelper.java    # 本地通知
```

更详细的模块说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 数据与隐私

项目没有自建后端。应用仅在实现功能时连接 SEP、JWXK 和 CSTCloud。验证码图片会被发送至 CSTCloud 接口用于识别；真实账号、密码和 Token 不应提交到 Issue、日志或仓库。详见 [PRIVACY.md](PRIVACY.md)。

## 贡献

欢迎提交 Issue 和 Pull Request。提交前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，并确保：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

能够通过。

## 许可与致谢

本项目采用 [Mozilla Public License 2.0](LICENSE)。认证和成绩查询流程参考了 [wirsbf/traintime_pda_ucas](https://github.com/wirsbf/traintime_pda_ucas)，该项目同样采用 MPL-2.0。详细归属信息见 [NOTICE](NOTICE)。
