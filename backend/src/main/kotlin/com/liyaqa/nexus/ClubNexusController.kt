package com.liyaqa.nexus

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.nexus.dto.ClubDetailNexusResponse
import com.liyaqa.nexus.dto.ClubListItemResponse
import com.liyaqa.nexus.dto.CreateClubNexusRequest
import com.liyaqa.nexus.dto.UpdateClubNexusRequest
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nexus/organizations/{orgId}/clubs")
@Tag(name = "Clubs (Nexus)", description = "Platform-scoped club management")
@Validated
class ClubNexusController(
    private val clubNexusService: ClubNexusService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'club:read')")
    @Operation(summary = "List clubs for an organization")
    fun list(
        @PathVariable orgId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<ClubListItemResponse>> {
        authentication.nexusContext()
        return ResponseEntity.ok(clubNexusService.list(orgId, pageable))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'club:read')")
    @Operation(summary = "Get club detail with metrics and MRR estimate")
    fun getById(
        @PathVariable orgId: UUID,
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<ClubDetailNexusResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(clubNexusService.getById(orgId, id))
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'club:create')")
    @Operation(summary = "Create a club under an organization")
    fun create(
        @PathVariable orgId: UUID,
        @Valid @RequestBody request: CreateClubNexusRequest,
        authentication: Authentication,
    ): ResponseEntity<ClubDetailNexusResponse> {
        authentication.nexusContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(clubNexusService.create(orgId, request))
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'club:update')")
    @Operation(summary = "Update a club")
    fun update(
        @PathVariable orgId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateClubNexusRequest,
        authentication: Authentication,
    ): ResponseEntity<ClubDetailNexusResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(clubNexusService.update(orgId, id, request))
    }
}
