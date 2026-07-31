# 贡献指南

感谢你参与改进国科大成绩助手。

## 开始之前

- 不要在 Issue、PR、测试或提交记录中放入真实账号、密码、Token、Cookie 或成绩；
- 网络请求测试必须使用伪造页面或 Mock，不得在 CI 中访问真实教务系统；
- 对认证流程的修改应尽量局部、可回滚，并补充离线测试；
- UI 修改不应破坏 v2.6.0 已验证可用的查询流程。

## 开发流程

```bash
git checkout -b feature/your-change
./gradlew testDebugUnitTest lintDebug assembleDebug
```

提交信息建议使用 Conventional Commits：

```text
feat: add semester filter
fix: handle captcha string response
refactor: isolate query retry policy
docs: improve build guide
```

## Pull Request 要求

- 描述问题、实现方式和风险；
- UI 修改附截图；
- 网络流程修改说明失败回退策略；
- 所有测试、Lint 和调试 APK 构建通过；
- 不提交生成的 APK、AAB、签名文件或 `local.properties`。

## 代码风格

- Java 17；
- 四空格缩进；
- 公开或复杂逻辑添加简洁注释；
- 用户可见文字保持中文；
- 避免增加不必要的第三方 SDK。
