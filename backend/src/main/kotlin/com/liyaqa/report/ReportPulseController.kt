package com.liyaqa.report

import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.report.dto.CashDrawerReportResponse
import com.liyaqa.report.dto.LeadReportResponse
import com.liyaqa.report.dto.ReportQueryParams
import com.liyaqa.report.dto.RetentionReportResponse
import com.liyaqa.report.dto.RevenueReportResponse
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports (Pulse)", description = "Report and analytics endpoints — club operations")
@Validated
class ReportPulseController(
    private val revenueReportService: RevenueReportService,
    private val retentionReportService: RetentionReportService,
    private val leadReportService: LeadReportService,
    private val cashDrawerReportService: CashDrawerReportService,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
) {
    // ── Revenue ────────────────────────────────────────────────────────────

    @GetMapping("/revenue")
    @PreAuthorize("hasPermission(null, 'report:revenue:view')")
    @Operation(summary = "Get revenue report data")
    fun getRevenueReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) groupBy: String?,
        authentication: Authentication,
    ): ResponseEntity<RevenueReportResponse> {
        val params = resolveParams(from, to, branchId, groupBy, authentication)
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(revenueReportService.generate(clubId, params))
    }

    @GetMapping("/revenue/export")
    @PreAuthorize("hasPermission(null, 'report:revenue:view')")
    @Operation(summary = "Export revenue report as CSV")
    fun exportRevenueReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) groupBy: String?,
        authentication: Authentication,
    ): ResponseEntity<ByteArray> {
        val params = resolveParams(from, to, branchId, groupBy, authentication)
        val clubId = resolveClubId(authentication)
        val report = revenueReportService.generate(clubId, params)

        val csv = buildRevenueCsv(report)
        return csvResponse(csv, "report-revenue-${params.from}-${params.to}.csv")
    }

    // ── Retention ──────────────────────────────────────────────────────────

    @GetMapping("/retention")
    @PreAuthorize("hasPermission(null, 'report:retention:view')")
    @Operation(summary = "Get retention and churn report data")
    fun getRetentionReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) groupBy: String?,
        authentication: Authentication,
    ): ResponseEntity<RetentionReportResponse> {
        val params = resolveParams(from, to, branchId, groupBy, authentication)
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(retentionReportService.generate(clubId, params))
    }

    @GetMapping("/retention/export")
    @PreAuthorize("hasPermission(null, 'report:retention:view')")
    @Operation(summary = "Export retention report as CSV")
    fun exportRetentionReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) groupBy: String?,
        authentication: Authentication,
    ): ResponseEntity<ByteArray> {
        val params = resolveParams(from, to, branchId, groupBy, authentication)
        val clubId = resolveClubId(authentication)
        val report = retentionReportService.generate(clubId, params)

        val csv = buildRetentionCsv(report)
        return csvResponse(csv, "report-retention-${params.from}-${params.to}.csv")
    }

    // ── Leads ──────────────────────────────────────────────────────────────

    @GetMapping("/leads")
    @PreAuthorize("hasPermission(null, 'report:leads:view')")
    @Operation(summary = "Get lead conversion funnel report data")
    fun getLeadReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) groupBy: String?,
        authentication: Authentication,
    ): ResponseEntity<LeadReportResponse> {
        val params = resolveParams(from, to, branchId, groupBy, authentication)
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(leadReportService.generate(clubId, params))
    }

    @GetMapping("/leads/export")
    @PreAuthorize("hasPermission(null, 'report:leads:view')")
    @Operation(summary = "Export lead funnel report as CSV")
    fun exportLeadReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) groupBy: String?,
        authentication: Authentication,
    ): ResponseEntity<ByteArray> {
        val params = resolveParams(from, to, branchId, groupBy, authentication)
        val clubId = resolveClubId(authentication)
        val report = leadReportService.generate(clubId, params)

        val csv = buildLeadCsv(report)
        return csvResponse(csv, "report-leads-${params.from}-${params.to}.csv")
    }

    // ── Cash Drawer ────────────────────────────────────────────────────────

    @GetMapping("/cash-drawer")
    @PreAuthorize("hasPermission(null, 'report:cash-drawer:view')")
    @Operation(summary = "Get cash drawer summary report data")
    fun getCashDrawerReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) groupBy: String?,
        authentication: Authentication,
    ): ResponseEntity<CashDrawerReportResponse> {
        val params = resolveParams(from, to, branchId, groupBy, authentication)
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(cashDrawerReportService.generate(clubId, params))
    }

    @GetMapping("/cash-drawer/export")
    @PreAuthorize("hasPermission(null, 'report:cash-drawer:view')")
    @Operation(summary = "Export cash drawer report as CSV")
    fun exportCashDrawerReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) groupBy: String?,
        authentication: Authentication,
    ): ResponseEntity<ByteArray> {
        val params = resolveParams(from, to, branchId, groupBy, authentication)
        val clubId = resolveClubId(authentication)
        val report = cashDrawerReportService.generate(clubId, params)

        val csv = buildCashDrawerCsv(report)
        return csvResponse(csv, "report-cash-drawer-${params.from}-${params.to}.csv")
    }

    // ── Shared helpers ─────────────────────────────────────────────────────

    private fun resolveParams(
        from: LocalDate?,
        to: LocalDate?,
        branchPublicId: UUID?,
        groupByParam: String?,
        authentication: Authentication,
    ): ReportQueryParams {
        if (from == null || to == null) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Both 'from' and 'to' date parameters are required.",
            )
        }
        if (from.isAfter(to)) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "'from' date cannot be after 'to' date.",
            )
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Date range cannot exceed one year.",
            )
        }

        val branchId =
            if (branchPublicId != null) {
                val claims = authentication.pulseContext()
                val orgId = resolveOrgId(claims.requireOrganizationId())
                val clubInternalId = resolveClubInternalId(claims.requireClubId(), orgId)
                branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                    branchPublicId,
                    orgId,
                    clubInternalId,
                ).orElseThrow {
                    ArenaException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "business-rule-violation",
                        "Branch does not belong to this club.",
                    )
                }.id
            } else {
                null
            }

        val groupBy = ReportQueryParams.resolveGroupBy(from, to, groupByParam)
        return ReportQueryParams(from = from, to = to, branchId = branchId, groupBy = groupBy)
    }

    private fun resolveClubId(authentication: Authentication): Long {
        val claims = authentication.pulseContext()
        val orgId = resolveOrgId(claims.requireOrganizationId())
        return resolveClubInternalId(claims.requireClubId(), orgId)
    }

    private fun resolveOrgId(orgPublicId: UUID): Long =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }
            .id

    private fun resolveClubInternalId(
        clubPublicId: UUID,
        organizationId: Long,
    ): Long =
        clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }
            .id

    // ── CSV builders ───────────────────────────────────────────────────────

    private fun buildRevenueCsv(report: RevenueReportResponse): String {
        val sb = StringBuilder()
        sb.appendLine(
            "Period,Period Start,Period End," +
                "Total Revenue (SAR),Membership Revenue (SAR)," +
                "PT Revenue (SAR),Other Revenue (SAR),Payment Count",
        )
        for (p in report.periods) {
            sb.appendLine(
                "${p.label},${p.periodStart},${p.periodEnd}," +
                    "${p.totalRevenue.sar},${p.membershipRevenue.sar}," +
                    "${p.ptRevenue.sar},${p.otherRevenue.sar},${p.paymentCount}",
            )
        }
        return sb.toString()
    }

    private fun buildRetentionCsv(report: RetentionReportResponse): String {
        val sb = StringBuilder()
        sb.appendLine("Period,Period Start,Period End,New Members,Renewals,Expired,Active At End,Churn Rate (%)")
        for (p in report.periods) {
            sb.appendLine(
                "${p.label},${p.periodStart},${p.periodEnd},${p.newMembers},${p.renewals},${p.expired},${p.activeAtEnd},${p.churnRate}",
            )
        }
        return sb.toString()
    }

    private fun buildLeadCsv(report: LeadReportResponse): String {
        val sb = StringBuilder()
        sb.appendLine("Period,Period Start,Period End,New Leads,Converted,Lost,Conversion Rate (%)")
        for (p in report.periods) {
            sb.appendLine("${p.label},${p.periodStart},${p.periodEnd},${p.newLeads},${p.converted},${p.lost},${p.conversionRate}")
        }
        return sb.toString()
    }

    private fun buildCashDrawerCsv(report: CashDrawerReportResponse): String {
        val sb = StringBuilder()
        sb.appendLine(
            "Period,Period Start,Period End,Sessions," +
                "Cash In (SAR),Cash Out (SAR),Net Cash (SAR)," +
                "Shortages (SAR),Surpluses (SAR)",
        )
        for (p in report.periods) {
            sb.appendLine(
                "${p.label},${p.periodStart},${p.periodEnd}," +
                    "${p.sessionCount},${p.totalCashIn.sar}," +
                    "${p.totalCashOut.sar},${p.netCash.sar}," +
                    "${p.shortages.sar},${p.surpluses.sar}",
            )
        }
        return sb.toString()
    }

    private fun csvResponse(
        csv: String,
        filename: String,
    ): ResponseEntity<ByteArray> {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + csv.toByteArray(Charsets.UTF_8)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes)
    }
}

// ── Auth helpers (same pattern as MembershipPulseController) ─────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
