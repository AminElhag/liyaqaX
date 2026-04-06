package com.liyaqa.cashdrawer

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CashDrawerSessionRepository : JpaRepository<CashDrawerSession, Long> {
    fun findByBranchIdAndStatusAndDeletedAtIsNull(
        branchId: Long,
        status: String,
    ): Optional<CashDrawerSession>

    fun findByPublicIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        clubId: Long,
    ): Optional<CashDrawerSession>

    @Query(
        """
        SELECT s FROM CashDrawerSession s
        WHERE s.clubId = :clubId
          AND s.deletedAt IS NULL
          AND (:branchId IS NULL OR s.branchId = :branchId)
          AND (:status IS NULL OR s.status = :status)
        """,
    )
    fun findAllFiltered(
        clubId: Long,
        branchId: Long?,
        status: String?,
        pageable: Pageable,
    ): Page<CashDrawerSession>
}
