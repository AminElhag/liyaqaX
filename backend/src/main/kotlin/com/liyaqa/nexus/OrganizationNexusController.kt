package com.liyaqa.nexus

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.nexus.dto.CreateOrganizationNexusRequest
import com.liyaqa.nexus.dto.OrgDetailResponse
import com.liyaqa.nexus.dto.OrgListItemResponse
import com.liyaqa.nexus.dto.UpdateOrganizationNexusRequest
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nexus/organizations")
@Tag(name = "Organizations (Nexus)", description = "Platform-scoped organization management")
@Validated
class OrganizationNexusController(
    private val organizationNexusService: OrganizationNexusService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'organization:read')")
    @Operation(summary = "List organizations with optional search")
    fun list(
        @RequestParam(required = false) q: String?,
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<OrgListItemResponse>> {
        authentication.nexusContext()
        return ResponseEntity.ok(organizationNexusService.list(q, pageable))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'organization:read')")
    @Operation(summary = "Get organization detail with club summary")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<OrgDetailResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(organizationNexusService.getById(id))
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'organization:create')")
    @Operation(summary = "Create a new organization")
    fun create(
        @Valid @RequestBody request: CreateOrganizationNexusRequest,
        authentication: Authentication,
    ): ResponseEntity<OrgDetailResponse> {
        authentication.nexusContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationNexusService.create(request))
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'organization:update')")
    @Operation(summary = "Update an organization")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateOrganizationNexusRequest,
        authentication: Authentication,
    ): ResponseEntity<OrgDetailResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(organizationNexusService.update(id, request))
    }
}
