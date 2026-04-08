package com.liyaqa.role.dto

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class UpdateRolePermissionsRequest(
    @field:NotEmpty(message = "A role must have at least one permission")
    val permissionIds: List<UUID>,
)
