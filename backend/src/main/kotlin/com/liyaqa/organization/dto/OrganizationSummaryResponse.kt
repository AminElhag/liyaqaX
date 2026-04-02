package com.liyaqa.organization.dto

import java.util.UUID

data class OrganizationSummaryResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val isActive: Boolean,
)
