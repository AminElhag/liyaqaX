package com.liyaqa.member

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.dto.ActivateMemberRequest
import com.liyaqa.member.dto.CreateMemberRequest
import com.liyaqa.member.dto.EmergencyContactRequest
import com.liyaqa.member.dto.EmergencyContactResponse
import com.liyaqa.member.dto.MemberResponse
import com.liyaqa.member.dto.MemberSummaryResponse
import com.liyaqa.member.dto.PendingMemberResponse
import com.liyaqa.member.dto.RejectMemberRequest
import com.liyaqa.member.dto.UpdateMemberRequest
import com.liyaqa.member.dto.WaiverStatusResponse
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
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
@RequestMapping("/api/v1/members")
@Tag(name = "Members (Pulse)", description = "Member management endpoints — club operations")
@Validated
class MemberPulseController(
    private val memberService: MemberService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'member:create')")
    @Operation(summary = "Register a new member")
    fun create(
        @Valid @RequestBody request: CreateMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<MemberResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            memberService.create(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                request = request,
            ),
        )
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "List members for the caller's club")
    fun getAll(
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<MemberSummaryResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberService.getAll(claims.requireOrganizationId(), claims.requireClubId(), pageable),
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "Get a member by ID")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<MemberResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberService.getByPublicId(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'member:update')")
    @Operation(summary = "Update a member's information")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<MemberResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberService.update(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = id,
                request = request,
            ),
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'member:delete')")
    @Operation(summary = "Soft-delete a member")
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        memberService.delete(claims.requireOrganizationId(), claims.requireClubId(), id)
        return ResponseEntity.noContent().build()
    }

    // ── Pending activation endpoints ────────────────────────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "List members with pending_activation status")
    fun getPendingMembers(
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<PendingMemberResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberService.getPendingMembers(claims.requireOrganizationId(), claims.requireClubId(), pageable),
        )
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'member:create')")
    @Operation(summary = "Activate a pending member")
    fun activate(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ActivateMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<MemberResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberService.activate(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = id,
                request = request,
            ),
        )
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission(null, 'member:create')")
    @Operation(summary = "Reject a pending member registration")
    fun reject(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RejectMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        memberService.reject(
            orgPublicId = claims.requireOrganizationId(),
            clubPublicId = claims.requireClubId(),
            memberPublicId = id,
            reason = request.reason,
        )
        return ResponseEntity.ok().build()
    }

    // ── Emergency contact endpoints ──────────────────────────────────────────

    @GetMapping("/{id}/emergency-contacts")
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "List emergency contacts for a member")
    fun listEmergencyContacts(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<EmergencyContactResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberService.listEmergencyContacts(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PostMapping("/{id}/emergency-contacts")
    @PreAuthorize("hasPermission(null, 'member:update')")
    @Operation(summary = "Add an emergency contact to a member")
    fun addEmergencyContact(
        @PathVariable id: UUID,
        @Valid @RequestBody request: EmergencyContactRequest,
        authentication: Authentication,
    ): ResponseEntity<EmergencyContactResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            memberService.addEmergencyContact(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = id,
                request = request,
            ),
        )
    }

    @DeleteMapping("/{id}/emergency-contacts/{contactId}")
    @PreAuthorize("hasPermission(null, 'member:update')")
    @Operation(summary = "Remove an emergency contact from a member")
    fun deleteEmergencyContact(
        @PathVariable id: UUID,
        @PathVariable contactId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        memberService.deleteEmergencyContact(
            orgPublicId = claims.requireOrganizationId(),
            clubPublicId = claims.requireClubId(),
            memberPublicId = id,
            contactPublicId = contactId,
        )
        return ResponseEntity.noContent().build()
    }

    // ── Waiver endpoints ─────────────────────────────────────────────────────

    @GetMapping("/{id}/waiver-status")
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "Check if a member has signed the current health waiver")
    fun getWaiverStatus(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<WaiverStatusResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberService.getWaiverStatus(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PostMapping("/{id}/waiver-sign")
    @PreAuthorize("hasPermission(null, 'member:update')")
    @Operation(summary = "Record a waiver signature on behalf of a member")
    fun signWaiver(
        @PathVariable id: UUID,
        authentication: Authentication,
        request: HttpServletRequest,
    ): ResponseEntity<WaiverStatusResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            memberService.signWaiver(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = id,
                ipAddress = request.remoteAddr,
            ),
        )
    }
}

// ── Auth helpers ──────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
