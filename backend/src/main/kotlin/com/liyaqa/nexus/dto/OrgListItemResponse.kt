package com.liyaqa.nexus.dto

import java.time.Instant
import java.util.UUID

data class OrgListItemResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val vatNumber: String?,
    val clubCount: Int,
    val activeMemberCount: Int,
    val createdAt: Instant,
)
