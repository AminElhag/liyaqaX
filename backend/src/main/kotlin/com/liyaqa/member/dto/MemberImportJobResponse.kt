package com.liyaqa.member.dto

import java.time.Instant
import java.util.UUID

data class MemberImportJobResponse(
    val jobId: UUID,
    val status: String,
    val fileName: String,
    val totalRows: Int?,
    val importedCount: Int?,
    val skippedCount: Int?,
    val errorCount: Int?,
    val errorDetail: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
)
