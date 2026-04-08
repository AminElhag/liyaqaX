package com.liyaqa.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.notification.dto.NotificationResponse
import com.liyaqa.notification.dto.UnreadCountResponse
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val log = LoggerFactory.getLogger(NotificationService::class.java)
        private const val REDIS_PREFIX = "notification_unread:"
        private val REDIS_TTL = Duration.ofHours(24)
    }

    @Transactional
    fun create(
        recipientUserId: Long,
        recipientScope: String,
        type: NotificationType,
        paramsJson: String? = null,
        entityType: String? = null,
        entityId: String? = null,
    ): Notification? {
        val user = userRepository.findById(recipientUserId).orElse(null)
        if (user == null) {
            log.warn("Notification skipped: recipient userId={} not found", recipientUserId)
            return null
        }

        val userPublicId = user.publicId.toString()

        // Dedup for scheduler types
        if (type in SCHEDULER_TYPES && entityId != null) {
            val cutoff = Instant.now().minus(Duration.ofHours(24))
            if (notificationRepository.existsByTypeAndEntityIdAndCreatedAtAfter(type, entityId, cutoff)) {
                log.debug("Notification dedup: {} for entity {} already exists within 24h", type, entityId)
                return null
            }
        }

        val notification =
            notificationRepository.save(
                Notification(
                    recipientUserId = userPublicId,
                    recipientScope = recipientScope,
                    type = type,
                    titleKey = type.titleKey,
                    bodyKey = type.bodyKey,
                    paramsJson = paramsJson,
                    entityType = entityType,
                    entityId = entityId,
                ),
            )

        incrementUnreadCount(userPublicId)
        return notification
    }

    fun listNotifications(
        userId: String,
        unreadOnly: Boolean,
        pageable: Pageable,
    ): List<NotificationResponse> {
        val page =
            if (unreadOnly) {
                notificationRepository.findAllByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
            } else {
                notificationRepository.findAllByRecipientUserIdOrderByCreatedAtDesc(userId, pageable)
            }
        return page.content.map { it.toResponse() }
    }

    fun getUnreadCount(userId: String): UnreadCountResponse {
        val redisKey = "$REDIS_PREFIX$userId"
        val cached = redisTemplate.opsForValue().get(redisKey)
        if (cached != null) {
            redisTemplate.expire(redisKey, REDIS_TTL)
            return UnreadCountResponse(count = cached.toLongOrNull() ?: 0)
        }
        val count = notificationRepository.countByRecipientUserIdAndReadAtIsNull(userId)
        redisTemplate.opsForValue().set(redisKey, count.toString(), REDIS_TTL)
        return UnreadCountResponse(count = count)
    }

    @Transactional
    fun markRead(
        notificationPublicId: UUID,
        callerUserId: String,
    ) {
        val notification =
            notificationRepository.findByPublicId(notificationPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Notification not found.") }

        if (notification.recipientUserId != callerUserId) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "Cannot mark another user's notification as read.")
        }

        if (notification.readAt == null) {
            notification.readAt = Instant.now()
            notificationRepository.save(notification)
            decrementUnreadCount(callerUserId)
        }
    }

    @Transactional
    fun markAllRead(userId: String) {
        notificationRepository.markAllReadByRecipientUserId(userId)
        setUnreadCount(userId, 0)
    }

    @Transactional
    fun deleteOlderThan(cutoff: Instant): Int {
        return notificationRepository.deleteOlderThan(cutoff)
    }

    private fun incrementUnreadCount(userId: String) {
        val redisKey = "$REDIS_PREFIX$userId"
        redisTemplate.opsForValue().increment(redisKey)
        redisTemplate.expire(redisKey, REDIS_TTL)
    }

    private fun decrementUnreadCount(userId: String) {
        val redisKey = "$REDIS_PREFIX$userId"
        val current = redisTemplate.opsForValue().get(redisKey)?.toLongOrNull() ?: return
        if (current > 0) {
            redisTemplate.opsForValue().decrement(redisKey)
        }
        redisTemplate.expire(redisKey, REDIS_TTL)
    }

    private fun setUnreadCount(
        userId: String,
        count: Long,
    ) {
        val redisKey = "$REDIS_PREFIX$userId"
        redisTemplate.opsForValue().set(redisKey, count.toString(), REDIS_TTL)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Notification.toResponse(): NotificationResponse {
        val params: Map<String, Any>? =
            paramsJson?.let {
                try {
                    objectMapper.readValue(it, Map::class.java) as Map<String, Any>
                } catch (e: Exception) {
                    null
                }
            }
        return NotificationResponse(
            id = publicId,
            type = type.name,
            titleKey = titleKey,
            bodyKey = bodyKey,
            params = params,
            entityType = entityType,
            entityId = entityId,
            readAt = readAt,
            createdAt = createdAt,
        )
    }
}

private val SCHEDULER_TYPES =
    setOf(
        NotificationType.MEMBERSHIP_EXPIRING_SOON,
        NotificationType.PT_SESSION_REMINDER,
        NotificationType.LOW_GX_SPOTS,
        NotificationType.GX_CLASS_REMINDER,
        NotificationType.ZATCA_CSID_EXPIRING_SOON,
        NotificationType.ZATCA_INVOICE_DEADLINE_AT_RISK,
        NotificationType.FOLLOW_UP_DUE,
    )
