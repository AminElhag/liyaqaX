package com.liyaqa.trainer.dto

import jakarta.validation.constraints.Size

data class UpdateTrainerRequest(
    @field:Size(max = 100)
    val firstNameAr: String? = null,
    @field:Size(max = 100)
    val firstNameEn: String? = null,
    @field:Size(max = 100)
    val lastNameAr: String? = null,
    @field:Size(max = 100)
    val lastNameEn: String? = null,
    @field:Size(max = 50)
    val phone: String? = null,
    @field:Size(max = 50)
    val nationalId: String? = null,
    val bioAr: String? = null,
    val bioEn: String? = null,
    val isActive: Boolean? = null,
)
