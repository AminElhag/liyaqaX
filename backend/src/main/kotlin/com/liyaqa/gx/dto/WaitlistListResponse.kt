package com.liyaqa.gx.dto

data class WaitlistListResponse(
    val waitlistCount: Int,
    val entries: List<WaitlistEntryResponse>,
)
