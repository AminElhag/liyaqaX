# Plan 31 — ZATCA Health Dashboard & CSID Expiry Alerts

## Overview

Plan 23 built the full ZATCA Phase 2 integration. This plan adds the operational safety net: proactive alerts when CSIDs are about to expire, alerts when invoices are approaching the 24-hour ZATCA reporting deadline without being reported, and a health dashboard in web-nexus that gives platform admins full visibility into every club's ZATCA status. It also adds a manual retry mechanism for permanently failed invoices.

**No new Flyway migration.** Everything in this plan reads from and writes to tables and columns that already exist: `club_zatca_certificates`, `invoices`, `notifications`, `audit_logs`.

## What Gets Built

### Backend

1. **Two new scheduler jobs** added to the existing `ZatcaReportingScheduler`
2. **`ZatcaHealthService`** — aggregates health statistics across all clubs
3. **`ZatcaRetryService`** — resets failed invoices for manual retry
4. **Two new endpoints** on `ZatcaNexusController` — health summary + failed invoice list + retry action
5. **One new notification type**: `ZATCA_CSID_EXPIRING_SOON`
6. **Two new audit actions**: `ZATCA_CSID_RENEWED` (already planned, wire it here), `ZATCA_INVOICE_RETRY_REQUESTED`
7. **One new permission**: `zatca:retry`

### Frontend (web-nexus only)

8. **ZATCA screen redesigned** — adds health summary cards at the top, color-coded status on the club table, new "Failed Invoices" tab

---

## Implementation Steps

### Step 1 — New Notification Type

In `NotificationType.kt` (or wherever the enum/constants live), add:

```kotlin
ZATCA_CSID_EXPIRING_SOON,   // CSID expires within 30 days
ZATCA_INVOICE_DEADLINE_AT_RISK, // invoice unreported and within 1 hour of 24h deadline
```

In `NotificationTriggerService` (or `ZatcaHealthService` — see Step 2), these notifications target the **platform admin user(s)** — not club staff, not members. The notification `userId` should be set to all platform users with the `zatca:read` permission.

No new `@EventListener` needed — these are **proactive scheduler-driven** notifications, same pattern as `MEMBERSHIP_EXPIRING_SOON`.

---

### Step 2 — ZatcaHealthService

**`ZatcaHealthService.kt`** in `com.liyaqa.zatca.service`:

```kotlin
package com.liyaqa.zatca.service

import com.liyaqa.zatca.dto.ZatcaHealthSummary
import com.liyaqa.zatca.dto.ZatcaFailedInvoiceResponse
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import com.liyaqa.invoice.repository.InvoiceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class ZatcaHealthService(
    private val certRepository: ClubZatcaCertificateRepository,
    private val invoiceRepository: InvoiceRepository
) {

    /**
     * Returns a platform-wide health summary across all clubs.
     */
    fun getHealthSummary(): ZatcaHealthSummary {
        val now = Instant.now()
        val thirtyDaysFromNow = now.plus(30, ChronoUnit.DAYS)

        val totalActive = certRepository.countByOnboardingStatusAndDeletedAtIsNull("active")
        val expiringSoon = certRepository.countExpiringSoon(thirtyDaysFromNow)
        val notOnboarded = certRepository.countByOnboardingStatusNotAndDeletedAtIsNull("active")
        val pendingInvoices = invoiceRepository.countPendingZatcaReporting()
        val failedInvoices = invoiceRepository.countFailedZatcaReporting()
        val deadlineAtRisk = invoiceRepository.countInvoicesApproachingDeadline(
            now.minus(23, ChronoUnit.HOURS)
        )

        return ZatcaHealthSummary(
            totalActiveCsids = totalActive,
            csidsExpiringSoon = expiringSoon,
            clubsNotOnboarded = notOnboarded,
            invoicesPending = pendingInvoices,
            invoicesFailed = failedInvoices,
            invoicesDeadlineAtRisk = deadlineAtRisk
        )
    }

    /**
     * Returns list of permanently failed invoices with enough detail for
     * platform admin to diagnose and retry.
     */
    fun getFailedInvoices(): List<ZatcaFailedInvoiceResponse> {
        return invoiceRepository.findFailedZatcaInvoicesWithClub()
            .map { row ->
                ZatcaFailedInvoiceResponse(
                    invoicePublicId = row.invoicePublicId,
                    invoiceNumber = row.invoiceNumber,
                    clubName = row.clubName,
                    memberName = row.memberName,
                    amountSar = "%.2f".format(row.amountHalalas / 100.0),
                    createdAt = row.createdAt.toString(),
                    zatcaRetryCount = row.zatcaRetryCount,
                    zatcaLastError = row.zatcaLastError,
                    zatcaStatus = row.zatcaStatus
                )
            }
    }
}
```

**DTOs:**

```kotlin
// ZatcaHealthSummary.kt
data class ZatcaHealthSummary(
    val totalActiveCsids: Long,
    val csidsExpiringSoon: Long,
    val clubsNotOnboarded: Long,
    val invoicesPending: Long,
    val invoicesFailed: Long,
    val invoicesDeadlineAtRisk: Long
)

// ZatcaFailedInvoiceResponse.kt
data class ZatcaFailedInvoiceResponse(
    val invoicePublicId: java.util.UUID,
    val invoiceNumber: String?,
    val clubName: String,
    val memberName: String,
    val amountSar: String,
    val createdAt: String,
    val zatcaRetryCount: Int,
    val zatcaLastError: String?,
    val zatcaStatus: String
)
```

---

### Step 3 — ZatcaRetryService

**`ZatcaRetryService.kt`** in `com.liyaqa.zatca.service`:

```kotlin
package com.liyaqa.zatca.service

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.repository.InvoiceRepository
import com.liyaqa.audit.service.AuditService
import com.liyaqa.audit.model.AuditAction
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ZatcaRetryService(
    private val invoiceRepository: InvoiceRepository,
    private val auditService: AuditService
) {

    /**
     * Resets a permanently failed invoice so the scheduler will pick it
     * up again on the next run.
     *
     * Business rules:
     * - Invoice must currently have zatcaStatus = 'failed'
     * - Resets zatcaRetryCount to 0
     * - Resets zatcaStatus to 'generated'
     * - Clears zatcaLastError
     * - Does NOT re-submit immediately — the scheduler handles submission
     */
    @Transactional
    fun retryInvoice(invoicePublicId: UUID) {
        val invoice = invoiceRepository.findByPublicIdAndDeletedAtIsNull(invoicePublicId)
            ?: throw ArenaException("Invoice not found", HttpStatus.NOT_FOUND)

        if (invoice.zatcaStatus != "failed") {
            throw ArenaException(
                "Invoice is not in failed state (current: ${invoice.zatcaStatus})",
                HttpStatus.CONFLICT
            )
        }

        invoice.zatcaRetryCount = 0
        invoice.zatcaStatus = "generated"
        invoice.zatcaLastError = null
        invoiceRepository.save(invoice)

        auditService.log(
            action = AuditAction.ZATCA_INVOICE_RETRY_REQUESTED,
            entityType = "Invoice",
            entityId = invoice.publicId.toString(),
            changes = mapOf("invoicePublicId" to invoicePublicId.toString())
        )
    }

    /**
     * Bulk retry — resets all permanently failed invoices for a specific club.
     * Used when a club had a temporary CSID issue and all their invoices failed.
     */
    @Transactional
    fun retryAllFailedForClub(clubPublicId: UUID) {
        val invoiceIds = invoiceRepository.findFailedZatcaReportingByClub(clubPublicId)
        invoiceIds.forEach { invoiceId ->
            val invoice = invoiceRepository.findById(invoiceId).orElse(null) ?: return@forEach
            invoice.zatcaRetryCount = 0
            invoice.zatcaStatus = "generated"
            invoice.zatcaLastError = null
            invoiceRepository.save(invoice)
        }
        auditService.log(
            action = AuditAction.ZATCA_INVOICE_RETRY_REQUESTED,
            entityType = "Club",
            entityId = clubPublicId.toString(),
            changes = mapOf("count" to invoiceIds.size.toString(), "clubPublicId" to clubPublicId.toString())
        )
    }
}
```

---

### Step 4 — New Repository Queries

Add to **`ClubZatcaCertificateRepository`**:

```kotlin
@Query(
    value = "SELECT COUNT(*) FROM club_zatca_certificates WHERE onboarding_status = :status AND deleted_at IS NULL",
    nativeQuery = true
)
fun countByOnboardingStatusAndDeletedAtIsNull(status: String): Long

@Query(
    value = "SELECT COUNT(*) FROM club_zatca_certificates WHERE onboarding_status != 'active' AND deleted_at IS NULL",
    nativeQuery = true
)
fun countByOnboardingStatusNotAndDeletedAtIsNull(status: String): Long

@Query(
    value = """
        SELECT COUNT(*) FROM club_zatca_certificates
        WHERE onboarding_status = 'active'
          AND csid_expires_at < :threshold
          AND deleted_at IS NULL
    """,
    nativeQuery = true
)
fun countExpiringSoon(threshold: java.time.Instant): Long
```

Add to **`InvoiceRepository`**:

```kotlin
@Query(
    value = """
        SELECT COUNT(*) FROM invoices
        WHERE zatca_status IN ('generated', 'signed')
          AND zatca_retry_count < 5
          AND deleted_at IS NULL
    """,
    nativeQuery = true
)
fun countPendingZatcaReporting(): Long

@Query(
    value = """
        SELECT COUNT(*) FROM invoices
        WHERE zatca_status = 'failed'
          AND deleted_at IS NULL
    """,
    nativeQuery = true
)
fun countFailedZatcaReporting(): Long

/**
 * Invoices that are still unreported and were created more than 23 hours ago.
 * These are approaching ZATCA's 24-hour reporting deadline.
 */
@Query(
    value = """
        SELECT COUNT(*) FROM invoices
        WHERE zatca_status IN ('generated', 'signed')
          AND created_at < :threshold
          AND deleted_at IS NULL
    """,
    nativeQuery = true
)
fun countInvoicesApproachingDeadline(threshold: java.time.Instant): Long

/**
 * Returns failed invoice details joined to club and member info.
 * Uses an interface projection to avoid loading full entities.
 */
@Query(
    value = """
        SELECT
            i.public_id        AS invoicePublicId,
            i.invoice_number   AS invoiceNumber,
            c.name_en          AS clubName,
            m.name_en          AS memberName,
            i.total_halalas    AS amountHalalas,
            i.created_at       AS createdAt,
            i.zatca_retry_count AS zatcaRetryCount,
            i.zatca_last_error  AS zatcaLastError,
            i.zatca_status      AS zatcaStatus
        FROM invoices i
        JOIN memberships ms ON ms.id = i.membership_id
        JOIN membership_plans mp ON mp.id = ms.membership_plan_id
        JOIN clubs c ON c.id = mp.club_id
        JOIN members m ON m.id = ms.member_id
        WHERE i.zatca_status = 'failed'
          AND i.deleted_at IS NULL
        ORDER BY i.created_at DESC
        LIMIT 500
    """,
    nativeQuery = true
)
fun findFailedZatcaInvoicesWithClub(): List<FailedZatcaInvoiceProjection>

@Query(
    value = """
        SELECT i.id FROM invoices i
        JOIN memberships ms ON ms.id = i.membership_id
        JOIN membership_plans mp ON mp.id = ms.membership_plan_id
        JOIN clubs c ON c.id = mp.club_id
        WHERE i.zatca_status = 'failed'
          AND c.public_id = :clubPublicId
          AND i.deleted_at IS NULL
    """,
    nativeQuery = true
)
fun findFailedZatcaReportingByClub(clubPublicId: java.util.UUID): List<Long>
```

**Interface projection** (in `com.liyaqa.invoice.repository`):

```kotlin
interface FailedZatcaInvoiceProjection {
    val invoicePublicId: java.util.UUID
    val invoiceNumber: String?
    val clubName: String
    val memberName: String
    val amountHalalas: Long
    val createdAt: java.time.Instant
    val zatcaRetryCount: Int
    val zatcaLastError: String?
    val zatcaStatus: String
}
```

---

### Step 5 — Extend ZatcaReportingScheduler

Add two new scheduled methods to the existing `ZatcaReportingScheduler`:

```kotlin
/**
 * Daily at 07:00 Riyadh (04:00 UTC): check for CSIDs expiring within 30 days.
 * Creates a ZATCA_CSID_EXPIRING_SOON notification for each platform admin
 * who has zatca:read permission.
 */
@Scheduled(cron = "0 0 4 * * *")  // 04:00 UTC = 07:00 Riyadh
fun alertExpiringCsids() {
    val threshold = Instant.now().plus(30, ChronoUnit.DAYS)
    val expiring = certRepository.findExpiringSoon(threshold)
    if (expiring.isEmpty()) return

    log.warn("ZATCA: {} CSIDs expiring within 30 days", expiring.size)

    expiring.forEach { cert ->
        val daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), cert.csidExpiresAt)
        notificationService.createForPlatformAdmins(
            type = "ZATCA_CSID_EXPIRING_SOON",
            titleAr = "شهادة زاتكا تنتهي قريباً",
            titleEn = "ZATCA Certificate Expiring Soon",
            bodyAr = "شهادة نادي ${cert.club.nameAr} ستنتهي خلال $daysUntilExpiry يوم. يرجى التجديد.",
            bodyEn = "CSID for ${cert.club.nameEn} expires in $daysUntilExpiry days. Please renew.",
            entityId = cert.club.publicId.toString(),
            entityType = "Club"
        )
    }
}

/**
 * Every hour: check for invoices that are unreported and older than 23 hours.
 * ZATCA requires reporting within 24 hours — these are at risk.
 */
@Scheduled(fixedDelay = 60 * 60 * 1000)  // every hour
fun alertInvoicesApproachingDeadline() {
    val threshold = Instant.now().minus(23, ChronoUnit.HOURS)
    val count = invoiceRepository.countInvoicesApproachingDeadline(threshold)
    if (count == 0L) return

    log.error(
        "ZATCA DEADLINE RISK: {} invoices unreported and older than 23 hours. " +
        "ZATCA requires reporting within 24 hours.",
        count
    )

    notificationService.createForPlatformAdmins(
        type = "ZATCA_INVOICE_DEADLINE_AT_RISK",
        titleAr = "تحذير: فواتير لم يتم إرسالها لزاتكا",
        titleEn = "ZATCA Reporting Deadline At Risk",
        bodyAr = "$count فاتورة لم يتم إرسالها وقاربت على انتهاء المهلة (24 ساعة).",
        bodyEn = "$count invoice(s) unreported and approaching the 24-hour ZATCA deadline.",
        entityId = null,
        entityType = "Invoice"
    )
}
```

Add a helper to `NotificationService` (or create `ZatcaNotificationHelper`) to create notifications for all platform users with `zatca:read` permission:

```kotlin
fun createForPlatformAdmins(
    type: String,
    titleAr: String,
    titleEn: String,
    bodyAr: String,
    bodyEn: String,
    entityId: String?,
    entityType: String
) {
    // Find all platform users who have zatca:read permission
    val adminUserIds = userRepository.findPlatformUsersWithPermission("zatca:read")
    adminUserIds.forEach { userId ->
        createNotification(
            userId = userId,
            type = type,
            titleAr = titleAr,
            titleEn = titleEn,
            bodyAr = bodyAr,
            bodyEn = bodyEn,
            entityId = entityId,
            entityType = entityType
        )
    }
}
```

---

### Step 6 — New Audit Actions

Add to `AuditAction.kt` (or wherever the enum/constants live):

```kotlin
ZATCA_CSID_RENEWED,
ZATCA_INVOICE_RETRY_REQUESTED,
```

Wire `ZATCA_CSID_RENEWED` into `ZatcaOnboardingService.renewClubCsid()` — it was planned in Plan 23 but was listed as a future action. Add the audit call at the end of the renewal method:

```kotlin
auditService.log(
    action = AuditAction.ZATCA_CSID_RENEWED,
    entityType = "ClubZatcaCertificate",
    entityId = cert.publicId.toString(),
    changes = mapOf("clubId" to club.publicId.toString())
)
```

---

### Step 7 — New Permission

Add to permission constants and seed data:

```kotlin
// Permission code
const val ZATCA_RETRY = "zatca:retry"
```

Seed to: **NexusAdmin** role only (platform admin can reset failed invoices; club owners cannot).

---

### Step 8 — New Controller Endpoints

Add to **`ZatcaNexusController`**:

```kotlin
/**
 * GET /api/v1/zatca/health
 * Platform-wide ZATCA health summary: KPI cards for the dashboard header.
 */
@GetMapping("/health")
@Operation(summary = "Get ZATCA platform health summary")
@PreAuthorize("hasPermission(null, 'zatca:read')")
fun getHealthSummary(): ResponseEntity<ZatcaHealthSummary> =
    ResponseEntity.ok(healthService.getHealthSummary())

/**
 * GET /api/v1/zatca/invoices/failed
 * List of permanently failed invoices with error detail.
 */
@GetMapping("/invoices/failed")
@Operation(summary = "List permanently failed ZATCA invoices")
@PreAuthorize("hasPermission(null, 'zatca:read')")
fun getFailedInvoices(): ResponseEntity<List<ZatcaFailedInvoiceResponse>> =
    ResponseEntity.ok(healthService.getFailedInvoices())

/**
 * POST /api/v1/zatca/invoices/{invoicePublicId}/retry
 * Reset a single failed invoice for the scheduler to retry.
 */
@PostMapping("/invoices/{invoicePublicId}/retry")
@Operation(summary = "Retry a permanently failed ZATCA invoice")
@PreAuthorize("hasPermission(null, 'zatca:retry')")
fun retryInvoice(
    @PathVariable invoicePublicId: UUID
): ResponseEntity<Map<String, String>> {
    retryService.retryInvoice(invoicePublicId)
    return ResponseEntity.ok(mapOf("message" to "Invoice queued for retry"))
}

/**
 * POST /api/v1/zatca/clubs/{clubPublicId}/retry-all
 * Reset all failed invoices for a specific club.
 */
@PostMapping("/clubs/{clubPublicId}/retry-all")
@Operation(summary = "Retry all failed ZATCA invoices for a club")
@PreAuthorize("hasPermission(null, 'zatca:retry')")
fun retryAllFailedForClub(
    @PathVariable clubPublicId: UUID
): ResponseEntity<Map<String, String>> {
    retryService.retryAllFailedForClub(clubPublicId)
    return ResponseEntity.ok(mapOf("message" to "All failed invoices queued for retry"))
}
```

---

### Step 9 — Frontend: web-nexus ZATCA Screen Redesign

The existing ZATCA management screen (from Plan 23) shows a plain club table. This step adds health cards at the top and a second tab for failed invoices.

**File:** `apps/web-nexus/src/routes/zatca/index.tsx`

The redesigned screen has two tabs:
- **Tab 1: Clubs** — the existing onboarding table (unchanged), with color-coded row backgrounds based on status
- **Tab 2: Failed Invoices** — new table of permanently failed invoices with retry actions

**Health summary cards** (above the tabs, always visible):

| Card | Value | Color |
|------|-------|-------|
| Active CSIDs | `totalActiveCsids` | green |
| Expiring Soon (30 days) | `csidsExpiringSoon` | amber if > 0, grey if 0 |
| Not Onboarded | `clubsNotOnboarded` | red if > 0, grey if 0 |
| Invoices Pending | `invoicesPending` | blue |
| Invoices Failed | `invoicesFailed` | red if > 0, grey if 0 |
| Deadline At Risk | `invoicesDeadlineAtRisk` | red if > 0, grey if 0 |

**Failed Invoices tab** columns:
- Invoice # (or UUID if no number)
- Club Name
- Member Name
- Amount (SAR)
- Created At (relative time — "2 hours ago")
- Retry Count
- Last Error (truncated to 80 chars, tooltip for full text)
- Actions: "Retry" button (calls `POST /invoices/{id}/retry`, permission-gated to `zatca:retry`)

Each club row in the Clubs tab gets a "Retry All" button in the actions column if that club has failed invoices (`invoicesFailed > 0` from per-club status).

**New API calls (TanStack Query):**

```ts
// GET /api/v1/zatca/health
// GET /api/v1/zatca/invoices/failed
// POST /api/v1/zatca/invoices/{invoicePublicId}/retry
// POST /api/v1/zatca/clubs/{clubPublicId}/retry-all
```

Auto-refresh: health summary cards refresh every 60 seconds (same pattern as Plan 22 pending badge).

**New i18n strings** (add to `ar.json` and `en.json`):

```
zatca.health.title
zatca.health.active_csids
zatca.health.expiring_soon
zatca.health.not_onboarded
zatca.health.invoices_pending
zatca.health.invoices_failed
zatca.health.deadline_at_risk
zatca.tabs.clubs
zatca.tabs.failed_invoices
zatca.failed_invoices.invoice_number
zatca.failed_invoices.club
zatca.failed_invoices.member
zatca.failed_invoices.amount
zatca.failed_invoices.created_at
zatca.failed_invoices.retry_count
zatca.failed_invoices.last_error
zatca.failed_invoices.retry
zatca.failed_invoices.retry_all
zatca.failed_invoices.retry_success
zatca.failed_invoices.empty
```

---

### Step 10 — Tests

#### Unit Tests

**`ZatcaHealthServiceTest`**:
- `getHealthSummary returns correct counts from repository mocks`
- `getFailedInvoices maps projection to DTO correctly`
- `expiring soon count is 0 when no CSIDs near threshold`

**`ZatcaRetryServiceTest`**:
- `retryInvoice resets zatcaRetryCount to 0 and status to generated`
- `retryInvoice throws NOT_FOUND when invoice does not exist`
- `retryInvoice throws CONFLICT when invoice status is not failed`
- `retryInvoice clears zatcaLastError`
- `retryAllFailedForClub resets all failed invoices for that club`
- `retryAllFailedForClub does nothing when no failed invoices`

**`ZatcaSchedulerAlertTest`**:
- `alertExpiringCsids creates notification for each expiring certificate`
- `alertExpiringCsids skips when no expiring CSIDs`
- `alertInvoicesApproachingDeadline creates notification when count > 0`
- `alertInvoicesApproachingDeadline skips when count is 0`

#### Integration Tests (Testcontainers)

**`ZatcaHealthControllerIntegrationTest`**:
- `GET /api/v1/zatca/health returns 200 with correct shape`
- `GET /api/v1/zatca/invoices/failed returns 200 with list`
- `GET /api/v1/zatca/health returns 403 without zatca:read permission`
- `POST /api/v1/zatca/invoices/{id}/retry returns 200 and resets invoice`
- `POST /api/v1/zatca/invoices/{id}/retry returns 403 without zatca:retry permission`
- `POST /api/v1/zatca/invoices/{id}/retry returns 409 when invoice is not in failed state`
- `POST /api/v1/zatca/clubs/{id}/retry-all resets all failed invoices for the club`

#### Frontend Tests

**`zatca-health.test.tsx`**:
- renders health summary cards with correct values
- "Deadline At Risk" card is red when count > 0
- "Failed Invoices" tab renders failed invoice table
- "Retry" button calls retry endpoint and invalidates query
- empty state shown when no failed invoices

---

## New Endpoints Summary

| Method | Path | Permission | Description |
|--------|------|------------|-------------|
| GET | `/api/v1/zatca/health` | `zatca:read` | Platform health summary KPIs |
| GET | `/api/v1/zatca/invoices/failed` | `zatca:read` | List permanently failed invoices |
| POST | `/api/v1/zatca/invoices/{id}/retry` | `zatca:retry` | Reset single failed invoice |
| POST | `/api/v1/zatca/clubs/{id}/retry-all` | `zatca:retry` | Reset all failed invoices for a club |

---

## Business Rules

1. Only invoices with `zatcaStatus = 'failed'` can be retried — any other status returns `409 Conflict`
2. Retrying resets `zatcaRetryCount = 0` and `zatcaStatus = 'generated'` — the scheduler picks it up within 5 minutes
3. `zatcaLastError` is cleared on retry so the error field reflects the outcome of the next attempt
4. The retry action does NOT immediately re-submit — it queues for the existing `@Scheduled` job
5. `ZATCA_CSID_EXPIRING_SOON` notifications are sent to all platform users with `zatca:read` — not to club staff
6. `ZATCA_INVOICE_DEADLINE_AT_RISK` fires hourly — deduplication check ensures only one notification per hour per state (same pattern as existing notification deduplication: check last 1 hour for same type)
7. `alertExpiringCsids` uses the same `findExpiringSoon` query already in `ClubZatcaCertificateRepository` — no new queries needed for that method
8. The "Retry All" action for a club requires the same `zatca:retry` permission as single retry

---

## What Is NOT in Scope

- Automatic CSID renewal — renewal still requires a human to generate an OTP from the FATOORA Portal (same flow as Plan 23). This plan only adds the alert, not automation.
- Email notifications for CSID expiry — the notification system (Plan 21) already handles bell + drawer. Email for `ZATCA_CSID_EXPIRING_SOON` is not added here; that can be wired later.
- Reporting for club owners — the ZATCA health data is platform-admin-only. The `ZatcaPulseController` status endpoint from Plan 23 remains the only club-facing ZATCA view.
- Clearing `zatca_report_response` on retry — it is preserved so admins can see what ZATCA last returned even after a retry is requested.

---

## Definition of Done

- [ ] `ZATCA_CSID_EXPIRING_SOON` and `ZATCA_INVOICE_DEADLINE_AT_RISK` added to notification types
- [ ] `ZatcaHealthService` compiles, all methods return correct data
- [ ] `ZatcaRetryService` resets invoices correctly with audit logging
- [ ] 6 new native queries added to `ClubZatcaCertificateRepository` and `InvoiceRepository` — all use `nativeQuery = true`
- [ ] `FailedZatcaInvoiceProjection` interface defined and used by repository
- [ ] Two new scheduler jobs in `ZatcaReportingScheduler` (expiry alert at 04:00 UTC, deadline check every hour)
- [ ] `ZATCA_CSID_RENEWED` audit action wired into `ZatcaOnboardingService.renewClubCsid()`
- [ ] `ZATCA_INVOICE_RETRY_REQUESTED` audit action wired into both retry methods
- [ ] `zatca:retry` permission seeded for NexusAdmin role
- [ ] 4 new endpoints on `ZatcaNexusController` — all with `@Operation` and `@PreAuthorize`
- [ ] web-nexus ZATCA screen: 6 health cards visible above tabs
- [ ] web-nexus ZATCA screen: "Failed Invoices" tab with retry buttons
- [ ] Health cards auto-refresh every 60 seconds
- [ ] All i18n strings added in Arabic and English
- [ ] All unit and integration tests pass
- [ ] Backend builds with `./gradlew build` — no warnings
- [ ] `PROJECT-STATE.md` updated: Plan 31 complete, test counts updated
- [ ] `PLAN-zatca-health.md` deleted before merging

When all items are checked, confirm: **"Plan 31 — ZATCA Health Dashboard complete. X backend tests, Y frontend tests."**

