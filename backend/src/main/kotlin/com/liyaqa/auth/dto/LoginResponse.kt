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
)
