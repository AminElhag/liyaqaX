package com.liyaqa.report.dto

data class RetentionReportResponse(
    val summary: RetentionReportSummary,
    val periods: List<TimePeriodRetention>,
    val atRisk: List<AtRiskMember>,
)

data class RetentionReportSummary(
    val activeMembers: Long,
    val expiredThisPeriod: Long,
    val newMembersThisPeriod: Long,
    val renewedThisPeriod: Long,
    val churnRate: Double,
    val retentionRate: Double,
    val expiringNext30Days: Long,
)
