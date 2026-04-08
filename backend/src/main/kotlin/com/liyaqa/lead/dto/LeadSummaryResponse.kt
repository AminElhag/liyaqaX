package com.liyaqa.lead.dto

import java.time.Instant
import java.util.UUID

data class LeadSummaryResponse(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val stage: String,
    val leadSource: LeadSourceSummary?,
    val assignedStaff: LeadStaffSummary?,
    val createdAt: Instant,
)
