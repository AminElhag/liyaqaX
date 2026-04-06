package com.liyaqa.arena

import com.liyaqa.arena.dto.InvoiceArenaDetailResponse
import com.liyaqa.arena.dto.InvoiceArenaResponse
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.portal.ClubPortalSettingsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/arena/invoices")
@Tag(name = "Arena Invoices", description = "Member invoice list and detail")
@Validated
class InvoiceArenaController(
    private val invoiceRepository: InvoiceRepository,
    private val memberRepository: MemberRepository,
    private val portalSettingsService: ClubPortalSettingsService,
) {
    @GetMapping
    @Operation(summary = "List member's invoices")
    fun listInvoices(
        @PageableDefault(size = 20, sort = ["issuedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<InvoiceArenaResponse>> {
        val member = resolveMember(authentication)
        portalSettingsService.requireFeatureEnabled(member.clubId, "invoice")

        val page =
            invoiceRepository.findAllByMemberId(member.id, pageable)
                .map { invoice ->
                    InvoiceArenaResponse(
                        id = invoice.publicId,
                        invoiceNumber = invoice.invoiceNumber,
                        issuedAt = invoice.issuedAt,
                        subtotalHalalas = invoice.subtotalHalalas,
                        vatAmountHalalas = invoice.vatAmountHalalas,
                        totalHalalas = invoice.totalHalalas,
                        zatcaStatus = invoice.zatcaStatus,
                    )
                }

        return ResponseEntity.ok(page.toPageResponse())
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice detail with ZATCA QR code")
    fun getInvoiceDetail(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<InvoiceArenaDetailResponse> {
        val member = resolveMember(authentication)
        portalSettingsService.requireFeatureEnabled(member.clubId, "invoice")

        val invoice =
            invoiceRepository.findByPublicIdAndOrganizationId(id, member.organizationId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Invoice not found.") }

        // Rule 10 — tenant isolation: ensure invoice belongs to this member
        if (invoice.memberId != member.id) {
            throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Invoice not found.")
        }

        return ResponseEntity.ok(
            InvoiceArenaDetailResponse(
                id = invoice.publicId,
                invoiceNumber = invoice.invoiceNumber,
                issuedAt = invoice.issuedAt,
                subtotalHalalas = invoice.subtotalHalalas,
                vatAmountHalalas = invoice.vatAmountHalalas,
                totalHalalas = invoice.totalHalalas,
                zatcaStatus = invoice.zatcaStatus,
                zatcaQrCode = invoice.zatcaQrCode,
            ),
        )
    }

    private fun resolveMember(authentication: Authentication): Member {
        val claims = authentication.arenaContext()
        val memberPublicId = claims.requireMemberId()
        return memberRepository.findAll().firstOrNull { it.publicId == memberPublicId && it.deletedAt == null }
            ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.")
    }
}
