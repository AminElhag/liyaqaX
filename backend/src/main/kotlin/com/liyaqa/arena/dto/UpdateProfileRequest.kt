package com.liyaqa.arena.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(max = 100, message = "First name must be at most 100 characters")
    val firstNameAr: String? = null,
    @field:Size(max = 100, message = "Last name must be at most 100 characters")
    val lastNameAr: String? = null,
    @field:Pattern(regexp = "^(ar|en)$", message = "Language must be 'ar' or 'en'")
    val preferredLanguage: String? = null,
)
