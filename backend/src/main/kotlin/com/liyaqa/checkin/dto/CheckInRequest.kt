package com.liyaqa.checkin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class CheckInRequest(
    @field:NotNull
    val memberPublicId: UUID,
    @field:NotBlank
    val method: String,
)
