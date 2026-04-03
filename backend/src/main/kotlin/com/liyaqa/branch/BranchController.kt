package com.liyaqa.branch

import com.liyaqa.branch.dto.BranchResponse
import com.liyaqa.branch.dto.BranchSummaryResponse
import com.liyaqa.branch.dto.CreateBranchRequest
import com.liyaqa.branch.dto.UpdateBranchRequest
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
@RequestMapping("/api/v1/organizations/{orgId}/clubs/{clubId}/branches")
@Tag(name = "Branches", description = "Branch management endpoints")
@Validated
class BranchController(
    private val branchService: BranchService,
) {
    @PostMapping
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Create a new branch under a club")
    fun create(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @Valid @RequestBody request: CreateBranchRequest,
    ): ResponseEntity<BranchResponse> = ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(orgId, clubId, request))

    @GetMapping
    @PreAuthorize(Roles.NEXUS_READ)
    @Operation(summary = "List all branches for a club")
    fun getAll(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<PageResponse<BranchSummaryResponse>> = ResponseEntity.ok(branchService.getAll(orgId, clubId, pageable))

    @GetMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_READ)
    @Operation(summary = "Get a branch by ID")
    fun getById(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<BranchResponse> = ResponseEntity.ok(branchService.getByPublicId(orgId, clubId, id))

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Update a branch")
    fun update(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBranchRequest,
    ): ResponseEntity<BranchResponse> = ResponseEntity.ok(branchService.update(orgId, clubId, id, request))

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Soft-delete a branch")
    fun delete(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        branchService.delete(orgId, clubId, id)
        return ResponseEntity.noContent().build()
    }
}
