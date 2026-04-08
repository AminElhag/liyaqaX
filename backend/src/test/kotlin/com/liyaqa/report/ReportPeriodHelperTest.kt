package com.liyaqa.report

import com.liyaqa.report.dto.GroupByPeriod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReportPeriodHelperTest {
    @Test
    fun `generatePeriods DAY returns one period per day`() {
        val periods =
            ReportPeriodHelper.generatePeriods(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 3),
                GroupByPeriod.DAY,
            )
        assertThat(periods).hasSize(3)
        assertThat(periods[0].start).isEqualTo(LocalDate.of(2025, 1, 1))
        assertThat(periods[0].end).isEqualTo(LocalDate.of(2025, 1, 1))
        assertThat(periods[2].start).isEqualTo(LocalDate.of(2025, 1, 3))
    }

    @Test
    fun `generatePeriods WEEK generates week-sized periods`() {
        val periods =
            ReportPeriodHelper.generatePeriods(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 21),
                GroupByPeriod.WEEK,
            )
        assertThat(periods).hasSizeGreaterThanOrEqualTo(3)
        for (period in periods) {
            assertThat(period.start).isBeforeOrEqualTo(period.end)
        }
    }

    @Test
    fun `generatePeriods MONTH generates month-sized periods`() {
        val periods =
            ReportPeriodHelper.generatePeriods(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 3, 31),
                GroupByPeriod.MONTH,
            )
        assertThat(periods).hasSize(3)
        assertThat(periods[0].label).contains("Jan")
        assertThat(periods[1].label).contains("Feb")
        assertThat(periods[2].label).contains("Mar")
    }

    @Test
    fun `comparisonPeriod returns equal-length preceding period`() {
        val (compFrom, compTo) =
            ReportPeriodHelper.comparisonPeriod(
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28),
            )
        assertThat(compTo).isEqualTo(LocalDate.of(2025, 1, 31))
        val originalDays =
            java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28),
            )
        val compDays = java.time.temporal.ChronoUnit.DAYS.between(compFrom, compTo)
        assertThat(compDays).isEqualTo(originalDays)
    }

    @Test
    fun `single day range returns single period`() {
        val periods =
            ReportPeriodHelper.generatePeriods(
                LocalDate.of(2025, 3, 15),
                LocalDate.of(2025, 3, 15),
                GroupByPeriod.DAY,
            )
        assertThat(periods).hasSize(1)
    }
}
