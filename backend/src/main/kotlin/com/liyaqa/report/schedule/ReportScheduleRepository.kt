package com.liyaqa.report.schedule

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ReportScheduleRepository : JpaRepository<ReportSchedule, Long> {
    fun findByTemplateIdAndDeletedAtIsNull(templateId: Long): Optional<ReportSchedule>

    fun findAllByIsActiveTrueAndDeletedAtIsNull(): List<ReportSchedule>
}
