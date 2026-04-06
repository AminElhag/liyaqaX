package com.liyaqa.report.dto

data class TimePeriodLeads(
    val label: String,
    val periodStart: String,
    val periodEnd: String,
    val newLeads: Long,
    val converted: Long,
    val lost: Long,
    val conversionRate: Double,
)
