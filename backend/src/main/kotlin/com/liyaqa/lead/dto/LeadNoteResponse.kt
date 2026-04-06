package com.liyaqa.lead.dto

import java.time.Instant
import java.util.UUID

data class LeadNoteResponse(
    val id: UUID,
    val body: String,
    val staff: LeadStaffSummary,
    val createdAt: Instant,
)
