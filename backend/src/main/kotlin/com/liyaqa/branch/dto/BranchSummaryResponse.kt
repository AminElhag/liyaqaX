package com.liyaqa.branch.dto

import java.util.UUID

data class BranchSummaryResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val city: String?,
    val isActive: Boolean,
)
