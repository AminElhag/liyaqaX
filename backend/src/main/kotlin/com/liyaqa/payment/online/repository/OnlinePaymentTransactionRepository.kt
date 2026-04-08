package com.liyaqa.payment.online.repository

import com.liyaqa.payment.online.entity.OnlinePaymentTransaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OnlinePaymentTransactionRepository : JpaRepository<OnlinePaymentTransaction, Long> {

    @Query(
        value = """
            SELECT * FROM online_payment_transactions
            WHERE moyasar_id = :moyasarId
        """,
        nativeQuery = true,
    )
    fun findByMoyasarId(@Param("moyasarId") moyasarId: String): OnlinePaymentTransaction?

    @Query(
        value = """
            SELECT * FROM online_payment_transactions
            WHERE membership_id = :membershipId
              AND status = 'INITIATED'
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findInitiatedByMembership(@Param("membershipId") membershipId: Long): OnlinePaymentTransaction?

    @Query(
        value = """
            SELECT t.public_id AS publicId,
                   t.moyasar_id AS moyasarId,
                   mp.name_en AS planNameEn,
                   mp.name_ar AS planNameAr,
                   t.amount_halalas AS amountHalalas,
                   t.status AS status,
                   t.payment_method AS paymentMethod,
                   t.created_at AS createdAt
            FROM online_payment_transactions t
            JOIN memberships m ON m.id = t.membership_id
            JOIN membership_plans mp ON mp.id = m.plan_id
            WHERE t.member_id = :memberId
            ORDER BY t.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findByMemberId(@Param("memberId") memberId: Long): List<TransactionHistoryProjection>

    @Query(
        value = """
            SELECT t.public_id AS publicId,
                   t.moyasar_id AS moyasarId,
                   mp.name_en AS planNameEn,
                   mp.name_ar AS planNameAr,
                   t.amount_halalas AS amountHalalas,
                   t.status AS status,
                   t.payment_method AS paymentMethod,
                   t.created_at AS createdAt
            FROM online_payment_transactions t
            JOIN memberships m ON m.id = t.membership_id
            JOIN membership_plans mp ON mp.id = m.plan_id
            WHERE t.member_id = :memberId
              AND t.club_id = :clubId
            ORDER BY t.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findByMemberIdAndClubId(
        @Param("memberId") memberId: Long,
        @Param("clubId") clubId: Long,
    ): List<TransactionHistoryProjection>
}
