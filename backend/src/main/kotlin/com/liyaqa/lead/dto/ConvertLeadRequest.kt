package com.liyaqa.lead.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class ConvertLeadRequest(
    @field:NotNull
    val branchId: UUID,
    val membershipPlanId: UUID? = null,
)
