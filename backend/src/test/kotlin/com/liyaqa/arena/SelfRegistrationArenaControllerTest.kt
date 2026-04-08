package com.liyaqa.arena

import com.liyaqa.auth.MemberOtp
import com.liyaqa.auth.MemberOtpRepository
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRegistrationIntentRepository
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.portal.ClubPortalSettings
import com.liyaqa.portal.ClubPortalSettingsRepository
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.security.JwtService
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.security.MessageDigest
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SelfRegistrationArenaControllerTest {
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

    @Autowired lateinit var portalSettingsRepository: ClubPortalSettingsRepository

    @Autowired lateinit var otpRepository: MemberOtpRepository

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch

    companion object {
        private const val PHONE = "+966501111111"
        private const val OTP = "123456"
    }

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))
        roleRepository.save(
            Role(nameAr = "عضو", nameEn = "Member", scope = "member", organizationId = org.id, clubId = club.id),
        )
    }

    @AfterEach
    fun cleanup() {
        memberRegistrationIntentRepository.deleteAllInBatch()
        otpRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        userRoleRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        portalSettingsRepository.deleteAllInBatch()
        roleRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun enableSelfRegistration() {
        portalSettingsRepository.save(
            ClubPortalSettings(clubId = club.id, selfRegistrationEnabled = true),
        )
    }

    private fun disableSelfRegistration() {
        portalSettingsRepository.save(
            ClubPortalSettings(clubId = club.id, selfRegistrationEnabled = false),
        )
    }

    private fun createValidOtp(phone: String = PHONE): MemberOtp {
        val hash = sha256(OTP)
        return otpRepository.save(
            MemberOtp(
                phone = phone,
                otpHash = hash,
                expiresAt = Instant.now().plusSeconds(600),
            ),
        )
    }

    private fun registrationToken(phone: String = PHONE): String = jwtService.generateRegistrationToken(phone, club.id)

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // ── Registration disabled ─────────────────────────────────────

    @Test
    fun `registrationDisabled returns 403 on OTP request`() {
        disableSelfRegistration()

        mockMvc.post("/api/v1/arena/register/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone":"$PHONE","clubId":"${club.publicId}"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── Check endpoint ────────────────────────────────────────────

    @Test
    fun `check returns selfRegistrationEnabled status`() {
        enableSelfRegistration()

        mockMvc.get("/api/v1/arena/register/check?clubId=${club.publicId}")
            .andExpect {
                status { isOk() }
                jsonPath("$.selfRegistrationEnabled", equalTo(true))
            }
    }

    // ── Complete registration ─────────────────────────────────────

    @Test
    fun `validRegistration withPlan returns 201`() {
        enableSelfRegistration()
        val token = registrationToken()

        mockMvc.post("/api/v1/arena/register/complete") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "nameAr": "أحمد الراشدي",
                    "nameEn": "Ahmed Al-Rashidi",
                    "email": "ahmed-reg@test.com",
                    "gender": "male"
                }
                """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.memberId", notNullValue())
            jsonPath("$.status", equalTo("pending_activation"))
        }
    }

    @Test
    fun `validRegistration withoutPlan returns 201`() {
        enableSelfRegistration()
        val token = registrationToken()

        mockMvc.post("/api/v1/arena/register/complete") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "أحمد"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status", equalTo("pending_activation"))
        }
    }

    @Test
    fun `invalidRegistrationToken returns 401`() {
        enableSelfRegistration()

        mockMvc.post("/api/v1/arena/register/complete") {
            header("Authorization", "Bearer invalid-token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "أحمد"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `bothNamesBlank returns 422`() {
        enableSelfRegistration()
        val token = registrationToken()

        mockMvc.post("/api/v1/arena/register/complete") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameEn": "", "nameAr": ""}"""
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    fun `duplicatePhone activeStatus returns 409`() {
        enableSelfRegistration()
        // Create an existing active member with this phone
        val user =
            userRepository.save(
                com.liyaqa.user.User(
                    email = "existing@test.com",
                    passwordHash = "hashed",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        memberRepository.save(
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                userId = user.id, firstNameAr = "أ", firstNameEn = "A",
                lastNameAr = "ب", lastNameEn = "B", phone = PHONE,
                membershipStatus = "active",
            ),
        )

        val token = registrationToken()

        mockMvc.post("/api/v1/arena/register/complete") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "أحمد"}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `duplicatePhone pendingStatus returns 409`() {
        enableSelfRegistration()
        val user =
            userRepository.save(
                com.liyaqa.user.User(
                    email = "pending@test.com",
                    passwordHash = "hashed",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        memberRepository.save(
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                userId = user.id, firstNameAr = "أ", firstNameEn = "A",
                lastNameAr = "ب", lastNameEn = "B", phone = PHONE,
                membershipStatus = "pending_activation",
            ),
        )

        val token = registrationToken()

        mockMvc.post("/api/v1/arena/register/complete") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "أحمد"}"""
        }.andExpect {
            status { isConflict() }
        }
    }
}
