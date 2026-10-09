# CountMe

Attendance tracker. Data lives only on the user's device and in the user's own Google Drive. There is no developer backend.

## Features
- Subjects with target %, categories, search, present/absent marking, per-subject history
- Safe-to-miss / must-attend calculator
- Optional Google sign-in: data syncs to `My Drive/CountMe/countme_data.json` (readable JSON, visible in Drive)
- Export as JSON, CSV or PDF, either to any location on the device (system file picker, Drive included) or into `CountMe/Exports` in Drive

## Google setup (one time, developer side)
No secrets are stored in the app. Google identifies the app by package name + signing certificate.
1. Google Cloud Console: create a project, enable **Google Drive API**.
2. OAuth consent screen: add scope `.../auth/drive.file` (non-sensitive: app only sees files it created).
3. Credentials, create **OAuth client ID, type Android**: package `com.jd_s4nd_b0x.CountMe`, SHA-1 of your signing key (debug: `./gradlew signingReport`). Add one per key (debug and release/Play).
4. While consent screen is in "Testing", add tester Google accounts.

## Privacy model
- Scope `drive.file`: the app cannot read any other Drive file.
- Auth tokens come from Google Play services; never sent anywhere except Google's Drive API.
- Sign-out removes the local copy; Drive copy stays and is restored on next sign-in.
- Conflict rule: newest `modified` timestamp wins.

## Quality gates and releases

Every push and pull request runs formatting, Checkstyle, PMD, SpotBugs, Error Prone, Android lint,
unit tests with a coverage gate, CodeQL and a secret scan. A failing check blocks the merge. A
`vX.Y.Z` tag builds, signs and publishes the release APK. See [CONTRIBUTING.md](CONTRIBUTING.md).
