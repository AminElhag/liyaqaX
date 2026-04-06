package com.liyaqa.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    @Query(
        value = """
            SELECT * FROM audit_logs a
            WHERE (CAST(:actorId AS VARCHAR) IS NULL OR a.actor_id = :actorId)
              AND (CAST(:action AS VARCHAR) IS NULL OR a.action = :action)
              AND (CAST(:entityType AS VARCHAR) IS NULL OR a.entity_type = :entityType)
              AND (CAST(:organizationId AS VARCHAR) IS NULL OR a.organization_id = :organizationId)
              AND (CAST(:fromDate AS TIMESTAMPTZ) IS NULL OR a.created_at >= :fromDate)
              AND (CAST(:toDate AS TIMESTAMPTZ) IS NULL OR a.created_at <= :toDate)
            ORDER BY a.created_at DESC
        """,
        countQuery = """
            SELECT count(*) FROM audit_logs a
            WHERE (CAST(:actorId AS VARCHAR) IS NULL OR a.actor_id = :actorId)
              AND (CAST(:action AS VARCHAR) IS NULL OR a.action = :action)
              AND (CAST(:entityType AS VARCHAR) IS NULL OR a.entity_type = :entityType)
              AND (CAST(:organizationId AS VARCHAR) IS NULL OR a.organization_id = :organizationId)
              AND (CAST(:fromDate AS TIMESTAMPTZ) IS NULL OR a.created_at >= :fromDate)
              AND (CAST(:toDate AS TIMESTAMPTZ) IS NULL OR a.created_at <= :toDate)
        """,
        nativeQuery = true,
    )
    fun findAllFiltered(
        @Param("actorId") actorId: String?,
        @Param("action") action: String?,
        @Param("entityType") entityType: String?,
        @Param("organizationId") organizationId: String?,
        @Param("fromDate") fromDate: Instant?,
        @Param("toDate") toDate: Instant?,
        pageable: Pageable,
    ): Page<AuditLog>
}
