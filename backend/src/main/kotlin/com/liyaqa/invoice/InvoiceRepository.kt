package com.liyaqa.invoice

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
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

    fun findByPublicId(publicId: UUID): Optional<Invoice>

    @Query(
        value = """
            SELECT COUNT(*) FROM invoices
            WHERE zatca_status IN ('generated', 'signed')
              AND zatca_retry_count < 5
        """,
        nativeQuery = true,
    )
    fun countPendingZatcaReporting(): Long

    @Query(
        value = "SELECT COUNT(*) FROM invoices WHERE zatca_status = 'failed'",
        nativeQuery = true,
    )
    fun countFailedZatcaReporting(): Long

    @Query(
        value = """
            SELECT COUNT(*) FROM invoices
            WHERE zatca_status IN ('generated', 'signed')
              AND created_at < :threshold
        """,
        nativeQuery = true,
    )
    fun countInvoicesApproachingDeadline(threshold: Instant): Long

    @Query(
        value = """
            SELECT
                i.public_id        AS invoicePublicId,
                i.invoice_number   AS invoiceNumber,
                c.name_en          AS clubName,
                CONCAT(m.first_name_en, ' ', m.last_name_en) AS memberName,
                i.total_halalas    AS amountHalalas,
                i.created_at       AS createdAt,
                i.zatca_retry_count AS zatcaRetryCount,
                i.zatca_last_error  AS zatcaLastError,
                i.zatca_status      AS zatcaStatus
            FROM invoices i
            JOIN clubs c ON c.id = i.club_id
            JOIN members m ON m.id = i.member_id
            WHERE i.zatca_status = 'failed'
            ORDER BY i.created_at DESC
            LIMIT 500
        """,
        nativeQuery = true,
    )
    fun findFailedZatcaInvoicesWithClub(): List<FailedZatcaInvoiceProjection>

    @Query(
        value = """
            SELECT i.id FROM invoices i
            JOIN clubs c ON c.id = i.club_id
            WHERE i.zatca_status = 'failed'
              AND c.public_id = :clubPublicId
        """,
        nativeQuery = true,
    )
    fun findFailedZatcaReportingByClub(clubPublicId: UUID): List<Long>
}
