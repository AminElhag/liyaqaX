package com.liyaqa.zatca.service

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.zatca.client.ZatcaApiClient
import com.liyaqa.zatca.entity.ClubZatcaCertificate
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.KeyPair
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class ZatcaOnboardingService(
    private val clubRepository: ClubRepository,
    private val certRepository: ClubZatcaCertificateRepository,
    private val cryptoService: ZatcaCryptoService,
    private val encryptionService: ZatcaEncryptionService,
    private val xmlService: ZatcaXmlService,
    private val apiClient: ZatcaApiClient,
    @Value("\${zatca.environment}") private val environment: String,
) {
    @Transactional
    fun onboardClub(
        clubPublicId: UUID,
        otp: String,
    ) {
        // 1. Look up club
        val club =
            clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found") }

        // 2. Prevent double onboarding
        certRepository.findByClubIdAndDeletedAtIsNull(club.id).ifPresent { existing ->
            if (existing.onboardingStatus == "active") {
                throw ArenaException(HttpStatus.CONFLICT, "conflict", "Club already has an active CSID")
            }
        }

        val vatNumber =
            club.vatNumber
                ?: throw ArenaException(HttpStatus.BAD_REQUEST, "validation-failed", "Club VAT number is required")

        // 3. Generate ECDSA secp256k1 key pair
        val keyPair = cryptoService.generateKeyPair()
        val privateKeyBase64 = cryptoService.exportPrivateKeyBase64(keyPair)
        val privateKeyEncrypted = encryptionService.encrypt(privateKeyBase64)

        // 4. Build EGS serial number
        val egsSerialNumber = "1-Liyaqa|2-$vatNumber|3-gym-${club.publicId}"

        // 5. Build CSR
        val csrPem =
            cryptoService.buildCsr(
                keyPair = keyPair,
                vatNumber = vatNumber,
                egsSerialNumber = egsSerialNumber,
                organizationName = club.nameEn,
            )
        val csrBase64 = Base64.getEncoder().encodeToString(csrPem.toByteArray())

        // 6. Save preliminary record
        val cert =
            certRepository.findByClubIdAndDeletedAtIsNull(club.id).orElse(
                ClubZatcaCertificate(clubId = club.id, environment = environment),
            ).apply {
                this.csrPem = csrPem
                this.privateKeyEncrypted = privateKeyEncrypted
                this.onboardingStatus = "pending"
            }
        certRepository.save(cert)

        // 7. Call /compliance — issue Compliance CSID
        val complianceResponse = apiClient.issuanceComplianceCsid(csrBase64, otp)
        val complianceRequestId = complianceResponse.get("requestID").asText()
        val complianceBinaryToken = complianceResponse.get("binarySecurityToken").asText()
        val complianceSecret = complianceResponse.get("secret").asText()

        // 8. Save compliance tokens
        cert.complianceRequestId = complianceRequestId
        cert.complianceBinaryToken = complianceBinaryToken
        cert.complianceSecret = complianceSecret
        cert.onboardingStatus = "compliance_issued"
        certRepository.save(cert)

        // 9. Run 3 compliance invoice checks
        runComplianceChecks(club.vatNumber ?: vatNumber, club.nameEn, complianceBinaryToken, complianceSecret, keyPair, privateKeyBase64)

        // 10. Update status to compliance_checked
        cert.onboardingStatus = "compliance_checked"
        certRepository.save(cert)

        // 11. Call /production/csids — issue Production CSID
        val productionResponse =
            apiClient.issuanceProductionCsid(
                complianceBinaryToken = complianceBinaryToken,
                complianceSecret = complianceSecret,
                complianceRequestId = complianceRequestId,
            )

        val productionBinaryToken = productionResponse.get("binarySecurityToken").asText()
        val productionSecret = productionResponse.get("secret").asText()
        val productionRequestId = productionResponse.get("requestID").asText()

        // 12. Parse certificate, save production tokens, set active
        val certPem = String(Base64.getDecoder().decode(productionBinaryToken))
        val expiryDate = parseCertificateExpiry(certPem)

        cert.productionRequestId = productionRequestId
        cert.productionBinaryToken = productionBinaryToken
        cert.productionSecret = productionSecret
        cert.certificatePem = certPem
        cert.csidExpiresAt = expiryDate
        cert.onboardingStatus = "active"
        certRepository.save(cert)
    }

    @Transactional
    fun renewClubCsid(
        clubPublicId: UUID,
        otp: String,
    ) {
        val club =
            clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found") }

        val cert =
            certRepository.findByClubIdAndDeletedAtIsNull(club.id)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "No CSID found for club") }

        val vatNumber =
            club.vatNumber
                ?: throw ArenaException(HttpStatus.BAD_REQUEST, "validation-failed", "Club VAT number is required")

        val newKeyPair = cryptoService.generateKeyPair()
        val newPrivateKeyBase64 = cryptoService.exportPrivateKeyBase64(newKeyPair)
        val egsSerialNumber = "1-Liyaqa|2-$vatNumber|3-gym-${club.publicId}"
        val csrPem = cryptoService.buildCsr(newKeyPair, vatNumber, egsSerialNumber, club.nameEn)
        val csrBase64 = Base64.getEncoder().encodeToString(csrPem.toByteArray())

        val renewalResponse =
            apiClient.renewProductionCsid(
                complianceBinaryToken = cert.complianceBinaryToken!!,
                complianceSecret = cert.complianceSecret!!,
                otp = otp,
                csrBase64 = csrBase64,
            )

        val newBinaryToken = renewalResponse.get("binarySecurityToken").asText()
        val newSecret = renewalResponse.get("secret").asText()
        val certPem = String(Base64.getDecoder().decode(newBinaryToken))

        cert.privateKeyEncrypted = encryptionService.encrypt(newPrivateKeyBase64)
        cert.csrPem = csrPem
        cert.productionBinaryToken = newBinaryToken
        cert.productionSecret = newSecret
        cert.certificatePem = certPem
        cert.csidExpiresAt = parseCertificateExpiry(certPem)
        cert.onboardingStatus = "active"
        certRepository.save(cert)
    }

    private fun runComplianceChecks(
        vatNumber: String,
        sellerName: String,
        binaryToken: String,
        secret: String,
        keyPair: KeyPair,
        privateKeyBase64: String,
    ) {
        val complianceInvoices = xmlService.generateComplianceInvoices(vatNumber, sellerName, keyPair, privateKeyBase64)
        complianceInvoices.forEach { (invoiceHash, uuid, invoiceBase64) ->
            val result = apiClient.complianceInvoiceCheck(binaryToken, secret, invoiceHash, uuid, invoiceBase64)
            val status = result.get("validationResults")?.get("status")?.asText()
            if (status != "PASS") {
                throw ArenaException(
                    HttpStatus.BAD_REQUEST,
                    "integration-error",
                    "ZATCA compliance check failed: ${result.toPrettyString()}",
                )
            }
        }
    }

    private fun parseCertificateExpiry(certPem: String): Instant {
        val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
        val cert =
            certFactory.generateCertificate(certPem.byteInputStream())
                as java.security.cert.X509Certificate
        return cert.notAfter.toInstant()
    }
}
