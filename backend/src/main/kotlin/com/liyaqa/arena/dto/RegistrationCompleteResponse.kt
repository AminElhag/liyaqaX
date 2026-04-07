package com.liyaqa.arena.dto

import java.util.UUID

data class RegistrationCompleteResponse(
    val memberId: UUID,
    val status: String,
)
