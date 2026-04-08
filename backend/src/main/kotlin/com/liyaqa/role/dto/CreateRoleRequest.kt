package com.liyaqa.role.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateRoleRequest(
    @field:NotBlank(message = "Role name is required")
    @field:Size(max = 100, message = "Role name must be at most 100 characters")
    val name: String,
    @field:Size(max = 500, message = "Description must be at most 500 characters")
    val description: String? = null,
)
