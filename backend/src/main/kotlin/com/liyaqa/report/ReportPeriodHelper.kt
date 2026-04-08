package com.liyaqa.report

import com.liyaqa.report.dto.GroupByPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class PeriodRange(
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
)

object ReportPeriodHelper {
    private val DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy")

    fun generatePeriods(
        from: LocalDate,
        to: LocalDate,
        groupBy: GroupByPeriod,
    ): List<PeriodRange> =
        when (groupBy) {
            GroupByPeriod.DAY -> generateDayPeriods(from, to)
            GroupByPeriod.WEEK -> generateWeekPeriods(from, to)
            GroupByPeriod.MONTH -> generateMonthPeriods(from, to)
        }

    private fun generateDayPeriods(
        from: LocalDate,
        to: LocalDate,
    ): List<PeriodRange> {
        val periods = mutableListOf<PeriodRange>()
        var current = from
        while (!current.isAfter(to)) {
            periods.add(PeriodRange(current.format(DAY_FMT), current, current))
            current = current.plusDays(1)
        }
        return periods
    }

    private fun generateWeekPeriods(
        from: LocalDate,
        to: LocalDate,
    ): List<PeriodRange> {
        val periods = mutableListOf<PeriodRange>()
        var weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (weekStart.isBefore(from)) weekStart = from
        while (!weekStart.isAfter(to)) {
            val weekEnd = minOf(weekStart.plusDays(6), to)
            val label = "${weekStart.format(DAY_FMT)} - ${weekEnd.format(DAY_FMT)}"
            periods.add(PeriodRange(label, weekStart, weekEnd))
            weekStart = weekEnd.plusDays(1)
        }
        return periods
    }

    private fun generateMonthPeriods(
        from: LocalDate,
        to: LocalDate,
    ): List<PeriodRange> {
        val periods = mutableListOf<PeriodRange>()
        var monthStart = from.withDayOfMonth(1)
        if (monthStart.isBefore(from)) monthStart = from
        while (!monthStart.isAfter(to)) {
            val monthEnd = minOf(monthStart.withDayOfMonth(monthStart.lengthOfMonth()), to)
            val label = monthStart.withDayOfMonth(1).format(MONTH_FMT)
            periods.add(PeriodRange(label, monthStart, monthEnd))
            monthStart = monthEnd.plusDays(1)
        }
        return periods
    }

    fun comparisonPeriod(
        from: LocalDate,
        to: LocalDate,
    ): Pair<LocalDate, LocalDate> {
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to)
        val compTo = from.minusDays(1)
        val compFrom = compTo.minusDays(days)
        return compFrom to compTo
    }
}
