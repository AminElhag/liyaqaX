package com.liyaqa.zatca.entity

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "club_zatca_certificates")
class ClubZatcaCertificate(
    @Column(name = "club_id", nullable = false, unique = true)
    val clubId: Long,
    @Column(name = "environment", nullable = false, length = 20)
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
    @Column(name = "onboarding_status", nullable = false, length = 50)
    var onboardingStatus: String = "pending",
    @Column(name = "csid_expires_at")
    var csidExpiresAt: Instant? = null,
) : AuditEntity()
