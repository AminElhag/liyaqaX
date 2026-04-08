package com.liyaqa.invoice

import java.time.Instant
import java.util.UUID

interface FailedZatcaInvoiceProjection {
    val invoicePublicId: UUID
    val invoiceNumber: String?
    val clubName: String
    val memberName: String
    val amountHalalas: Long
    val createdAt: Instant
    val zatcaRetryCount: Int
    val zatcaLastError: String?
    val zatcaStatus: String
}
