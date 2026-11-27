# Film Merkezi Android TV / Google TV

Native Kotlin / Leanback TV UI and a single Media3/ExoPlayer. Film Merkezi Klasik keeps the original logo, Turkish interface, DPAD controls, shared content identity, series tracking, library and playback preferences.

Current release: **0.12.2 / 38**, including the plugin-process HTTP initialization fix and P002–P005 default order. [APK, pilot packages and targeted validation](docs/pilot-p002-p005-report.md).

## Modular distribution

- APK: native UI, settings, storage, identity, network and Media3.
- Separate signed Kotlin/DEX packages: site catalogues and source resolvers.
- [Android extension repository](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler): controlled P002/R002 PuhuTV, P003/R003 NOW, P004/R004 Filmci Baba and P005/R005 Star TV pilot. Other withdrawn addons remain unpublished. These eight packages require app versionCode 38.
- [SDK, signatures, dependencies and publisher guide](docs/android-plugin-sdk.md).
- [Signing migration and data transfer](docs/android-signing-migration.md).
- [Delivery and actual validation](docs/modular-0120-report.md).

Checks run while the app is open and idle, at most once per 30 minutes. Users explicitly choose download/apply and trust extension publishers. No repository is preselected. Dynamic native code runs in a helper process with the app UID; it is not an untrusted-code sandbox.

APK releases are published under [Releases](https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases). Version 0.11.5 / 35 is the old-signature data-transfer bridge; 0.12.0 / 36 uses the permanent signing key. Save an external backup before removing an old-signature installation.

## Build

Requires JDK 25, Android SDK platform 37 / Build Tools 36.1.0. Gradle wrapper and dependencies are pinned. Tizen/Node/WebView are not production runtime dependencies. minSdk 23; the DEX-only APK supports 32/64-bit ARM and x86_64 without bundled native libraries.

```text
git clone https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV.git Android
git clone https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler.git Android-Eklentiler
```

The native APK builds without the extension checkout. The sibling extension runtime is required for deterministic adapter tests and migration APK builds. Alternatively set `FM_ADDONS_DIR` to the absolute extension checkout path.

```text
android/gradlew -p android :native:assembleRelease :native:lintRelease
android/gradlew -p android :native:testDebugUnitTest
android/gradlew -p android :native:assembleRelease -PmigrationBuild=true
node --test tests/android-distribution.test.cjs
```

Release builds are shrunk and unsigned until [local signing](tools/sign-release.ps1). Never commit private keys, signing passwords, local device data or generated reports. CI contains no signing secrets and creates an unsigned APK.

The application uses NewPipeExtractor for YouTube; its licenses and complete third-party notices are included under `android/native/src/main/assets`. Original Film Merkezi source keeps its MIT notice; dependency licenses apply to their respective components.

Historical feature and device reports remain under [docs](docs/). Real ARM TV validation remains a separate device acceptance step; emulator results are not ARM performance measurements.
