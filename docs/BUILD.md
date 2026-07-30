# 构建与运行

## 环境

- JDK 17；
- Android SDK Platform 36；
- Android SDK Build-Tools；
- Android SDK Platform-Tools（ADB）；
- Android 8.0 或更高版本的真机/模拟器。

## Android Studio

1. 使用 Android Studio 打开仓库根目录；
2. 选择 JDK 17；
3. 等待 Gradle Sync；
4. 选择设备后运行 `app`；
5. 首次运行授予通知权限。

## VS Code / 命令行

在仓库根目录复制 SDK 配置：

```powershell
Copy-Item local.properties.example local.properties
```

编辑 `local.properties`：

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
```

Windows：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

macOS/Linux：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew installDebug
```

APK 路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 常见问题

### `SDK location not found`

创建 `local.properties` 并填写正确的 `sdk.dir`。

### Java 版本错误

```bash
java -version
./gradlew --version
```

两者都应使用 JDK 17。

### 找不到 API 36

在 Android Studio 的 SDK Manager 中安装 Android SDK Platform 36。

### 真机未识别

```bash
adb devices
```

确认 USB 调试已开启并在手机上允许当前电脑。
