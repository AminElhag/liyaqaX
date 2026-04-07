# Plan 25 — GX Class Waitlist

## Status
Ready for implementation

## Branch
`feature/plan-25-gx-waitlist`

## Goal
When a GX class is full, members joining from web-arena are placed on a waitlist instead of receiving a hard rejection. When a spot opens, the top-queued member is notified and has 2 hours to accept. If they do not accept, the next person in queue is automatically offered the spot. Converts full-class rejections into confirmed bookings with zero staff involvement.

## Context
- `GXClassInstance` already has `capacity` and `currentBookingCount` fields
- `GXBooking` already exists with statuses; booking cancellation logic already in `GxArenaController`
- Staff can remove members from a class via web-pulse (`GxPulseController`)
- Staff can update class instance capacity via web-pulse
- Notification system (Plan 21) is live — new notification types wire in automatically
- `@Scheduled` scheduler pattern already established in `ZatcaReportingScheduler` and `NotificationSchedulerService`
- Next Flyway migration: **V17**

---

## Scope — what this plan covers

- [ ] Flyway V17 — `gx_waitlist_entries` table
- [ ] `GXWaitlistEntry` entity + `GXWaitlistStatus` enum
- [ ] `GXWaitlistService` — join, accept, leave, promote, expire
- [ ] `GXWaitlistScheduler` — hourly check for expired offers
- [ ] Hook into 3 spot-opening triggers: booking cancellation, staff removal, capacity increase
- [ ] 3 new notification types: `GX_WAITLIST_OFFERED`, `GX_WAITLIST_EXPIRED`, `GX_WAITLIST_CONFIRMED`
- [ ] Hook class cancellation: clear waitlist + notify all waiting/offered members
- [ ] 5 new endpoints (3 arena, 2 pulse)
- [ ] web-arena: waitlist join/leave/accept UI on class detail and bookings screen
- [ ] web-pulse: waitlist count column on GX schedule grid + waitlist tab on class detail
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- Waitlist for PT sessions
- Email notifications for waitlist events (bell + drawer only, same as Plan 21)
- Paid waitlist reservations
- Staff manually reordering waitlist positions
- Waitlist cap (unlimited entries per class)

---

## Decisions already made

- **Offer window**: 2 hours from `notifiedAt`. Enforced by `GXWaitlistScheduler` (hourly).
- **Expired offer**: entry status → `EXPIRED`, scheduler immediately promotes next `WAITING` entry.
- **Accept mechanism**: "Accept Spot" button in web-arena. Acceptance atomically creates a `GXBooking` and sets entry status → `ACCEPTED`.
- **Duplicate guard**: member already has an active booking for this class → 409. Member already has a WAITING or OFFERED entry for this class → 409.
- **Class cancelled**: all `WAITING` and `OFFERED` entries → `CANCELLED`. Notify all affected members with existing `GX_CLASS_CANCELLED` type.
- **Spot triggers**: booking cancellation, staff removes member from class, staff increases `GXClassInstance.capacity`.
- **Position**: calculated dynamically — `COUNT(*) WHERE class_instance_id = ? AND status = 'WAITING' AND position < thisEntry.position + 1`. Shown exactly in web-arena.
- **No Flyway gap**: this is V17 (V16 = member_import_jobs).

---

## Entity design

### GXWaitlistEntry

```kotlin
@Entity
@Table(
    name = "gx_waitlist_entries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_waitlist_member_class",
            columnNames = ["class_instance_id", "member_id"]
        )
    ]
)
class GXWaitlistEntry(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    @Column(name = "class_instance_id", nullable = false)
    val classInstanceId: Long,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    // Sequential position within this class's waitlist — lower = closer to front
    // Set on insert as MAX(position) + 1 for the class; never reordered on expiry/cancellation
    @Column(name = "position", nullable = false)
    val position: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: GXWaitlistStatus = GXWaitlistStatus.WAITING,

    // Set when status transitions to OFFERED
    @Column(name = "notified_at")
    var notifiedAt: Instant? = null,

    // Set when status transitions to ACCEPTED — references the GXBooking created on acceptance
    @Column(name = "accepted_booking_id")
    var acceptedBookingId: Long? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

enum class GXWaitlistStatus {
    WAITING,    // in queue, not yet offered
    OFFERED,    // spot offered, 2-hour window active
    ACCEPTED,   // member accepted, GXBooking created
    EXPIRED,    // 2-hour window elapsed without acceptance
    CANCELLED   // class was cancelled or member left the waitlist manually
}
```

---

## Flyway V17

```sql
-- V17__gx_waitlist.sql

CREATE TABLE gx_waitlist_entries (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    class_instance_id   BIGINT NOT NULL REFERENCES gx_class_instances(id),
    member_id           BIGINT NOT NULL REFERENCES members(id),
    position            INTEGER NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    notified_at         TIMESTAMPTZ,
    accepted_booking_id BIGINT REFERENCES gx_bookings(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_waitlist_member_class UNIQUE (class_instance_id, member_id)
);

CREATE INDEX idx_gx_waitlist_class_status ON gx_waitlist_entries(class_instance_id, status);
CREATE INDEX idx_gx_waitlist_member ON gx_waitlist_entries(member_id);
```

---

## Notification types

Add to `NotificationType.kt`:

```kotlin
GX_WAITLIST_OFFERED,    // spot available — accept within 2 hours
GX_WAITLIST_EXPIRED,    // 2-hour offer window elapsed
GX_WAITLIST_CONFIRMED,  // member accepted, now booked
```

Body strings (en / ar):

| Type | EN | AR |
|------|----|----|
| `GX_WAITLIST_OFFERED` | "A spot opened in {className} on {date}. Accept by {deadline}." | "تفتح مقعد في {className} بتاريخ {date}. اقبل قبل {deadline}." |
| `GX_WAITLIST_EXPIRED` | "Your waitlist offer for {className} has expired." | "انتهت صلاحية عرض قائمة الانتظار لـ {className}." |
| `GX_WAITLIST_CONFIRMED` | "You're booked for {className} on {date}!" | "تم حجزك في {className} بتاريخ {date}!" |

---

## API endpoints

| Method | Path | Scope | Permission | Description |
|--------|------|-------|------------|-------------|
| `POST` | `/api/v1/arena/gx/{classInstancePublicId}/waitlist` | member | (authenticated member) | Join the waitlist for a full class |
| `DELETE` | `/api/v1/arena/gx/{classInstancePublicId}/waitlist` | member | (authenticated member) | Leave the waitlist |
| `POST` | `/api/v1/arena/gx/{classInstancePublicId}/waitlist/accept` | member | (authenticated member) | Accept an offered spot — creates booking |
| `GET` | `/api/v1/pulse/gx/classes/{classInstancePublicId}/waitlist` | club staff | `gx-class:manage-bookings` | List all waitlist entries for a class |
| `DELETE` | `/api/v1/pulse/gx/classes/{classInstancePublicId}/waitlist/{entryPublicId}` | club staff | `gx-class:manage-bookings` | Staff removes a member from the waitlist |

---

## Request / Response shapes

### POST /arena/gx/{id}/waitlist → 201 Created

```json
{
  "entryId": "uuid",
  "position": 3,
  "status": "WAITING",
  "message": "You are #3 on the waitlist."
}
```

### GET /pulse/gx/classes/{id}/waitlist → 200 OK

```json
{
  "waitlistCount": 4,
  "entries": [
    {
      "entryId": "uuid",
      "position": 1,
      "status": "OFFERED",
      "memberName": "Ahmed Al-Rashidi",
      "memberPhone": "+966501234567",
      "notifiedAt": "2026-04-07T10:00:00Z",
      "offerExpiresAt": "2026-04-07T12:00:00Z"
    }
  ]
}
```

### POST /arena/gx/{id}/waitlist/accept → 200 OK

```json
{
  "bookingId": "uuid",
  "message": "Booking confirmed for Yoga — Monday 7 April at 09:00."
}
```

---

## Business rules — enforce in service layer

1. **Join waitlist**: only allowed when `GXClassInstance.currentBookingCount >= capacity`. If the class has space, return `409 Conflict` ("Class has available spots — book directly").
2. **Duplicate booking guard**: if member already has an active `GXBooking` for this class → `409 Conflict`.
3. **Duplicate waitlist guard**: if member already has a `WAITING` or `OFFERED` entry for this class → `409 Conflict`.
4. **Past class guard**: cannot join waitlist for a class whose `startAt` is in the past → `400 Bad Request`.
5. **Position assignment**: `SELECT COALESCE(MAX(position), 0) + 1 FROM gx_waitlist_entries WHERE class_instance_id = ? AND status IN ('WAITING', 'OFFERED')` — assigned at insert time inside a transaction. Use `SELECT ... FOR UPDATE` on the class instance row to prevent races.
6. **Spot promotion**: when a spot opens, find the entry with the lowest `position` where `status = 'WAITING'` for that class. Set its `status = OFFERED`, `notifiedAt = now()`. Send `GX_WAITLIST_OFFERED` notification.
7. **Accept**: only allowed when the caller's entry has `status = OFFERED` → `409 Conflict` otherwise. Creates a `GXBooking` atomically in the same transaction. Sets `entry.status = ACCEPTED`, `entry.acceptedBookingId = newBooking.id`. Sends `GX_WAITLIST_CONFIRMED` notification. Increments `GXClassInstance.currentBookingCount`.
8. **Accept race condition**: if the class is somehow full again at accept time (two members offered, both accept simultaneously) → the transaction that runs second will see `currentBookingCount >= capacity` and return `409 Conflict` ("Spot no longer available"). The second member's entry reverts to `WAITING` and they are re-queued.
9. **Offer expiry**: scheduler runs every hour. Finds entries where `status = OFFERED` AND `notified_at < now() - interval '2 hours'`. Sets `status = EXPIRED`. Immediately calls spot promotion for that class to offer the next person. Sends `GX_WAITLIST_EXPIRED` notification to the expired member.
10. **Booking cancelled**: when a `GXBooking` is cancelled (by member via arena or staff via pulse), call `GXWaitlistService.promoteNext(classInstanceId)`. Decrement `currentBookingCount` BEFORE calling promote so the spot is genuinely available.
11. **Staff removes from class**: same as booking cancellation — triggers `promoteNext`.
12. **Capacity increased**: when staff patches `GXClassInstance.capacity`, calculate new available spots = `newCapacity - currentBookingCount`. Call `promoteNext` once per newly available spot.
13. **Class cancelled**: set all `WAITING` and `OFFERED` entries for that class to `CANCELLED`. Send `GX_CLASS_CANCELLED` notification (existing type) to each affected member.
14. **Leave waitlist**: member can DELETE their own `WAITING` or `OFFERED` entry. If their entry was `OFFERED`, a spot is now wasted — call `promoteNext` immediately. Cannot leave if `status = ACCEPTED`.
15. **Staff removes from waitlist**: same effect as leave — if the removed entry was `OFFERED`, call `promoteNext`.

---

## Seed data updates

No new seed data needed. The existing GX class instances in dev will show "Join Waitlist" when at capacity.

---

## Frontend additions

### web-arena

**GX schedule / class detail** (`/gx/schedule`, `/gx/classes/{id}`):
- When `classInstance.availableSpots == 0` AND `classInstance.waitlistEnabled == true`: show "Join Waitlist" button instead of "Book"
- After joining: show "You are #N on the waitlist" badge + "Leave Waitlist" button
- When entry `status = OFFERED`: show amber banner "A spot is available! Accept by [deadline]" + "Accept Spot" CTA
- When entry `status = ACCEPTED`: show normal "Booked" state

**My Bookings screen** (`/gx/bookings`):
- Add a "Waitlist" tab alongside "Upcoming Bookings"
- Lists classes the member is waiting for, with position, status, and offer deadline if `OFFERED`

**New API calls (TanStack Query)**:
```ts
// POST /api/v1/arena/gx/{id}/waitlist
// DELETE /api/v1/arena/gx/{id}/waitlist
// POST /api/v1/arena/gx/{id}/waitlist/accept
```

**New i18n strings** (`ar.json` + `en.json`):
```
gx.waitlist.join
gx.waitlist.leave
gx.waitlist.position        // "You are #{{position}} on the waitlist"
gx.waitlist.offered_banner  // "A spot is available! Accept by {{deadline}}"
gx.waitlist.accept
gx.waitlist.accepted
gx.waitlist.tab
gx.waitlist.empty
gx.waitlist.class_full_join // shown when class is full and waitlist is available
```

### web-pulse

**GX schedule grid** (`/gx`):
- Add "Waitlist" count column: shows number of `WAITING` + `OFFERED` entries per class instance

**Class detail** (`/gx/classes/{id}`):
- New "Waitlist" tab (alongside Bookings tab)
- Table: position, member name, member phone, status badge, notified_at, offer expires at
- "Remove from Waitlist" button per row (calls staff DELETE endpoint)

**New i18n strings**:
```
gx.waitlist.count_column
gx.waitlist.tab_title
gx.waitlist.remove
gx.waitlist.remove_confirm
```

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/kotlin/com/liyaqa/gx/entity/GXWaitlistEntry.kt`
- `backend/src/main/kotlin/com/liyaqa/gx/entity/GXWaitlistStatus.kt`
- `backend/src/main/kotlin/com/liyaqa/gx/repository/GXWaitlistRepository.kt`
- `backend/src/main/kotlin/com/liyaqa/gx/service/GXWaitlistService.kt`
- `backend/src/main/kotlin/com/liyaqa/gx/scheduler/GXWaitlistScheduler.kt`
- `backend/src/main/kotlin/com/liyaqa/gx/dto/WaitlistJoinResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/gx/dto/WaitlistEntryResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/gx/dto/WaitlistListResponse.kt`
- `backend/src/main/resources/db/migration/V17__gx_waitlist.sql`
- `backend/src/test/kotlin/com/liyaqa/gx/service/GXWaitlistServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/gx/scheduler/GXWaitlistSchedulerTest.kt`
- `backend/src/test/kotlin/com/liyaqa/gx/controller/GXWaitlistControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-arena/src/api/gxWaitlist.ts`
- `apps/web-arena/src/components/gx/WaitlistBanner.tsx`
- `apps/web-arena/src/routes/gx/bookings.tsx` (new Waitlist tab — may be modify)
- `apps/web-arena/src/tests/gx-waitlist.test.tsx`
- `apps/web-pulse/src/components/gx/WaitlistTab.tsx`
- `apps/web-pulse/src/tests/gx-waitlist-pulse.test.tsx`

### Files to modify

- `backend/.../notification/model/NotificationType.kt` — 3 new types
- `backend/.../gx/controller/GxArenaController.kt` — hook booking cancellation → `promoteNext`
- `backend/.../gx/controller/GxPulseController.kt` — hook staff removal + capacity change + class cancel → waitlist service
- `apps/web-arena/src/routes/gx/schedule.tsx` — Join Waitlist button + offered banner
- `apps/web-arena/src/routes/gx/classes/$classId.tsx` — waitlist state display
- `apps/web-arena/src/locales/ar.json` + `en.json`
- `apps/web-pulse/src/routes/gx/index.tsx` — waitlist count column
- `apps/web-pulse/src/routes/gx/classes/$classId.tsx` — Waitlist tab
- `apps/web-pulse/src/locales/ar.json` + `en.json`

---

## Implementation order

### Step 1 — Flyway V17 + entities
- Write `V17__gx_waitlist.sql`
- Write `GXWaitlistEntry.kt` + `GXWaitlistStatus.kt`
- Write `GXWaitlistRepository.kt` (native queries — see Step 4)
- Verify: `./gradlew flywayMigrate`

### Step 2 — Notification types
- Add `GX_WAITLIST_OFFERED`, `GX_WAITLIST_EXPIRED`, `GX_WAITLIST_CONFIRMED` to `NotificationType.kt`
- Verify: `./gradlew compileKotlin`

### Step 3 — GXWaitlistService
Core methods:

```kotlin
// Join — validates business rules 1–5, assigns position, persists entry
fun joinWaitlist(classInstancePublicId: UUID, memberId: Long): WaitlistJoinResponse

// Accept — validates rule 6 (status = OFFERED), creates booking atomically
@Transactional
fun acceptOffer(classInstancePublicId: UUID, memberId: Long): GXBooking

// Leave — validates rule 14 (cannot leave ACCEPTED), calls promoteNext if was OFFERED
@Transactional
fun leaveWaitlist(classInstancePublicId: UUID, memberId: Long)

// Promote — finds lowest-position WAITING entry, sets OFFERED, fires notification
// Called by: booking cancel, staff removal, capacity increase, offer expiry
@Transactional
fun promoteNext(classInstanceId: Long)

// Cancel all — sets WAITING+OFFERED entries to CANCELLED, notifies each member
@Transactional
fun cancelAllForClass(classInstanceId: Long)
```

Verify: unit tests in `GXWaitlistServiceTest`

### Step 4 — Repository queries
All must use `nativeQuery = true`:

```kotlin
// Find the next WAITING entry (lowest position) for a class
@Query(value = """
    SELECT * FROM gx_waitlist_entries
    WHERE class_instance_id = :classInstanceId
      AND status = 'WAITING'
    ORDER BY position ASC
    LIMIT 1
""", nativeQuery = true)
fun findNextWaiting(classInstanceId: Long): GXWaitlistEntry?

// Find all WAITING + OFFERED entries for a class (for class cancellation)
@Query(value = """
    SELECT * FROM gx_waitlist_entries
    WHERE class_instance_id = :classInstanceId
      AND status IN ('WAITING', 'OFFERED')
""", nativeQuery = true)
fun findActiveEntriesForClass(classInstanceId: Long): List<GXWaitlistEntry>

// Find member's entry for a specific class (any status)
@Query(value = """
    SELECT * FROM gx_waitlist_entries
    WHERE class_instance_id = :classInstanceId
      AND member_id = :memberId
    LIMIT 1
""", nativeQuery = true)
fun findByClassAndMember(classInstanceId: Long, memberId: Long): GXWaitlistEntry?

// Find all entries for a member with WAITING or OFFERED status (for arena bookings tab)
@Query(value = """
    SELECT * FROM gx_waitlist_entries
    WHERE member_id = :memberId
      AND status IN ('WAITING', 'OFFERED')
    ORDER BY created_at DESC
""", nativeQuery = true)
fun findActiveEntriesForMember(memberId: Long): List<GXWaitlistEntry>

// Find expired OFFERED entries (for scheduler)
@Query(value = """
    SELECT * FROM gx_waitlist_entries
    WHERE status = 'OFFERED'
      AND notified_at < :threshold
""", nativeQuery = true)
fun findExpiredOffers(threshold: Instant): List<GXWaitlistEntry>

// Count WAITING + OFFERED entries for a class (for pulse grid column)
@Query(value = """
    SELECT COUNT(*) FROM gx_waitlist_entries
    WHERE class_instance_id = :classInstanceId
      AND status IN ('WAITING', 'OFFERED')
""", nativeQuery = true)
fun countActiveForClass(classInstanceId: Long): Long

// Compute next position for a class
@Query(value = """
    SELECT COALESCE(MAX(position), 0) + 1
    FROM gx_waitlist_entries
    WHERE class_instance_id = :classInstanceId
      AND status IN ('WAITING', 'OFFERED')
""", nativeQuery = true)
fun nextPosition(classInstanceId: Long): Int
```

### Step 5 — GXWaitlistScheduler
```kotlin
@Component
class GXWaitlistScheduler(
    private val waitlistRepository: GXWaitlistRepository,
    private val waitlistService: GXWaitlistService
) {
    // Every hour: find OFFERED entries older than 2 hours, expire them, promote next
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    fun expireStaleOffers() {
        val threshold = Instant.now().minus(2, ChronoUnit.HOURS)
        val expired = waitlistRepository.findExpiredOffers(threshold)
        expired.forEach { entry ->
            entry.status = GXWaitlistStatus.EXPIRED
            waitlistRepository.save(entry)
            // Send expiry notification to member
            notificationService.create(
                userId = entry.memberId,  // resolve to userId
                type = "GX_WAITLIST_EXPIRED",
                ...
            )
            // Immediately promote the next person
            waitlistService.promoteNext(entry.classInstanceId)
        }
    }
}
```

### Step 6 — Hook spot-opening triggers

**In `GxArenaController.cancelBooking()`:**
- After setting booking status to cancelled and decrementing `currentBookingCount` → call `waitlistService.promoteNext(classInstance.id)`

**In `GxPulseController`:**
- `removeBooking()` (staff removes member) → call `waitlistService.promoteNext(classInstance.id)`
- `updateClassInstance()` (capacity change) → calculate `newSpots = newCapacity - currentBookingCount`; call `waitlistService.promoteNext(classInstance.id)` once per new spot
- `cancelClassInstance()` → call `waitlistService.cancelAllForClass(classInstance.id)`

### Step 7 — Controllers

**`GxArenaController`** — add 3 new endpoints:
- `POST /arena/gx/{id}/waitlist` → `waitlistService.joinWaitlist()`
- `DELETE /arena/gx/{id}/waitlist` → `waitlistService.leaveWaitlist()`
- `POST /arena/gx/{id}/waitlist/accept` → `waitlistService.acceptOffer()`

**`GxPulseController`** — add 2 new endpoints:
- `GET /pulse/gx/classes/{id}/waitlist` → list entries for staff
- `DELETE /pulse/gx/classes/{id}/waitlist/{entryId}` → staff removes entry

No new controller files — these endpoints go into the existing controllers.

### Step 8 — Frontend

**web-arena:**
- `gxWaitlist.ts` — 3 API functions
- `WaitlistBanner.tsx` — amber offered-spot banner with countdown + Accept button
- Modify GX schedule + class detail: "Join Waitlist" button state when full, waitlist position badge
- Modify bookings screen: add Waitlist tab with active entries

**web-pulse:**
- `WaitlistTab.tsx` — table of entries with status badges + remove button
- Modify GX schedule grid: waitlist count column
- Modify class detail: add Waitlist tab

Add all i18n strings.

### Step 9 — Tests

**Unit: `GXWaitlistServiceTest`**
- `joinWaitlist returns position 1 when no one else is waiting`
- `joinWaitlist returns position 2 when one person is already waiting`
- `joinWaitlist throws 409 when class has available spots`
- `joinWaitlist throws 409 when member already has active booking`
- `joinWaitlist throws 409 when member already on waitlist`
- `joinWaitlist throws 400 when class is in the past`
- `promoteNext sets first WAITING entry to OFFERED and sends notification`
- `promoteNext does nothing when no WAITING entries exist`
- `acceptOffer creates booking and sets entry to ACCEPTED`
- `acceptOffer throws 409 when entry is not OFFERED`
- `acceptOffer throws 409 when class is full at accept time (race condition)`
- `leaveWaitlist removes WAITING entry cleanly`
- `leaveWaitlist calls promoteNext when leaving an OFFERED entry`
- `leaveWaitlist throws 409 when entry is ACCEPTED`
- `cancelAllForClass sets all active entries to CANCELLED and notifies members`

**Unit: `GXWaitlistSchedulerTest`**
- `expireStaleOffers sets OFFERED entries older than 2 hours to EXPIRED`
- `expireStaleOffers calls promoteNext for each expired entry`
- `expireStaleOffers skips OFFERED entries within the 2-hour window`
- `expireStaleOffers does nothing when no expired entries exist`

**Integration: `GXWaitlistControllerIntegrationTest`**
- `POST /arena/gx/{id}/waitlist returns 201 with correct position`
- `POST /arena/gx/{id}/waitlist returns 409 when class has available spots`
- `POST /arena/gx/{id}/waitlist returns 409 when already on waitlist`
- `DELETE /arena/gx/{id}/waitlist removes WAITING entry`
- `POST /arena/gx/{id}/waitlist/accept creates booking when entry is OFFERED`
- `POST /arena/gx/{id}/waitlist/accept returns 409 when entry is WAITING not OFFERED`
- `GET /pulse/gx/classes/{id}/waitlist returns list of entries for staff`
- `GET /pulse/gx/classes/{id}/waitlist returns 403 without gx-class:manage-bookings`
- `DELETE /pulse/gx/classes/{id}/waitlist/{entryId} removes entry`
- `cancelling booking promotes next WAITING member to OFFERED`

**Frontend:**
- `gx-waitlist.test.tsx` (arena):
  - shows "Join Waitlist" button when class is full
  - does not show "Join Waitlist" when class has spots
  - shows position badge after joining
  - shows amber offered banner when status is OFFERED
  - Accept button calls accept endpoint and invalidates query
- `gx-waitlist-pulse.test.tsx` (pulse):
  - renders waitlist tab with entries
  - Remove button calls staff remove endpoint

---

## RBAC matrix rows added by this plan

No new permissions. Waitlist management uses existing `gx-class:manage-bookings`.

---

## Definition of Done

- [ ] Flyway V17 runs cleanly: `gx_waitlist_entries` table created with `uq_waitlist_member_class` constraint
- [ ] `GXWaitlistEntry` entity + `GXWaitlistStatus` enum compile
- [ ] 3 new notification types added: `GX_WAITLIST_OFFERED`, `GX_WAITLIST_EXPIRED`, `GX_WAITLIST_CONFIRMED`
- [ ] `joinWaitlist` enforces all 5 guards (class full, not past, not already booked, not already waiting, position race-safe)
- [ ] `acceptOffer` is atomic: booking + entry update in single `@Transactional`
- [ ] `acceptOffer` handles race condition: 409 if class full at accept time
- [ ] `promoteNext` triggered by: booking cancellation, staff removal, capacity increase
- [ ] `cancelAllForClass` triggered by class cancellation, notifies all affected members
- [ ] `GXWaitlistScheduler` expires stale OFFERED entries hourly and immediately promotes next
- [ ] All repository queries use `nativeQuery = true`
- [ ] 5 endpoints live: 3 arena + 2 pulse, all with `@Operation`
- [ ] web-arena: "Join Waitlist" / position badge / offered banner / Accept button all render correctly
- [ ] web-arena: Waitlist tab on bookings screen shows active entries
- [ ] web-pulse: waitlist count column on GX schedule grid
- [ ] web-pulse: Waitlist tab on class detail with Remove button
- [ ] All i18n strings added in Arabic and English (web-arena + web-pulse)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors in web-arena or web-pulse
- [ ] `PROJECT-STATE.md` updated: Plan 25 complete, test counts, V17 noted
- [ ] `PLAN-25-gx-waitlist.md` deleted before merging

When all items are checked, confirm: **"Plan 25 — GX Class Waitlist complete. X backend tests, Y frontend tests."**
