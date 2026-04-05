package com.liyaqa.membership.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class MembershipResponse(
    val id: UUID,
    val memberId: UUID,
    val plan: MembershipPlanSummaryInfo,
    val status: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val graceEndDate: LocalDate?,
    val freezeDaysUsed: Int,
    val payment: MembershipPaymentInfo?,
    val invoice: MembershipInvoiceInfo?,
    val createdAt: Instant,
)

data class MembershipPlanSummaryInfo(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val priceHalalas: Long,
    val priceSar: String,
    val durationDays: Int,
    val freezeAllowed: Boolean,
    val maxFreezeDays: Int,
)

data class MembershipPaymentInfo(
    val id: UUID,
    val amountHalalas: Long,
    val amountSar: String,
    val paymentMethod: String,
    val paidAt: Instant,
)

data class MembershipInvoiceInfo(
    val id: UUID,
    val invoiceNumber: String,
    val totalHalalas: Long,
    val totalSar: String,
    val issuedAt: Instant,
)
