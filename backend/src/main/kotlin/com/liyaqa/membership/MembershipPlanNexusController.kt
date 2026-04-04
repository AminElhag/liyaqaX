package com.liyaqa.membership

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.membership.dto.MembershipPlanResponse
import com.liyaqa.membership.dto.MembershipPlanSummaryResponse
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
@RequestMapping("/api/v1/organizations/{orgId}/clubs/{clubId}/membership-plans")
@Tag(name = "Membership Plans (Nexus)", description = "Membership plan viewing endpoints — internal team")
@Validated
class MembershipPlanNexusController(
    private val membershipPlanService: MembershipPlanService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'membership-plan:read')")
    @Operation(summary = "List membership plans for a club (Nexus)")
    fun getAll(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PageableDefault(size = 20, sort = ["sortOrder"]) pageable: Pageable,
    ): ResponseEntity<PageResponse<MembershipPlanSummaryResponse>> =
        ResponseEntity.ok(membershipPlanService.getAllForNexus(orgId, clubId, pageable))

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'membership-plan:read')")
    @Operation(summary = "Get a membership plan by ID (Nexus)")
    fun getById(
        @PathVariable orgId: UUID,
        @PathVariable clubId: UUID,
        @PathVariable id: UUID,
    ): ResponseEntity<MembershipPlanResponse> = ResponseEntity.ok(membershipPlanService.getByPublicIdForNexus(orgId, clubId, id))
}
