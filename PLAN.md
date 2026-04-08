# Plan 30 — Liyaqa Subscription Billing (Platform Layer)

## Status
Ready for implementation

## Branch
`feature/plan-30-subscription-billing`

## Goal
Add the SaaS billing layer that gates all club-scoped API access behind an active subscription. Platform admins assign plans to clubs via web-nexus. Expired clubs get a 7-day grace period, then receive 402 on all requests (staff + members). Usage limits (max branches, max staff) are enforced at plan level.

## Context
- `Club`, `Organization`, `Branch`, `StaffMember` entities already exist
- `User` entity has `scope = "platform"` for Super Admin / Support Agent roles
- Notification system (Plan 21) exists — `SUBSCRIPTION_EXPIRING_SOON` wires into it
- Redis already used for RBAC permission caching — reuse same `RedisTemplate` for subscription cache
- `Payment` entity exists for member payments — no dependency here (platform billing is manual invoice, no card)
- Next Flyway migration: **V23**

---

## Scope — what this plan covers

- [ ] Flyway V23 — `subscription_plans` + `club_subscriptions` tables
- [ ] `SubscriptionPlan` entity
- [ ] `ClubSubscription` entity
- [ ] `SubscriptionEnforcementInterceptor` — reads club subscription status on every club-scoped request
- [ ] Redis cache for subscription status: key `subscription_status:{clubId}`, TTL 5 minutes
- [ ] `SubscriptionService` — CRUD for plans, assign to club, extend/cancel, status check
- [ ] Plan limit enforcement in `BranchService` and `StaffMemberService`
- [ ] Daily scheduler: transition ACTIVE → GRACE → EXPIRED, fire expiry notifications
- [ ] New notification types: `SUBSCRIPTION_EXPIRING_SOON_14`, `SUBSCRIPTION_EXPIRING_SOON_7`, `SUBSCRIPTION_EXPIRED`
- [ ] New audit actions: `SUBSCRIPTION_PLAN_CREATED`, `SUBSCRIPTION_PLAN_UPDATED`, `SUBSCRIPTION_ASSIGNED`, `SUBSCRIPTION_EXTENDED`, `SUBSCRIPTION_CANCELLED`
- [ ] New permissions: `subscription:manage` (Super Admin), `subscription:read` (Super Admin, Support Agent)
- [ ] 10 endpoints (nexus only)
- [ ] web-nexus: Subscriptions section — plan catalog, per-club assignment, expiry dashboard
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- Automated card charging or payment processing for platform billing
- Self-serve plan selection by club owners
- Usage reporting beyond branch + staff counts
- Proration / partial period billing
- Plan upgrade/downgrade flows

---

## Decisions already made

- **Subscription states**: `ACTIVE`, `GRACE`, `EXPIRED`, `CANCELLED`. Scheduler moves ACTIVE → GRACE (at `currentPeriodEnd`), GRACE → EXPIRED (at `gracePeriodEndsAt`). Cancelled stops enforcement (treated same as EXPIRED for access — blocks new access but does not delete data).
- **Grace period**: `gracePeriodEndsAt = currentPeriodEnd + 7 days`. Set at the same time as `currentPeriodEnd`.
- **Enforcement**: `SubscriptionEnforcementInterceptor` intercepts all requests containing `clubId` JWT claim. Checks Redis cache → falls back to DB. If status is `EXPIRED` or `CANCELLED` → 402 with `errorCode: SUBSCRIPTION_EXPIRED`. If `GRACE` → passes request but appends `X-Subscription-Grace: true` response header.
- **Full lockout**: both `scope = "club"` (staff/pulse) and `scope = "member"` (arena) requests are intercepted. No exemptions for members.
- **Plan limits**: `maxBranches` and `maxStaff` on `SubscriptionPlan`. Value `0` means unlimited (used for Enterprise). Checked in `BranchService.createBranch()` and `StaffMemberService.createStaffMember()` → 402 `PLAN_LIMIT_EXCEEDED`.
- **Seeded plans**:
  - Starter: 500 SAR/month, maxBranches = 1, maxStaff = 10
  - Growth: 1200 SAR/month, maxBranches = 3, maxStaff = 30
  - Enterprise: 3000 SAR/month, maxBranches = 0 (unlimited), maxStaff = 0 (unlimited)
- **Two expiry notifications**: scheduler fires `SUBSCRIPTION_EXPIRING_SOON_14` at `currentPeriodEnd - 14 days` and `SUBSCRIPTION_EXPIRING_SOON_7` at `currentPeriodEnd - 7 days`. Both go to all `scope = "platform"` users with `subscription:read` permission. Deduplication: check if the same notification type was already sent for this clubId in the last 24 hours before sending.
- **Permissions**: `subscription:manage` → Super Admin only. `subscription:read` → Super Admin + Support Agent.
- **Dev seed**: Elixir Gym is seeded with an ACTIVE Growth subscription, `currentPeriodEnd = today + 30 days`.
- **Flyway V23**

---

## Entity design

### SubscriptionPlan

```kotlin
@Entity
@Table(name = "subscription_plans")
class SubscriptionPlan(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    // Monthly price in halalas (1 SAR = 100 halalas)
    @Column(name = "monthly_price_halalas", nullable = false)
    var monthlyPriceHalalas: Long,

    // 0 = unlimited
    @Column(name = "max_branches", nullable = false)
    var maxBranches: Int,

    // 0 = unlimited
    @Column(name = "max_staff", nullable = false)
    var maxStaff: Int,

    @Column(name = "features", columnDefinition = "jsonb")
    var features: String? = null,   // JSON string, e.g. {"gxBooking": true, "csvImport": true}

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
)
```

### ClubSubscription

```kotlin
@Entity
@Table(name = "club_subscriptions")
class ClubSubscription(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    @Column(name = "club_id", nullable = false)
    val clubId: Long,

    @Column(name = "plan_id", nullable = false)
    var planId: Long,

    // ACTIVE | GRACE | EXPIRED | CANCELLED
    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    @Column(name = "current_period_start", nullable = false)
    var currentPeriodStart: Instant,

    @Column(name = "current_period_end", nullable = false)
    var currentPeriodEnd: Instant,

    // currentPeriodEnd + 7 days
    @Column(name = "grace_period_ends_at", nullable = false)
    var gracePeriodEndsAt: Instant,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "assigned_by_user_id", nullable = false, updatable = false)
    val assignedByUserId: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
```

No `deletedAt` on `ClubSubscription` — status transitions only. A club can only have one non-CANCELLED subscription at a time (enforced in service layer).

---

## Flyway V23

```sql
-- V23__subscription_billing.sql

CREATE TABLE subscription_plans (
    id                      BIGSERIAL PRIMARY KEY,
    public_id               UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                    VARCHAR(100) NOT NULL,
    monthly_price_halalas   BIGINT NOT NULL,
    max_branches            INT NOT NULL DEFAULT 0,
    max_staff               INT NOT NULL DEFAULT 0,
    features                JSONB,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at              TIMESTAMPTZ
);

CREATE INDEX idx_subscription_plans_active ON subscription_plans(is_active) WHERE deleted_at IS NULL;

CREATE TABLE club_subscriptions (
    id                      BIGSERIAL PRIMARY KEY,
    public_id               UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    club_id                 BIGINT NOT NULL REFERENCES clubs(id),
    plan_id                 BIGINT NOT NULL REFERENCES subscription_plans(id),
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_period_start    TIMESTAMPTZ NOT NULL,
    current_period_end      TIMESTAMPTZ NOT NULL,
    grace_period_ends_at    TIMESTAMPTZ NOT NULL,
    cancelled_at            TIMESTAMPTZ,
    assigned_by_user_id     BIGINT NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_club_subscription_active
    ON club_subscriptions(club_id)
    WHERE status NOT IN ('CANCELLED', 'EXPIRED');

CREATE INDEX idx_club_subscription_club_id   ON club_subscriptions(club_id);
CREATE INDEX idx_club_subscription_status    ON club_subscriptions(status, current_period_end);
CREATE INDEX idx_club_subscription_expiry    ON club_subscriptions(current_period_end)
    WHERE status = 'ACTIVE';
```

**Index rationale:**
- `idx_subscription_plans_active` (partial) — list available plans quickly
- `idx_club_subscription_active` (unique partial) — enforces one active subscription per club at DB level
- `idx_club_subscription_club_id` — look up a club's subscription (enforcement interceptor)
- `idx_club_subscription_status` — scheduler queries for expiring/expired subscriptions
- `idx_club_subscription_expiry` (partial, ACTIVE only) — expiry notification scheduler query

---

## Subscription enforcement interceptor

```kotlin
@Component
class SubscriptionEnforcementInterceptor(
    private val subscriptionService: SubscriptionService,
    private val redisTemplate: RedisTemplate<String, String>
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val clubId = extractClubIdFromJwt(request) ?: return true  // platform scope — skip
        val status = getCachedStatus(clubId) ?: fetchAndCacheStatus(clubId)

        return when (status) {
            "ACTIVE", "GRACE" -> {
                if (status == "GRACE") response.setHeader("X-Subscription-Grace", "true")
                true
            }
            "EXPIRED", "CANCELLED" -> {
                response.status = 402
                response.contentType = "application/json"
                response.writer.write("""{"errorCode":"SUBSCRIPTION_EXPIRED","message":"Club subscription has expired. Please contact support to renew."}""")
                false
            }
            else -> true  // No subscription found — allow (new clubs)
        }
    }

    private fun getCachedStatus(clubId: Long): String? =
        redisTemplate.opsForValue().get("subscription_status:$clubId")

    private fun fetchAndCacheStatus(clubId: Long): String? {
        val subscription = subscriptionService.findActiveByClubId(clubId) ?: return null
        val status = subscription.status
        redisTemplate.opsForValue().set("subscription_status:$clubId", status, Duration.ofMinutes(5))
        return status
    }
}
```

**Cache invalidation**: whenever a subscription's status changes (assignment, extension, cancellation, scheduler transition), call `redisTemplate.delete("subscription_status:$clubId")`.

---

## Business rules — enforce in service layer

### Subscription management
1. **One active per club**: a club cannot have two non-CANCELLED/EXPIRED subscriptions simultaneously. If an ACTIVE or GRACE subscription already exists when assigning a new plan → `409 Conflict`, `errorCode: SUBSCRIPTION_ALREADY_ACTIVE`, message: "This club already has an active subscription. Cancel it before assigning a new one."
2. **Cannot cancel EXPIRED**: if trying to cancel an EXPIRED subscription → `409 Conflict`, `errorCode: SUBSCRIPTION_ALREADY_EXPIRED`.
3. **Plan must be active**: cannot assign an inactive (soft-deleted) plan → `422`, `errorCode: PLAN_NOT_AVAILABLE`.
4. **Audit on all changes**: every assignment, extension, or cancellation logs the appropriate audit action.

### Plan limit enforcement
5. **Branch limit**: in `BranchService.createBranch()` — count non-deleted branches for the club. If `count >= plan.maxBranches AND plan.maxBranches != 0` → `402`, `errorCode: PLAN_LIMIT_EXCEEDED`, message: "Your plan allows a maximum of {maxBranches} branches. Upgrade to add more."
6. **Staff limit**: in `StaffMemberService.createStaffMember()` — count non-deleted staff for the club. If `count >= plan.maxStaff AND plan.maxStaff != 0` → `402`, `errorCode: PLAN_LIMIT_EXCEEDED`, message: "Your plan allows a maximum of {maxStaff} staff members. Upgrade to add more."

### Scheduler transitions
7. **ACTIVE → GRACE**: when `currentPeriodEnd <= now()` and status = ACTIVE → set `status = GRACE`. Invalidate Redis cache.
8. **GRACE → EXPIRED**: when `gracePeriodEndsAt <= now()` and status = GRACE → set `status = EXPIRED`. Invalidate Redis cache. Fire `SUBSCRIPTION_EXPIRED` notification.
9. **Notification deduplication**: before sending `SUBSCRIPTION_EXPIRING_SOON_14` or `SUBSCRIPTION_EXPIRING_SOON_7`, check if a notification of that type for this clubId was already sent in the last 24 hours → skip if so.

---

## API endpoints

| Method | Path | Permission | Description |
|--------|------|------------|-------------|
| `POST` | `/api/v1/nexus/subscription-plans` | `subscription:manage` | Create a subscription plan |
| `GET` | `/api/v1/nexus/subscription-plans` | `subscription:read` | List all active subscription plans |
| `PATCH` | `/api/v1/nexus/subscription-plans/{planId}` | `subscription:manage` | Update plan name, price, or limits |
| `DELETE` | `/api/v1/nexus/subscription-plans/{planId}` | `subscription:manage` | Soft-delete a plan (only if no active subscriptions) |
| `POST` | `/api/v1/nexus/clubs/{clubId}/subscription` | `subscription:manage` | Assign a plan to a club (sets period start/end) |
| `GET` | `/api/v1/nexus/clubs/{clubId}/subscription` | `subscription:read` | Get current subscription for a club |
| `POST` | `/api/v1/nexus/clubs/{clubId}/subscription/extend` | `subscription:manage` | Extend `currentPeriodEnd` by N months; recalculate `gracePeriodEndsAt` |
| `POST` | `/api/v1/nexus/clubs/{clubId}/subscription/cancel` | `subscription:manage` | Cancel current subscription |
| `GET` | `/api/v1/nexus/subscriptions` | `subscription:read` | List all clubs with their subscription status + expiry (paginated) |
| `GET` | `/api/v1/nexus/subscriptions/expiring` | `subscription:read` | Clubs expiring within 30 days (for dashboard widget) |

---

## Request / Response shapes

### POST /nexus/subscription-plans — request
```json
{
  "name": "Growth",
  "monthlyPriceHalalas": 120000,
  "maxBranches": 3,
  "maxStaff": 30,
  "features": { "csvImport": true, "customReports": true }
}
```

### POST /nexus/clubs/{clubId}/subscription — request
```json
{
  "planPublicId": "uuid",
  "periodStartDate": "2026-04-08",
  "periodMonths": 1
}
```

The `currentPeriodEnd = periodStartDate + periodMonths * 30 days`. `gracePeriodEndsAt = currentPeriodEnd + 7 days`.

### POST /nexus/clubs/{clubId}/subscription/extend — request
```json
{
  "additionalMonths": 1
}
```

`currentPeriodEnd += additionalMonths * 30 days`. `gracePeriodEndsAt = new currentPeriodEnd + 7 days`. Status resets to ACTIVE if GRACE.

### GET /nexus/subscriptions — response
```json
{
  "subscriptions": [
    {
      "clubId": "uuid",
      "clubName": "Elixir Gym",
      "planName": "Growth",
      "status": "ACTIVE",
      "currentPeriodEnd": "2026-05-08",
      "gracePeriodEndsAt": "2026-05-15",
      "daysUntilExpiry": 30,
      "monthlyPriceSar": "1200.00"
    }
  ],
  "totalCount": 1,
  "page": 0,
  "pageSize": 20
}
```

### GET /nexus/subscriptions/expiring — response
```json
{
  "expiringSoon": [
    {
      "clubId": "uuid",
      "clubName": "Elixir Gym",
      "planName": "Growth",
      "status": "ACTIVE",
      "currentPeriodEnd": "2026-04-22",
      "daysUntilExpiry": 14
    }
  ]
}
```

---

## Repository queries

All must use `nativeQuery = true`:

```kotlin
// SubscriptionPlanRepository

@Query(value = """
    SELECT * FROM subscription_plans
    WHERE deleted_at IS NULL
      AND is_active = TRUE
    ORDER BY monthly_price_halalas
""", nativeQuery = true)
fun findAllActivePlans(): List<SubscriptionPlan>

// ClubSubscriptionRepository

// Used by enforcement interceptor (must be fast)
@Query(value = """
    SELECT * FROM club_subscriptions
    WHERE club_id = :clubId
      AND status NOT IN ('CANCELLED', 'EXPIRED')
    LIMIT 1
""", nativeQuery = true)
fun findActiveByClubId(clubId: Long): ClubSubscription?

// Scheduler: find ACTIVE subscriptions past their period end
@Query(value = """
    SELECT * FROM club_subscriptions
    WHERE status = 'ACTIVE'
      AND current_period_end <= :now
""", nativeQuery = true)
fun findActiveExpired(now: Instant): List<ClubSubscription>

// Scheduler: find GRACE subscriptions past their grace period
@Query(value = """
    SELECT * FROM club_subscriptions
    WHERE status = 'GRACE'
      AND grace_period_ends_at <= :now
""", nativeQuery = true)
fun findGraceExpired(now: Instant): List<ClubSubscription>

// Scheduler: find ACTIVE subscriptions expiring in exactly 14 or 7 days
@Query(value = """
    SELECT * FROM club_subscriptions
    WHERE status = 'ACTIVE'
      AND DATE(current_period_end AT TIME ZONE 'Asia/Riyadh') = :targetDate
""", nativeQuery = true)
fun findExpiringOnDate(targetDate: java.time.LocalDate): List<ClubSubscription>

// Dashboard: all clubs with subscription, paginated
@Query(value = """
    SELECT cs.*, c.name AS club_name, sp.name AS plan_name, sp.monthly_price_halalas
    FROM club_subscriptions cs
    JOIN clubs c  ON c.id = cs.club_id
    JOIN subscription_plans sp ON sp.id = cs.plan_id
    WHERE cs.status != 'CANCELLED'
    ORDER BY cs.current_period_end
    LIMIT :pageSize OFFSET :offset
""", nativeQuery = true)
fun findAllForDashboard(pageSize: Int, offset: Int): List<SubscriptionDashboardProjection>

@Query(value = """
    SELECT COUNT(*) FROM club_subscriptions
    WHERE status != 'CANCELLED'
""", nativeQuery = true)
fun countForDashboard(): Long

// Expiring within 30 days
@Query(value = """
    SELECT cs.*, c.name AS club_name, sp.name AS plan_name
    FROM club_subscriptions cs
    JOIN clubs c  ON c.id = cs.club_id
    JOIN subscription_plans sp ON sp.id = cs.plan_id
    WHERE cs.status = 'ACTIVE'
      AND cs.current_period_end <= :cutoff
    ORDER BY cs.current_period_end
""", nativeQuery = true)
fun findExpiringSoon(cutoff: Instant): List<SubscriptionDashboardProjection>
```

Interface projections:

```kotlin
interface SubscriptionDashboardProjection {
    val publicId: UUID          // club_subscriptions.public_id
    val clubId: Long
    val clubName: String
    val planName: String
    val monthlyPriceHalalas: Long
    val status: String
    val currentPeriodEnd: Instant
    val gracePeriodEndsAt: Instant
    val cancelledAt: Instant?
}
```

---

## Scheduler

```kotlin
@Component
class SubscriptionScheduler(
    private val subscriptionService: SubscriptionService
) {
    // Runs daily at 03:00 Riyadh time
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Riyadh")
    fun processSubscriptionLifecycle() {
        subscriptionService.transitionExpiredToGrace()
        subscriptionService.transitionGraceToExpired()
        subscriptionService.sendExpiryNotifications()
    }
}
```

`sendExpiryNotifications()` fires `SUBSCRIPTION_EXPIRING_SOON_14` for clubs whose `currentPeriodEnd` is exactly 14 days from today (Riyadh date), and `SUBSCRIPTION_EXPIRING_SOON_7` for 7 days from today.

---

## Frontend additions

### web-nexus — Subscriptions section

**New nav item**: "Subscriptions" in web-nexus sidebar (visible to Super Admin + Support Agent).

**Sub-pages:**

#### `/nexus/subscriptions` — Dashboard
```
┌──────────────────────────────────────────────────────────────┐
│  Subscriptions                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Active   │  │  Grace   │  │ Expiring │  │ Expired  │   │
│  │   12     │  │    1     │  │  3 clubs │  │    0     │   │
│  │  clubs   │  │  clubs   │  │ (30 days)│  │          │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
├──────────────────────────────────────────────────────────────┤
│  Club          Plan      Status   Expires       Price       │
│  Elixir Gym    Growth    ✅ Active  May 8        1,200 SAR   │
│  [Manage]                                                    │
└──────────────────────────────────────────────────────────────┘
```

- 4 KPI cards: Active count, Grace count, Expiring in 30 days count, Expired count
- Table: paginated, sortable by expiry date
- Status badges: green (Active), orange (Grace), red (Expired), grey (Cancelled)
- "Manage" button → club subscription detail page

#### `/nexus/subscriptions/plans` — Plan Catalog
- List of subscription plans (name, price, maxBranches, maxStaff, features JSON, active toggle)
- "Create Plan" button → modal (name, price SAR, maxBranches, maxStaff, features)
- Edit plan inline (name, price, limits — changes apply to future assignments only, not current subscriptions)
- Soft-delete (only if no active clubs on that plan)

#### `/nexus/clubs/{clubId}/subscription` — Club Subscription Detail
- Current plan, status badge, period start/end, grace period end
- "Assign Plan" button (if no active subscription) → modal: plan picker + period start + months
- "Extend" button (if ACTIVE or GRACE) → modal: additional months
- "Cancel" button → confirmation dialog
- History: list of all past subscriptions for the club

**New i18n strings** (`ar.json` + `en.json`):
```
subscription.page_title
subscription.nav_label
subscription.status.active
subscription.status.grace
subscription.status.expired
subscription.status.cancelled
subscription.kpi.active_clubs
subscription.kpi.grace_clubs
subscription.kpi.expiring_soon
subscription.kpi.expired_clubs
subscription.table.club
subscription.table.plan
subscription.table.status
subscription.table.expires
subscription.table.price
subscription.assign_title
subscription.assign_plan
subscription.assign_period_start
subscription.assign_months
subscription.extend_title
subscription.extend_months
subscription.cancel_confirm
subscription.plan.catalog_title
subscription.plan.create
subscription.plan.name
subscription.plan.price
subscription.plan.max_branches
subscription.plan.max_staff
subscription.plan.unlimited
subscription.error.already_active
subscription.error.plan_limit_exceeded
subscription.grace_banner       // "Your subscription has expired. {N} days remaining in grace period."
```

### web-pulse — Grace period banner

When the response header `X-Subscription-Grace: true` is present, show a sticky warning banner at the top of web-pulse:

```
⚠️ Your club subscription has expired. Access will be removed in {N} days. Contact support to renew.
```

- Banner persists across pages until the header is no longer returned
- `N` days computed from a `X-Grace-Days-Remaining` header (add this header alongside `X-Subscription-Grace`)
- No banner in web-arena (members are not responsible for billing)

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/kotlin/com/liyaqa/subscription/entity/SubscriptionPlan.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/entity/ClubSubscription.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/repository/SubscriptionPlanRepository.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/repository/ClubSubscriptionRepository.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/repository/SubscriptionDashboardProjection.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/service/SubscriptionService.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/interceptor/SubscriptionEnforcementInterceptor.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/scheduler/SubscriptionScheduler.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/dto/CreatePlanRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/dto/UpdatePlanRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/dto/AssignSubscriptionRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/dto/ExtendSubscriptionRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/dto/SubscriptionPlanResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/dto/ClubSubscriptionResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/dto/SubscriptionDashboardResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/controller/SubscriptionPlanController.kt`
- `backend/src/main/kotlin/com/liyaqa/subscription/controller/ClubSubscriptionController.kt`
- `backend/src/main/resources/db/migration/V23__subscription_billing.sql`
- `backend/src/test/kotlin/com/liyaqa/subscription/service/SubscriptionServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/subscription/interceptor/SubscriptionEnforcementInterceptorTest.kt`
- `backend/src/test/kotlin/com/liyaqa/subscription/controller/SubscriptionControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-nexus/src/routes/subscriptions/index.tsx`
- `apps/web-nexus/src/routes/subscriptions/plans.tsx`
- `apps/web-nexus/src/routes/subscriptions/$clubId.tsx`
- `apps/web-nexus/src/api/subscriptions.ts`
- `apps/web-nexus/src/tests/subscriptions.test.tsx`
- `apps/web-pulse/src/components/GracePeriodBanner.tsx`
- `apps/web-pulse/src/tests/grace-period-banner.test.tsx`

### Files to modify

- `backend/.../audit/model/AuditAction.kt` — add 5 new audit actions
- `backend/.../permission/PermissionConstants.kt` — add `SUBSCRIPTION_MANAGE`, `SUBSCRIPTION_READ`
- `backend/DevDataLoader.kt` — seed 3 plans + Growth subscription for Elixir Gym; seed permissions to Super Admin + Support Agent
- `backend/.../notification/model/NotificationType.kt` — add `SUBSCRIPTION_EXPIRING_SOON_14`, `SUBSCRIPTION_EXPIRING_SOON_7`, `SUBSCRIPTION_EXPIRED`
- `backend/.../WebMvcConfig.kt` (or `InterceptorConfig.kt`) — register `SubscriptionEnforcementInterceptor`
- `backend/.../branch/service/BranchService.kt` — add plan limit check in `createBranch()`
- `backend/.../staff/service/StaffMemberService.kt` — add plan limit check in `createStaffMember()`
- `apps/web-nexus/src/routes/` (sidebar) — add Subscriptions nav item
- `apps/web-nexus/src/locales/ar.json` + `en.json`
- `apps/web-pulse/src/components/AppShell.tsx` (or layout) — add `GracePeriodBanner` reading response headers
- `apps/web-pulse/src/locales/ar.json` + `en.json` — add grace banner string

---

## Implementation order

### Step 1 — Flyway V23 + entities + repositories
- Write `V23__subscription_billing.sql`
- Write `SubscriptionPlan.kt`, `ClubSubscription.kt`
- Write `SubscriptionPlanRepository.kt` with 1 native query
- Write `ClubSubscriptionRepository.kt` with 7 native queries
- Write `SubscriptionDashboardProjection.kt`
- Verify: `./gradlew flywayMigrate`

### Step 2 — Permissions + audit actions + notification types
- Add `SUBSCRIPTION_MANAGE`, `SUBSCRIPTION_READ` to `PermissionConstants.kt`
- Add 5 audit actions to `AuditAction.kt`
- Add 3 notification types to `NotificationType.kt`
- Seed in `DevDataLoader` (3 plans + Growth subscription for Elixir Gym + permissions)
- Verify: `./gradlew compileKotlin`

### Step 3 — SubscriptionService
Implement:
- `createPlan()`, `updatePlan()`, `deletePlan()` (enforce no active clubs on plan before delete)
- `assignSubscription()` — enforces BR1 (one active per club), sets period dates, logs `SUBSCRIPTION_ASSIGNED`, invalidates cache
- `extendSubscription()` — updates period end + grace period, resets GRACE → ACTIVE, logs `SUBSCRIPTION_EXTENDED`, invalidates cache
- `cancelSubscription()` — enforces BR2, sets `cancelledAt`, logs `SUBSCRIPTION_CANCELLED`, invalidates cache
- `findActiveByClubId()` — used by interceptor
- `transitionExpiredToGrace()` — queries `findActiveExpired()`, bulk updates to GRACE, invalidates caches
- `transitionGraceToExpired()` — queries `findGraceExpired()`, bulk updates to EXPIRED, fires `SUBSCRIPTION_EXPIRED` notification, invalidates caches
- `sendExpiryNotifications()` — queries 14-day and 7-day windows, deduplicates, fires notifications
- Verify: unit tests in `SubscriptionServiceTest`

### Step 4 — SubscriptionEnforcementInterceptor
- Implement interceptor with Redis cache
- Register in `WebMvcConfig`
- Add `X-Subscription-Grace` and `X-Grace-Days-Remaining` headers
- Unit test in `SubscriptionEnforcementInterceptorTest`

### Step 5 — Plan limit enforcement
- Add branch count check to `BranchService.createBranch()`
- Add staff count check to `StaffMemberService.createStaffMember()`
- Both check active subscription's plan limits; skip check if no subscription or unlimited (maxBranches/maxStaff = 0)
- Verify: `./gradlew compileKotlin`

### Step 6 — Controllers
- `SubscriptionPlanController` — 4 endpoints for plan CRUD
- `ClubSubscriptionController` — 6 endpoints for club subscription management
- All with `@Operation` + `@PreAuthorize`
- Verify: `./gradlew compileKotlin`

### Step 7 — Frontend: web-nexus
- `/subscriptions` dashboard with 4 KPI cards + paginated table
- `/subscriptions/plans` plan catalog
- `/subscriptions/$clubId` club detail with assign/extend/cancel
- `subscriptions.ts` API module
- Add Subscriptions nav item
- Add i18n strings
- Verify: `npm run typecheck`

### Step 8 — Frontend: web-pulse grace banner
- `GracePeriodBanner` reads `X-Subscription-Grace` + `X-Grace-Days-Remaining` from API response interceptor (Axios interceptor or TanStack Query middleware)
- Sticky yellow banner at top of AppShell
- Add i18n string
- Verify: `npm run typecheck`

### Step 9 — Tests

**Unit: `SubscriptionServiceTest`**
- `assignSubscription creates ACTIVE subscription with correct period dates`
- `assignSubscription throws 409 when club already has active subscription`
- `assignSubscription throws 422 when plan is not active`
- `extendSubscription extends period end and grace period`
- `extendSubscription resets GRACE status to ACTIVE`
- `cancelSubscription sets cancelled_at`
- `cancelSubscription throws 409 when subscription already expired`
- `transitionExpiredToGrace moves ACTIVE past period_end to GRACE`
- `transitionGraceToExpired moves GRACE past grace_period_ends_at to EXPIRED`
- `sendExpiryNotifications fires 14-day notification`
- `sendExpiryNotifications fires 7-day notification`
- `sendExpiryNotifications deduplicates within 24 hours`

**Unit: `SubscriptionEnforcementInterceptorTest`**
- `allows request when subscription is ACTIVE`
- `allows request with grace header when subscription is GRACE`
- `blocks request with 402 when subscription is EXPIRED`
- `blocks request with 402 when subscription is CANCELLED`
- `skips check for platform-scope JWT (no clubId claim)`
- `uses Redis cache and avoids DB call on cache hit`

**Integration: `SubscriptionControllerIntegrationTest`**
- `POST /nexus/subscription-plans creates plan`
- `POST /nexus/subscription-plans returns 403 without subscription:manage`
- `GET /nexus/subscription-plans returns active plans`
- `POST /nexus/clubs/{id}/subscription assigns plan to club`
- `POST /nexus/clubs/{id}/subscription returns 409 when already active`
- `POST /nexus/clubs/{id}/subscription/extend extends period`
- `POST /nexus/clubs/{id}/subscription/cancel cancels subscription`
- `GET /nexus/subscriptions returns paginated dashboard`
- `GET /nexus/subscriptions/expiring returns clubs expiring in 30 days`
- `POST /nexus/branches (branch create) returns 402 when plan limit exceeded`
- `POST /nexus/staff (staff create) returns 402 when plan limit exceeded`

**Frontend: `subscriptions.test.tsx` (nexus)**
- renders KPI cards with correct counts
- renders subscription table with status badges
- assign plan modal submits correctly

**Frontend: `grace-period-banner.test.tsx` (pulse)**
- renders banner when X-Subscription-Grace header is present
- shows correct days remaining
- does not render when header is absent

---

## RBAC matrix rows added by this plan

| Permission | Super Admin | Support Agent | Others |
|---|---|---|---|
| `subscription:manage` | ✅ (seeded) | — | — |
| `subscription:read` | ✅ (seeded) | ✅ (seeded) | — |

---

## Definition of Done

- [ ] Flyway V23 runs cleanly: `subscription_plans` and `club_subscriptions` with all indexes
- [ ] Unique partial index enforces one active subscription per club at DB level
- [ ] `SubscriptionPlan` has `deletedAt`; `ClubSubscription` does not
- [ ] `subscription:manage` and `subscription:read` seeded correctly
- [ ] 5 audit actions and 3 notification types wired in
- [ ] 3 seed plans (Starter/Growth/Enterprise) and Elixir Gym on Growth in `DevDataLoader`
- [ ] `SubscriptionEnforcementInterceptor` registered and fires on all club-scoped requests
- [ ] ACTIVE → GRACE → EXPIRED scheduler transitions correct
- [ ] Redis cache key `subscription_status:{clubId}` with 5-minute TTL
- [ ] Cache invalidated on every status change
- [ ] `X-Subscription-Grace` + `X-Grace-Days-Remaining` headers set for GRACE status
- [ ] 402 SUBSCRIPTION_EXPIRED returned for EXPIRED and CANCELLED
- [ ] Platform-scope JWTs (no clubId) bypass enforcement
- [ ] Branch creation blocked with 402 PLAN_LIMIT_EXCEEDED when at maxBranches
- [ ] Staff creation blocked with 402 PLAN_LIMIT_EXCEEDED when at maxStaff
- [ ] 0 = unlimited: no limit check when maxBranches or maxStaff = 0
- [ ] Two expiry notifications: 14-day and 7-day, deduplicated within 24 hours
- [ ] All 8 repository queries use `nativeQuery = true`
- [ ] 10 endpoints live: 4 plan CRUD + 6 club subscription management, all with `@Operation`
- [ ] web-nexus: Subscriptions dashboard with 4 KPI cards + table
- [ ] web-nexus: Plan catalog with create/edit/delete
- [ ] web-nexus: Club subscription detail with assign/extend/cancel
- [ ] web-pulse: Grace period banner reads response headers
- [ ] All i18n strings added in Arabic and English (web-nexus + web-pulse)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors in web-nexus or web-pulse
- [ ] `PROJECT-STATE.md` updated: Plan 30 complete, test counts, V23 noted
- [ ] `PLAN-30-subscription-billing.md` deleted before merging

When all items are checked, confirm: **"Plan 30 — Liyaqa Subscription Billing complete. X backend tests, Y frontend tests."**
