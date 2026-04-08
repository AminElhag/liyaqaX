package com.liyaqa.payment.online.controller

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.Membership
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.online.client.MoyasarClient
import com.liyaqa.payment.online.client.MoyasarPaymentResponse
import com.liyaqa.payment.online.entity.OnlinePaymentTransaction
import com.liyaqa.payment.online.repository.OnlinePaymentTransactionRepository
import com.liyaqa.payment.online.service.MoyasarWebhookVerifier
import com.liyaqa.portal.ClubPortalSettings
import com.liyaqa.portal.ClubPortalSettingsRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnlinePaymentControllerIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var passwordEncoder: PasswordEncoder
    @Autowired lateinit var organizationRepository: OrganizationRepository
    @Autowired lateinit var clubRepository: ClubRepository
    @Autowired lateinit var branchRepository: BranchRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var membershipPlanRepository: MembershipPlanRepository
    @Autowired lateinit var membershipRepository: MembershipRepository
    @Autowired lateinit var transactionRepository: OnlinePaymentTransactionRepository
    @Autowired lateinit var portalSettingsRepository: ClubPortalSettingsRepository

    @MockBean lateinit var permissionService: PermissionService
    @MockBean lateinit var moyasarClient: MoyasarClient
    @MockBean lateinit var webhookVerifier: MoyasarWebhookVerifier

    private val staffRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000020")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var memberUser: User
    private lateinit var staffUser: User
    private lateinit var member: Member
    private lateinit var plan: MembershipPlan
    private lateinit var membership: Membership
    private lateinit var portalSettings: ClubPortalSettings

    companion object {
        private const val TEST_PASSWORD = "Test@12345678"
    }

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))

        memberUser = userRepository.save(
            User(email = "member@test.com", passwordHash = passwordEncoder.encode(TEST_PASSWORD), organizationId = org.id, clubId = club.id),
        )
        staffUser = userRepository.save(
            User(email = "staff@test.com", passwordHash = passwordEncoder.encode(TEST_PASSWORD), organizationId = org.id, clubId = club.id),
        )

        member = memberRepository.save(
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id, userId = memberUser.id,
                firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "الرشيدي", lastNameEn = "Al-Rashidi",
                phone = "+966501234567", membershipStatus = "pending",
            ),
        )

        plan = membershipPlanRepository.save(
            MembershipPlan(
                organizationId = org.id, clubId = club.id,
                nameAr = "أساسي شهري", nameEn = "Basic Monthly",
                priceHalalas = 15000L, durationDays = 30, gracePeriodDays = 0,
            ),
        )

        membership = membershipRepository.save(
            Membership(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                memberId = member.id, planId = plan.id,
                membershipStatus = "pending_payment",
                startDate = LocalDate.now(), endDate = LocalDate.now().plusDays(30),
            ),
        )

        portalSettings = portalSettingsRepository.save(ClubPortalSettings(clubId = club.id).also { it.onlinePaymentEnabled = true })
    }

    @AfterEach
    fun cleanup() {
        transactionRepository.deleteAllInBatch()
        membershipRepository.deleteAllInBatch()
        membershipPlanRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        portalSettingsRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun memberToken(): String {
        val claims = mapOf(
            "scope" to "member",
            "memberId" to member.publicId.toString(),
            "organizationId" to org.publicId.toString(),
            "clubId" to club.publicId.toString(),
            "branchId" to branch.publicId.toString(),
        )
        return "Bearer ${jwtService.generateToken(memberUser.publicId.toString(), claims)}"
    }

    private fun pulseToken(vararg permissions: String): String {
        permissions.forEach { perm ->
            whenever(permissionService.hasPermission(staffRoleId, perm)).thenReturn(true)
        }
        val claims = mapOf(
            "roleId" to staffRoleId.toString(),
            "scope" to "club",
            "organizationId" to org.publicId.toString(),
            "clubId" to club.publicId.toString(),
        )
        return "Bearer ${jwtService.generateToken(staffUser.publicId.toString(), claims)}"
    }

    private fun forbiddenPulseToken(): String {
        val claims = mapOf(
            "roleId" to noPermRoleId.toString(),
            "scope" to "club",
            "organizationId" to org.publicId.toString(),
            "clubId" to club.publicId.toString(),
        )
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    // ── POST /arena/payments/initiate ──────────────────────────────────

    @Test
    fun `POST arena payments initiate returns 201 with hostedUrl`() {
        whenever(moyasarClient.buildCallbackUrl(any())).thenReturn("https://test/callback")
        whenever(moyasarClient.createPayment(any())).thenReturn(
            MoyasarPaymentResponse(id = "pay_new", status = "initiated", amount = 15000, url = "https://hosted.url"),
        )

        mockMvc.post("/api/v1/arena/payments/initiate") {
            header("Authorization", memberToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"membershipPublicId":"${membership.publicId}"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.hostedUrl", equalTo("https://hosted.url"))
            jsonPath("$.amountSar", equalTo("150.00"))
        }
    }

    @Test
    fun `POST arena payments initiate returns 403 when feature disabled`() {
        portalSettings.onlinePaymentEnabled = false
        portalSettingsRepository.save(portalSettings)

        mockMvc.post("/api/v1/arena/payments/initiate") {
            header("Authorization", memberToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"membershipPublicId":"${membership.publicId}"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST arena payments initiate returns 409 for non-payable membership`() {
        membership.membershipStatus = "active"
        membershipRepository.save(membership)

        mockMvc.post("/api/v1/arena/payments/initiate") {
            header("Authorization", memberToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"membershipPublicId":"${membership.publicId}"}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `POST arena payments initiate returns 201 for LAPSED member`() {
        member.membershipStatus = "lapsed"
        memberRepository.save(member)

        whenever(moyasarClient.buildCallbackUrl(any())).thenReturn("https://test/callback")
        whenever(moyasarClient.createPayment(any())).thenReturn(
            MoyasarPaymentResponse(id = "pay_lapsed", status = "initiated", amount = 15000, url = "https://hosted.url"),
        )

        mockMvc.post("/api/v1/arena/payments/initiate") {
            header("Authorization", memberToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"membershipPublicId":"${membership.publicId}"}"""
        }.andExpect {
            status { isCreated() }
        }
    }

    // ── GET /arena/payments/{id}/status ────────────────────────────────

    @Test
    fun `GET arena payments status returns INITIATED status`() {
        val tx = transactionRepository.save(
            OnlinePaymentTransaction(
                moyasarId = "pay_status1", membershipId = membership.id, memberId = member.id,
                clubId = club.id, amountHalalas = 15000L, status = "INITIATED",
                moyasarHostedUrl = "https://hosted.url",
            ),
        )

        mockMvc.get("/api/v1/arena/payments/pay_status1/status") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", equalTo("INITIATED"))
        }
    }

    // ── GET /arena/payments/history ────────────────────────────────────

    @Test
    fun `GET arena payments history returns member transactions`() {
        transactionRepository.save(
            OnlinePaymentTransaction(
                moyasarId = "pay_hist1", membershipId = membership.id, memberId = member.id,
                clubId = club.id, amountHalalas = 15000L, status = "PAID",
                moyasarHostedUrl = "https://hosted.url",
            ),
        )

        mockMvc.get("/api/v1/arena/payments/history") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isOk() }
        }
    }

    // ── POST /webhooks/moyasar ─────────────────────────────────────────

    @Test
    fun `POST webhooks moyasar returns 200 and activates membership on paid`() {
        val tx = transactionRepository.save(
            OnlinePaymentTransaction(
                moyasarId = "pay_webhook1", membershipId = membership.id, memberId = member.id,
                clubId = club.id, amountHalalas = 15000L, status = "INITIATED",
                moyasarHostedUrl = "https://hosted.url",
            ),
        )

        whenever(webhookVerifier.verify(any(), any())).thenReturn(true)

        val webhookBody = """{"id":"pay_webhook1","type":"payment_paid","data":{"id":"pay_webhook1","status":"paid","amount":15000,"source":{"type":"mada"}}}"""

        mockMvc.post("/api/v1/webhooks/moyasar") {
            contentType = MediaType.APPLICATION_JSON
            content = webhookBody
            header("Moyasar-Signature", "valid-sig")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `POST webhooks moyasar returns 200 and fails gracefully on failed payment`() {
        transactionRepository.save(
            OnlinePaymentTransaction(
                moyasarId = "pay_fail1", membershipId = membership.id, memberId = member.id,
                clubId = club.id, amountHalalas = 15000L, status = "INITIATED",
                moyasarHostedUrl = "https://hosted.url",
            ),
        )

        whenever(webhookVerifier.verify(any(), any())).thenReturn(true)

        val webhookBody = """{"data":{"id":"pay_fail1","status":"failed","amount":15000}}"""

        mockMvc.post("/api/v1/webhooks/moyasar") {
            contentType = MediaType.APPLICATION_JSON
            content = webhookBody
            header("Moyasar-Signature", "valid-sig")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `POST webhooks moyasar returns 401 on invalid signature`() {
        whenever(webhookVerifier.verify(any(), any())).thenReturn(false)

        val webhookBody = """{"data":{"id":"pay_bad","status":"paid","amount":15000}}"""

        mockMvc.post("/api/v1/webhooks/moyasar") {
            contentType = MediaType.APPLICATION_JSON
            content = webhookBody
            header("Moyasar-Signature", "invalid")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `POST webhooks moyasar is idempotent on duplicate paid event`() {
        transactionRepository.save(
            OnlinePaymentTransaction(
                moyasarId = "pay_dup1", membershipId = membership.id, memberId = member.id,
                clubId = club.id, amountHalalas = 15000L, status = "PAID",
                moyasarHostedUrl = "https://hosted.url",
            ),
        )

        whenever(webhookVerifier.verify(any(), any())).thenReturn(true)

        val webhookBody = """{"data":{"id":"pay_dup1","status":"paid","amount":15000,"source":{"type":"mada"}}}"""

        mockMvc.post("/api/v1/webhooks/moyasar") {
            contentType = MediaType.APPLICATION_JSON
            content = webhookBody
            header("Moyasar-Signature", "valid-sig")
        }.andExpect {
            status { isOk() }
        }
    }

    // ── GET /pulse/members/{id}/online-payments ────────────────────────

    @Test
    fun `GET pulse members online-payments returns transactions`() {
        transactionRepository.save(
            OnlinePaymentTransaction(
                moyasarId = "pay_pulse1", membershipId = membership.id, memberId = member.id,
                clubId = club.id, amountHalalas = 15000L, status = "PAID",
                moyasarHostedUrl = "https://hosted.url",
            ),
        )

        mockMvc.get("/api/v1/pulse/members/${member.publicId}/online-payments") {
            header("Authorization", pulseToken("online-payment:read"))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `GET pulse members online-payments returns 403 without online-payment read`() {
        mockMvc.get("/api/v1/pulse/members/${member.publicId}/online-payments") {
            header("Authorization", forbiddenPulseToken())
        }.andExpect {
            status { isForbidden() }
        }
    }
}
