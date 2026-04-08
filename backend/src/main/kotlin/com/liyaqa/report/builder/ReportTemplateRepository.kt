package com.liyaqa.report.builder

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ReportTemplateRepository : JpaRepository<ReportTemplate, Long> {
    fun findByClubIdAndDeletedAtIsNull(clubId: Long): List<ReportTemplate>

    fun findByPublicIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        clubId: Long,
    ): Optional<ReportTemplate>

    fun existsByNameAndClubIdAndDeletedAtIsNull(
        name: String,
        clubId: Long,
    ): Boolean
}
