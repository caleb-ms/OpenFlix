# Fix KSP Property Missing Value Error

The project is using Android Gradle Plugin (AGP) 9.3.1 and Gradle 9.5.0, but it is still using an older Kotlin version (2.0.21) and has opted out of "Built-in Kotlin" support. The error `Cannot query the value ... property 'classpathSnapshotProperties.useClasspathSnapshot'` is caused by an incompatibility between the KSP plugin and the newer Gradle/AGP environment.

This plan migrates the project to AGP 9.0's "Built-in Kotlin" support and updates Kotlin/KSP versions to resolve the incompatibility.

## User Review Required

> [!IMPORTANT]
> This change migrates the project to AGP 9.0's Built-in Kotlin support. This removes the need for the `kotlin-android` plugin.

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle.properties](file:///home/caleb/AndroidStudioProjects/OpenFlix/gradle.properties)
- Enable `android.builtInKotlin`.
- Enable `android.newDsl`.

#### [MODIFY] [libs.versions.toml](file:///home/caleb/AndroidStudioProjects/OpenFlix/gradle/libs.versions.toml)
- Update `kotlin` version to `2.2.10` (minimum for AGP 9.0).
- Remove `kotlinAndroid` plugin from the catalog as it's no longer needed.

#### [MODIFY] [build.gradle.kts](file:///home/caleb/AndroidStudioProjects/OpenFlix/build.gradle.kts) (root)
- Remove `alias(libs.plugins.kotlinAndroid) apply false`.

#### [MODIFY] [app/build.gradle.kts](file:///home/caleb/AndroidStudioProjects/OpenFlix/app/build.gradle.kts)
- Remove `alias(libs.plugins.kotlinAndroid)`.
- Update KSP version to `2.2.10-2.0.2` which is compatible with AGP 9.0's default KGP version.
- Migrate `kotlinOptions` if present (already checked, it uses standard `compileOptions`).

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify the build completes successfully.
- Run Room database related tests if available to ensure KSP is working.

### Manual Verification
- Verify that the IDE no longer shows errors in Kotlin files.
