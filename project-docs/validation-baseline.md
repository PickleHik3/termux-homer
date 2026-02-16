# Validation Baseline

## Build Configuration

### Environment Requirements
- Java 17 (as specified in GitHub workflow)
- Android SDK with compileSdkVersion 34
- NDK version 26.3.11579264

### Build Commands

```bash
# Debug build (used by CI)
export TERMUX_APP_VERSION_NAME="0.119.0-b1+monet36"
export TERMUX_APK_VERSION_TAG="v0.119.0-b1+monet36-apt-android-7-github-debug"
export TERMUX_PACKAGE_VARIANT="apt-android-7"
./gradlew assembleDebug

# Expected outputs in app/build/outputs/apk/debug/:
# - termux-app_<version>_universal.apk
# - termux-app_<version>_arm64-v8a.apk
# - termux-app_<version>_armeabi-v7a.apk
# - termux-app_<version>_x86_64.apk
# - termux-app_<version>_x86.apk
```

## Test Commands

```bash
# Unit tests
./gradlew :app:testDebugUnitTest :terminal-emulator:test
```

## GitHub Workflows

### Build Workflow
- **File**: `.github/workflows/debug_build.yml`
- **Trigger**: Manual (`workflow_dispatch`)
- **Java Version**: 17
- **Package Variant**: apt-android-7

### Test Workflow  
- **File**: `.github/workflows/run_tests.yml`
- **Trigger**: Manual (`workflow_dispatch`)
- **Java Version**: 17

## Gradle Configuration

### Key Properties (gradle.properties)
- minSdkVersion: 26
- targetSdkVersion: 28
- compileSdkVersion: 34
- ndkVersion: 26.3.11579264
- markwonVersion: 4.6.2

### JVM Args
```
org.gradle.jvmargs=-Xmx2048M \
  --add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED
```

## Smoke Test Checklist

### Launcher Functionality
- [ ] App appears in launcher app list
- [ ] Can be set as default launcher
- [ ] Home button opens termux-launcher

### Suggestion Bar
- [ ] Suggestion bar visible below terminal
- [ ] Typing app name shows matching apps
- [ ] Tapping app button launches app
- [ ] Icon display works

### Terminal
- [ ] Terminal opens
- [ ] Shell prompt appears
- [ ] Basic commands work (ls, cd, pwd)

### Background/Wallpaper
- [ ] Background image can be set from settings
- [ ] System wallpaper sync works
- [ ] Blur effect applies (if enabled)

### Material You (Android 12+)
- [ ] Dynamic colors apply on API 31+
- [ ] Colors match system accent

## Known Issues / Notes

- Version scheme: `0.119.0-b1+monet36` (semantic versioning with build metadata)
- Uses Material3 DynamicColors on Android 12+ (values-v31/themes.xml)
- Custom signing config in debug builds
- ProGuard enabled for release builds

## Validation Status

**Date**: 2025-02-16
**Commit**: 1a6e190e (pre-rebase checkpoint)
**Build Status**: User confirmed successful build
**Test Status**: To be verified with command above

---

*This baseline will be used to validate the post-rebase build.*
