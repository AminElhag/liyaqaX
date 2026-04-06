package com.liyaqa.cashdrawer.dto

import java.time.Instant
import java.util.UUID

data class CashDrawerEntryResponse(
    val id: UUID,
    val entryType: String,
    val amount: MoneyResponse,
    val description: String,
    val paymentId: UUID?,
    val recordedBy: StaffSummary,
    val recordedAt: Instant,
)
