package com.liyaqa.membership.dto

import java.time.LocalDate
import java.util.UUID

data class MembershipSummaryResponse(
    val id: UUID,
    val planNameAr: String,
    val planNameEn: String,
    val status: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val amountHalalas: Long,
    val amountSar: String,
    val paymentMethod: String?,
)
