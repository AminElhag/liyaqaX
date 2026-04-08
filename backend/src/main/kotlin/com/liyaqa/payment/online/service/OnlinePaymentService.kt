package com.liyaqa.payment.online.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.InvoiceService
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.Membership
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.Payment
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.payment.online.client.MoyasarClient
import com.liyaqa.payment.online.client.MoyasarCreatePaymentRequest
import com.liyaqa.payment.online.dto.InitiatePaymentResponse
import com.liyaqa.payment.online.dto.PaymentStatusResponse
import com.liyaqa.payment.online.dto.TransactionHistoryResponse
import com.liyaqa.payment.online.dto.TransactionItem
import com.liyaqa.payment.online.entity.OnlinePaymentTransaction
import com.liyaqa.payment.online.repository.OnlinePaymentTransactionRepository
import com.liyaqa.payment.online.repository.TransactionHistoryProjection
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OnlinePaymentService(
    private val transactionRepository: OnlinePaymentTransactionRepository,
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val clubRepository: ClubRepository,
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val invoiceService: InvoiceService,
    private val portalSettingsService: ClubPortalSettingsService,
    private val moyasarClient: MoyasarClient,
    private val webhookVerifier: MoyasarWebhookVerifier,
    private val auditService: AuditService,
) {
    private val log = LoggerFactory.getLogger(OnlinePaymentService::class.java)

    @Transactional
    fun initiatePayment(
        memberPublicId: UUID,
        membershipPublicId: UUID,
        clubPublicId: UUID,
    ): InitiatePaymentResponse {
        val member = findMember(memberPublicId)
        val club = findClub(clubPublicId)

        // Rule 1 — Feature flag check
        val settings = portalSettingsService.getOrCreateSettings(club.id)
        if (!settings.onlinePaymentEnabled) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "business-rule-violation",
                "Online payments are not enabled for this club.",
                "ONLINE_PAYMENT_DISABLED",
            )
        }

        // Rule 9 — Club scoping: member can only pay for their own club
        if (member.clubId != club.id) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "Club scope mismatch.")
        }

        val membership = findMembership(membershipPublicId, member.organizationId)

        // Rule 2 — Membership payability check
        val isPayable = membership.membershipStatus == "pending_payment" ||
            member.membershipStatus == "lapsed"
        if (!isPayable) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "business-rule-violation",
                "This membership is not awaiting payment.",
                "MEMBERSHIP_NOT_PAYABLE",
            )
        }

        // Rule 4 — Duplicate transaction guard (idempotent initiate)
        val existing = transactionRepository.findInitiatedByMembership(membership.id)
        if (existing != null) {
            val plan = membershipPlanRepository.findById(membership.planId).orElse(null)
            return InitiatePaymentResponse(
                transactionId = existing.publicId,
                hostedUrl = existing.moyasarHostedUrl,
                amountSar = formatSar(existing.amountHalalas),
                planName = plan?.nameEn ?: "Membership",
            )
        }

        // Rule 3 — Amount from plan, never from request
        val plan = membershipPlanRepository.findById(membership.planId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Plan not found.") }

        val callbackUrl = moyasarClient.buildCallbackUrl("")

        val moyasarResponse = moyasarClient.createPayment(
            MoyasarCreatePaymentRequest(
                amount = plan.priceHalalas,
                description = "${plan.nameEn} — ${club.nameEn}",
                publishableApiKey = "",
                callbackUrl = moyasarClient.buildCallbackUrl(""),
                metadata = mapOf(
                    "membershipId" to membership.publicId.toString(),
                    "memberId" to member.publicId.toString(),
                    "clubId" to club.publicId.toString(),
                ),
            ),
        )

        val transaction = transactionRepository.save(
            OnlinePaymentTransaction(
                moyasarId = moyasarResponse.id,
                membershipId = membership.id,
                memberId = member.id,
                clubId = club.id,
                amountHalalas = plan.priceHalalas,
                status = "INITIATED",
                moyasarHostedUrl = moyasarResponse.url ?: "",
            ),
        )

        auditService.logFromContext(
            action = AuditAction.ONLINE_PAYMENT_INITIATED,
            entityType = "OnlinePaymentTransaction",
            entityId = transaction.publicId.toString(),
            changesJson = """{"amountHalalas":${plan.priceHalalas},"moyasarId":"${moyasarResponse.id}"}""",
        )

        return InitiatePaymentResponse(
            transactionId = transaction.publicId,
            hostedUrl = moyasarResponse.url ?: "",
            amountSar = formatSar(plan.priceHalalas),
            planName = plan.nameEn,
        )
    }

    @Transactional
    fun handleWebhook(rawBody: ByteArray, signature: String, moyasarId: String, status: String, sourceType: String?) {
        // Rule 5 — Webhook signature verification
        if (!webhookVerifier.verify(rawBody, signature)) {
            throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid webhook signature.")
        }

        val transaction = transactionRepository.findByMoyasarId(moyasarId)
        if (transaction == null) {
            log.warn("Webhook received for unknown moyasarId={}", moyasarId)
            return
        }

        when (status) {
            "paid" -> handlePaid(transaction, sourceType)
            "failed", "cancelled" -> handleFailed(transaction)
            else -> log.info("Ignoring webhook status={} for moyasarId={}", status, moyasarId)
        }
    }

    @Transactional
    fun handlePaid(transaction: OnlinePaymentTransaction, sourceType: String?) {
        // Rule 6 — Idempotent webhook
        if (transaction.status == "PAID") {
            log.info("Duplicate paid webhook for moyasarId={}, skipping", transaction.moyasarId)
            return
        }

        transaction.status = "PAID"
        transaction.paymentMethod = sourceType
        transaction.callbackReceivedAt = Instant.now()
        transactionRepository.save(transaction)

        // Activate membership
        val membership = membershipRepository.findById(transaction.membershipId)
            .orElseThrow { RuntimeException("Membership not found for transaction ${transaction.publicId}") }
        val plan = membershipPlanRepository.findById(membership.planId)
            .orElseThrow { RuntimeException("Plan not found for membership ${membership.publicId}") }

        val today = LocalDate.now()
        val endDate = today.plusDays(plan.durationDays.toLong())
        membershipRepository.activateMembership(membership.id, today, endDate)

        // Reactivate member if lapsed
        val member = memberRepository.findById(transaction.memberId)
            .orElseThrow { RuntimeException("Member not found for transaction ${transaction.publicId}") }
        if (member.membershipStatus == "lapsed") {
            member.membershipStatus = "active"
            memberRepository.save(member)
        } else if (member.membershipStatus != "active") {
            member.membershipStatus = "active"
            memberRepository.save(member)
        }

        // Create Payment record
        val club = clubRepository.findById(transaction.clubId)
            .orElseThrow { RuntimeException("Club not found") }
        val org = organizationRepository.findById(member.organizationId)
            .orElseThrow { RuntimeException("Organization not found") }

        val systemUser = userRepository.findById(member.userId).orElse(null)
        val payment = paymentRepository.save(
            Payment(
                organizationId = member.organizationId,
                clubId = member.clubId,
                branchId = member.branchId,
                memberId = member.id,
                membershipId = membership.id,
                amountHalalas = transaction.amountHalalas,
                paymentMethod = "online-${sourceType ?: "card"}",
                referenceNumber = transaction.moyasarId,
                collectedById = systemUser?.id ?: member.userId,
                paidAt = Instant.now(),
                notes = "Moyasar online payment: ${transaction.moyasarId}",
            ),
        )

        // Create Invoice (ZATCA fires automatically)
        invoiceService.createInvoice(
            payment = payment,
            member = member,
            club = club,
            organization = org,
            subtotalHalalas = transaction.amountHalalas,
        )

        auditService.log(
            action = AuditAction.ONLINE_PAYMENT_SUCCEEDED,
            entityType = "OnlinePaymentTransaction",
            entityId = transaction.publicId.toString(),
            actorId = "system",
            actorScope = "webhook",
            organizationId = member.organizationId.toString(),
            clubId = club.publicId.toString(),
            changesJson = """{"moyasarId":"${transaction.moyasarId}","amountHalalas":${transaction.amountHalalas}}""",
        )
    }

    @Transactional
    fun handleFailed(transaction: OnlinePaymentTransaction) {
        if (transaction.status == "PAID" || transaction.status == "FAILED") {
            return
        }

        transaction.status = "FAILED"
        transaction.callbackReceivedAt = Instant.now()
        transactionRepository.save(transaction)

        auditService.log(
            action = AuditAction.ONLINE_PAYMENT_FAILED,
            entityType = "OnlinePaymentTransaction",
            entityId = transaction.publicId.toString(),
            actorId = "system",
            actorScope = "webhook",
            changesJson = """{"moyasarId":"${transaction.moyasarId}"}""",
        )
    }

    fun getStatus(moyasarId: String, memberPublicId: UUID): PaymentStatusResponse {
        val member = findMember(memberPublicId)
        val transaction = transactionRepository.findByMoyasarId(moyasarId)
            ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Transaction not found.")

        if (transaction.memberId != member.id) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "You cannot view this transaction.")
        }

        return PaymentStatusResponse(
            moyasarId = transaction.moyasarId,
            status = transaction.status,
            paymentMethod = transaction.paymentMethod,
            amountSar = formatSar(transaction.amountHalalas),
            paidAt = transaction.callbackReceivedAt,
        )
    }

    fun getMemberHistory(memberPublicId: UUID): TransactionHistoryResponse {
        val member = findMember(memberPublicId)
        val projections = transactionRepository.findByMemberId(member.id)
        return toHistoryResponse(projections)
    }

    fun getMemberHistoryForStaff(memberPublicId: UUID, clubPublicId: UUID): TransactionHistoryResponse {
        val member = findMember(memberPublicId)
        val club = findClub(clubPublicId)
        val projections = transactionRepository.findByMemberIdAndClubId(member.id, club.id)
        return toHistoryResponse(projections)
    }

    private fun toHistoryResponse(projections: List<TransactionHistoryProjection>): TransactionHistoryResponse =
        TransactionHistoryResponse(
            transactions = projections.map { p ->
                TransactionItem(
                    transactionId = p.getPublicId(),
                    moyasarId = p.getMoyasarId(),
                    planName = p.getPlanNameEn() ?: p.getPlanNameAr() ?: "Unknown",
                    amountSar = formatSar(p.getAmountHalalas()),
                    status = p.getStatus(),
                    paymentMethod = p.getPaymentMethod(),
                    createdAt = p.getCreatedAt(),
                )
            },
        )

    private fun findMember(publicId: UUID): Member =
        memberRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.") }

    private fun findClub(publicId: UUID): Club =
        clubRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }

    private fun findMembership(publicId: UUID, organizationId: Long): Membership =
        membershipRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(publicId, organizationId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Membership not found.") }

    private fun formatSar(halalas: Long): String = "%.2f".format(halalas / 100.0)
}
