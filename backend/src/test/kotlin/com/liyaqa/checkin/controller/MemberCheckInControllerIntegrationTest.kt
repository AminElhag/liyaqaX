package com.liyaqa.checkin.controller

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.checkin.entity.MemberCheckIn
import com.liyaqa.checkin.repository.MemberCheckInRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberCheckInControllerIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var checkInRepository: MemberCheckInRepository

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000020")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var staffUser: User
    private lateinit var member: Member
    private lateinit var memberUser: User

    companion object {
        private const val TEST_PASSWORD = "Test@12345678"
    }

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Test Branch"))

        staffUser =
            userRepository.save(
                User(
                    email = "staff-checkin@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        memberUser =
            userRepository.save(
                User(
                    email = "member-checkin@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        member =
            memberRepository.save(
                Member(
                    organizationId = org.id, clubId = club.id, branchId = branch.id, userId = memberUser.id,
                    firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "الرشيدي", lastNameEn = "Al-Rashidi",
                    phone = "+966501234567", membershipStatus = "active",
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        checkInRepository.deleteAllInBatch()
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
                "branchIds" to listOf(branch.publicId.toString()),
            )
        return "Bearer ${jwtService.generateToken(staffUser.publicId.toString(), claims)}"
    }

    private fun forbiddenToken(): String {
        val claims =
            mapOf(
                "roleId" to noPermRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
                "branchIds" to listOf(branch.publicId.toString()),
            )
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    private fun arenaToken(): String {
        val claims =
            mapOf(
                "scope" to "member",
                "memberId" to member.publicId.toString(),
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(memberUser.publicId.toString(), claims)}"
    }

    // ── POST /pulse/check-in ─────────────────────────────────────────────────

    @Test
    fun `POST pulse check-in returns 201 for active member`() {
        val token = pulseToken("check-in:create")

        mockMvc.post("/api/v1/pulse/check-in") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"memberPublicId":"${member.publicId}","method":"staff_phone"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.checkInId", notNullValue())
            jsonPath("$.memberName", equalTo("Ahmed Al-Rashidi"))
            jsonPath("$.method", equalTo("staff_phone"))
            jsonPath("$.todayCount", notNullValue())
        }
    }

    @Test
    fun `POST pulse check-in returns 409 for lapsed member`() {
        member.membershipStatus = "lapsed"
        memberRepository.save(member)

        val token = pulseToken("check-in:create")

        mockMvc.post("/api/v1/pulse/check-in") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"memberPublicId":"${member.publicId}","method":"staff_phone"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.errorCode", equalTo("MEMBERSHIP_LAPSED"))
        }
    }

    @Test
    fun `POST pulse check-in returns 409 for duplicate check-in within 60 minutes`() {
        val token = pulseToken("check-in:create")

        checkInRepository.save(
            MemberCheckIn(
                memberId = member.id,
                branchId = branch.id,
                checkedInByUserId = staffUser.id,
                method = "staff_phone",
                checkedInAt = Instant.now().minusSeconds(300),
            ),
        )

        mockMvc.post("/api/v1/pulse/check-in") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"memberPublicId":"${member.publicId}","method":"staff_phone"}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `POST pulse check-in returns 403 without check-in create permission`() {
        mockMvc.post("/api/v1/pulse/check-in") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"memberPublicId":"${member.publicId}","method":"staff_phone"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── GET /pulse/check-in/today-count ──────────────────────────────────────

    @Test
    fun `GET pulse check-in today-count returns count for active branch`() {
        val token = pulseToken("check-in:read")

        mockMvc.get("/api/v1/pulse/check-in/today-count") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.count", notNullValue())
            jsonPath("$.branchName", equalTo("Test Branch"))
        }
    }

    // ── GET /pulse/check-in/recent ───────────────────────────────────────────

    @Test
    fun `GET pulse check-in recent returns last 20 check-ins`() {
        val token = pulseToken("check-in:read")

        checkInRepository.save(
            MemberCheckIn(
                memberId = member.id,
                branchId = branch.id,
                checkedInByUserId = staffUser.id,
                method = "qr_scan",
            ),
        )

        mockMvc.get("/api/v1/pulse/check-in/recent") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.checkIns.length()", equalTo(1))
        }
    }

    @Test
    fun `GET pulse check-in recent returns 403 without check-in read permission`() {
        mockMvc.get("/api/v1/pulse/check-in/recent") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── GET /arena/me/qr ─────────────────────────────────────────────────────

    @Test
    fun `GET arena me qr returns member publicId`() {
        val token = arenaToken()

        mockMvc.get("/api/v1/arena/me/qr") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.qrValue", equalTo(member.publicId.toString()))
        }
    }

    @Test
    fun `GET arena me qr returns 401 without arena JWT`() {
        mockMvc.get("/api/v1/arena/me/qr") {
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
