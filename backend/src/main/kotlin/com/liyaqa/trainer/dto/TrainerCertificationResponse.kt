package com.liyaqa.trainer.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class TrainerCertificationResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val issuingBody: String?,
    val issuedAt: LocalDate?,
    val expiresAt: LocalDate?,
    val status: String,
    val rejectionReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
