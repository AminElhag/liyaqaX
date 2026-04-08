package com.liyaqa.checkin.repository

import java.time.Instant
import java.util.UUID

interface RecentCheckInProjection {
    val publicId: UUID
    val memberNameEn: String?
    val memberNameAr: String
    val phone: String
    val method: String
    val checkedInAt: Instant
}
