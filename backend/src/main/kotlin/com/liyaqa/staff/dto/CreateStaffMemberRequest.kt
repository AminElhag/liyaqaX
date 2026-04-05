package com.liyaqa.staff.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class CreateStaffMemberRequest(
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
    @field:Size(max = 50)
    val phone: String? = null,
    @field:Size(max = 50)
    val nationalId: String? = null,
    @field:NotNull
    val roleId: UUID,
    @field:NotEmpty
    val branchIds: List<UUID>,
    @field:NotBlank
    @field:Pattern(regexp = "full-time|part-time|contractor")
    val employmentType: String = "full-time",
    @field:NotNull
    val joinedAt: LocalDate,
)
