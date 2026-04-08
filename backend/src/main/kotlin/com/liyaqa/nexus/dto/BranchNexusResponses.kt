package com.liyaqa.nexus.dto

import java.time.Instant
import java.util.UUID

data class BranchListItemResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val staffCount: Int,
    val trainerCount: Int,
    val activeMemberCount: Int,
    val createdAt: Instant,
)

data class BranchDetailNexusResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val staffCount: Int,
    val trainerCount: Int,
    val activeMemberCount: Int,
    val createdAt: Instant,
)
