package com.liyaqa.payment

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.Member
import com.liyaqa.payment.dto.PaymentResponse
import com.liyaqa.user.User
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val auditService: AuditService,
) {
    // TODO(#future): Future plan will support refunds via separate refund entity

    @Transactional
    fun recordPayment(
        member: Member,
        membershipId: Long?,
        amountHalalas: Long,
        paymentMethod: String,
        referenceNumber: String?,
        collectedBy: User,
        notes: String?,
    ): Payment {
        val payment =
            paymentRepository.save(
                Payment(
                    organizationId = member.organizationId,
                    clubId = member.clubId,
                    branchId = member.branchId,
                    memberId = member.id,
                    membershipId = membershipId,
                    amountHalalas = amountHalalas,
                    paymentMethod = paymentMethod,
                    referenceNumber = referenceNumber,
                    collectedById = collectedBy.id,
                    paidAt = Instant.now(),
                    notes = notes,
                ),
            )

        auditService.logFromContext(
            action = AuditAction.PAYMENT_COLLECTED,
            entityType = "Payment",
            entityId = payment.publicId.toString(),
            changesJson = """{"amountHalalas":$amountHalalas,"paymentMethod":"$paymentMethod"}""",
        )

        return payment
    }

    fun toResponse(
        payment: Payment,
        memberPublicId: UUID,
        memberName: String,
        collectedByEmail: String,
        invoiceNumber: String?,
    ): PaymentResponse =
        PaymentResponse(
            id = payment.publicId,
            memberId = memberPublicId,
            memberName = memberName,
            amountHalalas = payment.amountHalalas,
            amountSar = formatSar(payment.amountHalalas),
            paymentMethod = payment.paymentMethod,
            referenceNumber = payment.referenceNumber,
            invoiceNumber = invoiceNumber,
            collectedBy = collectedByEmail,
            paidAt = payment.paidAt,
        )

    fun findByPublicIdAndOrgId(
        publicId: UUID,
        organizationId: Long,
    ): Payment =
        paymentRepository.findByPublicIdAndOrganizationId(publicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Payment not found.")
            }

    private fun formatSar(halalas: Long): String = "%.2f".format(halalas / 100.0)
}
