package com.liyaqa.zatca

import java.security.MessageDigest
import java.util.Base64

object ZatcaHashUtil {
    fun hashInvoice(
        uuid: String,
        invoiceNumber: String,
        issuedAt: String,
        sellerVatNumber: String,
        totalWithVatSar: String,
        vatAmountSar: String,
    ): String {
        val canonical =
            "$uuid|$invoiceNumber|$issuedAt|" +
                "$sellerVatNumber|$totalWithVatSar|$vatAmountSar"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    // Initial PIH for the very first invoice per club.
    // Verified against zatca-envoice-sdk-203/Data/PIH/pih.txt — exact match.
    // Decodes to SHA-256("0") = 5feceb66ffc86f38d952786c6d696c79c2dbc239dd4e91b46729d73a27fb57e9
    const val INITIAL_PIH =
        "NWZlY2ViNjZmZmM4NmYzOGQ5NTI3ODZjNmQ2OTZjNzljMmRiYzIzOWRkNGU5MWI0" +
            "NjcyOWQ3M2EyN2ZiNTdlOQ=="
}
