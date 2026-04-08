package com.liyaqa.notification

import com.liyaqa.coach.coachContext
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.notification.dto.NotificationResponse
import com.liyaqa.notification.dto.UnreadCountResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/coach/notifications")
@Tag(name = "Notifications (Coach)", description = "Trainer notifications")
@Validated
class NotificationCoachController(
    private val notificationService: NotificationService,
) {
    @GetMapping
    @Operation(summary = "List notifications for the current trainer")
    fun list(
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<List<NotificationResponse>> {
        val userId = resolveUserPublicId(authentication)
        return ResponseEntity.ok(notificationService.listNotifications(userId, unreadOnly, pageable))
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    fun unreadCount(authentication: Authentication): ResponseEntity<UnreadCountResponse> {
        val userId = resolveUserPublicId(authentication)
        return ResponseEntity.ok(notificationService.getUnreadCount(userId))
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    fun markRead(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val userId = resolveUserPublicId(authentication)
        notificationService.markRead(id, userId)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    fun markAllRead(authentication: Authentication): ResponseEntity<Void> {
        val userId = resolveUserPublicId(authentication)
        notificationService.markAllRead(userId)
        return ResponseEntity.noContent().build()
    }

    private fun resolveUserPublicId(authentication: Authentication): String {
        val claims = authentication.coachContext()
        return claims.userPublicId?.toString()
            ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid user identity in token.")
    }
}
