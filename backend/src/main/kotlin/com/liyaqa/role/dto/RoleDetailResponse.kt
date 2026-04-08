package com.liyaqa.role.dto

import java.util.UUID

data class RoleDetailResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val description: String?,
    val scope: String,
    val isSystem: Boolean,
    val permissions: List<PermissionResponse>,
    val staffCount: Long,
)
