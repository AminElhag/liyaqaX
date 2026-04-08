package com.liyaqa.member.dto

data class TimelineResponse(
    val events: List<TimelineEvent>,
    val nextCursor: String?,
)
