package com.liyaqa.zatca.dto

data class ZatcaHealthSummary(
    val totalActiveCsids: Long,
    val csidsExpiringSoon: Long,
    val clubsNotOnboarded: Long,
    val invoicesPending: Long,
    val invoicesFailed: Long,
    val invoicesDeadlineAtRisk: Long,
)
