package com.liyaqa.role.dto

import java.util.UUID

data class RoleListItemResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val description: String?,
    val scope: String,
    val isSystem: Boolean,
    val permissionCount: Long,
    val staffCount: Long,
)
