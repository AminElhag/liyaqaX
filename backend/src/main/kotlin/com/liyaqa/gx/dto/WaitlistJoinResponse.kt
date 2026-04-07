package com.liyaqa.gx.dto

import java.util.UUID

data class WaitlistJoinResponse(
    val entryId: UUID,
    val position: Int,
    val status: String,
    val message: String,
)
