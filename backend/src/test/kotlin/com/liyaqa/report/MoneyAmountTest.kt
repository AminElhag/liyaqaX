package com.liyaqa.report

import com.liyaqa.report.dto.MoneyAmount
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MoneyAmountTest {
    @Test
    fun `of converts halalas to SAR string`() {
        val amount = MoneyAmount.of(125050)
        assertThat(amount.halalas).isEqualTo(125050)
        assertThat(amount.sar).isEqualTo("1250.50")
    }

    @Test
    fun `of handles zero`() {
        val amount = MoneyAmount.of(0)
        assertThat(amount.halalas).isEqualTo(0)
        assertThat(amount.sar).isEqualTo("0.00")
    }

    @Test
    fun `zero factory method returns zero amount`() {
        val amount = MoneyAmount.zero()
        assertThat(amount.halalas).isEqualTo(0)
        assertThat(amount.sar).isEqualTo("0.00")
    }

    @Test
    fun `of formats with two decimal places`() {
        val amount = MoneyAmount.of(100)
        assertThat(amount.sar).isEqualTo("1.00")
    }

    @Test
    fun `of handles fractional halalas correctly`() {
        val amount = MoneyAmount.of(1)
        assertThat(amount.sar).isEqualTo("0.01")
    }
}
