package com.liyaqa.role

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.role.dto.CreateRoleRequest
import com.liyaqa.role.dto.RoleDetailResponse
import com.liyaqa.role.dto.RoleListItemResponse
import com.liyaqa.role.dto.UpdateRoleRequest
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles (Pulse)", description = "Club role management — Pulse only")
@Validated
class RolePulseController(
    private val roleManagementService: RoleManagementService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'role:read')")
    @Operation(summary = "List club-scoped roles for the caller's club")
    fun list(authentication: Authentication): ResponseEntity<List<RoleListItemResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            roleManagementService.listClubRoles(claims.requireOrganizationId(), claims.requireClubId()),
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:read')")
    @Operation(summary = "Get club role detail with permissions")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<RoleDetailResponse> {
        val claims = authentication.pulseContext()
        val role = roleManagementService.findRoleOrThrow(id)
        val clubInternalId = roleManagementService.resolveClubInternalId(claims.requireOrganizationId(), claims.requireClubId())
        roleManagementService.requireClubScope(role, clubInternalId)
        return ResponseEntity.ok(roleManagementService.getRoleDetail(id))
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'role:create')")
    @Operation(summary = "Create a new club role")
    fun create(
        @Valid @RequestBody request: CreateRoleRequest,
        authentication: Authentication,
    ): ResponseEntity<RoleDetailResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            roleManagementService.createRole(
                request,
                scope = "club",
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
            ),
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    @Operation(summary = "Update a club role name/description")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRoleRequest,
        authentication: Authentication,
    ): ResponseEntity<RoleDetailResponse> {
        val claims = authentication.pulseContext()
        val role = roleManagementService.findRoleOrThrow(id)
        val clubInternalId = roleManagementService.resolveClubInternalId(claims.requireOrganizationId(), claims.requireClubId())
        roleManagementService.requireClubScope(role, clubInternalId)
        return ResponseEntity.ok(roleManagementService.updateRole(id, request))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:delete')")
    @Operation(summary = "Delete a club role")
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        val role = roleManagementService.findRoleOrThrow(id)
        val clubInternalId = roleManagementService.resolveClubInternalId(claims.requireOrganizationId(), claims.requireClubId())
        roleManagementService.requireClubScope(role, clubInternalId)
        roleManagementService.deleteRole(id)
        return ResponseEntity.noContent().build()
    }
}

// ── Auth helpers ────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
