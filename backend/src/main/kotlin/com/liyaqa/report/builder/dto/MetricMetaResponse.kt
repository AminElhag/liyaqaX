package com.liyaqa.report.builder.dto

data class MetricMetaResponse(
    val code: String,
    val label: String,
    val labelAr: String,
    val unit: String,
    val scope: String,
    val description: String,
)
