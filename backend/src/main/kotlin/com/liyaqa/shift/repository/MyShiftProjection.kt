package com.liyaqa.shift.repository

import java.time.Instant
import java.util.UUID

interface MyShiftProjection {
    val publicId: UUID
    val branchName: String
    val startAt: Instant
    val endAt: Instant
    val notes: String?
}
