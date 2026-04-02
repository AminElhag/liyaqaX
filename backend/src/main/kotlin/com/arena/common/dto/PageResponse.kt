package com.arena.common.dto

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
