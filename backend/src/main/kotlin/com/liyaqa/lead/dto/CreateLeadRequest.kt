package com.liyaqa.lead.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateLeadRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val firstName: String,
    @field:NotBlank
    @field:Size(max = 100)
    val lastName: String,
    @field:Size(max = 100)
    val firstNameAr: String? = null,
    @field:Size(max = 100)
    val lastNameAr: String? = null,
    @field:Size(max = 20)
    val phone: String? = null,
    @field:Email
    @field:Size(max = 255)
    val email: String? = null,
    @field:Pattern(regexp = "^(male|female)$")
    val gender: String? = null,
    val leadSourceId: UUID? = null,
    val assignedStaffId: UUID? = null,
    val branchId: UUID? = null,
    val notes: String? = null,
)
