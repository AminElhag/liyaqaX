package com.liyaqa.cashdrawer.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class ReconcileSessionRequest(
    @field:NotBlank(message = "Reconciliation status is required")
    @field:Pattern(regexp = "^(approved|flagged)$", message = "Must be 'approved' or 'flagged'")
    val reconciliationStatus: String,
    val reconciliationNotes: String? = null,
)
