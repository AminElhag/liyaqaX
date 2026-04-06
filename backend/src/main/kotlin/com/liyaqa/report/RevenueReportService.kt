package com.liyaqa.report

import com.liyaqa.report.dto.MoneyAmount
import com.liyaqa.report.dto.ReportQueryParams
import com.liyaqa.report.dto.RevenueReportResponse
import com.liyaqa.report.dto.RevenueReportSummary
import com.liyaqa.report.dto.TimePeriodRevenue
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class RevenueReportService(
    private val em: EntityManager,
) {
    fun generate(
        clubId: Long,
        params: ReportQueryParams,
    ): RevenueReportResponse {
        val summary = computeSummary(clubId, params)
        val periods = computePeriods(clubId, params)
        return RevenueReportResponse(summary = summary, periods = periods)
    }

    private fun computeSummary(
        clubId: Long,
        params: ReportQueryParams,
    ): RevenueReportSummary {
        val branchFilter = if (params.branchId != null) "AND p.branch_id = :branchId" else ""

        @Suppress("SqlResolve")
        val sql =
            """
            SELECT
                COALESCE(SUM(p.amount_halalas), 0) AS total,
                COALESCE(SUM(CASE WHEN p.membership_id IS NOT NULL THEN p.amount_halalas ELSE 0 END), 0) AS membership,
                COALESCE(SUM(CASE WHEN p.membership_id IS NULL AND p.payment_method != 'other' THEN p.amount_halalas ELSE 0 END), 0) AS pt,
                COALESCE(SUM(CASE WHEN p.membership_id IS NULL AND p.payment_method = 'other' THEN p.amount_halalas ELSE 0 END), 0) AS other_rev,
                COUNT(*) AS cnt
            FROM payments p
            WHERE p.club_id = :clubId
              AND CAST(p.paid_at AS DATE) >= :fromDate
              AND CAST(p.paid_at AS DATE) <= :toDate
              $branchFilter
            """.trimIndent()

        val query =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", params.from)
                .setParameter("toDate", params.to)
        if (params.branchId != null) query.setParameter("branchId", params.branchId)

        val row = query.singleResult as Array<*>
        val totalHalalas = (row[0] as Number).toLong()
        val membershipHalalas = (row[1] as Number).toLong()
        val ptHalalas = (row[2] as Number).toLong()
        val otherHalalas = (row[3] as Number).toLong()
        val totalPayments = (row[4] as Number).toLong()

        val avgPayment = if (totalPayments > 0) totalHalalas / totalPayments else 0L

        val (compFrom, compTo) = ReportPeriodHelper.comparisonPeriod(params.from, params.to)
        val compRevenue = computeTotalRevenue(clubId, compFrom, compTo, params.branchId)

        val growthPercent =
            if (compRevenue > 0) {
                BigDecimal(totalHalalas - compRevenue)
                    .multiply(BigDecimal(100))
                    .divide(BigDecimal(compRevenue), 1, java.math.RoundingMode.HALF_UP)
                    .toDouble()
            } else {
                null
            }

        return RevenueReportSummary(
            totalRevenue = MoneyAmount.of(totalHalalas),
            membershipRevenue = MoneyAmount.of(membershipHalalas),
            ptRevenue = MoneyAmount.of(ptHalalas),
            otherRevenue = MoneyAmount.of(otherHalalas),
            totalPayments = totalPayments,
            averagePaymentValue = MoneyAmount.of(avgPayment),
            comparisonPeriodRevenue = MoneyAmount.of(compRevenue),
            growthPercent = growthPercent,
        )
    }

    private fun computeTotalRevenue(
        clubId: Long,
        from: LocalDate,
        to: LocalDate,
        branchId: Long?,
    ): Long {
        val branchFilter = if (branchId != null) "AND p.branch_id = :branchId" else ""

        @Suppress("SqlResolve")
        val sql =
            """
            SELECT COALESCE(SUM(p.amount_halalas), 0)
            FROM payments p
            WHERE p.club_id = :clubId
              AND CAST(p.paid_at AS DATE) >= :fromDate
              AND CAST(p.paid_at AS DATE) <= :toDate
              $branchFilter
            """.trimIndent()

        val query =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
        if (branchId != null) query.setParameter("branchId", branchId)

        return (query.singleResult as Number).toLong()
    }

    private fun computePeriods(
        clubId: Long,
        params: ReportQueryParams,
    ): List<TimePeriodRevenue> {
        val periods = ReportPeriodHelper.generatePeriods(params.from, params.to, params.groupBy)
        return periods.map { period ->
            val periodParams = params.copy(from = period.start, to = period.end)
            val branchFilter = if (params.branchId != null) "AND p.branch_id = :branchId" else ""

            @Suppress("SqlResolve")
            val sql =
                """
                SELECT
                    COALESCE(SUM(p.amount_halalas), 0) AS total,
                    COALESCE(SUM(CASE WHEN p.membership_id IS NOT NULL THEN p.amount_halalas ELSE 0 END), 0) AS membership,
                    COALESCE(SUM(CASE WHEN p.membership_id IS NULL AND p.payment_method != 'other' THEN p.amount_halalas ELSE 0 END), 0) AS pt,
                    COALESCE(SUM(CASE WHEN p.membership_id IS NULL AND p.payment_method = 'other' THEN p.amount_halalas ELSE 0 END), 0) AS other_rev,
                    COUNT(*) AS cnt
                FROM payments p
                WHERE p.club_id = :clubId
                  AND CAST(p.paid_at AS DATE) >= :fromDate
                  AND CAST(p.paid_at AS DATE) <= :toDate
                  $branchFilter
                """.trimIndent()

            val query =
                em.createNativeQuery(sql)
                    .setParameter("clubId", clubId)
                    .setParameter("fromDate", periodParams.from)
                    .setParameter("toDate", periodParams.to)
            if (params.branchId != null) query.setParameter("branchId", params.branchId)

            val row = query.singleResult as Array<*>
            TimePeriodRevenue(
                label = period.label,
                periodStart = period.start.toString(),
                periodEnd = period.end.toString(),
                totalRevenue = MoneyAmount.of((row[0] as Number).toLong()),
                membershipRevenue = MoneyAmount.of((row[1] as Number).toLong()),
                ptRevenue = MoneyAmount.of((row[2] as Number).toLong()),
                otherRevenue = MoneyAmount.of((row[3] as Number).toLong()),
                paymentCount = (row[4] as Number).toLong(),
            )
        }
    }
}
