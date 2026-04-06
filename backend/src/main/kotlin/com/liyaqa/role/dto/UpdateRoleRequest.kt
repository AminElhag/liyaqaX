package com.liyaqa.role.dto

import jakarta.validation.constraints.Size

data class UpdateRoleRequest(
    @field:Size(max = 100, message = "Role name must be at most 100 characters")
    val name: String? = null,
    @field:Size(max = 500, message = "Description must be at most 500 characters")
    val description: String? = null,
)
