# Plan 33 — Membership Lapse Recovery

## Status
Ready for implementation

## Branch
`feature/plan-33-lapse-recovery`

## Goal
Automatically detect and mark members whose memberships have expired, transition them to a new `lapsed` status, restrict their club access (no QR, no class booking) while still allowing web-arena login, and give staff a focused recovery screen to send renewal offers and re-engage lapsed members at scale.

## Context
- `MemberStatus` enum already exists: `active`, `pending_activation`, `inactive`, `terminated`
- `Membership.status` already has: `active`, `pending_payment`, `frozen`, `terminated`
- `NotificationSchedulerService` already runs daily at 06:00 UTC — extend it
- `MemberNoteService` (Plan 32) already exists — "Send Renewal Offer" creates a `follow_up` note
- `AuditService.log()` already established
- `MEMBERSHIP_EXPIRING_SOON` notification already fires 7 days before expiry — this plan handles what happens after
- No Flyway migration — adds a new enum value to `MemberStatus` (handled in Kotlin enum + VARCHAR column)

---

## Scope — what this plan covers

- [ ] Add `LAPSED` to `MemberStatus` enum
- [ ] `MemberLapseService` — lapse detection, lapse transition, re-activation on new membership
- [ ] Extend `NotificationSchedulerService` — daily lapse scheduler at 07:00 Riyadh (04:00 UTC)
- [ ] New notification type: `MEMBERSHIP_LAPSED`
- [ ] New audit actions: `MEMBERSHIP_LAPSED`, `MEMBER_REACTIVATED`
- [ ] Hook re-activation into existing membership assignment flow
- [ ] web-arena: full-screen "Membership Expired" banner for lapsed members; gate QR and booking endpoints
- [ ] web-pulse: `lapsed` badge on `/members` list + dedicated `/memberships/lapsed` recovery screen
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- Automated SMS or email outreach to lapsed members
- Lapsed member self-service renewal via web-arena (Plan 24 — Online Payments)
- Grace period after expiry
- Bulk re-activation without assigning a plan
- Lapse report in the Custom Report Builder (existing dimensions can be extended later)

---

## Decisions already made

- **New `MemberStatus.LAPSED`** — clean separation from `inactive` (manual staff action). Lapsed = expired membership. Inactive = manually disabled.
- **No Flyway migration** — `member_status` is stored as `VARCHAR` with `@Enumerated(EnumType.STRING)`; adding a new enum value requires no DDL change.
- **Scheduler**: daily at 04:00 UTC (07:00 Riyadh) — finds memberships where `end_date < today` AND `membership.status = 'active'` AND `member.status = 'active'`. Transitions both membership → `lapsed` and member → `lapsed`.
- **web-arena lapsed UX**: member can log in; all action endpoints (QR, book GX, book PT) return `403` with a specific `MEMBERSHIP_LAPSED` error code; frontend shows a full-screen banner.
- **Re-activation**: when staff assigns a new membership to a lapsed member (existing `POST /api/v1/members/{id}/memberships` flow), the service checks `member.status == LAPSED` → auto-sets `member.status = ACTIVE`. Logs `MEMBER_REACTIVATED`.
- **Renewal offer**: "Send Renewal Offer" button on the `/lapsed` screen creates a `MemberNote` of type `FOLLOW_UP` with content `"Renewal offer sent"` and `followUpAt = today + 3 days`. Uses existing `MemberNoteService.createNote()`.
- **Bulk action**: select multiple lapsed members → "Send Renewal Offer to Selected" → creates a follow_up note for each.

---

## MemberStatus enum change

```kotlin
// Add to existing MemberStatus enum
enum class MemberStatus {
    ACTIVE,
    PENDING_ACTIVATION,
    INACTIVE,
    TERMINATED,
    LAPSED          // ← new — membership expired, scheduler-driven
}
```

No SQL migration needed — column is `VARCHAR`, Hibernate reads/writes the string value.

---

## New notification type

Add to `NotificationType.kt`:

```kotlin
MEMBERSHIP_LAPSED,  // fired to club staff when a member's membership expires
```

- **Target**: all staff with `membership:read` permission in the member's club
- **Body (en)**: `"{memberName}'s membership expired on {endDate}. They have been moved to lapsed status."`
- **Body (ar)**: `"انتهت عضوية {memberName} في {endDate}. تم نقلهم إلى حالة منتهية الصلاحية."`

> Note: This notifies staff, not the member. The member already received `MEMBERSHIP_EXPIRING_SOON` 7 days prior.

---

## Lapse logic — MemberLapseService

```kotlin
@Service
class MemberLapseService(
    private val membershipRepository: MembershipRepository,
    private val memberRepository: MemberRepository,
    private val auditService: AuditService,
    private val notificationService: NotificationService
) {

    /**
     * Called by scheduler daily.
     * Finds all active memberships whose end_date < today and whose member is still active.
     * Transitions both to LAPSED.
     */
    @Transactional
    fun lapseExpiredMemberships() {
        val today = LocalDate.now(ZoneId.of("Asia/Riyadh"))
        val expired = membershipRepository.findExpiredActiveMemberships(today)

        expired.forEach { membership ->
            membership.status = MembershipStatus.LAPSED
            membershipRepository.save(membership)

            val member = memberRepository.findById(membership.memberId).orElse(null) ?: return@forEach
            if (member.status == MemberStatus.ACTIVE) {
                member.status = MemberStatus.LAPSED
                memberRepository.save(member)

                auditService.log(
                    action = AuditAction.MEMBERSHIP_LAPSED,
                    entityType = "Membership",
                    entityId = membership.publicId.toString(),
                    changes = mapOf(
                        "memberId" to member.publicId.toString(),
                        "endDate" to membership.endDate.toString()
                    )
                )

                notificationService.createForClubStaffWithPermission(
                    clubId = member.clubId,
                    permission = "membership:read",
                    type = "MEMBERSHIP_LAPSED",
                    entityId = member.publicId.toString(),
                    entityType = "Member",
                    titleEn = "Membership Lapsed",
                    titleAr = "انتهت العضوية",
                    bodyEn = "${member.nameEn ?: member.nameAr}'s membership expired on ${membership.endDate}.",
                    bodyAr = "انتهت عضوية ${member.nameAr} في ${membership.endDate}."
                )
            }
        }
    }

    /**
     * Called when staff assigns a new membership to a lapsed member.
     * Automatically re-activates the member.
     */
    @Transactional
    fun reactivateMemberIfLapsed(memberId: Long) {
        val member = memberRepository.findById(memberId).orElse(null) ?: return
        if (member.status == MemberStatus.LAPSED) {
            member.status = MemberStatus.ACTIVE
            memberRepository.save(member)
            auditService.log(
                action = AuditAction.MEMBER_REACTIVATED,
                entityType = "Member",
                entityId = member.publicId.toString(),
                changes = mapOf("previousStatus" to "LAPSED")
            )
        }
    }
}
```

---

## MembershipStatus enum change

Add `LAPSED` to `MembershipStatus` as well — the membership itself also needs to reflect the expired state distinctly from `terminated`:

```kotlin
enum class MembershipStatus {
    ACTIVE,
    PENDING_PAYMENT,
    FROZEN,
    TERMINATED,
    LAPSED      // ← new — expired via scheduler; not manually terminated
}
```

---

## Repository queries

Add to `MembershipRepository`:

```kotlin
// Memberships that expired before today, still marked active (for lapse scheduler)
@Query(value = """
    SELECT ms.* FROM memberships ms
    JOIN members m ON m.id = ms.member_id
    WHERE ms.end_date < :today
      AND ms.status = 'ACTIVE'
      AND m.status = 'ACTIVE'
      AND ms.deleted_at IS NULL
      AND m.deleted_at IS NULL
""", nativeQuery = true)
fun findExpiredActiveMemberships(today: java.time.LocalDate): List<Membership>
```

Add to `MemberRepository`:

```kotlin
// Lapsed members for a club, ordered by most recently lapsed (for /lapsed screen)
@Query(value = """
    SELECT m.* FROM members m
    JOIN memberships ms ON ms.member_id = m.id
    WHERE m.club_id = :clubId
      AND m.status = 'LAPSED'
      AND m.deleted_at IS NULL
    ORDER BY ms.end_date DESC
    LIMIT :limit OFFSET :offset
""", nativeQuery = true)
fun findLapsedByClub(clubId: Long, limit: Int, offset: Int): List<Member>

@Query(value = """
    SELECT COUNT(*) FROM members
    WHERE club_id = :clubId
      AND status = 'LAPSED'
      AND deleted_at IS NULL
""", nativeQuery = true)
fun countLapsedByClub(clubId: Long): Long
```

---

## Re-activation hook

In the existing membership creation service (wherever `POST /api/v1/members/{id}/memberships` is handled — likely `MembershipService.assignMembership()`), add after saving the new membership:

```kotlin
// Auto-reactivate member if they were lapsed
memberLapseService.reactivateMemberIfLapsed(member.id)
```

This is the only change needed to the existing membership flow.

---

## web-arena access gates for lapsed members

Add a check in `ArenaAuthHelper` (or equivalent request filter used by arena controllers):

```kotlin
// After validating the JWT and loading the member:
if (member.status == MemberStatus.LAPSED) {
    // Allow: GET /me, GET /portal-settings, GET /notifications, auth endpoints
    // Block with 403 + MEMBERSHIP_LAPSED error code: everything else
}
```

Specifically block:
- `GET /api/v1/arena/invoices/{id}` — QR code endpoint
- `POST /api/v1/arena/gx/{id}/book`
- `POST /api/v1/arena/gx/{id}/waitlist`
- `POST /api/v1/arena/gx/{id}/waitlist/accept`
- `GET /api/v1/arena/pt/packages`

Error response for blocked endpoints:
```json
{
  "type": "https://liyaqa.com/errors/membership-lapsed",
  "title": "Membership Expired",
  "status": 403,
  "detail": "Your membership has expired. Please contact the gym to renew.",
  "errorCode": "MEMBERSHIP_LAPSED"
}
```

---

## API endpoints

| Method | Path | Scope | Permission | Description |
|--------|------|-------|------------|-------------|
| `GET` | `/api/v1/pulse/memberships/lapsed` | club staff | `membership:read` | Paginated list of lapsed members for this club |
| `POST` | `/api/v1/pulse/members/{memberPublicId}/renewal-offer` | club staff | `member-note:create` | Creates a follow_up note + 3-day due date on the member |
| `POST` | `/api/v1/pulse/memberships/lapsed/renewal-offer-bulk` | club staff | `member-note:create` | Bulk renewal offer — creates follow_up note for each given member ID |

No new permissions needed — all endpoints use existing `membership:read` and `member-note:create`.

---

## Request / Response shapes

### GET /pulse/memberships/lapsed

```json
{
  "total": 47,
  "page": 1,
  "pageSize": 20,
  "members": [
    {
      "memberPublicId": "uuid",
      "nameAr": "سارة الزهراني",
      "nameEn": "Sarah Al-Zahrani",
      "phone": "+966501234567",
      "lastMembershipPlan": "Basic Monthly",
      "expiredOn": "2026-03-31",
      "daysSinceLapse": 8,
      "hasOpenFollowUp": false
    }
  ]
}
```

### POST /pulse/members/{id}/renewal-offer → 201

```json
{
  "noteId": "uuid",
  "followUpAt": "2026-04-10T00:00:00Z",
  "message": "Renewal offer follow-up created for Sarah Al-Zahrani. Due in 3 days."
}
```

### POST /pulse/memberships/lapsed/renewal-offer-bulk

Request:
```json
{ "memberPublicIds": ["uuid1", "uuid2", "uuid3"] }
```

Response `200 OK`:
```json
{ "created": 3, "skipped": 0 }
```

`skipped` = members who already have an open follow_up note created in the last 24h (deduplication).

---

## Business rules — enforce in service layer

1. **Lapse scheduler idempotent**: if a membership is already `LAPSED`, skip it. If member is already `LAPSED`, skip re-transitioning. Safe to run multiple times.
2. **Only active → lapsed**: the scheduler only transitions memberships with `status = 'ACTIVE'`. Frozen, terminated, and pending_payment memberships are NOT lapsed by this scheduler.
3. **Member lapse only if no other active membership**: if a member has multiple memberships (e.g. a new one already assigned), do NOT lapse the member even if the old one expired. Check: `SELECT COUNT(*) FROM memberships WHERE member_id = ? AND status = 'ACTIVE'` — if > 0, skip member status transition.
4. **Re-activation is automatic**: when a new `active` or `pending_payment` membership is assigned to a lapsed member, call `reactivateMemberIfLapsed()` immediately — do not wait for the scheduler.
5. **Renewal offer deduplication**: before creating a follow_up note via "Send Renewal Offer", check if a `FOLLOW_UP` note with content starting with `"Renewal offer sent"` already exists for this member in the last 24 hours. If yes, skip and count as `skipped` in the bulk result.
6. **Lapsed member in web-arena**: `GET /me` and notification endpoints remain accessible. All booking, QR, and package endpoints return `403` with `errorCode: MEMBERSHIP_LAPSED`.
7. **Lapsed screen scoping**: lapsed members are scoped to the current staff user's `clubId` from the JWT. Staff cannot see lapsed members from other clubs.
8. **`hasOpenFollowUp` field**: computed in service layer — `true` if there is an open (non-deleted) `FOLLOW_UP` note created in the last 7 days for this member.
9. **Bulk offer cap**: maximum 100 member IDs per bulk request. Exceeding the cap returns `400 Bad Request`.

---

## Scheduler extension

Add to existing `NotificationSchedulerService` (or create a new `MemberLapseScheduler`):

```kotlin
// Daily at 04:00 UTC (07:00 Riyadh) — run AFTER expiry notifications
@Scheduled(cron = "0 0 4 * * *")
fun lapseMemberships() {
    memberLapseService.lapseExpiredMemberships()
}
```

> Run at 04:00 UTC, same cron window as ZATCA CSID expiry check. Order within the same cron minute does not matter since they are independent.

---

## Seed data updates

No new seed data needed. In the dev environment, Ahmed Al-Rashidi has an active Basic Monthly membership — it will not lapse during development unless the date is advanced manually.

---

## Frontend additions

### web-pulse

**`/members` list** (existing screen):
- Members with `status = LAPSED` show a red "Lapsed" badge on their row
- Existing member list already fetches `status` — no API change needed, just a badge condition in the UI

**`/memberships/lapsed` — new route**:
- Table columns: Member Name (link to profile), Phone, Last Plan, Expired On, Days Since Lapse, Follow-up status (green tick if `hasOpenFollowUp`, grey dash if not)
- "Send Renewal Offer" button per row — calls `POST /renewal-offer`, shows success toast
- Checkbox column for bulk selection
- "Send Renewal Offer to Selected (N)" bulk action button — calls bulk endpoint
- Pagination: 20 per page
- Empty state: "No lapsed members — great retention!"
- Sidebar nav item: "Lapsed Members" under Memberships section, with a count badge showing `countLapsedByClub`

**New i18n strings** (`ar.json` + `en.json`):
```
lapsed.page_title
lapsed.nav_label
lapsed.badge
lapsed.columns.expired_on
lapsed.columns.days_since
lapsed.columns.follow_up_status
lapsed.columns.has_follow_up
lapsed.send_offer
lapsed.send_offer_bulk
lapsed.send_offer_success
lapsed.empty
members.status.lapsed
```

### web-arena

**Lapsed banner** (shown when `member.status = 'lapsed'` from `GET /me`):
- Full-screen overlay (not a dismissable toast — it cannot be closed)
- Message (en): "Your membership has expired. Please visit the gym or contact staff to renew."
- Message (ar): "انتهت عضويتك. يرجى زيارة النادي أو التواصل مع الموظفين للتجديد."
- Contact info pulled from `ClubPortalSettings.portalMessage` if set
- All nav items except Profile are hidden/disabled when banner is showing

**New i18n strings** (`ar.json` + `en.json`):
```
lapsed.banner_title
lapsed.banner_body
lapsed.banner_contact
```

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/kotlin/com/liyaqa/membership/service/MemberLapseService.kt`
- `backend/src/main/kotlin/com/liyaqa/membership/dto/LapsedMemberResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/membership/dto/RenewalOfferResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/membership/dto/BulkRenewalOfferRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/membership/dto/BulkRenewalOfferResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/membership/controller/MemberLapsePulseController.kt`
- `backend/src/test/kotlin/com/liyaqa/membership/service/MemberLapseServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/membership/controller/MemberLapseControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-pulse/src/routes/memberships/lapsed/index.tsx`
- `apps/web-pulse/src/api/memberLapse.ts`
- `apps/web-arena/src/components/LapsedBanner.tsx`
- `apps/web-pulse/src/tests/lapsed-members.test.tsx`
- `apps/web-arena/src/tests/lapsed-banner.test.tsx`

### Files to modify

- `backend/.../member/entity/MemberStatus.kt` — add `LAPSED`
- `backend/.../membership/entity/MembershipStatus.kt` — add `LAPSED`
- `backend/.../membership/repository/MembershipRepository.kt` — add `findExpiredActiveMemberships`
- `backend/.../member/repository/MemberRepository.kt` — add `findLapsedByClub`, `countLapsedByClub`
- `backend/.../notification/model/NotificationType.kt` — add `MEMBERSHIP_LAPSED`
- `backend/.../audit/model/AuditAction.kt` — add `MEMBERSHIP_LAPSED`, `MEMBER_REACTIVATED`
- `backend/.../notification/service/NotificationSchedulerService.kt` — add lapse scheduler cron
- `backend/.../membership/service/MembershipService.kt` — hook `reactivateMemberIfLapsed()` into assignment
- `backend/.../arena/auth/ArenaAuthHelper.kt` — add lapsed member access gate
- `apps/web-pulse/src/routes/members/index.tsx` — add Lapsed badge to member rows
- `apps/web-pulse/src/routes/memberships/index.tsx` — add "Lapsed Members" sidebar link + badge
- `apps/web-arena/src/routes/_authenticated.tsx` (or root layout) — check lapsed status, render banner
- `apps/web-pulse/src/locales/ar.json` + `en.json`
- `apps/web-arena/src/locales/ar.json` + `en.json`

---

## Implementation order

### Step 1 — Enum extensions
- Add `LAPSED` to `MemberStatus.kt`
- Add `LAPSED` to `MembershipStatus.kt`
- Verify: `./gradlew compileKotlin`

### Step 2 — Notification type + audit actions
- Add `MEMBERSHIP_LAPSED` to `NotificationType.kt`
- Add `MEMBERSHIP_LAPSED`, `MEMBER_REACTIVATED` to `AuditAction.kt`
- Verify: `./gradlew compileKotlin`

### Step 3 — Repository queries
- Add `findExpiredActiveMemberships` to `MembershipRepository`
- Add `findLapsedByClub`, `countLapsedByClub` to `MemberRepository`
- All must use `nativeQuery = true`
- Verify: `./gradlew compileKotlin`

### Step 4 — MemberLapseService
- Implement `lapseExpiredMemberships()` — full transition logic with business rule 3 (skip if other active membership)
- Implement `reactivateMemberIfLapsed()` — idempotent re-activation
- Verify: unit tests in `MemberLapseServiceTest`

### Step 5 — Extend scheduler + re-activation hook
- Add lapse cron method to `NotificationSchedulerService` (or new `MemberLapseScheduler`)
- Hook `reactivateMemberIfLapsed()` into `MembershipService.assignMembership()`
- Verify: `./gradlew compileKotlin`

### Step 6 — web-arena access gate
- Add lapsed status check in `ArenaAuthHelper`
- Blocked endpoints return RFC 7807 `403` with `errorCode: MEMBERSHIP_LAPSED`
- Allowed endpoints: `/me`, `/notifications`, `/portal-settings`, all auth endpoints
- Verify: integration test covers 403 on QR + booking endpoints for lapsed member

### Step 7 — MemberLapsePulseController
- `GET /pulse/memberships/lapsed` — paginated lapsed member list
- `POST /pulse/members/{id}/renewal-offer` — single renewal offer
- `POST /pulse/memberships/lapsed/renewal-offer-bulk` — bulk renewal offers (cap 100)
- All with `@Operation` + `@PreAuthorize`
- Verify: `./gradlew compileKotlin`

### Step 8 — Frontend: web-pulse
- `/memberships/lapsed` route — table, per-row offer button, bulk action, pagination, empty state
- `memberLapse.ts` — 3 API functions
- Add Lapsed badge to existing members list rows
- Add Lapsed Members sidebar nav item with count badge
- Add all i18n strings

### Step 9 — Frontend: web-arena
- `LapsedBanner.tsx` — full-screen non-dismissable overlay
- Check `member.status === 'lapsed'` from `GET /me` response in root layout
- Hide/disable nav items when banner is showing
- Add i18n strings

### Step 10 — Tests

**Unit: `MemberLapseServiceTest`**
- `lapseExpiredMemberships transitions active membership and member to LAPSED`
- `lapseExpiredMemberships skips already-lapsed memberships (idempotent)`
- `lapseExpiredMemberships skips member if another active membership exists`
- `lapseExpiredMemberships fires MEMBERSHIP_LAPSED notification for each lapsed member`
- `lapseExpiredMemberships logs MEMBERSHIP_LAPSED audit action`
- `reactivateMemberIfLapsed transitions LAPSED member to ACTIVE`
- `reactivateMemberIfLapsed does nothing if member is not LAPSED`
- `reactivateMemberIfLapsed logs MEMBER_REACTIVATED audit action`

**Integration: `MemberLapseControllerIntegrationTest`**
- `GET /pulse/memberships/lapsed returns paginated lapsed members`
- `GET /pulse/memberships/lapsed returns 403 without membership:read`
- `POST /pulse/members/{id}/renewal-offer creates follow_up note`
- `POST /pulse/members/{id}/renewal-offer returns 409 when offer already sent in last 24h` — wait, this is a skip not an error; returns 201 with skipped=1 in bulk, for single returns 201 with a note that it was skipped
- `POST /pulse/memberships/lapsed/renewal-offer-bulk creates notes for all given members`
- `POST /pulse/memberships/lapsed/renewal-offer-bulk returns 400 when more than 100 IDs provided`
- `assigning new membership to lapsed member auto-sets member status to ACTIVE`
- `lapsed member in web-arena receives 403 on GX booking endpoint`
- `lapsed member in web-arena can still access GET /me`

**Frontend:**
- `lapsed-members.test.tsx` (pulse):
  - renders lapsed members table with correct columns
  - "Send Renewal Offer" button calls offer endpoint and shows toast
  - bulk select and bulk offer button calls bulk endpoint
  - empty state shown when no lapsed members
- `lapsed-banner.test.tsx` (arena):
  - renders full-screen banner when member status is lapsed
  - does not render banner when member status is active
  - nav items are hidden when banner is showing

---

## RBAC matrix

No new permissions added. Uses existing:
- `membership:read` — guards GET lapsed list
- `member-note:create` — guards renewal offer endpoints

---

## Definition of Done

- [ ] `LAPSED` added to `MemberStatus` enum
- [ ] `LAPSED` added to `MembershipStatus` enum
- [ ] `MEMBERSHIP_LAPSED` and `MEMBER_REACTIVATED` audit actions added
- [ ] `MEMBERSHIP_LAPSED` notification type added
- [ ] 3 repository queries added with `nativeQuery = true`
- [ ] `lapseExpiredMemberships()` correctly transitions only active memberships with no other active membership on the member
- [ ] Lapse scheduler is idempotent — safe to run multiple times
- [ ] `reactivateMemberIfLapsed()` hooked into `MembershipService.assignMembership()`
- [ ] Lapsed member in web-arena: QR and booking endpoints return `403` with `MEMBERSHIP_LAPSED` error code
- [ ] Lapsed member in web-arena: `GET /me` and notifications remain accessible
- [ ] `GET /pulse/memberships/lapsed` is scoped to current user's club
- [ ] Renewal offer deduplication: skip if open follow_up note exists in last 24h
- [ ] Bulk renewal offer capped at 100 members — returns `400` if exceeded
- [ ] web-pulse: Lapsed badge on members list
- [ ] web-pulse: `/memberships/lapsed` page with renewal offer + bulk action + count badge
- [ ] web-arena: full-screen non-dismissable lapsed banner
- [ ] web-arena: nav hidden when banner is showing
- [ ] All i18n strings added in Arabic and English (web-pulse + web-arena)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors in web-pulse or web-arena
- [ ] `PROJECT-STATE.md` updated: Plan 33 complete, test counts
- [ ] `PLAN-33-lapse-recovery.md` deleted before merging

When all items are checked, confirm: **"Plan 33 — Membership Lapse Recovery complete. X backend tests, Y frontend tests."**
