package com.liyaqa.payment.online.service

import com.liyaqa.audit.AuditService
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.InvoiceService
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.Membership
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.Payment
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.payment.online.client.MoyasarClient
import com.liyaqa.payment.online.client.MoyasarPaymentResponse
import com.liyaqa.payment.online.entity.OnlinePaymentTransaction
import com.liyaqa.payment.online.repository.OnlinePaymentTransactionRepository
import com.liyaqa.portal.ClubPortalSettings
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OnlinePaymentServiceTest {
    @Mock lateinit var transactionRepository: OnlinePaymentTransactionRepository
    @Mock lateinit var memberRepository: MemberRepository
    @Mock lateinit var membershipRepository: MembershipRepository
    @Mock lateinit var membershipPlanRepository: MembershipPlanRepository
    @Mock lateinit var clubRepository: ClubRepository
    @Mock lateinit var organizationRepository: OrganizationRepository
    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var paymentRepository: PaymentRepository
    @Mock lateinit var invoiceService: InvoiceService
    @Mock lateinit var portalSettingsService: ClubPortalSettingsService
    @Mock lateinit var moyasarClient: MoyasarClient
    @Mock lateinit var webhookVerifier: MoyasarWebhookVerifier
    @Mock lateinit var auditService: AuditService

    @InjectMocks lateinit var service: OnlinePaymentService

    private val orgId = 1L
    private val clubId = 1L

    private fun member(status: String = "active") = Member(
        organizationId = orgId, clubId = clubId, branchId = 1L, userId = 1L,
        firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "الرشيدي", lastNameEn = "Al-Rashidi",
        phone = "+966501234567", membershipStatus = status,
    )

    private fun club() = Club(organizationId = orgId, nameAr = "نادي", nameEn = "Test Club")

    private fun plan() = MembershipPlan(
        organizationId = orgId, clubId = clubId, nameAr = "أساسي", nameEn = "Basic Monthly",
        priceHalalas = 15000L, durationDays = 30, gracePeriodDays = 0,
    )

    private fun membership(status: String = "pending_payment") = Membership(
        organizationId = orgId, clubId = clubId, branchId = 1L, memberId = 1L, planId = 1L,
        membershipStatus = status, startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(30),
    )

    private fun settings(enabled: Boolean = true): ClubPortalSettings {
        val s = ClubPortalSettings(clubId = clubId)
        s.onlinePaymentEnabled = enabled
        return s
    }

    private fun transaction(status: String = "INITIATED") = OnlinePaymentTransaction(
        moyasarId = "pay_test123", membershipId = 1L, memberId = 1L, clubId = clubId,
        amountHalalas = 15000L, status = status, moyasarHostedUrl = "https://payment.moyasar.com/test",
    )

    // ── initiatePayment tests ──────────────────────────────────────────

    @Test
    fun `initiatePayment returns hostedUrl for pending_payment membership`() {
        val m = member("pending")
        val c = club()
        val ms = membership("pending_payment")
        val p = plan()
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(m))
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(c))
        whenever(portalSettingsService.getOrCreateSettings(any())).thenReturn(settings(true))
        whenever(membershipRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(ms))
        whenever(transactionRepository.findInitiatedByMembership(any())).thenReturn(null)
        whenever(membershipPlanRepository.findById(any())).thenReturn(Optional.of(p))
        whenever(moyasarClient.buildCallbackUrl(any())).thenReturn("https://test/callback")
        whenever(moyasarClient.createPayment(any())).thenReturn(
            MoyasarPaymentResponse(id = "pay_new", status = "initiated", amount = 15000, url = "https://hosted.url"),
        )
        whenever(transactionRepository.save(any<OnlinePaymentTransaction>())).thenAnswer { it.arguments[0] }

        val result = service.initiatePayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        assertThat(result.hostedUrl).isEqualTo("https://hosted.url")
        assertThat(result.amountSar).isEqualTo("150.00")
    }

    @Test
    fun `initiatePayment returns existing INITIATED transaction when duplicate requested`() {
        val m = member("pending")
        val c = club()
        val ms = membership("pending_payment")
        val existing = transaction("INITIATED")
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(m))
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(c))
        whenever(portalSettingsService.getOrCreateSettings(any())).thenReturn(settings(true))
        whenever(membershipRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(ms))
        whenever(transactionRepository.findInitiatedByMembership(any())).thenReturn(existing)
        whenever(membershipPlanRepository.findById(any())).thenReturn(Optional.of(plan()))

        val result = service.initiatePayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        assertThat(result.hostedUrl).isEqualTo("https://payment.moyasar.com/test")
        verify(moyasarClient, never()).createPayment(any())
    }

    @Test
    fun `initiatePayment throws 403 when onlinePaymentEnabled is false`() {
        val m = member()
        val c = club()
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(m))
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(c))
        whenever(portalSettingsService.getOrCreateSettings(any())).thenReturn(settings(false))

        assertThatThrownBy {
            service.initiatePayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status").isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `initiatePayment throws 409 MEMBERSHIP_NOT_PAYABLE for active membership`() {
        val m = member("active")
        val c = club()
        val ms = membership("active")
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(m))
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(c))
        whenever(portalSettingsService.getOrCreateSettings(any())).thenReturn(settings(true))
        whenever(membershipRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(ms))

        assertThatThrownBy {
            service.initiatePayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status").isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `initiatePayment works for LAPSED member`() {
        val m = member("lapsed")
        val c = club()
        val ms = membership("expired")
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(m))
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(c))
        whenever(portalSettingsService.getOrCreateSettings(any())).thenReturn(settings(true))
        whenever(membershipRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(ms))
        whenever(transactionRepository.findInitiatedByMembership(any())).thenReturn(null)
        whenever(membershipPlanRepository.findById(any())).thenReturn(Optional.of(plan()))
        whenever(moyasarClient.buildCallbackUrl(any())).thenReturn("https://test/callback")
        whenever(moyasarClient.createPayment(any())).thenReturn(
            MoyasarPaymentResponse(id = "pay_lapsed", status = "initiated", amount = 15000, url = "https://hosted.url"),
        )
        whenever(transactionRepository.save(any<OnlinePaymentTransaction>())).thenAnswer { it.arguments[0] }

        val result = service.initiatePayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        assertThat(result.hostedUrl).isEqualTo("https://hosted.url")
    }

    // ── webhook tests ──────────────────────────────────────────────────

    @Test
    fun `handleWebhook activates membership on paid status`() {
        val tx = transaction("INITIATED")
        val ms = membership("pending_payment")
        val p = plan()
        val m = member("pending")
        val c = club()
        val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "org@t.com")

        whenever(webhookVerifier.verify(any(), any())).thenReturn(true)
        whenever(transactionRepository.findByMoyasarId("pay_test123")).thenReturn(tx)
        whenever(transactionRepository.save(any<OnlinePaymentTransaction>())).thenAnswer { it.arguments[0] }
        whenever(membershipRepository.findById(any())).thenReturn(Optional.of(ms))
        whenever(membershipPlanRepository.findById(any())).thenReturn(Optional.of(p))
        whenever(memberRepository.findById(any())).thenReturn(Optional.of(m))
        whenever(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        whenever(clubRepository.findById(any())).thenReturn(Optional.of(c))
        whenever(organizationRepository.findById(any())).thenReturn(Optional.of(org))
        whenever(userRepository.findById(any())).thenReturn(Optional.empty())
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        whenever(invoiceService.createInvoice(any(), any(), any(), any(), any())).thenReturn(any())

        service.handleWebhook("{}".toByteArray(), "sig", "pay_test123", "paid", "mada")

        assertThat(tx.status).isEqualTo("PAID")
        assertThat(tx.paymentMethod).isEqualTo("mada")
        verify(membershipRepository).activateMembership(any(), any(), any())
    }

    @Test
    fun `handleWebhook sets member status to ACTIVE when member was LAPSED`() {
        val tx = transaction("INITIATED")
        val ms = membership("pending_payment")
        val p = plan()
        val m = member("lapsed")
        val c = club()
        val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "org@t.com")

        whenever(webhookVerifier.verify(any(), any())).thenReturn(true)
        whenever(transactionRepository.findByMoyasarId("pay_test123")).thenReturn(tx)
        whenever(transactionRepository.save(any<OnlinePaymentTransaction>())).thenAnswer { it.arguments[0] }
        whenever(membershipRepository.findById(any())).thenReturn(Optional.of(ms))
        whenever(membershipPlanRepository.findById(any())).thenReturn(Optional.of(p))
        whenever(memberRepository.findById(any())).thenReturn(Optional.of(m))
        whenever(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        whenever(clubRepository.findById(any())).thenReturn(Optional.of(c))
        whenever(organizationRepository.findById(any())).thenReturn(Optional.of(org))
        whenever(userRepository.findById(any())).thenReturn(Optional.empty())
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        whenever(invoiceService.createInvoice(any(), any(), any(), any(), any())).thenReturn(any())

        service.handleWebhook("{}".toByteArray(), "sig", "pay_test123", "paid", "mada")

        assertThat(m.membershipStatus).isEqualTo("active")
    }

    @Test
    fun `handleWebhook creates Payment and Invoice records on success`() {
        val tx = transaction("INITIATED")
        val ms = membership("pending_payment")
        val p = plan()
        val m = member("pending")
        val c = club()
        val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "org@t.com")

        whenever(webhookVerifier.verify(any(), any())).thenReturn(true)
        whenever(transactionRepository.findByMoyasarId("pay_test123")).thenReturn(tx)
        whenever(transactionRepository.save(any<OnlinePaymentTransaction>())).thenAnswer { it.arguments[0] }
        whenever(membershipRepository.findById(any())).thenReturn(Optional.of(ms))
        whenever(membershipPlanRepository.findById(any())).thenReturn(Optional.of(p))
        whenever(memberRepository.findById(any())).thenReturn(Optional.of(m))
        whenever(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        whenever(clubRepository.findById(any())).thenReturn(Optional.of(c))
        whenever(organizationRepository.findById(any())).thenReturn(Optional.of(org))
        whenever(userRepository.findById(any())).thenReturn(Optional.empty())
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        whenever(invoiceService.createInvoice(any(), any(), any(), any(), any())).thenReturn(any())

        service.handleWebhook("{}".toByteArray(), "sig", "pay_test123", "paid", "mada")

        verify(paymentRepository).save(any())
        verify(invoiceService).createInvoice(any(), any(), any(), any(), any())
    }

    @Test
    fun `handleWebhook is idempotent when transaction already PAID`() {
        val tx = transaction("PAID")

        whenever(webhookVerifier.verify(any(), any())).thenReturn(true)
        whenever(transactionRepository.findByMoyasarId("pay_test123")).thenReturn(tx)

        service.handleWebhook("{}".toByteArray(), "sig", "pay_test123", "paid", "mada")

        verify(paymentRepository, never()).save(any())
        verify(invoiceService, never()).createInvoice(any(), any(), any(), any(), any())
    }

    @Test
    fun `handleWebhook sets status FAILED on failed payment`() {
        val tx = transaction("INITIATED")

        whenever(webhookVerifier.verify(any(), any())).thenReturn(true)
        whenever(transactionRepository.findByMoyasarId("pay_test123")).thenReturn(tx)
        whenever(transactionRepository.save(any<OnlinePaymentTransaction>())).thenAnswer { it.arguments[0] }

        service.handleWebhook("{}".toByteArray(), "sig", "pay_test123", "failed", null)

        assertThat(tx.status).isEqualTo("FAILED")
    }

    @Test
    fun `handleWebhook throws 401 on invalid signature`() {
        whenever(webhookVerifier.verify(any(), any())).thenReturn(false)

        assertThatThrownBy {
            service.handleWebhook("{}".toByteArray(), "invalid", "pay_test123", "paid", "mada")
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
