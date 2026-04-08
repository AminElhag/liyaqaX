package com.liyaqa.role

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.role.dto.AssignStaffRoleRequest
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/staff/{staffId}/role")
@Tag(name = "Staff Role Assignment (Pulse)", description = "Assign roles to staff members")
@Validated
class StaffRoleAssignmentPulseController(
    private val roleManagementService: RoleManagementService,
) {
    @PatchMapping
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Reassign a staff member to a different role")
    fun assignRole(
        @PathVariable staffId: UUID,
        @Valid @RequestBody request: AssignStaffRoleRequest,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.staffRolePulseContext()
        roleManagementService.assignStaffRole(
            staffPublicId = staffId,
            newRolePublicId = request.roleId,
            callerOrgPublicId = claims.requireStaffOrgId(),
            callerClubPublicId = claims.requireStaffClubId(),
        )
        return ResponseEntity.noContent().build()
    }
}

// ── Auth helpers (scoped to this file) ──────────────────────────────────────

private fun Authentication.staffRolePulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireStaffOrgId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireStaffClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
