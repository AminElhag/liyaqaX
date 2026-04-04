package com.liyaqa.membership

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.Invoice
import com.liyaqa.invoice.InvoiceService
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.dto.AssignMembershipRequest
import com.liyaqa.membership.dto.MembershipInvoiceInfo
import com.liyaqa.membership.dto.MembershipPaymentInfo
import com.liyaqa.membership.dto.MembershipPlanSummaryInfo
import com.liyaqa.membership.dto.MembershipResponse
import com.liyaqa.membership.dto.MembershipSummaryResponse
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.Payment
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.payment.PaymentService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MembershipService(
    private val membershipRepository: MembershipRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val memberRepository: MemberRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val invoiceService: InvoiceService,
) {
    companion object {
        private val ACTIVE_STATUSES = listOf("active", "frozen")
    }

    /**
     * Assigns a membership plan to a member. Creates membership, records payment,
     * generates invoice stub, and activates member — all in one transaction.
     * Enforces all 8 business rules.
     */
    @Transactional
    fun assignPlan(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        request: AssignMembershipRequest,
        callerUserPublicId: UUID,
    ): MembershipResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        val plan = findPlanOrThrow(request.planId, org.id)
        val callerUser = findUserOrThrow(callerUserPublicId)

        // Rule 3 — Plan must belong to the same club
        if (plan.clubId != club.id) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "This plan does not belong to your club.",
            )
        }

        // Rule 4 — Plan must be active
        if (!plan.isActive) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "This plan is no longer available.",
            )
        }

        // Rule 1 — One active membership at a time
        if (membershipRepository.existsByMemberIdAndMembershipStatusInAndDeletedAtIsNull(member.id, ACTIVE_STATUSES)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "This member already has an active or frozen membership. Only one active membership is allowed at a time.",
            )
        }

        // Rule 2 — Payment amount must match plan price
        if (request.amountHalalas != plan.priceHalalas) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Payment amount (${request.amountHalalas}) does not match plan price (${plan.priceHalalas}).",
            )
            // TODO(#future): Future plan will allow partial payments and custom pricing with manager approval
        }

        val startDate = request.startDate ?: LocalDate.now()
        val endDate = startDate.plusDays(plan.durationDays.toLong())
        val graceEndDate = if (plan.gracePeriodDays > 0) endDate.plusDays(plan.gracePeriodDays.toLong()) else null

        // Create membership
        val membership =
            membershipRepository.save(
                Membership(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = member.branchId,
                    memberId = member.id,
                    planId = plan.id,
                    membershipStatus = "active",
                    startDate = startDate,
                    endDate = endDate,
                    graceEndDate = graceEndDate,
                    notes = request.notes,
                ),
            )

        // Record payment
        val payment =
            paymentService.recordPayment(
                member = member,
                membershipId = membership.id,
                amountHalalas = request.amountHalalas,
                paymentMethod = request.paymentMethod,
                referenceNumber = request.referenceNumber,
                collectedBy = callerUser,
                notes = request.notes,
            )

        // Rule 5 — VAT calculation (server-side) + Rule 8 — Invoice total integrity
        val invoice =
            invoiceService.createInvoiceStub(
                payment = payment,
                member = member,
                club = club,
                subtotalHalalas = plan.priceHalalas,
            )

        // Rule 6 — Member status update (in same transaction)
        member.membershipStatus = "active"
        memberRepository.save(member)

        // Rule 7 — Atomicity is guaranteed by @Transactional
        return toResponse(membership, member, plan, payment, invoice)
    }

    fun getActiveMembership(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
    ): MembershipResponse? {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        val membership =
            membershipRepository
                .findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(member.id, ACTIVE_STATUSES)
                .orElse(null)
                ?: return null

        val plan = membershipPlanRepository.findById(membership.planId).orElse(null) ?: return null
        val payment = findPaymentForMembership(membership)
        val invoice = payment?.let { invoiceService.findByPaymentId(it.id) }

        return toResponse(membership, member, plan, payment, invoice)
    }

    fun getMembershipHistory(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<MembershipSummaryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        return membershipRepository
            .findAllByMemberIdAndDeletedAtIsNull(member.id, pageable)
            .map { membership ->
                val plan = membershipPlanRepository.findById(membership.planId).orElse(null)
                val payment = findPaymentForMembership(membership)
                MembershipSummaryResponse(
                    id = membership.publicId,
                    planNameAr = plan?.nameAr ?: "",
                    planNameEn = plan?.nameEn ?: "",
                    status = membership.membershipStatus,
                    startDate = membership.startDate,
                    endDate = membership.endDate,
                    amountHalalas = payment?.amountHalalas ?: 0,
                    amountSar = formatSar(payment?.amountHalalas ?: 0),
                    paymentMethod = payment?.paymentMethod,
                )
            }
            .toPageResponse()
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun findPaymentForMembership(membership: Membership): Payment? =
        paymentRepository.findByMembershipId(membership.id).orElse(null)

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.")
            }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ): Club =
        clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
            }

    private fun findMemberOrThrow(
        memberPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Member =
        memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberPublicId, organizationId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.")
            }

    private fun findPlanOrThrow(
        planPublicId: UUID,
        organizationId: Long,
    ): MembershipPlan =
        membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(planPublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Membership plan not found.")
            }

    private fun findUserOrThrow(userPublicId: UUID): User =
        userRepository.findByPublicIdAndDeletedAtIsNull(userPublicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "User not found.")
            }

    private fun toResponse(
        membership: Membership,
        member: Member,
        plan: MembershipPlan,
        payment: Payment?,
        invoice: Invoice?,
    ): MembershipResponse =
        MembershipResponse(
            id = membership.publicId,
            memberId = member.publicId,
            plan =
                MembershipPlanSummaryInfo(
                    id = plan.publicId,
                    nameAr = plan.nameAr,
                    nameEn = plan.nameEn,
                    priceHalalas = plan.priceHalalas,
                    priceSar = formatSar(plan.priceHalalas),
                    durationDays = plan.durationDays,
                ),
            status = membership.membershipStatus,
            startDate = membership.startDate,
            endDate = membership.endDate,
            graceEndDate = membership.graceEndDate,
            freezeDaysUsed = membership.freezeDaysUsed,
            payment =
                payment?.let {
                    MembershipPaymentInfo(
                        id = it.publicId,
                        amountHalalas = it.amountHalalas,
                        amountSar = formatSar(it.amountHalalas),
                        paymentMethod = it.paymentMethod,
                        paidAt = it.paidAt,
                    )
                },
            invoice =
                invoice?.let {
                    MembershipInvoiceInfo(
                        id = it.publicId,
                        invoiceNumber = it.invoiceNumber,
                        totalHalalas = it.totalHalalas,
                        totalSar = formatSar(it.totalHalalas),
                        issuedAt = it.issuedAt,
                    )
                },
            createdAt = membership.createdAt,
        )

    private fun formatSar(halalas: Long): String = "%.2f".format(halalas / 100.0)
}
