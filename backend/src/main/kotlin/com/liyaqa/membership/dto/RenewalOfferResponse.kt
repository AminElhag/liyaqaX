package com.liyaqa.membership.dto

import java.time.Instant
import java.util.UUID

data class RenewalOfferResponse(
    val noteId: UUID,
    val followUpAt: Instant,
    val message: String,
)
