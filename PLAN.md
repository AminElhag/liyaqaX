# PLAN.md — Audit Logging

## Status
Ready for implementation

## Branch
feat/audit-logging

## Goal
Add a persistent, tamper-evident audit trail to the Liyaqa platform. Every
significant write operation across all domains (members, memberships, payments,
invoices, staff, trainers, GX bookings, PT sessions, leads, cash drawer) is
recorded in an `AuditLog` entity with actor, action, entity type, entity ID,
old/new value snapshot, IP address, and timestamp. The web-nexus audit screen
(already built and returning a graceful empty list) lights up automatically
once this plan ships.

## Context
- All major entities and services are built and in production use.
- `AuditNexusController` already exists and returns an empty page with
  `meta.note = "Audit logging will be available in a future release"`.
  Once `AuditLog` exists, it will serve real data with no frontend changes needed.
- `audit:read` permission already exists on Super Admin and Read-Only Auditor roles.
- The backend uses Spring's `@Transactional` services — audit records are written
  within the same transaction as the business operation (same DB write, guaranteed
  consistency). If the transaction rolls back, the audit record rolls back too.
- `ddl-auto: create-drop` in dev — no Flyway migration needed for dev.
  Production uses Flyway — this plan introduces V10.
- JWT claims carry `sub` (userId UUID) and `scope` — sufficient to identify actor.

---

## Scope — what this plan covers

### Backend
- [ ] `AuditLog.kt` entity + `AuditLogRepository.kt`
- [ ] `AuditService.kt` — single `log()` method called from all service layers
- [ ] `AuditAction.kt` — sealed enum of all auditable action codes
- [ ] Wire audit calls into existing services:
  - MemberService — created, updated, deleted
  - MembershipService — assigned, renewed, frozen, unfrozen, terminated
  - PaymentService — collected, refunded
  - StaffService — created, updated, deleted
  - TrainerService — created, updated, deleted
  - GXBookingService (arena) — booked, cancelled
  - PTSession (coach) — attendance marked
  - LeadService — created, updated, converted, lost
  - CashDrawerService — session opened, session closed, entry added
  - MemberOtpService (arena auth) — member login
- [ ] Update `AuditNexusController` to serve real data (remove meta.note)
- [ ] `AuditNexusController` filters: date range, actorId, action, entityType
- [ ] Flyway V10 migration: `audit_logs` table
- [ ] Unit tests: `AuditServiceTest`
- [ ] Integration tests: `AuditNexusControllerTest` + spot-check that key
  services write audit records (MemberServiceAuditTest, MembershipServiceAuditTest)

### Frontend
- No frontend changes required. web-nexus audit screen already built.
  The `meta.note` banner will disappear automatically once the backend
  returns real records.

---

## Out of scope — do not implement in this plan
- Audit log viewer in web-pulse (staff-facing) — future plan
- Audit log export to CSV — future plan
- Real-time audit streaming / webhooks — future plan
- Login failures / security events audit — separate security event log
- Read operation logging (only writes are audited)
- GDPR right-to-erasure for audit logs — future legal plan

---

## Decisions already made

- **Same-transaction write**: `AuditService.log()` is called from within
  `@Transactional` service methods, BEFORE the service method returns.
  This means the audit record and the business record commit or roll back
  together. No eventual consistency, no async queue needed at this scale.

- **Actor from SecurityContext**: `AuditService` calls
  `SecurityContextHolder.getContext().authentication` to get the current
  JWT principal. Extracts `userId` (sub claim) and `scope`. Works for all
  JWT types (platform, club, trainer, member). For system-initiated operations
  (DevDataLoader, scheduled tasks) actor is set to `"system"`.

- **Old/new value as JSON snapshot**: `changesJson` stores a compact JSON
  string of what changed: `{"field": ["oldValue", "newValue"], ...}`.
  Only changed fields are included — not the full entity. Max 4000 chars.
  Truncated with `"...(truncated)"` if over limit. Never store passwords,
  OTP hashes, or JWT tokens in changesJson.

- **No soft delete on AuditLog**: audit records are immutable. No
  `deleted_at`, no update after insert. The table is append-only.
  `AuditLog` does NOT extend `AuditEntity` (which adds `deleted_at`) —
  it extends a minimal `BaseAuditLog` with only `id` (Long PK) and
  `publicId` (UUID). No `createdBy` / `updatedBy` fields on the audit
  log itself (that would be recursive).

- **Flyway V10**: this is the first plan since V9 (cash drawer). V7 was
  skipped. Next available version is V10.

- **AuditAction enum** — string codes stored in DB as VARCHAR(100):
  ```
  MEMBER_CREATED, MEMBER_UPDATED, MEMBER_DELETED
  MEMBERSHIP_ASSIGNED, MEMBERSHIP_RENEWED, MEMBERSHIP_FROZEN,
    MEMBERSHIP_UNFROZEN, MEMBERSHIP_TERMINATED
  PAYMENT_COLLECTED, PAYMENT_REFUNDED
  STAFF_CREATED, STAFF_UPDATED, STAFF_DELETED
  TRAINER_CREATED, TRAINER_UPDATED, TRAINER_DELETED
  GX_BOOKED, GX_BOOKING_CANCELLED
  PT_ATTENDANCE_MARKED
  LEAD_CREATED, LEAD_UPDATED, LEAD_CONVERTED, LEAD_LOST
  CASH_DRAWER_SESSION_OPENED, CASH_DRAWER_SESSION_CLOSED, CASH_DRAWER_ENTRY_ADDED
  MEMBER_LOGIN
  ```

- **IP address best-effort**: extracted from `X-Forwarded-For` header if
  present, else `HttpServletRequest.remoteAddr`. Nullable — not available
  for internal/system operations. Stored as VARCHAR(45) (supports IPv6).

- **entityType + entityId**: `entityType` is the domain name as a string
  (e.g., `"Member"`, `"Membership"`, `"Payment"`). `entityId` is the
  `publicId` UUID of the affected record. Both are VARCHAR — no FK constraint
  (avoids cascade delete issues; audit log must survive entity deletion).

---

## Entity design

### AuditLog

Does NOT extend `AuditEntity`. Minimal custom base only.

```
id                BIGINT PK auto-increment       (internal, never exposed)
public_id         UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()
actor_id          VARCHAR(100) NOT NULL           (userId UUID or "system")
actor_scope       VARCHAR(20) NOT NULL            ("platform"|"club"|"trainer"|"member"|"system")
action            VARCHAR(100) NOT NULL           (AuditAction enum value)
entity_type       VARCHAR(100) NOT NULL           ("Member", "Membership", etc.)
entity_id         VARCHAR(100) NOT NULL           (publicId UUID of affected record)
organization_id   VARCHAR(100)                    nullable (UUID, for tenant filtering)
club_id           VARCHAR(100)                    nullable (UUID)
changes_json      TEXT                            nullable (compact JSON of changes)
ip_address        VARCHAR(45)                     nullable
created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

No `updated_at`, no `deleted_at`, no `created_by`. Append-only.

### Flyway V10 migration

```sql
CREATE TABLE audit_logs (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    actor_id          VARCHAR(100) NOT NULL,
    actor_scope       VARCHAR(20) NOT NULL,
    action            VARCHAR(100) NOT NULL,
    entity_type       VARCHAR(100) NOT NULL,
    entity_id         VARCHAR(100) NOT NULL,
    organization_id   VARCHAR(100),
    club_id           VARCHAR(100),
    changes_json      TEXT,
    ip_address        VARCHAR(45),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_actor_id    ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action      ON audit_logs(action);
CREATE INDEX idx_audit_logs_entity      ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_org_id      ON audit_logs(organization_id);
CREATE INDEX idx_audit_logs_created_at  ON audit_logs(created_at DESC);
```

---

## API endpoints

### AuditNexusController — `/api/v1/nexus/audit` (UPDATE existing)

```
GET /api/v1/nexus/audit
  ?page=0&size=20
  &actorId=uuid           (optional — filter by actor)
  &action=MEMBER_CREATED  (optional — filter by action code)
  &entityType=Member      (optional — filter by entity type)
  &organizationId=uuid    (optional — filter by org)
  &from=yyyy-MM-dd        (optional — created_at >= from)
  &to=yyyy-MM-dd          (optional — created_at <= to + end of day)
```

Returns standard Spring `Page<AuditLogResponse>`. No more `meta.note`.
Required permission: `audit:read`.

---

## Request / Response shapes

### AuditLogResponse
```json
{
  "id": "uuid",
  "actorId": "uuid | system",
  "actorScope": "platform | club | trainer | member | system",
  "action": "MEMBER_CREATED",
  "entityType": "Member",
  "entityId": "uuid",
  "organizationId": "uuid | null",
  "clubId": "uuid | null",
  "changesJson": "{\"firstName\": [\"Ali\", \"Ahmed\"]}",
  "ipAddress": "192.168.1.1 | null",
  "createdAt": "ISO 8601"
}
```

---

## Business rules — enforce in service layer

1. **Audit is fire-and-forget within transaction**: `AuditService.log()` never
   throws. If building the audit record fails for any reason (e.g., missing
   claim), it logs a WARN and returns — it must never cause the business
   operation to fail.

2. **Never audit reads**: only write operations (create, update, delete,
   state transitions) are audited. No GET endpoint triggers an audit record.

3. **Never store sensitive data in changesJson**: passwords, OTP hashes,
   JWT tokens, and full payment card numbers must never appear. For Payment
   records, log `amountHalalas` and `paymentMethod` only — never raw card data.

4. **Truncate changesJson at 4000 chars**: if the serialized changes JSON
   exceeds 4000 characters, truncate and append `"...(truncated)"`.

5. **System actor**: DevDataLoader and any background job calls `AuditService`
   with `actorId = "system"`, `actorScope = "system"`. Never null actor.

6. **Filters use nativeQuery = true**: all date-range and multi-field filter
   queries use `nativeQuery = true` with a `countQuery` for pagination.
   No JPQL date arithmetic.

7. **organizationId / clubId on audit record**: extracted from the JWT claims
   when available (`organizationId`, `clubId`). Null for platform-scope actors
   acting outside a specific org. Used for tenant-scoped filtering in the
   nexus audit screen.

8. **Member login audited**: `MemberAuthService.verifyOtp()` success path
   calls `AuditService.log(MEMBER_LOGIN, "Member", memberId, ...)`.
   Failed OTP attempts are NOT audited (avoid polluting log with noise).

---

## Seed data updates

No new seed data. DevDataLoader already creates all entities — once the audit
table exists, the seeded operations will write audit records automatically
(since `AuditService.log()` is called from within the service methods).

---

## Files to generate

### Backend — new files
```
audit/
  AuditLog.kt
  AuditLogRepository.kt
  AuditService.kt
  AuditAction.kt              (enum/sealed class with all action codes)
  dto/
    AuditLogResponse.kt
```

### Backend — modified files
```
nexus/AuditNexusController.kt         replace empty stub with real paginated query
nexus/dto/AuditLogResponse.kt         (already exists — verify fields match, update if needed)
member/MemberService.kt               add audit calls: MEMBER_CREATED/UPDATED/DELETED
membership/MembershipService.kt       add audit calls: 5 membership actions
payment/PaymentService.kt             add audit calls: PAYMENT_COLLECTED/REFUNDED
staff/StaffService.kt                 add audit calls: STAFF_CREATED/UPDATED/DELETED
trainer/TrainerService.kt             add audit calls: TRAINER_CREATED/UPDATED/DELETED
arena/GxArenaService.kt               add audit calls: GX_BOOKED/GX_BOOKING_CANCELLED
coach/PtCoachService.kt               add audit call: PT_ATTENDANCE_MARKED
lead/LeadService.kt                   add audit calls: LEAD_CREATED/UPDATED/CONVERTED/LOST
cashdrawer/CashDrawerService.kt       add audit calls: 3 cash drawer actions
auth/MemberAuthService.kt             add audit call: MEMBER_LOGIN on successful verify
resources/db/migration/V10__audit_logs.sql
```

---

## Implementation order

```
Step 1 — AuditLog entity + AuditAction enum + AuditService
  audit/AuditAction.kt — enum with all 24 action codes as strings
  audit/AuditLog.kt — append-only entity, no AuditEntity extension,
    custom base with id (Long) + publicId (UUID) + createdAt only
  audit/AuditLogRepository.kt — save() + paginated filtered query
    (nativeQuery=true with countQuery, all filter params nullable)
  audit/AuditService.kt:
    fun log(action, entityType, entityId, actorId, actorScope,
            organizationId?, clubId?, changesJson?, ipAddress?)
    Never throws — catches all exceptions, logs WARN (rule 1)
    Truncates changesJson > 4000 chars (rule 4)
  Verify: ./gradlew build -x test

Step 2 — Flyway V10 migration
  resources/db/migration/V10__audit_logs.sql
  CREATE TABLE audit_logs with all columns + 5 indexes
  Verify: ./gradlew flywayMigrate (staging/test DB)
  Dev uses ddl-auto: create-drop — no manual migration needed in dev

Step 3 — Wire audit into member + membership + payment services
  MemberService.kt:
    createMember() → log(MEMBER_CREATED, "Member", member.publicId, ...)
    updateMember() → log(MEMBER_UPDATED, changes snapshot)
    deleteMember() → log(MEMBER_DELETED)
  MembershipService.kt:
    assignMembership() → MEMBERSHIP_ASSIGNED
    renewMembership() → MEMBERSHIP_RENEWED
    freezeMembership() → MEMBERSHIP_FROZEN
    unfreezeMembership() → MEMBERSHIP_UNFROZEN
    terminateMembership() → MEMBERSHIP_TERMINATED
  PaymentService.kt:
    collectPayment() → PAYMENT_COLLECTED (log amountHalalas + paymentMethod only)
    refundPayment() → PAYMENT_REFUNDED
  Verify: ./gradlew build -x test

Step 4 — Wire audit into staff + trainer + lead + cash drawer services
  StaffService.kt → STAFF_CREATED/UPDATED/DELETED
  TrainerService.kt → TRAINER_CREATED/UPDATED/DELETED
  LeadService.kt → LEAD_CREATED/UPDATED/CONVERTED/LOST
  CashDrawerService.kt:
    openSession() → CASH_DRAWER_SESSION_OPENED
    closeSession() → CASH_DRAWER_SESSION_CLOSED
    addEntry() → CASH_DRAWER_ENTRY_ADDED
  Verify: ./gradlew build -x test

Step 5 — Wire audit into arena + coach services
  GxArenaService.kt (arena booking):
    bookClass() → GX_BOOKED
    cancelBooking() → GX_BOOKING_CANCELLED
  PtCoachService.kt:
    markAttendance() → PT_ATTENDANCE_MARKED (log sessionId + new status)
  MemberAuthService.kt:
    verifyOtp() success path → MEMBER_LOGIN (rule 8)
    Failed OTP → NOT audited
  Verify: ./gradlew build -x test

Step 6 — Update AuditNexusController
  Replace the graceful-empty stub with real implementation:
    GET /nexus/audit with all filter params (actorId, action, entityType,
    organizationId, from, to, page, size)
  All filtering via nativeQuery=true with countQuery (rule 6)
  Remove meta.note from response — return standard Page<AuditLogResponse>
  Verify: ./gradlew build -x test
  Manual: ./gradlew bootRun → POST login as admin → GET /nexus/audit
    → should see MEMBER_LOGIN + DevDataLoader seed audit records

Step 7 — Backend tests
  AuditServiceTest.kt (unit):
    - log() persists record with correct fields
    - log() never throws even when AuditLogRepository throws (rule 1)
    - changesJson truncated at 4000 chars (rule 4)
    - system actor accepted (rule 5)
  MemberServiceAuditTest.kt (integration):
    - createMember() produces MEMBER_CREATED audit record
    - updateMember() produces MEMBER_UPDATED with changes snapshot
    - deleteMember() produces MEMBER_DELETED
  MembershipServiceAuditTest.kt (integration):
    - assignMembership() → MEMBERSHIP_ASSIGNED record exists
    - terminateMembership() → MEMBERSHIP_TERMINATED record exists
  AuditNexusControllerTest.kt (integration):
    - GET /nexus/audit returns paginated records
    - filter by action=MEMBER_CREATED returns only that action
    - filter by from/to returns correct date range
    - filter by organizationId scopes correctly
    - non-audit:read user returns 403
    - pagination: page=0&size=5 returns correct slice
  Verify: ./gradlew test --no-daemon

Step 8 — Backend final checks
  ./gradlew ktlintFormat --no-daemon
  ./gradlew ktlintCheck --no-daemon
  ./gradlew build --no-daemon
```

---

## Acceptance criteria

### Backend
- [ ] `audit_logs` table created by V10 migration with all 5 indexes
- [ ] Creating a member writes a `MEMBER_CREATED` audit record
- [ ] Assigning a membership writes a `MEMBERSHIP_ASSIGNED` audit record
- [ ] Collecting a payment writes a `PAYMENT_COLLECTED` record with amount only (no card data)
- [ ] GX booking writes `GX_BOOKED`, cancellation writes `GX_BOOKING_CANCELLED`
- [ ] Member OTP login success writes `MEMBER_LOGIN`
- [ ] Failed OTP does NOT write any audit record
- [ ] `AuditService.log()` never causes a business operation to fail (exception swallowed)
- [ ] changesJson over 4000 chars is truncated with `...(truncated)`
- [ ] `GET /nexus/audit` returns real paginated records (no meta.note)
- [ ] `GET /nexus/audit?action=MEMBER_CREATED` filters correctly
- [ ] `GET /nexus/audit?from=2026-01-01&to=2026-01-31` filters by date range
- [ ] Support Agent calling `GET /nexus/audit` returns 403 (no audit:read)
- [ ] All 344+ existing tests still pass

### Frontend
- No new acceptance criteria — web-nexus audit screen already built.
  Verify manually: audit screen in web-nexus shows real records after login
  and a few operations. The `meta.note` info banner no longer appears.

---

## RBAC matrix

No new permissions. `audit:read` already exists on Super Admin and
Read-Only Auditor from the web-nexus plan.

---

## Definition of done

- All acceptance criteria checked
- AuditService.log() verified to never throw (unit test with mock that throws)
- At least 10 action types covered by audit wiring
- changesJson truncation tested at exactly 4000 and 4001 chars
- web-nexus audit screen shows real data end-to-end (manual verify)
- All CI checks pass on PR
- PLAN.md deleted before merging
- PR title: `feat(audit): add persistent audit trail across all write operations`
- Target branch: `develop`

