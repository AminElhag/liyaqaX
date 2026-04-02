package com.liyaqa.auth.dto

import java.util.UUID

data class LoginResponse(
    val accessToken: String,
    val userId: UUID,
    val role: String,
    val organizationId: UUID?,
    val clubId: UUID?,
)
