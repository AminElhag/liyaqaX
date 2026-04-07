package com.liyaqa.zatca.service

import org.springframework.stereotype.Service
import java.security.KeyPair
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

@Service
class ZatcaXmlService(
    private val cryptoService: ZatcaCryptoService,
) {
    fun signInvoiceXml(
        invoiceXml: String,
        invoiceHash: String,
        privateKeyBase64: String,
        certificatePem: String,
    ): String {
        val hashBytes = Base64.getDecoder().decode(invoiceHash)
        val signatureBytes = cryptoService.signData(privateKeyBase64, hashBytes)
        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes)

        val certClean =
            certificatePem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\n", "")
                .replace("\r", "")

        val signedXml =
            invoiceXml
                .replace("{{SIGNATURE_VALUE}}", signatureBase64)
                .replace("{{CERTIFICATE}}", certClean)

        return Base64.getEncoder().encodeToString(signedXml.toByteArray())
    }

    fun generateComplianceInvoices(
        vatNumber: String,
        sellerName: String,
        keyPair: KeyPair,
        privateKeyBase64: String,
    ): List<Triple<String, String, String>> =
        listOf(
            generateComplianceInvoice(vatNumber, sellerName, "388", keyPair, privateKeyBase64),
            generateComplianceInvoice(vatNumber, sellerName, "383", keyPair, privateKeyBase64),
            generateComplianceInvoice(vatNumber, sellerName, "381", keyPair, privateKeyBase64),
        )

    private fun generateComplianceInvoice(
        vatNumber: String,
        sellerName: String,
        invoiceTypeCode: String,
        keyPair: KeyPair,
        privateKeyBase64: String,
    ): Triple<String, String, String> {
        val uuid = UUID.randomUUID().toString()
        val now = ZonedDateTime.now()
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        val xml =
            buildMinimalComplianceXml(
                uuid = uuid,
                invoiceTypeCode = invoiceTypeCode,
                issueDate = dateStr,
                issueTime = timeStr,
                vatNumber = vatNumber,
                sellerName = sellerName,
                subtotalHalalas = 10000L,
                vatHalalas = 1500L,
                totalHalalas = 11500L,
            )

        val xmlBytes = xml.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(xmlBytes)
        val hashBase64 = Base64.getEncoder().encodeToString(hashBytes)
        val xmlBase64 = Base64.getEncoder().encodeToString(xmlBytes)

        return Triple(hashBase64, uuid, xmlBase64)
    }

    @Suppress("LongParameterList")
    private fun buildMinimalComplianceXml(
        uuid: String,
        invoiceTypeCode: String,
        issueDate: String,
        issueTime: String,
        vatNumber: String,
        sellerName: String,
        subtotalHalalas: Long,
        vatHalalas: Long,
        totalHalalas: Long,
    ): String {
        val subtotalSar = "%.2f".format(subtotalHalalas / 100.0)
        val vatSar = "%.2f".format(vatHalalas / 100.0)
        val totalSar = "%.2f".format(totalHalalas / 100.0)

        return """<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
         xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2"
         xmlns:xades="http://uri.etsi.org/01903/v1.3.2#"
         xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
  <cbc:ProfileID>reporting:1.0</cbc:ProfileID>
  <cbc:ID>COMPLIANCE-$uuid</cbc:ID>
  <cbc:UUID>$uuid</cbc:UUID>
  <cbc:IssueDate>$issueDate</cbc:IssueDate>
  <cbc:IssueTime>$issueTime</cbc:IssueTime>
  <cbc:InvoiceTypeCode name="0200000">$invoiceTypeCode</cbc:InvoiceTypeCode>
  <cbc:DocumentCurrencyCode>SAR</cbc:DocumentCurrencyCode>
  <cbc:TaxCurrencyCode>SAR</cbc:TaxCurrencyCode>
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cac:PartyIdentification>
        <cbc:ID schemeID="CRN">$vatNumber</cbc:ID>
      </cac:PartyIdentification>
      <cac:PostalAddress>
        <cbc:StreetName>Main Street</cbc:StreetName>
        <cbc:CityName>Riyadh</cbc:CityName>
        <cac:Country><cbc:IdentificationCode>SA</cbc:IdentificationCode></cac:Country>
      </cac:PostalAddress>
      <cac:PartyTaxScheme>
        <cbc:CompanyID>$vatNumber</cbc:CompanyID>
        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
      </cac:PartyTaxScheme>
      <cac:PartyLegalEntity><cbc:RegistrationName>$sellerName</cbc:RegistrationName></cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="SAR">$vatSar</cbc:TaxAmount>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="SAR">$subtotalSar</cbc:LineExtensionAmount>
    <cbc:TaxExclusiveAmount currencyID="SAR">$subtotalSar</cbc:TaxExclusiveAmount>
    <cbc:TaxInclusiveAmount currencyID="SAR">$totalSar</cbc:TaxInclusiveAmount>
    <cbc:PayableAmount currencyID="SAR">$totalSar</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
</Invoice>"""
    }
}
