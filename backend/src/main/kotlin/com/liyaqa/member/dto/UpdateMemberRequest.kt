package com.liyaqa.member.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpdateMemberRequest(
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
    val dateOfBirth: LocalDate? = null,
    @field:Pattern(regexp = "male|female|unspecified")
    val gender: String? = null,
    val notes: String? = null,
)
