# CLAUDE.md — mobile-arena (Member Mobile App)

This repository is a **git submodule** of the main monorepo.
It inherits the global rules defined in the monorepo root `CLAUDE.md`.
This file adds rules specific to `mobile-arena`.

---

## 1. What this app is

**Arena Mobile** is the full-featured member experience for Android and iOS.
It is the premium counterpart to `web-arena` — everything the web app offers, plus
device-native capabilities that justify it as a separate investment.

Capabilities exclusive to mobile (not in `web-arena`):
- QR code check-in at the club gate
- Push notifications (FCM on Android, APNs on iOS)
- Wearable / fitness tracker integration (Health Connect on Android, HealthKit on iOS)
- Offline-capable core screens (schedule, session notes, profile)
- Apple Pay / Google Pay
- Camera-based body metrics (future — architecture must not prevent it)
- Gamification: streaks, badges, leaderboard
- Social feed and member community
- Live class streaming (future)
- Personalized AI workout suggestions (future)
- Biometric login (fingerprint / Face ID)

Users of this app are club members only — identical audience to `web-arena`.
The shared backend API serves both. No mobile-specific backend endpoints unless
strictly required by a device capability (e.g., push token registration).

---

## 2. Technology stack

| Layer | Technology |
|---|---|
| Language | Kotlin (all targets) |
| Shared logic | Kotlin Multiplatform (KMP) |
| Shared UI | Compose Multiplatform (CMP) |
| Android host | Android (minSdk 26 / Android 8.0) |
| iOS host | iOS 16+ |
| Build system | Gradle with Kotlin DSL (`build.gradle.kts` only — no Groovy) |
| Dependency injection | Koin (multiplatform-compatible) |
| Networking | Ktor Client (multiplatform) |
| Serialization | kotlinx.serialization |
| Local storage | SQLDelight (multiplatform SQL) |
| Async | Kotlin Coroutines + Flow |
| Navigation | Compose Navigation (multiplatform) |
| Image loading | Coil 3 (multiplatform) |
| Date/time | kotlinx-datetime |
| Logging | Kermit (multiplatform logger) |
| Testing (shared) | kotlin.test + Turbine (Flow testing) |
| Testing (Android) | JUnit 4, Robolectric, Compose UI Test |
| Testing (iOS) | XCTest via KMP test targets |
| Crash reporting | Firebase Crashlytics (platform-specific, wired via `expect/actual`) |
| Analytics | Firebase Analytics (platform-specific, wired via `expect/actual`) |

---

## 3. Module structure

```
mobile-arena/
├── shared/                          ← KMP module: all shared logic and UI
│   ├── src/
│   │   ├── commonMain/              ← shared across all platforms
│   │   │   └── kotlin/com/arena/
│   │   │       ├── domain/          ← pure business logic, zero dependencies
│   │   │       │   ├── model/       ← data classes: Member, Membership, GXClass...
│   │   │       │   ├── repository/  ← interfaces (ports) only
│   │   │       │   └── usecase/     ← use cases, business rules
│   │   │       ├── data/            ← repository implementations (adapters)
│   │   │       │   ├── remote/      ← Ktor API client, DTOs, mappers
│   │   │       │   ├── local/       ← SQLDelight queries, local cache
│   │   │       │   └── repository/  ← concrete repository impls
│   │   │       ├── presentation/    ← ViewModels (using StateFlow), UI state
│   │   │       │   ├── home/
│   │   │       │   ├── classes/
│   │   │       │   ├── sessions/
│   │   │       │   ├── progress/
│   │   │       │   ├── payments/
│   │   │       │   ├── messages/
│   │   │       │   ├── notifications/
│   │   │       │   └── account/
│   │   │       └── ui/              ← Compose Multiplatform composables
│   │   │           ├── screens/     ← one file per screen
│   │   │           ├── components/  ← reusable composables
│   │   │           ├── navigation/  ← NavHost, routes, deep links
│   │   │           └── theme/       ← MaterialTheme, typography, colors, shapes
│   │   │
│   │   ├── androidMain/             ← Android-specific implementations
│   │   │   └── kotlin/com/arena/
│   │   │       ├── actual/          ← actual implementations for expect declarations
│   │   │       └── platform/        ← HealthConnect, FCM, biometrics, QR scan
│   │   │
│   │   ├── iosMain/                 ← iOS-specific implementations
│   │   │   └── kotlin/com/arena/
│   │   │       ├── actual/          ← actual implementations for expect declarations
│   │   │       └── platform/        ← HealthKit, APNs, Face ID, QR scan
│   │   │
│   │   ├── commonTest/              ← shared unit tests
│   │   ├── androidUnitTest/         ← Android-specific unit tests
│   │   └── iosTest/                 ← iOS-specific unit tests
│   │
│   └── build.gradle.kts
│
├── androidApp/                      ← Android host application
│   ├── src/main/
│   │   ├── kotlin/com/arena/android/
│   │   │   ├── MainActivity.kt      ← entry point, sets up Compose window
│   │   │   ├── ArenaApplication.kt  ← Koin init, platform setup
│   │   │   └── di/                  ← Android-specific DI modules
│   │   ├── res/                     ← Android resources (launcher icons, strings)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── iosApp/                          ← iOS host application (Xcode project)
│   ├── iosApp/
│   │   ├── iOSApp.swift             ← entry point, sets up ComposeUIViewController
│   │   ├── AppDelegate.swift        ← APNs registration, Firebase setup
│   │   └── Info.plist
│   ├── iosApp.xcodeproj/
│   └── Podfile
│
├── gradle/
│   └── libs.versions.toml           ← version catalog (single source of truth for all deps)
├── build.gradle.kts                 ← root build file
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
├── CLAUDE.md                        ← this file
└── README.md
```

---

## 4. Architecture — Clean Architecture on KMP

The architecture mirrors the backend's Clean/Hexagonal approach.
The same principles, the same layering, the same direction of dependencies.

```
UI (Composables)
    ↓
Presentation (ViewModels / UI State)
    ↓
Domain (Use Cases + Repository Interfaces)   ← pure Kotlin, zero framework deps
    ↓
Data (Repository Implementations + Remote + Local)
    ↓
Platform (expect/actual — device APIs)
```

### Dependency rule

Dependencies point inward only. The domain layer knows nothing about Ktor, SQLDelight,
Compose, or any platform SDK. If you find yourself importing Ktor inside a use case,
the architecture is wrong — fix the use case, not the rule.

### Layer responsibilities

**domain/** — the heart of the app. Pure Kotlin. No annotations, no framework imports.
- `model/` — immutable data classes representing business concepts.
  These are not DTOs and not database entities. They are the truth.
- `repository/` — interfaces (ports) declared in the domain, implemented in data.
- `usecase/` — one public function per class. Orchestrates domain objects.
  Accepts domain models, returns domain models. All business rules live here.

**data/** — adapters that implement the domain's ports.
- `remote/` — Ktor client, JSON DTOs, mappers (DTO → domain model).
  DTOs live here, not in domain. Mappers are pure functions.
- `local/` — SQLDelight schemas and generated query wrappers.
  Local entities live here. Mappers to domain models are pure functions.
- `repository/` — concrete implementations of domain repository interfaces.
  Decide whether to fetch from remote, serve from cache, or both.

**presentation/** — ViewModels using `StateFlow<UiState>`.
- One ViewModel per screen or tightly related screen group.
- ViewModels call use cases. They do not call repositories directly.
- `UiState` is a sealed class or data class — always a snapshot of what the UI should show.
- No business logic in ViewModels. If a ViewModel is computing something, extract it to a use case.

**ui/** — Compose Multiplatform composables.
- Composables observe `UiState` and emit `UiEvent` back to the ViewModel.
- No business logic, no direct repository calls, no coroutine launching (except `LaunchedEffect` for one-time effects).
- Composables are pure functions of their input state.

---

## 5. Gradle & dependency management

- Use **Kotlin DSL** (`build.gradle.kts`) exclusively. No Groovy `.gradle` files.
- All dependency versions are declared in `gradle/libs.versions.toml` (version catalog).
  Never hardcode a version string in a `build.gradle.kts` file — always reference the catalog.
- The root `build.gradle.kts` contains only `plugins {}` and `subprojects {}` configuration.
  Module-specific config belongs in the module's own `build.gradle.kts`.
- Use `gradle.properties` for JVM args, Kotlin compiler options, and build flags:
  ```properties
  kotlin.code.style=official
  kotlin.native.cacheKind=none
  android.useAndroidX=true
  org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
  org.gradle.parallel=true
  org.gradle.caching=true
  ```
- Enable Gradle build cache and configuration cache in all CI runs.
- Never use `implementation(project(":shared"))` with a string path.
  Use the type-safe accessor: `implementation(projects.shared)`.

---

## 6. expect / actual pattern

Use `expect/actual` for capabilities that differ between platforms.
Keep the expect declarations minimal and behaviour-focused — not API-mirroring.

```kotlin
// commonMain
expect class PlatformLogger() {
    fun log(tag: String, message: String)
}

expect fun getPlatformName(): String
```

Rules:
- Every `expect` declaration must have an `actual` in every platform source set (`androidMain`, `iosMain`).
- `expect` declarations live in `commonMain/kotlin/com/arena/platform/`.
- `actual` implementations live in `androidMain/kotlin/com/arena/actual/` and `iosMain/kotlin/com/arena/actual/`.
- Never put business logic inside an `actual` implementation. The `actual` is a thin adapter.
  If platform-specific logic is complex, extract it to a platform-specific class and call it from `actual`.
- Prefer interfaces + dependency injection over `expect/actual` when the capability can be
  injected at startup (e.g., analytics, crash reporting). Use `expect/actual` only when
  the platform API must be invoked at the call site without DI.

---

## 7. Networking — Ktor Client

- One `HttpClient` instance per app. Configured in the Koin DI module, injected everywhere.
- Configure the client in `data/remote/HttpClientFactory.kt` (common):
  ```kotlin
  fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
      install(Auth) { bearer { /* token provider */ } }
      install(HttpTimeout) {
          requestTimeoutMillis = 30_000
          connectTimeoutMillis = 10_000
          socketTimeoutMillis = 30_000
      }
      install(Logging) {
          logger = object : Logger { override fun log(message: String) = Kermit.d { message } }
          level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
      }
  }
  ```
- The `HttpClientEngine` is provided per platform via DI:
  - Android: `OkHttp` engine
  - iOS: `Darwin` engine
- DTOs use `@Serializable` from `kotlinx.serialization`. Every DTO field that is optional
  on the API must be `val field: Type? = null` — never assume a field is always present.
- Map every DTO to a domain model in a `fun XxxDto.toDomain(): Xxx` extension function
  in `data/remote/mapper/`. Domain models are never exposed from the data layer as DTOs.
- All API calls are `suspend` functions. No callbacks, no RxJava.
- Handle HTTP errors in a single place: a Ktor plugin or a wrapper function that maps
  `HttpResponse` status codes to typed domain errors.

---

## 8. Local persistence — SQLDelight

- One database per app: `ArenaDatabase`. Schema defined in `.sq` files under
  `shared/src/commonMain/sqldelight/com/arena/`.
- One `.sq` file per table. Table name = file name.
- All SQL is written in `.sq` files — never as raw strings in Kotlin code.
- Expose queries via generated `Queries` interfaces. Never access the database driver directly.
- Local entities are separate from domain models. A `MembershipEntity` in the database
  is mapped to a `Membership` domain model via a mapper function — they are not the same class.
- The database is the cache. It is not the source of truth. Remote is always the source of truth.
  Never make business decisions based solely on cached data for financial or membership-status
  queries — always validate with the backend.
- Use `Flow<List<T>>` from SQLDelight for reactive queries. Do not use one-shot queries
  where a live-updating list is needed.
- Apply database migrations as versioned `.sqm` files. Never change an existing `.sq` schema
  without a corresponding migration. Follow the same discipline as Flyway on the backend.

---

## 9. Dependency injection — Koin

- Koin modules are defined per layer and per feature:
  ```
  shared/commonMain:
    networkModule.kt       ← HttpClient, API services
    databaseModule.kt      ← SQLDelight driver, database, queries
    repositoryModule.kt    ← repository implementations
    useCaseModule.kt       ← use case classes
    viewModelModule.kt     ← ViewModels
  androidMain:
    androidPlatformModule.kt ← Android-specific bindings
  iosMain:
    iosPlatformModule.kt     ← iOS-specific bindings
  ```
- Start Koin once in `ArenaApplication` (Android) and `iOSApp.swift` (iOS).
- Inject ViewModels using `koinViewModel()` in Compose (Android) and
  `getViewModel()` via a helper in iOS.
- Never use `GlobalScope`. All coroutines are launched from a ViewModel's `viewModelScope`
  or a repository's scoped coroutine scope provided by Koin.
- Never call `KoinComponent.get()` inside a composable. Inject into ViewModel, pass state down.

---

## 10. Coroutines & Flow

- All async operations are `suspend` functions or return `Flow<T>`.
- Use `StateFlow<UiState>` in ViewModels to expose UI state. Never use `LiveData`.
- Use `SharedFlow<UiEffect>` for one-time effects (navigation events, toasts, dialogs).
- Collect flows in composables using `collectAsStateWithLifecycle()` (Android) or
  `collectAsState()` (iOS/common) — never `LaunchedEffect { flow.collect {} }` for state.
- Error handling: use a sealed `Result<T>` or typed error type — never swallow exceptions
  silently. A `try/catch` with an empty catch block is a build error.
- Use `withContext(Dispatchers.IO)` for I/O operations in suspend functions.
  Never block the main thread.
- Structured concurrency: every coroutine has a defined scope and a clear cancellation path.
  No fire-and-forget coroutines in production code.

---

## 11. UI — Compose Multiplatform

### General rules

- One file per screen. Screen files live in `ui/screens/`. File name = `<Feature>Screen.kt`.
- Composables are stateless where possible. Hoist state to the ViewModel.
- The screen composable receives `uiState: UiState` and `onEvent: (UiEvent) -> Unit`.
  It does not know about the ViewModel — the ViewModel is wired in the navigation layer.
- Reusable components live in `ui/components/`. A component is reusable if it is used in
  more than one screen. Single-use UI helpers stay in the screen file.
- No hardcoded dimensions. Use `MaterialTheme.spacing` (a custom extension) or
  standard `dp` values from a spacing scale defined in `ui/theme/Spacing.kt`.
- No hardcoded colors. All colors come from `MaterialTheme.colorScheme`.
- No hardcoded strings. All user-facing strings use string resources
  (`stringResource()` on Android, equivalent on iOS via `expect/actual`).

### Theme

- Define the Arena theme in `ui/theme/ArenaTheme.kt`.
- Support **light and dark mode** on both platforms.
- Support **RTL layout** for Arabic. Use `LocalLayoutDirection` and `Arrangement.Start/End`
  instead of `Arrangement.absoluteLeft/Right`. Use `start/end` padding, not `left/right`.
- Typography, color scheme, and shapes are defined in the theme — never inline.
- Use Material Design 3 (`androidx.compose.material3`) as the component library.

### Navigation

- Use Compose Navigation Multiplatform (`org.jetbrains.androidx.navigation`).
- Define all routes as a sealed class or object in `ui/navigation/Routes.kt`.
- Deep links are declared alongside their route in the same file.
- The `NavHost` is the only place that knows about all routes. Screens know only their own route.
- Pass only primitive types or serializable IDs as navigation arguments.
  Never pass complex objects through navigation — load them from the repository by ID in the destination ViewModel.
- Back stack state is managed by the navigation component — do not replicate it in Zustand-style stores.

---

## 12. Offline support

Arena Mobile must work in offline or degraded network conditions for core read-only flows.

### Offline-capable screens

| Screen | Offline behaviour |
|---|---|
| Home / membership card | Show cached data with a "Last updated X min ago" indicator |
| GX class schedule | Show cached schedule; disable booking buttons with "Connect to book" |
| Upcoming PT sessions | Show cached sessions |
| Progress / body metrics | Show cached metrics; allow adding new entries (sync on reconnect) |
| Trainer messages | Show cached thread; allow composing (queue for send on reconnect) |
| Club info / branch hours | Show cached info |

### Not available offline

- Payment flows (never allow offline payment state)
- GX booking or cancellation
- Membership upgrade or freeze requests
- Waiver signing

### Implementation rules

- SQLDelight is the offline cache. Every network response that supports offline is written to the database immediately after fetching.
- ViewModels emit cached data first, then update with fresh data when the network call completes (stale-while-revalidate pattern).
- Outbox pattern for offline writes (new metric entry, queued message): store the pending write in a local `outbox` table with status `pending`. A background sync job processes the outbox when connectivity is restored.
- Never show stale financial data (balance, payment history) without a clear "offline" indicator. Financial data must always be fetched fresh.
- Detect connectivity using `NetworkMonitor` (expect/actual): `Flow<Boolean>` that emits `true` when online.

---

## 13. Push notifications

- Android: Firebase Cloud Messaging (FCM). Token registered on login, deregistered on logout.
- iOS: Apple Push Notification Service (APNs) via Firebase SDK. Same token lifecycle.
- Push token is sent to the backend after login and on every token refresh.
- All notification handling logic (parsing payload, routing to the correct screen) is in
  `platform/notifications/NotificationHandler.kt` per platform — not in `MainActivity` or `AppDelegate`.
- Deep link from notification: tapping a notification navigates to the relevant screen.
  Use the same deep link scheme as in-app navigation.
- Notification channels (Android 8+): define one channel per notification type:
  `membership`, `sessions`, `messages`, `payments`, `announcements`.
- Never show a notification for an event the user has already seen in-app.
  The backend marks notifications as delivered when the app is foregrounded.
- Notification permission is requested after the member completes their first login —
  never on first app launch before any context is established.

---

## 14. QR code check-in

- The QR code is generated **server-side** and returned as a signed, time-limited token
  (valid for 5 minutes). The app displays it; the club's scanner validates it with the backend.
- The QR is displayed on a dedicated full-screen view with high brightness and no screen timeout.
- The token auto-refreshes 30 seconds before expiry. A countdown timer is shown.
- If the token fails to refresh (no connectivity), show the last valid token with a clear
  "Offline — may not scan" warning. Never silently show an expired token as valid.
- The QR screen is accessible from the home screen and the bottom navigation.
- QR data format: a signed JWT containing `memberId`, `branchId`, `issuedAt`, `expiresAt`.
  The app displays; it does not generate or validate.

---

## 15. Biometric authentication

- Available on devices that support fingerprint or Face ID.
- Biometric login is an **optional convenience** — it never replaces the password.
  The full email + password flow is always available as a fallback.
- On first login after enabling biometrics, the JWT refresh token is stored in the platform
  secure enclave (Android Keystore / iOS Secure Enclave), encrypted and biometric-gated.
- Biometric prompt is shown on app resume if the session has been backgrounded for more
  than the configured idle timeout (default: 15 minutes).
- If biometric authentication fails 3 times, fall back to email + password login and clear
  the stored refresh token.
- Biometric setup is offered (not forced) after the member's first successful password login.

---

## 16. Health & fitness integration

- Android: **Health Connect** (`androidx.health.connect`)
- iOS: **HealthKit** (`HealthKit.framework`)
- Both are wired behind an `expect/actual` interface:
  ```kotlin
  // commonMain
  interface HealthPlatform {
      suspend fun requestPermissions(): Boolean
      suspend fun readSteps(startDate: LocalDate, endDate: LocalDate): List<StepRecord>
      suspend fun readWeightEntries(since: LocalDate): List<WeightRecord>
      suspend fun writeWeight(value: Double, date: LocalDate): Boolean
  }
  ```
- Health data is read and written only with explicit member permission — request permissions
  before first use with a clear explanation of what will be read and why.
- Health data is **never sent to the backend** without an explicit member action (e.g.,
  "Sync this week's steps to my progress"). Auto-sync is not implemented in v1.
- Health permissions can be revoked from the platform settings at any time. The app handles
  permission denial gracefully — disable the sync feature, do not crash.

---

## 17. Payments — mobile

- Android: **Google Pay** via the Google Pay API.
- iOS: **Apple Pay** via PassKit.
- Both are supplementary to card payment via the gateway's mobile SDK.
  The primary payment flow uses the gateway's native mobile SDK (hosted payment sheet).
- Google Pay and Apple Pay are shown as options only when the device and account support them.
  The primary "Pay by card" option is always available.
- The payment sheet (gateway SDK) is invoked as a full-screen modal. The app does not
  handle raw card data at any point.
- Payment result handling:
  - Success: dismiss sheet, show success screen, fetch updated balance and membership status.
  - Failure: dismiss sheet, show failure screen with reason and retry option.
  - Cancellation: dismiss sheet, return to the payment initiation screen silently.

---

## 18. Security

- JWTs are stored in the platform secure storage:
  - Android: `EncryptedSharedPreferences` (Jetpack Security)
  - iOS: Keychain
- Never store tokens in plain `SharedPreferences`, `UserDefaults`, or any unencrypted storage.
- The access token lives in memory only. Only the refresh token is persisted to secure storage.
- Certificate pinning is applied to the backend API host using Ktor's `CertificatePinner` plugin
  (Android) and `TrustKit` / URLSession pinning (iOS). Pinned certificates are bundled with the app.
  A pin rotation strategy must be defined before releasing v1.
- All network traffic goes over HTTPS. HTTP is not permitted even in debug builds.
- Sensitive screens (payment, QR check-in, biometric setup) must not appear in the app
  switcher thumbnail. Apply `FLAG_SECURE` (Android) and `UIApplication.ignoreSnapshotOnNextApplicationLaunch` (iOS).
- No member personal data is written to log output in release builds.
  Debug logs are stripped by ProGuard/R8 on Android and by build configuration on iOS.
- Deep links from external sources are validated before navigation. A malformed deep link
  navigates to home — it does not crash or expose internal state.

---

## 19. Naming conventions

Follows the backend Kotlin conventions from the root `CLAUDE.md`, plus mobile-specific additions:

| Construct | Convention | Example |
|---|---|---|
| Screen composables | PascalCase + `Screen` suffix | `HomeScreen`, `ClassScheduleScreen` |
| Component composables | PascalCase, no suffix | `MembershipCard`, `SessionRow` |
| ViewModels | PascalCase + `ViewModel` suffix | `HomeViewModel`, `BookingViewModel` |
| UI state classes | PascalCase + `UiState` suffix | `HomeUiState`, `BookingUiState` |
| UI event sealed classes | PascalCase + `UiEvent` suffix | `BookingUiEvent` |
| UI effect sealed classes | PascalCase + `UiEffect` suffix | `BookingUiEffect` |
| Use cases | PascalCase + `UseCase` suffix | `BookClassUseCase`, `GetMembershipUseCase` |
| Repository interfaces | PascalCase + `Repository` suffix | `MembershipRepository` |
| Repository impls | PascalCase + `RepositoryImpl` suffix | `MembershipRepositoryImpl` |
| Remote API services | PascalCase + `ApiService` suffix | `MembershipApiService` |
| DTOs | PascalCase + `Dto` suffix | `MembershipDto`, `GXClassDto` |
| SQLDelight entities | PascalCase + `Entity` suffix | `MembershipEntity` |
| Mapper functions | Extension functions on the source type | `fun MembershipDto.toDomain()` |
| expect/actual files | Same name in each source set | `PlatformLogger.kt` in all |
| DI modules | camelCase + `Module` suffix | `networkModule`, `repositoryModule` |

---

## 20. Testing

### Shared module (commonTest)

- Every use case has a unit test. Use case tests are pure Kotlin — no mocks of platform code.
- Repository implementations are tested with a fake in-memory data source, not the real Ktor client.
- Use `Turbine` for testing `Flow` emissions: assert every emitted value, do not use `first()` alone.
- ViewModels are tested in `commonTest` using `kotlinx-coroutines-test` and `TestScope`.
  Replace `Dispatchers.Main` with `UnconfinedTestDispatcher` in tests.
- Mappers (DTO → domain, entity → domain) have exhaustive unit tests asserting every field maps correctly.
- All tests follow Arrange / Act / Assert structure with a blank line separating each section.

### Android (androidUnitTest)

- Composable rendering is tested with Compose UI Test (`@Composable` + `ComposeTestRule`).
- Screenshot tests for critical screens: `HomeScreen`, `MembershipCard`, `BookingConfirmDialog`.
- Robolectric for tests requiring Android context without a device.

### Test naming convention

```kotlin
@Test
fun `given active membership, when home screen loads, then membership card shows green status`() { }
```

Pattern: `given <precondition>, when <action>, then <expected outcome>`

### What must be tested

- Every use case: happy path + all error paths.
- Every ViewModel: state transitions for each `UiEvent`.
- Every mapper: all fields, null handling, edge values.
- Offline behaviour: repository returns cached data when network throws `IOException`.
- QR token expiry: countdown reaches zero, refresh is triggered, new token displayed.
- Biometric fallback: after 3 failures, password login is shown and refresh token is cleared.
- Payment flow: success, failure, and cancellation result in correct screen transitions.

---

## 21. Build variants & flavors

### Android build types

| Build type | Minification | Logging | API endpoint | Debuggable |
|---|---|---|---|---|
| `debug` | Off | Full | `dev` | Yes |
| `staging` | On | Errors only | `staging` | No |
| `release` | On (R8) | Off | `production` | No |

### iOS configurations

| Configuration | Logging | API endpoint |
|---|---|---|
| Debug | Full | `dev` |
| Staging | Errors only | `staging` |
| Release | Off | `production` |

Rules:
- `BuildConfig` (Android) and a generated `Config.kt` (common, via buildkonfig plugin) hold
  the API base URL and environment flags. Never hardcode URLs in source.
- The `staging` build is what QA and the client test. `release` is only built for store submission.
- ProGuard / R8 rules must be maintained and committed. The `release` build must be run locally
  and verified before every store submission — never submit a release build that has not been
  tested locally.

---

## 22. CI/CD — mobile

Mobile CI runs on every push to any branch. All steps must be green before merging.

```
1. Shared module tests     ← ./gradlew :shared:test (fast, no device needed)
2. Android unit tests      ← ./gradlew :androidApp:testDebugUnitTest
3. Lint                    ← ./gradlew lint (Android) + ktlint (shared)
4. Shared compile check    ← compile for all KMP targets (Android + iOS arm64 + sim)
5. iOS build check         ← xcodebuild (macOS runner only, on develop/main)
6. Android staging build   ← ./gradlew :androidApp:assembleStagingRelease (on develop/main)
7. Distribute to testers   ← Firebase App Distribution (on develop)
8. Store submission        ← manual trigger only, from main, after QA sign-off
```

- Steps 1–4 run on every push (Linux runner, fast).
- Steps 5–8 run only on `develop` and `main` (macOS runner for iOS, costs more).
- The submodule pointer in the monorepo is only bumped after step 6 passes on `develop`.
- App version (`versionCode` / `versionName` on Android, `CFBundleVersion` on iOS) is
  auto-incremented by CI using the build number. Never manually edit version codes.

---

## 23. Localization — mobile

- String resources: Android uses `res/values/strings.xml` and `res/values-ar/strings.xml`.
  iOS uses `.strings` files. A shared source generation approach (via a script or plugin)
  keeps both in sync from a single `i18n/` source of truth in the shared module.
- Default locale: **Arabic** (matching `web-arena` and `web-pulse`).
- RTL is automatic on both platforms when the device locale is Arabic.
  Use `Arrangement.Start/End` and `start/end` padding everywhere — never `left/right`.
- Numbers: use `NumberFormat` from `kotlinx-datetime` or platform formatters.
  Never format numbers manually.
- Dates: show both Hijri and Gregorian dates on all date displays and pickers.
  Use `java.time` (Android) and `Foundation` (iOS) via `expect/actual` for calendar conversion.
- Monetary amounts: always SAR. Arabic locale: ر.س symbol. English locale: "SAR".

---

## General principles

- **Shared by default.** Before writing platform-specific code, ask: can this be in `commonMain`? If yes, it goes there.
- **Domain knows nothing.** If the domain layer has a single platform import, the architecture is broken.
- **One source of truth per data type.** Domain models in `domain/model/`. DTOs in `data/remote/`. Entities in `data/local/`. Never mix them.
- **Offline is a feature, not an afterthought.** Every screen decision includes: what does this show with no network?
- **Security is not optional.** Token storage, certificate pinning, and secure screen flags are applied from day one — not added before release.
