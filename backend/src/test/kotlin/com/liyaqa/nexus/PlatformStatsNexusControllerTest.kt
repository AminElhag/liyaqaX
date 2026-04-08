package com.liyaqa.nexus

import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import org.hamcrest.Matchers.notNullValue
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
class PlatformStatsNexusControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @MockBean lateinit var permissionService: PermissionService

    private val roleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun platformToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(roleId, it)).thenReturn(true) }
        return "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to roleId.toString(), "scope" to "platform"))}"
    }

    @Test
    fun `GET stats returns all KPI fields`() {
        mockMvc.get("/api/v1/nexus/stats") {
            header("Authorization", platformToken("platform:stats:view"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalOrganizations", notNullValue())
            jsonPath("$.totalClubs", notNullValue())
            jsonPath("$.totalBranches", notNullValue())
            jsonPath("$.totalActiveMembers", notNullValue())
            jsonPath("$.totalActiveMemberships", notNullValue())
            jsonPath("$.estimatedMrrHalalas", notNullValue())
            jsonPath("$.estimatedMrrSar", notNullValue())
            jsonPath("$.generatedAt", notNullValue())
        }
    }

    @Test
    fun `GET stats returns 403 for support agent without permission`() {
        mockMvc.get("/api/v1/nexus/stats") {
            header(
                "Authorization",
                "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"))}",
            )
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GET stats returns 403 for non-platform scope`() {
        mockMvc.get("/api/v1/nexus/stats") {
            header(
                "Authorization",
                "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to roleId.toString(), "scope" to "club"))}",
            )
        }.andExpect {
            status { isForbidden() }
        }
    }
}
