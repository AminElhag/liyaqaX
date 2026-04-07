package com.liyaqa.notification

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findAllByRecipientUserIdOrderByCreatedAtDesc(
        recipientUserId: String,
        pageable: Pageable,
    ): Page<Notification>

    fun findAllByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
        recipientUserId: String,
        pageable: Pageable,
    ): Page<Notification>

    fun countByRecipientUserIdAndReadAtIsNull(recipientUserId: String): Long

    fun existsByTypeAndEntityIdAndCreatedAtAfter(
        type: NotificationType,
        entityId: String,
        cutoff: Instant,
    ): Boolean

    fun findByPublicId(publicId: java.util.UUID): java.util.Optional<Notification>

    @Modifying
    @Query(
        value = "UPDATE notifications SET read_at = NOW(), updated_at = NOW() WHERE recipient_user_id = :userId AND read_at IS NULL",
        nativeQuery = true,
    )
    fun markAllReadByRecipientUserId(userId: String): Int

    @Modifying
    @Query(
        value = "DELETE FROM notifications WHERE created_at < :cutoff",
        nativeQuery = true,
    )
    fun deleteOlderThan(cutoff: Instant): Int
}
