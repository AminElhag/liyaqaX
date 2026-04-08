package com.liyaqa.subscription.controller

import com.liyaqa.nexus.nexusContext
import com.liyaqa.security.JwtClaims
import com.liyaqa.subscription.dto.AssignSubscriptionRequest
import com.liyaqa.subscription.dto.ClubSubscriptionResponse
import com.liyaqa.subscription.dto.ExpiringSubscriptionsResponse
import com.liyaqa.subscription.dto.ExtendSubscriptionRequest
import com.liyaqa.subscription.dto.SubscriptionDashboardResponse
import com.liyaqa.subscription.service.SubscriptionService
import com.liyaqa.user.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nexus")
@Tag(name = "Club Subscriptions (Nexus)", description = "Platform-scoped club subscription management")
@Validated
class ClubSubscriptionController(
    private val subscriptionService: SubscriptionService,
    private val userRepository: UserRepository,
) {
    @PostMapping("/clubs/{clubId}/subscription")
    @PreAuthorize("hasPermission(null, 'subscription:manage')")
    @Operation(summary = "Assign a subscription plan to a club")
    fun assign(
        @PathVariable clubId: UUID,
        @Valid @RequestBody request: AssignSubscriptionRequest,
        authentication: Authentication,
    ): ResponseEntity<ClubSubscriptionResponse> {
        val claims = authentication.nexusContext()
        val userId = resolveUserId(claims)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(subscriptionService.assignSubscription(clubId, request, userId))
    }

    @GetMapping("/clubs/{clubId}/subscription")
    @PreAuthorize("hasPermission(null, 'subscription:read')")
    @Operation(summary = "Get current subscription for a club")
    fun get(
        @PathVariable clubId: UUID,
        authentication: Authentication,
    ): ResponseEntity<ClubSubscriptionResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(subscriptionService.getClubSubscription(clubId))
    }

    @PostMapping("/clubs/{clubId}/subscription/extend")
    @PreAuthorize("hasPermission(null, 'subscription:manage')")
    @Operation(summary = "Extend a club subscription by additional months")
    fun extend(
        @PathVariable clubId: UUID,
        @Valid @RequestBody request: ExtendSubscriptionRequest,
        authentication: Authentication,
    ): ResponseEntity<ClubSubscriptionResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(subscriptionService.extendSubscription(clubId, request))
    }

    @PostMapping("/clubs/{clubId}/subscription/cancel")
    @PreAuthorize("hasPermission(null, 'subscription:manage')")
    @Operation(summary = "Cancel a club subscription")
    fun cancel(
        @PathVariable clubId: UUID,
        authentication: Authentication,
    ): ResponseEntity<ClubSubscriptionResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(subscriptionService.cancelSubscription(clubId))
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("hasPermission(null, 'subscription:read')")
    @Operation(summary = "List all clubs with subscription status (paginated)")
    fun dashboard(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        authentication: Authentication,
    ): ResponseEntity<SubscriptionDashboardResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(subscriptionService.getDashboard(page, pageSize))
    }

    @GetMapping("/subscriptions/expiring")
    @PreAuthorize("hasPermission(null, 'subscription:read')")
    @Operation(summary = "List clubs with subscriptions expiring within 30 days")
    fun expiring(authentication: Authentication): ResponseEntity<ExpiringSubscriptionsResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(subscriptionService.getExpiring())
    }

    private fun resolveUserId(claims: JwtClaims): Long {
        val userPublicId = claims.userPublicId
            ?: throw IllegalStateException("JWT missing user publicId")
        return userRepository.findByPublicIdAndDeletedAtIsNull(userPublicId)
            .orElseThrow { IllegalStateException("User not found") }
            .id
    }
}
