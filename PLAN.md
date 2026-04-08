# Plan 32 — Member Notes & Activity Timeline

## Status
Ready for implementation

## Branch
`feature/plan-32-member-notes`

## Goal
Extend the existing `MemberNote` entity (created in Plan 22 for rejection reasons) with four general-purpose note types and a combined activity timeline on each member profile. A `/follow-ups` page gives gated staff a daily view of members with pending follow-up notes due within 7 days. Trainers can read and create notes in web-coach. The activity timeline combines notes, membership events, and payments in a single newest-first view.

## Context
- `MemberNote` entity already exists from Plan 22 with fields: `id`, `memberId`, `content`, `createdByUserId`, `createdAt`, `deletedAt`
- Plan 22 used it for rejection reasons only — `noteType` column does NOT exist yet
- `followUpAt` column does NOT exist yet
- Notification scheduler (`NotificationSchedulerService`) already runs daily at 06:00 UTC — extend it
- `AuditService.log()` already established — wire new actions in
- Trainers have web-coach with their own JWT scope
- Next Flyway migration: **V18** (this plan needs a small migration to add columns to `member_notes`)

---

## Scope — what this plan covers

- [ ] Flyway V18 — add `note_type`, `follow_up_at`, `created_by_user_id` (if missing) to `member_notes`
- [ ] Extend `MemberNote` entity with `noteType` and `followUpAt` fields
- [ ] `MemberNoteService` — create, delete, list (with timeline merge)
- [ ] Extend `NotificationSchedulerService` — fire `FOLLOW_UP_DUE` for notes due today
- [ ] New notification type: `FOLLOW_UP_DUE`
- [ ] New audit actions: `MEMBER_NOTE_ADDED`, `MEMBER_NOTE_DELETED`
- [ ] New permissions: `member-note:create`, `member-note:read`, `member-note:delete`, `member-note:follow-up:read`
- [ ] 5 endpoints (3 pulse, 2 coach)
- [ ] web-pulse: Notes tab on member profile — combined activity timeline
- [ ] web-pulse: `/follow-ups` page — follow-up notes due within 7 days
- [ ] web-coach: Notes tab on member profile (read + create)
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- Editing notes (append-only by design)
- Pinning notes
- Email notifications for `FOLLOW_UP_DUE` (bell + drawer only)
- Note attachments or images
- Note visibility restrictions by type (all staff with `member-note:read` see all types including health)
- Member-facing notes (notes are staff/trainer-only)

---

## Decisions already made

- **Append-only** — no edit endpoint; soft delete only (`deleted_at = now()`)
- **5 note types**: `general`, `health`, `complaint`, `follow_up`, `rejection` (rejection already used by Plan 22 — preserve it)
- **`followUpAt`**: nullable; only meaningful on `follow_up` type notes; ignored on other types
- **Timeline**: combined view of notes + membership events (JOINED, RENEWED, FROZEN, TERMINATED) + payments (COLLECTED); sorted newest first; assembled in service layer from three separate native queries, merged and sorted in Kotlin
- **Trainer access**: full — read and create notes in web-coach; trainers should use `general` and `health` types only (enforced by validation: trainers cannot create `complaint` or `follow_up` notes)
- **`/follow-ups` page**: gated by `member-note:follow-up:read` permission; shows all notes where `follow_up_at` is between today and today + 7 days, ordered by `follow_up_at ASC`
- **`FOLLOW_UP_DUE` notification**: fired daily by scheduler for notes where `follow_up_at = today`; sent to the user who created the note (`created_by_user_id`)
- **Flyway V18** — migration needed (adds columns to `member_notes`)

---

## Entity design

### MemberNote (extended)

The entity already exists. Add these fields:

```kotlin
// Add to existing MemberNote entity

@Enumerated(EnumType.STRING)
@Column(name = "note_type", nullable = false)
var noteType: MemberNoteType = MemberNoteType.GENERAL

@Column(name = "follow_up_at")
var followUpAt: Instant? = null
```

```kotlin
enum class MemberNoteType {
    GENERAL,
    HEALTH,
    COMPLAINT,
    FOLLOW_UP,
    REJECTION   // existing — used by Plan 22 activation flow
}
```

No new entity. No new table.

---

## Flyway V18

```sql
-- V18__member_notes_extend.sql

ALTER TABLE member_notes
    ADD COLUMN IF NOT EXISTS note_type  VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN IF NOT EXISTS follow_up_at TIMESTAMPTZ;

-- Backfill: all existing notes created by Plan 22 are rejection notes
UPDATE member_notes SET note_type = 'REJECTION' WHERE note_type = 'GENERAL' AND content LIKE '%rejection%';

CREATE INDEX idx_member_notes_follow_up_at ON member_notes(follow_up_at)
    WHERE follow_up_at IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_member_notes_member_type ON member_notes(member_id, note_type)
    WHERE deleted_at IS NULL;
```

> Note: If `created_by_user_id` column was already added by Plan 22, do NOT re-add it. Check the existing schema before writing the migration. Use `ADD COLUMN IF NOT EXISTS` for safety.

---

## Activity Timeline — assembled in service layer

The timeline is a unified list of events for a single member, newest first. Three sources:

| Source | Event types included |
|--------|---------------------|
| `member_notes` | All non-deleted notes for this member |
| `memberships` | Created (JOINED), renewed (RENEWED), frozen (FROZEN), terminated (TERMINATED) — use `status` transitions + `created_at` / `updatedAt` — or use `audit_logs` filtered by `entity_type = 'Membership'` and `entity_id` |
| `payments` | All payments for this member — use `payment_date` or `created_at` |

**Assembly strategy**: three separate native queries → three lists → merge into a single `List<TimelineEvent>` sealed class in Kotlin → sort by `eventAt DESC` → paginate (20 items per page, cursor-based using `eventAt + id`).

```kotlin
sealed class TimelineEvent {
    abstract val eventAt: Instant
    abstract val eventType: String

    data class NoteEvent(
        override val eventAt: Instant,
        override val eventType: String,  // "NOTE_GENERAL", "NOTE_HEALTH", etc.
        val noteId: UUID,
        val content: String,
        val noteType: String,
        val followUpAt: Instant?,
        val createdByName: String,
        val canDelete: Boolean  // true if current user is the author or has manager role
    ) : TimelineEvent()

    data class MembershipEvent(
        override val eventAt: Instant,
        override val eventType: String,  // "MEMBERSHIP_JOINED", "MEMBERSHIP_RENEWED", etc.
        val membershipId: UUID,
        val planName: String,
        val detail: String  // e.g. "Frozen until 15 Apr 2026"
    ) : TimelineEvent()

    data class PaymentEvent(
        override val eventAt: Instant,
        override val eventType: String,  // "PAYMENT_COLLECTED"
        val paymentId: UUID,
        val amountSar: String,
        val method: String
    ) : TimelineEvent()
}
```

---

## Notification type

Add to `NotificationType.kt`:

```kotlin
FOLLOW_UP_DUE,  // member follow-up note is due today — sent to note creator
```

Body strings:

| Lang | Body |
|------|------|
| EN | "Follow-up due today for {memberName}: {noteContent truncated to 80 chars}" |
| AR | "متابعة مستحقة اليوم لـ {memberName}: {noteContent}" |

---

## API endpoints

| Method | Path | Scope | Permission | Description |
|--------|------|-------|------------|-------------|
| `POST` | `/api/v1/pulse/members/{memberPublicId}/notes` | club staff | `member-note:create` | Create a new note |
| `DELETE` | `/api/v1/pulse/members/{memberPublicId}/notes/{notePublicId}` | club staff | `member-note:delete` | Soft-delete a note |
| `GET` | `/api/v1/pulse/members/{memberPublicId}/timeline` | club staff | `member-note:read` | Combined activity timeline (paginated) |
| `GET` | `/api/v1/pulse/follow-ups` | club staff | `member-note:follow-up:read` | Follow-up notes due within 7 days |
| `POST` | `/api/v1/coach/members/{memberPublicId}/notes` | trainer | (authenticated trainer) | Trainer creates a note — types limited to `general` and `health` |
| `GET` | `/api/v1/coach/members/{memberPublicId}/notes` | trainer | (authenticated trainer) | Trainer reads notes for a member they train |

---

## Request / Response shapes

### POST /pulse/members/{id}/notes

Request:
```json
{
  "noteType": "follow_up",
  "content": "Member mentioned knee pain — check with trainer before renewing PT package.",
  "followUpAt": "2026-04-14"
}
```

Response `201 Created`:
```json
{
  "noteId": "uuid",
  "noteType": "follow_up",
  "content": "Member mentioned knee pain...",
  "followUpAt": "2026-04-14T00:00:00Z",
  "createdByName": "Ahmed Al-Mansoori",
  "createdAt": "2026-04-07T09:30:00Z"
}
```

### GET /pulse/members/{id}/timeline

```json
{
  "events": [
    {
      "eventType": "NOTE_FOLLOW_UP",
      "eventAt": "2026-04-07T09:30:00Z",
      "noteId": "uuid",
      "content": "Member mentioned knee pain...",
      "followUpAt": "2026-04-14T00:00:00Z",
      "createdByName": "Ahmed Al-Mansoori",
      "canDelete": true
    },
    {
      "eventType": "PAYMENT_COLLECTED",
      "eventAt": "2026-04-01T10:00:00Z",
      "paymentId": "uuid",
      "amountSar": "150.00",
      "method": "cash"
    },
    {
      "eventType": "MEMBERSHIP_JOINED",
      "eventAt": "2026-03-01T08:00:00Z",
      "membershipId": "uuid",
      "planName": "Basic Monthly",
      "detail": "Joined on Basic Monthly plan"
    }
  ],
  "nextCursor": "2026-03-01T08:00:00Z_123"
}
```

### GET /pulse/follow-ups

```json
{
  "followUps": [
    {
      "noteId": "uuid",
      "followUpAt": "2026-04-07T00:00:00Z",
      "content": "Member mentioned knee pain...",
      "memberName": "Sarah Al-Zahrani",
      "memberPublicId": "uuid",
      "createdByName": "Ahmed Al-Mansoori",
      "daysUntilDue": 0
    }
  ]
}
```

---

## Business rules — enforce in service layer

1. **Create — type validation**: `noteType` must be one of `general`, `health`, `complaint`, `follow_up`. Trainers (coach scope) may only use `general` or `health` — attempting `complaint` or `follow_up` returns `403 Forbidden`.
2. **Create — content required**: `content` must be 1–1000 characters → `400 Bad Request` if empty or over limit.
3. **Create — `followUpAt` guard**: `followUpAt` is only accepted on `follow_up` type notes. If provided on any other type, return `400 Bad Request`. If provided, must be a future date.
4. **Delete — soft delete only**: sets `deletedAt = Instant.now()`. Never hard deletes.
5. **Delete — author or manager only**: only the user who created the note OR a user with `member-note:delete` permission who has a role with `isManager = true` (Owner / Branch Manager) may delete. All others → `403 Forbidden`.
6. **Delete — REJECTION notes**: notes with `noteType = REJECTION` cannot be deleted — return `409 Conflict` ("Rejection notes cannot be deleted").
7. **Timeline — club scoping**: timeline queries are scoped to the current user's `clubId` from the JWT. Staff cannot view timelines of members in other clubs.
8. **Trainer — member scoping**: trainer can only view/create notes for members who have at least one active `PTPackage` or `GXBooking` associated with this trainer. Attempting to access another member → `403 Forbidden`.
9. **Follow-ups page**: returns notes where `follow_up_at BETWEEN now() AND now() + 7 days` AND `deleted_at IS NULL`, ordered by `follow_up_at ASC`. Scoped to the current user's club.
10. **`FOLLOW_UP_DUE` notification**: scheduler fires daily; finds notes where `DATE(follow_up_at) = today` AND `deleted_at IS NULL`; sends notification to `created_by_user_id` (resolved to their `userId`). Deduplication: check last 24h for same `(type, entityId = noteId)` before creating.

---

## Seed data updates

No new seed data needed. The dev member (Ahmed Al-Rashidi) will show an empty timeline until notes are created manually.

---

## Frontend additions

### web-pulse

**Member profile — new "Notes & Timeline" tab** (replace or add alongside existing tabs):
- Combined timeline view: newest first, 20 items per scroll page (infinite scroll)
- Event type icons: note icon (general=grey, health=red, complaint=amber, follow_up=blue), membership icon (green), payment icon (teal)
- "Add Note" button → slide-in form: type selector (radio), text area (max 1000 chars), optional date picker (follow_up type only)
- Each note row: type badge, content, author name, created-at (relative: "2 hours ago"), delete icon (shown only if `canDelete = true`)
- Delete: confirmation toast before calling DELETE endpoint

**`/follow-ups` page** (new route in sidebar, visible only with `member-note:follow-up:read`):
- Table: Due Date, Member Name (link to profile), Note Content (truncated 80 chars), Created By, Days Until Due
- Color coding: due today = red row, due tomorrow = amber, later = default
- No pagination needed for MVP — fetch all (API returns up to 200)
- Sidebar nav item: "Follow-ups" with a count badge of items due today

**New i18n strings** (`ar.json` + `en.json`):
```
notes.tab_title
notes.add_button
notes.type.general
notes.type.health
notes.type.complaint
notes.type.follow_up
notes.type.rejection
notes.follow_up_date
notes.content_placeholder
notes.delete_confirm
notes.empty
timeline.payment_collected
timeline.membership_joined
timeline.membership_renewed
timeline.membership_frozen
timeline.membership_terminated
followups.page_title
followups.due_today
followups.due_tomorrow
followups.days_until_due
followups.empty
followups.nav_badge
```

### web-coach

**Member detail — new "Notes" tab**:
- List of notes for this member (read: all types visible, no distinction)
- "Add Note" button → form with type limited to `general` and `health` only
- No delete button (trainers cannot delete notes)
- No timeline — notes list only (not merged with membership/payment events)

**New i18n strings** (coach `ar.json` + `en.json`):
```
notes.tab_title
notes.add_button
notes.type.general
notes.type.health
notes.empty
```

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/kotlin/com/liyaqa/member/entity/MemberNoteType.kt`
- `backend/src/main/kotlin/com/liyaqa/member/service/MemberNoteService.kt`
- `backend/src/main/kotlin/com/liyaqa/member/dto/CreateNoteRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/member/dto/NoteResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/member/dto/TimelineEvent.kt` (sealed class)
- `backend/src/main/kotlin/com/liyaqa/member/dto/TimelineResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/member/dto/FollowUpResponse.kt`
- `backend/src/main/resources/db/migration/V18__member_notes_extend.sql`
- `backend/src/test/kotlin/com/liyaqa/member/service/MemberNoteServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/member/controller/MemberNoteControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-pulse/src/components/members/NotesTimeline.tsx`
- `apps/web-pulse/src/components/members/AddNoteForm.tsx`
- `apps/web-pulse/src/routes/follow-ups/index.tsx`
- `apps/web-pulse/src/api/memberNotes.ts`
- `apps/web-coach/src/components/members/MemberNotes.tsx`
- `apps/web-coach/src/api/memberNotes.ts`
- `apps/web-pulse/src/tests/notes-timeline.test.tsx`
- `apps/web-coach/src/tests/member-notes-coach.test.tsx`

### Files to modify

- `backend/.../member/entity/MemberNote.kt` — add `noteType`, `followUpAt` fields
- `backend/.../member/repository/MemberNoteRepository.kt` — add native queries
- `backend/.../notification/model/NotificationType.kt` — add `FOLLOW_UP_DUE`
- `backend/.../notification/service/NotificationSchedulerService.kt` — add `FOLLOW_UP_DUE` daily check
- `backend/.../audit/model/AuditAction.kt` — add `MEMBER_NOTE_ADDED`, `MEMBER_NOTE_DELETED`
- `backend/.../permission/PermissionConstants.kt` — add 4 new permission codes
- `backend/DevDataLoader.kt` — seed new permissions to appropriate roles
- `backend/.../member/controller/MemberPulseController.kt` (or new `MemberNotePulseController.kt`) — add 3 pulse endpoints
- `backend/.../coach/controller/CoachMemberController.kt` (or new) — add 2 coach endpoints
- `apps/web-pulse/src/routes/members/$memberId.tsx` — add Notes & Timeline tab
- `apps/web-pulse/src/locales/ar.json` + `en.json`
- `apps/web-coach/src/routes/members/$memberId.tsx` — add Notes tab
- `apps/web-coach/src/locales/ar.json` + `en.json`

---

## Repository queries

All must use `nativeQuery = true`:

```kotlin
// Notes for a member (non-deleted, newest first, paginated)
@Query(value = """
    SELECT * FROM member_notes
    WHERE member_id = :memberId
      AND deleted_at IS NULL
    ORDER BY created_at DESC
    LIMIT :limit OFFSET :offset
""", nativeQuery = true)
fun findByMemberId(memberId: Long, limit: Int, offset: Int): List<MemberNote>

// Follow-up notes due within N days for a club (via member → membership → club join)
@Query(value = """
    SELECT mn.* FROM member_notes mn
    JOIN members m ON m.id = mn.member_id
    WHERE mn.follow_up_at BETWEEN :from AND :to
      AND mn.deleted_at IS NULL
      AND m.club_id = :clubId
    ORDER BY mn.follow_up_at ASC
    LIMIT 200
""", nativeQuery = true)
fun findFollowUpsDueWithin(clubId: Long, from: Instant, to: Instant): List<MemberNote>

// Follow-up notes due exactly today (for scheduler — across all clubs)
@Query(value = """
    SELECT * FROM member_notes
    WHERE DATE(follow_up_at AT TIME ZONE 'Asia/Riyadh') = :today
      AND deleted_at IS NULL
      AND note_type = 'FOLLOW_UP'
""", nativeQuery = true)
fun findFollowUpsDueToday(today: java.time.LocalDate): List<MemberNote>

// Find specific note by publicId
@Query(value = """
    SELECT * FROM member_notes
    WHERE public_id = :publicId
      AND deleted_at IS NULL
""", nativeQuery = true)
fun findByPublicId(publicId: UUID): MemberNote?
```

Add to `PaymentRepository`:
```kotlin
// Payments for a member (for timeline)
@Query(value = """
    SELECT id, public_id, amount_halalas, payment_method, payment_date, created_at
    FROM payments
    WHERE member_id = :memberId
      AND deleted_at IS NULL
    ORDER BY payment_date DESC
    LIMIT :limit
""", nativeQuery = true)
fun findByMemberIdForTimeline(memberId: Long, limit: Int): List<PaymentTimelineProjection>
```

Add to `MembershipRepository`:
```kotlin
// Membership events for a member (for timeline)
@Query(value = """
    SELECT id, public_id, status, start_date, end_date, created_at, updated_at,
           mp.name_en AS plan_name_en, mp.name_ar AS plan_name_ar
    FROM memberships ms
    JOIN membership_plans mp ON mp.id = ms.membership_plan_id
    WHERE ms.member_id = :memberId
      AND ms.deleted_at IS NULL
    ORDER BY ms.created_at DESC
    LIMIT :limit
""", nativeQuery = true)
fun findByMemberIdForTimeline(memberId: Long, limit: Int): List<MembershipTimelineProjection>
```

---

## Implementation order

### Step 1 — Flyway V18 + entity extension
- Write `V18__member_notes_extend.sql` (check existing columns first)
- Add `noteType: MemberNoteType` and `followUpAt: Instant?` to `MemberNote.kt`
- Write `MemberNoteType.kt` enum
- Verify: `./gradlew flywayMigrate`

### Step 2 — Permissions + audit actions + notification type
- Add `MEMBER_NOTE_CREATE`, `MEMBER_NOTE_READ`, `MEMBER_NOTE_DELETE`, `MEMBER_NOTE_FOLLOW_UP_READ` to `PermissionConstants.kt`
- Add `MEMBER_NOTE_ADDED`, `MEMBER_NOTE_DELETED` to `AuditAction.kt`
- Add `FOLLOW_UP_DUE` to `NotificationType.kt`
- Seed permissions to roles in `DevDataLoader`:
  - `member-note:create` → Owner, Branch Manager, Receptionist, Sales Agent + all trainer roles
  - `member-note:read` → Owner, Branch Manager, Receptionist, Sales Agent + all trainer roles
  - `member-note:delete` → Owner, Branch Manager, Receptionist, Sales Agent
  - `member-note:follow-up:read` → Owner, Branch Manager, Receptionist, Sales Agent
- Verify: `./gradlew compileKotlin`

### Step 3 — Repository queries
- Add 4 queries to `MemberNoteRepository`
- Add `PaymentTimelineProjection` interface + query to `PaymentRepository`
- Add `MembershipTimelineProjection` interface + query to `MembershipRepository`
- Verify: `./gradlew compileKotlin`

### Step 4 — MemberNoteService
Implement:
- `createNote(memberPublicId, request, actorUserId, actorScope)` — enforces rules 1–3, saves, logs audit
- `deleteNote(notePublicId, actorUserId, actorRole)` — enforces rules 4–6, soft-deletes, logs audit
- `getTimeline(memberPublicId, clubId, cursor, limit)` — fetches 3 sources, merges, sorts, paginates
- `getFollowUps(clubId)` — fetches follow-up notes due within 7 days
- Verify: unit tests in `MemberNoteServiceTest`

### Step 5 — Extend NotificationSchedulerService
Add to the existing daily 06:00 UTC scheduler method:
```kotlin
// Fire FOLLOW_UP_DUE for notes due today
val today = LocalDate.now(ZoneId.of("Asia/Riyadh"))
val dueNotes = memberNoteRepository.findFollowUpsDueToday(today)
dueNotes.forEach { note ->
    // deduplication check: last 24h for same (FOLLOW_UP_DUE, noteId)
    notificationService.createIfNotDuplicate(
        userId = resolveUserId(note.createdByUserId),
        type = "FOLLOW_UP_DUE",
        entityId = note.publicId.toString(),
        entityType = "MemberNote",
        titleEn = "Follow-up Due Today",
        titleAr = "متابعة مستحقة اليوم",
        bodyEn = "Follow-up for ${resolveMemberName(note.memberId)}: ${note.content.take(80)}",
        bodyAr = "متابعة لـ ${resolveMemberName(note.memberId)}: ${note.content.take(80)}"
    )
}
```

### Step 6 — Controllers

**`MemberNotePulseController`** (or add to existing `MemberPulseController`):
- `POST /pulse/members/{id}/notes` → `memberNoteService.createNote(...)`
- `DELETE /pulse/members/{id}/notes/{noteId}` → `memberNoteService.deleteNote(...)`
- `GET /pulse/members/{id}/timeline` → `memberNoteService.getTimeline(...)`
- `GET /pulse/follow-ups` → `memberNoteService.getFollowUps(clubId)`

**Coach controller** (add to existing or new `CoachMemberController`):
- `POST /coach/members/{id}/notes` → `memberNoteService.createNote(...)` with scope=trainer, type restriction
- `GET /coach/members/{id}/notes` → notes-only list (no timeline merge), scoped to trainer's members

### Step 7 — Frontend: web-pulse
- `NotesTimeline.tsx` — infinite scroll timeline with event type icons, add note button, delete with confirmation
- `AddNoteForm.tsx` — type selector, text area, date picker (follow_up only)
- `/follow-ups` route — table with color-coded rows, count badge on sidebar nav
- `memberNotes.ts` — 4 API functions (create, delete, timeline, follow-ups)
- Add Notes & Timeline tab to member profile page
- Add i18n strings

### Step 8 — Frontend: web-coach
- `MemberNotes.tsx` — notes list + add form (types: general, health only)
- `memberNotes.ts` — 2 API functions (create, list)
- Add Notes tab to coach member detail page
- Add i18n strings

### Step 9 — Tests

**Unit: `MemberNoteServiceTest`**
- `createNote saves note with correct type and content`
- `createNote throws 400 when content is empty`
- `createNote throws 400 when content exceeds 1000 characters`
- `createNote throws 400 when followUpAt is in the past`
- `createNote throws 400 when followUpAt is provided on non-follow_up type`
- `createNote throws 403 when trainer attempts to create complaint note`
- `createNote throws 403 when trainer attempts to create follow_up note`
- `deleteNote soft-deletes the note`
- `deleteNote throws 403 when non-author without manager role attempts delete`
- `deleteNote throws 409 when attempting to delete a REJECTION note`
- `getTimeline returns merged and sorted events from all three sources`
- `getTimeline returns empty list when member has no events`
- `getFollowUps returns notes due within 7 days for the club`
- `getFollowUps returns empty list when no follow-up notes are due`

**Integration: `MemberNoteControllerIntegrationTest`**
- `POST /pulse/members/{id}/notes returns 201 with note details`
- `POST /pulse/members/{id}/notes returns 400 when content is empty`
- `POST /pulse/members/{id}/notes returns 403 without member-note:create permission`
- `DELETE /pulse/members/{id}/notes/{noteId} returns 204 and soft-deletes`
- `DELETE /pulse/members/{id}/notes/{noteId} returns 409 for REJECTION note`
- `GET /pulse/members/{id}/timeline returns combined events sorted by date`
- `GET /pulse/follow-ups returns 200 with notes due within 7 days`
- `GET /pulse/follow-ups returns 403 without member-note:follow-up:read permission`
- `POST /coach/members/{id}/notes returns 201 for general type`
- `POST /coach/members/{id}/notes returns 403 for complaint type from trainer`

**Frontend:**
- `notes-timeline.test.tsx` (pulse):
  - renders note events in timeline
  - renders membership and payment events in timeline
  - delete icon visible on own notes
  - delete icon hidden on others' notes
  - add note form shows date picker only for follow_up type
  - follow-ups page renders due notes with color coding
- `member-notes-coach.test.tsx` (coach):
  - renders notes list for member
  - add form shows only general and health type options

---

## RBAC matrix rows added by this plan

| Permission | Owner | Branch Mgr | Receptionist | Sales Agent | PT Trainer | GX Instructor |
|------------|-------|------------|--------------|-------------|------------|---------------|
| `member-note:create` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `member-note:read` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `member-note:delete` | ✅ | ✅ | ✅ | ✅ | — | — |
| `member-note:follow-up:read` | ✅ | ✅ | ✅ | ✅ | — | — |

---

## Definition of Done

- [ ] Flyway V18 runs cleanly: `note_type` and `follow_up_at` columns added to `member_notes`
- [ ] `MemberNote.noteType` and `MemberNote.followUpAt` fields compile
- [ ] `MemberNoteType` enum has 5 values including `REJECTION`
- [ ] 4 new permissions seeded to correct roles
- [ ] `MEMBER_NOTE_ADDED` and `MEMBER_NOTE_DELETED` audit actions wired
- [ ] `FOLLOW_UP_DUE` notification type added and fired by daily scheduler
- [ ] All 10 business rules enforced in `MemberNoteService`
- [ ] Trainer type restriction: `complaint` and `follow_up` return 403 from coach scope
- [ ] `REJECTION` notes cannot be deleted — return 409
- [ ] Timeline merges notes + membership events + payments, sorted newest first
- [ ] Follow-ups query scoped to club, returns within 7 days, ordered by `follow_up_at ASC`
- [ ] All repository queries use `nativeQuery = true`
- [ ] 6 endpoints live: 4 pulse + 2 coach, all with `@Operation`
- [ ] web-pulse: Notes & Timeline tab on member profile with add/delete
- [ ] web-pulse: `/follow-ups` page with color-coded rows and count badge
- [ ] web-coach: Notes tab with add form restricted to `general` and `health`
- [ ] All i18n strings added in Arabic and English (web-pulse + web-coach)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors in web-pulse or web-coach
- [ ] `PROJECT-STATE.md` updated: Plan 32 complete, test counts, V18 noted
- [ ] `PLAN-32-member-notes.md` deleted before merging

When all items are checked, confirm: **"Plan 32 — Member Notes & Activity Timeline complete. X backend tests, Y frontend tests."**
