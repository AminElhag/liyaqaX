package com.liyaqa.report.dto

data class LeadReportResponse(
    val summary: LeadReportSummary,
    val periods: List<TimePeriodLeads>,
    val lostReasons: List<LostReasonCount>,
)

data class LeadReportSummary(
    val totalLeads: Long,
    val byStage: Map<String, Long>,
    val conversionRate: Double,
    val avgDaysToConvert: Double?,
    val topSources: List<LeadSourceStat>,
)

data class LeadSourceStat(
    val sourceName: String,
    val sourceNameAr: String,
    val color: String,
    val count: Long,
    val conversionRate: Double,
)

data class LostReasonCount(
    val reason: String,
    val count: Long,
)
