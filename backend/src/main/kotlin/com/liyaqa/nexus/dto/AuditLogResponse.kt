package com.liyaqa.nexus.dto

data class AuditLogPageResponse(
    val content: List<Any>,
    val totalElements: Long,
    val meta: AuditLogMeta?,
)

data class AuditLogMeta(
    val note: String,
)
