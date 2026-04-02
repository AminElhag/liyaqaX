package com.liyaqa.common.dto

data class PageResponse<T>(
    val items: List<T> = emptyList(),
)
