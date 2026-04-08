# Plan 27 — Member Check-In & Attendance Tracking

## Status
Ready for implementation

## Branch
`feature/plan-27-checkin`

## Goal
Give receptionists a dedicated check-in screen in web-pulse where they can check in members by phone, name, or QR code. Records every visit with branch and method. Blocks lapsed members and duplicate check-ins within 1 hour. Adds a member-facing check-in QR in web-arena and plugs check-in data into the existing Custom Report Builder.

## Context
- `Member`, `Branch` entities already exist
- `MemberStatus.LAPSED` added in Plan 33 — check-in service must respect it
- Custom Report Builder (`MetricCatalogue`, `DimensionCatalogue`) already exists from Plan 19 — extend it
- `MemberNoteService` (Plan 32) already exists — no dependency for this plan
- Next Flyway migration: **V20**

---

## Scope — what this plan covers

- [ ] Flyway V20 — `member_check_ins` table
- [ ] `MemberCheckIn` entity
- [ ] `MemberCheckInService` — check-in, validation, search, today's count
- [ ] New audit action: `MEMBER_CHECKED_IN`
- [ ] New permissions: `check-in:create` (Receptionist, Branch Manager), `check-in:read` (Receptionist, Branch Manager, Owner)
- [ ] 4 endpoints (3 pulse, 1 arena)
- [ ] web-pulse: `/check-in` screen — search by phone/name/QR, check-in action, today's counter, recent check-ins list
- [ ] web-arena: check-in QR code screen (large QR of member publicId)
- [ ] Extend Custom Report Builder: `check_in_count` metric + `day_of_week` dimension
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- Member-facing visit history in web-arena
- Automatic check-out / visit duration tracking
- Turnstile / access control hardware integration
- Push notification on check-in
- Check-in via web-coach

---

## Decisions already made

- **3 check-in methods**: `staff_phone`, `staff_name`, `qr_scan` — stored on `MemberCheckIn.method`
- **QR code**: separate from ZATCA invoice QR — encodes member `publicId` as a plain UUID string (no JWT, no expiry); generated client-side in web-arena using a QR library
- **Duplicate block**: if a check-in record exists for this member at this branch within the last 60 minutes → `409 Conflict` with message "Already checked in N minutes ago"
- **Lapsed block**: member with `status = LAPSED` → `409 Conflict` with `errorCode: MEMBERSHIP_LAPSED` — same error shape as Plan 33's arena gate
- **Live counter**: `GET /api/v1/pulse/check-in/today-count` returns today's count for the authenticated staff member's active branch; refreshes on each successful check-in
- **No member-facing visit history** in web-arena (staff-side only)
- **Report Builder extension**: `check_in_count` metric (COUNT of check-ins) + `day_of_week` dimension (extracted from `checked_in_at`)
- **Flyway V20**

---

## Entity design

### MemberCheckIn

```kotlin
@Entity
@Table(name = "member_check_ins")
class MemberCheckIn(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "branch_id", nullable = false)
    val branchId: Long,

    @Column(name = "checked_in_by_user_id", nullable = false)
    val checkedInByUserId: Long,

    // staff_phone | staff_name | qr_scan
    @Column(name = "method", nullable = false, length = 20)
    val method: String,

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    val checkedInAt: Instant = Instant.now()
)
```

No `deletedAt` — check-in records are immutable. No soft delete.

---

## Flyway V20

```sql
-- V20__member_check_ins.sql

CREATE TABLE member_check_ins (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    member_id             BIGINT NOT NULL REFERENCES members(id),
    branch_id             BIGINT NOT NULL REFERENCES branches(id),
    checked_in_by_user_id BIGINT NOT NULL REFERENCES users(id),
    method                VARCHAR(20) NOT NULL,
    checked_in_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_check_ins_member_id     ON member_check_ins(member_id);
CREATE INDEX idx_check_ins_branch_date   ON member_check_ins(branch_id, checked_in_at);
CREATE INDEX idx_check_ins_member_branch ON member_check_ins(member_id, branch_id, checked_in_at DESC);
```

---

## Business rules — enforce in service layer

1. **Membership required**: member must have `status = ACTIVE`. If `status = LAPSED` → `409 Conflict` with `errorCode: MEMBERSHIP_LAPSED`, message: "Membership expired on {endDate}. Please renew before checking in." If `status = INACTIVE` or `TERMINATED` → `409 Conflict`, message: "Member account is not active."
2. **Duplicate block**: check for an existing `MemberCheckIn` where `member_id = ?` AND `branch_id = ?` AND `checked_in_at > now() - 60 minutes`. If found → `409 Conflict`, message: "Already checked in {N} minutes ago at this branch."
3. **Branch scoping**: `branchId` is taken from the JWT claims (`branchIds[0]` — the staff member's active branch). Staff cannot check in members to a branch they are not assigned to.
4. **QR code check-in**: the QR value is the member's `publicId` (UUID string). The backend resolves it via `memberRepository.findByPublicId()`. Same business rules 1–3 apply.
5. **Search scoping**: phone and name search is scoped to the club (`clubId` from JWT). Staff cannot find or check in members from other clubs.
6. **Audit**: every successful check-in logs `MEMBER_CHECKED_IN` with `entityType = "MemberCheckIn"`, `entityId = checkIn.publicId`, `changes = { memberId, branchId, method }`.

---

## API endpoints

| Method | Path | Scope | Permission | Description |
|--------|------|-------|------------|-------------|
| `POST` | `/api/v1/pulse/check-in` | club staff | `check-in:create` | Check in a member; body contains `memberPublicId` + `method` |
| `GET` | `/api/v1/pulse/check-in/today-count` | club staff | `check-in:read` | Today's check-in count for the active branch |
| `GET` | `/api/v1/pulse/check-in/recent` | club staff | `check-in:read` | Last 20 check-ins at the active branch (newest first) |
| `GET` | `/api/v1/arena/me/qr` | member | (authenticated member) | Member's check-in QR code data (returns `publicId` as string) |

Search (phone / name) is handled by the existing `GET /api/v1/pulse/members?search=` endpoint — no new search endpoint needed.

---

## Request / Response shapes

### POST /pulse/check-in

Request:
```json
{
  "memberPublicId": "uuid",
  "method": "staff_phone"
}
```

Response `201 Created`:
```json
{
  "checkInId": "uuid",
  "memberName": "Ahmed Al-Rashidi",
  "memberPhone": "+966501234567",
  "membershipPlan": "Basic Monthly",
  "checkedInAt": "2026-04-08T07:30:00Z",
  "branchName": "Elixir Gym - Riyadh",
  "method": "staff_phone",
  "todayCount": 47
}
```

The `todayCount` in the response lets the frontend update the counter without an extra round trip.

### GET /pulse/check-in/today-count

```json
{ "count": 47, "branchName": "Elixir Gym - Riyadh", "date": "2026-04-08" }
```

### GET /pulse/check-in/recent

```json
{
  "checkIns": [
    {
      "checkInId": "uuid",
      "memberName": "Ahmed Al-Rashidi",
      "memberPhone": "+966501234567",
      "method": "qr_scan",
      "checkedInAt": "2026-04-08T07:30:00Z"
    }
  ]
}
```

### GET /arena/me/qr

```json
{ "qrValue": "3fa85f64-5717-4562-b3fc-2c963f66afa6" }
```

The frontend renders this UUID as a QR image using a QR library (e.g. `qrcode.react`).

---

## Repository queries

All must use `nativeQuery = true`:

```kotlin
// Duplicate check-in detection
@Query(value = """
    SELECT COUNT(*) FROM member_check_ins
    WHERE member_id = :memberId
      AND branch_id = :branchId
      AND checked_in_at > :threshold
""", nativeQuery = true)
fun countRecentCheckIns(memberId: Long, branchId: Long, threshold: Instant): Long

// Today's count for a branch (Riyadh timezone)
@Query(value = """
    SELECT COUNT(*) FROM member_check_ins
    WHERE branch_id = :branchId
      AND DATE(checked_in_at AT TIME ZONE 'Asia/Riyadh') = :today
""", nativeQuery = true)
fun countTodayByBranch(branchId: Long, today: java.time.LocalDate): Long

// Recent check-ins for a branch
@Query(value = """
    SELECT ci.*, m.name_en AS member_name_en, m.name_ar AS member_name_ar, m.phone
    FROM member_check_ins ci
    JOIN members m ON m.id = ci.member_id
    WHERE ci.branch_id = :branchId
    ORDER BY ci.checked_in_at DESC
    LIMIT 20
""", nativeQuery = true)
fun findRecentByBranch(branchId: Long): List<RecentCheckInProjection>

// For report builder: count by club + date range + optional day_of_week
@Query(value = """
    SELECT COUNT(*) FROM member_check_ins ci
    JOIN branches b ON b.id = ci.branch_id
    WHERE b.club_id = :clubId
      AND ci.checked_in_at BETWEEN :from AND :to
""", nativeQuery = true)
fun countByClubAndDateRange(clubId: Long, from: Instant, to: Instant): Long
```

Interface projection for recent check-ins:

```kotlin
interface RecentCheckInProjection {
    val publicId: UUID
    val memberNameEn: String?
    val memberNameAr: String
    val phone: String
    val method: String
    val checkedInAt: Instant
}
```

---

## Custom Report Builder extension

In `MetricCatalogue.kt`, add:

```kotlin
MetricDefinition(
    code = "check_in_count",
    labelEn = "Check-In Count",
    labelAr = "عدد تسجيلات الحضور",
    sqlFragment = "COUNT(DISTINCT mci.id)",
    requiresJoin = "member_check_ins mci ON mci.member_id = m.id",
    scope = "operations"
)
```

In `DimensionCatalogue.kt`, add:

```kotlin
DimensionDefinition(
    code = "day_of_week",
    labelEn = "Day of Week",
    labelAr = "يوم الأسبوع",
    sqlFragment = "TO_CHAR(mci.checked_in_at AT TIME ZONE 'Asia/Riyadh', 'Day')",
    requiresJoin = "member_check_ins mci ON mci.member_id = m.id"
)
```

Update `CompatibilityMatrix` to allow `check_in_count` with dimensions: `branch`, `day_of_week`, `month`, `membership_plan`.

---

## Frontend additions

### web-pulse — `/check-in` new route

**Layout:**

```
┌─────────────────────────────────────────────────────┐
│  Check-In — Elixir Gym Riyadh     Today: 47 visits  │
├─────────────────────────────────────────────────────┤
│  [ Search by phone or name...          🔍 ]         │
│  [ Or enter QR code value...           📷 ]         │
├─────────────────────────────────────────────────────┤
│  Search results:                                    │
│  ○ Ahmed Al-Rashidi  +966501234567  Active          │
│    [ Check In ]                                     │
├─────────────────────────────────────────────────────┤
│  Recent check-ins (today):                          │
│  ● Ahmed Al-Rashidi   QR    07:30                   │
│  ● Sara Al-Zahrani    Phone  07:15                  │
└─────────────────────────────────────────────────────┘
```

- Search field calls existing `GET /pulse/members?search=` (debounced 300ms, min 2 chars)
- QR field: text input where receptionist types or pastes the UUID from the member's screen (no camera API — simpler, no browser permission needed)
- Search results show: name, phone, membership status badge (Active / Lapsed)
- "Check In" button calls `POST /pulse/check-in`; on 409 shows inline error ("Already checked in 5 min ago"); on success updates counter and recent list
- Recent list shows last 20 check-ins for the branch; refreshed after each check-in
- Sidebar nav item: "Check-In" (visible to Receptionist, Branch Manager, Owner)

**New i18n strings** (`ar.json` + `en.json`):
```
checkin.page_title
checkin.today_count
checkin.search_placeholder
checkin.qr_placeholder
checkin.check_in_button
checkin.already_checked_in     // "Already checked in {N} minutes ago"
checkin.membership_lapsed      // "Membership expired — cannot check in"
checkin.success_toast          // "Ahmed checked in ✓"
checkin.recent_title
checkin.method.staff_phone
checkin.method.staff_name
checkin.method.qr_scan
checkin.empty_recent
```

### web-arena — Check-In QR screen

**New route** `/qr` (or accessible from Profile screen):
- Large QR code displaying the member's `publicId`
- Rendered using `qrcode.react` (add to `package.json` if not present)
- Caption: "Show this to the receptionist to check in"
- QR value fetched from `GET /arena/me/qr`
- Screen brightness note: auto-brighten the screen (set `screen.orientation.lock` or similar) — optional / best-effort
- No expiry — the QR is static (member publicId never changes)

**New i18n strings** (`ar.json` + `en.json`):
```
qr.page_title
qr.instruction
qr.member_name
```

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/kotlin/com/liyaqa/checkin/entity/MemberCheckIn.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/repository/MemberCheckInRepository.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/repository/RecentCheckInProjection.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/service/MemberCheckInService.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/dto/CheckInRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/dto/CheckInResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/dto/TodayCountResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/dto/RecentCheckInsResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/controller/MemberCheckInPulseController.kt`
- `backend/src/main/kotlin/com/liyaqa/checkin/controller/MemberCheckInArenaController.kt`
- `backend/src/main/resources/db/migration/V20__member_check_ins.sql`
- `backend/src/test/kotlin/com/liyaqa/checkin/service/MemberCheckInServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/checkin/controller/MemberCheckInControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-pulse/src/routes/check-in/index.tsx`
- `apps/web-pulse/src/api/checkIn.ts`
- `apps/web-pulse/src/tests/check-in.test.tsx`
- `apps/web-arena/src/routes/qr/index.tsx`
- `apps/web-arena/src/api/memberQr.ts`
- `apps/web-arena/src/tests/member-qr.test.tsx`

### Files to modify

- `backend/.../audit/model/AuditAction.kt` — add `MEMBER_CHECKED_IN`
- `backend/.../permission/PermissionConstants.kt` — add `CHECK_IN_CREATE`, `CHECK_IN_READ`
- `backend/DevDataLoader.kt` — seed permissions to Receptionist, Branch Manager, Owner
- `backend/.../report/catalogue/MetricCatalogue.kt` — add `check_in_count`
- `backend/.../report/catalogue/DimensionCatalogue.kt` — add `day_of_week`
- `backend/.../report/catalogue/CompatibilityMatrix.kt` — add `check_in_count` compatibility rows
- `apps/web-pulse/src/routes/` (sidebar) — add Check-In nav item
- `apps/web-pulse/src/locales/ar.json` + `en.json`
- `apps/web-arena/src/locales/ar.json` + `en.json`
- `apps/web-arena/src/routes/_authenticated.tsx` or nav — add QR screen link

---

## Implementation order

### Step 1 — Flyway V20 + entity
- Write `V20__member_check_ins.sql`
- Write `MemberCheckIn.kt`
- Write `MemberCheckInRepository.kt` with all 4 native queries
- Write `RecentCheckInProjection.kt` interface
- Verify: `./gradlew flywayMigrate`

### Step 2 — Permissions + audit action
- Add `CHECK_IN_CREATE = "check-in:create"` and `CHECK_IN_READ = "check-in:read"` to `PermissionConstants.kt`
- Add `MEMBER_CHECKED_IN` to `AuditAction.kt`
- Seed to Receptionist, Branch Manager, Owner in `DevDataLoader`
- Verify: `./gradlew compileKotlin`

### Step 3 — MemberCheckInService
Implement:
- `checkIn(memberPublicId, method, actorUserId, branchId)` — enforces all 6 business rules, saves, logs audit, returns `CheckInResponse` including `todayCount`
- `getTodayCount(branchId)` — queries `countTodayByBranch` using Riyadh-local date
- `getRecent(branchId)` — queries `findRecentByBranch`, maps to response DTOs
- Verify: unit tests in `MemberCheckInServiceTest`

### Step 4 — Controllers
- `MemberCheckInPulseController` — 3 pulse endpoints with `@Operation` + `@PreAuthorize`
- `MemberCheckInArenaController` — 1 arena endpoint (`GET /arena/me/qr`) returning `{ qrValue: member.publicId }`
- Verify: `./gradlew compileKotlin`

### Step 5 — Custom Report Builder extension
- Add `check_in_count` to `MetricCatalogue`
- Add `day_of_week` to `DimensionCatalogue`
- Update `CompatibilityMatrix` — `check_in_count` compatible with `branch`, `day_of_week`, `month`, `membership_plan`
- Verify: `./gradlew test` — existing report builder tests still pass

### Step 6 — Frontend: web-pulse `/check-in`
- `/check-in` route with search field, QR input, search results, check-in button, today counter, recent list
- `checkIn.ts` — 3 API functions (checkIn, getTodayCount, getRecent)
- Add Check-In sidebar nav item
- Add i18n strings
- Verify: `npm run typecheck`

### Step 7 — Frontend: web-arena `/qr`
- `/qr` route with `qrcode.react` rendering member `publicId`
- `memberQr.ts` — 1 API function
- Add QR link to arena nav / profile screen
- Add i18n strings
- Verify: `npm run typecheck`

### Step 8 — Tests

**Unit: `MemberCheckInServiceTest`**
- `checkIn succeeds for active member and records check-in`
- `checkIn throws 409 with MEMBERSHIP_LAPSED when member is lapsed`
- `checkIn throws 409 when member is inactive`
- `checkIn throws 409 when member is terminated`
- `checkIn throws 409 with duplicate message when checked in within 60 minutes`
- `checkIn allows check-in after 60-minute window has passed`
- `checkIn logs MEMBER_CHECKED_IN audit action`
- `checkIn returns todayCount in response`
- `getTodayCount returns correct count for branch using Riyadh timezone`
- `getRecent returns last 20 check-ins for branch`

**Integration: `MemberCheckInControllerIntegrationTest`**
- `POST /pulse/check-in returns 201 for active member`
- `POST /pulse/check-in returns 409 for lapsed member`
- `POST /pulse/check-in returns 409 for duplicate check-in within 60 minutes`
- `POST /pulse/check-in returns 403 without check-in:create permission`
- `GET /pulse/check-in/today-count returns count for active branch`
- `GET /pulse/check-in/recent returns last 20 check-ins`
- `GET /pulse/check-in/recent returns 403 without check-in:read permission`
- `GET /arena/me/qr returns member publicId`
- `GET /arena/me/qr returns 401 without arena JWT`

**Frontend: `check-in.test.tsx` (pulse)**
- renders check-in page with search field and QR input
- search results appear after debounce
- Check In button calls check-in endpoint and updates counter
- error shown when duplicate check-in returned
- error shown when membership lapsed
- recent list renders after successful check-in

**Frontend: `member-qr.test.tsx` (arena)**
- renders QR code with member publicId
- renders member name below QR

---

## RBAC matrix rows added by this plan

| Permission | Owner | Branch Manager | Receptionist | Sales Agent |
|------------|-------|----------------|--------------|-------------|
| `check-in:create` | ✅ | ✅ | ✅ | — |
| `check-in:read` | ✅ | ✅ | ✅ | — |

---

## Definition of Done

- [ ] Flyway V20 runs cleanly: `member_check_ins` table with 3 indexes
- [ ] `MemberCheckIn` entity compiles with no `deletedAt` (immutable records)
- [ ] `check-in:create` and `check-in:read` permissions seeded to Receptionist, Branch Manager, Owner
- [ ] `MEMBER_CHECKED_IN` audit action wired into service
- [ ] Lapsed member check-in blocked with `errorCode: MEMBERSHIP_LAPSED`
- [ ] Inactive / terminated member check-in blocked
- [ ] Duplicate check-in within 60 minutes blocked with "Already checked in N minutes ago"
- [ ] All repository queries use `nativeQuery = true`
- [ ] Today's count uses Riyadh timezone (`Asia/Riyadh`)
- [ ] `POST /pulse/check-in` response includes `todayCount`
- [ ] 4 endpoints live: 3 pulse + 1 arena, all with `@Operation`
- [ ] Report Builder: `check_in_count` metric and `day_of_week` dimension added to catalogues
- [ ] Report Builder: `CompatibilityMatrix` updated for new metric + dimension
- [ ] web-pulse: `/check-in` route with search, QR input, results, counter, recent list
- [ ] web-pulse: Check-In sidebar nav item visible to Receptionist, Branch Manager, Owner
- [ ] web-arena: `/qr` route renders QR code of member publicId
- [ ] All i18n strings added in Arabic and English (web-pulse + web-arena)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors in web-pulse or web-arena
- [ ] `PROJECT-STATE.md` updated: Plan 27 complete, test counts, V20 noted
- [ ] `PLAN-27-checkin.md` deleted before merging

When all items are checked, confirm: **"Plan 27 — Member Check-In & Attendance complete. X backend tests, Y frontend tests."**

