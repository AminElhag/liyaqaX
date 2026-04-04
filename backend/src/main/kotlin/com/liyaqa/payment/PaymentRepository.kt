package com.liyaqa.payment

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
}
