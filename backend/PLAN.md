# PLAN.md — Membership Lifecycle

## Status
Ready for implementation

## Branch
feat/membership-lifecycle

## Goal
Add the full membership lifecycle to the existing Membership
domain: renewal, freeze, unfreeze, expiry processing, and
termination. After this plan, the system correctly handles
every membership state transition that happens in daily
club operations.

---

## Context

The Membership entity and MembershipService already exist from
Plan 8. The current implementation only supports creating a
membership (assignPlan). This plan adds:

- **Renewal** — extend a membership by assigning a new period
- **Freeze** — pause a membership, extending the end date
- **Unfreeze** — resume a frozen membership
- **Expiry** — scheduled job that marks expired memberships
- **Termination** — staff manually terminates a membership

These operations are the most common daily tasks at the
reception desk. Without them, every membership in the system
will eventually expire with no way to handle it.

Current state of the Membership entity:
- membershipStatus: String (pending|active|frozen|expired|terminated)
- startDate: LocalDate
- endDate: LocalDate
- graceEndDate: LocalDate? (end_date + plan.gracePeriodDays)
- freezeDaysUsed: Int

---

## Scope — what this plan covers

### Backend
- [ ] MembershipService — add lifecycle methods:
      renew, freeze, unfreeze, terminate, expire
- [ ] MembershipPulseController — add lifecycle endpoints
- [ ] FreezePeriod entity + repository
      (tracks individual freeze periods for audit trail)
- [ ] MembershipExpiryJob — scheduled job that runs daily,
      marks memberships as expired when endDate has passed
- [ ] Update Member.membershipStatus in sync with Membership
      status changes (same pattern as Plan 8 assignPlan)
- [ ] New DTOs for lifecycle operations
- [ ] Update DevDataLoader — no changes needed
      (seeded membership is active — lifecycle tested via API)
- [ ] Unit tests for all lifecycle methods
- [ ] Integration tests for all new endpoints
- [ ] OpenAPI spec updates

### Frontend (web-pulse)
- [ ] Update src/routes/members/$memberId.membership.tsx
      — add action buttons: Renew, Freeze, Unfreeze, Terminate
- [ ] RenewalForm component (drawer)
- [ ] FreezeForm component (drawer)
- [ ] MembershipStatusTimeline component
      (shows freeze periods and renewal history)
- [ ] Renewals queue page — /memberships/index.tsx
      (list of members expiring in next 30 days)
- [ ] i18n keys

---

## Out of scope — do not implement in this plan

- Member self-service freeze request via web-arena
- Automated renewal (payment gateway auto-charge)
- Proration for mid-period upgrades
- Refunds on termination
- Email/SMS notifications on status change
- Bulk operations (freeze all members of a plan type)
- Grace period configuration changes after membership created

---

## Decisions already made

- Renewal creates a NEW Membership record — it does not
  extend the existing one. The old membership stays in the
  database as history.
- Renewal can start: immediately (today), on the day the
  current membership expires, or on a custom date.
  Default: on the current membership's end_date + 1 day.
- Freeze extends the end_date by the freeze duration.
  graceEndDate is recalculated accordingly.
- A membership can only be frozen if:
  - Current status is "active"
  - plan.freezeAllowed = true
  - freezeDaysUsed + requestedDays <= plan.maxFreezeDays
- Unfreeze before the freeze end date resumes the membership
  and adjusts end_date to reflect only the days actually frozen.
- The expiry job runs daily at 01:00 Asia/Riyadh (UTC-3 = 22:00 UTC).
  It transitions:
  - active → expired when endDate < today AND graceEndDate IS NULL
  - active → expired when graceEndDate < today
  - Updates Member.membershipStatus accordingly
- Termination is irreversible. It sets status to "terminated"
  and records the reason.
- After any status transition, Member.membershipStatus is
  updated to mirror the active Membership status in the same
  transaction.

---

## New entity — FreezePeriod

Tracks individual freeze periods for audit trail and
freeze days calculation.

```
organization_id   BIGINT       NOT NULL FK → organizations(id)
membership_id     BIGINT       NOT NULL FK → memberships(id)
member_id         BIGINT       NOT NULL FK → members(id)
freeze_start_date DATE         NOT NULL
freeze_end_date   DATE         NOT NULL
actual_end_date   DATE         NULL
                  (null if freeze not yet ended;
                   set when unfreeze is called)
duration_days     INTEGER      NOT NULL
                  (freeze_end_date - freeze_start_date)
reason            TEXT         NULL
requested_by_id   BIGINT       NOT NULL FK → users(id)
-- id + created_at only (no full audit entity)
```

---

## New API endpoints

### Membership lifecycle (Pulse-facing)

```
POST   /api/v1/members/{memberId}/memberships/{id}/renew
POST   /api/v1/members/{memberId}/memberships/{id}/freeze
POST   /api/v1/members/{memberId}/memberships/{id}/unfreeze
POST   /api/v1/members/{memberId}/memberships/{id}/terminate

GET    /api/v1/memberships/expiring
       ← members with memberships expiring in next N days
          query param: days (default 30, max 90)
```

Required permissions:
- renew: `membership:create`
- freeze / unfreeze: `membership:freeze` / `membership:unfreeze`
- terminate: `membership:update`
- expiring list: `membership:read`

---

## Request / Response shapes

### RenewMembershipRequest

```json
{
  "planId": "uuid (required — can renew on same or different plan)",
  "startDate": "date (optional — defaults to current endDate + 1)",
  "paymentMethod": "cash | card | bank-transfer | other",
  "amountHalalas": "integer (required, must equal new plan price)",
  "referenceNumber": "string (optional)",
  "notes": "string (optional)"
}
```

Response: `MembershipResponse` (same as Plan 8)

### FreezeMembershipRequest

```json
{
  "freezeStartDate": "date (required, >= today)",
  "freezeEndDate": "date (required, > freezeStartDate)",
  "reason": "string (optional)"
}
```

Response: updated `MembershipResponse` with new endDate

### UnfreezeMembershipRequest

```json
{
  "notes": "string (optional)"
}
```

Response: updated `MembershipResponse`

### TerminateMembershipRequest

```json
{
  "reason": "string (required)"
}
```

Response: updated `MembershipResponse`

### ExpiringMembershipsResponse

```json
{
  "items": [
    {
      "memberId": "uuid",
      "memberName": "string",
      "memberPhone": "string",
      "planName": "string",
      "endDate": "date",
      "daysRemaining": "integer",
      "membershipId": "uuid",
      "membershipStatus": "string"
    }
  ],
  "pagination": { ... }
}
```

---

## Business rules — enforce in service layer

1. **Only active memberships can be frozen** — status must be
   "active". Return 422 if not active.

2. **Plan must allow freeze** — plan.freezeAllowed must be true.
   Return 422 if freeze not allowed on this plan.

3. **Freeze days limit** — freezeDaysUsed + requestedDays
   must not exceed plan.maxFreezeDays.
   requestedDays = freezeEndDate - freezeStartDate.
   Return 422 with message showing how many days remain.

4. **No overlapping freeze periods** — a membership cannot have
   two overlapping active freeze periods. Return 409.

5. **Freeze start must be today or future** — cannot freeze
   in the past. Return 422.

6. **Only frozen memberships can be unfrozen** — status must
   be "frozen". Return 422 if not frozen.

7. **Renewal requires active or expired membership** — cannot
   renew a terminated or pending membership. Return 422.

8. **Renewal payment amount must match new plan price** —
   same rule as Plan 8. Return 422 if mismatch.

9. **Only active/frozen memberships can be terminated** —
   cannot terminate an expired or pending membership.
   Return 422.

10. **Termination reason required** — reason field must not
    be blank. Return 422 if empty.

11. **Member status sync** — after every status transition,
    Member.membershipStatus must be updated in the same
    transaction to reflect the new membership status.

---

## MembershipExpiryJob

```kotlin
@Component
class MembershipExpiryJob(
    private val membershipService: MembershipService,
) {
    // Runs daily at 01:00 Asia/Riyadh = 22:00 UTC previous day
    @Scheduled(cron = "0 0 22 * * *", zone = "UTC")
    fun processExpiredMemberships() {
        membershipService.expireOverdueMemberships()
    }
}
```

`expireOverdueMemberships()` in MembershipService:
- Find all memberships where:
  - status = "active" OR status = "frozen"
  - AND ((graceEndDate IS NOT NULL AND graceEndDate < today)
       OR (graceEndDate IS NULL AND endDate < today))
- For each: set status = "expired"
- Update Member.membershipStatus = "expired"
- Log count of expired memberships
- All in one @Transactional batch

---

## Frontend additions (web-pulse)

### Membership tab updates

Add action buttons below the active membership card:

- **Renew** button — visible always when membership is active
  or expired (has `membership:create` permission)
  Opens `RenewalForm` drawer
- **Freeze** button — visible when status is "active"
  and plan.freezeAllowed = true (has `membership:freeze`)
  Opens `FreezeForm` drawer
- **Unfreeze** button — visible when status is "frozen"
  (has `membership:unfreeze`)
  Confirm dialog — no form needed
- **Terminate** button — visible when status is "active"
  or "frozen" (has `membership:update`)
  Confirm dialog with mandatory reason text field
  Styled as destructive (red)

All action buttons show loading state during mutation.
After any action: invalidate membership query, show toast.

### RenewalForm component

- Plan selector dropdown (active plans)
- Start date picker (defaults to current endDate + 1)
- Payment method selector
- Amount auto-filled from selected plan (not editable)
- Submit: "Renew membership & collect payment"

### FreezeForm component

- Start date picker (min: today)
- End date picker (min: startDate + 1)
- Reason text field (optional)
- Live calculation: "X days freeze — Y days remaining
  out of Z max"
- Warning if freeze days would exceed plan limit

### MembershipStatusTimeline component

- Visual timeline below the active card
- Shows: membership start, any freeze periods (with dates),
  renewals, expiry date
- Each event is a dot on the timeline with date and label

### Renewals queue — /memberships (update existing stub)

- Table of all members with expiring memberships
- Columns: member name, plan, expiry date, days remaining,
  phone, "Renew" action button
- Default filter: expiring in next 30 days
- Filter options: 7 days, 14 days, 30 days, overdue (expired)
- Overdue section highlighted in red
- Auto-refreshes every 5 minutes

---

## Files to generate / modify

### Backend — new files

```
membership/
  FreezePeriod.kt
  FreezePeriodRepository.kt
  MembershipExpiryJob.kt
  dto/
    RenewMembershipRequest.kt
    FreezeMembershipRequest.kt
    UnfreezeMembershipRequest.kt
    TerminateMembershipRequest.kt
    ExpiringMembershipResponse.kt
```

### Backend — modified files

```
membership/
  MembershipService.kt    add: renew, freeze, unfreeze,
                          terminate, expireOverdueMemberships
  MembershipPulseController.kt  add: 5 new endpoints
```

### Frontend — new files

```
src/components/membership/
  RenewalForm.tsx
  FreezeForm.tsx
  MembershipStatusTimeline.tsx
  ExpiringMembershipsTable.tsx
```

### Frontend — modified files

```
src/routes/memberships/index.tsx          (renewals queue)
src/routes/members/$memberId.membership.tsx  (add action buttons)
```

---

## Implementation order

```
Step 1 — FreezePeriod entity
  FreezePeriod.kt + FreezePeriodRepository.kt
  Verify: ./gradlew build -x test

Step 2 — New DTOs
  RenewMembershipRequest
  FreezeMembershipRequest
  UnfreezeMembershipRequest
  TerminateMembershipRequest
  ExpiringMembershipResponse
  Verify: ./gradlew build -x test

Step 3 — Freeze / Unfreeze in MembershipService
  freeze() — rules 1-5, creates FreezePeriod,
             extends endDate and graceEndDate
  unfreeze() — rule 6, closes FreezePeriod,
               adjusts endDate to actual days frozen
  Verify: ./gradlew build -x test

Step 4 — Renew in MembershipService
  renew() — rules 7-8, reuses PaymentService
            and InvoiceService from Plan 8,
            creates new Membership record
  Verify: ./gradlew build -x test

Step 5 — Terminate in MembershipService
  terminate() — rules 9-11
  Verify: ./gradlew build -x test

Step 6 — Expiry job and batch method
  expireOverdueMemberships() in MembershipService
  MembershipExpiryJob.kt with @Scheduled
  Enable scheduling in Spring config if not already:
    @EnableScheduling on main application class
  Verify: ./gradlew build -x test

Step 7 — Controller endpoints
  Add 5 new endpoints to MembershipPulseController
  GET /api/v1/memberships/expiring
  Verify: ./gradlew build -x test

Step 8 — Backend tests
  MembershipLifecycleServiceTest:
    - freeze: happy path, freeze days limit, plan disallows
    - unfreeze: happy path, end_date adjustment
    - renew: happy path, wrong amount
    - terminate: happy path, reason required
    - expireOverdueMemberships: batch expiry
  MembershipLifecyclePulseControllerTest
  ./gradlew test — all 193+ tests must pass

Step 9 — Backend final checks
  ktlintFormat → ktlintCheck → build

Step 10 — OpenAPI spec
  Update docs/api/memberships.yaml with new endpoints

Step 11 — Frontend types update
  Add FreezePeriod, ExpiringMembership to domain.ts
  Add new API functions to src/api/memberships.ts
  Verify: npm run typecheck

Step 12 — Frontend components
  RenewalForm.tsx
  FreezeForm.tsx
  MembershipStatusTimeline.tsx
  ExpiringMembershipsTable.tsx
  Verify: npm run typecheck

Step 13 — Update membership tab
  Add action buttons (Renew, Freeze, Unfreeze, Terminate)
  Wire up forms and confirm dialogs
  Verify: npm run dev → Ahmed's membership tab
    shows Freeze button (active membership, freezeAllowed)
    Freeze → form validates days limit
    Unfreeze → confirm → status returns to active

Step 14 — Renewals queue page
  src/routes/memberships/index.tsx
  Verify: /memberships shows expiring members
    (seed data member expires in 30 days — should appear)

Step 15 — Frontend tests
  RenewalForm.test.tsx
  FreezeForm.test.tsx — validate days limit warning
  ExpiringMembershipsTable.test.tsx
  npm test

Step 16 — Frontend final checks
  npm run typecheck → lint → build
```

---

## Acceptance criteria

### Backend
- [ ] POST .../freeze creates FreezePeriod, extends endDate,
      sets status to "frozen"
- [ ] POST .../unfreeze closes FreezePeriod, adjusts endDate
      to actual days frozen, sets status to "active"
- [ ] Freeze beyond plan limit returns 422 with days remaining
- [ ] Freeze on non-active membership returns 422
- [ ] POST .../renew creates new Membership + Payment + Invoice,
      old membership status unchanged
- [ ] POST .../terminate sets status to "terminated",
      requires reason
- [ ] expireOverdueMemberships() transitions correct memberships
- [ ] Member.membershipStatus synced after every transition
- [ ] GET /api/v1/memberships/expiring returns correct list
- [ ] All 193+ existing tests still pass

### Frontend
- [ ] Freeze button visible for active membership with
      freezeAllowed plan, hidden for non-freezeable plans
- [ ] Freeze form shows live days remaining calculation
- [ ] Freeze form blocks submit if days exceed plan limit
- [ ] Unfreeze shows confirmation dialog before action
- [ ] Terminate requires reason text — submit blocked if empty
- [ ] Renew form pre-fills next start date correctly
- [ ] /memberships shows expiring members queue
- [ ] npm run typecheck, lint, test, build all pass

---

## RBAC matrix rows added by this plan

| Action | Owner | Branch Manager | Receptionist | Sales Agent |
|---|---|---|---|---|
| membership:freeze | ✅ | ✅ | ✅ | ❌ |
| membership:unfreeze | ✅ | ✅ | ✅ | ❌ |
| membership:terminate | ✅ | ✅ | ❌ | ❌ |

(membership:create already covers renewal — Plan 8)

---

## Definition of done

- All acceptance criteria checked
- Freeze → Unfreeze round trip verified:
  endDate restored to correct value after early unfreeze
- Expiry job tested with a membership whose endDate is yesterday
- All CI checks pass on PR
- PLAN.md deleted before merging
- PR title: `feat(membership-lifecycle): implement renew, freeze, unfreeze, expire, terminate`
- Target branch: `develop`
