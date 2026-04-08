package com.liyaqa.report.dto

data class AtRiskMember(
    val memberId: String,
    val memberName: String,
    val membershipPlan: String,
    val expiresAt: String,
    val daysUntilExpiry: Long,
    val lastPaymentDate: String?,
)
