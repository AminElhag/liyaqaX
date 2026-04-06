package com.liyaqa.report.dto

data class MoneyAmount(
    val halalas: Long,
    val sar: String,
) {
    companion object {
        fun of(halalas: Long): MoneyAmount = MoneyAmount(halalas = halalas, sar = "%.2f".format(halalas / 100.0))

        fun zero(): MoneyAmount = of(0)
    }
}
