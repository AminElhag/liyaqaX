package com.liyaqa.checkin.dto

import java.time.LocalDate

data class TodayCountResponse(
    val count: Long,
    val branchName: String,
    val date: LocalDate,
)
