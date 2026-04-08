package com.liyaqa.nexus.dto

import java.time.Instant

data class PlatformStatsResponse(
    val totalOrganizations: Long,
    val totalClubs: Long,
    val totalBranches: Long,
    val totalActiveMembers: Long,
    val totalActiveMemberships: Long,
    val estimatedMrrHalalas: Long,
    val estimatedMrrSar: String,
    val newMembersLast30Days: Long,
    val generatedAt: Instant,
)
