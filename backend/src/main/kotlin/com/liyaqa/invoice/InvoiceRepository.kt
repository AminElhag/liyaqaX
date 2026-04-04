package com.liyaqa.invoice

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface InvoiceRepository : JpaRepository<Invoice, Long> {
    fun findByPublicIdAndOrganizationId(
        publicId: UUID,
        organizationId: Long,
    ): Optional<Invoice>

    fun findAllByMemberId(
        memberId: Long,
        pageable: Pageable,
    ): Page<Invoice>

    fun findAllByOrganizationIdAndBranchId(
        organizationId: Long,
        branchId: Long,
        pageable: Pageable,
    ): Page<Invoice>

    fun findAllByOrganizationId(
        organizationId: Long,
        pageable: Pageable,
    ): Page<Invoice>

    fun findByPaymentId(paymentId: Long): Optional<Invoice>

    @Query(
        "SELECT COUNT(i) FROM Invoice i WHERE i.clubId = :clubId AND YEAR(i.issuedAt) = :year",
    )
    fun countByClubIdAndYear(
        clubId: Long,
        year: Int,
    ): Long
}
