# Arena Mobile

Kotlin Multiplatform + Compose Multiplatform member mobile app (Android + iOS).

## Prerequisites

- JDK 21+
- Android Studio (latest stable) with Kotlin Multiplatform plugin
- Xcode 15+ (macOS, for iOS builds)
- CocoaPods (`sudo gem install cocoapods`)

## Build & run

```bash
# Shared module tests
./gradlew :shared:test

# Android debug build
./gradlew :androidApp:assembleDebug

# iOS — install pods first
cd iosApp && pod install
# Then open iosApp.xcworkspace in Xcode
```

## Environment variables

See the monorepo root `.env.example` for backend API configuration.
