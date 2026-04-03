package com.liyaqa.staff

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.security.JwtClaims
import com.liyaqa.staff.dto.CreateStaffMemberRequest
import com.liyaqa.staff.dto.StaffBranchResponse
import com.liyaqa.staff.dto.StaffMemberResponse
import com.liyaqa.staff.dto.StaffMemberSummaryResponse
import com.liyaqa.staff.dto.UpdateStaffMemberRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
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
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff (Pulse)", description = "Staff management endpoints — club operations")
@Validated
class StaffMemberPulseController(
    private val staffMemberService: StaffMemberService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'staff:create')")
    @Operation(summary = "Create a staff member")
    fun create(
        @Valid @RequestBody request: CreateStaffMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<StaffMemberResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            staffMemberService.create(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                callerUserPublicId = claims.requireUserPublicId(),
                callerRolePublicId = claims.requireRoleId(),
                callerScope = claims.scope ?: "club",
                request = request,
            ),
        )
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List staff for the caller's club")
    fun getAll(
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<StaffMemberSummaryResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            staffMemberService.getAll(claims.requireOrganizationId(), claims.requireClubId(), pageable),
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "Get a staff member by ID")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<StaffMemberResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            staffMemberService.getByPublicId(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Update a staff member")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateStaffMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<StaffMemberResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            staffMemberService.update(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                staffPublicId = id,
                callerRolePublicId = claims.requireRoleId(),
                callerScope = claims.scope ?: "club",
                request = request,
            ),
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:delete')")
    @Operation(summary = "Soft-delete a staff member")
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        staffMemberService.delete(claims.requireOrganizationId(), claims.requireClubId(), id, claims.requireUserPublicId())
        return ResponseEntity.noContent().build()
    }

    // ── Branch assignment endpoints ───────────────────────────────────────────

    @PostMapping("/{id}/branches/{branchId}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Assign a staff member to a branch")
    fun assignBranch(
        @PathVariable id: UUID,
        @PathVariable branchId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        staffMemberService.assignBranch(claims.requireOrganizationId(), claims.requireClubId(), id, branchId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/branches/{branchId}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Remove a branch assignment from a staff member")
    fun removeBranch(
        @PathVariable id: UUID,
        @PathVariable branchId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        staffMemberService.removeBranch(claims.requireOrganizationId(), claims.requireClubId(), id, branchId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/branches")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List branch assignments for a staff member")
    fun listBranches(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<StaffBranchResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(staffMemberService.listBranches(claims.requireOrganizationId(), claims.requireClubId(), id))
    }
}

// ── Auth helpers ──────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid user identity in token.")

private fun JwtClaims.requireRoleId(): UUID =
    roleId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "No role found in token.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
