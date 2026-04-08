package com.liyaqa.member.dto

import java.time.Instant
import java.util.UUID

data class NoteResponse(
    val noteId: UUID,
    val noteType: String,
    val content: String,
    val followUpAt: Instant?,
    val createdByName: String,
    val createdAt: Instant,
)
