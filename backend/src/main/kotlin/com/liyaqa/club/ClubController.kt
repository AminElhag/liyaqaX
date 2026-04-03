package com.liyaqa.club

import com.liyaqa.club.dto.ClubResponse
import com.liyaqa.club.dto.CreateClubRequest
import com.liyaqa.club.dto.UpdateClubRequest
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.security.Roles
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
@RequestMapping("/api/v1/organizations/{orgId}/clubs")
@Tag(name = "Clubs", description = "Club management endpoints")
@Validated
class ClubController(
    private val clubService: ClubService,
) {
    @PostMapping
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Create a new club under an organization")
    fun create(
        @PathVariable orgId: UUID,
        @Valid @RequestBody request: CreateClubRequest,
    ): ResponseEntity<ClubResponse> = ResponseEntity.status(HttpStatus.CREATED).body(clubService.create(orgId, request))

    @GetMapping
    @PreAuthorize(Roles.NEXUS_READ)
    @Operation(summary = "List all clubs for an organization")
    fun getAll(
        @PathVariable orgId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<PageResponse<ClubResponse>> = ResponseEntity.ok(clubService.getAll(orgId, pageable))

    @GetMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_READ)
    @Operation(summary = "Get a club by ID")
    fun getById(
        @PathVariable orgId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<ClubResponse> = ResponseEntity.ok(clubService.getByPublicId(orgId, id))

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Update a club")
    fun update(
        @PathVariable orgId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateClubRequest,
    ): ResponseEntity<ClubResponse> = ResponseEntity.ok(clubService.update(orgId, id, request))

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Soft-delete a club")
    fun delete(
        @PathVariable orgId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        clubService.delete(orgId, id)
        return ResponseEntity.noContent().build()
    }
}
