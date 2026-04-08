package com.liyaqa.report.dto

data class TimePeriodCashDrawer(
    val label: String,
    val periodStart: String,
    val periodEnd: String,
    val sessionCount: Long,
    val totalCashIn: MoneyAmount,
    val totalCashOut: MoneyAmount,
    val netCash: MoneyAmount,
    val shortages: MoneyAmount,
    val surpluses: MoneyAmount,
)
