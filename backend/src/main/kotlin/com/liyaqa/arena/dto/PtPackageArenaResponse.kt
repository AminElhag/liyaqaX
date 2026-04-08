package com.liyaqa.arena.dto

import java.util.UUID

data class PtPackageArenaResponse(
    val id: UUID,
    val name: String,
    val nameAr: String,
    val sessionsUsed: Int,
    val sessionsTotal: Int,
    val status: String,
)
