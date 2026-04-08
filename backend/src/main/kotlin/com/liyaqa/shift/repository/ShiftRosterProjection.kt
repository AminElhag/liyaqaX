package com.liyaqa.shift.repository

import java.time.Instant
import java.util.UUID

interface ShiftRosterProjection {
    val id: Long
    val publicId: UUID
    val staffMemberId: Long
    val staffPublicId: UUID
    val staffNameEn: String?
    val staffNameAr: String
    val startAt: Instant
    val endAt: Instant
    val notes: String?
}
