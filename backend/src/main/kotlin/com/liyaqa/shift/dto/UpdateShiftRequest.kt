package com.liyaqa.shift.dto

import java.time.Instant

data class UpdateShiftRequest(
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val notes: String? = null,
)
