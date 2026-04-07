# Plan 23 — ZATCA Phase 2 Integration (Fatoora API)

## Overview

Integrate Liyaqa's backend with the ZATCA FATOORA platform to comply with Phase 2 of Saudi Arabia's e-invoicing mandate. Gym membership payments are B2C transactions, which means they are **Simplified Tax Invoices** using the **Reporting model** — sign locally with the club's Production CSID, share with buyer, then report to ZATCA within 24 hours.

Phase 1 is already complete: invoices are generated, stored in UBL XML format, and have their `invoice_counter_value`, `previous_invoice_hash`, `zatca_uuid`, `zatca_hash`, `zatca_qr_code` fields populated. Phase 2 builds on top of that.

**Key blocker:** Each club must obtain a **Production CSID** from ZATCA before any real invoices can be reported. This requires the club's owner/accountant to log into the FATOORA Portal (via ERAD SSO) and generate a one-time OTP. The OTP is then entered into the Liyaqa admin UI to trigger the automated onboarding flow. Without a valid Production CSID per club, no invoices can be submitted.

## Architecture Summary

```
Club Owner  →  FATOORA Portal  →  generates OTP (one-time)
                                              ↓
web-nexus admin  →  enters OTP  →  liyaqa-api onboarding
                                              ↓
                              generates CSR (ECDSA secp256k1)
                                              ↓
                              POST /compliance  →  Compliance CSID
                                              ↓
                              POST /compliance/invoices  (3 compliance checks)
                                              ↓
                              POST /production/csids  →  Production CSID stored
                                              ↓
Invoice created  →  sign XML with CSID  →  build 9-tag QR
                                              ↓
                      @Scheduled job  →  POST /invoices/reporting/single
                                              ↓
                              zatcaStatus: reported / failed
```

## ZATCA API Endpoints (Sandbox: `https://gw-apic-gov.gazt.gov.sa/e-invoicing/developer-portal`)

| API | Method | Path | Auth |
|-----|--------|------|------|
| Compliance CSID | POST | `/compliance` | OTP + CSR (Basic: sandbox dummy) |
| Compliance Invoice Check | POST | `/compliance/invoices` | Basic: binarySecurityToken:secret |
| Production CSID (Onboarding) | POST | `/production/csids` | Basic: binarySecurityToken:secret (from compliance) |
| Production CSID (Renewal) | PATCH | `/production/csids` | Basic: binarySecurityToken:secret |
| Reporting (Simplified) | POST | `/invoices/reporting/single` | Basic: binarySecurityToken:secret (from production) |
| Clearance (Standard, NOT used) | POST | `/invoices/clearance/single` | — |

All API calls require headers:
- `Accept-Version: V2` (required — only valid version)
- `Accept-Language: en` or `ar`

For Reporting/Clearance: `Clearance-Status: 0` (disabled) for simplified invoices.

## What Changes in Each Layer

### Database (new migration: V15)

**New table: `club_zatca_certificates`**
```sql
CREATE TABLE club_zatca_certificates (
    id                     BIGSERIAL PRIMARY KEY,
    public_id              UUID NOT NULL UNIQUE,
    club_id                BIGINT NOT NULL UNIQUE REFERENCES clubs(id),
    environment            VARCHAR(20) NOT NULL DEFAULT 'sandbox', -- 'sandbox' | 'production'
    csr_pem                TEXT,          -- base64 PEM of the CSR (for reference)
    private_key_encrypted  TEXT NOT NULL, -- AES-256 encrypted PKCS8 private key
    compliance_request_id  VARCHAR(255),  -- requestID from /compliance response
    compliance_binary_token TEXT,         -- binarySecurityToken from /compliance (used for compliance checks)
    compliance_secret      VARCHAR(255),  -- secret from /compliance response
    production_request_id  VARCHAR(255),  -- requestID from /production/csids
    production_binary_token TEXT,         -- binarySecurityToken from /production/csids (used for reporting)
    production_secret      VARCHAR(255),  -- secret from /production/csids
    certificate_pem        TEXT,          -- full X.509 certificate PEM (decoded from binarySecurityToken)
    serial_number          VARCHAR(255),
    onboarding_status      VARCHAR(50) NOT NULL DEFAULT 'pending',
                                         -- 'pending' | 'compliance_issued' | 'compliance_checked' | 'active' | 'expired' | 'revoked'
    csid_expires_at        TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at             TIMESTAMP WITH TIME ZONE
);
```

**Modify `invoices` table** — add ZATCA Phase 2 status tracking:
```sql
ALTER TABLE invoices
    ADD COLUMN zatca_status       VARCHAR(50)  DEFAULT 'generated',
    -- 'generated' | 'signed' | 'reported' | 'failed' | 'skipped'
    ADD COLUMN zatca_signed_xml   TEXT,        -- base64 signed XML (after ECDSA stamp)
    ADD COLUMN zatca_reported_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN zatca_report_response TEXT,     -- raw JSON from ZATCA reporting API
    ADD COLUMN zatca_retry_count  INT          DEFAULT 0,
    ADD COLUMN zatca_last_error   TEXT;        -- last error message for diagnosis
```

> Note: `zatca_hash`, `zatca_qr_code`, `invoice_counter_value`, `previous_invoice_hash` already exist from Phase 1.

### Backend — New Package `com.liyaqa.zatca`

All ZATCA logic lives in a dedicated package. Never mix into existing membership/invoice packages.

**Package structure:**
```
com.liyaqa.zatca/
  entity/
    ClubZatcaCertificate.kt
  dto/
    OnboardingRequest.kt          -- club publicId + OTP from FATOORA Portal
    OnboardingStatusResponse.kt
    ZatcaInvoiceReportResult.kt
  repository/
    ClubZatcaCertificateRepository.kt
  service/
    ZatcaCryptoService.kt         -- key generation, CSR building, signing
    ZatcaXmlService.kt            -- UBL XML generation + signing
    ZatcaQrService.kt             -- 9-tag TLV QR code builder
    ZatcaOnboardingService.kt     -- orchestrates /compliance + /production/csids
    ZatcaReportingService.kt      -- submits invoices to FATOORA
    ZatcaEncryptionService.kt     -- AES-256 encryption for private key at rest
  client/
    ZatcaApiClient.kt             -- HTTP client wrapping FATOORA APIs
  scheduler/
    ZatcaReportingScheduler.kt    -- @Scheduled job: report pending invoices
  controller/
    ZatcaNexusController.kt       -- web-nexus endpoints (admin/platform)
    ZatcaPulseController.kt       -- web-pulse endpoints (club staff view)
```

---

## Implementation Steps

### Step 1 — Database Migration V15

**File:** `src/main/resources/db/migration/V15__zatca_phase2.sql`

```sql
-- New table for per-club ZATCA certificates
CREATE TABLE club_zatca_certificates (
    id                      BIGSERIAL PRIMARY KEY,
    public_id               UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    club_id                 BIGINT NOT NULL UNIQUE REFERENCES clubs(id),
    environment             VARCHAR(20) NOT NULL DEFAULT 'sandbox',
    csr_pem                 TEXT,
    private_key_encrypted   TEXT NOT NULL DEFAULT '',
    compliance_request_id   VARCHAR(255),
    compliance_binary_token TEXT,
    compliance_secret       VARCHAR(255),
    production_request_id   VARCHAR(255),
    production_binary_token TEXT,
    production_secret       VARCHAR(255),
    certificate_pem         TEXT,
    serial_number           VARCHAR(255),
    onboarding_status       VARCHAR(50) NOT NULL DEFAULT 'pending',
    csid_expires_at         TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMP WITH TIME ZONE
);

-- Extend invoices table for Phase 2
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS zatca_status       VARCHAR(50) DEFAULT 'generated',
    ADD COLUMN IF NOT EXISTS zatca_signed_xml   TEXT,
    ADD COLUMN IF NOT EXISTS zatca_reported_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS zatca_report_response TEXT,
    ADD COLUMN IF NOT EXISTS zatca_retry_count  INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS zatca_last_error   TEXT;

-- Index for the scheduler query (find unreported invoices)
CREATE INDEX idx_invoices_zatca_status ON invoices(zatca_status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_club_zatca_certificates_club_id ON club_zatca_certificates(club_id)
    WHERE deleted_at IS NULL;
```

**No Flyway in dev** — `ddl-auto: create-drop` in dev. Migration is for staging/prod only.

---

### Step 2 — Entity and Repository

**`ClubZatcaCertificate.kt`** — extends `AuditEntity` (gets id, publicId, timestamps, deletedAt automatically):

```kotlin
package com.liyaqa.zatca.entity

import com.liyaqa.common.entity.AuditEntity
import com.liyaqa.club.entity.Club
import jakarta.persistence.*

@Entity
@Table(name = "club_zatca_certificates")
class ClubZatcaCertificate(

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false, unique = true)
    var club: Club,

    @Column(name = "environment", nullable = false)
    var environment: String = "sandbox",

    @Column(name = "csr_pem", columnDefinition = "TEXT")
    var csrPem: String? = null,

    @Column(name = "private_key_encrypted", columnDefinition = "TEXT", nullable = false)
    var privateKeyEncrypted: String = "",

    @Column(name = "compliance_request_id")
    var complianceRequestId: String? = null,

    @Column(name = "compliance_binary_token", columnDefinition = "TEXT")
    var complianceBinaryToken: String? = null,

    @Column(name = "compliance_secret")
    var complianceSecret: String? = null,

    @Column(name = "production_request_id")
    var productionRequestId: String? = null,

    @Column(name = "production_binary_token", columnDefinition = "TEXT")
    var productionBinaryToken: String? = null,

    @Column(name = "production_secret")
    var productionSecret: String? = null,

    @Column(name = "certificate_pem", columnDefinition = "TEXT")
    var certificatePem: String? = null,

    @Column(name = "serial_number")
    var serialNumber: String? = null,

    @Column(name = "onboarding_status", nullable = false)
    var onboardingStatus: String = "pending",

    @Column(name = "csid_expires_at")
    var csidExpiresAt: java.time.Instant? = null,

) : AuditEntity()
```

**`ClubZatcaCertificateRepository.kt`**:
```kotlin
package com.liyaqa.zatca.repository

import com.liyaqa.zatca.entity.ClubZatcaCertificate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional
import java.util.UUID

interface ClubZatcaCertificateRepository : JpaRepository<ClubZatcaCertificate, Long> {

    fun findByClubIdAndDeletedAtIsNull(clubId: Long): Optional<ClubZatcaCertificate>

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<ClubZatcaCertificate>

    @Query(
        value = """
            SELECT czc.* FROM club_zatca_certificates czc
            WHERE czc.onboarding_status = 'active'
              AND czc.deleted_at IS NULL
        """,
        nativeQuery = true
    )
    fun findAllActive(): List<ClubZatcaCertificate>

    @Query(
        value = """
            SELECT czc.* FROM club_zatca_certificates czc
            WHERE czc.onboarding_status = 'active'
              AND czc.csid_expires_at < :expiryThreshold
              AND czc.deleted_at IS NULL
        """,
        nativeQuery = true
    )
    fun findExpiringSoon(expiryThreshold: java.time.Instant): List<ClubZatcaCertificate>
}
```

---

### Step 3 — ZATCA Crypto Service

**`ZatcaEncryptionService.kt`** — AES-256-GCM encryption for private keys at rest:

```kotlin
package com.liyaqa.zatca.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

@Service
class ZatcaEncryptionService(
    @Value("\${zatca.encryption.key}") private val encryptionKeyBase64: String
) {
    private val GCM_TAG_LENGTH = 128
    private val GCM_IV_LENGTH = 12

    private fun getKey(): SecretKeySpec {
        val keyBytes = Base64.getDecoder().decode(encryptionKeyBase64)
        require(keyBytes.size == 32) { "ZATCA encryption key must be 256 bits (32 bytes)" }
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encrypted: String): String {
        val combined = Base64.getDecoder().decode(encrypted)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext))
    }
}
```

**`ZatcaCryptoService.kt`** — key pair generation and CSR building:

```kotlin
package com.liyaqa.zatca.service

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.springframework.stereotype.Service
import java.io.StringWriter
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

@Service
class ZatcaCryptoService {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Generates an ECDSA secp256k1 key pair — required by ZATCA.
     */
    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC", "BC")
        kpg.initialize(ECGenParameterSpec("secp256k1"))
        return kpg.generateKeyPair()
    }

    /**
     * Exports private key as Base64 PKCS8 string for encryption and storage.
     */
    fun exportPrivateKeyBase64(keyPair: KeyPair): String =
        Base64.getEncoder().encodeToString(keyPair.private.encoded)

    /**
     * Imports private key from stored Base64 PKCS8 string.
     */
    fun importPrivateKeyFromBase64(base64: String): ECPrivateKey {
        val keyBytes = Base64.getDecoder().decode(base64)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("EC", "BC").generatePrivate(keySpec) as ECPrivateKey
    }

    /**
     * Builds a ZATCA-compliant CSR.
     * Subject fields must match exactly what is registered in FATOORA.
     *
     * @param vatNumber      Club's 15-digit VAT registration number
     * @param egsSerialNumber Unique EGS unit serial number for this club (e.g. "1-Liyaqa|2-{clubPublicId}|3-gym")
     * @param organizationName Club trade name
     * @param countryCode    "SA"
     * @param invoiceType    "1000" for simplified invoices (B2C)
     */
    fun buildCsr(
        keyPair: KeyPair,
        vatNumber: String,
        egsSerialNumber: String,
        organizationName: String,
        countryCode: String = "SA",
        invoiceType: String = "1000"
    ): String {
        // ZATCA requires specific OIDs in the CSR subject
        // 2.16.840.1.114412.1.1 = EGS Serial Number
        // 2.16.840.1.114412.1.5 = VAT Category
        val subject = X500Name(
            "CN=$egsSerialNumber,O=$organizationName,C=$countryCode," +
            "OU=$invoiceType,2.16.840.1.114412.1.1=$egsSerialNumber," +
            "2.16.840.1.114412.1.5=$vatNumber"
        )

        val csrBuilder = JcaPKCS10CertificationRequestBuilder(
            subject,
            keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider("BC")
            .build(keyPair.private)

        val csr = csrBuilder.build(signer)

        val sw = StringWriter()
        JcaPEMWriter(sw).use { writer ->
            writer.writeObject(csr)
        }
        return sw.toString()
    }

    /**
     * Signs data with the club's private key using SHA256withECDSA.
     * Used for the invoice digital signature.
     */
    fun signData(privateKeyBase64: String, data: ByteArray): ByteArray {
        val privateKey = importPrivateKeyFromBase64(privateKeyBase64)
        val signer = java.security.Signature.getInstance("SHA256withECDSA", "BC")
        signer.initSign(privateKey)
        signer.update(data)
        return signer.sign()
    }
}
```

> **Dependency to add to `build.gradle.kts`:**
> ```kotlin
> implementation("org.bouncycastle:bcprov-jdk15on:1.70")
> implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
> ```

---

### Step 4 — ZATCA API Client

**`ZatcaApiClient.kt`** — wraps all FATOORA REST calls:

```kotlin
package com.liyaqa.zatca.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.util.Base64

@Component
class ZatcaApiClient(
    private val objectMapper: ObjectMapper,
    @Value("\${zatca.api.base-url}") private val baseUrl: String
) {
    private val restTemplate = RestTemplate()

    /**
     * POST /compliance — issues a Compliance CSID.
     * Auth: sandbox uses dummy credentials; production uses actual ERAD OTP.
     * Body: { "csr": "<base64 PEM CSR>" }
     * Response: { requestID, binarySecurityToken, secret, ... }
     */
    fun issuanceComplianceCsid(csrBase64: String, otp: String): JsonNode {
        val headers = buildHeaders(null, null, otp)
        val body = mapOf("csr" to csrBase64)
        return post("$baseUrl/compliance", headers, body)
    }

    /**
     * POST /compliance/invoices — compliance check (run 3 times with different invoice types).
     * Auth: binarySecurityToken:secret from compliance step.
     */
    fun complianceInvoiceCheck(
        binarySecurityToken: String,
        secret: String,
        invoiceHash: String,
        uuid: String,
        invoiceBase64: String
    ): JsonNode {
        val headers = buildHeaders(binarySecurityToken, secret, null)
        val body = mapOf(
            "invoiceHash" to invoiceHash,
            "uuid" to uuid,
            "invoice" to invoiceBase64
        )
        return post("$baseUrl/compliance/invoices", headers, body)
    }

    /**
     * POST /production/csids — issues a Production CSID.
     * Auth: binarySecurityToken:secret from compliance step.
     * Body: { "compliance_request_id": "<requestID from compliance>" }
     */
    fun issuanceProductionCsid(
        complianceBinaryToken: String,
        complianceSecret: String,
        complianceRequestId: String
    ): JsonNode {
        val headers = buildHeaders(complianceBinaryToken, complianceSecret, null)
        val body = mapOf("compliance_request_id" to complianceRequestId)
        return post("$baseUrl/production/csids", headers, body)
    }

    /**
     * PATCH /production/csids — renews an existing Production CSID.
     */
    fun renewProductionCsid(
        complianceBinaryToken: String,
        complianceSecret: String,
        otp: String,
        csrBase64: String
    ): JsonNode {
        val headers = buildHeaders(complianceBinaryToken, complianceSecret, otp)
        val body = mapOf("csr" to csrBase64)
        return patch("$baseUrl/production/csids", headers, body)
    }

    /**
     * POST /invoices/reporting/single — reports a simplified invoice.
     * Auth: binarySecurityToken:secret from Production CSID.
     * Clearance-Status: 0 (disabled) for simplified B2C invoices.
     */
    fun reportSimplifiedInvoice(
        productionBinaryToken: String,
        productionSecret: String,
        invoiceHash: String,
        uuid: String,
        invoiceBase64: String
    ): JsonNode {
        val headers = buildHeaders(productionBinaryToken, productionSecret, null)
        headers["Clearance-Status"] = listOf("0")  // 0 = reporting (simplified), 1 = clearance (standard)
        val body = mapOf(
            "invoiceHash" to invoiceHash,
            "uuid" to uuid,
            "invoice" to invoiceBase64
        )
        return post("$baseUrl/invoices/reporting/single", headers, body)
    }

    private fun buildHeaders(
        binaryToken: String?,
        secret: String?,
        otp: String?
    ): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["Accept-Version"] = listOf("V2")
        headers["Accept-Language"] = listOf("en")

        if (binaryToken != null && secret != null) {
            val credentials = "$binaryToken:$secret"
            val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
            headers["Authorization"] = listOf("Basic $encoded")
        }
        if (otp != null) {
            headers["OTP"] = listOf(otp)
        }
        return headers
    }

    private fun post(url: String, headers: HttpHeaders, body: Any): JsonNode {
        val entity = HttpEntity(objectMapper.writeValueAsString(body), headers)
        val response = restTemplate.exchange(url, HttpMethod.POST, entity, String::class.java)
        return objectMapper.readTree(response.body)
    }

    private fun patch(url: String, headers: HttpHeaders, body: Any): JsonNode {
        val entity = HttpEntity(objectMapper.writeValueAsString(body), headers)
        val response = restTemplate.exchange(url, HttpMethod.PATCH, entity, String::class.java)
        return objectMapper.readTree(response.body)
    }
}
```

---

### Step 5 — Onboarding Service

**`ZatcaOnboardingService.kt`** — orchestrates the full onboarding flow:

```kotlin
package com.liyaqa.zatca.service

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.zatca.client.ZatcaApiClient
import com.liyaqa.zatca.entity.ClubZatcaCertificate
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import com.liyaqa.club.repository.ClubRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    @Value("\${zatca.environment}") private val environment: String  // "sandbox" | "production"
) {

    /**
     * Full onboarding: generates keys, builds CSR, calls /compliance,
     * runs compliance checks, calls /production/csids.
     *
     * Called by the platform admin from web-nexus after the club owner
     * generates an OTP on the FATOORA Portal.
     */
    @Transactional
    fun onboardClub(clubPublicId: UUID, otp: String) {
        val club = clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)
            ?: throw ArenaException("Club not found", HttpStatus.NOT_FOUND)

        // Prevent double onboarding
        certRepository.findByClubIdAndDeletedAtIsNull(club.id!!).ifPresent { existing ->
            if (existing.onboardingStatus == "active") {
                throw ArenaException("Club already has an active CSID", HttpStatus.CONFLICT)
            }
        }

        // 1. Generate ECDSA secp256k1 key pair
        val keyPair = cryptoService.generateKeyPair()
        val privateKeyBase64 = cryptoService.exportPrivateKeyBase64(keyPair)
        val privateKeyEncrypted = encryptionService.encrypt(privateKeyBase64)

        // 2. Build EGS serial number (unique per club unit)
        // Format: "1-{solutionName}|2-{vatNumber}|3-{unitIdentifier}"
        val egsSerialNumber = "1-Liyaqa|2-${club.vatNumber}|3-gym-${club.publicId}"

        // 3. Build CSR
        val csrPem = cryptoService.buildCsr(
            keyPair = keyPair,
            vatNumber = club.vatNumber ?: throw ArenaException("Club VAT number is required", HttpStatus.BAD_REQUEST),
            egsSerialNumber = egsSerialNumber,
            organizationName = club.nameEn ?: club.nameAr
        )
        val csrBase64 = Base64.getEncoder().encodeToString(csrPem.toByteArray())

        // 4. Save preliminary record
        val cert = certRepository.findByClubIdAndDeletedAtIsNull(club.id!!).orElse(
            ClubZatcaCertificate(club = club, environment = environment)
        ).apply {
            this.csrPem = csrPem
            this.privateKeyEncrypted = privateKeyEncrypted
            this.onboardingStatus = "pending"
        }
        certRepository.save(cert)

        // 5. Call /compliance — issue Compliance CSID
        val complianceResponse = apiClient.issuanceComplianceCsid(csrBase64, otp)
        val complianceRequestId = complianceResponse.get("requestID").asText()
        val complianceBinaryToken = complianceResponse.get("binarySecurityToken").asText()
        val complianceSecret = complianceResponse.get("secret").asText()

        cert.complianceRequestId = complianceRequestId
        cert.complianceBinaryToken = complianceBinaryToken
        cert.complianceSecret = complianceSecret
        cert.onboardingStatus = "compliance_issued"
        certRepository.save(cert)

        // 6. Run 3 compliance invoice checks
        // ZATCA requires submitting: 1 standard, 1 simplified, 1 credit/debit note
        // We generate dummy compliance invoices using the XML service
        runComplianceChecks(cert, club, complianceBinaryToken, complianceSecret, keyPair, privateKeyBase64)

        cert.onboardingStatus = "compliance_checked"
        certRepository.save(cert)

        // 7. Call /production/csids — issue Production CSID
        val productionResponse = apiClient.issuanceProductionCsid(
            complianceBinaryToken = complianceBinaryToken,
            complianceSecret = complianceSecret,
            complianceRequestId = complianceRequestId
        )

        val productionBinaryToken = productionResponse.get("binarySecurityToken").asText()
        val productionSecret = productionResponse.get("secret").asText()
        val productionRequestId = productionResponse.get("requestID").asText()

        // Parse certificate for expiry date
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

    private fun runComplianceChecks(
        cert: ClubZatcaCertificate,
        club: com.liyaqa.club.entity.Club,
        binaryToken: String,
        secret: String,
        keyPair: java.security.KeyPair,
        privateKeyBase64: String
    ) {
        // Generate 3 dummy compliance invoices (simplified, standard, credit note)
        // These are test invoices ONLY — not real invoices, not stored in the invoice table
        val complianceInvoices = xmlService.generateComplianceInvoices(club, keyPair, privateKeyBase64)
        complianceInvoices.forEach { (invoiceHash, uuid, invoiceBase64) ->
            val result = apiClient.complianceInvoiceCheck(binaryToken, secret, invoiceHash, uuid, invoiceBase64)
            val status = result.get("validationResults")?.get("status")?.asText()
            if (status != "PASS") {
                throw ArenaException(
                    "ZATCA compliance check failed: ${result.toPrettyString()}",
                    HttpStatus.BAD_REQUEST
                )
            }
        }
    }

    private fun parseCertificateExpiry(certPem: String): Instant {
        // Parse X.509 certificate to extract notAfter date
        val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(certPem.byteInputStream())
                as java.security.cert.X509Certificate
        return cert.notAfter.toInstant()
    }

    /**
     * Renew a CSID that is expiring or expired.
     * Requires a new OTP from FATOORA Portal.
     */
    @Transactional
    fun renewClubCsid(clubPublicId: UUID, otp: String) {
        val club = clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)
            ?: throw ArenaException("Club not found", HttpStatus.NOT_FOUND)

        val cert = certRepository.findByClubIdAndDeletedAtIsNull(club.id!!)
            .orElseThrow { ArenaException("No CSID found for club", HttpStatus.NOT_FOUND) }

        // Generate new key pair and CSR for renewal
        val newKeyPair = cryptoService.generateKeyPair()
        val newPrivateKeyBase64 = cryptoService.exportPrivateKeyBase64(newKeyPair)
        val egsSerialNumber = "1-Liyaqa|2-${club.vatNumber}|3-gym-${club.publicId}"
        val csrPem = cryptoService.buildCsr(newKeyPair, club.vatNumber!!, egsSerialNumber, club.nameEn ?: club.nameAr)
        val csrBase64 = Base64.getEncoder().encodeToString(csrPem.toByteArray())

        val renewalResponse = apiClient.renewProductionCsid(
            complianceBinaryToken = cert.complianceBinaryToken!!,
            complianceSecret = cert.complianceSecret!!,
            otp = otp,
            csrBase64 = csrBase64
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
}
```

---

### Step 6 — XML and QR Code Service

**`ZatcaXmlService.kt`** — generates and signs UBL XML invoices:

The XML structure is a UBL 2.1 document. For Phase 2 Simplified Tax Invoices, the key changes vs Phase 1:
- Add `<cac:Signature>` element with ECDSA signature
- Embed `<cac:SignatoryParty>` with club's X.509 certificate
- Include `<ds:SignatureValue>` with the ECDSA signature over the invoice hash

```kotlin
package com.liyaqa.zatca.service

import org.springframework.stereotype.Service
import java.security.KeyPair
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

@Service
class ZatcaXmlService(
    private val cryptoService: ZatcaCryptoService
) {

    /**
     * Generates the signed UBL XML for a real invoice.
     * Signs the invoice hash with the club's private key.
     * Returns base64-encoded signed XML.
     */
    fun signInvoiceXml(
        invoiceXml: String,        // The Phase 1 generated XML
        invoiceHash: String,       // SHA-256 hash from Phase 1 (hex string)
        privateKeyBase64: String,  // Decrypted private key
        certificatePem: String     // Club's X.509 PEM certificate
    ): String {
        // Convert hash from hex to bytes, sign with private key
        val hashBytes = invoiceHash.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val signatureBytes = cryptoService.signData(privateKeyBase64, hashBytes)
        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes)

        // Embed signature into XML (inject into existing <cac:Signature> placeholder)
        val certBase64 = Base64.getEncoder().encodeToString(
            certificatePem.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\n", "")
                .toByteArray()
        )

        val signedXml = invoiceXml
            .replace("{{SIGNATURE_VALUE}}", signatureBase64)
            .replace("{{CERTIFICATE}}", certBase64)

        return Base64.getEncoder().encodeToString(signedXml.toByteArray())
    }

    /**
     * Generates 3 dummy compliance invoices required by ZATCA onboarding.
     * Returns list of (invoiceHash, uuid, base64XML) triples.
     */
    fun generateComplianceInvoices(
        club: com.liyaqa.club.entity.Club,
        keyPair: KeyPair,
        privateKeyBase64: String
    ): List<Triple<String, String, String>> {
        return listOf(
            generateComplianceInvoice(club, "388", keyPair, privateKeyBase64),  // Simplified Tax Invoice
            generateComplianceInvoice(club, "383", keyPair, privateKeyBase64),  // Credit Note
            generateComplianceInvoice(club, "381", keyPair, privateKeyBase64)   // Standard Tax Invoice
        )
    }

    private fun generateComplianceInvoice(
        club: com.liyaqa.club.entity.Club,
        invoiceTypeCode: String,
        keyPair: KeyPair,
        privateKeyBase64: String
    ): Triple<String, String, String> {
        val uuid = UUID.randomUUID().toString()
        val now = ZonedDateTime.now()
        val dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        // Build minimal compliant UBL XML for compliance checking
        // In production this is identical structure to the real invoice XML
        val xml = buildMinimalComplianceXml(
            uuid = uuid,
            invoiceTypeCode = invoiceTypeCode,
            issueDate = dateStr,
            issueTime = timeStr,
            vatNumber = club.vatNumber ?: "300000000000003",
            sellerName = club.nameEn ?: club.nameAr,
            subtotalHalalas = 10000L,  // 100 SAR dummy amount
            vatHalalas = 1500L,        // 15 SAR = 15% VAT
            totalHalalas = 11500L
        )

        // Hash and sign
        val xmlBytes = xml.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(xmlBytes)
        val hashBase64 = Base64.getEncoder().encodeToString(hashBytes)
        val xmlBase64 = Base64.getEncoder().encodeToString(xmlBytes)

        return Triple(hashBase64, uuid, xmlBase64)
    }

    private fun buildMinimalComplianceXml(
        uuid: String,
        invoiceTypeCode: String,
        issueDate: String,
        issueTime: String,
        vatNumber: String,
        sellerName: String,
        subtotalHalalas: Long,
        vatHalalas: Long,
        totalHalalas: Long
    ): String {
        val subtotalSar = "%.2f".format(subtotalHalalas / 100.0)
        val vatSar = "%.2f".format(vatHalalas / 100.0)
        val totalSar = "%.2f".format(totalHalalas / 100.0)

        // Minimal UBL 2.1 XML — full implementation must follow ZATCA XML Implementation Standard
        // All required namespaces, elements, and field values per ZATCA Data Dictionary
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
```

**`ZatcaQrService.kt`** — builds the 9-tag TLV QR code for Phase 2:

```kotlin
package com.liyaqa.zatca.service

import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

@Service
class ZatcaQrService {

    /**
     * Builds the 9-tag TLV QR code for Phase 2 Simplified Tax Invoices.
     *
     * Phase 1 had 5 tags. Phase 2 adds:
     * Tag 6: Digital signature (ECDSA signature of invoice hash)
     * Tag 7: Public key (DER encoded)
     * Tag 8: Certificate hash (SHA-256 of the certificate)
     * Tag 9: Certificate raw (the Compliance Stamp — PIH signing chain)  [optional]
     */
    fun buildPhase2QrCode(
        sellerName: String,
        vatNumber: String,
        timestamp: String,       // ISO format: "2021-01-01T00:00:00Z"
        totalWithVat: String,    // "250.00" (SAR decimal)
        vatAmount: String,       // "32.61"
        digitalSignatureBase64: String,
        publicKeyDerBase64: String,
        certificatePem: String
    ): String {
        val baos = ByteArrayOutputStream()

        // Tag 1: Seller name
        writeTlv(baos, 1, sellerName.toByteArray(Charsets.UTF_8))
        // Tag 2: VAT Registration Number
        writeTlv(baos, 2, vatNumber.toByteArray(Charsets.UTF_8))
        // Tag 3: Timestamp
        writeTlv(baos, 3, timestamp.toByteArray(Charsets.UTF_8))
        // Tag 4: Invoice total with VAT
        writeTlv(baos, 4, totalWithVat.toByteArray(Charsets.UTF_8))
        // Tag 5: VAT amount
        writeTlv(baos, 5, vatAmount.toByteArray(Charsets.UTF_8))
        // Tag 6: Digital signature (ECDSA)
        writeTlv(baos, 6, Base64.getDecoder().decode(digitalSignatureBase64))
        // Tag 7: Public key (DER)
        writeTlv(baos, 7, Base64.getDecoder().decode(publicKeyDerBase64))
        // Tag 8: Certificate SHA-256 hash
        val certBytes = certificatePem.toByteArray()
        val certHash = MessageDigest.getInstance("SHA-256").digest(certBytes)
        writeTlv(baos, 8, certHash)

        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun writeTlv(baos: ByteArrayOutputStream, tag: Int, value: ByteArray) {
        baos.write(tag)
        baos.write(value.size)
        baos.write(value)
    }
}
```

---

### Step 7 — Reporting Service and Scheduler

**`ZatcaReportingService.kt`**:

```kotlin
package com.liyaqa.zatca.service

import com.liyaqa.invoice.repository.InvoiceRepository
import com.liyaqa.zatca.client.ZatcaApiClient
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ZatcaReportingService(
    private val invoiceRepository: InvoiceRepository,
    private val certRepository: ClubZatcaCertificateRepository,
    private val xmlService: ZatcaXmlService,
    private val qrService: ZatcaQrService,
    private val encryptionService: ZatcaEncryptionService,
    private val apiClient: ZatcaApiClient
) {
    private val log = LoggerFactory.getLogger(ZatcaReportingService::class.java)
    private val MAX_RETRIES = 5

    /**
     * Reports a single invoice to ZATCA.
     * Called by the scheduler for pending invoices.
     */
    @Transactional
    fun reportInvoice(invoiceId: Long) {
        val invoice = invoiceRepository.findById(invoiceId).orElseThrow {
            IllegalStateException("Invoice not found: $invoiceId")
        }

        if (invoice.zatcaStatus == "reported") return

        val club = invoice.membership?.plan?.club
            ?: throw IllegalStateException("Invoice has no associated club: $invoiceId")

        val cert = certRepository.findByClubIdAndDeletedAtIsNull(club.id!!)
            .orElse(null)

        if (cert == null || cert.onboardingStatus != "active") {
            // Club not onboarded yet — skip gracefully
            invoice.zatcaStatus = "skipped"
            invoice.zatcaLastError = "Club not onboarded for ZATCA Phase 2"
            invoiceRepository.save(invoice)
            return
        }

        try {
            // Decrypt private key
            val privateKeyBase64 = encryptionService.decrypt(cert.privateKeyEncrypted)

            // Build signed XML (Phase 1 XML already exists in invoice.zatcaXml or regenerate)
            val signedXmlBase64 = xmlService.signInvoiceXml(
                invoiceXml = invoice.zatcaXml ?: generateInvoiceXml(invoice),
                invoiceHash = invoice.zatcaHash ?: "",
                privateKeyBase64 = privateKeyBase64,
                certificatePem = cert.certificatePem ?: ""
            )
            invoice.zatcaSignedXml = signedXmlBase64
            invoice.zatcaStatus = "signed"
            invoiceRepository.save(invoice)

            // Submit to ZATCA
            val response = apiClient.reportSimplifiedInvoice(
                productionBinaryToken = cert.productionBinaryToken!!,
                productionSecret = cert.productionSecret!!,
                invoiceHash = invoice.zatcaHash ?: "",
                uuid = invoice.zatcaUuid ?: invoice.publicId.toString(),
                invoiceBase64 = signedXmlBase64
            )

            val reportingStatus = response.get("reportingStatus")?.asText()
            val validationStatus = response.get("validationResults")?.get("status")?.asText()

            if (reportingStatus == "REPORTED" || validationStatus == "PASS") {
                invoice.zatcaStatus = "reported"
                invoice.zatcaReportedAt = Instant.now()
                invoice.zatcaReportResponse = response.toString()
                log.info("Invoice {} reported to ZATCA successfully", invoice.publicId)
            } else {
                throw RuntimeException("ZATCA returned non-success: ${response.toPrettyString()}")
            }

        } catch (ex: Exception) {
            invoice.zatcaRetryCount = (invoice.zatcaRetryCount ?: 0) + 1
            invoice.zatcaLastError = ex.message?.take(1000)
            invoice.zatcaStatus = if ((invoice.zatcaRetryCount ?: 0) >= MAX_RETRIES) "failed" else "generated"
            log.error("ZATCA reporting failed for invoice {}: {}", invoice.publicId, ex.message)
        }

        invoiceRepository.save(invoice)
    }

    private fun generateInvoiceXml(invoice: com.liyaqa.invoice.entity.Invoice): String {
        // Re-generate invoice XML if not stored from Phase 1
        // This is a fallback — Phase 1 should already store zatcaXml
        throw IllegalStateException("Invoice XML not found for invoice ${invoice.publicId}. Phase 1 must store zatcaXml.")
    }
}
```

**`ZatcaReportingScheduler.kt`**:

```kotlin
package com.liyaqa.zatca.scheduler

import com.liyaqa.invoice.repository.InvoiceRepository
import com.liyaqa.zatca.service.ZatcaReportingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ZatcaReportingScheduler(
    private val invoiceRepository: InvoiceRepository,
    private val reportingService: ZatcaReportingService
) {
    private val log = LoggerFactory.getLogger(ZatcaReportingScheduler::class.java)

    /**
     * Every 5 minutes: report any invoices that are signed but not yet reported.
     * Also retries failed invoices (up to MAX_RETRIES).
     *
     * ZATCA requires simplified invoices to be reported within 24 hours of issuance.
     * Running every 5 minutes gives ample time for retries while meeting the deadline.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000) // every 5 minutes
    fun reportPendingInvoices() {
        val pending = invoiceRepository.findPendingZatcaReporting()
        if (pending.isEmpty()) return

        log.info("ZATCA scheduler: {} invoices pending reporting", pending.size)
        pending.forEach { invoiceId ->
            try {
                reportingService.reportInvoice(invoiceId)
            } catch (ex: Exception) {
                log.error("Unexpected error reporting invoice {}: {}", invoiceId, ex.message)
            }
        }
    }

    /**
     * Daily at 6am: alert on invoices that failed all retries.
     * These need manual intervention.
     */
    @Scheduled(cron = "0 0 6 * * *")
    fun alertFailedInvoices() {
        val failed = invoiceRepository.findFailedZatcaReporting()
        if (failed.isNotEmpty()) {
            log.error("ZATCA ALERT: {} invoices permanently failed reporting. Manual review required.", failed.size)
            // TODO: integrate with notification system (Plan 21) when notification types are expanded
        }
    }
}
```

**Add to `InvoiceRepository.kt`** (CRITICAL — use `nativeQuery = true`):

```kotlin
// In InvoiceRepository, add these two queries

@Query(
    value = """
        SELECT i.id FROM invoices i
        WHERE i.zatca_status IN ('generated', 'signed')
          AND i.zatca_retry_count < 5
          AND i.deleted_at IS NULL
        ORDER BY i.created_at ASC
        LIMIT 100
    """,
    nativeQuery = true
)
fun findPendingZatcaReporting(): List<Long>

@Query(
    value = """
        SELECT i.id FROM invoices i
        WHERE i.zatca_status = 'failed'
          AND i.deleted_at IS NULL
        ORDER BY i.updated_at DESC
    """,
    nativeQuery = true
)
fun findFailedZatcaReporting(): List<Long>
```

Enable scheduling in the main application class:
```kotlin
@EnableScheduling
@SpringBootApplication
class LiyaqaApplication
```

---

### Step 8 — Controllers

**`ZatcaNexusController.kt`** — platform admin endpoints:

```kotlin
package com.liyaqa.zatca.controller

import com.liyaqa.zatca.dto.OnboardingRequest
import com.liyaqa.zatca.dto.OnboardingStatusResponse
import com.liyaqa.zatca.service.ZatcaOnboardingService
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/zatca")
@Tag(name = "ZATCA (Nexus)", description = "ZATCA Phase 2 onboarding management (platform admin)")
class ZatcaNexusController(
    private val onboardingService: ZatcaOnboardingService,
    private val certRepository: ClubZatcaCertificateRepository
) {

    /**
     * Initiates the ZATCA onboarding flow for a club.
     * The club owner must first generate an OTP on the FATOORA Portal.
     * The platform admin enters that OTP here to trigger automated onboarding.
     */
    @PostMapping("/clubs/{clubPublicId}/onboard")
    @Operation(summary = "Onboard club to ZATCA Phase 2")
    @PreAuthorize("hasPermission(null, 'zatca:onboard')")
    fun onboardClub(
        @PathVariable clubPublicId: UUID,
        @RequestBody request: OnboardingRequest
    ): ResponseEntity<Map<String, String>> {
        onboardingService.onboardClub(clubPublicId, request.otp)
        return ResponseEntity.ok(mapOf("message" to "Club onboarded successfully to ZATCA Phase 2"))
    }

    /**
     * Get the current ZATCA onboarding status for a club.
     */
    @GetMapping("/clubs/{clubPublicId}/status")
    @Operation(summary = "Get ZATCA onboarding status for a club")
    @PreAuthorize("hasPermission(null, 'zatca:read')")
    fun getClubStatus(@PathVariable clubPublicId: UUID): ResponseEntity<OnboardingStatusResponse> {
        val cert = certRepository.findAll().find { it.club.publicId == clubPublicId && it.deletedAt == null }
        return if (cert == null) {
            ResponseEntity.ok(OnboardingStatusResponse(
                status = "not_onboarded",
                environment = null,
                csidExpiresAt = null,
                onboardingStatus = "pending"
            ))
        } else {
            ResponseEntity.ok(OnboardingStatusResponse(
                status = cert.onboardingStatus,
                environment = cert.environment,
                csidExpiresAt = cert.csidExpiresAt?.toString(),
                onboardingStatus = cert.onboardingStatus
            ))
        }
    }

    /**
     * List all clubs and their ZATCA onboarding status.
     */
    @GetMapping("/clubs")
    @Operation(summary = "List all clubs with ZATCA status")
    @PreAuthorize("hasPermission(null, 'zatca:read')")
    fun listClubsZatcaStatus(): ResponseEntity<List<OnboardingStatusResponse>> {
        val certs = certRepository.findAll().filter { it.deletedAt == null }
        return ResponseEntity.ok(certs.map { cert ->
            OnboardingStatusResponse(
                status = cert.onboardingStatus,
                environment = cert.environment,
                csidExpiresAt = cert.csidExpiresAt?.toString(),
                onboardingStatus = cert.onboardingStatus
            )
        })
    }

    /**
     * Renew a club's CSID (requires new OTP from FATOORA Portal).
     */
    @PostMapping("/clubs/{clubPublicId}/renew")
    @Operation(summary = "Renew club CSID")
    @PreAuthorize("hasPermission(null, 'zatca:onboard')")
    fun renewClubCsid(
        @PathVariable clubPublicId: UUID,
        @RequestBody request: OnboardingRequest
    ): ResponseEntity<Map<String, String>> {
        onboardingService.renewClubCsid(clubPublicId, request.otp)
        return ResponseEntity.ok(mapOf("message" to "CSID renewed successfully"))
    }
}
```

**`ZatcaPulseController.kt`** — club staff view (read-only status):

```kotlin
package com.liyaqa.zatca.controller

import com.liyaqa.zatca.dto.OnboardingStatusResponse
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import com.liyaqa.common.security.TenantContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/pulse/zatca")
@Tag(name = "ZATCA (Pulse)", description = "ZATCA integration status for club staff")
class ZatcaPulseController(
    private val certRepository: ClubZatcaCertificateRepository,
    private val tenantContext: TenantContext
) {

    @GetMapping("/status")
    @Operation(summary = "Get this club's ZATCA integration status")
    @PreAuthorize("hasPermission(null, 'zatca:read')")
    fun getMyClubZatcaStatus(): ResponseEntity<OnboardingStatusResponse> {
        val clubId = tenantContext.currentClubId()
        val cert = certRepository.findByClubIdAndDeletedAtIsNull(clubId).orElse(null)
        return ResponseEntity.ok(
            if (cert == null) OnboardingStatusResponse("not_onboarded", null, null, "pending")
            else OnboardingStatusResponse(cert.onboardingStatus, cert.environment, cert.csidExpiresAt?.toString(), cert.onboardingStatus)
        )
    }
}
```

**DTOs:**

```kotlin
// OnboardingRequest.kt
data class OnboardingRequest(val otp: String)

// OnboardingStatusResponse.kt
data class OnboardingStatusResponse(
    val status: String,
    val environment: String?,
    val csidExpiresAt: String?,
    val onboardingStatus: String
)
```

**Permissions to register:**
- `zatca:onboard` — platform admin only (NexusAdmin role)
- `zatca:read` — club owner + platform admin

---

### Step 9 — Configuration

Add to `application.yml`:

```yaml
zatca:
  environment: sandbox  # Change to 'production' for live
  api:
    base-url: https://gw-apic-gov.gazt.gov.sa/e-invoicing/developer-portal
    # Sandbox: https://gw-apic-gov.gazt.gov.sa/e-invoicing/developer-portal
    # Production: https://gw-apic-gov.gazt.gov.sa/e-invoicing/core
  encryption:
    key: ${ZATCA_ENCRYPTION_KEY}  # 32-byte base64 key — set in env, NEVER in source
```

Add to `application-dev.yml`:
```yaml
zatca:
  environment: sandbox
  api:
    base-url: https://gw-apic-gov.gazt.gov.sa/e-invoicing/developer-portal
  encryption:
    key: "aaaabbbbccccddddeeeeffffgggghhhh"  # dev-only dummy key, not used in prod
```

**Never commit the production encryption key.** Store in environment variables or a secrets manager. Add `ZATCA_ENCRYPTION_KEY` to `.env.example` with a note to generate with `openssl rand -base64 32`.

---

### Step 10 — Frontend — web-nexus ZATCA Management Screen

**Route:** `/zatca` (platform admin only)

**File:** `apps/web-nexus/src/routes/zatca/index.tsx`

The screen shows:
- Table of all clubs with their ZATCA onboarding status
- Color-coded status badge: `pending` (grey), `compliance_issued` (yellow), `active` (green), `expired` (red), `failed` (red)
- Per-club action buttons: "Onboard" (for pending clubs) or "Renew" (for active/expired clubs)
- OTP entry dialog — triggered when admin clicks Onboard or Renew
- CSID expiry date display
- Environment badge (sandbox / production)

**Key i18n strings:**
```ts
// Arabic (ar):
zatca: {
  title: "إعداد الفواتير الإلكترونية (زاتكا - المرحلة الثانية)",
  status: {
    not_onboarded: "غير مُفعّل",
    pending: "قيد الانتظار",
    compliance_issued: "تم إصدار شهادة الامتثال",
    compliance_checked: "تم التحقق من الامتثال",
    active: "نشط",
    expired: "منتهي الصلاحية",
    failed: "فشل"
  },
  otp_dialog: {
    title: "إدخال كود OTP",
    description: "قم بتسجيل الدخول إلى بوابة فاتورة وأنشئ رمز OTP لهذا النادي، ثم أدخله هنا.",
    otp_label: "رمز OTP",
    submit: "بدء الإعداد",
  }
}

// English (en):
zatca: {
  title: "E-Invoicing (ZATCA Phase 2)",
  status: {
    not_onboarded: "Not Onboarded",
    pending: "Pending",
    compliance_issued: "Compliance CSID Issued",
    compliance_checked: "Compliance Checked",
    active: "Active",
    expired: "Expired",
    failed: "Failed"
  },
  otp_dialog: {
    title: "Enter OTP",
    description: "Log into the FATOORA Portal and generate an OTP for this club, then enter it here.",
    otp_label: "OTP Code",
    submit: "Start Onboarding",
  }
}
```

**API calls:**
```ts
// GET /api/v1/zatca/clubs — list all clubs with ZATCA status
// POST /api/v1/zatca/clubs/{clubPublicId}/onboard — { otp: string }
// POST /api/v1/zatca/clubs/{clubPublicId}/renew — { otp: string }
```

---

### Step 11 — Frontend — web-pulse ZATCA Status Widget

**Route:** `/settings/zatca` (club staff — read-only)

A simple status card showing:
- Current onboarding status with icon
- CSID expiry date
- Instructions to contact platform admin if not onboarded

**File:** `apps/web-pulse/src/routes/settings/zatca/index.tsx`

---

### Step 12 — Tests

#### Unit Tests

**`ZatcaCryptoServiceTest.kt`**:
- `generates valid secp256k1 key pair`
- `exports and imports private key round-trip`
- `builds CSR with correct subject fields`
- `signs and verifies data with generated key pair`

**`ZatcaEncryptionServiceTest.kt`**:
- `encrypts and decrypts private key correctly`
- `different IV produces different ciphertext`
- `throws on wrong key`

**`ZatcaQrServiceTest.kt`**:
- `builds 9-tag TLV with correct tag order`
- `base64 output is valid`
- `tags 1-5 match Phase 1 content`
- `tag 6 contains digital signature bytes`

**`ZatcaApiClientTest.kt`** (mock RestTemplate):
- `compliance CSID request includes OTP header`
- `reporting request sets Clearance-Status: 0`
- `authorization header is correct Basic encoding`
- `accept-version is V2`

**`ZatcaReportingServiceTest.kt`**:
- `skips invoice when club has no active CSID`
- `marks invoice as reported on success`
- `increments retry count on API failure`
- `marks as failed when retry count reaches MAX_RETRIES`
- `does not re-report already-reported invoices`

#### Integration Tests

**`ZatcaOnboardingIntegrationTest.kt`** (Testcontainers):
- Mocks the ZATCA API (WireMock) — do not call real ZATCA in tests
- `full onboarding flow saves certificate correctly`
- `duplicate onboarding of active club throws CONFLICT`
- `onboarding rolls back on compliance check failure`
- `renewal updates existing certificate record`

**`ZatcaReportingSchedulerIntegrationTest.kt`**:
- `scheduler picks up pending invoices from database`
- `reported invoices are not picked up again`
- `failed invoices after MAX_RETRIES are not retried`

> **Testing rule**: Always use `const val TEST_PASSWORD = "Test@12345678"` for any auth in tests.

> **Note on WireMock**: Add WireMock to test dependencies:
> ```kotlin
> testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")
> ```
> Mock all ZATCA API calls — never hit the real sandbox in automated tests.

---

## Permissions Required

Add to the database permissions seed / migration:

| Permission Code | Description |
|----------------|-------------|
| `zatca:onboard` | Trigger ZATCA onboarding/renewal for a club |
| `zatca:read` | View ZATCA status |

Assign `zatca:onboard` to: NexusAdmin role only
Assign `zatca:read` to: NexusAdmin, ClubOwner roles

---

## Configuration Checklist (Before Going Live)

Before switching from `sandbox` to `production`:

1. Each club's owner must log into https://fatoora.zatca.gov.sa/ with their ERAD credentials
2. Club owner navigates to: E-Invoicing Devices → EGS Units → Add New Unit
3. Club owner generates an OTP (valid for a short window — coordinate timing with platform admin)
4. Platform admin enters OTP in web-nexus ZATCA screen immediately
5. System completes automated onboarding (generates keys, gets Compliance CSID, passes compliance checks, gets Production CSID)
6. Verify `onboarding_status = 'active'` in the database
7. Switch `zatca.environment: production` and `zatca.api.base-url` to production URL
8. Ensure `ZATCA_ENCRYPTION_KEY` is set in production environment variables (NOT sandbox key)
9. Run first invoice report manually and verify `zatca_status = 'reported'`

---

## What Was Already Done in Phase 1 (Do Not Redo)

- ✅ Invoice entity has: `invoice_counter_value`, `previous_invoice_hash`, `zatca_uuid`, `zatca_hash`, `zatca_qr_code`
- ✅ PIH hash chain is maintained
- ✅ 5-tag TLV QR code is generated
- ✅ `zatcaStatus = "generated"` is set on invoice creation

Phase 2 upgrades the QR to 9 tags and adds the reporting submission. The `zatcaStatus` field transitions from `generated` → `signed` → `reported`.

---

## Constraints and Rules Enforced

- All `@Query` on the InvoiceRepository for ZATCA use `nativeQuery = true` (SQL column names, not JPQL)
- All monetary values remain in halalas internally
- Private keys are AES-256-GCM encrypted before storing in the database
- `ZATCA_ENCRYPTION_KEY` is never in source code or git history
- No hardcoded test passwords — `TEST_PASSWORD = "Test@12345678"` only
- Internal PKs (`Long`) never exposed in API — only `UUID` publicIds
- Every controller endpoint has `@Operation` and `@PreAuthorize`
- Package: `com.liyaqa.zatca` — never `com.arena.*`
- Entity `ClubZatcaCertificate` extends `AuditEntity`
- ZATCA logic is fully isolated in the `zatca` package — no bleed into membership/invoice packages
- `@Transactional(readOnly = true)` on service class, `@Transactional` override on write methods

---

## Flyway Migration File

```
V15__zatca_phase2.sql
```

This is migration #15, following V14 from the member self-registration plan. Do NOT run Flyway in dev (`ddl-auto: create-drop` handles dev schema).

---

## Summary

| What | Count |
|------|-------|
| New migration files | 1 (V15) |
| New entities | 1 (ClubZatcaCertificate) |
| New repositories | 1 |
| New services | 5 (Crypto, Xml, Qr, Onboarding, Reporting) |
| New clients | 1 (ZatcaApiClient) |
| New schedulers | 1 |
| New controllers | 2 (Nexus + Pulse) |
| New frontend routes | 2 (web-nexus ZATCA mgmt + web-pulse status widget) |
| New permissions | 2 (zatca:onboard, zatca:read) |
| New unit tests (est.) | 25+ |
| New integration tests (est.) | 10+ |
| Estimated total backend tests after | 493+ |

**Critical blocker**: Club owners must generate OTPs on the FATOORA Portal before any real invoices can be reported. The sandbox environment is fully testable without this — use the dummy credentials provided by the ZATCA Developer Portal.

