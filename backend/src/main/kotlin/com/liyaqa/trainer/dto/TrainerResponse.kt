package com.liyaqa.trainer.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class TrainerResponse(
    val id: UUID,
    val userId: UUID,
    val organizationId: UUID,
    val clubId: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
    val email: String,
    val phone: String?,
    val bioAr: String?,
    val bioEn: String?,
    val trainerTypes: List<String>,
    val branches: List<TrainerBranchResponse>,
    val certifications: List<TrainerCertificationResponse>,
    val specializations: List<TrainerSpecializationResponse>,
    val isActive: Boolean,
    val joinedAt: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
)
