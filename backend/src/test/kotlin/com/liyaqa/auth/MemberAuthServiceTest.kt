package com.liyaqa.auth

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberAuthServiceTest {
    companion object {
        private const val TEST_PHONE = "+966501234099"
        private const val UNKNOWN_PHONE = "+966509999999"
        private const val TEST_PASSWORD = "Test@12345678"
    }

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var otpRepository: MemberOtpRepository

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var member: Member

    @BeforeEach
    fun setup() {
        org =
            organizationRepository.save(
                Organization(nameAr = "تجربة", nameEn = "Test Org", email = "test@test.com", country = "SA", timezone = "Asia/Riyadh"),
            )
        club =
            clubRepository.save(
                Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"),
            )
        branch =
            branchRepository.save(
                Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Test Branch", city = "Riyadh"),
            )
        val user =
            userRepository.save(
                User(
                    email = "member@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        member =
            memberRepository.save(
                Member(
                    organizationId = org.id, clubId = club.id, branchId = branch.id, userId = user.id,
                    firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "الرشيدي", lastNameEn = "Al-Rashidi",
                    phone = TEST_PHONE, membershipStatus = "active",
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        otpRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    @Test
    fun `requestOtp returns 200 for known phone`() {
        mockMvc.post("/api/v1/arena/auth/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$TEST_PHONE"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `requestOtp returns 200 for unknown phone - no info leak`() {
        mockMvc.post("/api/v1/arena/auth/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$UNKNOWN_PHONE"}"""
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `requestOtp returns 429 after 3 requests`() {
        repeat(3) {
            mockMvc.post("/api/v1/arena/auth/otp/request") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"phone": "$TEST_PHONE"}"""
            }.andExpect { status { isOk() } }
        }

        mockMvc.post("/api/v1/arena/auth/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$TEST_PHONE"}"""
        }.andExpect {
            status { isTooManyRequests() }
        }
    }

    @Test
    fun `verifyOtp returns 401 for wrong code`() {
        mockMvc.post("/api/v1/arena/auth/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$TEST_PHONE"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/arena/auth/otp/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$TEST_PHONE", "otp": "000000"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `verifyOtp returns 401 for already used OTP`() {
        // Mark all OTPs as used
        mockMvc.post("/api/v1/arena/auth/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$TEST_PHONE"}"""
        }.andExpect { status { isOk() } }

        val otps = otpRepository.findAll()
        otps.forEach { it.used = true }
        otpRepository.saveAll(otps)

        mockMvc.post("/api/v1/arena/auth/otp/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"phone": "$TEST_PHONE", "otp": "123456"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
