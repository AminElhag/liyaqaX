package com.liyaqa.report.builder.dto

data class DimensionMetaResponse(
    val code: String,
    val label: String,
    val labelAr: String,
    val compatibleMetricScopes: Set<String>,
)
