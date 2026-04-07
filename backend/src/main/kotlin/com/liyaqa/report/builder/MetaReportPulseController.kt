package com.liyaqa.report.builder

import com.liyaqa.report.builder.dto.DimensionMetaResponse
import com.liyaqa.report.builder.dto.FilterMetaResponse
import com.liyaqa.report.builder.dto.MetricMetaResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reports/meta")
@Tag(name = "Report Builder Meta", description = "Available metrics, dimensions, and filters for the custom report builder")
class MetaReportPulseController {
    @GetMapping("/metrics")
    @Operation(summary = "List available metric codes")
    fun getMetrics(): ResponseEntity<List<MetricMetaResponse>> {
        val metrics =
            MetricCatalogue.all().map {
                MetricMetaResponse(
                    code = it.code,
                    label = it.label,
                    labelAr = it.labelAr,
                    unit = it.unit,
                    scope = it.scope,
                    description = "${it.label} (${it.unit})",
                )
            }
        return ResponseEntity.ok(metrics)
    }

    @GetMapping("/dimensions")
    @Operation(summary = "List available dimension codes")
    fun getDimensions(): ResponseEntity<List<DimensionMetaResponse>> {
        val dimensions =
            DimensionCatalogue.all().map {
                DimensionMetaResponse(
                    code = it.code,
                    label = it.label,
                    labelAr = it.labelAr,
                    compatibleMetricScopes = it.compatibleScopes,
                )
            }
        return ResponseEntity.ok(dimensions)
    }

    @GetMapping("/filters")
    @Operation(summary = "List available filter codes")
    fun getFilters(): ResponseEntity<List<FilterMetaResponse>> {
        val filters =
            FilterCatalogue.all().map {
                FilterMetaResponse(
                    code = it.code,
                    label = it.label,
                    labelAr = it.labelAr,
                )
            }
        return ResponseEntity.ok(filters)
    }
}
