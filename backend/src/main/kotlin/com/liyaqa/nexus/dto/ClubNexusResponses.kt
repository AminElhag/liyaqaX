package com.liyaqa.nexus.dto

import java.time.Instant
import java.util.UUID

data class ClubListItemResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val vatNumber: String?,
    val branchCount: Int,
    val staffCount: Int,
    val activeMemberCount: Int,
    val createdAt: Instant,
)

data class ClubDetailNexusResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val vatNumber: String?,
    val branchCount: Int,
    val staffCount: Int,
    val activeMemberCount: Int,
    val estimatedMrrHalalas: Long,
    val estimatedMrrSar: String,
    val createdAt: Instant,
)
