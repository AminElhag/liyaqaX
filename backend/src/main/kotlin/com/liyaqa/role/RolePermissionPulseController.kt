package com.liyaqa.role

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.role.dto.PermissionResponse
import com.liyaqa.role.dto.UpdateRolePermissionsRequest
import com.liyaqa.security.JwtClaims
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/roles/{roleId}/permissions")
@Tag(name = "Role Permissions (Pulse)", description = "Manage permissions on a club role")
@Validated
class RolePermissionPulseController(
    private val roleManagementService: RoleManagementService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'role:read')")
    @Operation(summary = "List permissions assigned to a club role")
    fun list(
        @PathVariable roleId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<PermissionResponse>> {
        val claims = authentication.pulseClaimsContext()
        val role = roleManagementService.findRoleOrThrow(roleId)
        val clubInternalId = roleManagementService.resolveClubInternalId(claims.requireOrgId(), claims.requireClubIdClaim())
        roleManagementService.requireClubScope(role, clubInternalId)
        return ResponseEntity.ok(roleManagementService.getPermissionsForRole(roleId))
    }

    @PutMapping
    @PreAuthorize("hasPermission(null, 'role:update')")
    @Operation(summary = "Replace the full permission set on a club role")
    fun replace(
        @PathVariable roleId: UUID,
        @Valid @RequestBody request: UpdateRolePermissionsRequest,
        authentication: Authentication,
    ): ResponseEntity<List<PermissionResponse>> {
        val claims = authentication.pulseClaimsContext()
        val role = roleManagementService.findRoleOrThrow(roleId)
        val clubInternalId = roleManagementService.resolveClubInternalId(claims.requireOrgId(), claims.requireClubIdClaim())
        roleManagementService.requireClubScope(role, clubInternalId)
        return ResponseEntity.ok(roleManagementService.replacePermissions(roleId, request))
    }

    @PostMapping("/{permId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    @Operation(summary = "Add a single permission to a club role")
    fun add(
        @PathVariable roleId: UUID,
        @PathVariable permId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<PermissionResponse>> {
        val claims = authentication.pulseClaimsContext()
        val role = roleManagementService.findRoleOrThrow(roleId)
        val clubInternalId = roleManagementService.resolveClubInternalId(claims.requireOrgId(), claims.requireClubIdClaim())
        roleManagementService.requireClubScope(role, clubInternalId)
        return ResponseEntity.ok(roleManagementService.addPermission(roleId, permId))
    }

    @DeleteMapping("/{permId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    @Operation(summary = "Remove a single permission from a club role")
    fun remove(
        @PathVariable roleId: UUID,
        @PathVariable permId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<PermissionResponse>> {
        val claims = authentication.pulseClaimsContext()
        val role = roleManagementService.findRoleOrThrow(roleId)
        val clubInternalId = roleManagementService.resolveClubInternalId(claims.requireOrgId(), claims.requireClubIdClaim())
        roleManagementService.requireClubScope(role, clubInternalId)
        return ResponseEntity.ok(roleManagementService.removePermission(roleId, permId))
    }
}

// ── Auth helpers (scoped to this file to avoid name conflicts) ──────────────

private fun Authentication.pulseClaimsContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireOrgId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubIdClaim(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
