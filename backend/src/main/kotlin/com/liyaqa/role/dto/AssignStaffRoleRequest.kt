package com.liyaqa.role.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class AssignStaffRoleRequest(
    @field:NotNull(message = "Role ID is required")
    val roleId: UUID,
)
