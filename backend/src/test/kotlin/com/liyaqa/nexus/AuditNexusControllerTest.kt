package com.liyaqa.nexus

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditLog
import com.liyaqa.audit.AuditLogRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
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
class AuditNexusControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var auditLogRepository: AuditLogRepository

    @MockBean lateinit var permissionService: PermissionService

    private val roleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach
    fun setup() {
        auditLogRepository.save(
            AuditLog(
                actorId = "actor-1",
                actorScope = "platform",
                action = AuditAction.MEMBER_CREATED.code,
                entityType = "Member",
                entityId = "entity-1",
                organizationId = "org-1",
            ),
        )
        auditLogRepository.save(
            AuditLog(
                actorId = "actor-2",
                actorScope = "club",
                action = AuditAction.PAYMENT_COLLECTED.code,
                entityType = "Payment",
                entityId = "entity-2",
                organizationId = "org-1",
                changesJson = """{"amountHalalas":50000}""",
            ),
        )
        auditLogRepository.save(
            AuditLog(
                actorId = "actor-1",
                actorScope = "platform",
                action = AuditAction.STAFF_CREATED.code,
                entityType = "Staff",
                entityId = "entity-3",
                organizationId = "org-2",
            ),
        )
    }

    @AfterEach
    fun cleanup() {
        auditLogRepository.deleteAllInBatch()
    }

    private fun platformToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(roleId, it)).thenReturn(true) }
        return "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to roleId.toString(), "scope" to "platform"))}"
    }

    @Test
    fun `GET nexus audit returns paginated records`() {
        mockMvc.get("/api/v1/nexus/audit") {
            header("Authorization", platformToken("audit:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items", hasSize<Any>(3))
            jsonPath("$.pagination.totalElements", equalTo(3))
        }
    }

    @Test
    fun `filter by action returns only matching action`() {
        mockMvc.get("/api/v1/nexus/audit?action=MEMBER_CREATED") {
            header("Authorization", platformToken("audit:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items", hasSize<Any>(1))
            jsonPath("$.items[0].action", equalTo("MEMBER_CREATED"))
        }
    }

    @Test
    fun `filter by organizationId scopes correctly`() {
        mockMvc.get("/api/v1/nexus/audit?organizationId=org-2") {
            header("Authorization", platformToken("audit:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items", hasSize<Any>(1))
            jsonPath("$.items[0].entityType", equalTo("Staff"))
        }
    }

    @Test
    fun `pagination returns correct slice`() {
        mockMvc.get("/api/v1/nexus/audit?page=0&size=2") {
            header("Authorization", platformToken("audit:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items", hasSize<Any>(2))
            jsonPath("$.pagination.totalElements", equalTo(3))
            jsonPath("$.pagination.totalPages", equalTo(2))
            jsonPath("$.pagination.hasNext", equalTo(true))
        }
    }

    @Test
    fun `non audit-read user returns 403`() {
        mockMvc.get("/api/v1/nexus/audit") {
            header(
                "Authorization",
                "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"))}",
            )
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `filter by actorId returns only that actor`() {
        mockMvc.get("/api/v1/nexus/audit?actorId=actor-2") {
            header("Authorization", platformToken("audit:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items", hasSize<Any>(1))
            jsonPath("$.items[0].action", equalTo("PAYMENT_COLLECTED"))
        }
    }
}
