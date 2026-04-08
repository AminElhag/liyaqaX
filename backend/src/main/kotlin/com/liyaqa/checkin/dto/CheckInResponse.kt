package com.liyaqa.checkin.dto

import java.time.Instant
import java.util.UUID

data class CheckInResponse(
    val checkInId: UUID,
    val memberName: String,
    val memberPhone: String,
    val membershipPlan: String?,
    val checkedInAt: Instant,
    val branchName: String,
    val method: String,
    val todayCount: Long,
)
