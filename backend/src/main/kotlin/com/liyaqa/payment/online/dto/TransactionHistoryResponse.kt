package com.liyaqa.payment.online.dto

import java.time.Instant
import java.util.UUID

data class TransactionHistoryResponse(
    val transactions: List<TransactionItem>,
)

data class TransactionItem(
    val transactionId: UUID,
    val moyasarId: String,
    val planName: String,
    val amountSar: String,
    val status: String,
    val paymentMethod: String?,
    val createdAt: Instant,
)
