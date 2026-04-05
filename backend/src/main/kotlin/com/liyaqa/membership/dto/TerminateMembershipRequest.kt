package com.liyaqa.membership.dto

import jakarta.validation.constraints.NotBlank

data class TerminateMembershipRequest(
    @field:NotBlank(message = "Termination reason is required")
    val reason: String,
)
