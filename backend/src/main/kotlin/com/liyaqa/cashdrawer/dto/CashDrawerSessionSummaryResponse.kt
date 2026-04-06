package com.liyaqa.cashdrawer.dto

import java.time.Instant
import java.util.UUID

data class CashDrawerSessionSummaryResponse(
    val id: UUID,
    val status: String,
    val branch: BranchSummary,
    val openedBy: StaffSummary,
    val openingFloat: MoneyResponse,
    val difference: MoneyResponse?,
    val reconciliationStatus: String?,
    val openedAt: Instant,
    val closedAt: Instant?,
)
