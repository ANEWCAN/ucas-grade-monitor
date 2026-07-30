# Contributing

Do not include real credentials, tokens, cookies, identities, sessions, or score
records in issues, tests, commits, or pull requests. Network-flow tests must use
fixtures or mocks and must not contact real academic systems from CI.

Create a branch, make a focused change, and run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Pull requests should explain the problem, approach, risk, and rollback behavior.
Include screenshots for UI changes. Do not commit APK/AAB files, signing keys,
`local.properties`, or other local secrets.
