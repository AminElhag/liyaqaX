package com.liyaqa.nexus

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.nexus.dto.MemberDetailNexusResponse
import com.liyaqa.nexus.dto.MemberSearchItemResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nexus/members")
@Tag(name = "Members (Nexus)", description = "Platform-scoped cross-org member search")
@Validated
class MemberSearchNexusController(
    private val memberNexusService: MemberNexusService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "Search members across all organizations")
    fun search(
        @RequestParam q: String,
        @PageableDefault(size = 50) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<MemberSearchItemResponse>> {
        authentication.nexusContext()
        return ResponseEntity.ok(memberNexusService.search(q, pageable))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "Get member detail (read-only)")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<MemberDetailNexusResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(memberNexusService.getById(id))
    }
}
