package com.liyaqa.member.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class CreateMemberRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val password: String,
    @field:NotBlank
    @field:Size(max = 100)
    val firstNameAr: String,
    @field:NotBlank
    @field:Size(max = 100)
    val firstNameEn: String,
    @field:NotBlank
    @field:Size(max = 100)
    val lastNameAr: String,
    @field:NotBlank
    @field:Size(max = 100)
    val lastNameEn: String,
    @field:NotBlank
    @field:Size(max = 50)
    val phone: String,
    @field:Size(max = 50)
    val nationalId: String? = null,
    val dateOfBirth: LocalDate? = null,
    @field:Pattern(regexp = "male|female|unspecified")
    val gender: String? = null,
    @field:NotNull
    val branchId: UUID,
    val notes: String? = null,
    @field:NotNull
    @field:Valid
    val emergencyContact: EmergencyContactRequest,
)
