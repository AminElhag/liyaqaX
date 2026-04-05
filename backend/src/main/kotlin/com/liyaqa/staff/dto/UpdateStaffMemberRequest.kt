package com.liyaqa.staff.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateStaffMemberRequest(
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
    val roleId: UUID? = null,
    @field:Pattern(regexp = "full-time|part-time|contractor")
    val employmentType: String? = null,
    val isActive: Boolean? = null,
)
