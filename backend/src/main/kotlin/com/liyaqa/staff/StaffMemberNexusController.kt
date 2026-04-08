package com.liyaqa.staff

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.security.JwtClaims
import com.liyaqa.staff.dto.CreateStaffMemberRequest
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
@RequestMapping("/api/v1/organizations/{orgId}/clubs/{clubId}/staff")
@Tag(name = "Staff (Nexus)", description = "Staff management endpoints — internal team")
@Validated
class StaffMemberNexusController(
    private val staffMemberService: StaffMemberService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'staff:create')")
    @Operation(summary = "Create a staff member (Nexus)")
    fun create(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @Valid @RequestBody request: CreateStaffMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<StaffMemberResponse> {
        val claims = authentication.claims()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            staffMemberService.create(
                orgPublicId = orgId,
                clubPublicId = clubId,
                callerUserPublicId = claims.requireUserPublicId(),
                callerRolePublicId = claims.requireRoleId(),
                callerScope = claims.scope ?: "platform",
                request = request,
            ),
        )
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List staff for a club (Nexus)")
    fun getAll(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<PageResponse<StaffMemberSummaryResponse>> = ResponseEntity.ok(staffMemberService.getAll(orgId, clubId, pageable))

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "Get a staff member by ID (Nexus)")
    fun getById(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<StaffMemberResponse> = ResponseEntity.ok(staffMemberService.getByPublicId(orgId, clubId, id))

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Update a staff member (Nexus)")
    fun update(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateStaffMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<StaffMemberResponse> {
        val claims = authentication.claims()
        return ResponseEntity.ok(
            staffMemberService.update(
                orgPublicId = orgId,
                clubPublicId = clubId,
                staffPublicId = id,
                callerRolePublicId = claims.requireRoleId(),
                callerScope = claims.scope ?: "platform",
                request = request,
            ),
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:delete')")
    @Operation(summary = "Soft-delete a staff member (Nexus)")
    fun delete(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        staffMemberService.delete(orgId, clubId, id, authentication.claims().requireUserPublicId())
        return ResponseEntity.noContent().build()
    }
}

// ── Auth helpers ──────────────────────────────────────────────────────────────

private fun Authentication.claims(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid user identity in token.")

private fun JwtClaims.requireRoleId(): UUID =
    roleId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "No role found in token.")
