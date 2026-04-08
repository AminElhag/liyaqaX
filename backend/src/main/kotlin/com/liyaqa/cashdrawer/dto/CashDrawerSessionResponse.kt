package com.liyaqa.cashdrawer.dto

import java.time.Instant
import java.util.UUID

data class MoneyResponse(
    val halalas: Long,
    val sar: String,
)

data class StaffSummary(
    val id: UUID,
    val firstName: String,
    val lastName: String,
)

data class BranchSummary(
    val id: UUID,
    val name: String,
)

data class CashDrawerSessionResponse(
    val id: UUID,
    val status: String,
    val branch: BranchSummary,
    val openedBy: StaffSummary,
    val closedBy: StaffSummary?,
    val reconciledBy: StaffSummary?,
    val openingFloat: MoneyResponse,
    val countedClosing: MoneyResponse?,
    val expectedClosing: MoneyResponse?,
    val difference: MoneyResponse?,
    val reconciliationStatus: String?,
    val reconciliationNotes: String?,
    val openedAt: Instant,
    val closedAt: Instant?,
    val reconciledAt: Instant?,
    val totalCashIn: MoneyResponse,
    val totalCashOut: MoneyResponse,
    val entryCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
