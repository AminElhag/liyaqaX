package com.liyaqa.zatca.service

import com.liyaqa.invoice.FailedZatcaInvoiceProjection
import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ZatcaHealthServiceTest {
    @Mock lateinit var certRepository: ClubZatcaCertificateRepository

    @Mock lateinit var invoiceRepository: InvoiceRepository

    private lateinit var service: ZatcaHealthService

    @BeforeEach
    fun setUp() {
        service = ZatcaHealthService(certRepository, invoiceRepository)
    }

    @Test
    fun `getHealthSummary returns correct counts from repository mocks`() {
        whenever(certRepository.countByStatusAndNotDeleted("active")).thenReturn(5L)
        whenever(certRepository.countExpiringSoon(any())).thenReturn(2L)
        whenever(certRepository.countByStatusNotAndNotDeleted("active")).thenReturn(3L)
        whenever(invoiceRepository.countPendingZatcaReporting()).thenReturn(10L)
        whenever(invoiceRepository.countFailedZatcaReporting()).thenReturn(4L)
        whenever(invoiceRepository.countInvoicesApproachingDeadline(any())).thenReturn(1L)

        val summary = service.getHealthSummary()

        assertThat(summary.totalActiveCsids).isEqualTo(5L)
        assertThat(summary.csidsExpiringSoon).isEqualTo(2L)
        assertThat(summary.clubsNotOnboarded).isEqualTo(3L)
        assertThat(summary.invoicesPending).isEqualTo(10L)
        assertThat(summary.invoicesFailed).isEqualTo(4L)
        assertThat(summary.invoicesDeadlineAtRisk).isEqualTo(1L)
    }

    @Test
    fun `getFailedInvoices maps projection to DTO correctly`() {
        val projection = object : FailedZatcaInvoiceProjection {
            override val invoicePublicId: UUID = UUID.randomUUID()
            override val invoiceNumber: String = "INV-2026-001"
            override val clubName: String = "Elixir Gym"
            override val memberName: String = "Ahmed Al-Rashidi"
            override val amountHalalas: Long = 17250L
            override val createdAt: Instant = Instant.parse("2026-04-07T10:00:00Z")
            override val zatcaRetryCount: Int = 5
            override val zatcaLastError: String = "ZATCA rejected: invalid hash"
            override val zatcaStatus: String = "failed"
        }
        whenever(invoiceRepository.findFailedZatcaInvoicesWithClub()).thenReturn(listOf(projection))

        val result = service.getFailedInvoices()

        assertThat(result).hasSize(1)
        val dto = result[0]
        assertThat(dto.invoicePublicId).isEqualTo(projection.invoicePublicId)
        assertThat(dto.invoiceNumber).isEqualTo("INV-2026-001")
        assertThat(dto.clubName).isEqualTo("Elixir Gym")
        assertThat(dto.memberName).isEqualTo("Ahmed Al-Rashidi")
        assertThat(dto.amountSar).isEqualTo("172.50")
        assertThat(dto.zatcaRetryCount).isEqualTo(5)
        assertThat(dto.zatcaLastError).isEqualTo("ZATCA rejected: invalid hash")
        assertThat(dto.zatcaStatus).isEqualTo("failed")
    }

    @Test
    fun `expiring soon count is 0 when no CSIDs near threshold`() {
        whenever(certRepository.countByStatusAndNotDeleted("active")).thenReturn(3L)
        whenever(certRepository.countExpiringSoon(any())).thenReturn(0L)
        whenever(certRepository.countByStatusNotAndNotDeleted("active")).thenReturn(0L)
        whenever(invoiceRepository.countPendingZatcaReporting()).thenReturn(0L)
        whenever(invoiceRepository.countFailedZatcaReporting()).thenReturn(0L)
        whenever(invoiceRepository.countInvoicesApproachingDeadline(any())).thenReturn(0L)

        val summary = service.getHealthSummary()

        assertThat(summary.csidsExpiringSoon).isEqualTo(0L)
        assertThat(summary.clubsNotOnboarded).isEqualTo(0L)
        assertThat(summary.invoicesFailed).isEqualTo(0L)
        assertThat(summary.invoicesDeadlineAtRisk).isEqualTo(0L)
    }
}
