<div align="center">
  <img src="docs/images/app-icon.png" width="112" alt="UCAS Score Query icon">
  <h1>UCAS Score Query for Android</h1>
  <p>An Android app for querying UCAS scores, scheduled checks, and local notifications.</p>

  [简体中文](README.md) · [Build](docs/BUILD.md) · [Security](SECURITY.md) · [Privacy](PRIVACY.md)

  ![Android CI](https://github.com/ANEWCAN/ucas-score-query-android/actions/workflows/android-ci.yml/badge.svg)
  ![License](https://img.shields.io/badge/license-MPL--2.0-blue)
  ![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
</div>

> [!IMPORTANT]
> This is an unofficial community project and is not affiliated with or endorsed by UCAS, its academic systems, or CSTCloud.

## Highlights

- Manual and scheduled score queries;
- Semester-grouped course cards with degree-course highlighting;
- Notification on changes only, or after every scheduled query;
- Configurable retry count and handling for transient network/API failures;
- Recent manual, automatic, and last-successful query records;
- Android Keystore backed AES-256-GCM credential encryption;
- No ads, analytics SDK, tracking SDK, or project-owned backend;
- Chinese application UI.

## Build

Requirements: JDK 17, Android SDK 36, and an Android 8.0+ device.

```bash
git clone https://github.com/ANEWCAN/ucas-score-query-android.git
cd ucas-score-query-android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
See [docs/BUILD.md](docs/BUILD.md) for Android Studio, VS Code, and command-line instructions.

## Release signing

Release signing keys are never committed. The tag-based GitHub Release workflow reads signing material from repository secrets. See [docs/RELEASING.md](docs/RELEASING.md).

## License and attribution

Licensed under [MPL-2.0](LICENSE). The UCAS authentication and score-query flow was implemented with reference to [wirsbf/traintime_pda_ucas](https://github.com/wirsbf/traintime_pda_ucas), also licensed under MPL-2.0. See [NOTICE](NOTICE).
