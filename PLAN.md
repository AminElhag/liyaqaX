# Plan 26 — Staff Scheduling & Shifts

## Status
Ready for implementation

## Branch
`feature/plan-26-shifts`

## Goal
Give managers a weekly roster grid to schedule staff at their branch. Staff view their own upcoming shifts on a personal screen. Staff can request shift swaps with colleagues; the target colleague must accept, then a manager with `shift:manage` permission approves the transfer.

## Context
- `StaffMember`, `Branch`, `Club` entities already exist
- Notification system (Plan 21) exists — not used in this plan (no push notifications)
- Existing permissions seeded in `DevDataLoader` — new permissions added here
- Next Flyway migration: **V21**

---

## Scope — what this plan covers

- [ ] Flyway V21 — `staff_shifts` + `shift_swap_requests` tables
- [ ] `StaffShift` entity
- [ ] `ShiftSwapRequest` entity
- [ ] `StaffShiftService` — create, update, delete, conflict check
- [ ] `ShiftSwapService` — request, accept, approve, reject
- [ ] New permissions: `shift:manage`, `shift:read`
- [ ] New audit actions: `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_DELETED`, `SHIFT_SWAP_APPROVED`, `SHIFT_SWAP_REJECTED`
- [ ] 8 endpoints (pulse only — no arena/coach endpoints)
- [ ] web-pulse: `/schedule` weekly roster grid — manager view
- [ ] web-pulse: `/my-shifts` personal schedule — staff view
- [ ] web-pulse: Swap requests panel (pending swaps requiring manager action)
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- Notification / push alert on shift assignment
- Shift reminder the day before
- Integration with cash drawer sessions
- Mobile schedule view
- Recurring shift templates
- Public holiday detection

---

## Decisions already made

- **Custom start/end times**: no named shift blocks. Each shift is defined by `startAt` (TIMESTAMPTZ) and `endAt` (TIMESTAMPTZ).
- **Conflict detection**: if a staff member already has a shift that overlaps with the proposed start/end at **any** branch → `409 Conflict` with `errorCode: SHIFT_OVERLAP`. Check performed in service layer before saving.
- **Swap flow — 3 states**: `PENDING_ACCEPTANCE` (awaiting target) → `PENDING_APPROVAL` (target accepted, awaiting manager) → `APPROVED` / `REJECTED`. If target declines → `DECLINED`. If requester cancels before target accepts → `CANCELLED`.
- **Swap approval gate**: any user with `shift:manage` permission can approve or reject a swap.
- **Permissions**: `shift:manage` (create/edit/delete shifts + approve swaps), `shift:read` (view own shifts). Both are unseeded by default — the Owner seeds them to roles they choose via the existing role management UI. DevDataLoader seeds `shift:manage` to Owner and Branch Manager, `shift:read` to all club roles.
- **Roster scope**: per-branch. Staff member can only be rostered at branches they are assigned to (from `StaffBranchAssignment`). Validation: if a staff member is not assigned to the requested branch → `422`.
- **Weekly view**: 7-day grid. Default week = current week (Mon–Sun). Prev/Next week navigation.
- **Flyway V21**

---

## Entity design

### StaffShift

```kotlin
@Entity
@Table(name = "staff_shifts")
class StaffShift(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    @Column(name = "staff_member_id", nullable = false)
    val staffMemberId: Long,

    @Column(name = "branch_id", nullable = false)
    val branchId: Long,

    @Column(name = "start_at", nullable = false)
    val startAt: Instant,

    @Column(name = "end_at", nullable = false)
    val endAt: Instant,

    @Column(name = "notes", length = 500)
    val notes: String? = null,

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    val createdByUserId: Long,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
)
```

### ShiftSwapRequest

```kotlin
@Entity
@Table(name = "shift_swap_requests")
class ShiftSwapRequest(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    // The shift being offered for swap
    @Column(name = "shift_id", nullable = false, updatable = false)
    val shiftId: Long,

    // The staff member requesting the swap (owns the shift)
    @Column(name = "requester_staff_id", nullable = false, updatable = false)
    val requesterStaffId: Long,

    // The staff member being asked to take the shift
    @Column(name = "target_staff_id", nullable = false, updatable = false)
    val targetStaffId: Long,

    // PENDING_ACCEPTANCE | PENDING_APPROVAL | APPROVED | REJECTED | DECLINED | CANCELLED
    @Column(name = "status", nullable = false, length = 30)
    var status: String,

    @Column(name = "requester_note", length = 300)
    val requesterNote: String? = null,

    @Column(name = "resolved_by_user_id")
    var resolvedByUserId: Long? = null,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
```

---

## Flyway V21

```sql
-- V21__staff_shifts.sql

CREATE TABLE staff_shifts (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    staff_member_id     BIGINT NOT NULL REFERENCES staff_members(id),
    branch_id           BIGINT NOT NULL REFERENCES branches(id),
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    notes               VARCHAR(500),
    created_by_user_id  BIGINT NOT NULL REFERENCES users(id),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_shifts_staff_member  ON staff_shifts(staff_member_id);
CREATE INDEX idx_shifts_branch_start  ON staff_shifts(branch_id, start_at);
CREATE INDEX idx_shifts_staff_range   ON staff_shifts(staff_member_id, start_at, end_at)
    WHERE deleted_at IS NULL;

CREATE TABLE shift_swap_requests (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shift_id              BIGINT NOT NULL REFERENCES staff_shifts(id),
    requester_staff_id    BIGINT NOT NULL REFERENCES staff_members(id),
    target_staff_id       BIGINT NOT NULL REFERENCES staff_members(id),
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING_ACCEPTANCE',
    requester_note        VARCHAR(300),
    resolved_by_user_id   BIGINT REFERENCES users(id),
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_swap_shift          ON shift_swap_requests(shift_id);
CREATE INDEX idx_swap_requester      ON shift_swap_requests(requester_staff_id);
CREATE INDEX idx_swap_target         ON shift_swap_requests(target_staff_id);
CREATE INDEX idx_swap_status         ON shift_swap_requests(status) WHERE status IN ('PENDING_ACCEPTANCE', 'PENDING_APPROVAL');
```

**Index rationale:**
- `idx_shifts_staff_member` — personal schedule queries (load all shifts for a staff member)
- `idx_shifts_branch_start` — roster grid (all shifts for a branch in a week)
- `idx_shifts_staff_range` (partial, excludes deleted) — overlap conflict check
- `idx_swap_status` (partial) — fast lookup of open swap requests needing action

---

## Business rules — enforce in service layer

### Shift creation / update
1. **Branch assignment check**: staff member must be assigned to the target branch in `StaffBranchAssignment`. If not → `422 Unprocessable Entity`, `errorCode: STAFF_NOT_AT_BRANCH`.
2. **End after start**: `endAt` must be after `startAt`. If not → `422`, message: "Shift end time must be after start time."
3. **Overlap check**: query for non-deleted shifts for the same `staffMemberId` where `start_at < :endAt AND end_at > :startAt`. If any exist → `409 Conflict`, `errorCode: SHIFT_OVERLAP`, message: "Staff member already has a shift from {startAt} to {endAt} that overlaps."
4. **Club scoping**: `branchId` must belong to the authenticated user's club. Staff cannot be scheduled at branches outside their club.
5. **Audit**: every create/update/delete logs the appropriate audit action.

### Shift deletion
6. **Open swap block**: if the shift has an open `ShiftSwapRequest` with `status IN (PENDING_ACCEPTANCE, PENDING_APPROVAL)` → `409 Conflict`, `errorCode: SHIFT_HAS_PENDING_SWAP`, message: "Cannot delete a shift with a pending swap request."
7. **Soft delete**: set `deleted_at = NOW()`. Never hard-delete.

### Shift swap
8. **Requester owns shift**: only the staff member assigned to the shift can initiate a swap request for it.
9. **Target is club staff**: `targetStaffId` must be a staff member in the same club.
10. **No duplicate open request**: if a `PENDING_ACCEPTANCE` or `PENDING_APPROVAL` request already exists for the same shift → `409`, `errorCode: SWAP_ALREADY_PENDING`.
11. **Target acceptance**: only the target staff member can accept or decline.
12. **Target overlap check**: when target accepts → re-run overlap check for target's staffMemberId against the shift's time range. If overlap → `409`, `errorCode: SHIFT_OVERLAP`, message: "You already have a shift that overlaps with this one."
13. **Manager approval**: any user with `shift:manage` permission (scoped to same club) can approve or reject a swap in `PENDING_APPROVAL` state.
14. **Approved swap transfer**: on approval → update `StaffShift.staffMemberId` to target's staffMemberId, set swap `status = APPROVED`, `resolvedByUserId`, `resolvedAt`.
15. **Rejected swap**: swap `status = REJECTED`, shift ownership unchanged.
16. **Cancelled swap**: requester can cancel a `PENDING_ACCEPTANCE` swap → `status = CANCELLED`.

---

## API endpoints

| Method | Path | Permission | Description |
|--------|------|------------|-------------|
| `POST` | `/api/v1/pulse/shifts` | `shift:manage` | Create a shift for a staff member |
| `PATCH` | `/api/v1/pulse/shifts/{shiftId}` | `shift:manage` | Update shift times or notes |
| `DELETE` | `/api/v1/pulse/shifts/{shiftId}` | `shift:manage` | Soft-delete a shift |
| `GET` | `/api/v1/pulse/shifts` | `shift:manage` | Get roster grid for a branch+week |
| `GET` | `/api/v1/pulse/shifts/my` | `shift:read` | Get own upcoming shifts (next 14 days) |
| `POST` | `/api/v1/pulse/shifts/{shiftId}/swap-requests` | `shift:read` | Request a swap (requester = caller) |
| `PATCH` | `/api/v1/pulse/shifts/swap-requests/{swapId}/respond` | `shift:read` | Target accepts or declines (`{ "action": "accept" \| "decline" }`) |
| `PATCH` | `/api/v1/pulse/shifts/swap-requests/{swapId}/resolve` | `shift:manage` | Manager approves or rejects (`{ "action": "approve" \| "reject" }`) |
| `GET` | `/api/v1/pulse/shifts/swap-requests/pending` | `shift:manage` | List all pending-approval swaps for the club |

---

## Request / Response shapes

### POST /pulse/shifts — request
```json
{
  "staffMemberPublicId": "uuid",
  "branchPublicId": "uuid",
  "startAt": "2026-04-14T06:00:00Z",
  "endAt": "2026-04-14T14:00:00Z",
  "notes": "Opening shift"
}
```

### POST /pulse/shifts — response `201 Created`
```json
{
  "shiftId": "uuid",
  "staffMemberName": "Khalid Al-Otaibi",
  "branchName": "Elixir Gym - Riyadh",
  "startAt": "2026-04-14T06:00:00Z",
  "endAt": "2026-04-14T14:00:00Z",
  "notes": "Opening shift"
}
```

### GET /pulse/shifts — query params + response
Query: `?branchPublicId=uuid&weekStart=2026-04-13` (ISO date, Monday of week)

```json
{
  "branchName": "Elixir Gym - Riyadh",
  "weekStart": "2026-04-13",
  "weekEnd": "2026-04-19",
  "shifts": [
    {
      "shiftId": "uuid",
      "staffMemberId": "uuid",
      "staffMemberName": "Khalid Al-Otaibi",
      "startAt": "2026-04-14T06:00:00Z",
      "endAt": "2026-04-14T14:00:00Z",
      "notes": "Opening shift",
      "hasPendingSwap": false
    }
  ]
}
```

### GET /pulse/shifts/my — response
```json
{
  "shifts": [
    {
      "shiftId": "uuid",
      "branchName": "Elixir Gym - Riyadh",
      "startAt": "2026-04-14T06:00:00Z",
      "endAt": "2026-04-14T14:00:00Z",
      "notes": "Opening shift",
      "swapRequest": null
    }
  ]
}
```

The `swapRequest` field is null if no open swap exists for the shift; otherwise it contains `{ swapId, targetStaffName, status }`.

### POST /pulse/shifts/{shiftId}/swap-requests — request
```json
{
  "targetStaffPublicId": "uuid",
  "requesterNote": "Can you cover this? I have a doctor appointment"
}
```

### PATCH /pulse/shifts/swap-requests/{swapId}/respond — request
```json
{ "action": "accept" }
```
or
```json
{ "action": "decline" }
```

### PATCH /pulse/shifts/swap-requests/{swapId}/resolve — request
```json
{ "action": "approve" }
```
or
```json
{ "action": "reject" }
```

### GET /pulse/shifts/swap-requests/pending — response
```json
{
  "swapRequests": [
    {
      "swapId": "uuid",
      "shiftDate": "2026-04-14",
      "shiftStart": "2026-04-14T06:00:00Z",
      "shiftEnd": "2026-04-14T14:00:00Z",
      "requesterName": "Khalid Al-Otaibi",
      "targetName": "Sara Al-Zahrani",
      "status": "PENDING_APPROVAL",
      "requesterNote": "Can you cover this?"
    }
  ]
}
```

---

## Repository queries

All must use `nativeQuery = true`:

```kotlin
// Overlap check for a staff member in a time range
@Query(value = """
    SELECT COUNT(*) FROM staff_shifts
    WHERE staff_member_id = :staffMemberId
      AND deleted_at IS NULL
      AND start_at < :endAt
      AND end_at > :startAt
      AND id != :excludeId
""", nativeQuery = true)
fun countOverlapping(staffMemberId: Long, startAt: Instant, endAt: Instant, excludeId: Long): Long

// Roster grid for a branch in a week
@Query(value = """
    SELECT s.*, sm.name_en AS staff_name_en, sm.name_ar AS staff_name_ar
    FROM staff_shifts s
    JOIN staff_members sm ON sm.id = s.staff_member_id
    WHERE s.branch_id = :branchId
      AND s.start_at >= :weekStart
      AND s.start_at < :weekEnd
      AND s.deleted_at IS NULL
    ORDER BY s.start_at
""", nativeQuery = true)
fun findByBranchAndWeek(branchId: Long, weekStart: Instant, weekEnd: Instant): List<ShiftRosterProjection>

// My upcoming shifts (next 14 days)
@Query(value = """
    SELECT s.*, b.name AS branch_name
    FROM staff_shifts s
    JOIN branches b ON b.id = s.branch_id
    WHERE s.staff_member_id = :staffMemberId
      AND s.start_at >= :now
      AND s.start_at < :until
      AND s.deleted_at IS NULL
    ORDER BY s.start_at
""", nativeQuery = true)
fun findUpcoming(staffMemberId: Long, now: Instant, until: Instant): List<MyShiftProjection>

// Check for open swap on a shift
@Query(value = """
    SELECT COUNT(*) FROM shift_swap_requests
    WHERE shift_id = :shiftId
      AND status IN ('PENDING_ACCEPTANCE', 'PENDING_APPROVAL')
""", nativeQuery = true)
fun countOpenSwapsForShift(shiftId: Long): Long

// All pending-approval swaps for a club
@Query(value = """
    SELECT sr.*, 
           sm_req.name_en AS requester_name_en, sm_req.name_ar AS requester_name_ar,
           sm_tgt.name_en AS target_name_en,    sm_tgt.name_ar AS target_name_ar,
           s.start_at AS shift_start, s.end_at AS shift_end
    FROM shift_swap_requests sr
    JOIN staff_shifts s       ON s.id = sr.shift_id
    JOIN branches b           ON b.id = s.branch_id
    JOIN staff_members sm_req ON sm_req.id = sr.requester_staff_id
    JOIN staff_members sm_tgt ON sm_tgt.id = sr.target_staff_id
    WHERE b.club_id = :clubId
      AND sr.status = 'PENDING_APPROVAL'
    ORDER BY sr.created_at
""", nativeQuery = true)
fun findPendingApprovalByClub(clubId: Long): List<PendingSwapProjection>
```

Interface projections:

```kotlin
interface ShiftRosterProjection {
    val publicId: UUID
    val staffMemberId: Long
    val staffNameEn: String?
    val staffNameAr: String
    val startAt: Instant
    val endAt: Instant
    val notes: String?
}

interface MyShiftProjection {
    val publicId: UUID
    val branchName: String
    val startAt: Instant
    val endAt: Instant
    val notes: String?
}

interface PendingSwapProjection {
    val publicId: UUID         // swap public_id
    val shiftStart: Instant
    val shiftEnd: Instant
    val requesterNameEn: String?
    val requesterNameAr: String
    val targetNameEn: String?
    val targetNameAr: String
    val requesterNote: String?
}
```

---

## Frontend additions

### web-pulse — `/schedule` (new route, manager view)

**Layout:**

```
┌─────────────────────────────────────────────────────────────┐
│  Schedule — Elixir Gym Riyadh     ← Week of Apr 13, 2026 → │
├──────────┬──────┬──────┬──────┬──────┬──────┬──────┬───────┤
│ Staff    │ Mon  │ Tue  │ Wed  │ Thu  │ Fri  │ Sat  │ Sun   │
├──────────┼──────┼──────┼──────┼──────┼──────┼──────┼───────┤
│ Khalid   │06–14 │      │06–14 │      │      │      │       │
│ Sara     │      │10–18 │      │10–18 │      │10–18 │       │
│ Ahmed    │      │      │      │      │08–16 │08–16 │       │
└──────────┴──────┴──────┴──────┴──────┴──────┴──────┴───────┘
 + Add Shift button (opens a modal: staff picker, date, start time, end time, notes)
```

- Branch selector in topbar (existing component) controls which branch is shown
- Prev/Next week navigation buttons
- Each shift cell is clickable → shift detail popover (start/end, notes, delete button, swap status badge if pending swap)
- "Add Shift" opens a modal:
  - Staff member dropdown (all staff assigned to the active branch)
  - Date picker (defaults to clicked day)
  - Start time + End time (time inputs)
  - Notes (optional, 500 char max)
  - On 409 SHIFT_OVERLAP: inline error in modal
- Pending swap badge: orange `⇆` icon on shift cells with a pending swap
- Sidebar nav item: "Schedule" (visible to users with `shift:manage`)

**Pending swap requests panel** — shown below the roster grid when `swap-requests/pending` returns results:

```
┌──────────────────────────────────────────────────────┐
│  Pending Swap Requests                               │
│  Khalid → Sara  |  Mon Apr 14  06:00–14:00           │
│  "Can you cover this? Doctor appointment"            │
│  [ Approve ]  [ Reject ]                             │
└──────────────────────────────────────────────────────┘
```

### web-pulse — `/my-shifts` (new route, staff view)

- List of own upcoming shifts (next 14 days), sorted by date
- Each row: date, time range, branch name, notes
- "Request Swap" button per shift → modal: target staff member picker (same branch), optional note
- Swap status badge per shift: "Swap Pending — waiting for Sara to accept" / "Awaiting manager approval" / "Swap Approved" / "Swap Declined"
- Sidebar nav item: "My Shifts" (visible to all staff with `shift:read`)

**New i18n strings** (`ar.json` + `en.json`):
```
schedule.page_title
schedule.week_of
schedule.add_shift
schedule.shift_modal_title
schedule.shift_modal_staff
schedule.shift_modal_date
schedule.shift_modal_start
schedule.shift_modal_end
schedule.shift_modal_notes
schedule.overlap_error
schedule.not_at_branch_error
schedule.delete_shift_confirm
schedule.pending_swaps_title
schedule.swap_approve
schedule.swap_reject
myshifts.page_title
myshifts.no_shifts
myshifts.request_swap
myshifts.swap_modal_title
myshifts.swap_modal_target
myshifts.swap_modal_note
myshifts.swap_status.pending_acceptance
myshifts.swap_status.pending_approval
myshifts.swap_status.approved
myshifts.swap_status.declined
myshifts.swap_status.cancelled
```

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/kotlin/com/liyaqa/shift/entity/StaffShift.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/entity/ShiftSwapRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/repository/StaffShiftRepository.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/repository/ShiftSwapRequestRepository.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/repository/ShiftRosterProjection.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/repository/MyShiftProjection.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/repository/PendingSwapProjection.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/service/StaffShiftService.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/service/ShiftSwapService.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/dto/CreateShiftRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/dto/UpdateShiftRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/dto/ShiftResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/dto/RosterResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/dto/MyShiftsResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/dto/CreateSwapRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/dto/SwapActionRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/dto/PendingSwapsResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/shift/controller/StaffShiftController.kt`
- `backend/src/main/resources/db/migration/V21__staff_shifts.sql`
- `backend/src/test/kotlin/com/liyaqa/shift/service/StaffShiftServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/shift/service/ShiftSwapServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/shift/controller/StaffShiftControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-pulse/src/routes/schedule/index.tsx`
- `apps/web-pulse/src/routes/schedule/AddShiftModal.tsx`
- `apps/web-pulse/src/routes/my-shifts/index.tsx`
- `apps/web-pulse/src/routes/my-shifts/RequestSwapModal.tsx`
- `apps/web-pulse/src/api/shifts.ts`
- `apps/web-pulse/src/tests/schedule.test.tsx`
- `apps/web-pulse/src/tests/my-shifts.test.tsx`

### Files to modify

- `backend/.../audit/model/AuditAction.kt` — add `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_DELETED`, `SHIFT_SWAP_APPROVED`, `SHIFT_SWAP_REJECTED`
- `backend/.../permission/PermissionConstants.kt` — add `SHIFT_MANAGE = "shift:manage"`, `SHIFT_READ = "shift:read"`
- `backend/DevDataLoader.kt` — seed `shift:manage` to Owner + Branch Manager; `shift:read` to all club roles
- `apps/web-pulse/src/routes/` (sidebar) — add Schedule nav item (shift:manage), My Shifts nav item (shift:read)
- `apps/web-pulse/src/locales/ar.json` + `en.json`

---

## Implementation order

### Step 1 — Flyway V21 + entities + repositories
- Write `V21__staff_shifts.sql`
- Write `StaffShift.kt`, `ShiftSwapRequest.kt`
- Write `StaffShiftRepository.kt` with 4 native queries
- Write `ShiftSwapRequestRepository.kt` with 2 native queries
- Write 3 projection interfaces
- Verify: `./gradlew flywayMigrate`

### Step 2 — Permissions + audit actions
- Add `SHIFT_MANAGE`, `SHIFT_READ` to `PermissionConstants.kt`
- Add `SHIFT_CREATED`, `SHIFT_UPDATED`, `SHIFT_DELETED`, `SHIFT_SWAP_APPROVED`, `SHIFT_SWAP_REJECTED` to `AuditAction.kt`
- Seed in `DevDataLoader`
- Verify: `./gradlew compileKotlin`

### Step 3 — StaffShiftService
Implement:
- `createShift()` — validates branch assignment, validates end > start, runs overlap check, saves, logs `SHIFT_CREATED`
- `updateShift()` — validates end > start, runs overlap check (excluding current shift id), saves, logs `SHIFT_UPDATED`
- `deleteShift()` — checks for open swaps (409 if found), soft-deletes, logs `SHIFT_DELETED`
- `getRoster(branchId, weekStart)` — queries week's shifts for a branch, resolves week window (Mon–Sun)
- `getMyShifts(staffMemberId)` — queries upcoming 14 days, annotates each with any open swap request
- Verify: unit tests in `StaffShiftServiceTest`

### Step 4 — ShiftSwapService
Implement:
- `requestSwap()` — validates requester owns shift, validates target is same-club staff, checks no duplicate open swap, saves with `PENDING_ACCEPTANCE`
- `respondToSwap(action: accept|decline)` — validates caller is target staff; if accept → run overlap check for target, move to `PENDING_APPROVAL`; if decline → `DECLINED`
- `resolveSwap(action: approve|reject)` — validates caller has `shift:manage`; if approve → transfer shift ownership, log `SHIFT_SWAP_APPROVED`; if reject → log `SHIFT_SWAP_REJECTED`
- `cancelSwap()` — validates caller is requester, status must be `PENDING_ACCEPTANCE` → `CANCELLED`
- `getPendingApprovals(clubId)` — queries `PENDING_APPROVAL` swaps for the club
- Verify: unit tests in `ShiftSwapServiceTest`

### Step 5 — Controller
- `StaffShiftController` — 9 endpoints with `@Operation` + `@PreAuthorize`
- All endpoints are under `/api/v1/pulse/shifts`
- Verify: `./gradlew compileKotlin`

### Step 6 — Frontend: web-pulse `/schedule`
- Weekly grid (7 columns × N staff rows)
- AddShiftModal component
- Pending swap panel below grid
- Approve/Reject swap actions
- Branch-scoped (reads branchId from existing branch selector state)
- Add Schedule to sidebar
- Add i18n strings

### Step 7 — Frontend: web-pulse `/my-shifts`
- List of upcoming shifts for authenticated staff member
- RequestSwapModal component
- Swap status badges
- Add My Shifts to sidebar
- Add i18n strings
- Verify: `npm run typecheck`

### Step 8 — Tests

**Unit: `StaffShiftServiceTest`**
- `createShift succeeds for valid staff at assigned branch`
- `createShift throws 422 when staff is not assigned to branch`
- `createShift throws 422 when endAt is before startAt`
- `createShift throws 409 SHIFT_OVERLAP when staff has overlapping shift`
- `createShift allows non-overlapping shift at different time same branch`
- `deleteShift throws 409 when shift has pending swap request`
- `deleteShift soft-deletes and logs audit`

**Unit: `ShiftSwapServiceTest`**
- `requestSwap creates PENDING_ACCEPTANCE request`
- `requestSwap throws 409 when duplicate open swap exists`
- `respondToSwap accept moves status to PENDING_APPROVAL`
- `respondToSwap accept throws 409 SHIFT_OVERLAP when target already has overlapping shift`
- `respondToSwap decline moves status to DECLINED`
- `resolveSwap approve transfers shift ownership and logs audit`
- `resolveSwap reject logs audit and leaves shift unchanged`
- `cancelSwap cancels PENDING_ACCEPTANCE request`

**Integration: `StaffShiftControllerIntegrationTest`**
- `POST /pulse/shifts returns 201 for valid shift`
- `POST /pulse/shifts returns 409 SHIFT_OVERLAP for overlapping shift`
- `POST /pulse/shifts returns 422 when staff not at branch`
- `POST /pulse/shifts returns 403 without shift:manage permission`
- `GET /pulse/shifts returns roster grid for branch and week`
- `GET /pulse/shifts/my returns upcoming shifts for authenticated staff`
- `DELETE /pulse/shifts/{id} returns 204 for valid shift`
- `DELETE /pulse/shifts/{id} returns 409 when pending swap exists`
- `POST /pulse/shifts/{id}/swap-requests creates PENDING_ACCEPTANCE swap`
- `PATCH /pulse/shifts/swap-requests/{id}/respond accept moves to PENDING_APPROVAL`
- `PATCH /pulse/shifts/swap-requests/{id}/resolve approve transfers ownership`
- `GET /pulse/shifts/swap-requests/pending returns manager's pending approvals`

**Frontend: `schedule.test.tsx`**
- renders weekly roster grid with shifts
- Add Shift modal opens and submits
- overlap error shown inline in modal
- pending swap panel renders approve/reject actions

**Frontend: `my-shifts.test.tsx`**
- renders upcoming shifts list
- Request Swap modal opens
- swap status badge renders correct text

---

## RBAC matrix rows added by this plan

| Permission | Owner | Branch Manager | Receptionist | Sales Agent | PT Trainer | GX Instructor |
|---|---|---|---|---|---|---|
| `shift:manage` | ✅ (seeded) | ✅ (seeded) | — | — | — | — |
| `shift:read` | ✅ (seeded) | ✅ (seeded) | ✅ (seeded) | ✅ (seeded) | ✅ (seeded) | ✅ (seeded) |

Note: any role can be granted `shift:manage` via the role management UI — the seeded defaults are above.

---

## Definition of Done

- [ ] Flyway V21 runs cleanly: `staff_shifts` and `shift_swap_requests` tables with all indexes
- [ ] `StaffShift` entity has `deletedAt` (soft delete); `ShiftSwapRequest` has no `deletedAt`
- [ ] `shift:manage` and `shift:read` permissions seeded correctly
- [ ] All 5 audit actions wired into services
- [ ] Overlap check blocks duplicate shifts with `errorCode: SHIFT_OVERLAP`
- [ ] Branch assignment check blocks scheduling staff at unassigned branches with `errorCode: STAFF_NOT_AT_BRANCH`
- [ ] Shift delete blocked by open swap with `errorCode: SHIFT_HAS_PENDING_SWAP`
- [ ] Full swap lifecycle: PENDING_ACCEPTANCE → PENDING_APPROVAL → APPROVED / REJECTED (and DECLINED / CANCELLED paths)
- [ ] Swap approval transfers `StaffShift.staffMemberId` to target
- [ ] All repository queries use `nativeQuery = true`
- [ ] `GET /pulse/shifts` returns correct 7-day window (Mon–Sun of requested week)
- [ ] `GET /pulse/shifts/my` returns next 14 days only, annotated with swap status
- [ ] 9 endpoints live, all with `@Operation` and `@PreAuthorize`
- [ ] web-pulse: `/schedule` weekly grid with branch selector, Add Shift modal, pending swaps panel
- [ ] web-pulse: `/my-shifts` list with Request Swap modal and swap status badges
- [ ] Sidebar: Schedule nav item (shift:manage only), My Shifts nav item (shift:read)
- [ ] All i18n strings added in Arabic and English
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors in web-pulse
- [ ] `PROJECT-STATE.md` updated: Plan 26 complete, test counts, V21 noted
- [ ] `PLAN-26-shifts.md` deleted before merging

When all items are checked, confirm: **"Plan 26 — Staff Scheduling & Shifts complete. X backend tests, Y frontend tests."**

