package com.liyaqa.report

import com.liyaqa.report.dto.AtRiskMember
import com.liyaqa.report.dto.ReportQueryParams
import com.liyaqa.report.dto.RetentionReportResponse
import com.liyaqa.report.dto.RetentionReportSummary
import com.liyaqa.report.dto.TimePeriodRetention
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class RetentionReportService(
    private val em: EntityManager,
) {
    fun generate(
        clubId: Long,
        params: ReportQueryParams,
    ): RetentionReportResponse {
        val summary = computeSummary(clubId, params)
        val periods = computePeriods(clubId, params)
        val atRisk = computeAtRisk(clubId, params.branchId)
        return RetentionReportResponse(summary = summary, periods = periods, atRisk = atRisk)
    }

    private fun computeSummary(
        clubId: Long,
        params: ReportQueryParams,
    ): RetentionReportSummary {
        val bf = branchFilter(params.branchId)

        val activeMembers = countByStatus(clubId, params.branchId, "active")

        val expiredThisPeriod = countExpiredInRange(clubId, params.from, params.to, params.branchId)
        val newMembersThisPeriod = countCreatedInRange(clubId, params.from, params.to, params.branchId)
        val renewedThisPeriod = countRenewedInRange(clubId, params.from, params.to, params.branchId)

        val activeAtStart = countActiveAtDate(clubId, params.from, params.branchId)
        val churnRate = if (activeAtStart > 0) (expiredThisPeriod.toDouble() / activeAtStart) * 100.0 else 0.0
        val retentionRate = 100.0 - churnRate

        val expiringNext30 = countExpiringInDays(clubId, 30, params.branchId)

        return RetentionReportSummary(
            activeMembers = activeMembers,
            expiredThisPeriod = expiredThisPeriod,
            newMembersThisPeriod = newMembersThisPeriod,
            renewedThisPeriod = renewedThisPeriod,
            churnRate = roundTo1(churnRate),
            retentionRate = roundTo1(retentionRate),
            expiringNext30Days = expiringNext30,
        )
    }

    private fun countByStatus(
        clubId: Long,
        branchId: Long?,
        status: String,
    ): Long {
        val bf = branchFilter(branchId)

        @Suppress("SqlResolve")
        val sql =
            """
            SELECT COUNT(*) FROM memberships
            WHERE club_id = :clubId AND membership_status = :status AND deleted_at IS NULL $bf
            """.trimIndent()

        val q = em.createNativeQuery(sql).setParameter("clubId", clubId).setParameter("status", status)
        if (branchId != null) q.setParameter("branchId", branchId)
        return (q.singleResult as Number).toLong()
    }

    private fun countExpiredInRange(
        clubId: Long,
        from: LocalDate,
        to: LocalDate,
        branchId: Long?,
    ): Long {
        val bf = branchFilter(branchId)

        @Suppress("SqlResolve")
        val sql =
            """
            SELECT COUNT(*) FROM memberships
            WHERE club_id = :clubId AND deleted_at IS NULL
              AND membership_status IN ('expired', 'terminated')
              AND end_date >= :fromDate AND end_date <= :toDate $bf
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
        if (branchId != null) q.setParameter("branchId", branchId)
        return (q.singleResult as Number).toLong()
    }

    private fun countCreatedInRange(
        clubId: Long,
        from: LocalDate,
        to: LocalDate,
        branchId: Long?,
    ): Long {
        val bf = branchFilter(branchId)

        @Suppress("SqlResolve")
        val sql =
            """
            SELECT COUNT(*) FROM memberships
            WHERE club_id = :clubId AND deleted_at IS NULL
              AND CAST(created_at AS DATE) >= :fromDate AND CAST(created_at AS DATE) <= :toDate $bf
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
        if (branchId != null) q.setParameter("branchId", branchId)
        return (q.singleResult as Number).toLong()
    }

    private fun countRenewedInRange(
        clubId: Long,
        from: LocalDate,
        to: LocalDate,
        branchId: Long?,
    ): Long {
        val bf = branchFilter(branchId)

        @Suppress("SqlResolve")
        val sql =
            """
            SELECT COUNT(*) FROM memberships m
            WHERE m.club_id = :clubId AND m.deleted_at IS NULL
              AND CAST(m.created_at AS DATE) >= :fromDate AND CAST(m.created_at AS DATE) <= :toDate
              AND EXISTS (
                  SELECT 1 FROM memberships prev
                  WHERE prev.member_id = m.member_id AND prev.id != m.id AND prev.deleted_at IS NULL
              ) $bf
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
        if (branchId != null) q.setParameter("branchId", branchId)
        return (q.singleResult as Number).toLong()
    }

    private fun countActiveAtDate(
        clubId: Long,
        date: LocalDate,
        branchId: Long?,
    ): Long {
        val bf = branchFilter(branchId)

        @Suppress("SqlResolve")
        val sql =
            """
            SELECT COUNT(*) FROM memberships
            WHERE club_id = :clubId AND deleted_at IS NULL
              AND start_date <= :date AND end_date >= :date
              AND membership_status IN ('active', 'frozen') $bf
            """.trimIndent()

        val q = em.createNativeQuery(sql).setParameter("clubId", clubId).setParameter("date", date)
        if (branchId != null) q.setParameter("branchId", branchId)
        return (q.singleResult as Number).toLong()
    }

    private fun countExpiringInDays(
        clubId: Long,
        days: Int,
        branchId: Long?,
    ): Long {
        val bf = branchFilter(branchId)
        val today = LocalDate.now()
        val cutoff = today.plusDays(days.toLong())

        @Suppress("SqlResolve")
        val sql =
            """
            SELECT COUNT(*) FROM memberships
            WHERE club_id = :clubId AND deleted_at IS NULL
              AND membership_status = 'active'
              AND end_date >= :today AND end_date <= :cutoff $bf
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("today", today)
                .setParameter("cutoff", cutoff)
        if (branchId != null) q.setParameter("branchId", branchId)
        return (q.singleResult as Number).toLong()
    }

    @Suppress("UNCHECKED_CAST", "SqlResolve")
    private fun computeAtRisk(
        clubId: Long,
        branchId: Long?,
    ): List<AtRiskMember> {
        val bf = branchFilter(branchId)
        val today = LocalDate.now()
        val cutoff = today.plusDays(30)

        val sql =
            """
            SELECT
                m.public_id AS member_public_id,
                CONCAT(mem.first_name_en, ' ', mem.last_name_en) AS member_name,
                mp.name_en AS plan_name,
                m.end_date,
                p.paid_at AS last_payment_date
            FROM memberships m
            JOIN members mem ON mem.id = m.member_id
            JOIN membership_plans mp ON mp.id = m.plan_id
            LEFT JOIN LATERAL (
                SELECT paid_at FROM payments pay
                WHERE pay.member_id = m.member_id
                ORDER BY pay.paid_at DESC LIMIT 1
            ) p ON true
            WHERE m.club_id = :clubId AND m.deleted_at IS NULL
              AND m.membership_status = 'active'
              AND m.end_date >= :today AND m.end_date <= :cutoff $bf
            ORDER BY m.end_date ASC
            LIMIT 20
            """.trimIndent()

        val q =
            em.createNativeQuery(sql)
                .setParameter("clubId", clubId)
                .setParameter("today", today)
                .setParameter("cutoff", cutoff)
        if (branchId != null) q.setParameter("branchId", branchId)

        val rows = q.resultList as List<Array<*>>
        return rows.map { row ->
            val endDateRaw = row[3]
            val endDate =
                when (endDateRaw) {
                    is LocalDate -> endDateRaw
                    is java.sql.Date -> endDateRaw.toLocalDate()
                    else -> LocalDate.now()
                }
            val lastPaymentRaw = row[4]
            val lastPaymentStr =
                when (lastPaymentRaw) {
                    is java.time.Instant -> lastPaymentRaw.toString().substring(0, 10)
                    is java.sql.Timestamp -> lastPaymentRaw.toInstant().toString().substring(0, 10)
                    else -> null
                }
            AtRiskMember(
                memberId = row[0].toString(),
                memberName = row[1] as String,
                membershipPlan = row[2] as String,
                expiresAt = endDate.toString(),
                daysUntilExpiry = ChronoUnit.DAYS.between(today, endDate),
                lastPaymentDate = lastPaymentStr,
            )
        }
    }

    private fun computePeriods(
        clubId: Long,
        params: ReportQueryParams,
    ): List<TimePeriodRetention> {
        val periods = ReportPeriodHelper.generatePeriods(params.from, params.to, params.groupBy)
        return periods.map { period ->
            val newMembers = countCreatedInRange(clubId, period.start, period.end, params.branchId)
            val renewals = countRenewedInRange(clubId, period.start, period.end, params.branchId)
            val expired = countExpiredInRange(clubId, period.start, period.end, params.branchId)
            val activeAtEnd = countActiveAtDate(clubId, period.end, params.branchId)
            val activeAtStart = countActiveAtDate(clubId, period.start, params.branchId)
            val churnRate = if (activeAtStart > 0) (expired.toDouble() / activeAtStart) * 100.0 else 0.0

            TimePeriodRetention(
                label = period.label,
                periodStart = period.start.toString(),
                periodEnd = period.end.toString(),
                newMembers = newMembers,
                renewals = renewals,
                expired = expired,
                activeAtEnd = activeAtEnd,
                churnRate = roundTo1(churnRate),
            )
        }
    }

    private fun branchFilter(branchId: Long?): String = if (branchId != null) "AND branch_id = :branchId" else ""

    private fun roundTo1(value: Double): Double = java.math.BigDecimal(value).setScale(1, java.math.RoundingMode.HALF_UP).toDouble()
}
