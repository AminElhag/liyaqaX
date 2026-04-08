package com.liyaqa.report

import com.liyaqa.report.dto.LeadReportResponse
import com.liyaqa.report.dto.LeadReportSummary
import com.liyaqa.report.dto.LeadSourceStat
import com.liyaqa.report.dto.LostReasonCount
import com.liyaqa.report.dto.ReportQueryParams
import com.liyaqa.report.dto.TimePeriodLeads
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class LeadReportService(
    private val em: EntityManager,
) {
    fun generate(
        clubId: Long,
        params: ReportQueryParams,
    ): LeadReportResponse {
        val summary = computeSummary(clubId, params)
        val periods = computePeriods(clubId, params)
        val lostReasons = computeLostReasons(clubId, params)
        return LeadReportResponse(summary = summary, periods = periods, lostReasons = lostReasons)
    }

    @Suppress("UNCHECKED_CAST", "SqlResolve")
    private fun computeSummary(
        clubId: Long,
        params: ReportQueryParams,
    ): LeadReportSummary {
        val bf = branchFilter(params.branchId)

        val stageSql =
            """
            SELECT stage, COUNT(*) FROM leads
            WHERE club_id = :clubId AND deleted_at IS NULL
              AND CAST(created_at AS DATE) >= :fromDate AND CAST(created_at AS DATE) <= :toDate $bf
            GROUP BY stage
            """.trimIndent()

        val stageQuery =
            em.createNativeQuery(stageSql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", params.from)
                .setParameter("toDate", params.to)
        if (params.branchId != null) stageQuery.setParameter("branchId", params.branchId)

        val stageRows = stageQuery.resultList as List<Array<*>>
        val byStage = stageRows.associate { (it[0] as String) to (it[1] as Number).toLong() }
        val totalLeads = byStage.values.sum()
        val converted = byStage["converted"] ?: 0L
        val conversionRate = if (totalLeads > 0) roundTo1((converted.toDouble() / totalLeads) * 100.0) else 0.0

        val avgDays = computeAvgDaysToConvert(clubId, params.from, params.to, params.branchId)
        val topSources = computeTopSources(clubId, params)

        return LeadReportSummary(
            totalLeads = totalLeads,
            byStage = byStage,
            conversionRate = conversionRate,
            avgDaysToConvert = avgDays,
            topSources = topSources,
        )
    }

    @Suppress("SqlResolve")
    private fun computeAvgDaysToConvert(
        clubId: Long,
        from: LocalDate,
        to: LocalDate,
        branchId: Long?,
    ): Double? {
        val bf = branchFilter(branchId)

        val sql =
            """
            SELECT AVG(EXTRACT(EPOCH FROM (converted_at - created_at)) / 86400)
            FROM leads
            WHERE club_id = :clubId AND deleted_at IS NULL
              AND stage = 'converted' AND converted_at IS NOT NULL
              AND CAST(converted_at AS DATE) >= :fromDate AND CAST(converted_at AS DATE) <= :toDate $bf
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
        if (branchId != null) q.setParameter("branchId", branchId)

        val result = q.singleResult ?: return null
        return BigDecimal((result as Number).toDouble()).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    @Suppress("UNCHECKED_CAST", "SqlResolve")
    private fun computeTopSources(
        clubId: Long,
        params: ReportQueryParams,
    ): List<LeadSourceStat> {
        val bf = branchFilter(params.branchId)

        val sql =
            """
            SELECT ls.name, ls.name_ar, ls.color,
                   COUNT(*) AS total,
                   SUM(CASE WHEN l.stage = 'converted' THEN 1 ELSE 0 END) AS conv
            FROM leads l
            JOIN lead_sources ls ON ls.id = l.lead_source_id
            WHERE l.club_id = :clubId AND l.deleted_at IS NULL
              AND CAST(l.created_at AS DATE) >= :fromDate AND CAST(l.created_at AS DATE) <= :toDate $bf
            GROUP BY ls.id, ls.name, ls.name_ar, ls.color
            ORDER BY total DESC
            LIMIT 10
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", params.from)
                .setParameter("toDate", params.to)
        if (params.branchId != null) q.setParameter("branchId", params.branchId)

        val rows = q.resultList as List<Array<*>>
        return rows.map { row ->
            val total = (row[3] as Number).toLong()
            val conv = (row[4] as Number).toLong()
            val rate = if (total > 0) roundTo1((conv.toDouble() / total) * 100.0) else 0.0
            LeadSourceStat(
                sourceName = row[0] as String,
                sourceNameAr = row[1] as String,
                color = row[2] as String,
                count = total,
                conversionRate = rate,
            )
        }
    }

    @Suppress("UNCHECKED_CAST", "SqlResolve")
    private fun computeLostReasons(
        clubId: Long,
        params: ReportQueryParams,
    ): List<LostReasonCount> {
        val bf = branchFilter(params.branchId)

        val sql =
            """
            SELECT lost_reason, COUNT(*) AS cnt
            FROM leads
            WHERE club_id = :clubId AND deleted_at IS NULL
              AND stage = 'lost' AND lost_reason IS NOT NULL AND lost_reason != ''
              AND CAST(lost_at AS DATE) >= :fromDate AND CAST(lost_at AS DATE) <= :toDate $bf
            GROUP BY lost_reason
            ORDER BY cnt DESC
            LIMIT 10
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", params.from)
                .setParameter("toDate", params.to)
        if (params.branchId != null) q.setParameter("branchId", params.branchId)

        val rows = q.resultList as List<Array<*>>
        return rows.map { row ->
            LostReasonCount(
                reason = row[0] as String,
                count = (row[1] as Number).toLong(),
            )
        }
    }

    private fun computePeriods(
        clubId: Long,
        params: ReportQueryParams,
    ): List<TimePeriodLeads> {
        val periods = ReportPeriodHelper.generatePeriods(params.from, params.to, params.groupBy)
        val bf = branchFilter(params.branchId)

        return periods.map { period ->
            @Suppress("SqlResolve")
            val sql =
                """
                SELECT
                    COUNT(*) AS total_new,
                    COALESCE(SUM(CASE WHEN stage = 'converted' AND CAST(converted_at AS DATE) >= :ps AND CAST(converted_at AS DATE) <= :pe THEN 1 ELSE 0 END), 0) AS conv,
                    COALESCE(SUM(CASE WHEN stage = 'lost' AND CAST(lost_at AS DATE) >= :ps AND CAST(lost_at AS DATE) <= :pe THEN 1 ELSE 0 END), 0) AS lost
                FROM leads
                WHERE club_id = :clubId AND deleted_at IS NULL
                  AND CAST(created_at AS DATE) >= :ps AND CAST(created_at AS DATE) <= :pe $bf
                """.trimIndent()

            val q =
                em.createNativeQuery(sql)
                    .setParameter("clubId", clubId)
                    .setParameter("ps", period.start)
                    .setParameter("pe", period.end)
            if (params.branchId != null) q.setParameter("branchId", params.branchId)

            val row = q.singleResult as Array<*>
            val newLeads = (row[0] as? Number)?.toLong() ?: 0L
            val converted = (row[1] as? Number)?.toLong() ?: 0L
            val lost = (row[2] as? Number)?.toLong() ?: 0L
            val convRate = if (newLeads > 0) roundTo1((converted.toDouble() / newLeads) * 100.0) else 0.0

            TimePeriodLeads(
                label = period.label,
                periodStart = period.start.toString(),
                periodEnd = period.end.toString(),
                newLeads = newLeads,
                converted = converted,
                lost = lost,
                conversionRate = convRate,
            )
        }
    }

    private fun branchFilter(branchId: Long?): String = if (branchId != null) "AND l.branch_id = :branchId" else ""

    private fun roundTo1(value: Double): Double = BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toDouble()
}
