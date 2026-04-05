package com.liyaqa.membership.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class FreezeMembershipRequest(
    @field:NotNull(message = "Freeze start date is required")
    val freezeStartDate: LocalDate,
    @field:NotNull(message = "Freeze end date is required")
    val freezeEndDate: LocalDate,
    val reason: String? = null,
)
