package com.liyaqa.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotificationServiceTest {
    @Mock lateinit var notificationRepository: NotificationRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var redisTemplate: StringRedisTemplate

    @Mock lateinit var objectMapper: ObjectMapper

    @Mock lateinit var valueOps: ValueOperations<String, String>

    @InjectMocks lateinit var notificationService: NotificationService

    private fun mockUser(
        id: Long = 1L,
        publicId: UUID = UUID.randomUUID(),
    ): User {
        val user = User(email = "test@example.com", passwordHash = "hash")
        // Use reflection to set id and publicId
        val idField = user.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.setLong(user, id)
        return user
    }

    @Test
    fun `create persists notification and increments Redis`() {
        val userPublicId = UUID.randomUUID()
        val user = mockUser(1L, userPublicId)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { it.arguments[0] }
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        val result =
            notificationService.create(
                recipientUserId = 1L,
                recipientScope = "member",
                type = NotificationType.MEMBERSHIP_ASSIGNED,
                paramsJson = """{"planName":"Basic"}""",
                entityType = "Membership",
                entityId = "some-id",
            )

        assertThat(result).isNotNull
        verify(notificationRepository).save(any<Notification>())
        verify(valueOps).increment(any<String>())
    }

    @Test
    fun `create skips and warns when recipient not found`() {
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        val result =
            notificationService.create(
                recipientUserId = 999L,
                recipientScope = "member",
                type = NotificationType.MEMBERSHIP_ASSIGNED,
            )

        assertThat(result).isNull()
        verify(notificationRepository, never()).save(any<Notification>())
    }

    @Test
    fun `create deduplicates scheduler type within 24h`() {
        val user = mockUser(1L)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(
            notificationRepository.existsByTypeAndEntityIdAndCreatedAtAfter(
                any<NotificationType>(),
                any<String>(),
                any<Instant>(),
            ),
        ).thenReturn(true)

        val result =
            notificationService.create(
                recipientUserId = 1L,
                recipientScope = "member",
                type = NotificationType.MEMBERSHIP_EXPIRING_SOON,
                entityId = "membership-123",
            )

        assertThat(result).isNull()
        verify(notificationRepository, never()).save(any<Notification>())
    }

    @Test
    fun `markRead sets readAt and decrements Redis for own notification`() {
        val userId = UUID.randomUUID().toString()
        val notification =
            Notification(
                recipientUserId = userId,
                recipientScope = "member",
                type = NotificationType.MEMBERSHIP_ASSIGNED,
                titleKey = "t",
                bodyKey = "b",
            )
        whenever(notificationRepository.findByPublicId(notification.publicId))
            .thenReturn(Optional.of(notification))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { it.arguments[0] }
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get(any<String>())).thenReturn("5")

        notificationService.markRead(notification.publicId, userId)

        assertThat(notification.readAt).isNotNull()
        verify(valueOps).decrement(any<String>())
    }

    @Test
    fun `markRead throws 403 for other users notification`() {
        val notification =
            Notification(
                recipientUserId = "user-A",
                recipientScope = "member",
                type = NotificationType.MEMBERSHIP_ASSIGNED,
                titleKey = "t",
                bodyKey = "b",
            )
        whenever(notificationRepository.findByPublicId(notification.publicId))
            .thenReturn(Optional.of(notification))

        assertThatThrownBy {
            notificationService.markRead(notification.publicId, "user-B")
        }.isInstanceOf(ArenaException::class.java)
            .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN)
    }

    @Test
    fun `markAllRead updates DB and sets Redis to 0`() {
        whenever(notificationRepository.markAllReadByRecipientUserId("user-1")).thenReturn(3)
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        notificationService.markAllRead("user-1")

        verify(notificationRepository).markAllReadByRecipientUserId("user-1")
        verify(valueOps).set(
            org.mockito.kotlin.eq("notification_unread:user-1"),
            org.mockito.kotlin.eq("0"),
            any<Duration>(),
        )
    }

    @Test
    fun `getUnreadCount returns from Redis on cache hit`() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("notification_unread:user-1")).thenReturn("7")
        whenever(redisTemplate.expire(any<String>(), any<Duration>())).thenReturn(true)

        val result = notificationService.getUnreadCount("user-1")

        assertThat(result.count).isEqualTo(7)
        verify(notificationRepository, never()).countByRecipientUserIdAndReadAtIsNull(any())
    }

    @Test
    fun `getUnreadCount falls back to DB on cache miss`() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("notification_unread:user-1")).thenReturn(null)
        whenever(notificationRepository.countByRecipientUserIdAndReadAtIsNull("user-1")).thenReturn(3)

        val result = notificationService.getUnreadCount("user-1")

        assertThat(result.count).isEqualTo(3)
        verify(notificationRepository).countByRecipientUserIdAndReadAtIsNull("user-1")
        verify(valueOps).set(
            org.mockito.kotlin.eq("notification_unread:user-1"),
            org.mockito.kotlin.eq("3"),
            any<Duration>(),
        )
    }
}
