package com.liyaqa.membership

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface MembershipTimelineProjection {
    fun getId(): Long

    fun getPublicId(): UUID

    fun getStatus(): String

    fun getStartDate(): LocalDate

    fun getEndDate(): LocalDate

    fun getCreatedAt(): Instant

    fun getUpdatedAt(): Instant

    fun getPlanNameEn(): String

    fun getPlanNameAr(): String
}
