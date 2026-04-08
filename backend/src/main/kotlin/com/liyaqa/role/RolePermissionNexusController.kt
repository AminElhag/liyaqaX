package com.liyaqa.role

import com.liyaqa.nexus.nexusContext
import com.liyaqa.role.dto.PermissionResponse
import com.liyaqa.role.dto.UpdateRolePermissionsRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nexus/roles/{roleId}/permissions")
@Tag(name = "Role Permissions (Nexus)", description = "Manage permissions on a platform role")
@Validated
class RolePermissionNexusController(
    private val roleManagementService: RoleManagementService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'role:read')")
    @Operation(summary = "List permissions assigned to a platform role")
    fun list(
        @PathVariable roleId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<PermissionResponse>> {
        authentication.nexusContext()
        val role = roleManagementService.findRoleOrThrow(roleId)
        roleManagementService.requirePlatformScope(role)
        return ResponseEntity.ok(roleManagementService.getPermissionsForRole(roleId))
    }

    @PutMapping
    @PreAuthorize("hasPermission(null, 'role:update')")
    @Operation(summary = "Replace the full permission set on a platform role")
    fun replace(
        @PathVariable roleId: UUID,
        @Valid @RequestBody request: UpdateRolePermissionsRequest,
        authentication: Authentication,
    ): ResponseEntity<List<PermissionResponse>> {
        authentication.nexusContext()
        val role = roleManagementService.findRoleOrThrow(roleId)
        roleManagementService.requirePlatformScope(role)
        return ResponseEntity.ok(roleManagementService.replacePermissions(roleId, request))
    }

    @PostMapping("/{permId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    @Operation(summary = "Add a single permission to a platform role")
    fun add(
        @PathVariable roleId: UUID,
        @PathVariable permId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<PermissionResponse>> {
        authentication.nexusContext()
        val role = roleManagementService.findRoleOrThrow(roleId)
        roleManagementService.requirePlatformScope(role)
        return ResponseEntity.ok(roleManagementService.addPermission(roleId, permId))
    }

    @DeleteMapping("/{permId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    @Operation(summary = "Remove a single permission from a platform role")
    fun remove(
        @PathVariable roleId: UUID,
        @PathVariable permId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<PermissionResponse>> {
        authentication.nexusContext()
        val role = roleManagementService.findRoleOrThrow(roleId)
        roleManagementService.requirePlatformScope(role)
        return ResponseEntity.ok(roleManagementService.removePermission(roleId, permId))
    }
}
