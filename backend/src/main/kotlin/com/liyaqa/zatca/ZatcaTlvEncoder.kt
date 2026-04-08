package com.liyaqa.zatca

import java.util.Base64

object ZatcaTlvEncoder {
    private const val TAG_SELLER_NAME: Byte = 0x01
    private const val TAG_VAT_NUMBER: Byte = 0x02
    private const val TAG_TIMESTAMP: Byte = 0x03
    private const val TAG_TOTAL_WITH_VAT: Byte = 0x04
    private const val TAG_VAT_AMOUNT: Byte = 0x05

    private fun encodeTlv(
        tag: Byte,
        value: String,
    ): ByteArray {
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        require(valueBytes.size <= 255) {
            "TLV value too long for tag $tag: ${valueBytes.size} bytes"
        }
        return byteArrayOf(tag, valueBytes.size.toByte()) + valueBytes
    }

    fun generateQrCode(
        sellerName: String,
        vatNumber: String,
        timestamp: String,
        totalWithVatSar: String,
        vatAmountSar: String,
    ): String {
        val tlvBytes =
            encodeTlv(TAG_SELLER_NAME, sellerName) +
                encodeTlv(TAG_VAT_NUMBER, vatNumber) +
                encodeTlv(TAG_TIMESTAMP, timestamp) +
                encodeTlv(TAG_TOTAL_WITH_VAT, totalWithVatSar) +
                encodeTlv(TAG_VAT_AMOUNT, vatAmountSar)
        return Base64.getEncoder().encodeToString(tlvBytes)
    }
}
