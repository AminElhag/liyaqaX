package com.liyaqa.checkin.dto

import java.time.Instant
import java.util.UUID

data class RecentCheckInsResponse(
    val checkIns: List<RecentCheckInItem>,
)

data class RecentCheckInItem(
    val checkInId: UUID,
    val memberName: String,
    val memberPhone: String,
    val method: String,
    val checkedInAt: Instant,
)
