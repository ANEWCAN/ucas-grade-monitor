# 隐私说明 / Privacy Notice

## 本地存储

应用会在设备本地保存以下信息：

- 国科大账号；
- 国科大密码；
- CSTCloud API Token；
- 自动查询配置；
- 最近查询记录与成绩基线。

账号、密码和 Token 使用 Android Keystore 生成的 AES-256-GCM 密钥加密后保存到应用私有 `SharedPreferences`。应用关闭系统备份，并从设备迁移与云备份中排除应用数据。

## 网络请求

应用仅为完成用户主动配置的功能而连接：

- `https://sep.ucas.ac.cn`；
- `https://jwxk.ucas.ac.cn`；
- `https://uni-api.cstcloud.cn`。

验证码图片会发送至用户配置并授权使用的 CSTCloud 模型接口。项目不包含自建服务器、广告 SDK、统计 SDK 或第三方跟踪组件。

## 删除数据

在系统设置中清除应用数据或卸载应用，即可删除本机保存的配置和查询记录。

---

The app stores credentials and query state locally. Credentials are encrypted
with an AES-256-GCM key held by Android Keystore. The app has no project-owned
backend, advertising SDK, analytics SDK, or tracking component. Captcha images
are sent to the CSTCloud API selected by the user because that is required for
captcha recognition.
