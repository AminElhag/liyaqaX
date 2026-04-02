package com.arena.common.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProblemDetailResponse(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String? = null,
    val errors: List<FieldError>? = null,
)

data class FieldError(
    val field: String,
    val code: String,
    val message: String,
)
