package com.liyaqa.trainer.dto

import java.util.UUID

data class TrainerSummaryResponse(
    val id: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
    val email: String,
    val trainerTypes: List<String>,
    val isActive: Boolean,
)
