package com.liyaqa.report.dto

data class TimePeriodRetention(
    val label: String,
    val periodStart: String,
    val periodEnd: String,
    val newMembers: Long,
    val renewals: Long,
    val expired: Long,
    val activeAtEnd: Long,
    val churnRate: Double,
)
