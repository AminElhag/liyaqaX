package com.liyaqa.payment

import java.time.Instant
import java.util.UUID

interface PaymentTimelineProjection {
    fun getId(): Long

    fun getPublicId(): UUID

    fun getAmountHalalas(): Long

    fun getPaymentMethod(): String

    fun getPaidAt(): Instant

    fun getCreatedAt(): Instant
}
