# PLAN.md — Member Self-Registration (Plan 22)

## Status
Ready for implementation

## Branch
feat/member-self-registration

## Goal
Allow prospective members to register themselves via web-arena without
any staff involvement. The member enters their details, verifies their
phone via OTP, and either selects a membership plan and pays at desk
(staff completes payment on next visit) or skips the plan entirely
and waits for staff to assign one. The result is a real `Member` record
with `status = "pending_activation"` that staff can review and activate
in web-pulse.

## Context
- `Member` entity already exists with all profile fields.
- `MemberOtp` entity already exists (phone OTP, SHA-256, 10-min TTL, 3/10min rate-limit).
- `MembershipPlan` entity and public catalogue already exist.
- `ClubPortalSettings` has feature flags — we add `selfRegistrationEnabled`.
- web-arena already has the OTP login flow — self-registration reuses
  the same OTP endpoints with a different purpose (identity verification,
  not login).
- The existing `MemberStatus` enum has `active`, `frozen`, `terminated` —
  we add `pending_activation`.
- `AuditService` exists — wire in 2 new audit action codes.
- `NotificationTriggerService` already handles `MemberJoinedEvent` —
  that same event fires here, so staff get notified automatically.
- No payment gateway — payment is always "pay at desk" (cash/card on arrival).

---

## Scope — what this plan covers

### Backend
- [ ] Add `pending_activation` to `MemberStatus` enum
- [ ] Add `selfRegistrationEnabled: Boolean` to `ClubPortalSettings` (default `false`)
- [ ] `SelfRegistrationRequest` DTO — all required member fields
- [ ] `SelfRegistrationService` — full registration flow (OTP verify → create member → optional plan intent)
- [ ] `SelfRegistrationArenaController` — 3 public endpoints (no JWT required)
- [ ] `MemberRegistrationIntent` entity — stores desired plan before activation (optional)
- [ ] Flyway V14: `member_registration_intents` table + `self_registration_enabled` column on `club_portal_settings`
- [ ] `MemberPulseController` — add `GET /pending` and `POST /{id}/activate` endpoints
- [ ] 2 new `AuditAction` codes: `MEMBER_SELF_REGISTERED`, `MEMBER_ACTIVATED`
- [ ] Unit tests: `SelfRegistrationServiceTest`
- [ ] Integration tests: `SelfRegistrationArenaControllerTest`, `MemberActivationPulseControllerTest`

### Frontend — web-arena
- [ ] Registration entry point: "New member? Register here" link on OTP login screen
- [ ] Multi-step registration wizard (3 steps: Phone verify → Profile → Plan selection)
- [ ] Step 1: Phone OTP (reuses existing OTP request/verify endpoints)
- [ ] Step 2: Profile form (name, email, date of birth, gender, emergency contact)
- [ ] Step 3: Plan selection (optional — can skip) using existing membership plans catalogue
- [ ] Success screen with "pending activation" message
- [ ] Guard: hide registration link when `selfRegistrationEnabled = false` (checked via public portal-settings endpoint)

### Frontend — web-pulse
- [ ] Pending Members queue: new section under Members nav
- [ ] Pending member detail: profile preview + intended plan + Activate / Reject actions
- [ ] Activate modal: confirm, optionally assign plan right now (dropdown of active plans)
- [ ] Reject modal: reason field (stored in member notes)
- [ ] Badge on sidebar Members nav item showing pending count
- [ ] Notification bell already fires for `MEMBER_JOINED` — no extra wiring needed

---

## Out of scope — do not implement in this plan
- Online payment / payment gateway (pay at desk only)
- Email verification (phone OTP is sufficient for Saudi market)
- ID document upload (no file upload support)
- Auto-activation without staff review
- Member self-service plan upgrade or purchase
- Admin-side bulk activation
- Welcome email (no new email templates — existing notification covers it)

---

## Decisions already made

1. **`pending_activation` status** — new members from self-registration are never
   immediately `active`. A staff member must review and activate. This protects
   clubs from unverified walk-ins and maintains RBAC integrity (no JWT issued until active).

2. **OTP reuse** — existing `/api/v1/arena/auth/otp/request` and `/api/v1/arena/auth/otp/verify`
   endpoints are reused with a `purpose` query param (`login` vs `registration`).
   The `verify` endpoint returns a short-lived `registrationToken` (JWT with `scope = "registration"`, 15-min TTL)
   instead of a full member JWT when `purpose = registration`. This token is used
   only for the subsequent `POST /api/v1/arena/register/complete` call.

3. **`MemberRegistrationIntent`** — optional link from member to desired plan.
   Staff can see which plan the member wanted. Not a `Membership` — only becomes
   one when staff activates and assigns.

4. **`selfRegistrationEnabled` default = false** — clubs must explicitly opt in
   via Settings → Portal → Self-Registration toggle (web-pulse).

5. **Phone uniqueness** — during registration, check phone is not already used by
   an active/frozen/pending member in the same club. Return `409` if duplicate.
   Different clubs may share a phone (member may join multiple clubs).

6. **`MemberJoinedEvent` reuse** — `SelfRegistrationService` publishes
   `MemberJoinedEvent` after creating the member record, triggering the existing
   notification flow (staff get a bell notification in web-pulse automatically).

7. **Activation by staff** — Activate endpoint creates the member JWT scope if
   the member logs in later (no immediate JWT issued at activation time — member
   must log in again). Sets `status = "active"`, optionally assigns chosen plan
   (creates `Membership` with `status = "pending_payment"` if plan selected).

---

## Entity design

### Modified: `ClubPortalSettings`
```kotlin
var selfRegistrationEnabled: Boolean = false
```

### Modified: `MemberStatus` enum
```kotlin
enum class MemberStatus {
    active,
    frozen,
    terminated,
    pending_activation   // NEW — set on self-registration
}
```

### New entity: `MemberRegistrationIntent`
```kotlin
@Entity
@Table(name = "member_registration_intents")
class MemberRegistrationIntent(
    var memberId: Long,                          // FK to members.id
    var memberPublicId: UUID,                    // denormalized for fast lookup
    var membershipPlanId: Long?,                 // FK to membership_plans.id (nullable)
    var membershipPlanPublicId: UUID?,           // denormalized
    var membershipPlanNameEn: String?,           // snapshot at registration time
    var membershipPlanNameAr: String?,
    var membershipPlanPriceHalalas: Long?,       // snapshot
    var clubId: Long,
    var resolvedAt: Instant? = null,             // set when staff activates or rejects
    var resolvedBy: Long? = null                 // FK to users.id
) : AuditEntity()
```

Table: `member_registration_intents`

---

## API endpoints

### New — public (no JWT)
```
POST /api/v1/arena/register/otp/request
     Body: { phone, clubPortalSlug }
     → Reuses MemberOtp infrastructure, purpose = "registration"
     → 200 OK (never reveals phone existence in error, same as login)

POST /api/v1/arena/register/otp/verify
     Body: { phone, otp, clubPortalSlug }
     → Returns { registrationToken: String } (JWT, scope="registration", 15-min TTL)
     → 200 OK or 422 UNPROCESSABLE_ENTITY on wrong OTP / expired

POST /api/v1/arena/register/complete
     Header: Authorization: Bearer {registrationToken}
     Body: SelfRegistrationRequest
     → 201 CREATED { memberId: UUID, status: "pending_activation" }
     → 409 CONFLICT if phone already registered in this club
```

### New — staff (scope=club, permission=member:create)
```
GET  /api/v1/members/pending
     → Page<PendingMemberResponse> (pending_activation status only, scoped to club)
     → Supports ?page, ?size, ?search (name/phone)

POST /api/v1/members/{id}/activate
     Body: { membershipPlanId?: UUID }
     → 200 OK MemberResponse (status now "active")
     → 409 if already active

POST /api/v1/members/{id}/reject
     Body: { reason: String }
     → 200 OK (status set to "terminated", reason stored in member notes)
     → 409 if already active or terminated
```

### Modified — staff portal settings (scope=club, permission=portal-settings:update)
```
PATCH /api/v1/portal-settings
      Body now includes: { selfRegistrationEnabled?: Boolean }
```

### Modified — public portal settings
```
GET /api/v1/portal-settings/public?clubPortalSlug=...
    Response now includes: selfRegistrationEnabled: Boolean
```

---

## Request / Response shapes

### `SelfRegistrationRequest`
```json
{
  "phone": "+966501234567",
  "clubPortalSlug": "elixir-gym",
  "nameEn": "Ahmed Al-Rashidi",
  "nameAr": "أحمد الراشدي",
  "email": "ahmed@example.com",
  "dateOfBirth": "1990-05-15",
  "gender": "male",
  "emergencyContactName": "Sara Al-Rashidi",
  "emergencyContactPhone": "+966509876543",
  "desiredMembershipPlanId": "uuid-or-null"
}
```

### `PendingMemberResponse`
```json
{
  "id": "uuid",
  "nameEn": "Ahmed Al-Rashidi",
  "nameAr": "أحمد الراشدي",
  "phone": "+966501234567",
  "email": "ahmed@example.com",
  "dateOfBirth": "1990-05-15",
  "gender": "male",
  "registeredAt": "2026-04-07T09:15:00Z",
  "intent": {
    "planId": "uuid",
    "planNameEn": "Basic Monthly",
    "planNameAr": "الشهري الأساسي",
    "planPriceSar": "150.00"
  }
}
```

### `ActivateMemberRequest`
```json
{
  "membershipPlanId": "uuid-or-null"
}
```

---

## Business rules — enforce in service layer

1. **Self-registration disabled** — if `ClubPortalSettings.selfRegistrationEnabled = false`
   for the club resolved from `clubPortalSlug`, return `403 FORBIDDEN` with
   `"Self-registration is not enabled for this club"`.

2. **Phone uniqueness per club** — if a member with the same phone and same `clubId`
   already exists with `status IN (active, frozen, pending_activation)`, return
   `409 CONFLICT` with `"Phone number already registered at this club"`.
   Different clubs: allow.

3. **OTP purpose isolation** — a `registrationToken` (scope=registration) cannot be
   used to log in as a member (login endpoint rejects scope=registration). A member
   JWT (scope=member) cannot be used on the register/complete endpoint.

4. **Registration token one-time use** — after `POST /register/complete` succeeds,
   the `MemberOtp` record is marked `used = true`. The token cannot be replayed.

5. **Name required in at least one language** — `nameEn` OR `nameAr` must be
   non-blank. If both are blank, return `422` with field error.

6. **Plan must belong to this club** — if `desiredMembershipPlanId` is provided,
   verify `MembershipPlan.clubId` matches the registering club and plan is not
   soft-deleted. Return `422` if not found or mismatched club.

7. **Activate: staff club scope** — staff can only activate members in their own
   club's scope (enforced via `TenantContext.clubId`, same as all other member endpoints).

8. **Activate creates pending membership** — if staff selects a plan during activation,
   create a `Membership` with `status = "pending_payment"`, `startDate = today`,
   `endDate = today + plan.durationDays`. Payment is NOT collected automatically.

9. **Reject reason stored** — on rejection, create a `MemberNote` with
   `content = "Registration rejected: {reason}"`, `authorId = staffUserId`,
   then set `member.status = "terminated"`.

10. **No JWT issued to pending members** — the OTP login flow already checks
    `member.status`. `pending_activation` status → `401` with
    `"Your registration is pending staff approval"`.

---

## Seed data updates

### `ClubPortalSettings`
```kotlin
selfRegistrationEnabled = true   // enable for demo club so it's testable
```

### Dev test user
No new seed members — the registration flow creates its own during testing.

---

## Frontend additions

### web-arena

**Modified: OTP login screen** (`/auth/login`)
- Add "New member? Register here" link below the OTP form
- Hide the link when `portalSettings.selfRegistrationEnabled = false`
  (fetch portal settings from public endpoint before rendering)

**New: Registration wizard** (`/register`)
Three-step wizard with step indicator (Step 1 of 3):

**Step 1 — Verify Phone** (`/register/step-1`)
- Phone input → calls `POST /api/v1/arena/register/otp/request`
- OTP input → calls `POST /api/v1/arena/register/otp/verify`
- Stores `registrationToken` in Zustand (UI state — 15 min only, lost on refresh)
- If OTP verify returns existing active member → redirect to `/auth/login` with toast
  "You already have an account, please log in"

**Step 2 — Your Details** (`/register/step-2`)
- Fields: nameAr (required), nameEn, email, dateOfBirth, gender (radio: male/female)
- Emergency contact: name + phone (optional section, collapsible)
- Validation: React Hook Form + Zod
- Guarded: if `registrationToken` is missing → redirect to `/register/step-1`

**Step 3 — Choose a Plan** (`/register/step-3`)
- "Skip for now" button always visible
- Plan cards: fetch from `GET /api/v1/arena/membership-plans` (public endpoint)
- Each card: plan name (Arabic primary), duration, price in SAR, features list
- Selecting a plan highlights it; submit posts `desiredMembershipPlanId`
- Guarded: if `registrationToken` missing → redirect to `/register/step-1`

**New: Registration Success screen** (`/register/success`)
- Illustration + heading: "Registration Received" (Arabic: "تم استلام طلبك")
- Body: "Our staff will review your registration and contact you within 24 hours."
- CTA: "Back to Login"
- Clears `registrationToken` from Zustand

### web-pulse

**New: Pending Members page** (`/members/pending`)
- Table: Name, Phone, Date of Birth, Desired Plan, Registered At, Actions
- Actions: Activate button, Reject button
- Search bar: filter by name or phone
- Pagination (page size 20)
- Empty state: "No pending registrations"

**New: Activate Modal**
- Shows member name + desired plan (if any)
- Plan dropdown: shows desired plan pre-selected, allows staff to change
- "Confirm Activation" button → calls `POST /members/{id}/activate`
- On success: row removed from pending list, success toast

**New: Reject Modal**
- Reason textarea (required, min 10 chars)
- "Confirm Rejection" button → calls `POST /members/{id}/reject`
- On success: row removed from pending list, warning toast

**Modified: Members sidebar nav item**
- Badge showing count of pending members (fetched alongside the main member list)
- Calls `GET /api/v1/members/pending?size=1` and reads `meta.totalCount` for the badge number
- Refresh every 60 seconds (same pattern as notification polling)

**Modified: Portal Settings screen** (web-pulse)
- Add toggle: "Allow member self-registration" (maps to `selfRegistrationEnabled`)
- Warning text below toggle: "Members who register online will appear in the Pending Members queue for your review before gaining access."

---

## Files to generate

### New backend files
```
backend/src/main/kotlin/com/liyaqa/member/
  MemberRegistrationIntent.kt
  MemberRegistrationIntentRepository.kt

backend/src/main/kotlin/com/liyaqa/arena/
  SelfRegistrationRequest.kt          (DTO)
  SelfRegistrationArenaController.kt

backend/src/main/kotlin/com/liyaqa/member/
  SelfRegistrationService.kt
  PendingMemberResponse.kt            (DTO)
  ActivateMemberRequest.kt            (DTO)

backend/src/main/resources/db/migration/
  V14__member_self_registration.sql

backend/src/test/kotlin/com/liyaqa/arena/
  SelfRegistrationArenaControllerTest.kt

backend/src/test/kotlin/com/liyaqa/member/
  SelfRegistrationServiceTest.kt
  MemberActivationPulseControllerTest.kt
```

### Modified backend files
```
MemberStatus.kt               — add pending_activation
ClubPortalSettings.kt         — add selfRegistrationEnabled field
MemberPulseController.kt      — add /pending, /{id}/activate, /{id}/reject
PortalSettingsArenaController.kt — expose selfRegistrationEnabled in public response
PortalSettingsPulseController.kt — accept selfRegistrationEnabled in PATCH
AuditAction.kt                — add MEMBER_SELF_REGISTERED, MEMBER_ACTIVATED
DevDataLoader.kt              — set selfRegistrationEnabled = true for demo club
```

### New frontend files — web-arena
```
src/pages/register/
  step-1.tsx        (phone OTP)
  step-2.tsx        (profile form)
  step-3.tsx        (plan selection)
  success.tsx       (confirmation screen)
src/components/register/
  StepIndicator.tsx
  PlanCard.tsx
src/api/
  register.ts       (3 API functions)
src/store/
  useRegistrationStore.ts   (Zustand: registrationToken + form data across steps)
src/__tests__/register/
  RegistrationWizard.test.tsx
```

### Modified frontend files — web-arena
```
src/pages/auth/login.tsx            — add registration link
src/router.tsx (or routes config)   — add /register/* routes (public, no auth guard)
src/api/portalSettings.ts           — include selfRegistrationEnabled
```

### New frontend files — web-pulse
```
src/pages/members/pending.tsx
src/components/members/
  ActivateModal.tsx
  RejectModal.tsx
  PendingMemberRow.tsx
src/api/pendingMembers.ts
src/__tests__/members/
  PendingMembers.test.tsx
```

### Modified frontend files — web-pulse
```
src/components/layout/Sidebar.tsx   — add pending count badge on Members nav
src/pages/settings/portal.tsx       — add selfRegistrationEnabled toggle
src/api/portalSettings.ts           — include selfRegistrationEnabled in PATCH body
```

---

## Implementation order

### Step 1 — Flyway V14 + entity changes
Create `V14__member_self_registration.sql`:
```sql
-- Add self_registration_enabled to club_portal_settings
ALTER TABLE club_portal_settings
    ADD COLUMN self_registration_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Member registration intents
CREATE TABLE member_registration_intents (
    id                          BIGSERIAL PRIMARY KEY,
    public_id                   UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    member_id                   BIGINT      NOT NULL REFERENCES members(id),
    member_public_id            UUID        NOT NULL,
    membership_plan_id          BIGINT      REFERENCES membership_plans(id),
    membership_plan_public_id   UUID,
    membership_plan_name_en     VARCHAR(200),
    membership_plan_name_ar     VARCHAR(200),
    membership_plan_price_halalas BIGINT,
    club_id                     BIGINT      NOT NULL,
    resolved_at                 TIMESTAMPTZ,
    resolved_by                 BIGINT      REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ
);

CREATE INDEX idx_mri_member_id   ON member_registration_intents(member_id);
CREATE INDEX idx_mri_club_id     ON member_registration_intents(club_id);
CREATE INDEX idx_mri_resolved_at ON member_registration_intents(resolved_at)
    WHERE resolved_at IS NULL;
```

Update `MemberStatus.kt` → add `pending_activation`.
Update `ClubPortalSettings.kt` → add `selfRegistrationEnabled`.
Update `MemberRegistrationIntent.kt` + `MemberRegistrationIntentRepository.kt`.
Verify: `./gradlew test` — all existing tests still pass.

---

### Step 2 — OTP registration purpose
Add `purpose: String = "login"` param to existing OTP request/verify endpoints.
When `purpose = "registration"`:
- `requestOtp` — same logic as login but does NOT require an existing active member.
  Any phone can request a registration OTP.
- `verifyOtp` (registration) — after verifying hash + TTL + rate-limit:
  - If member exists with `status = active` or `frozen` → return
    `409` with `"Phone already registered — please log in"`.
  - If member exists with `status = pending_activation` → return
    `409` with `"Registration already pending staff review"`.
  - Otherwise → issue a `registrationToken` (JWT, `scope = "registration"`,
    `phone` claim, `clubId` claim, 15-min expiry).
  - Mark `MemberOtp.used = true`.

Update `JwtService` to support issuing registration-scoped tokens.
Update `MemberAuthArenaController` (or split into `SelfRegistrationArenaController`).
Verify: existing OTP login tests still pass.

---

### Step 3 — `SelfRegistrationService`
Implement all 10 business rules.

Key flow:
1. Validate `registrationToken` (scope = "registration") — extract `phone` + `clubId`.
2. Validate `selfRegistrationEnabled = true` (business rule 1).
3. Phone uniqueness check (business rule 2).
4. Validate `desiredMembershipPlanId` if provided (business rule 6).
5. Create `Member` with `status = "pending_activation"`.
6. Create `MemberRegistrationIntent` if plan desired.
7. Publish `MemberJoinedEvent` — existing notification flow fires automatically.
8. Call `auditService.log(MEMBER_SELF_REGISTERED, ...)`.
9. Return `{ memberId: UUID, status: "pending_activation" }`.

Verify: `./gradlew test`

---

### Step 4 — `SelfRegistrationArenaController`
3 endpoints (no `@PreAuthorize` — public):
- `POST /api/v1/arena/register/otp/request` — delegates to existing OTP service with `purpose=registration`
- `POST /api/v1/arena/register/otp/verify` — returns `registrationToken`
- `POST /api/v1/arena/register/complete` — validates `scope=registration` JWT, delegates to `SelfRegistrationService`

For `complete`, manually validate JWT scope in the controller (cannot use standard
`@PreAuthorize` — no `roleId` in registration JWT):
```kotlin
@PostMapping("/complete")
fun complete(
    @RequestHeader("Authorization") authHeader: String,
    @Valid @RequestBody request: SelfRegistrationRequest
): ResponseEntity<RegistrationCompleteResponse> {
    val token = authHeader.removePrefix("Bearer ")
    val claims = jwtService.parseRegistrationToken(token) // throws 401 if invalid/expired/wrong scope
    return ResponseEntity.status(201).body(selfRegistrationService.register(claims, request))
}
```

Verify: `./gradlew test`

---

### Step 5 — Member activation endpoints
Add to `MemberPulseController`:
- `GET /api/v1/members/pending` — `@PreAuthorize("hasPermission(null, 'member:read')")`
  Returns `Page<PendingMemberResponse>` including intent data (joined query).
  Filter: `status = pending_activation`, scoped to `TenantContext.clubId`.

- `POST /api/v1/members/{id}/activate` — `@PreAuthorize("hasPermission(null, 'member:create')")`
  Calls `MemberService.activate(memberPublicId, request)`.
  On success: audit `MEMBER_ACTIVATED`, return updated `MemberResponse`.

- `POST /api/v1/members/{id}/reject` — `@PreAuthorize("hasPermission(null, 'member:create')")`
  Calls `MemberService.reject(memberPublicId, reason, staffUserId)`.
  Creates `MemberNote`, sets `status = terminated`, resolves intent.

Verify: `./gradlew test`

---

### Step 6 — OTP login guard for pending members
In `MemberAuthArenaController.verifyOtp` (the login purpose path):
After fetching member by phone + clubId, add check:
```kotlin
if (member.status == MemberStatus.pending_activation) {
    throw ArenaException(HttpStatus.UNAUTHORIZED,
        "Your registration is pending staff approval")
}
```

Verify: existing login tests still pass + new test for pending_activation rejection.

---

### Step 7 — Portal settings exposure
Update `PortalSettingsArenaController`:
- Public GET endpoint response includes `selfRegistrationEnabled`.

Update `PortalSettingsPulseController`:
- `PATCH` body accepts `selfRegistrationEnabled?: Boolean`.

Update `ClubPortalSettingsService` accordingly.
Verify: `./gradlew test`

---

### Step 8 — Integration tests

**`SelfRegistrationArenaControllerTest`** (covers rules 1, 2, 3, 4, 5, 6, 10):
```
- registrationDisabled_returns403
- duplicatePhone_activeStatus_returns409
- duplicatePhone_pendingStatus_returns409
- invalidRegistrationToken_returns401
- replayedToken_returns401
- bothNamesBlank_returns422
- planFromOtherClub_returns422
- validRegistration_withPlan_returns201
- validRegistration_withoutPlan_returns201
- pendingMemberCannotLogin_returns401
```

**`SelfRegistrationServiceTest`** (unit, rules 1–10):
```
- selfRegistrationDisabled_throwsForbidden
- phoneAlreadyUsed_sameClub_throwsConflict
- phoneUsed_differentClub_allowed
- planNotInClub_throwsUnprocessable
- nameValidation_bothBlank_throwsUnprocessable
- successWithPlan_createsIntentAndPublishesEvent
- successWithoutPlan_noIntentCreated
```

**`MemberActivationPulseControllerTest`** (covers rules 7, 8, 9):
```
- activate_setsStatusActive
- activate_withPlan_createsPendingMembership
- activate_wrongClub_returns404
- activate_alreadyActive_returns409
- reject_createsNoteAndTerminates
- reject_alreadyActive_returns409
- pendingList_onlyShowsPendingStatus
- pendingList_scopedToClub
```

Verify: `./gradlew test` — all tests pass, ktlint clean.

---

### Step 9 — web-arena: registration store + API module

**`useRegistrationStore.ts`** (Zustand):
```typescript
interface RegistrationState {
  registrationToken: string | null
  phone: string | null
  step: 1 | 2 | 3
  profileData: ProfileFormData | null
  setToken: (token: string, phone: string) => void
  setProfile: (data: ProfileFormData) => void
  advance: () => void
  reset: () => void
}
```

**`src/api/register.ts`**:
```typescript
requestRegistrationOtp(phone: string, clubPortalSlug: string): Promise<void>
verifyRegistrationOtp(phone: string, otp: string, clubPortalSlug: string): Promise<{ registrationToken: string }>
completeRegistration(token: string, data: SelfRegistrationRequest): Promise<{ memberId: string, status: string }>
```

Verify: TypeScript strict + lint pass.

---

### Step 10 — web-arena: registration wizard screens

**`/register` route** — public (no auth guard, before the login redirect).
Add to router config.

**Step 1** (`step-1.tsx`):
- Phone form → OTP form (same pattern as login)
- On `verifyRegistrationOtp` success → store token → navigate to `/register/step-2`
- Show error if phone already registered (409 → redirect to login with toast)

**Step 2** (`step-2.tsx`):
- Profile form with Zod schema (nameAr required, others validated)
- On submit → save to store → navigate to `/register/step-3`
- Back button → `/register/step-1`

**Step 3** (`step-3.tsx`):
- Fetch plans from `GET /api/v1/arena/membership-plans` (already exists)
- `PlanCard` component: plan name (Arabic primary), duration badge, price SAR, select state
- "Skip for now" → `completeRegistration` with `desiredMembershipPlanId: null`
- "Continue with plan" → `completeRegistration` with selected plan ID
- On success → navigate to `/register/success`

**`success.tsx`**: static confirmation, CTA back to login.

**`StepIndicator.tsx`**: visual step 1/2/3 dots.

**Modify `src/pages/auth/login.tsx`**:
- Fetch portal settings on mount (already done for other feature flags)
- Conditionally render "Register" link if `selfRegistrationEnabled = true`

Add Arabic + English i18n strings for all new UI text.
Verify: `npm run typecheck && npm run lint && npm test && npm run build`

---

### Step 11 — web-pulse: pending members queue

**`/members/pending`** route — existing `member:read` permission gate.

**`pending.tsx`**:
```typescript
// TanStack Query
const { data: pendingMembers } = useQuery({
  queryKey: ['members', 'pending'],
  queryFn: () => api.getPendingMembers({ search, page }),
})
```

**`ActivateModal.tsx`**:
- Fetches membership plans for dropdown
- Pre-selects the intended plan (from intent)
- On confirm → `POST /members/{id}/activate` → invalidate `['members', 'pending']`

**`RejectModal.tsx`**:
- Reason textarea (Zod: min 10 chars)
- On confirm → `POST /members/{id}/reject` → invalidate `['members', 'pending']`

**Modify `Sidebar.tsx`**:
- Pending badge: `useQuery` on `GET /members/pending?size=1` for total count
- `refetchInterval: 60_000`
- Show red dot badge only when count > 0

**Modify `src/pages/settings/portal.tsx`**:
- Add `selfRegistrationEnabled` toggle with `Switch` component
- Warning text below toggle (Arabic + English)
- Include in PATCH payload

Add i18n strings (ar + en) for all new pulse UI.
Verify: `npm run typecheck && npm run lint && npm test && npm run build`

---

### Step 12 — Final verification + DevDataLoader

Update `DevDataLoader`:
- Set `selfRegistrationEnabled = true` for the demo club.

Run full test suite:
```bash
./gradlew test
cd web-arena && npm run typecheck && npm run lint && npm test && npm run build
cd web-pulse && npm run typecheck && npm run lint && npm test && npm run build
```

Confirm:
- All backend tests pass (437+)
- ktlint clean
- web-arena builds clean with registration wizard routes
- web-pulse builds clean with pending queue
- No PLAN.md references left in code

---

## Acceptance criteria

### Backend
- [ ] `POST /api/v1/arena/register/complete` returns 201 with `pending_activation` status
- [ ] Duplicate phone in same club returns 409
- [ ] `selfRegistrationEnabled = false` returns 403
- [ ] Invalid / expired registration token returns 401
- [ ] Pending member attempting login returns 401 with clear message
- [ ] `POST /members/{id}/activate` sets status to `active`, optionally creates `Membership`
- [ ] `POST /members/{id}/reject` sets status to `terminated`, creates `MemberNote`
- [ ] `MemberJoinedEvent` published on self-registration → staff notification fires
- [ ] 2 new AuditAction codes present and wired
- [ ] All new + existing tests pass; ktlint clean

### Frontend
- [ ] "Register" link hidden when `selfRegistrationEnabled = false` in web-arena
- [ ] Registration wizard navigates steps in order; back navigation works
- [ ] Step guard: accessing step 2 or 3 without token redirects to step 1
- [ ] Success screen shows correct Arabic confirmation message
- [ ] web-pulse pending queue shows only `pending_activation` members
- [ ] Activate modal pre-populates intended plan
- [ ] Reject modal requires reason (min 10 chars)
- [ ] Sidebar badge shows pending count; refreshes every 60 seconds
- [ ] Portal settings toggle persists `selfRegistrationEnabled`

---

## RBAC matrix rows added by this plan

| Permission | Super Admin | Owner | Branch Manager | Receptionist | Sales Agent | PT Trainer | GX Instructor |
|---|---|---|---|---|---|---|---|
| `member:read` (pending list) | ✓ (nexus) | ✓ | ✓ | ✓ | — | — | — |
| `member:create` (activate/reject) | — | ✓ | ✓ | — | — | — | — |

No new permission codes needed — `member:read` and `member:create` already exist
and already gate the relevant endpoints.

---

## Definition of done
- All acceptance criteria checked
- All tests pass (`./gradlew test`, `npm test` in web-arena + web-pulse)
- ktlint clean
- PLAN.md deleted before merging
- PR title: `feat(member): self-registration with pending activation queue`
- Target branch: `develop`
