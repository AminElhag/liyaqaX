package com.liyaqa.report.dto

data class CashDrawerReportResponse(
    val summary: CashDrawerReportSummary,
    val periods: List<TimePeriodCashDrawer>,
)

data class CashDrawerReportSummary(
    val totalSessions: Long,
    val totalCashIn: MoneyAmount,
    val totalCashOut: MoneyAmount,
    val netCash: MoneyAmount,
    val totalShortages: MoneyAmount,
    val totalSurpluses: MoneyAmount,
    val sessionsWithDiscrepancy: Long,
    val reconciliationRate: Double,
)
