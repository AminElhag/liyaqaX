package com.liyaqa.role

import com.liyaqa.role.dto.PermissionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions (Pulse)", description = "Read-only list of all available permissions")
class PermissionPulseController(
    private val roleManagementService: RoleManagementService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'role:read')")
    @Operation(summary = "List all available permission codes")
    fun list(): ResponseEntity<List<PermissionResponse>> = ResponseEntity.ok(roleManagementService.listAllPermissions())
}
