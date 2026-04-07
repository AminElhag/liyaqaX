package com.liyaqa.member.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RejectMemberRequest(
    @field:NotBlank(message = "Reason is required")
    @field:Size(min = 10, max = 1000, message = "Reason must be between 10 and 1000 characters")
    val reason: String,
)
