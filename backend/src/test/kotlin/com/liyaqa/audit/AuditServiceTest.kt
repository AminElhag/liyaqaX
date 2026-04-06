package com.liyaqa.audit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class AuditServiceTest {
    @Mock lateinit var auditLogRepository: AuditLogRepository

    @InjectMocks lateinit var auditService: AuditService

    @Test
    fun `log persists record with correct fields`() {
        whenever(auditLogRepository.save(any<AuditLog>())).thenAnswer { it.arguments[0] }

        auditService.log(
            action = AuditAction.MEMBER_CREATED,
            entityType = "Member",
            entityId = "abc-123",
            actorId = "user-456",
            actorScope = "platform",
            organizationId = "org-789",
            clubId = "club-012",
            changesJson = """{"firstName":["Ali","Ahmed"]}""",
            ipAddress = "192.168.1.1",
        )

        verify(auditLogRepository).save(
            org.mockito.kotlin.check { log ->
                assertThat(log.action).isEqualTo("MEMBER_CREATED")
                assertThat(log.entityType).isEqualTo("Member")
                assertThat(log.entityId).isEqualTo("abc-123")
                assertThat(log.actorId).isEqualTo("user-456")
                assertThat(log.actorScope).isEqualTo("platform")
                assertThat(log.organizationId).isEqualTo("org-789")
                assertThat(log.clubId).isEqualTo("club-012")
                assertThat(log.changesJson).isEqualTo("""{"firstName":["Ali","Ahmed"]}""")
                assertThat(log.ipAddress).isEqualTo("192.168.1.1")
            },
        )
    }

    @Test
    fun `log never throws even when repository throws`() {
        whenever(auditLogRepository.save(any<AuditLog>())).thenThrow(RuntimeException("DB down"))

        auditService.log(
            action = AuditAction.MEMBER_CREATED,
            entityType = "Member",
            entityId = "abc-123",
            actorId = "user-456",
            actorScope = "platform",
        )

        verify(auditLogRepository).save(any<AuditLog>())
    }

    @Test
    fun `changesJson truncated at 4000 chars`() {
        whenever(auditLogRepository.save(any<AuditLog>())).thenAnswer { it.arguments[0] }

        val longJson = "x".repeat(5000)

        auditService.log(
            action = AuditAction.MEMBER_UPDATED,
            entityType = "Member",
            entityId = "abc-123",
            actorId = "user-456",
            actorScope = "platform",
            changesJson = longJson,
        )

        verify(auditLogRepository).save(
            org.mockito.kotlin.check { log ->
                assertThat(log.changesJson).hasSize(4000)
                assertThat(log.changesJson).endsWith("...(truncated)")
            },
        )
    }

    @Test
    fun `changesJson at exactly 4000 chars is not truncated`() {
        whenever(auditLogRepository.save(any<AuditLog>())).thenAnswer { it.arguments[0] }

        val exactJson = "x".repeat(4000)

        auditService.log(
            action = AuditAction.MEMBER_UPDATED,
            entityType = "Member",
            entityId = "abc-123",
            actorId = "user-456",
            actorScope = "platform",
            changesJson = exactJson,
        )

        verify(auditLogRepository).save(
            org.mockito.kotlin.check { log ->
                assertThat(log.changesJson).hasSize(4000)
                assertThat(log.changesJson).doesNotEndWith("...(truncated)")
            },
        )
    }

    @Test
    fun `changesJson at 4001 chars is truncated`() {
        whenever(auditLogRepository.save(any<AuditLog>())).thenAnswer { it.arguments[0] }

        val json4001 = "x".repeat(4001)

        auditService.log(
            action = AuditAction.MEMBER_UPDATED,
            entityType = "Member",
            entityId = "abc-123",
            actorId = "user-456",
            actorScope = "platform",
            changesJson = json4001,
        )

        verify(auditLogRepository).save(
            org.mockito.kotlin.check { log ->
                assertThat(log.changesJson).hasSize(4000)
                assertThat(log.changesJson).endsWith("...(truncated)")
            },
        )
    }

    @Test
    fun `system actor accepted`() {
        whenever(auditLogRepository.save(any<AuditLog>())).thenAnswer { it.arguments[0] }

        auditService.log(
            action = AuditAction.MEMBER_CREATED,
            entityType = "Member",
            entityId = "abc-123",
            actorId = "system",
            actorScope = "system",
        )

        verify(auditLogRepository).save(
            org.mockito.kotlin.check { log ->
                assertThat(log.actorId).isEqualTo("system")
                assertThat(log.actorScope).isEqualTo("system")
            },
        )
    }

    @Test
    fun `null changesJson is stored as null`() {
        whenever(auditLogRepository.save(any<AuditLog>())).thenAnswer { it.arguments[0] }

        auditService.log(
            action = AuditAction.MEMBER_DELETED,
            entityType = "Member",
            entityId = "abc-123",
            actorId = "user-456",
            actorScope = "platform",
            changesJson = null,
        )

        verify(auditLogRepository).save(
            org.mockito.kotlin.check { log ->
                assertThat(log.changesJson).isNull()
            },
        )
    }
}
