package com.liyaqa.membership.controller

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberNoteRepository
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.Membership
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.membership.service.MemberLapseService
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberLapseControllerIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var membershipRepository: MembershipRepository

    @Autowired lateinit var membershipPlanRepository: MembershipPlanRepository

    @Autowired lateinit var memberNoteRepository: MemberNoteRepository

    @Autowired lateinit var memberLapseService: MemberLapseService

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000020")
    private val memberRoleId = UUID.fromString("00000000-0000-0000-0000-000000000030")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var staffUser: User
    private lateinit var member: Member
    private lateinit var memberUser: User
    private lateinit var plan: MembershipPlan
    private lateinit var lapsedMembership: Membership

    companion object {
        private const val TEST_PASSWORD = "Test@12345678"
    }

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "مؤسسة", nameEn = "Test Org", email = "lapse-test@liyaqa.com"))
        club =
            clubRepository.save(
                Club(
                    organizationId = org.id,
                    nameAr = "نادي",
                    nameEn = "Test Club",
                ),
            )
        branch =
            branchRepository.save(
                Branch(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "فرع",
                    nameEn = "Test Branch",
                    phone = "+966500000000",
                ),
            )
        staffUser =
            userRepository.save(
                User(
                    email = "staff-lapse@test.com",
                    passwordHash = "hash",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        memberUser =
            userRepository.save(
                User(
                    email = "member-lapse@test.com",
                    passwordHash = "hash",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        plan =
            membershipPlanRepository.save(
                MembershipPlan(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "خطة",
                    nameEn = "Basic Plan",
                    priceHalalas = 15000,
                    durationDays = 30,
                ),
            )
        member =
            memberRepository.save(
                Member(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    userId = memberUser.id,
                    firstNameAr = "سارة",
                    firstNameEn = "Sarah",
                    lastNameAr = "الزهراني",
                    lastNameEn = "Al-Zahrani",
                    phone = "+966501234567",
                    membershipStatus = "lapsed",
                ),
            )
        lapsedMembership =
            membershipRepository.save(
                Membership(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    memberId = member.id,
                    planId = plan.id,
                    membershipStatus = "lapsed",
                    startDate = LocalDate.now().minusDays(60),
                    endDate = LocalDate.now().minusDays(1),
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        memberNoteRepository.deleteAllInBatch()
        membershipRepository.deleteAllInBatch()
        membershipPlanRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun pulseToken(vararg permissions: String): String {
        permissions.forEach { perm ->
            whenever(permissionService.hasPermission(callerRoleId, perm)).thenReturn(true)
        }
        val claims =
            mapOf(
                "roleId" to callerRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(staffUser.publicId.toString(), claims)}"
    }

    private fun noPermToken(): String {
        val claims =
            mapOf(
                "roleId" to noPermRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(staffUser.publicId.toString(), claims)}"
    }

    private fun arenaToken(): String {
        val claims =
            mapOf(
                "roleId" to memberRoleId.toString(),
                "scope" to "member",
                "memberId" to member.publicId.toString(),
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
                "branchId" to branch.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(memberUser.publicId.toString(), claims)}"
    }

    @Test
    fun `GET lapsed returns paginated lapsed members`() {
        val token = pulseToken("membership:read")
        mockMvc.get("/api/v1/pulse/memberships/lapsed") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.total", greaterThanOrEqualTo(1))
            jsonPath("$.members[0].memberPublicId", equalTo(member.publicId.toString()))
            jsonPath("$.members[0].nameEn", equalTo("Sarah Al-Zahrani"))
        }
    }

    @Test
    fun `GET lapsed returns 403 without membership read permission`() {
        val token = noPermToken()
        mockMvc.get("/api/v1/pulse/memberships/lapsed") {
            header("Authorization", token)
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST renewal-offer creates follow_up note`() {
        val token = pulseToken("member-note:create")
        mockMvc.post("/api/v1/pulse/members/${member.publicId}/renewal-offer") {
            header("Authorization", token)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.message") { isNotEmpty() }
        }
    }

    @Test
    fun `POST renewal-offer-bulk creates notes for all given members`() {
        val token = pulseToken("member-note:create")
        mockMvc.post("/api/v1/pulse/memberships/lapsed/renewal-offer-bulk") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"memberPublicIds":["${member.publicId}"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.created", greaterThanOrEqualTo(1))
        }
    }

    @Test
    fun `POST renewal-offer-bulk returns 400 when more than 100 IDs provided`() {
        val token = pulseToken("member-note:create")
        val ids = (1..101).map { "\"${UUID.randomUUID()}\"" }.joinToString(",")
        mockMvc.post("/api/v1/pulse/memberships/lapsed/renewal-offer-bulk") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"memberPublicIds":[$ids]}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `reactivation sets lapsed member status to ACTIVE`() {
        assert(member.membershipStatus == "lapsed") {
            "Precondition: member should be lapsed"
        }

        memberLapseService.reactivateMemberIfLapsed(member.id)

        val updatedMember = memberRepository.findById(member.id).get()
        assert(updatedMember.membershipStatus == "active") {
            "Expected member status to be active but was ${updatedMember.membershipStatus}"
        }
    }

    @Test
    fun `lapsed member in web-arena receives 403 on GX booking endpoint`() {
        val token = arenaToken()
        mockMvc.post("/api/v1/arena/gx/${UUID.randomUUID()}/book") {
            header("Authorization", token)
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.errorCode", equalTo("MEMBERSHIP_LAPSED"))
        }
    }

    @Test
    fun `lapsed member in web-arena can still access GET me`() {
        val token = arenaToken()
        mockMvc.get("/api/v1/arena/me") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.memberStatus", equalTo("lapsed"))
        }
    }
}
