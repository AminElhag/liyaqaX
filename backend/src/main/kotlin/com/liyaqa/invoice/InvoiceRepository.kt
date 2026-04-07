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

    fun findTopByClubIdAndInvoiceCounterValueIsNotNullOrderByInvoiceCounterValueDesc(clubId: Long): Optional<Invoice>

    fun findAllByClubIdAndZatcaStatus(
        clubId: Long,
        zatcaStatus: String,
    ): List<Invoice>

    @Query(
        value = "SELECT COUNT(*) FROM invoices i WHERE i.club_id = :clubId AND EXTRACT(YEAR FROM i.issued_at) = :year",
        nativeQuery = true,
    )
    fun countByClubIdAndYear(
        clubId: Long,
        year: Int,
    ): Long

    @Query(
        value = """
            SELECT i.id FROM invoices i
            WHERE i.zatca_status IN ('generated', 'signed')
              AND i.zatca_retry_count < 5
            ORDER BY i.created_at ASC
            LIMIT 100
        """,
        nativeQuery = true,
    )
    fun findPendingZatcaReporting(): List<Long>

    @Query(
        value = """
            SELECT i.id FROM invoices i
            WHERE i.zatca_status = 'failed'
            ORDER BY i.updated_at DESC
        """,
        nativeQuery = true,
    )
    fun findFailedZatcaReporting(): List<Long>
}
