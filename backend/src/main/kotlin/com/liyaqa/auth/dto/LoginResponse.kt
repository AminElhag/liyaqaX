package com.liyaqa.auth.dto

import java.util.UUID

data class LoginResponse(
    val accessToken: String,
    val userId: UUID,
    val scope: String,
    val roleId: UUID,
    val roleName: String,
    val organizationId: UUID?,
    val clubId: UUID?,
    val trainerId: UUID? = null,
    val trainerTypes: List<String>? = null,
    val branchIds: List<UUID>? = null,
    val memberId: UUID? = null,
    val branchId: UUID? = null,
)
