package com.liyaqa.report.schedule

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.report.builder.ReportTemplateRepository
import com.liyaqa.report.schedule.dto.CreateReportScheduleRequest
import com.liyaqa.report.schedule.dto.ReportScheduleResponse
import com.liyaqa.report.schedule.dto.UpdateReportScheduleRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ReportScheduleService(
    private val reportScheduleRepository: ReportScheduleRepository,
    private val reportTemplateRepository: ReportTemplateRepository,
    private val reportSchedulerService: ReportSchedulerService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val MAX_RECIPIENTS = 10
        private val VALID_FREQUENCIES = setOf("daily", "weekly", "monthly")
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }

    fun getSchedule(
        templatePublicId: UUID,
        clubId: Long,
    ): ReportScheduleResponse {
        val template = findTemplateOrThrow(templatePublicId, clubId)
        val schedule =
            reportScheduleRepository.findByTemplateIdAndDeletedAtIsNull(template.id)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "No schedule exists for this template.")
                }
        return toResponse(schedule, template.publicId, template.name)
    }

    @Transactional
    fun createSchedule(
        templatePublicId: UUID,
        request: CreateReportScheduleRequest,
        clubId: Long,
    ): ReportScheduleResponse {
        val template = findTemplateOrThrow(templatePublicId, clubId)

        validateFrequency(request.frequency)
        validateRecipients(request.recipients)

        if (reportScheduleRepository.findByTemplateIdAndDeletedAtIsNull(template.id).isPresent) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A schedule already exists for this template. Update or delete it instead.",
            )
        }

        val schedule =
            ReportSchedule(
                templateId = template.id,
                clubId = clubId,
                frequency = request.frequency,
                recipientsJson = objectMapper.writeValueAsString(request.recipients),
                isActive = request.isActive,
            )

        val saved = reportScheduleRepository.save(schedule)

        auditService.logFromContext(
            action = AuditAction.REPORT_SCHEDULE_CREATED,
            entityType = "ReportSchedule",
            entityId = saved.publicId.toString(),
        )

        return toResponse(saved, template.publicId, template.name)
    }

    @Transactional
    fun updateSchedule(
        templatePublicId: UUID,
        request: UpdateReportScheduleRequest,
        clubId: Long,
    ): ReportScheduleResponse {
        val template = findTemplateOrThrow(templatePublicId, clubId)
        val schedule =
            reportScheduleRepository.findByTemplateIdAndDeletedAtIsNull(template.id)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "No schedule exists for this template.")
                }

        request.frequency?.let {
            validateFrequency(it)
            schedule.frequency = it
        }
        request.recipients?.let {
            validateRecipients(it)
            schedule.recipientsJson = objectMapper.writeValueAsString(it)
        }
        request.isActive?.let { schedule.isActive = it }

        val saved = reportScheduleRepository.save(schedule)

        auditService.logFromContext(
            action = AuditAction.REPORT_SCHEDULE_UPDATED,
            entityType = "ReportSchedule",
            entityId = saved.publicId.toString(),
        )

        return toResponse(saved, template.publicId, template.name)
    }

    @Transactional
    fun deleteSchedule(
        templatePublicId: UUID,
        clubId: Long,
    ) {
        val template = findTemplateOrThrow(templatePublicId, clubId)
        val schedule =
            reportScheduleRepository.findByTemplateIdAndDeletedAtIsNull(template.id)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "No schedule exists for this template.")
                }

        schedule.softDelete()
        reportScheduleRepository.save(schedule)

        auditService.logFromContext(
            action = AuditAction.REPORT_SCHEDULE_DELETED,
            entityType = "ReportSchedule",
            entityId = schedule.publicId.toString(),
        )
    }

    private fun findTemplateOrThrow(
        templatePublicId: UUID,
        clubId: Long,
    ) = reportTemplateRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(templatePublicId, clubId)
        .orElseThrow {
            ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Report template not found.")
        }

    private fun validateFrequency(frequency: String) {
        if (frequency !in VALID_FREQUENCIES) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Invalid frequency: $frequency. Must be one of: daily, weekly, monthly.",
            )
        }
    }

    private fun validateRecipients(recipients: List<String>) {
        if (recipients.size > MAX_RECIPIENTS) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Maximum $MAX_RECIPIENTS recipients allowed.",
            )
        }
        for (email in recipients) {
            if (!EMAIL_REGEX.matches(email)) {
                throw ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Invalid email address: $email",
                )
            }
        }
    }

    private fun toResponse(
        schedule: ReportSchedule,
        templatePublicId: UUID,
        templateName: String,
    ): ReportScheduleResponse {
        val recipients: List<String> =
            objectMapper.readValue(
                schedule.recipientsJson,
                objectMapper.typeFactory.constructCollectionType(List::class.java, String::class.java),
            )
        return ReportScheduleResponse(
            id = schedule.publicId,
            templateId = templatePublicId,
            templateName = templateName,
            frequency = schedule.frequency,
            recipients = recipients,
            isActive = schedule.isActive,
            lastRunAt = schedule.lastRunAt,
            lastRunStatus = schedule.lastRunStatus,
            lastError = schedule.lastError,
            nextRunAt = reportSchedulerService.nextRunAt(schedule.frequency),
            createdAt = schedule.createdAt,
        )
    }
}
