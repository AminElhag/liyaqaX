package com.liyaqa.payment.online.repository

import java.time.Instant
import java.util.UUID

interface TransactionHistoryProjection {
    fun getPublicId(): UUID
    fun getMoyasarId(): String
    fun getPlanNameEn(): String?
    fun getPlanNameAr(): String?
    fun getAmountHalalas(): Long
    fun getStatus(): String
    fun getPaymentMethod(): String?
    fun getCreatedAt(): Instant
}
