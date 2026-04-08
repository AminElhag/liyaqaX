package com.liyaqa.report.builder

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.report.builder.dto.CreateReportTemplateRequest
import com.liyaqa.report.builder.dto.ReportTemplateResponse
import com.liyaqa.report.builder.dto.UpdateReportTemplateRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ReportTemplateService(
    private val reportTemplateRepository: ReportTemplateRepository,
    private val reportResultRepository: ReportResultRepository,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val MAX_METRICS = 10
    }

    fun listTemplates(clubId: Long): List<ReportTemplateResponse> {
        val templates = reportTemplateRepository.findByClubIdAndDeletedAtIsNull(clubId)
        return templates.map { toResponse(it) }
    }

    fun getTemplate(
        publicId: UUID,
        clubId: Long,
    ): ReportTemplateResponse {
        val template = findOrThrow(publicId, clubId)
        return toResponse(template)
    }

    @Transactional
    fun createTemplate(
        request: CreateReportTemplateRequest,
        clubId: Long,
    ): ReportTemplateResponse {
        validateMetrics(request.metrics)
        validateDimensions(request.dimensions)
        validateCompatibility(request.metrics, request.dimensions)

        if (reportTemplateRepository.existsByNameAndClubIdAndDeletedAtIsNull(request.name, clubId)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A report template with name '${request.name}' already exists.",
            )
        }

        val scope = deriveScope(request.metrics)

        val template =
            ReportTemplate(
                clubId = clubId,
                name = request.name,
                description = request.description,
                metrics = objectMapper.writeValueAsString(request.metrics),
                dimensions = objectMapper.writeValueAsString(request.dimensions),
                filters = request.filters?.let { objectMapper.writeValueAsString(it) },
                metricScope = scope,
            )

        val saved = reportTemplateRepository.save(template)

        auditService.logFromContext(
            action = AuditAction.REPORT_TEMPLATE_CREATED,
            entityType = "ReportTemplate",
            entityId = saved.publicId.toString(),
        )

        return toResponse(saved)
    }

    @Transactional
    fun updateTemplate(
        publicId: UUID,
        request: UpdateReportTemplateRequest,
        clubId: Long,
    ): ReportTemplateResponse {
        val template = findOrThrow(publicId, clubId)

        if (template.isSystem) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "System templates cannot be modified.",
            )
        }

        val newMetrics = request.metrics ?: objectMapper.readValue<List<String>>(template.metrics)
        val newDimensions = request.dimensions ?: objectMapper.readValue<List<String>>(template.dimensions)

        if (request.metrics != null || request.dimensions != null) {
            validateMetrics(newMetrics)
            validateDimensions(newDimensions)
            validateCompatibility(newMetrics, newDimensions)
        }

        request.name?.let { template.name = it }
        request.description?.let { template.description = it }
        if (request.metrics != null) {
            template.metrics = objectMapper.writeValueAsString(newMetrics)
            template.metricScope = deriveScope(newMetrics)
        }
        if (request.dimensions != null) {
            template.dimensions = objectMapper.writeValueAsString(newDimensions)
        }
        if (request.filters != null) {
            template.filters = objectMapper.writeValueAsString(request.filters)
        }

        return toResponse(reportTemplateRepository.save(template))
    }

    @Transactional
    fun deleteTemplate(
        publicId: UUID,
        clubId: Long,
    ) {
        val template = findOrThrow(publicId, clubId)

        if (template.isSystem) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "System templates cannot be deleted.",
            )
        }

        template.softDelete()
        reportTemplateRepository.save(template)

        auditService.logFromContext(
            action = AuditAction.REPORT_TEMPLATE_DELETED,
            entityType = "ReportTemplate",
            entityId = publicId.toString(),
        )
    }

    internal fun findOrThrow(
        publicId: UUID,
        clubId: Long,
    ): ReportTemplate =
        reportTemplateRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(publicId, clubId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.NOT_FOUND,
                    "resource-not-found",
                    "Report template not found.",
                )
            }

    private fun validateMetrics(metricCodes: List<String>) {
        if (metricCodes.isEmpty()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "At least one metric is required.",
            )
        }
        if (metricCodes.size > MAX_METRICS) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Maximum $MAX_METRICS metrics allowed.",
            )
        }
        for (code in metricCodes) {
            MetricCatalogue.fromCode(code)
                ?: throw ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Unknown metric: $code",
                )
        }
    }

    private fun validateDimensions(dimensionCodes: List<String>) {
        if (dimensionCodes.isEmpty()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "At least one dimension is required.",
            )
        }
        for (code in dimensionCodes) {
            DimensionCatalogue.fromCode(code)
                ?: throw ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Unknown dimension: $code",
                )
        }
    }

    private fun validateCompatibility(
        metricCodes: List<String>,
        dimensionCodes: List<String>,
    ) {
        val metrics = metricCodes.map { MetricCatalogue.fromCode(it)!! }
        val dimensions = dimensionCodes.map { DimensionCatalogue.fromCode(it)!! }
        val error = CompatibilityMatrix.validateAll(metrics, dimensions)
        if (error != null) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                error,
            )
        }
    }

    private fun deriveScope(metricCodes: List<String>): String? {
        val scopes = metricCodes.mapNotNull { MetricCatalogue.fromCode(it)?.scope }.toSet()
        return if (scopes.size == 1) scopes.first() else null
    }

    private fun toResponse(template: ReportTemplate): ReportTemplateResponse {
        val lastResult =
            reportResultRepository.findFirstByTemplateIdAndDeletedAtIsNullOrderByRunAtDesc(template.id)

        return ReportTemplateResponse(
            id = template.publicId,
            name = template.name,
            description = template.description,
            metrics = objectMapper.readValue(template.metrics),
            dimensions = objectMapper.readValue(template.dimensions),
            filters = template.filters?.let { objectMapper.readValue(it) },
            metricScope = template.metricScope,
            isSystem = template.isSystem,
            lastRunAt = lastResult.map { it.runAt }.orElse(null),
            createdAt = template.createdAt,
        )
    }
}
