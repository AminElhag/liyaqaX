package com.liyaqa.membership

import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.PaginationMeta
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.invoice.InvoiceService
import com.liyaqa.invoice.dto.InvoiceResponse
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.dto.AssignMembershipRequest
import com.liyaqa.membership.dto.ExpiringMembershipResponse
import com.liyaqa.membership.dto.FreezeMembershipRequest
import com.liyaqa.membership.dto.MembershipResponse
import com.liyaqa.membership.dto.MembershipSummaryResponse
import com.liyaqa.membership.dto.RenewMembershipRequest
import com.liyaqa.membership.dto.TerminateMembershipRequest
import com.liyaqa.membership.dto.UnfreezeMembershipRequest
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.payment.PaymentService
import com.liyaqa.payment.dto.PaymentResponse
import com.liyaqa.security.JwtClaims
import com.liyaqa.user.UserRepository
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Memberships (Pulse)", description = "Membership assignment and history endpoints — club operations")
@Validated
class MembershipPulseController(
    private val membershipService: MembershipService,
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val invoiceService: InvoiceService,
    private val invoiceRepository: InvoiceRepository,
    private val memberRepository: MemberRepository,
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
) {
    // ── Membership endpoints ────────────────────────────────────────────────

    @PostMapping("/members/{memberId}/memberships")
    @PreAuthorize("hasPermission(null, 'membership:create')")
    @Operation(summary = "Assign a membership plan to a member and collect payment")
    fun assignMembership(
        @PathVariable memberId: UUID,
        @Valid @RequestBody request: AssignMembershipRequest,
        authentication: Authentication,
    ): ResponseEntity<MembershipResponse> {
        val claims = authentication.pulseContext()
        val response =
            membershipService.assignPlan(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberId,
                request = request,
                callerUserPublicId = claims.requireUserPublicId(),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/members/{memberId}/memberships/active")
    @PreAuthorize("hasPermission(null, 'membership:read')")
    @Operation(summary = "Get the active membership for a member")
    fun getActiveMembership(
        @PathVariable memberId: UUID,
        authentication: Authentication,
    ): ResponseEntity<MembershipResponse> {
        val claims = authentication.pulseContext()
        val response =
            membershipService.getActiveMembership(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberId,
            )
        return if (response != null) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.noContent().build()
        }
    }

    @GetMapping("/members/{memberId}/memberships")
    @PreAuthorize("hasPermission(null, 'membership:read')")
    @Operation(summary = "Get membership history for a member")
    fun getMembershipHistory(
        @PathVariable memberId: UUID,
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<MembershipSummaryResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            membershipService.getMembershipHistory(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberId,
                pageable = pageable,
            ),
        )
    }

    // ── Membership lifecycle endpoints ──────────────────────────────────────

    @PostMapping("/members/{memberId}/memberships/{membershipId}/renew")
    @PreAuthorize("hasPermission(null, 'membership:create')")
    @Operation(summary = "Renew a membership by creating a new membership and collecting payment")
    fun renewMembership(
        @PathVariable memberId: UUID,
        @PathVariable membershipId: UUID,
        @Valid @RequestBody request: RenewMembershipRequest,
        authentication: Authentication,
    ): ResponseEntity<MembershipResponse> {
        val claims = authentication.pulseContext()
        val response =
            membershipService.renew(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberId,
                membershipPublicId = membershipId,
                request = request,
                callerUserPublicId = claims.requireUserPublicId(),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/members/{memberId}/memberships/{membershipId}/freeze")
    @PreAuthorize("hasPermission(null, 'membership:freeze')")
    @Operation(summary = "Freeze a membership")
    fun freezeMembership(
        @PathVariable memberId: UUID,
        @PathVariable membershipId: UUID,
        @Valid @RequestBody request: FreezeMembershipRequest,
        authentication: Authentication,
    ): ResponseEntity<MembershipResponse> {
        val claims = authentication.pulseContext()
        val response =
            membershipService.freeze(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberId,
                membershipPublicId = membershipId,
                request = request,
                callerUserPublicId = claims.requireUserPublicId(),
            )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/members/{memberId}/memberships/{membershipId}/unfreeze")
    @PreAuthorize("hasPermission(null, 'membership:unfreeze')")
    @Operation(summary = "Unfreeze a frozen membership")
    fun unfreezeMembership(
        @PathVariable memberId: UUID,
        @PathVariable membershipId: UUID,
        @RequestBody(required = false) request: UnfreezeMembershipRequest?,
        authentication: Authentication,
    ): ResponseEntity<MembershipResponse> {
        val claims = authentication.pulseContext()
        val response =
            membershipService.unfreeze(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberId,
                membershipPublicId = membershipId,
                request = request ?: UnfreezeMembershipRequest(),
            )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/members/{memberId}/memberships/{membershipId}/terminate")
    @PreAuthorize("hasPermission(null, 'membership:update')")
    @Operation(summary = "Terminate a membership")
    fun terminateMembership(
        @PathVariable memberId: UUID,
        @PathVariable membershipId: UUID,
        @Valid @RequestBody request: TerminateMembershipRequest,
        authentication: Authentication,
    ): ResponseEntity<MembershipResponse> {
        val claims = authentication.pulseContext()
        val response =
            membershipService.terminate(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberId,
                membershipPublicId = membershipId,
                request = request,
            )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/memberships/expiring")
    @PreAuthorize("hasPermission(null, 'membership:read')")
    @Operation(summary = "Get memberships expiring within the next N days")
    fun getExpiringMemberships(
        @RequestParam(defaultValue = "30") days: Int,
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<ExpiringMembershipResponse>> {
        val claims = authentication.pulseContext()
        val effectiveDays = days.coerceIn(1, 90)
        return ResponseEntity.ok(
            membershipService.getExpiringMemberships(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                days = effectiveDays,
                pageable = pageable,
            ),
        )
    }

    // ── Payment endpoints ───────────────────────────────────────────────────

    @GetMapping("/members/{memberId}/payments")
    @PreAuthorize("hasPermission(null, 'payment:read')")
    @Operation(summary = "Get payment history for a member")
    fun getMemberPayments(
        @PathVariable memberId: UUID,
        @PageableDefault(size = 20, sort = ["paidAt"]) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<PaymentResponse>> {
        val claims = authentication.pulseContext()
        val member = resolveMember(memberId, claims)

        val page =
            paymentRepository.findAllByMemberId(member.id, pageable)
                .map { payment ->
                    val collectedByEmail = resolveUserEmail(payment.collectedById)
                    val invoiceNumber = invoiceService.findByPaymentId(payment.id)?.invoiceNumber
                    paymentService.toResponse(
                        payment,
                        member.publicId,
                        memberFullName(member),
                        collectedByEmail,
                        invoiceNumber,
                    )
                }
        return ResponseEntity.ok(page.toControllerPageResponse())
    }

    @GetMapping("/payments")
    @PreAuthorize("hasPermission(null, 'payment:read')")
    @Operation(summary = "List all payments (branch-scoped, paginated)")
    fun getAllPayments(
        @RequestParam(required = false) branchId: UUID?,
        @PageableDefault(size = 20, sort = ["paidAt"]) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<PaymentResponse>> {
        val claims = authentication.pulseContext()
        val orgId = resolveOrgId(claims.requireOrganizationId())

        val page =
            if (branchId != null) {
                val branchInternalId = resolveBranchId(branchId, orgId)
                paymentRepository.findAllByOrganizationIdAndBranchId(orgId, branchInternalId, pageable)
            } else {
                paymentRepository.findAllByOrganizationId(orgId, pageable)
            }

        val items =
            page.content.map { payment ->
                val member = memberRepository.findById(payment.memberId).orElse(null)
                val collectedByEmail = resolveUserEmail(payment.collectedById)
                val invoiceNumber = invoiceService.findByPaymentId(payment.id)?.invoiceNumber
                paymentService.toResponse(
                    payment,
                    member?.publicId ?: UUID.randomUUID(),
                    member?.let { memberFullName(it) } ?: "Unknown",
                    collectedByEmail,
                    invoiceNumber,
                )
            }

        return ResponseEntity.ok(page.toPageResponseWith(items))
    }

    // ── Invoice endpoints ───────────────────────────────────────────────────

    @GetMapping("/members/{memberId}/invoices")
    @PreAuthorize("hasPermission(null, 'invoice:read')")
    @Operation(summary = "Get invoice history for a member")
    fun getMemberInvoices(
        @PathVariable memberId: UUID,
        @PageableDefault(size = 20, sort = ["issuedAt"]) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<InvoiceResponse>> {
        val claims = authentication.pulseContext()
        val member = resolveMember(memberId, claims)

        val page =
            invoiceRepository.findAllByMemberId(member.id, pageable)
                .map { invoice ->
                    val payment = paymentRepository.findById(invoice.paymentId).orElse(null)
                    invoiceService.toResponse(
                        invoice,
                        member.publicId,
                        memberFullName(member),
                        payment?.paymentMethod ?: "Unknown",
                    )
                }
        return ResponseEntity.ok(page.toControllerPageResponse())
    }

    @GetMapping("/members/{memberId}/invoices/{invoiceId}")
    @PreAuthorize("hasPermission(null, 'invoice:read')")
    @Operation(summary = "Get a single invoice detail")
    fun getInvoice(
        @PathVariable memberId: UUID,
        @PathVariable invoiceId: UUID,
        authentication: Authentication,
    ): ResponseEntity<InvoiceResponse> {
        val claims = authentication.pulseContext()
        val member = resolveMember(memberId, claims)
        val orgId = resolveOrgId(claims.requireOrganizationId())
        val invoice = invoiceService.findByPublicIdAndOrgId(invoiceId, orgId)

        if (invoice.memberId != member.id) {
            throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Invoice not found.")
        }

        val payment = paymentRepository.findById(invoice.paymentId).orElse(null)
        return ResponseEntity.ok(
            invoiceService.toResponse(
                invoice,
                member.publicId,
                memberFullName(member),
                payment?.paymentMethod ?: "Unknown",
            ),
        )
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasPermission(null, 'invoice:read')")
    @Operation(summary = "List all invoices (branch-scoped, paginated)")
    fun getAllInvoices(
        @RequestParam(required = false) branchId: UUID?,
        @PageableDefault(size = 20, sort = ["issuedAt"]) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<InvoiceResponse>> {
        val claims = authentication.pulseContext()
        val orgId = resolveOrgId(claims.requireOrganizationId())

        val page =
            if (branchId != null) {
                val branchInternalId = resolveBranchId(branchId, orgId)
                invoiceRepository.findAllByOrganizationIdAndBranchId(orgId, branchInternalId, pageable)
            } else {
                invoiceRepository.findAllByOrganizationId(orgId, pageable)
            }

        val items =
            page.content.map { invoice ->
                val member = memberRepository.findById(invoice.memberId).orElse(null)
                val payment = paymentRepository.findById(invoice.paymentId).orElse(null)
                invoiceService.toResponse(
                    invoice,
                    member?.publicId ?: UUID.randomUUID(),
                    member?.let { memberFullName(it) } ?: "Unknown",
                    payment?.paymentMethod ?: "Unknown",
                )
            }

        return ResponseEntity.ok(page.toPageResponseWith(items))
    }

    @GetMapping("/invoices/{invoiceId}/qr-code")
    @PreAuthorize("hasPermission(null, 'invoice:read')")
    @Operation(summary = "Get the ZATCA QR code string for an invoice")
    fun getInvoiceQrCode(
        @PathVariable invoiceId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Map<String, String>> {
        val claims = authentication.pulseContext()
        val orgId = resolveOrgId(claims.requireOrganizationId())
        val invoice = invoiceService.findByPublicIdAndOrgId(invoiceId, orgId)

        val qrCode =
            invoice.zatcaQrCode
                ?: throw ArenaException(
                    HttpStatus.NOT_FOUND,
                    "resource-not-found",
                    "QR code not yet generated for this invoice.",
                )

        return ResponseEntity.ok(mapOf("qrCode" to qrCode))
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun resolveMember(
        memberPublicId: UUID,
        claims: JwtClaims,
    ): Member {
        val orgId = resolveOrgId(claims.requireOrganizationId())
        val clubId = resolveClubId(claims.requireClubId(), orgId)
        return memberRepository
            .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberPublicId, orgId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.")
            }
    }

    private fun resolveOrgId(orgPublicId: UUID): Long =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }
            .id

    private fun resolveClubId(
        clubPublicId: UUID,
        organizationId: Long,
    ): Long =
        clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }
            .id

    private fun resolveBranchId(
        branchPublicId: UUID,
        organizationId: Long,
    ): Long =
        branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, organizationId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.") }
            .id

    private fun resolveUserEmail(userId: Long): String = userRepository.findById(userId).map { it.email }.orElse("Unknown")

    private fun memberFullName(member: Member): String = "${member.firstNameEn} ${member.lastNameEn}"

    private fun <T> org.springframework.data.domain.Page<T>.toControllerPageResponse(): PageResponse<T> =
        PageResponse(
            items = content,
            pagination =
                PaginationMeta(
                    page = number,
                    size = size,
                    totalElements = totalElements,
                    totalPages = totalPages,
                    hasNext = hasNext(),
                    hasPrevious = hasPrevious(),
                ),
        )

    private fun <T, R> org.springframework.data.domain.Page<T>.toPageResponseWith(items: List<R>): PageResponse<R> =
        PageResponse(
            items = items,
            pagination =
                PaginationMeta(
                    page = number,
                    size = size,
                    totalElements = totalElements,
                    totalPages = totalPages,
                    hasNext = hasNext(),
                    hasPrevious = hasPrevious(),
                ),
        )
}

// ── Auth helpers ──────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No user identity in token.")
