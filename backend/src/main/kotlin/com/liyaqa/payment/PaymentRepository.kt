package com.liyaqa.payment

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByPublicIdAndOrganizationId(
        publicId: UUID,
        organizationId: Long,
    ): Optional<Payment>

    fun findAllByMemberId(
        memberId: Long,
        pageable: Pageable,
    ): Page<Payment>

    fun findAllByOrganizationIdAndBranchId(
        organizationId: Long,
        branchId: Long,
        pageable: Pageable,
    ): Page<Payment>

    fun findAllByOrganizationId(
        organizationId: Long,
        pageable: Pageable,
    ): Page<Payment>

    fun findByMembershipId(membershipId: Long): Optional<Payment>

    @Query(
        value = """
            SELECT id, public_id AS publicId, amount_halalas AS amountHalalas,
                   payment_method AS paymentMethod, paid_at AS paidAt, created_at AS createdAt
            FROM payments
            WHERE member_id = :memberId
            ORDER BY paid_at DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findByMemberIdForTimeline(
        @Param("memberId") memberId: Long,
        @Param("limit") limit: Int,
    ): List<PaymentTimelineProjection>
}
