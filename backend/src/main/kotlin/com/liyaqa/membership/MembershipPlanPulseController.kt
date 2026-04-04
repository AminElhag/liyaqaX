package com.liyaqa.membership

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.membership.dto.CreateMembershipPlanRequest
import com.liyaqa.membership.dto.MembershipPlanResponse
import com.liyaqa.membership.dto.MembershipPlanSummaryResponse
import com.liyaqa.membership.dto.UpdateMembershipPlanRequest
import com.liyaqa.security.JwtClaims
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
@RequestMapping("/api/v1/membership-plans")
@Tag(name = "Membership Plans (Pulse)", description = "Membership plan management endpoints — club operations")
@Validated
class MembershipPlanPulseController(
    private val membershipPlanService: MembershipPlanService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'membership-plan:create')")
    @Operation(summary = "Create a new membership plan")
    fun create(
        @Valid @RequestBody request: CreateMembershipPlanRequest,
        authentication: Authentication,
    ): ResponseEntity<MembershipPlanResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            membershipPlanService.create(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                request = request,
            ),
        )
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'membership-plan:read')")
    @Operation(summary = "List membership plans for the caller's club")
    fun getAll(
        @PageableDefault(size = 20, sort = ["sortOrder"]) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<MembershipPlanSummaryResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            membershipPlanService.getAll(claims.requireOrganizationId(), claims.requireClubId(), pageable),
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'membership-plan:read')")
    @Operation(summary = "Get a membership plan by ID")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<MembershipPlanResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            membershipPlanService.getByPublicId(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'membership-plan:update')")
    @Operation(summary = "Update a membership plan")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateMembershipPlanRequest,
        authentication: Authentication,
    ): ResponseEntity<MembershipPlanResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            membershipPlanService.update(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                planPublicId = id,
                request = request,
            ),
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'membership-plan:delete')")
    @Operation(summary = "Soft-delete a membership plan")
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        membershipPlanService.delete(claims.requireOrganizationId(), claims.requireClubId(), id)
        return ResponseEntity.noContent().build()
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
