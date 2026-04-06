# PLAN.md — web-arena (Member Self-Service Portal)

## Status
Ready for implementation

## Branch
feat/web-arena

## Goal
Build the member-facing self-service portal at web-arena (port 5176). Members
log in with phone number + OTP, choose their preferred language on first login,
then access their membership status, GX class bookings, PT session schedule,
and invoice history. Each feature is individually toggled per club via
ClubPortalSettings — the club decides what members can see and do.

## Context
- `Member`, `Membership`, `MembershipPlan`, `GXClassInstance`, `GXBooking`,
  `PTSession`, `PTPackage`, `Invoice`, `Club`, `Organization` all exist.
- `User` entity exists — members already have a linked `User` record
  (created by staff during member registration). This plan reuses that
  User for auth, adding phone-based OTP login alongside the existing
  email/password flow.
- Redis 7 is already running — used here for OTP TTL storage.
- web-pulse already exists as the reference implementation for app structure,
  routing, i18n, and Tailwind usage. web-arena follows the same patterns
  but is a fully separate Vite app in `web-arena/`.
- JWT `scope = "member"` already defined in the auth system.
- `ddl-auto: create-drop` in dev — no Flyway migration needed for dev.
  New tables: `club_portal_settings`, `member_otps` (V10).

---

## Scope — what this plan covers

### Backend
- [ ] `ClubPortalSettings.kt` + `ClubPortalSettingsRepository.kt`
- [ ] `ClubPortalSettingsService.kt` — CRUD for portal feature flags
- [ ] `MemberOtp.kt` + `MemberOtpRepository.kt` — OTP request tracking
- [ ] `MemberAuthService.kt` — phone OTP request + verify + JWT issue
- [ ] `MemberArenaController.kt` — member-facing auth endpoints
- [ ] `ClubPortalSettingsPulseController.kt` — staff manage portal settings
- [ ] `MemberProfileArenaController.kt` — profile + membership view
- [ ] `GxArenaController.kt` — GX schedule view + booking
- [ ] `PtArenaController.kt` — PT sessions view
- [ ] `InvoiceArenaController.kt` — invoice list + detail
- [ ] DTOs: request/response shapes for all arena endpoints
- [ ] Update `DevDataLoader.kt` — seed ClubPortalSettings + member phone
- [ ] Unit tests for `MemberAuthService`, `ClubPortalSettingsService`
- [ ] Integration tests for all arena controllers
- [ ] Next Flyway migration: V10

### Frontend (web-arena — NEW app)
- [ ] Bootstrap `web-arena/` Vite + React 18 + TypeScript app
  (copy structure from web-pulse: TanStack Router, TanStack Query,
  Zustand, React Hook Form, Zod, i18next, Tailwind)
- [ ] Phone OTP login flow (2 screens: phone entry → OTP entry)
- [ ] Language selection screen (shown once after first login)
- [ ] App shell: bottom nav bar (mobile-first), header with name + avatar
- [ ] Home / dashboard screen
- [ ] Membership screen — current plan, expiry, freeze status
- [ ] GX schedule screen — weekly view, book/cancel
- [ ] PT sessions screen — upcoming + past sessions
- [ ] Invoices screen — list + detail with QR code
- [ ] Profile screen — view/edit name, phone, language preference
- [ ] i18n: Arabic (default after language choice) + English

---

## Out of scope — do not implement in this plan
- Online payment / membership renewal (no payment gateway — club flag
  `onlinePaymentEnabled` stored but always false until Plan 21)
- Push notifications (no notification system yet)
- File upload / profile photo (no file upload in this project yet)
- Member registration self-signup (staff registers members — not self-service)
- Password reset or email-based login for members (phone OTP only)
- GX waitlist (separate plan)
- PT package purchase online (separate plan)

---

## Decisions already made

- **Phone OTP, no SMS gateway yet**: OTP is a 6-digit code, SHA-256 hashed
  and stored in `member_otps` table with a 10-minute expiry. In dev, the OTP
  is logged to the console (`log.info("DEV OTP for {}: {}", phone, otp)`).
  A `// TODO: replace with SMS gateway (Twilio/Unifonic)` comment marks the
  send point. Max 3 active OTP requests per phone per 10 minutes (rate limit).

- **OTP stored in DB, not Redis**: Redis is used for RBAC permission caching.
  OTPs are stored in a `member_otps` table with `expires_at` — simpler,
  auditable, and consistent with the project's DB-first approach.

- **Language stored on Member**: `preferredLanguage VARCHAR(10)` added to
  `members` table (`"ar"` or `"en"`). Language selection screen shown when
  `preferredLanguage` is null. After selection, `PATCH /api/v1/arena/profile`
  saves it. Frontend reads it from `GET /api/v1/arena/me` on app init.

- **ClubPortalSettings per club**: one row per club, created with defaults
  when a club is created (or lazily on first access). Feature flags:
  `gxBookingEnabled`, `ptViewEnabled`, `invoiceViewEnabled`,
  `onlinePaymentEnabled` (always false for now). Backend enforces these
  flags — if `gxBookingEnabled = false`, the GX endpoints return 403.
  Frontend hides the nav item entirely.

- **Arena controllers are separate from Pulse controllers**: naming convention
  `[Domain]ArenaController` for all member-facing endpoints. They use
  `@PreAuthorize` with `scope = 'member'` JWT claim check, not the staff
  RBAC permission system.

- **Member scope JWT**: the existing JWT for members has
  `scope = "member"`, `memberId`, `clubId`, `organizationId`, `branchId`.
  Arena controllers extract tenant context from these claims directly.

- **Mobile-first UI**: web-arena is designed for phones. Bottom navigation
  bar (5 items max), large touch targets, full-width cards. Uses the same
  Tailwind + logical CSS properties as web-pulse.

- **web-arena is a fresh Vite app**: created from scratch in `web-arena/`
  with its own `package.json`, `package-lock.json`, and `node_modules`.
  Do NOT run npm from the project root.

---

## Entity design

### ClubPortalSettings

Fields beyond standard AuditEntity columns:

```
club_id                  BIGINT NOT NULL UNIQUE    FK → clubs(id)
gx_booking_enabled       BOOLEAN NOT NULL DEFAULT true
pt_view_enabled          BOOLEAN NOT NULL DEFAULT true
invoice_view_enabled     BOOLEAN NOT NULL DEFAULT true
online_payment_enabled   BOOLEAN NOT NULL DEFAULT false
portal_message           VARCHAR(500)              nullable
                         (optional welcome message shown on member home)
```

### MemberOtp

Fields beyond standard AuditEntity columns:

```
phone                VARCHAR(20) NOT NULL
otp_hash             VARCHAR(255) NOT NULL   (SHA-256 of the 6-digit code)
expires_at           TIMESTAMPTZ NOT NULL    (NOW() + 10 minutes)
used                 BOOLEAN NOT NULL DEFAULT false
member_id            BIGINT                  FK → members(id), nullable
                     (null until phone is matched to a member on verify)
```

No soft delete — OTPs are short-lived and cleaned up by expiry.

### Member (modify existing)

Add one field:
```
preferred_language   VARCHAR(10)   nullable   ('ar' or 'en')
```

---

## API endpoints

### MemberArenaController — `/api/v1/arena/auth`

```
POST   /api/v1/arena/auth/otp/request    request OTP for phone number
POST   /api/v1/arena/auth/otp/verify     verify OTP → returns JWT
POST   /api/v1/arena/auth/logout         invalidate refresh token
```

No `@PreAuthorize` on request/verify — they are public endpoints.
Logout requires valid member JWT.

### MemberProfileArenaController — `/api/v1/arena`

```
GET    /api/v1/arena/me                  member profile + membership summary
PATCH  /api/v1/arena/profile             update name fields + preferredLanguage
GET    /api/v1/arena/membership          active membership detail
GET    /api/v1/arena/portal-settings     club portal feature flags (for nav)
```

### GxArenaController — `/api/v1/arena/gx`

```
GET    /api/v1/arena/gx/schedule         upcoming GX class instances (7 days)
POST   /api/v1/arena/gx/{instanceId}/book    book a spot
DELETE /api/v1/arena/gx/{instanceId}/book    cancel booking
GET    /api/v1/arena/gx/bookings         member's booking history
```

Gated by `ClubPortalSettings.gxBookingEnabled`.

### PtArenaController — `/api/v1/arena/pt`

```
GET    /api/v1/arena/pt/sessions         upcoming + past PT sessions
GET    /api/v1/arena/pt/packages         member's PT packages with session counts
```

Gated by `ClubPortalSettings.ptViewEnabled`.

### InvoiceArenaController — `/api/v1/arena/invoices`

```
GET    /api/v1/arena/invoices            invoice list (paginated)
GET    /api/v1/arena/invoices/{id}       invoice detail with QR code
```

Gated by `ClubPortalSettings.invoiceViewEnabled`.

### ClubPortalSettingsPulseController — `/api/v1/portal-settings`

```
GET    /api/v1/portal-settings           get settings for club
PATCH  /api/v1/portal-settings           update feature flags + portal message
```

Required permission: `portal-settings:update` (Owner, Branch Manager).

---

## Request / Response shapes

### OtpRequestRequest
```json
{ "phone": "+966501234567 (required)" }
```

### OtpVerifyRequest
```json
{
  "phone": "+966501234567 (required)",
  "otp": "123456 (required, 6 digits)"
}
```

### OtpVerifyResponse
```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "member": {
    "id": "uuid",
    "firstName": "string",
    "lastName": "string",
    "preferredLanguage": "ar | en | null"
  }
}
```

### MemberMeResponse
```json
{
  "id": "uuid",
  "firstName": "string",
  "lastName": "string",
  "firstNameAr": "string | null",
  "lastNameAr": "string | null",
  "phone": "string",
  "email": "string | null",
  "preferredLanguage": "ar | en | null",
  "club": { "id": "uuid", "name": "string", "nameAr": "string" },
  "membership": {
    "planName": "string",
    "planNameAr": "string",
    "status": "active | expired | frozen",
    "startDate": "yyyy-MM-dd",
    "expiresAt": "yyyy-MM-dd",
    "daysRemaining": 14
  }
}
```

### UpdateProfileRequest
```json
{
  "firstNameAr": "string (optional)",
  "lastNameAr": "string (optional)",
  "preferredLanguage": "ar | en (optional)"
}
```

### PortalSettingsResponse
```json
{
  "gxBookingEnabled": true,
  "ptViewEnabled": true,
  "invoiceViewEnabled": true,
  "onlinePaymentEnabled": false,
  "portalMessage": "string | null"
}
```

### GxScheduleItemResponse
```json
{
  "id": "uuid",
  "classType": { "name": "string", "nameAr": "string", "color": "string" },
  "instructorName": "string",
  "startTime": "ISO 8601",
  "endTime": "ISO 8601",
  "capacity": 20,
  "bookedCount": 14,
  "spotsRemaining": 6,
  "isBooked": true
}
```

### PtSessionArenaResponse
```json
{
  "id": "uuid",
  "scheduledAt": "ISO 8601",
  "status": "scheduled | attended | missed | cancelled",
  "trainerName": "string",
  "packageName": "string",
  "sessionsUsed": 3,
  "sessionsTotal": 10
}
```

---

## Business rules — enforce in service layer

1. **Phone must match a member in the club** — `OtpRequestRequest.phone`
   must match exactly one `Member` record. Return 200 regardless (never
   reveal whether phone exists — security). Log OTP to console in dev.

2. **OTP rate limit** — max 3 unused, unexpired OTPs per phone in the last
   10 minutes. Return 429 "Too many OTP requests. Please wait." if exceeded.

3. **OTP expires in 10 minutes** — `verifyOtp` checks `expires_at > NOW()`
   and `used = false`. Return 401 "OTP expired or invalid" if check fails.
   Do not distinguish expired vs wrong code in error message.

4. **OTP single-use** — mark `used = true` immediately on successful verify.
   Return 401 if already used.

5. **Portal feature gate** — before executing any GX, PT, or invoice
   operation, `ClubPortalSettingsService.getSettings(clubId)` is checked.
   Return 403 "This feature is not enabled for your club" if the relevant
   flag is false.

6. **GX booking: capacity check** — `bookedCount` must be < `capacity`
   before creating a `GXBooking`. Return 422 "Class is fully booked".

7. **GX booking: no duplicate** — a member cannot book the same
   `GXClassInstance` twice. Return 409 "Already booked".

8. **GX booking: future only** — cannot book a class whose `startTime` is
   in the past. Return 422 "Cannot book a past class".

9. **GX cancel: own booking only** — member can only cancel their own
   `GXBooking`. Return 403 if `booking.memberId ≠ JWT memberId`.

10. **Tenant isolation** — all arena queries filter by `memberId` and
    `clubId` from JWT claims. A member can never see another member's data.

11. **preferredLanguage valid values** — only `"ar"` or `"en"` accepted.
    Return 422 for any other value.

---

## Seed data updates

Add to `DevDataLoader.kt`:

```
ClubPortalSettings for Elixir Gym:
  gxBookingEnabled = true
  ptViewEnabled = true
  invoiceViewEnabled = true
  onlinePaymentEnabled = false
  portalMessage = "Welcome to Elixir Gym! 💪" / "مرحباً بك في نادي إكسير!"

Update Ahmed Al-Rashidi (member@elixir.com) member record:
  phone = "+966501234099"   (so OTP login works in dev)
  preferredLanguage = null  (so language selection screen appears on first login)

Add portal-settings:update permission to Owner and Branch Manager seed roles.
```

---

## Frontend additions (web-arena)

### Bootstrap web-arena app
New Vite app at `web-arena/` with same stack as web-pulse:
React 18, TypeScript strict, TanStack Router (file-based), TanStack Query,
Zustand (auth state only), React Hook Form + Zod, Tailwind CSS, i18next.
Port: 5176. JWT scope check: rejects non-`member` tokens with
"This app is for gym members only".

### Phone OTP Login — /auth/login
Step 1: Phone number input (Saudi format, +966 prefix selector).
"Send Code" button → `POST /api/v1/arena/auth/otp/request`.
Step 2: 6-digit OTP input (auto-advance between boxes).
60-second countdown before "Resend Code" is enabled.
On verify success → save JWT in memory → check `preferredLanguage`.

### Language Selection — /auth/language
Full-screen language choice shown when `preferredLanguage` is null after login.
Two large cards: Arabic (العربية) and English. Tapping one calls
`PATCH /api/v1/arena/profile` then redirects to home.
Cannot be skipped — auth guard redirects here if language not set.

### Home / Dashboard — /
Member's name greeting, membership status card (plan name, days remaining,
expiry date, status badge). Quick action buttons for enabled features
(Book a Class, My PT Sessions, My Invoices). `portalMessage` banner if set.

### Membership — /membership
Full membership detail card: plan name, start date, expiry date, status,
freeze periods if any. Renewal reminder banner if < 7 days remaining.

### GX Schedule — /gx
Weekly view (today + 6 days). Each class card: type name, time, instructor,
spots remaining. Booked classes shown with green checkmark.
Tap to book (if spots available) or cancel (if already booked).
Confirmation bottom sheet before booking/cancel.

### PT Sessions — /pt
Two tabs: Upcoming / Past. Each session card: date/time, trainer name,
package name, status badge (scheduled/attended/missed).
Package summary at top: sessions used / total.

### Invoices — /invoices
List of invoices: invoice number, date, amount, status.
Tap → invoice detail with all ZATCA fields and QR code image.
QR rendered with `qrcode.react` (already used in web-pulse — add to web-arena too).

### Profile — /profile
Shows member name, phone, language preference.
Edit button → inline form for Arabic name fields + language toggle.
Save calls `PATCH /api/v1/arena/profile`.

### App shell
Bottom navigation bar: Home, GX, PT, Invoices, Profile.
Items hidden via `portalSettings` flags fetched on app init.
Header: club name/logo (left), member name (right).

### i18n (key sample)
```json
{
  "auth.phone.title": "Enter your phone number",
  "auth.otp.title": "Enter verification code",
  "auth.otp.resend": "Resend code",
  "auth.language.title": "Choose your language",
  "home.greeting": "Welcome back, {{name}}",
  "membership.expires_in": "Expires in {{days}} days",
  "gx.book": "Book",
  "gx.cancel_booking": "Cancel Booking",
  "gx.full": "Class Full",
  "pt.upcoming": "Upcoming",
  "pt.past": "Past",
  "invoices.title": "My Invoices",
  "profile.language": "Language"
}
```

---

## Files to generate

### Backend — new files
```
portal/
  ClubPortalSettings.kt
  ClubPortalSettingsRepository.kt
  ClubPortalSettingsService.kt
  ClubPortalSettingsPulseController.kt
  dto/
    ClubPortalSettingsResponse.kt
    UpdatePortalSettingsRequest.kt

auth/
  MemberOtp.kt
  MemberOtpRepository.kt
  MemberAuthService.kt
  MemberArenaController.kt
  dto/
    OtpRequestRequest.kt
    OtpVerifyRequest.kt
    OtpVerifyResponse.kt

arena/
  MemberProfileArenaController.kt
  GxArenaController.kt
  PtArenaController.kt
  InvoiceArenaController.kt
  dto/
    MemberMeResponse.kt
    UpdateProfileRequest.kt
    GxScheduleItemResponse.kt
    GxBookingResponse.kt
    PtSessionArenaResponse.kt
    PtPackageArenaResponse.kt
    InvoiceArenaResponse.kt
    InvoiceArenaDetailResponse.kt
```

### Backend — modified files
```
member/Member.kt               add preferredLanguage field
config/DevDataLoader.kt        add ClubPortalSettings + Ahmed's phone + permission
```

### Frontend — new app
```
web-arena/
  package.json                 (React 18, TypeScript, Vite, TanStack Router/Query,
                                Zustand, RHF, Zod, i18next, Tailwind, qrcode.react)
  vite.config.ts               (port 5176)
  tsconfig.json
  index.html
  src/
    main.tsx
    router.tsx                 (TanStack Router file-based)
    store/authStore.ts         (Zustand — JWT in memory, member profile)
    lib/
      api.ts                   (axios instance, arena base URL)
      permissions.ts           (portal settings helper: isFeatureEnabled)
      formatCurrency.ts        (copy from web-pulse)
    types/
      domain.ts                (arena-specific types)
    i18n/
      index.ts
      en.json
      ar.json
    api/
      auth.ts
      profile.ts
      gx.ts
      pt.ts
      invoices.ts
    routes/
      __root.tsx               (auth guard + language guard + portal settings fetch)
      auth/
        login.tsx              (phone OTP step 1 + step 2)
        language.tsx           (language selection)
      index.tsx                (home/dashboard)
      membership.tsx
      gx.tsx
      pt.tsx
      invoices/
        index.tsx
        $invoiceId.tsx
      profile.tsx
    components/
      shell/
        BottomNav.tsx
        AppHeader.tsx
      auth/
        PhoneInput.tsx
        OtpInput.tsx           (6-box auto-advance)
      membership/
        MembershipCard.tsx
      gx/
        GxClassCard.tsx
        BookingConfirmSheet.tsx
      pt/
        PtSessionCard.tsx
      invoices/
        InvoiceListItem.tsx
        InvoiceQrCode.tsx      (uses qrcode.react)
      common/
        StatusBadge.tsx
        EmptyState.tsx
        LoadingSpinner.tsx
```

---

## Implementation order

```
Step 1 — ClubPortalSettings entity + service + Pulse controller
  ClubPortalSettings.kt, ClubPortalSettingsRepository.kt
  ClubPortalSettingsService.kt — get (lazy create with defaults), update
  ClubPortalSettingsPulseController.kt — GET + PATCH
  DTOs: ClubPortalSettingsResponse, UpdatePortalSettingsRequest
  Verify: ./gradlew build -x test

Step 2 — MemberOtp entity + MemberAuthService
  MemberOtp.kt, MemberOtpRepository.kt
  Add preferredLanguage to Member.kt
  MemberAuthService.kt:
    requestOtp: find member by phone, enforce rate limit (rule 2),
      generate 6-digit OTP, SHA-256 hash, store in member_otps,
      log to console in dev (TODO: SMS gateway comment)
    verifyOtp: find unexpired unused OTP hash match (rules 3, 4),
      mark used, issue JWT with scope=member claims
  MemberArenaController.kt — POST /otp/request, POST /otp/verify, POST /logout
  DTOs: OtpRequestRequest, OtpVerifyRequest, OtpVerifyResponse
  Verify: ./gradlew build -x test

Step 3 — MemberProfileArenaController
  GET /arena/me — member profile + active membership summary
  PATCH /arena/profile — update name fields + preferredLanguage (rule 11)
  GET /arena/membership — full membership detail
  GET /arena/portal-settings — club feature flags
  DTOs: MemberMeResponse, UpdateProfileRequest, PortalSettingsResponse
  All queries tenant-scoped via JWT memberId/clubId (rule 10)
  Verify: ./gradlew build -x test

Step 4 — GxArenaController
  GET /arena/gx/schedule — upcoming 7 days, isBooked per instance
  POST /arena/gx/{id}/book — rules 5 (portal gate), 6 (capacity),
    7 (no duplicate), 8 (future only)
  DELETE /arena/gx/{id}/book — rule 9 (own booking only)
  GET /arena/gx/bookings — member's booking history
  DTOs: GxScheduleItemResponse, GxBookingResponse
  Verify: ./gradlew build -x test

Step 5 — PtArenaController + InvoiceArenaController
  GET /arena/pt/sessions — upcoming + past, ordered by scheduledAt
  GET /arena/pt/packages — member's packages with session counts
  GET /arena/invoices — paginated invoice list
  GET /arena/invoices/{id} — invoice detail with zatcaQrCode
  All gated by portal settings (rule 5)
  Verify: ./gradlew build -x test

Step 6 — Seed data + permissions
  Update DevDataLoader.kt:
    ClubPortalSettings for Elixir Gym
    Ahmed's phone = "+966501234099"
    portal-settings:update permission to Owner + Branch Manager
  Verify: ./gradlew bootRun --args='--spring.profiles.active=dev'
  Manual verify: POST /api/v1/arena/auth/otp/request {"phone":"+966501234099"}
    → console shows OTP
  Manual verify: POST /api/v1/arena/auth/otp/verify → returns JWT
  Manual verify: GET /api/v1/arena/me with member JWT → returns Ahmed's profile

Step 7 — Backend tests
  MemberAuthServiceTest.kt:
    - requestOtp: happy path, unknown phone returns 200 (no info leak),
      rate limit exceeded (429)
    - verifyOtp: correct OTP returns JWT, wrong code (401),
      expired OTP (401), already used (401)
  ClubPortalSettingsServiceTest.kt:
    - lazy create with defaults, update flags, portal gate enforcement
  GxArenaControllerTest.kt:
    - book class: happy path, full class (422), duplicate booking (409),
      past class (422), feature disabled (403)
    - cancel: own booking, other member's booking (403)
  MemberProfileArenaControllerTest.kt:
    - me endpoint, update preferredLanguage, invalid language value (422)
    - tenant isolation: member cannot see another member's data
  Verify: ./gradlew test --no-daemon

Step 8 — Backend final checks
  ./gradlew ktlintFormat --no-daemon
  ./gradlew ktlintCheck --no-daemon
  ./gradlew build --no-daemon

Step 9 — Bootstrap web-arena app
  Create web-arena/ with package.json (port 5176)
  Install: react, react-dom, typescript, vite, @tanstack/react-router,
    @tanstack/react-query, zustand, react-hook-form, zod, i18next,
    react-i18next, tailwindcss, qrcode.react
  Setup: vite.config.ts, tsconfig.json, tailwind.config.js, index.html,
    src/main.tsx, src/router.tsx
  src/store/authStore.ts — JWT in memory, member profile, portalSettings
  src/lib/api.ts — axios instance pointing to backend at port 8080
  Verify: cd web-arena && npm run dev → blank app loads at localhost:5176

Step 10 — Auth flow
  src/api/auth.ts — requestOtp, verifyOtp, logout
  src/routes/auth/login.tsx:
    Step 1: PhoneInput.tsx with +966 prefix, submit → requestOtp
    Step 2: OtpInput.tsx (6 auto-advance boxes), 60s countdown,
      resend button, submit → verifyOtp → save JWT → redirect
  src/routes/auth/language.tsx:
    Two large language cards, tap → PATCH /arena/profile → redirect home
  src/routes/__root.tsx:
    Auth guard: no JWT → /auth/login
    Language guard: JWT + preferredLanguage null → /auth/language
    Fetch portal settings on mount → store in Zustand
  Verify: npm run dev → login with Ahmed's phone, get OTP from console,
    enter it, land on language selection, pick Arabic

Step 11 — App shell + home screen
  src/components/shell/AppHeader.tsx — club name, member name
  src/components/shell/BottomNav.tsx — 5 items, hidden by portalSettings
  src/routes/index.tsx — greeting, MembershipCard summary,
    quick action buttons, portalMessage banner
  Verify: npm run dev → home screen with Ahmed's membership shown

Step 12 — Membership screen
  src/routes/membership.tsx
  MembershipCard.tsx — plan name, dates, status badge, freeze info
  Renewal reminder banner if daysRemaining < 7
  Verify: npm run dev → /membership shows Ahmed's Basic Monthly plan

Step 13 — GX screen
  src/api/gx.ts
  src/routes/gx.tsx — weekly class list
  GxClassCard.tsx — type color, time, instructor, spots, booked state
  BookingConfirmSheet.tsx — bottom sheet: "Book this class?" confirm/cancel
  Verify: npm run dev → book a class, card shows green checkmark,
    cancel it, spots count updates

Step 14 — PT screen
  src/api/pt.ts
  src/routes/pt.tsx — Upcoming/Past tabs
  PtSessionCard.tsx — date, trainer, status badge
  Package summary bar at top
  Verify: npm run dev → Ahmed's PT sessions shown in upcoming tab

Step 15 — Invoices screens
  src/api/invoices.ts
  src/routes/invoices/index.tsx — paginated list
  src/routes/invoices/$invoiceId.tsx — detail with QR code
  InvoiceListItem.tsx, InvoiceQrCode.tsx (qrcode.react)
  Verify: npm run dev → Ahmed's invoice shown, tap to see QR code

Step 16 — Profile screen
  src/routes/profile.tsx
  Edit name fields (Arabic), language toggle
  Verify: npm run dev → change language → app switches to English/Arabic

Step 17 — Frontend tests
  PhoneInput.test.tsx — validates Saudi phone format
  OtpInput.test.tsx — auto-advance between boxes
  MembershipCard.test.tsx — shows correct status badge
  GxClassCard.test.tsx — booked/available/full states
  BottomNav.test.tsx — hides items when portal feature disabled
  Verify: npm test

Step 18 — Frontend final checks
  npm run typecheck
  npm run lint
  npm run build
```

---

## Acceptance criteria

### Backend
- [ ] `POST /otp/request` with known phone returns 200 and logs OTP to console
- [ ] `POST /otp/request` with unknown phone also returns 200 (no info leak)
- [ ] More than 3 OTP requests in 10 minutes returns 429
- [ ] Correct OTP returns JWT with `scope = "member"`
- [ ] Expired OTP returns 401
- [ ] Already-used OTP returns 401
- [ ] `GET /arena/me` returns member profile + membership summary
- [ ] `preferredLanguage` saved on `PATCH /arena/profile`
- [ ] Invalid `preferredLanguage` value returns 422
- [ ] GX booking on full class returns 422
- [ ] Duplicate GX booking returns 409
- [ ] GX booking on past class returns 422
- [ ] Cancelling another member's booking returns 403
- [ ] Disabled portal feature returns 403
- [ ] Member cannot access another member's invoices or PT sessions
- [ ] All 296+ existing tests still pass

### Frontend (web-arena)
- [ ] Phone OTP login flow works end-to-end with seeded Ahmed account
- [ ] Language selection screen appears on first login (null preferredLanguage)
- [ ] Language selection persists after app refresh (re-login)
- [ ] Bottom nav hides GX/PT/Invoices links when portal flags are false
- [ ] Home screen shows membership card with correct days remaining
- [ ] GX booking updates card state immediately
- [ ] PT sessions show correct upcoming/past split
- [ ] Invoice QR code is visible and scannable
- [ ] Arabic RTL layout correct throughout
- [ ] npm run typecheck, lint, test, build all pass

---

## RBAC matrix rows added by this plan

| Permission | Owner | Branch Manager | Receptionist | Sales Agent | PT Trainer | GX Instructor | Member |
|---|---|---|---|---|---|---|---|
| portal-settings:update | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

Note: Arena endpoints use JWT scope check (`scope = "member"`), not the
staff RBAC permission system. No additional permission codes needed for
member-facing endpoints.

---

## Definition of done

- All acceptance criteria checked
- All 11 business rules covered by unit tests
- OTP flow tested end-to-end with console OTP in dev
- Tenant isolation: member A cannot see member B's data
- Portal settings gate tested: disabled feature returns 403 on backend
  AND nav item hidden on frontend
- web-arena runs independently on port 5176 (`cd web-arena && npm run dev`)
- All CI checks pass on PR
- No TODOs without a linked issue (SMS gateway TODO is intentional — keep it)
- PLAN.md deleted before merging
- PR title: `feat(arena): implement member self-service portal with phone OTP auth`
- Target branch: `develop`
