package com.liyaqa.nexus

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
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberNexusControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var userRepository: UserRepository

    @MockBean lateinit var permissionService: PermissionService

    private val roleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private lateinit var testMember: Member

    @BeforeEach
    fun setup() {
        val org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        val club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        val user = userRepository.save(User(email = "ahmed@test.com", passwordHash = "hashed", organizationId = org.id, clubId = club.id))
        testMember =
            memberRepository.save(
                Member(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = 0,
                    userId = user.id,
                    firstNameAr = "أحمد",
                    firstNameEn = "Ahmed",
                    lastNameAr = "الراشدي",
                    lastNameEn = "Al-Rashidi",
                    phone = "+966500000001",
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        memberRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun platformToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(roleId, it)).thenReturn(true) }
        return "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to roleId.toString(), "scope" to "platform"))}"
    }

    @Test
    fun `GET search returns matching members`() {
        mockMvc.get("/api/v1/nexus/members?q=Ahmed") {
            header("Authorization", platformToken("member:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items", hasSize<Any>(1))
            jsonPath("$.items[0].firstNameEn", equalTo("Ahmed"))
        }
    }

    @Test
    fun `GET search returns 422 for query less than 2 chars`() {
        mockMvc.get("/api/v1/nexus/members?q=a") {
            header("Authorization", platformToken("member:read"))
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    fun `GET member detail returns member`() {
        mockMvc.get("/api/v1/nexus/members/${testMember.publicId}") {
            header("Authorization", platformToken("member:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.firstNameEn", equalTo("Ahmed"))
        }
    }

    @Test
    fun `GET search returns 403 for integration specialist without member read`() {
        mockMvc.get("/api/v1/nexus/members?q=Ahmed") {
            header(
                "Authorization",
                "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"))}",
            )
        }.andExpect {
            status { isForbidden() }
        }
    }
}
