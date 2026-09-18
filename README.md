# PRC

A minimal Android app built with **Kotlin** and **Jetpack Compose** (Material 3).

## Toolchain

| Component | Version |
|---|---|
| Kotlin | 2.4.20 (built into AGP 9) |
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.7.1 |
| JDK | 17+ (21 recommended) |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

Library versions are pinned in `gradle/libs.versions.toml` to the newest set
compatible with compileSdk 36 (Compose BOM 2026.06.01 / Compose 1.11.4,
core-ktx 1.18.0, activity-compose 1.12.4, lifecycle 2.10.0).

## Build & test

```bash
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # local unit tests
./gradlew installDebug         # install on a connected device/emulator
```

Requires `ANDROID_HOME` (or `local.properties` with `sdk.dir`) pointing at an
Android SDK with platform 36, and `JAVA_HOME` at a JDK 17+.

## Structure

```
app/src/main/java/com/prc/app/MainActivity.kt   # Compose entry point
app/src/main/java/com/prc/app/ui/theme/         # Material 3 theme (dynamic color on Android 12+)
app/src/test/                                   # Local unit tests (JUnit)
app/src/androidTest/                            # Instrumented tests (requires device/emulator)
```
