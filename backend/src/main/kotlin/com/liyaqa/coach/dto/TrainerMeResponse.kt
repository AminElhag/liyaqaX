package com.liyaqa.coach.dto

import java.time.LocalDate
import java.util.UUID

data class TrainerMeResponse(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val firstNameAr: String?,
    val lastNameAr: String?,
    val email: String,
    val phone: String?,
    val trainerTypes: List<String>,
    val club: ClubSummary,
    val branches: List<BranchSummary>,
    val certifications: List<CertificationSummary>,
)

data class ClubSummary(
    val id: UUID,
    val name: String,
    val nameAr: String,
)

data class BranchSummary(
    val id: UUID,
    val name: String,
)

data class CertificationSummary(
    val id: UUID,
    val name: String,
    val issuingOrganization: String?,
    val issueDate: LocalDate?,
    val expiryDate: LocalDate?,
)
