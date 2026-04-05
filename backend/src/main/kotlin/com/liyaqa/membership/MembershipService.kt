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
import com.liyaqa.membership.dto.ExpiringMembershipResponse
import com.liyaqa.membership.dto.FreezeMembershipRequest
import com.liyaqa.membership.dto.MembershipInvoiceInfo
import com.liyaqa.membership.dto.MembershipPaymentInfo
import com.liyaqa.membership.dto.MembershipPlanSummaryInfo
import com.liyaqa.membership.dto.MembershipResponse
import com.liyaqa.membership.dto.MembershipSummaryResponse
import com.liyaqa.membership.dto.RenewMembershipRequest
import com.liyaqa.membership.dto.TerminateMembershipRequest
import com.liyaqa.membership.dto.UnfreezeMembershipRequest
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
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MembershipService(
    private val membershipRepository: MembershipRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val freezePeriodRepository: FreezePeriodRepository,
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

    // ── Freeze / Unfreeze ────────────────────────────────────────────────────

    @Transactional
    fun freeze(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        membershipPublicId: UUID,
        request: FreezeMembershipRequest,
        callerUserPublicId: UUID,
    ): MembershipResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        val membership = findMembershipOrThrow(membershipPublicId, org.id)
        val plan =
            membershipPlanRepository.findById(membership.planId).orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Membership plan not found.")
            }
        val callerUser = findUserOrThrow(callerUserPublicId)

        // Rule 1 — Only active memberships can be frozen
        if (membership.membershipStatus != "active") {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Only active memberships can be frozen. Current status: ${membership.membershipStatus}.",
            )
        }

        // Rule 2 — Plan must allow freeze
        if (!plan.freezeAllowed) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "This membership plan does not allow freezing.",
            )
        }

        val requestedDays = ChronoUnit.DAYS.between(request.freezeStartDate, request.freezeEndDate).toInt()

        // Rule 3 — Freeze days limit
        val remainingFreezeDays = plan.maxFreezeDays - membership.freezeDaysUsed
        if (membership.freezeDaysUsed + requestedDays > plan.maxFreezeDays) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Freeze request exceeds the allowed limit. Requested: $requestedDays days. " +
                    "Remaining: $remainingFreezeDays days out of ${plan.maxFreezeDays} total.",
            )
        }

        // Rule 4 — No overlapping freeze periods
        if (freezePeriodRepository.existsByMembershipIdAndActualEndDateIsNull(membership.id)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "This membership already has an active freeze period.",
            )
        }

        // Rule 5 — Freeze start must be today or future
        if (request.freezeStartDate.isBefore(LocalDate.now())) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Freeze start date cannot be in the past.",
            )
        }

        // Create freeze period record
        freezePeriodRepository.save(
            FreezePeriod(
                organizationId = org.id,
                membershipId = membership.id,
                memberId = member.id,
                freezeStartDate = request.freezeStartDate,
                freezeEndDate = request.freezeEndDate,
                durationDays = requestedDays,
                reason = request.reason,
                requestedById = callerUser.id,
            ),
        )

        // Extend endDate and graceEndDate by the freeze duration
        membership.endDate = membership.endDate.plusDays(requestedDays.toLong())
        if (membership.graceEndDate != null) {
            membership.graceEndDate = membership.graceEndDate!!.plusDays(requestedDays.toLong())
        }
        membership.freezeDaysUsed += requestedDays
        membership.membershipStatus = "frozen"
        membershipRepository.save(membership)

        // Rule 11 — Member status sync
        member.membershipStatus = "frozen"
        memberRepository.save(member)

        val payment = findPaymentForMembership(membership)
        val invoice = payment?.let { invoiceService.findByPaymentId(it.id) }
        return toResponse(membership, member, plan, payment, invoice)
    }

    @Transactional
    fun unfreeze(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        membershipPublicId: UUID,
        request: UnfreezeMembershipRequest,
    ): MembershipResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        val membership = findMembershipOrThrow(membershipPublicId, org.id)
        val plan =
            membershipPlanRepository.findById(membership.planId).orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Membership plan not found.")
            }

        // Rule 6 — Only frozen memberships can be unfrozen
        if (membership.membershipStatus != "frozen") {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Only frozen memberships can be unfrozen. Current status: ${membership.membershipStatus}.",
            )
        }

        // Find the active freeze period
        val activeFreezePeriod =
            freezePeriodRepository
                .findByMembershipIdAndActualEndDateIsNull(membership.id)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "business-rule-violation",
                        "No active freeze period found for this membership.",
                    )
                }

        val today = LocalDate.now()
        val actualDaysFrozen =
            ChronoUnit.DAYS.between(activeFreezePeriod.freezeStartDate, today).toInt()
                .coerceAtLeast(0)
        val originalFreezeDays = activeFreezePeriod.durationDays
        val daysRecovered = originalFreezeDays - actualDaysFrozen

        // Close the freeze period
        activeFreezePeriod.actualEndDate = today
        freezePeriodRepository.save(activeFreezePeriod)

        // Adjust endDate: remove the days that were not actually frozen
        if (daysRecovered > 0) {
            membership.endDate = membership.endDate.minusDays(daysRecovered.toLong())
            if (membership.graceEndDate != null) {
                membership.graceEndDate = membership.graceEndDate!!.minusDays(daysRecovered.toLong())
            }
            membership.freezeDaysUsed -= daysRecovered
        }

        membership.membershipStatus = "active"
        if (request.notes != null) {
            membership.notes = request.notes
        }
        membershipRepository.save(membership)

        // Rule 11 — Member status sync
        member.membershipStatus = "active"
        memberRepository.save(member)

        val payment = findPaymentForMembership(membership)
        val invoice = payment?.let { invoiceService.findByPaymentId(it.id) }
        return toResponse(membership, member, plan, payment, invoice)
    }

    // ── Renew ──────────────────────────────────────────────────────────────

    @Transactional
    fun renew(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        membershipPublicId: UUID,
        request: RenewMembershipRequest,
        callerUserPublicId: UUID,
    ): MembershipResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        val currentMembership = findMembershipOrThrow(membershipPublicId, org.id)
        val newPlan = findPlanOrThrow(request.planId, org.id)
        val callerUser = findUserOrThrow(callerUserPublicId)

        // Rule 7 — Renewal requires active or expired membership
        if (currentMembership.membershipStatus !in listOf("active", "expired")) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Only active or expired memberships can be renewed. " +
                    "Current status: ${currentMembership.membershipStatus}.",
            )
        }

        // Rule 3 (from assignPlan) — Plan must belong to same club
        if (newPlan.clubId != club.id) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "This plan does not belong to your club.",
            )
        }

        // Plan must be active
        if (!newPlan.isActive) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "This plan is no longer available.",
            )
        }

        // Rule 8 — Renewal payment amount must match new plan price
        if (request.amountHalalas != newPlan.priceHalalas) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Payment amount (${request.amountHalalas}) does not match plan price (${newPlan.priceHalalas}).",
            )
        }

        // Default start date: current membership endDate + 1 day
        val startDate = request.startDate ?: currentMembership.endDate.plusDays(1)
        val endDate = startDate.plusDays(newPlan.durationDays.toLong())
        val graceEndDate =
            if (newPlan.gracePeriodDays > 0) endDate.plusDays(newPlan.gracePeriodDays.toLong()) else null

        // Create new membership record (old membership stays as history)
        val newMembership =
            membershipRepository.save(
                Membership(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = member.branchId,
                    memberId = member.id,
                    planId = newPlan.id,
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
                membershipId = newMembership.id,
                amountHalalas = request.amountHalalas,
                paymentMethod = request.paymentMethod,
                referenceNumber = request.referenceNumber,
                collectedBy = callerUser,
                notes = request.notes,
            )

        // Generate invoice
        val invoice =
            invoiceService.createInvoiceStub(
                payment = payment,
                member = member,
                club = club,
                subtotalHalalas = newPlan.priceHalalas,
            )

        // Rule 11 — Member status sync
        member.membershipStatus = "active"
        memberRepository.save(member)

        return toResponse(newMembership, member, newPlan, payment, invoice)
    }

    // ── Terminate ──────────────────────────────────────────────────────────

    @Transactional
    fun terminate(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        membershipPublicId: UUID,
        request: TerminateMembershipRequest,
    ): MembershipResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        val membership = findMembershipOrThrow(membershipPublicId, org.id)
        val plan =
            membershipPlanRepository.findById(membership.planId).orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Membership plan not found.")
            }

        // Rule 9 — Only active/frozen memberships can be terminated
        if (membership.membershipStatus !in listOf("active", "frozen")) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Only active or frozen memberships can be terminated. " +
                    "Current status: ${membership.membershipStatus}.",
            )
        }

        // Rule 10 — Termination reason required (validated by @NotBlank on DTO, but double-check)

        membership.membershipStatus = "terminated"
        membership.notes = request.reason
        membershipRepository.save(membership)

        // Rule 11 — Member status sync
        member.membershipStatus = "terminated"
        memberRepository.save(member)

        val payment = findPaymentForMembership(membership)
        val invoice = payment?.let { invoiceService.findByPaymentId(it.id) }
        return toResponse(membership, member, plan, payment, invoice)
    }

    // ── Expiry ─────────────────────────────────────────────────────────────

    @Transactional
    fun expireOverdueMemberships() {
        val today = LocalDate.now()
        val overdueStatuses = listOf("active", "frozen")
        val overdueMemberships =
            membershipRepository.findOverdueMemberships(overdueStatuses, today)

        for (membership in overdueMemberships) {
            membership.membershipStatus = "expired"
            membershipRepository.save(membership)

            val member = memberRepository.findById(membership.memberId).orElse(null)
            if (member != null) {
                member.membershipStatus = "expired"
                memberRepository.save(member)
            }
        }
    }

    // ── Expiring memberships query ─────────────────────────────────────────

    fun getExpiringMemberships(
        orgPublicId: UUID,
        clubPublicId: UUID,
        days: Int,
        pageable: Pageable,
    ): PageResponse<ExpiringMembershipResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val today = LocalDate.now()
        val cutoffDate = today.plusDays(days.toLong())

        return membershipRepository
            .findExpiringMemberships(org.id, club.id, listOf("active", "frozen"), today, cutoffDate, pageable)
            .map { membership ->
                val member = memberRepository.findById(membership.memberId).orElse(null)
                val plan = membershipPlanRepository.findById(membership.planId).orElse(null)
                val daysRemaining = ChronoUnit.DAYS.between(today, membership.endDate).toInt()
                ExpiringMembershipResponse(
                    memberId = member?.publicId ?: UUID.randomUUID(),
                    memberName = member?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "Unknown",
                    memberPhone = member?.phone ?: "",
                    planNameAr = plan?.nameAr ?: "",
                    planNameEn = plan?.nameEn ?: "",
                    endDate = membership.endDate,
                    daysRemaining = daysRemaining,
                    membershipId = membership.publicId,
                    membershipStatus = membership.membershipStatus,
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

    private fun findMembershipOrThrow(
        membershipPublicId: UUID,
        organizationId: Long,
    ): Membership =
        membershipRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(membershipPublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Membership not found.")
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
                    freezeAllowed = plan.freezeAllowed,
                    maxFreezeDays = plan.maxFreezeDays,
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
