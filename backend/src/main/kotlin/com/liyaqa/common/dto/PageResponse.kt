package com.liyaqa.common.dto

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val items: List<T>,
    val pagination: PaginationMeta,
)

data class PaginationMeta(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

fun <T> Page<T>.toPageResponse(): PageResponse<T> =
    PageResponse(
        items = content,
        pagination =
            PaginationMeta(
                page = number,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = hasNext(),
                hasPrevious = hasPrevious(),
            ),
    )
