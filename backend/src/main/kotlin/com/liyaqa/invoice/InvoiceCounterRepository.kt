package com.liyaqa.invoice

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface InvoiceCounterRepository : JpaRepository<InvoiceCounter, Long> {
    @Query(
        value = """
            UPDATE invoice_counters
            SET last_value = last_value + 1, updated_at = NOW()
            WHERE club_id = :clubId
            RETURNING last_value
        """,
        nativeQuery = true,
    )
    fun incrementAndGet(
        @Param("clubId") clubId: Long,
    ): Long

    fun findByClubId(clubId: Long): InvoiceCounter?
}
