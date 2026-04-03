package com.liyaqa.club.dto

import java.util.UUID

data class ClubSummaryResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val isActive: Boolean,
)
