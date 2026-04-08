package com.liyaqa.lead.dto

import jakarta.validation.constraints.NotBlank

data class CreateLeadNoteRequest(
    @field:NotBlank
    val body: String,
)
