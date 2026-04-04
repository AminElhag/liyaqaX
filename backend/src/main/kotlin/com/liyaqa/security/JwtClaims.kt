package com.liyaqa.security

import java.util.UUID

data class JwtClaims(
    val userPublicId: UUID?,
    val roleId: UUID?,
    val scope: String?,
    val organizationId: UUID?,
    val clubId: UUID?,
    val trainerId: UUID? = null,
    val trainerTypes: List<String> = emptyList(),
    val branchIds: List<UUID> = emptyList(),
)
