package com.liyaqa.arena.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class SelfRegistrationRequest(
    @field:Size(max = 100, message = "Name must be at most 100 characters")
    val nameEn: String? = null,
    @field:Size(max = 100, message = "Name must be at most 100 characters")
    val nameAr: String? = null,
    @field:Email(message = "Invalid email format")
    @field:Size(max = 255, message = "Email must be at most 255 characters")
    val email: String? = null,
    val dateOfBirth: LocalDate? = null,
    @field:Size(max = 20, message = "Gender must be at most 20 characters")
    val gender: String? = null,
    @field:Size(max = 100, message = "Emergency contact name must be at most 100 characters")
    val emergencyContactName: String? = null,
    @field:Size(max = 50, message = "Emergency contact phone must be at most 50 characters")
    val emergencyContactPhone: String? = null,
    val desiredMembershipPlanId: UUID? = null,
)
