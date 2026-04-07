package com.liyaqa.report.builder

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.report.builder.dto.ReportResultResponse
import com.liyaqa.report.builder.dto.RunReportRequest
import jakarta.persistence.EntityManager
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@Service
@Transactional(readOnly = true)
class ReportBuilderService(
    private val em: EntityManager,
    private val reportResultRepository: ReportResultRepository,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
    private val auditService: AuditService,
) {
    companion object {
        private const val MAX_DATE_RANGE_DAYS = 366L
        private const val MAX_ROWS = 50_000
        private const val CACHE_TTL_MINUTES = 10L
        private const val CACHE_KEY_PREFIX = "report_result"
    }

    @Transactional
    fun runReport(
        template: ReportTemplate,
        request: RunReportRequest,
        clubId: Long,
        actorId: String,
    ): ReportResultResponse {
        validateDateRange(request.dateFrom, request.dateTo)

        val paramHash = computeParamHash(template.id, request)
        val cacheKey = "$CACHE_KEY_PREFIX:${template.id}:$paramHash"

        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            val response: ReportResultResponse = objectMapper.readValue(cached)
            return response.copy(fromCache = true)
        }

        val metrics = objectMapper.readValue<List<String>>(template.metrics).map { MetricCatalogue.fromCode(it)!! }
        val dimensions = objectMapper.readValue<List<String>>(template.dimensions).map { DimensionCatalogue.fromCode(it)!! }
        val runtimeFilters = mergeFilters(template.filters, request.filters)

        val (sql, params) = buildQuery(metrics, dimensions, runtimeFilters, clubId, request.dateFrom, request.dateTo)

        @Suppress("UNCHECKED_CAST")
        val query = em.createNativeQuery(sql)
        params.forEach { (name, value) -> query.setParameter(name, value) }

        @Suppress("UNCHECKED_CAST")
        val rawRows = query.resultList as List<Array<*>>

        val columns = buildColumnNames(dimensions, metrics)
        val rows =
            rawRows.map { row ->
                columns.mapIndexed { i, col -> col to row[i] }.toMap()
            }

        val truncated = rows.size >= MAX_ROWS
        val rowCount = rows.size

        val resultJson = objectMapper.writeValueAsString(rows)
        val response =
            ReportResultResponse(
                templateId = template.publicId,
                runAt = java.time.Instant.now().toString(),
                dateFrom = request.dateFrom.toString(),
                dateTo = request.dateTo.toString(),
                columns = columns,
                rows = rows,
                rowCount = rowCount,
                truncated = truncated,
                fromCache = false,
            )

        reportResultRepository.softDeleteAllByTemplateId(template.id)
        reportResultRepository.save(
            ReportResult(
                templateId = template.id,
                runByUserId = actorId,
                dateFrom = request.dateFrom,
                dateTo = request.dateTo,
                resultJson = resultJson,
                rowCount = rowCount,
                truncated = truncated,
                runParamsHash = paramHash,
            ),
        )

        redisTemplate.opsForValue().set(
            cacheKey,
            objectMapper.writeValueAsString(response),
            CACHE_TTL_MINUTES,
            TimeUnit.MINUTES,
        )

        auditService.logFromContext(
            action = AuditAction.REPORT_RUN,
            entityType = "ReportTemplate",
            entityId = template.publicId.toString(),
        )

        return response
    }

    fun getLastResult(template: ReportTemplate): ReportResultResponse {
        val result =
            reportResultRepository.findFirstByTemplateIdAndDeletedAtIsNullOrderByRunAtDesc(template.id)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.NOT_FOUND,
                        "resource-not-found",
                        "No result available. Run the report first.",
                    )
                }

        val rows: List<Map<String, Any?>> = objectMapper.readValue(result.resultJson)
        val metrics = objectMapper.readValue<List<String>>(template.metrics).map { MetricCatalogue.fromCode(it)!! }
        val dimensions = objectMapper.readValue<List<String>>(template.dimensions).map { DimensionCatalogue.fromCode(it)!! }
        val columns = buildColumnNames(dimensions, metrics)

        return ReportResultResponse(
            templateId = template.publicId,
            runAt = result.runAt.toString(),
            dateFrom = result.dateFrom.toString(),
            dateTo = result.dateTo.toString(),
            columns = columns,
            rows = rows,
            rowCount = result.rowCount,
            truncated = result.truncated,
            fromCache = false,
        )
    }

    fun exportCsv(template: ReportTemplate): ByteArray {
        val result =
            reportResultRepository.findFirstByTemplateIdAndDeletedAtIsNullOrderByRunAtDesc(template.id)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.NOT_FOUND,
                        "resource-not-found",
                        "No result available. Run the report first.",
                    )
                }

        val rows: List<Map<String, Any?>> = objectMapper.readValue(result.resultJson)
        val metrics = objectMapper.readValue<List<String>>(template.metrics).map { MetricCatalogue.fromCode(it)!! }
        val dimensions = objectMapper.readValue<List<String>>(template.dimensions).map { DimensionCatalogue.fromCode(it)!! }
        val columns = buildColumnNames(dimensions, metrics)

        val sb = StringBuilder()
        sb.appendLine(columns.joinToString(","))
        for (row in rows) {
            sb.appendLine(columns.joinToString(",") { row[it]?.toString() ?: "" })
        }

        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        return bom + sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun validateDateRange(
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ) {
        if (dateFrom.isAfter(dateTo)) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "'dateFrom' cannot be after 'dateTo'.",
            )
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) > MAX_DATE_RANGE_DAYS) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Date range cannot exceed $MAX_DATE_RANGE_DAYS days.",
            )
        }
    }

    private fun mergeFilters(
        templateFilters: String?,
        runtimeFilters: Map<String, String?>?,
    ): Map<String, String> {
        val base: Map<String, String?> =
            if (templateFilters != null) objectMapper.readValue(templateFilters) else emptyMap()
        val overrides = runtimeFilters ?: emptyMap()
        val merged = base + overrides
        return merged.filterValues { it != null }.mapValues { it.value!! }
    }

    @Suppress("SqlResolve")
    private fun buildQuery(
        metrics: List<MetricCatalogue>,
        dimensions: List<DimensionCatalogue>,
        filters: Map<String, String>,
        clubId: Long,
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ): Pair<String, Map<String, Any>> {
        val primaryTable = metrics.first().sourceTable
        val primaryAlias = primaryTable.split(" ").last()
        val dateColumn = resolveDateColumn(primaryAlias)

        val selectParts = mutableListOf<String>()
        val groupByParts = mutableListOf<String>()
        val joinParts = mutableListOf<String>()
        val whereParts = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        for (dim in dimensions) {
            selectParts.add("${dim.sqlSelectFragment.replace("{date_column}", dateColumn)} AS ${dim.code}")
            groupByParts.add(dim.sqlGroupByFragment.replace("{date_column}", dateColumn))
            dim.joinFragment?.let {
                joinParts.add(it.replace("{table}", primaryAlias))
            }
        }

        for (metric in metrics) {
            selectParts.add("${metric.sqlFragment} AS ${metric.code}")
        }

        val clubIdExpression = resolveClubIdExpression(primaryAlias, joinParts)
        whereParts.add("$clubIdExpression = :clubId")
        params["clubId"] = clubId

        whereParts.add("CAST($dateColumn AS DATE) >= :dateFrom")
        params["dateFrom"] = dateFrom

        whereParts.add("CAST($dateColumn AS DATE) <= :dateTo")
        params["dateTo"] = dateTo

        if (hasDeletedAt(primaryAlias)) {
            whereParts.add("$primaryAlias.deleted_at IS NULL")
        }

        for ((filterCode, filterValue) in filters) {
            val filter = FilterCatalogue.fromCode(filterCode) ?: continue
            whereParts.add(filter.sqlWhereFragment.replace("{table}", primaryAlias))
            params[filter.parameterName] = filterValue.toLong()
        }

        val sql =
            buildString {
                append("SELECT ")
                append(selectParts.joinToString(", "))
                append(" FROM $primaryTable")
                for (join in joinParts.distinct()) {
                    append(" $join")
                }
                append(" WHERE ")
                append(whereParts.joinToString(" AND "))
                append(" GROUP BY ")
                append(groupByParts.joinToString(", "))
                append(" ORDER BY 1")
                append(" LIMIT $MAX_ROWS")
            }

        return sql to params
    }

    private fun resolveDateColumn(alias: String): String =
        when (alias) {
            "p" -> "p.paid_at"
            "m" -> "m.created_at"
            "ms" -> "ms.created_at"
            "gb" -> "gb.booked_at"
            "ps" -> "ps.scheduled_at"
            "l" -> "l.created_at"
            "cde" -> "cde.recorded_at"
            else -> "$alias.created_at"
        }

    private fun hasDeletedAt(alias: String): Boolean =
        when (alias) {
            "p", "gb", "cde" -> false
            else -> true
        }

    private fun resolveClubIdExpression(
        alias: String,
        joinParts: MutableList<String>,
    ): String =
        when (alias) {
            "cde" -> {
                joinParts.add("JOIN cash_drawer_sessions cds ON cds.id = cde.session_id")
                "cds.club_id"
            }
            else -> "$alias.club_id"
        }

    private fun buildColumnNames(
        dimensions: List<DimensionCatalogue>,
        metrics: List<MetricCatalogue>,
    ): List<String> = dimensions.map { it.code } + metrics.map { it.code }

    private fun computeParamHash(
        templateId: Long,
        request: RunReportRequest,
    ): String {
        val raw = "$templateId:${request.dateFrom}:${request.dateTo}:${objectMapper.writeValueAsString(
            request.filters ?: emptyMap<String, String>(),
        )}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
