# ADR-0010 — Kotlin Multiplatform and Compose Multiplatform for mobile

## Status
Accepted

## Context
The mobile app must target both Android and iOS with feature parity: QR check-in, push notifications, wearable integration, offline support, biometric login, and native payment methods (Google Pay, Apple Pay). Three approaches were evaluated: fully native (separate Kotlin and Swift codebases), React Native / Flutter (cross-platform with a non-native UI layer), and Kotlin Multiplatform (KMP) with Compose Multiplatform (CMP). Fully native maximizes platform fidelity but doubles the development and maintenance cost for business logic. React Native and Flutter introduce a JavaScript or Dart runtime and a bridge layer that complicates integration with platform APIs like HealthKit, Health Connect, and secure enclaves. KMP allows sharing business logic (domain, data, presentation layers) in Kotlin across both platforms while using Compose Multiplatform for a shared declarative UI. The backend is already Kotlin/Spring Boot, so KMP keeps the entire stack in one language. Platform-specific capabilities (FCM, APNs, Keychain, Android Keystore, HealthKit, Health Connect) are handled via the `expect/actual` pattern with thin platform adapters.

## Decision
Use Kotlin Multiplatform (KMP) for shared business logic and Compose Multiplatform (CMP) for shared UI. The shared module contains domain, data, presentation, and UI layers. Platform-specific code lives in `androidMain` and `iosMain` source sets, accessed via `expect/actual` declarations or dependency injection. The Android host app and iOS host app contain only entry points, DI wiring, and platform setup — no business logic.

## Consequences
- Business logic, networking, local persistence, and UI are written once in Kotlin and shared across both platforms — significantly reducing duplication and divergence risk.
- The backend team and mobile team share the same language (Kotlin), enabling code review across teams and consistent domain modeling.
- Compose Multiplatform provides a declarative UI framework on both platforms with Material Design 3, supporting light/dark mode and RTL layout for Arabic.
- Platform-specific APIs (biometrics, health data, push notifications, payments) require `expect/actual` implementations per platform — these are thin adapters but must be maintained for both Android and iOS.
- The iOS build depends on a Kotlin/Native compilation step, which has longer build times than pure Swift and requires a macOS CI runner.
- Compose Multiplatform on iOS is newer than on Android — the team must track CMP releases and test iOS rendering carefully, especially for complex components like calendars and charts.
- The shared module must remain free of platform-specific imports (`android.*`, `UIKit`, `Foundation`) — violations break the build, enforced by CI.
