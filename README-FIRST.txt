UCAS Score Query Android v2.5.0 — GitHub 发布前说明
====================================================

1. 解压后进入项目目录。
2. 替换 README 与工作流中的 GitHub 用户名占位符：

   python scripts/configure_repository.py --github-user 你的GitHub用户名

3. 使用 JDK 17 和 Android SDK 36 执行：

   Windows:
   .\gradlew.bat testDebugUnitTest lintDebug assembleDebug

   macOS/Linux:
   ./gradlew testDebugUnitTest lintDebug assembleDebug

4. 初始化并推送仓库：

   git init
   git add .
   git commit -m "feat: initial open-source release"
   git branch -M main
   git remote add origin https://github.com/你的GitHub用户名/ucas-score-query-android.git
   git push -u origin main

5. 发布标签前，请按 docs/RELEASING.md 配置 GitHub Secrets。

注意：不要提交真实账号、密码、Token、Cookie、local.properties 或签名密钥。
