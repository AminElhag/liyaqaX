package com.liyaqa.branch.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class UpdateBranchRequest(
    @field:Size(max = 255)
    val nameAr: String? = null,
    @field:Size(max = 255)
    val nameEn: String? = null,
    val addressAr: String? = null,
    val addressEn: String? = null,
    @field:Size(max = 100)
    val city: String? = null,
    @field:Size(max = 50)
    val phone: String? = null,
    @field:Email
    @field:Size(max = 255)
    val email: String? = null,
    val isActive: Boolean? = null,
)
