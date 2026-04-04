package com.liyaqa.trainer

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.security.JwtClaims
import com.liyaqa.trainer.dto.CreateTrainerRequest
import com.liyaqa.trainer.dto.TrainerBranchResponse
import com.liyaqa.trainer.dto.TrainerCertificationResponse
import com.liyaqa.trainer.dto.TrainerResponse
import com.liyaqa.trainer.dto.TrainerSummaryResponse
import com.liyaqa.trainer.dto.UpdateTrainerRequest
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
@RequestMapping("/api/v1/trainers")
@Tag(name = "Trainers (Pulse)", description = "Trainer management endpoints — club operations")
@Validated
class TrainerPulseController(
    private val trainerService: TrainerService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'staff:create')")
    @Operation(summary = "Create a trainer")
    fun create(
        @Valid @RequestBody request: CreateTrainerRequest,
        authentication: Authentication,
    ): ResponseEntity<TrainerResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            trainerService.create(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                callerUserPublicId = claims.requireUserPublicId(),
                request = request,
            ),
        )
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List trainers for the caller's club")
    fun getAll(
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<TrainerSummaryResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            trainerService.getAll(claims.requireOrganizationId(), claims.requireClubId(), pageable),
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "Get a trainer by ID")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<TrainerResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            trainerService.getByPublicId(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Update a trainer")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTrainerRequest,
        authentication: Authentication,
    ): ResponseEntity<TrainerResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            trainerService.update(claims.requireOrganizationId(), claims.requireClubId(), id, request),
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:delete')")
    @Operation(summary = "Soft-delete a trainer")
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        trainerService.delete(
            claims.requireOrganizationId(),
            claims.requireClubId(),
            id,
            claims.requireUserPublicId(),
        )
        return ResponseEntity.noContent().build()
    }

    // ── Branch assignment ────────────────────────────────────────────────────

    @PostMapping("/{id}/branches/{branchId}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Assign a trainer to a branch")
    fun assignBranch(
        @PathVariable id: UUID,
        @PathVariable branchId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        trainerService.assignBranch(claims.requireOrganizationId(), claims.requireClubId(), id, branchId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/branches/{branchId}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Remove a branch assignment from a trainer")
    fun removeBranch(
        @PathVariable id: UUID,
        @PathVariable branchId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        trainerService.removeBranch(claims.requireOrganizationId(), claims.requireClubId(), id, branchId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/branches")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List branch assignments for a trainer")
    fun listBranches(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<TrainerBranchResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            trainerService.listBranches(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    // ── Certifications ───────────────────────────────────────────────────────

    @GetMapping("/{id}/certifications")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List certifications for a trainer")
    fun listCertifications(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<TrainerCertificationResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            trainerService.listCertifications(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PatchMapping("/{id}/certifications/{certId}/approve")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Approve a trainer certification")
    fun approveCertification(
        @PathVariable id: UUID,
        @PathVariable certId: UUID,
        authentication: Authentication,
    ): ResponseEntity<TrainerCertificationResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            trainerService.approveCertification(claims.requireOrganizationId(), claims.requireClubId(), id, certId),
        )
    }

    @PatchMapping("/{id}/certifications/{certId}/reject")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Reject a trainer certification")
    fun rejectCertification(
        @PathVariable id: UUID,
        @PathVariable certId: UUID,
        @RequestBody body: Map<String, String?>,
        authentication: Authentication,
    ): ResponseEntity<TrainerCertificationResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            trainerService.rejectCertification(
                claims.requireOrganizationId(),
                claims.requireClubId(),
                id,
                certId,
                body["reason"],
            ),
        )
    }
}

// ── Auth helpers ─────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid user identity in token.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
