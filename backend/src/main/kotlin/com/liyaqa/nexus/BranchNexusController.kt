package com.liyaqa.nexus

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.nexus.dto.BranchDetailNexusResponse
import com.liyaqa.nexus.dto.BranchListItemResponse
import com.liyaqa.nexus.dto.CreateBranchNexusRequest
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
@RequestMapping("/api/v1/nexus/organizations/{orgId}/clubs/{clubId}/branches")
@Tag(name = "Branches (Nexus)", description = "Platform-scoped branch management")
@Validated
class BranchNexusController(
    private val branchNexusService: BranchNexusService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'branch:read')")
    @Operation(summary = "List branches for a club")
    fun list(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<BranchListItemResponse>> {
        authentication.nexusContext()
        return ResponseEntity.ok(branchNexusService.list(orgId, clubId, pageable))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'branch:read')")
    @Operation(summary = "Get branch detail with counts")
    fun getById(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<BranchDetailNexusResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(branchNexusService.getById(orgId, clubId, id))
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'branch:create')")
    @Operation(summary = "Create a branch under a club")
    fun create(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @Valid @RequestBody request: CreateBranchNexusRequest,
        authentication: Authentication,
    ): ResponseEntity<BranchDetailNexusResponse> {
        authentication.nexusContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(branchNexusService.create(orgId, clubId, request))
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'branch:update')")
    @Operation(summary = "Update a branch")
    fun update(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateBranchNexusRequest,
        authentication: Authentication,
    ): ResponseEntity<BranchDetailNexusResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(branchNexusService.update(orgId, clubId, id, request))
    }
}
