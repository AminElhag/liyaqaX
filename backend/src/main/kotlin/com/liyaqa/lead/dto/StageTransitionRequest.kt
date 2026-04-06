package com.liyaqa.lead.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class StageTransitionRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^(new|contacted|interested|lost)$")
    val stage: String,
    val lostReason: String? = null,
)
