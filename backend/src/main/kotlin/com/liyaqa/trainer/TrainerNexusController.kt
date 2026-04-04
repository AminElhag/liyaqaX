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
@RequestMapping("/api/v1/organizations/{orgId}/clubs/{clubId}/trainers")
@Tag(name = "Trainers (Nexus)", description = "Trainer management endpoints — internal team")
@Validated
class TrainerNexusController(
    private val trainerService: TrainerService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'staff:create')")
    @Operation(summary = "Create a trainer (Nexus)")
    fun create(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @Valid @RequestBody request: CreateTrainerRequest,
        authentication: Authentication,
    ): ResponseEntity<TrainerResponse> {
        val claims = authentication.claims()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            trainerService.create(
                orgPublicId = orgId,
                clubPublicId = clubId,
                callerUserPublicId = claims.requireUserPublicId(),
                request = request,
            ),
        )
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List trainers for a club (Nexus)")
    fun getAll(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<PageResponse<TrainerSummaryResponse>> = ResponseEntity.ok(trainerService.getAll(orgId, clubId, pageable))

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "Get a trainer by ID (Nexus)")
    fun getById(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<TrainerResponse> = ResponseEntity.ok(trainerService.getByPublicId(orgId, clubId, id))

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Update a trainer (Nexus)")
    fun update(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTrainerRequest,
    ): ResponseEntity<TrainerResponse> = ResponseEntity.ok(trainerService.update(orgId, clubId, id, request))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:delete')")
    @Operation(summary = "Soft-delete a trainer (Nexus)")
    fun delete(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        trainerService.delete(orgId, clubId, id, authentication.claims().requireUserPublicId())
        return ResponseEntity.noContent().build()
    }

    // ── Branch assignment ────────────────────────────────────────────────────

    @PostMapping("/{id}/branches/{branchId}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Assign a trainer to a branch (Nexus)")
    fun assignBranch(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        @PathVariable branchId: UUID,
    ): ResponseEntity<Void> {
        trainerService.assignBranch(orgId, clubId, id, branchId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/branches/{branchId}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Remove a branch assignment from a trainer (Nexus)")
    fun removeBranch(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        @PathVariable branchId: UUID,
    ): ResponseEntity<Void> {
        trainerService.removeBranch(orgId, clubId, id, branchId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/branches")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List branch assignments for a trainer (Nexus)")
    fun listBranches(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<List<TrainerBranchResponse>> = ResponseEntity.ok(trainerService.listBranches(orgId, clubId, id))

    // ── Certifications ───────────────────────────────────────────────────────

    @GetMapping("/{id}/certifications")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    @Operation(summary = "List certifications for a trainer (Nexus)")
    fun listCertifications(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<List<TrainerCertificationResponse>> = ResponseEntity.ok(trainerService.listCertifications(orgId, clubId, id))

    @PatchMapping("/{id}/certifications/{certId}/approve")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Approve a trainer certification (Nexus)")
    fun approveCertification(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        @PathVariable certId: UUID,
    ): ResponseEntity<TrainerCertificationResponse> = ResponseEntity.ok(trainerService.approveCertification(orgId, clubId, id, certId))

    @PatchMapping("/{id}/certifications/{certId}/reject")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    @Operation(summary = "Reject a trainer certification (Nexus)")
    fun rejectCertification(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        @PathVariable certId: UUID,
        @RequestBody body: Map<String, String?>,
    ): ResponseEntity<TrainerCertificationResponse> =
        ResponseEntity.ok(trainerService.rejectCertification(orgId, clubId, id, certId, body["reason"]))
}

// ── Auth helpers ─────────────────────────────────────────────────────────────

private fun Authentication.claims(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid user identity in token.")
