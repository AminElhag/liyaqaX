package com.liyaqa.member

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.security.JwtService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberActivationPulseControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var roleRepository: RoleRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var userRoleRepository: UserRoleRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var memberRegistrationIntentRepository: MemberRegistrationIntentRepository

    @Autowired lateinit var membershipRepository: MembershipRepository

    @Autowired lateinit var membershipPlanRepository: MembershipPlanRepository

    @Autowired lateinit var emergencyContactRepository: EmergencyContactRepository

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var plan: MembershipPlan

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))
        roleRepository.save(
            Role(nameAr = "عضو", nameEn = "Member", scope = "member", organizationId = org.id, clubId = club.id),
        )
        plan =
            membershipPlanRepository.save(
                MembershipPlan(
                    organizationId = org.id, clubId = club.id,
                    nameAr = "شهري", nameEn = "Monthly",
                    priceHalalas = 15000, durationDays = 30,
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        membershipRepository.deleteAllInBatch()
        memberRegistrationIntentRepository.deleteAllInBatch()
        emergencyContactRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        membershipPlanRepository.deleteAllInBatch()
        userRoleRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        roleRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun bearerToken(vararg permissions: String): String {
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
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    private fun createPendingMember(phone: String = "+966509999999"): Member {
        val user =
            userRepository.save(
                User(
                    email = "pending-${UUID.randomUUID()}@self-registration.internal",
                    passwordHash = "hashed",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        return memberRepository.save(
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                userId = user.id, firstNameAr = "أحمد", firstNameEn = "Ahmed",
                lastNameAr = "الراشدي", lastNameEn = "Al-Rashidi",
                phone = phone, membershipStatus = "pending_activation",
            ),
        )
    }

    // ── GET /members/pending ────────────────────────────────────

    @Test
    fun `pendingList onlyShowsPendingStatus`() {
        val token = bearerToken("member:read")
        createPendingMember("+966501111111")
        createPendingMember("+966502222222")

        // Also create an active member that should NOT appear
        val activeUser =
            userRepository.save(
                User(
                    email = "active-${UUID.randomUUID()}@test.com",
                    passwordHash = "hashed",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        memberRepository.save(
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                userId = activeUser.id, firstNameAr = "فعال", firstNameEn = "Active",
                lastNameAr = "م", lastNameEn = "M",
                phone = "+966503333333", membershipStatus = "active",
            ),
        )

        mockMvc.get("/api/v1/members/pending") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items", hasSize<Int>(2))
        }
    }

    // ── POST /members/{id}/activate ────────────────────────────

    @Test
    fun `activate setsStatusActive`() {
        val token = bearerToken("member:create")
        val member = createPendingMember()

        mockMvc.post("/api/v1/members/${member.publicId}/activate") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.membershipStatus", equalTo("active"))
        }
    }

    @Test
    fun `activate withPlan createsPendingMembership`() {
        val token = bearerToken("member:create")
        val member = createPendingMember()

        mockMvc.post("/api/v1/members/${member.publicId}/activate") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"membershipPlanId":"${plan.publicId}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.membershipStatus", equalTo("active"))
        }

        // Verify membership was created
        val memberships = membershipRepository.findAll()
        assert(memberships.any { it.memberId == member.id && it.membershipStatus == "pending" })
    }

    @Test
    fun `activate alreadyActive returns 409`() {
        val token = bearerToken("member:create")
        val user =
            userRepository.save(
                User(
                    email = "active-${UUID.randomUUID()}@test.com",
                    passwordHash = "hashed",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        val activeMember =
            memberRepository.save(
                Member(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    userId = user.id, firstNameAr = "أ", firstNameEn = "A",
                    lastNameAr = "ب", lastNameEn = "B",
                    phone = "+966508888888", membershipStatus = "active",
                ),
            )

        mockMvc.post("/api/v1/members/${activeMember.publicId}/activate") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    // ── POST /members/{id}/reject ──────────────────────────────

    @Test
    fun `reject createsNoteAndTerminates`() {
        val token = bearerToken("member:create")
        val member = createPendingMember()

        mockMvc.post("/api/v1/members/${member.publicId}/reject") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"Insufficient documentation provided by the applicant"}"""
        }.andExpect {
            status { isOk() }
        }

        val updated = memberRepository.findById(member.id).get()
        assert(updated.membershipStatus == "terminated")
        assert(updated.notes?.contains("Registration rejected") == true)
    }

    @Test
    fun `reject alreadyActive returns 409`() {
        val token = bearerToken("member:create")
        val user =
            userRepository.save(
                User(
                    email = "active2-${UUID.randomUUID()}@test.com",
                    passwordHash = "hashed",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        val activeMember =
            memberRepository.save(
                Member(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    userId = user.id, firstNameAr = "أ", firstNameEn = "A",
                    lastNameAr = "ب", lastNameEn = "B",
                    phone = "+966507777777", membershipStatus = "active",
                ),
            )

        mockMvc.post("/api/v1/members/${activeMember.publicId}/reject") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"Insufficient documentation provided by the applicant"}"""
        }.andExpect {
            status { isConflict() }
        }
    }
}
