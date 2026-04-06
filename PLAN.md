# PLAN.md — web-coach (Trainer Dashboard)

## Status
Ready for implementation

## Branch
feat/web-coach

## Goal
Build the trainer-facing dashboard at web-coach (port 5175). Trainers log in
with email + password (existing auth, scope = "trainer"), then see their daily
schedule, manage PT sessions (view, mark attendance), manage GX classes they
instruct (view bookings, mark attendance), and view their own profile and
certifications. Two trainer types exist: PT trainers and GX instructors — a
trainer can be both. The app surfaces only what is relevant to their
trainerTypes claim in the JWT.

## Context
- `Trainer`, `TrainerBranchAssignment`, `TrainerCertification` already exist.
- `PTSession`, `PTPackage`, `PTPackageCatalog` already exist.
- `GXClassType`, `GXClassInstance`, `GXBooking`, `GXAttendance` already exist.
- JWT `scope = "trainer"` is already defined. Claims include `trainerId`,
  `trainerTypes` (array: `["pt"]`, `["gx"]`, or `["pt","gx"]`), `clubId`,
  `organizationId`, `branchIds`.
- web-pulse is the reference implementation for app structure.
- web-arena was just built — web-coach follows the same bootstrap pattern
  but uses email/password login (not phone OTP) and a sidebar layout
  (tablet/desktop-friendly) instead of a bottom nav.
- `ddl-auto: create-drop` in dev — no Flyway migration needed.
- No new entities required — this plan is pure read + targeted writes
  (mark attendance only).

---

## Scope — what this plan covers

### Backend
- [ ] `TrainerAuthCoachController.kt` — login reusing existing auth, scope check
- [ ] `TrainerProfileCoachController.kt` — trainer profile + certifications
- [ ] `ScheduleCoachController.kt` — today's + upcoming schedule (PT + GX combined)
- [ ] `PtCoachController.kt` — PT sessions for this trainer, mark attendance
- [ ] `GxCoachController.kt` — GX class instances for this trainer, bookings list, mark attendance
- [ ] `CoachAuthHelper.kt` — JWT scope/claim extraction for coach endpoints
- [ ] DTOs for all coach endpoints
- [ ] Unit tests: `PtCoachServiceTest`, `GxCoachServiceTest`
- [ ] Integration tests: `ScheduleCoachControllerTest`, `PtCoachControllerTest`, `GxCoachControllerTest`

### Frontend (web-coach — NEW app)
- [ ] Bootstrap `web-coach/` Vite + React 18 + TypeScript app
  (same stack as web-pulse and web-arena: TanStack Router, TanStack Query,
  Zustand, React Hook Form, Zod, i18next, Tailwind — port 5175)
- [ ] Email + password login (scope check: rejects non-`trainer` tokens)
- [ ] App shell: collapsible sidebar (desktop) + top header, mobile-responsive
- [ ] Today's schedule screen — combined PT + GX timeline view
- [ ] PT sessions screen — list with filters (upcoming/past), mark attendance
- [ ] GX classes screen — weekly grid, class detail with bookings list, mark attendance
- [ ] Profile screen — trainer info, certifications list
- [ ] i18n: Arabic (default) + English

---

## Out of scope — do not implement in this plan
- Creating or editing PT sessions (staff-only in web-pulse)
- Creating or editing GX class instances (staff-only in web-pulse)
- Viewing member profiles or payment data (trainer has no access)
- Trainer availability / scheduling management (future plan)
- Push notifications
- File upload / profile photo
- PT package management (staff-only)

---

## Decisions already made

- **Email + password login, not OTP**: Trainers are internal users set up by
  staff — they already have email/password credentials created in web-pulse.
  Reuses the existing `POST /api/v1/auth/login` endpoint. Coach app just
  checks that the returned JWT has `scope = "trainer"` and rejects anything
  else with "This app is for trainers only".

- **No new backend auth endpoint**: `/api/v1/auth/login` already issues
  trainer-scoped JWTs. The coach app uses it directly, same as web-pulse uses
  it for club scope.

- **Coach controllers are separate from Pulse controllers**: naming convention
  `[Domain]CoachController`. They use `CoachAuthHelper` to extract `trainerId`
  and `trainerTypes` from JWT claims and enforce that trainers can only see
  their own data.

- **trainerTypes gates UI sections**: if `trainerTypes = ["pt"]`, GX nav item
  is hidden. If `trainerTypes = ["gx"]`, PT nav item is hidden. If both, both
  shown. Enforced on frontend via Zustand auth store + on backend via 403 if
  trainer type doesn't match the resource.

- **Mark attendance is the only write operation**: trainers can mark a
  `PTSession` as `attended` or `missed`, and mark individual `GXBooking`
  records as attended. No other writes. Both already have status/attendance
  fields on the existing entities.

- **Schedule = combined view**: `GET /api/v1/coach/schedule` returns today's
  PT sessions + GX class instances for this trainer, sorted by time, as a
  unified list with a `type` discriminator (`"pt"` or `"gx"`).

- **Sidebar layout, not bottom nav**: Trainers use tablets or laptops at the
  gym. web-coach uses a left sidebar (collapsible) like web-pulse, not a
  mobile bottom nav like web-arena. Still responsive down to 375px.

- **JWT in memory only**: same rule as all frontend apps — never
  localStorage, never sessionStorage.

---

## Entity design

No new entities. This plan only adds coach-facing read endpoints + two
targeted write endpoints (mark PT attendance, mark GX attendance) over
existing entities.

---

## API endpoints

### TrainerProfileCoachController — `/api/v1/coach`

```
GET    /api/v1/coach/me          trainer profile + certifications + branch assignments
```

### ScheduleCoachController — `/api/v1/coach/schedule`

```
GET    /api/v1/coach/schedule    today's PT sessions + GX instances for this trainer
                                 query param: ?date=yyyy-MM-dd (default: today)
```

### PtCoachController — `/api/v1/coach/pt`

```
GET    /api/v1/coach/pt/sessions             upcoming + past PT sessions for this trainer
                                              query params: ?status=upcoming|past&page=0&size=20
PATCH  /api/v1/coach/pt/sessions/{id}/attendance   mark session attended or missed
```

### GxCoachController — `/api/v1/coach/gx`

```
GET    /api/v1/coach/gx/classes              GX class instances for this trainer
                                              query params: ?from=yyyy-MM-dd&to=yyyy-MM-dd
GET    /api/v1/coach/gx/classes/{id}/bookings   bookings list for a specific class instance
PATCH  /api/v1/coach/gx/classes/{id}/attendance  bulk mark attendance for a class
```

All coach endpoints require a valid JWT with `scope = "trainer"`.
No staff RBAC permission system — trainer identity comes from JWT claims only.

---

## Request / Response shapes

### TrainerMeResponse
```json
{
  "id": "uuid",
  "firstName": "string",
  "lastName": "string",
  "firstNameAr": "string | null",
  "lastNameAr": "string | null",
  "email": "string",
  "phone": "string | null",
  "trainerTypes": ["pt", "gx"],
  "club": { "id": "uuid", "name": "string", "nameAr": "string" },
  "branches": [{ "id": "uuid", "name": "string" }],
  "certifications": [
    {
      "id": "uuid",
      "name": "string",
      "issuingOrganization": "string",
      "issueDate": "yyyy-MM-dd",
      "expiryDate": "yyyy-MM-dd | null"
    }
  ]
}
```

### ScheduleItemResponse
```json
{
  "type": "pt | gx",
  "id": "uuid",
  "startTime": "ISO 8601",
  "endTime": "ISO 8601 | null",
  "title": "string",
  "memberOrClassName": "string",
  "status": "string",
  "bookedCount": 12,
  "capacity": 20
}
```
`bookedCount` and `capacity` only present when `type = "gx"`.
`memberOrClassName` = member name for PT, class type name for GX.

### PtSessionCoachResponse
```json
{
  "id": "uuid",
  "scheduledAt": "ISO 8601",
  "status": "scheduled | attended | missed | cancelled",
  "memberName": "string",
  "packageName": "string",
  "notes": "string | null"
}
```

### MarkPtAttendanceRequest
```json
{ "status": "attended | missed" }
```

### GxClassCoachResponse
```json
{
  "id": "uuid",
  "classType": { "name": "string", "nameAr": "string", "color": "string" },
  "startTime": "ISO 8601",
  "endTime": "ISO 8601",
  "capacity": 20,
  "bookedCount": 14,
  "attendedCount": 0
}
```

### GxBookingAttendanceItem
```json
{ "bookingId": "uuid", "attended": true }
```

### MarkGxAttendanceRequest
```json
{
  "attendances": [
    { "bookingId": "uuid", "attended": true },
    { "bookingId": "uuid", "attended": false }
  ]
}
```

### GxBookingCoachResponse
```json
{
  "id": "uuid",
  "memberName": "string",
  "bookedAt": "ISO 8601",
  "attended": "boolean | null"
}
```

---

## Business rules — enforce in service layer

1. **Trainer scope only** — all coach endpoints extract `trainerId` from JWT.
   Any request where the JWT `scope ≠ "trainer"` is rejected with 403.

2. **Trainer owns the PT session** — `PATCH /coach/pt/sessions/{id}/attendance`
   checks that `ptSession.trainerId = JWT trainerId`. Return 403 if not.

3. **Trainer instructs the GX class** — `GET` and `PATCH` on
   `/coach/gx/classes/{id}` check that `gxClassInstance.trainerId = JWT trainerId`.
   Return 403 if not.

4. **PT attendance: only scheduled sessions** — can only mark `attended` or
   `missed` on sessions with current status `scheduled`. Return 422
   "Session is already {status}" if already attended/missed/cancelled.

5. **GX attendance: class must be in the past or ongoing** — cannot mark
   attendance for a class whose `startTime` is more than 30 minutes in the
   future. Return 422 "Class has not started yet".

6. **trainerType gate** — if `trainerTypes` in JWT does not include `"pt"`,
   all `/coach/pt/*` endpoints return 403 "Not a PT trainer".
   If `trainerTypes` does not include `"gx"`, all `/coach/gx/*` endpoints
   return 403 "Not a GX instructor".

7. **Tenant isolation** — all queries filter by `clubId` and `trainerId`
   from JWT. A trainer can never see another trainer's sessions or classes.

8. **Schedule date range** — `GET /coach/schedule` only returns items for the
   requested date (default today). Maximum look-ahead: 30 days. Return 422
   if `?date` is more than 30 days in the future.

---

## Seed data updates

No new seed data needed. Existing seed already has:
- `pt@elixir.com` / `Trainer1234!` → PT Trainer (Khalid Al-Otaibi), `trainerTypes = ["pt"]`
- `gx@elixir.com` / `Trainer1234!` → GX Instructor (Noura Al-Harbi), `trainerTypes = ["gx"]`
- Ahmed's PT sessions assigned to Khalid
- GX class instances assigned to Noura

No DevDataLoader changes required.

---

## Frontend additions (web-coach)

### Bootstrap web-coach app
New Vite app at `web-coach/` with same stack as web-pulse:
React 18, TypeScript strict, TanStack Router (file-based), TanStack Query,
Zustand (auth state), React Hook Form + Zod, Tailwind CSS, i18next.
Port: 5175. JWT scope check: rejects non-`trainer` tokens with
"This app is for trainers only".

### Login — /auth/login
Standard email + password form. Calls `POST /api/v1/auth/login`.
On success: checks `scope === "trainer"`, stores JWT in memory + trainer
profile in Zustand. Redirects to `/`.

### App shell
Left collapsible sidebar (same pattern as web-pulse). Top header with
trainer name and language toggle. Nav items:
- Schedule (always shown)
- PT Sessions (shown only if `trainerTypes` includes `"pt"`)
- GX Classes (shown only if `trainerTypes` includes `"gx"`)
- Profile (always shown)

### Today's Schedule — /
Timeline view of today's PT sessions + GX classes sorted by time.
Date picker to navigate to other days (max 30 days ahead).
Each item shows type badge (PT/GX), time, title, member/class name, status.
Tap a PT item → PT session detail. Tap a GX item → GX class detail.

### PT Sessions — /pt
List view with two tabs: Upcoming / Past.
Each card: date/time, member name, package name, status badge.
Tap → PT session detail modal: full info + mark attended/missed buttons
(only shown when status = scheduled).

### GX Classes — /gx
Weekly grid view (same concept as web-pulse /gx but trainer-scoped).
Each class card: type color, time, capacity/booked count, attended count.
Tap → GX class detail page:
  - Class info (type, time, instructor — themselves)
  - Bookings list: member name, booked at, attended checkbox
  - "Save Attendance" button → `PATCH /coach/gx/classes/{id}/attendance`
  - Button disabled if class is more than 30 min in the future

### Profile — /profile
Trainer info: name (AR + EN), email, phone, trainer types badges.
Certifications list: name, issuing org, issue date, expiry (with
"Expires soon" warning badge if < 30 days to expiry).
Read-only — trainers cannot edit their own profile (staff manages this).

### i18n key sample
```json
{
  "login.scope_error": "This app is for trainers only",
  "schedule.title": "Today's Schedule",
  "schedule.empty": "No sessions or classes today",
  "pt.upcoming": "Upcoming",
  "pt.past": "Past",
  "pt.mark_attended": "Mark Attended",
  "pt.mark_missed": "Mark Missed",
  "gx.bookings": "Bookings",
  "gx.save_attendance": "Save Attendance",
  "gx.class_not_started": "Class hasn't started yet",
  "profile.certifications": "Certifications",
  "profile.expires_soon": "Expires soon",
  "profile.trainer_type.pt": "Personal Trainer",
  "profile.trainer_type.gx": "Group Exercise Instructor"
}
```

---

## Files to generate

### Backend — new files
```
coach/
  CoachAuthHelper.kt
  TrainerProfileCoachController.kt
  ScheduleCoachController.kt
  PtCoachController.kt
  GxCoachController.kt
  dto/
    TrainerMeResponse.kt
    ScheduleItemResponse.kt
    PtSessionCoachResponse.kt
    MarkPtAttendanceRequest.kt
    GxClassCoachResponse.kt
    GxBookingCoachResponse.kt
    MarkGxAttendanceRequest.kt
    GxBookingAttendanceItem.kt
```

### Backend — modified files
```
(none — no entity changes, no new permissions, no DevDataLoader changes)
```

### Frontend — new app
```
web-coach/
  package.json                 (React 18, TypeScript, Vite, TanStack Router/Query,
                                Zustand, RHF, Zod, i18next, Tailwind)
  vite.config.ts               (port 5175)
  tsconfig.json
  index.html
  src/
    main.tsx
    router.tsx
    store/authStore.ts         (JWT in memory, trainer profile + trainerTypes)
    lib/
      api.ts                   (axios instance → backend port 8080)
      formatCurrency.ts        (copy from web-pulse)
    types/
      domain.ts
    i18n/
      index.ts
      en.json
      ar.json
    api/
      auth.ts
      schedule.ts
      pt.ts
      gx.ts
      profile.ts
    routes/
      __root.tsx               (auth guard)
      auth/
        login.tsx
      index.tsx                (today's schedule)
      pt.tsx
      gx/
        index.tsx
        $classId.tsx           (class detail + attendance)
      profile.tsx
    components/
      shell/
        Sidebar.tsx
        AppHeader.tsx
      schedule/
        ScheduleItem.tsx
        DatePicker.tsx
      pt/
        PtSessionCard.tsx
        AttendanceModal.tsx
      gx/
        GxClassCard.tsx
        BookingAttendanceRow.tsx
      profile/
        CertificationCard.tsx
      common/
        StatusBadge.tsx
        TrainerTypeBadge.tsx
        EmptyState.tsx
        LoadingSpinner.tsx
```

---

## Implementation order

```
Step 1 — CoachAuthHelper + TrainerProfileCoachController
  coach/CoachAuthHelper.kt:
    extractTrainerId(jwt), extractTrainerTypes(jwt), requireTrainerType(jwt, "pt"|"gx")
    All coach endpoints call requireScope("trainer") first
  coach/TrainerProfileCoachController.kt:
    GET /api/v1/coach/me — fetch Trainer + TrainerCertification + TrainerBranchAssignment
    Map to TrainerMeResponse
  DTOs: TrainerMeResponse
  Verify: ./gradlew build -x test

Step 2 — ScheduleCoachController
  coach/ScheduleCoachController.kt:
    GET /api/v1/coach/schedule?date=yyyy-MM-dd
    Fetch PTSessions where trainerId = JWT trainerId AND scheduledAt = requested date
    Fetch GXClassInstances where trainerId = JWT trainerId AND startTime = requested date
    Merge, sort by time, map to ScheduleItemResponse list
    Rule 8: reject ?date > today + 30 days (422)
  DTOs: ScheduleItemResponse
  Verify: ./gradlew build -x test

Step 3 — PtCoachController
  coach/PtCoachController.kt:
    GET /api/v1/coach/pt/sessions?status=upcoming|past&page&size
    PATCH /api/v1/coach/pt/sessions/{id}/attendance
  Rules 2 (trainer owns session), 4 (only scheduled), 6 (trainerType gate: pt)
  All native @Query for any date filtering (no JPQL date arithmetic)
  DTOs: PtSessionCoachResponse, MarkPtAttendanceRequest
  Verify: ./gradlew build -x test

Step 4 — GxCoachController
  coach/GxCoachController.kt:
    GET /api/v1/coach/gx/classes?from=yyyy-MM-dd&to=yyyy-MM-dd
    GET /api/v1/coach/gx/classes/{id}/bookings
    PATCH /api/v1/coach/gx/classes/{id}/attendance
  Rules 3 (trainer instructs class), 5 (class not too far in future), 6 (trainerType gate: gx)
  Bulk attendance: iterate bookingIds, set GXBooking.attended, save all
  DTOs: GxClassCoachResponse, GxBookingCoachResponse, MarkGxAttendanceRequest, GxBookingAttendanceItem
  Verify: ./gradlew build -x test

Step 5 — Backend tests
  PtCoachServiceTest.kt (unit):
    - mark attendance: happy path (scheduled → attended), already attended (422),
      wrong trainer (403), not a PT trainer (403)
  GxCoachServiceTest.kt (unit):
    - bulk mark attendance: happy path, class in future (422),
      wrong trainer (403), not a GX instructor (403)
  ScheduleCoachControllerTest.kt (integration):
    - today's schedule for PT trainer (only PT items), for GX instructor (only GX items),
      for dual trainer (both), date > 30 days (422)
  PtCoachControllerTest.kt (integration):
    - session list upcoming/past, mark attended, mark missed,
      other trainer's session (403)
  GxCoachControllerTest.kt (integration):
    - class list, bookings list, mark attendance, future class (422),
      other trainer's class (403)
  Verify: ./gradlew test --no-daemon

Step 6 — Backend final checks
  ./gradlew ktlintFormat --no-daemon
  ./gradlew ktlintCheck --no-daemon
  ./gradlew build --no-daemon

Step 7 — Bootstrap web-coach app
  Create web-coach/ with package.json (port 5175)
  Install: react, react-dom, typescript, vite, @tanstack/react-router,
    @tanstack/react-query, zustand, react-hook-form, zod, i18next,
    react-i18next, tailwindcss, axios
  Setup: vite.config.ts, tsconfig.json, tailwind.config.js, index.html,
    src/main.tsx, src/router.tsx
  src/store/authStore.ts — JWT in memory, trainer profile, trainerTypes array
  src/lib/api.ts — axios instance → backend at port 8080
  Verify: cd web-coach && npm run dev → blank app loads at localhost:5175

Step 8 — Auth flow
  src/api/auth.ts — login (POST /api/v1/auth/login), logout, refresh
  src/routes/auth/login.tsx:
    Email + password form, submit → login → check scope === "trainer"
    If scope wrong → show "This app is for trainers only" error, clear token
    On success → save JWT in Zustand → redirect to /
  src/routes/__root.tsx:
    Auth guard: no JWT → /auth/login
    On mount: GET /api/v1/coach/me → populate trainer profile + trainerTypes
    On 401 → clear auth store + redirect to login
  Verify: npm run dev → login with pt@elixir.com / Trainer1234! → lands on schedule

Step 9 — App shell
  src/components/shell/Sidebar.tsx:
    Logo, nav items (Schedule always, PT if trainerTypes includes "pt",
    GX if trainerTypes includes "gx", Profile always)
    Collapsible on mobile
  src/components/shell/AppHeader.tsx:
    Trainer name, language toggle, logout button
  Verify: npm run dev → sidebar shows correct items for PT-only trainer

Step 10 — Today's schedule screen
  src/api/schedule.ts — getSchedule(date)
  src/routes/index.tsx:
    DatePicker to select date (default today, max today+30)
    ScheduleItem list sorted by time, type badge (PT/GX), tap to navigate
  src/components/schedule/ScheduleItem.tsx
  src/components/schedule/DatePicker.tsx
  Verify: npm run dev → Khalid's PT sessions appear for today

Step 11 — PT sessions screen
  src/api/pt.ts — getPtSessions(status, page), markPtAttendance(id, status)
  src/routes/pt.tsx:
    Upcoming / Past tabs, paginated list
    Tap → AttendanceModal: session detail + mark attended/missed buttons
    Buttons disabled when status ≠ scheduled
  src/components/pt/PtSessionCard.tsx
  src/components/pt/AttendanceModal.tsx
  Verify: npm run dev → mark Ahmed's session as attended → status updates

Step 12 — GX classes screen
  src/api/gx.ts — getGxClasses(from, to), getClassBookings(id), markGxAttendance(id, attendances)
  src/routes/gx/index.tsx — weekly grid of class cards
  src/routes/gx/$classId.tsx:
    Class detail: info + bookings list
    BookingAttendanceRow per booking (checkbox for attended)
    "Save Attendance" button → PATCH, disabled if class > 30 min in future
  src/components/gx/GxClassCard.tsx
  src/components/gx/BookingAttendanceRow.tsx
  Verify: npm run dev → Noura's Yoga class → check off attendees → save

Step 13 — Profile screen
  src/api/profile.ts — getMe()
  src/routes/profile.tsx:
    Trainer info card, TrainerTypeBadge per type
    Certifications list with CertificationCard
    "Expires soon" warning badge if expiry < 30 days away
  src/components/profile/CertificationCard.tsx
  src/components/common/TrainerTypeBadge.tsx
  Verify: npm run dev → Khalid's profile shows PT badge + certifications

Step 14 — Frontend tests
  Login.test.tsx — rejects non-trainer scope JWT
  Sidebar.test.tsx — PT-only trainer: no GX nav item; GX-only: no PT nav item
  ScheduleItem.test.tsx — renders correct type badge and time
  PtSessionCard.test.tsx — shows mark-attendance buttons only when scheduled
  BookingAttendanceRow.test.tsx — checkbox state, save button disabled for future class
  CertificationCard.test.tsx — "Expires soon" badge when < 30 days
  Verify: npm test

Step 15 — Frontend final checks
  npm run typecheck
  npm run lint
  npm run build
```

---

## Acceptance criteria

### Backend
- [ ] `GET /coach/me` returns Khalid's profile with PT type and certifications
- [ ] `GET /coach/schedule` returns only PT sessions for a PT-only trainer
- [ ] `GET /coach/schedule` returns only GX classes for a GX-only trainer
- [ ] `GET /coach/schedule?date=` more than 30 days ahead returns 422
- [ ] `PATCH /coach/pt/sessions/{id}/attendance` marks session attended → status changes
- [ ] Marking already-attended session returns 422
- [ ] Marking another trainer's PT session returns 403
- [ ] PT endpoint called by non-PT trainer (gx-only) returns 403
- [ ] `GET /coach/gx/classes/{id}/bookings` returns Noura's class bookings
- [ ] `PATCH /coach/gx/classes/{id}/attendance` bulk-marks bookings
- [ ] Marking attendance on a future class (>30min) returns 422
- [ ] GX endpoint called by non-GX trainer (pt-only) returns 403
- [ ] Another trainer's GX class returns 403
- [ ] All 305+ existing tests still pass

### Frontend (web-coach)
- [ ] Login with `pt@elixir.com` → lands on schedule, no GX nav item visible
- [ ] Login with `gx@elixir.com` → lands on schedule, no PT nav item visible
- [ ] Login with club staff token → rejected with "This app is for trainers only"
- [ ] Today's schedule shows Khalid's PT sessions on the correct date
- [ ] Mark PT session attended → card status updates immediately
- [ ] GX class detail shows Noura's bookings list with attendance checkboxes
- [ ] Save Attendance button disabled for a class that hasn't started
- [ ] Profile shows correct trainer type badges and certifications
- [ ] Arabic RTL layout correct throughout
- [ ] `npm run typecheck`, `npm run lint`, `npm run test`, `npm run build` all pass

---

## RBAC matrix

No new permissions added by this plan. Coach endpoints use JWT scope check
(`scope = "trainer"`) and trainerType claims — not the staff RBAC system.

---

## Definition of done

- All acceptance criteria checked
- All 8 business rules covered by unit or integration tests
- Login scope rejection tested (non-trainer token shows error message)
- trainerType gate tested: PT-only trainer cannot call GX endpoints (403)
- Tenant isolation: trainer A cannot see trainer B's sessions or classes
- web-coach runs independently on port 5175 (`cd web-coach && npm run dev`)
- All CI checks pass on PR
- PLAN.md deleted before merging
- PR title: `feat(coach): implement trainer dashboard with schedule and attendance`
- Target branch: `develop`
