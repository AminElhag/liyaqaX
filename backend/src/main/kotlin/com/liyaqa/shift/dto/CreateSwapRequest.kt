package com.liyaqa.shift.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CreateSwapRequest(
    @field:NotNull
    val targetStaffPublicId: UUID,
    val requesterNote: String? = null,
)
