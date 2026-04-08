package com.liyaqa.shift.repository

import java.time.Instant
import java.util.UUID

interface PendingSwapProjection {
    val publicId: UUID
    val shiftStart: Instant
    val shiftEnd: Instant
    val requesterNameEn: String?
    val requesterNameAr: String
    val targetNameEn: String?
    val targetNameAr: String
    val requesterNote: String?
}
