package com.liyaqa.nexus.dto

import java.time.Instant
import java.util.UUID

data class OrgDetailResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val vatNumber: String?,
    val createdAt: Instant,
    val clubs: List<ClubSummaryItem>,
)

data class ClubSummaryItem(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val branchCount: Int,
    val activeMemberCount: Int,
)
