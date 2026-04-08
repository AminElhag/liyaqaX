package com.liyaqa.report

import com.liyaqa.report.dto.GroupByPeriod
import com.liyaqa.report.dto.ReportQueryParams
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReportQueryParamsTest {
    @Test
    fun `auto groupBy selects DAY when range is 14 days or less`() {
        val result =
            ReportQueryParams.resolveGroupBy(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 14),
                null,
            )
        assertThat(result).isEqualTo(GroupByPeriod.DAY)
    }

    @Test
    fun `auto groupBy selects DAY for single day range`() {
        val date = LocalDate.of(2025, 3, 15)
        val result = ReportQueryParams.resolveGroupBy(date, date, null)
        assertThat(result).isEqualTo(GroupByPeriod.DAY)
    }

    @Test
    fun `auto groupBy selects WEEK when range is 15 to 90 days`() {
        val result =
            ReportQueryParams.resolveGroupBy(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 3, 1),
                null,
            )
        assertThat(result).isEqualTo(GroupByPeriod.WEEK)
    }

    @Test
    fun `auto groupBy selects MONTH when range exceeds 90 days`() {
        val result =
            ReportQueryParams.resolveGroupBy(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 6, 30),
                null,
            )
        assertThat(result).isEqualTo(GroupByPeriod.MONTH)
    }

    @Test
    fun `explicit groupBy overrides auto selection`() {
        val result =
            ReportQueryParams.resolveGroupBy(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 5),
                "month",
            )
        assertThat(result).isEqualTo(GroupByPeriod.MONTH)
    }

    @Test
    fun `explicit groupBy is case insensitive`() {
        val result =
            ReportQueryParams.resolveGroupBy(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 5),
                "WEEK",
            )
        assertThat(result).isEqualTo(GroupByPeriod.WEEK)
    }

    @Test
    fun `invalid explicit groupBy falls back to MONTH`() {
        val result =
            ReportQueryParams.resolveGroupBy(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 5),
                "invalid",
            )
        assertThat(result).isEqualTo(GroupByPeriod.MONTH)
    }

    @Test
    fun `boundary at exactly 90 days selects WEEK`() {
        val result =
            ReportQueryParams.resolveGroupBy(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 4, 1),
                null,
            )
        assertThat(result).isEqualTo(GroupByPeriod.WEEK)
    }

    @Test
    fun `boundary at 91 days selects MONTH`() {
        val result =
            ReportQueryParams.resolveGroupBy(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 4, 2),
                null,
            )
        assertThat(result).isEqualTo(GroupByPeriod.MONTH)
    }
}
