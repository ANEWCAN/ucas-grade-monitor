# 安全政策 / Security Policy

## 支持版本

| 版本 | 安全更新 |
|---|---|
| 2.5.x | 支持 |
| 2.4.x 及更早 | 不保证 |

## 报告漏洞

请使用 GitHub 仓库的 **Security → Report a vulnerability** 私密报告功能。不要在公开 Issue 中提交以下内容：

- 真实账号、密码或 Token；
- Cookie、Session、Identity 参数；
- 包含个人成绩的完整日志或截图；
- 可直接复现的敏感利用细节。

维护者确认问题前不会要求你提供真实密码。建议在日志中用 `***` 替换敏感值。

## 安全边界

- 密钥由 Android Keystore 生成且不可从应用代码中导出；
- 凭据使用 AES-GCM 加密后保存；
- 禁止明文 HTTP；
- 禁止应用数据备份；
- 仓库和 APK 不包含用户凭据或发布签名私钥。

## Supported versions and reporting

Security fixes target the latest 2.5.x release. Report vulnerabilities through
GitHub private vulnerability reporting. Never post real credentials, tokens,
cookies, sessions, or personal score records in public issues.
