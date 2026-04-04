package com.liyaqa.gx.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class BookMemberRequest(
    @field:NotNull
    val memberId: UUID,
)
