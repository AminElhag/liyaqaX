package com.liyaqa.report.dto

data class RevenueReportResponse(
    val summary: RevenueReportSummary,
    val periods: List<TimePeriodRevenue>,
)

data class RevenueReportSummary(
    val totalRevenue: MoneyAmount,
    val membershipRevenue: MoneyAmount,
    val ptRevenue: MoneyAmount,
    val otherRevenue: MoneyAmount,
    val totalPayments: Long,
    val averagePaymentValue: MoneyAmount,
    val comparisonPeriodRevenue: MoneyAmount,
    val growthPercent: Double?,
)
