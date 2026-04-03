package com.liyaqa.organization

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.organization.dto.CreateOrganizationRequest
import com.liyaqa.organization.dto.OrganizationResponse
import com.liyaqa.organization.dto.UpdateOrganizationRequest
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
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organization management endpoints")
@Validated
class OrganizationController(
    private val organizationService: OrganizationService,
) {
    @PostMapping
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Create a new organization")
    fun create(
        @Valid @RequestBody request: CreateOrganizationRequest,
    ): ResponseEntity<OrganizationResponse> = ResponseEntity.status(HttpStatus.CREATED).body(organizationService.create(request))

    @GetMapping
    @PreAuthorize(Roles.NEXUS_READ)
    @Operation(summary = "List all organizations")
    fun getAll(
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<PageResponse<OrganizationResponse>> = ResponseEntity.ok(organizationService.getAll(pageable))

    @GetMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_READ)
    @Operation(summary = "Get an organization by ID")
    fun getById(
        @PathVariable id: UUID,
    ): ResponseEntity<OrganizationResponse> = ResponseEntity.ok(organizationService.getByPublicId(id))

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Update an organization")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateOrganizationRequest,
    ): ResponseEntity<OrganizationResponse> = ResponseEntity.ok(organizationService.update(id, request))

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.NEXUS_WRITE)
    @Operation(summary = "Soft-delete an organization")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        organizationService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
