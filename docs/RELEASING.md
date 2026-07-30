# 发布与签名

## 不要提交签名文件

发布密钥、密码和 keystore 不得提交到仓库。丢失发布密钥后，已有用户可能无法覆盖安装后续版本。

## 创建签名密钥

```bash
keytool -genkeypair -v \
  -keystore ucas-score-query-release.jks \
  -alias ucas-score-query \
  -keyalg RSA -keysize 4096 -validity 10000
```

请离线备份 keystore 和密码。

## GitHub Secrets

在仓库 `Settings → Secrets and variables → Actions` 中配置：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key 密码 |

Windows 生成 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("ucas-score-query-release.jks")) |
  Set-Content -NoNewline keystore.base64.txt
```

## 创建发布

更新 `versionCode`、`versionName` 和 `CHANGELOG.md`，然后：

```bash
git tag -a v2.5.0 -m "UCAS Score Query Android v2.5.0"
git push origin v2.5.0
```

Release 工作流会构建签名 APK 与 AAB，并创建 GitHub Release。
