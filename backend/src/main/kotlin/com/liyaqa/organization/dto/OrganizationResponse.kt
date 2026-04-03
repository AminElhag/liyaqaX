package com.liyaqa.organization.dto

import java.time.Instant
import java.util.UUID

data class OrganizationResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val email: String,
    val phone: String?,
    val country: String,
    val timezone: String,
    val locale: String,
    val isActive: Boolean,
    val vatNumber: String?,
    val crNumber: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
