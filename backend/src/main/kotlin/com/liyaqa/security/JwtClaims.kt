package com.liyaqa.security

import java.util.UUID

data class JwtClaims(
    val userPublicId: UUID?,
    val roleId: UUID?,
    val scope: String?,
    val organizationId: UUID?,
    val clubId: UUID?,
)
