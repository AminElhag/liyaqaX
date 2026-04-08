package com.liyaqa.report.dto

data class TimePeriodRevenue(
    val label: String,
    val periodStart: String,
    val periodEnd: String,
    val totalRevenue: MoneyAmount,
    val membershipRevenue: MoneyAmount,
    val ptRevenue: MoneyAmount,
    val otherRevenue: MoneyAmount,
    val paymentCount: Long,
)
