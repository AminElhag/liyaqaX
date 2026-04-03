package com.liyaqa.branch.dto

import java.time.Instant
import java.util.UUID

data class BranchResponse(
    val id: UUID,
    val organizationId: UUID,
    val clubId: UUID,
    val nameAr: String,
    val nameEn: String,
    val addressAr: String?,
    val addressEn: String?,
    val city: String?,
    val phone: String?,
    val email: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
