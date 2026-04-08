package com.liyaqa.zatca.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.audit.AuditService
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.zatca.client.ZatcaApiClient
import com.liyaqa.zatca.entity.ClubZatcaCertificate
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ZatcaOnboardingServiceTest {
    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var certRepository: ClubZatcaCertificateRepository

    @Mock lateinit var cryptoService: ZatcaCryptoService

    @Mock lateinit var encryptionService: ZatcaEncryptionService

    @Mock lateinit var xmlService: ZatcaXmlService

    @Mock lateinit var apiClient: ZatcaApiClient

    @Mock lateinit var auditService: AuditService

    private lateinit var service: ZatcaOnboardingService
    private val objectMapper = ObjectMapper()

    private val clubPublicId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service =
            ZatcaOnboardingService(
                clubRepository, certRepository, cryptoService,
                encryptionService, xmlService, apiClient, auditService, "sandbox",
            )
    }

    @Test
    fun `onboarding throws NOT_FOUND when club does not exist`() {
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.onboardClub(clubPublicId, "123456") }
            .isInstanceOf(ArenaException::class.java)
            .matches { (it as ArenaException).status == HttpStatus.NOT_FOUND }
    }

    @Test
    fun `duplicate onboarding of active club throws CONFLICT`() {
        val club = testClub()
        val activeCert =
            ClubZatcaCertificate(clubId = club.id, environment = "sandbox").apply {
                onboardingStatus = "active"
            }

        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId))
            .thenReturn(Optional.of(club))
        whenever(certRepository.findByClubIdAndDeletedAtIsNull(club.id))
            .thenReturn(Optional.of(activeCert))

        assertThatThrownBy { service.onboardClub(clubPublicId, "123456") }
            .isInstanceOf(ArenaException::class.java)
            .matches { (it as ArenaException).status == HttpStatus.CONFLICT }
    }

    @Test
    fun `onboarding throws BAD_REQUEST when club has no VAT number`() {
        val club = testClub(vatNumber = null)

        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId))
            .thenReturn(Optional.of(club))
        whenever(certRepository.findByClubIdAndDeletedAtIsNull(club.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.onboardClub(clubPublicId, "123456") }
            .isInstanceOf(ArenaException::class.java)
            .matches { (it as ArenaException).status == HttpStatus.BAD_REQUEST }
    }

    @Test
    fun `onboarding rolls back on compliance check failure`() {
        val club = testClub()
        val realCryptoService = ZatcaCryptoService()
        val keyPair = realCryptoService.generateKeyPair()

        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId))
            .thenReturn(Optional.of(club))
        whenever(certRepository.findByClubIdAndDeletedAtIsNull(club.id))
            .thenReturn(Optional.empty())
        whenever(cryptoService.generateKeyPair()).thenReturn(keyPair)
        whenever(cryptoService.exportPrivateKeyBase64(any())).thenReturn("base64key")
        whenever(
            cryptoService.buildCsr(any(), any(), any(), any(), any(), any()),
        ).thenReturn("-----BEGIN CERTIFICATE REQUEST-----\nfake\n-----END CERTIFICATE REQUEST-----")
        whenever(encryptionService.encrypt(any())).thenReturn("encryptedKey")
        whenever(certRepository.save(any<ClubZatcaCertificate>())).thenAnswer { it.arguments[0] }

        val complianceResponse =
            objectMapper.readTree(
                """{"requestID":"req1","binarySecurityToken":"token","secret":"secret"}""",
            )
        whenever(apiClient.issuanceComplianceCsid(any(), any())).thenReturn(complianceResponse)

        val failedCheck =
            objectMapper.readTree(
                """{"validationResults":{"status":"FAIL","errors":["bad"]}}""",
            )
        whenever(xmlService.generateComplianceInvoices(any(), any(), any(), any()))
            .thenReturn(listOf(Triple("hash", "uuid", "xml")))
        whenever(apiClient.complianceInvoiceCheck(any(), any(), any(), any(), any()))
            .thenReturn(failedCheck)

        assertThatThrownBy { service.onboardClub(clubPublicId, "otp123") }
            .isInstanceOf(ArenaException::class.java)
            .hasMessageContaining("compliance check failed")
    }

    private fun testClub(vatNumber: String? = "300000000000003"): Club {
        val club =
            Club(
                organizationId = 1L,
                nameAr = "نادي إكسير",
                nameEn = "Elixir Gym",
                vatNumber = vatNumber,
            )
        // Set id via reflection since it's generated
        val idField = club.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(club, 1L)
        val pubIdField = club.javaClass.getDeclaredField("publicId")
        pubIdField.isAccessible = true
        pubIdField.set(club, clubPublicId)
        return club
    }
}
