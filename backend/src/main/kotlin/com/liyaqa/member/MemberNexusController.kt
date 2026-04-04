package com.liyaqa.member

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.member.dto.MemberResponse
import com.liyaqa.member.dto.MemberSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/clubs/{clubId}/members")
@Tag(name = "Members (Nexus)", description = "Member viewing endpoints — internal team")
@Validated
class MemberNexusController(
    private val memberService: MemberService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "List members for a club (Nexus)")
    fun getAll(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<PageResponse<MemberSummaryResponse>> = ResponseEntity.ok(memberService.getAll(orgId, clubId, pageable))

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'member:read')")
    @Operation(summary = "Get a member by ID (Nexus)")
    fun getById(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<MemberResponse> = ResponseEntity.ok(memberService.getByPublicId(orgId, clubId, id))
}
