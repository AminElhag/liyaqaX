package com.liyaqa.report.builder

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ReportResultRepository : JpaRepository<ReportResult, Long> {
    fun findFirstByTemplateIdAndDeletedAtIsNullOrderByRunAtDesc(templateId: Long): Optional<ReportResult>

    @Modifying
    @Query("UPDATE ReportResult r SET r.deletedAt = CURRENT_TIMESTAMP WHERE r.templateId = :templateId AND r.deletedAt IS NULL")
    fun softDeleteAllByTemplateId(templateId: Long)
}
