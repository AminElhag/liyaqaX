package com.liyaqa.report

import com.liyaqa.report.dto.CashDrawerReportResponse
import com.liyaqa.report.dto.CashDrawerReportSummary
import com.liyaqa.report.dto.MoneyAmount
import com.liyaqa.report.dto.ReportQueryParams
import com.liyaqa.report.dto.TimePeriodCashDrawer
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional(readOnly = true)
class CashDrawerReportService(
    private val em: EntityManager,
) {
    fun generate(
        clubId: Long,
        params: ReportQueryParams,
    ): CashDrawerReportResponse {
        val summary = computeSummary(clubId, params)
        val periods = computePeriods(clubId, params)
        return CashDrawerReportResponse(summary = summary, periods = periods)
    }

    @Suppress("SqlResolve")
    private fun computeSummary(
        clubId: Long,
        params: ReportQueryParams,
    ): CashDrawerReportSummary {
        val bf = branchFilter(params.branchId)

        val sql =
            """
            SELECT
                COUNT(*) AS total_sessions,
                COALESCE(SUM(e_in.cash_in), 0) AS total_cash_in,
                COALESCE(SUM(e_out.cash_out), 0) AS total_cash_out,
                COALESCE(SUM(CASE WHEN s.difference_halalas < 0 THEN ABS(s.difference_halalas) ELSE 0 END), 0) AS shortages,
                COALESCE(SUM(CASE WHEN s.difference_halalas > 0 THEN s.difference_halalas ELSE 0 END), 0) AS surpluses,
                SUM(CASE WHEN s.difference_halalas IS NOT NULL AND s.difference_halalas != 0 THEN 1 ELSE 0 END) AS discrepancy_count,
                SUM(CASE WHEN s.status = 'reconciled' THEN 1 ELSE 0 END) AS reconciled_count
            FROM cash_drawer_sessions s
            LEFT JOIN LATERAL (
                SELECT COALESCE(SUM(amount_halalas), 0) AS cash_in
                FROM cash_drawer_entries WHERE session_id = s.id AND entry_type = 'cash_in'
            ) e_in ON true
            LEFT JOIN LATERAL (
                SELECT COALESCE(SUM(amount_halalas), 0) AS cash_out
                FROM cash_drawer_entries WHERE session_id = s.id AND entry_type = 'cash_out'
            ) e_out ON true
            WHERE s.club_id = :clubId
              AND s.status IN ('closed', 'reconciled')
              AND s.deleted_at IS NULL
              AND CAST(s.opened_at AS DATE) >= :fromDate AND CAST(s.opened_at AS DATE) <= :toDate $bf
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", params.from)
                .setParameter("toDate", params.to)
        if (params.branchId != null) q.setParameter("branchId", params.branchId)

        val row = q.singleResult as Array<*>
        val totalSessions = (row[0] as? Number)?.toLong() ?: 0L
        val cashIn = (row[1] as? Number)?.toLong() ?: 0L
        val cashOut = (row[2] as? Number)?.toLong() ?: 0L
        val shortages = (row[3] as? Number)?.toLong() ?: 0L
        val surpluses = (row[4] as? Number)?.toLong() ?: 0L
        val discrepancyCount = (row[5] as? Number)?.toLong() ?: 0L
        val reconciledCount = (row[6] as? Number)?.toLong() ?: 0L

        val reconciliationRate =
            if (totalSessions > 0) {
                BigDecimal(reconciledCount)
                    .multiply(BigDecimal(100))
                    .divide(BigDecimal(totalSessions), 1, RoundingMode.HALF_UP)
                    .toDouble()
            } else {
                0.0
            }

        return CashDrawerReportSummary(
            totalSessions = totalSessions,
            totalCashIn = MoneyAmount.of(cashIn),
            totalCashOut = MoneyAmount.of(cashOut),
            netCash = MoneyAmount.of(cashIn - cashOut),
            totalShortages = MoneyAmount.of(shortages),
            totalSurpluses = MoneyAmount.of(surpluses),
            sessionsWithDiscrepancy = discrepancyCount,
            reconciliationRate = reconciliationRate,
        )
    }

    @Suppress("SqlResolve")
    private fun computePeriods(
        clubId: Long,
        params: ReportQueryParams,
    ): List<TimePeriodCashDrawer> {
        val periods = ReportPeriodHelper.generatePeriods(params.from, params.to, params.groupBy)
        val bf = branchFilter(params.branchId)

        return periods.map { period ->
            val sql =
                """
                SELECT
                    COUNT(*) AS session_count,
                    COALESCE(SUM(e_in.cash_in), 0) AS cash_in,
                    COALESCE(SUM(e_out.cash_out), 0) AS cash_out,
                    COALESCE(SUM(CASE WHEN s.difference_halalas < 0 THEN ABS(s.difference_halalas) ELSE 0 END), 0) AS shortages,
                    COALESCE(SUM(CASE WHEN s.difference_halalas > 0 THEN s.difference_halalas ELSE 0 END), 0) AS surpluses
                FROM cash_drawer_sessions s
                LEFT JOIN LATERAL (
                    SELECT COALESCE(SUM(amount_halalas), 0) AS cash_in
                    FROM cash_drawer_entries WHERE session_id = s.id AND entry_type = 'cash_in'
                ) e_in ON true
                LEFT JOIN LATERAL (
                    SELECT COALESCE(SUM(amount_halalas), 0) AS cash_out
                    FROM cash_drawer_entries WHERE session_id = s.id AND entry_type = 'cash_out'
                ) e_out ON true
                WHERE s.club_id = :clubId
                  AND s.status IN ('closed', 'reconciled')
                  AND s.deleted_at IS NULL
                  AND CAST(s.opened_at AS DATE) >= :ps AND CAST(s.opened_at AS DATE) <= :pe $bf
                """.trimIndent()

            val q =
                em.createNativeQuery(sql)
                    .setParameter("clubId", clubId)
                    .setParameter("ps", period.start)
                    .setParameter("pe", period.end)
            if (params.branchId != null) q.setParameter("branchId", params.branchId)

            val row = q.singleResult as Array<*>
            val sessionCount = (row[0] as? Number)?.toLong() ?: 0L
            val cashIn = (row[1] as? Number)?.toLong() ?: 0L
            val cashOut = (row[2] as? Number)?.toLong() ?: 0L
            val shortages = (row[3] as? Number)?.toLong() ?: 0L
            val surpluses = (row[4] as? Number)?.toLong() ?: 0L

            TimePeriodCashDrawer(
                label = period.label,
                periodStart = period.start.toString(),
                periodEnd = period.end.toString(),
                sessionCount = sessionCount,
                totalCashIn = MoneyAmount.of(cashIn),
                totalCashOut = MoneyAmount.of(cashOut),
                netCash = MoneyAmount.of(cashIn - cashOut),
                shortages = MoneyAmount.of(shortages),
                surpluses = MoneyAmount.of(surpluses),
            )
        }
    }

    private fun branchFilter(branchId: Long?): String = if (branchId != null) "AND s.branch_id = :branchId" else ""
}
