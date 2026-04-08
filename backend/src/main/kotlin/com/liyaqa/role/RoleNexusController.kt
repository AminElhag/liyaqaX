package com.liyaqa.role

import com.liyaqa.nexus.nexusContext
import com.liyaqa.role.dto.CreateRoleRequest
import com.liyaqa.role.dto.RoleDetailResponse
import com.liyaqa.role.dto.RoleListItemResponse
import com.liyaqa.role.dto.UpdateRoleRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nexus/roles")
@Tag(name = "Roles (Nexus)", description = "Platform role management — Nexus only")
@Validated
class RoleNexusController(
    private val roleManagementService: RoleManagementService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'role:read')")
    @Operation(summary = "List all platform-scoped roles")
    fun list(authentication: Authentication): ResponseEntity<List<RoleListItemResponse>> {
        authentication.nexusContext()
        return ResponseEntity.ok(roleManagementService.listPlatformRoles())
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:read')")
    @Operation(summary = "Get platform role detail with permissions")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<RoleDetailResponse> {
        authentication.nexusContext()
        val role = roleManagementService.findRoleOrThrow(id)
        roleManagementService.requirePlatformScope(role)
        return ResponseEntity.ok(roleManagementService.getRoleDetail(id))
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'role:create')")
    @Operation(summary = "Create a new platform role")
    fun create(
        @Valid @RequestBody request: CreateRoleRequest,
        authentication: Authentication,
    ): ResponseEntity<RoleDetailResponse> {
        authentication.nexusContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            roleManagementService.createRole(request, scope = "platform"),
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    @Operation(summary = "Update a platform role name/description")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRoleRequest,
        authentication: Authentication,
    ): ResponseEntity<RoleDetailResponse> {
        authentication.nexusContext()
        val role = roleManagementService.findRoleOrThrow(id)
        roleManagementService.requirePlatformScope(role)
        return ResponseEntity.ok(roleManagementService.updateRole(id, request))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:delete')")
    @Operation(summary = "Delete a platform role")
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        authentication.nexusContext()
        val role = roleManagementService.findRoleOrThrow(id)
        roleManagementService.requirePlatformScope(role)
        roleManagementService.deleteRole(id)
        return ResponseEntity.noContent().build()
    }
}
