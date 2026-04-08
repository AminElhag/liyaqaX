package com.liyaqa.zatca

import com.liyaqa.club.Club
import com.liyaqa.organization.Organization
import org.springframework.stereotype.Service

@Service
class ZatcaQrService {
    fun resolveVatNumber(
        club: Club,
        organization: Organization,
    ): String =
        club.vatNumber
            ?: organization.vatNumber
            ?: "PENDING-VAT-REGISTRATION"

    fun resolveSellerName(organization: Organization): String = organization.nameAr

    fun formatSarAmount(halalas: Long): String = "%.2f".format(halalas / 100.0)

    fun generateQrCode(
        sellerName: String,
        vatNumber: String,
        timestamp: String,
        totalWithVatSar: String,
        vatAmountSar: String,
    ): String =
        ZatcaTlvEncoder.generateQrCode(
            sellerName = sellerName,
            vatNumber = vatNumber,
            timestamp = timestamp,
            totalWithVatSar = totalWithVatSar,
            vatAmountSar = vatAmountSar,
        )

    fun generateHash(
        uuid: String,
        invoiceNumber: String,
        issuedAt: String,
        sellerVatNumber: String,
        totalWithVatSar: String,
        vatAmountSar: String,
    ): String =
        ZatcaHashUtil.hashInvoice(
            uuid = uuid,
            invoiceNumber = invoiceNumber,
            issuedAt = issuedAt,
            sellerVatNumber = sellerVatNumber,
            totalWithVatSar = totalWithVatSar,
            vatAmountSar = vatAmountSar,
        )
}
