package com.liyaqa.cashdrawer

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CashDrawerEntryRepository : JpaRepository<CashDrawerEntry, Long> {
    fun findAllBySessionIdOrderByRecordedAtAsc(sessionId: Long): List<CashDrawerEntry>

    @Query(
        """
        SELECT COALESCE(SUM(e.amountHalalas), 0)
        FROM CashDrawerEntry e
        WHERE e.sessionId = :sessionId
          AND e.entryType = :entryType
        """,
    )
    fun sumBySessionIdAndEntryType(
        sessionId: Long,
        entryType: String,
    ): Long
}
