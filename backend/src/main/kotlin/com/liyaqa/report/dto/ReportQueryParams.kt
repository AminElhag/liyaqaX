package com.liyaqa.report.dto

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReportQueryParams(
    val from: LocalDate,
    val to: LocalDate,
    val branchId: Long?,
    val groupBy: GroupByPeriod,
) {
    val daysBetween: Long get() = ChronoUnit.DAYS.between(from, to) + 1

    companion object {
        fun resolveGroupBy(
            from: LocalDate,
            to: LocalDate,
            explicit: String?,
        ): GroupByPeriod {
            if (explicit != null) {
                return GroupByPeriod.entries.firstOrNull { it.name.equals(explicit, ignoreCase = true) }
                    ?: GroupByPeriod.MONTH
            }
            val days = ChronoUnit.DAYS.between(from, to)
            return when {
                days <= 14 -> GroupByPeriod.DAY
                days <= 90 -> GroupByPeriod.WEEK
                else -> GroupByPeriod.MONTH
            }
        }
    }
}

enum class GroupByPeriod {
    DAY,
    WEEK,
    MONTH,
}
