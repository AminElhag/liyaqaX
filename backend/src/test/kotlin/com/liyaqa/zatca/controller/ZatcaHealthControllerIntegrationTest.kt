package com.liyaqa.zatca.controller

import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.everyItem
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
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ZatcaHealthControllerIntegrationTest {
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
    fun `GET health returns 200 with correct shape`() {
        mockMvc.get("/api/v1/zatca/health") {
            header("Authorization", platformToken("zatca:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalActiveCsids", notNullValue())
            jsonPath("$.csidsExpiringSoon", notNullValue())
            jsonPath("$.clubsNotOnboarded", notNullValue())
            jsonPath("$.invoicesPending", notNullValue())
            jsonPath("$.invoicesFailed", notNullValue())
            jsonPath("$.invoicesDeadlineAtRisk", notNullValue())
        }
    }

    @Test
    fun `GET invoices failed returns 200 with list`() {
        mockMvc.get("/api/v1/zatca/invoices/failed") {
            header("Authorization", platformToken("zatca:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$", everyItem(anything()))
        }
    }

    @Test
    fun `GET health returns 403 without zatca read permission`() {
        mockMvc.get("/api/v1/zatca/health") {
            header(
                "Authorization",
                "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"))}",
            )
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST retry returns 404 when invoice does not exist`() {
        val fakeId = UUID.randomUUID()
        mockMvc.post("/api/v1/zatca/invoices/$fakeId/retry") {
            header("Authorization", platformToken("zatca:retry"))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST retry returns 403 without zatca retry permission`() {
        val fakeId = UUID.randomUUID()
        mockMvc.post("/api/v1/zatca/invoices/$fakeId/retry") {
            header(
                "Authorization",
                "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"))}",
            )
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST retry-all returns 200 for non-existent club`() {
        val fakeClubId = UUID.randomUUID()
        mockMvc.post("/api/v1/zatca/clubs/$fakeClubId/retry-all") {
            header("Authorization", platformToken("zatca:retry"))
        }.andExpect {
            status { isOk() }
        }
    }
}
