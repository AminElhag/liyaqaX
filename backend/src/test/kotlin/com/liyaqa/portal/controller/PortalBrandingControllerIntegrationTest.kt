package com.liyaqa.portal.controller

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.permission.PermissionConstants
import com.liyaqa.portal.ClubPortalSettings
import com.liyaqa.portal.ClubPortalSettingsRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
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
import org.springframework.test.web.servlet.patch
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalBrandingControllerIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var settingsRepository: ClubPortalSettingsRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @MockBean lateinit var permissionService: PermissionService

    private val ownerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val branchManagerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000020")
    private val memberRoleId = UUID.fromString("00000000-0000-0000-0000-000000000030")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var memberUser: User
    private lateinit var member: Member

    @BeforeEach
    fun setup() {
        org =
            organizationRepository.save(
                Organization(nameAr = "تجربة", nameEn = "Test Org", email = "test@test.com", country = "SA", timezone = "Asia/Riyadh"),
            )
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        memberUser = userRepository.save(User(email = "member@test.com", passwordHash = "hash", organizationId = org.id, clubId = club.id))
        member =
            memberRepository.save(
                Member(
                    firstNameAr = "أحمد",
                    lastNameAr = "الراشدي",
                    firstNameEn = "Ahmed",
                    lastNameEn = "Rashidi",
                    phone = "+966500000001",
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = 0,
                    userId = memberUser.id,
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        settingsRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun ownerToken(): String {
        whenever(permissionService.hasPermission(ownerRoleId, PermissionConstants.PORTAL_SETTINGS_UPDATE)).thenReturn(true)
        whenever(permissionService.hasPermission(ownerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(true)
        val claims =
            mapOf(
                "roleId" to ownerRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    private fun branchManagerToken(): String {
        whenever(permissionService.hasPermission(branchManagerRoleId, PermissionConstants.PORTAL_SETTINGS_UPDATE)).thenReturn(true)
        whenever(permissionService.hasPermission(branchManagerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(false)
        val claims =
            mapOf(
                "roleId" to branchManagerRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    private fun memberToken(): String {
        whenever(permissionService.hasPermission(memberRoleId, PermissionConstants.PORTAL_SETTINGS_UPDATE)).thenReturn(false)
        val claims =
            mapOf(
                "roleId" to memberRoleId.toString(),
                "scope" to "member",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
                "memberId" to member.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    @Test
    fun `PATCH pulse portal-settings saves branding fields for Owner`() {
        mockMvc.patch("/api/v1/portal-settings") {
            header("Authorization", ownerToken())
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "logoUrl": "https://cdn.elixirgym.sa/logo.png",
                    "primaryColorHex": "#1A73E8",
                    "secondaryColorHex": "#F8F9FA",
                    "portalTitle": "Elixir Gym"
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.logoUrl", equalTo("https://cdn.elixirgym.sa/logo.png"))
            jsonPath("$.primaryColorHex", equalTo("#1A73E8"))
            jsonPath("$.secondaryColorHex", equalTo("#F8F9FA"))
            jsonPath("$.portalTitle", equalTo("Elixir Gym"))
        }
    }

    @Test
    fun `PATCH pulse portal-settings returns 403 for Branch Manager attempting branding update`() {
        mockMvc.patch("/api/v1/portal-settings") {
            header("Authorization", branchManagerToken())
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "primaryColorHex": "#1A73E8"
                }
                """.trimIndent()
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `PATCH pulse portal-settings returns 400 for invalid hex code`() {
        mockMvc.patch("/api/v1/portal-settings") {
            header("Authorization", ownerToken())
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "primaryColorHex": "notahex"
                }
                """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PATCH pulse portal-settings returns 400 for http logo URL`() {
        mockMvc.patch("/api/v1/portal-settings") {
            header("Authorization", ownerToken())
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "logoUrl": "http://cdn.example.com/logo.png"
                }
                """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PATCH pulse portal-settings allows Branch Manager to update feature flags`() {
        mockMvc.patch("/api/v1/portal-settings") {
            header("Authorization", branchManagerToken())
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "gxBookingEnabled": false
                }
                """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.gxBookingEnabled", equalTo(false))
        }
    }

    @Test
    fun `GET arena portal-settings returns branding fields when set`() {
        settingsRepository.save(
            ClubPortalSettings(
                clubId = club.id,
                logoUrl = "https://cdn.example.com/logo.png",
                primaryColorHex = "#FF0000",
                secondaryColorHex = "#00FF00",
                portalTitle = "My Gym",
            ),
        )

        mockMvc.get("/api/v1/arena/portal-settings") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isOk() }
            jsonPath("$.logoUrl", equalTo("https://cdn.example.com/logo.png"))
            jsonPath("$.primaryColorHex", equalTo("#FF0000"))
            jsonPath("$.secondaryColorHex", equalTo("#00FF00"))
            jsonPath("$.portalTitle", equalTo("My Gym"))
        }
    }

    @Test
    fun `GET arena portal-settings returns null branding fields when not configured`() {
        mockMvc.get("/api/v1/arena/portal-settings") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isOk() }
            jsonPath("$.logoUrl", nullValue())
            jsonPath("$.primaryColorHex", nullValue())
            jsonPath("$.secondaryColorHex", nullValue())
            jsonPath("$.portalTitle", nullValue())
        }
    }
}
