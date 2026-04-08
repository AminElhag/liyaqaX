package com.liyaqa.subscription.controller

import com.liyaqa.nexus.nexusContext
import com.liyaqa.subscription.dto.CreatePlanRequest
import com.liyaqa.subscription.dto.SubscriptionPlanResponse
import com.liyaqa.subscription.dto.UpdatePlanRequest
import com.liyaqa.subscription.service.SubscriptionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nexus/subscription-plans")
@Tag(name = "Subscription Plans (Nexus)", description = "Platform-scoped subscription plan management")
@Validated
class SubscriptionPlanController(
    private val subscriptionService: SubscriptionService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'subscription:manage')")
    @Operation(summary = "Create a subscription plan")
    fun create(
        @Valid @RequestBody request: CreatePlanRequest,
        authentication: Authentication,
    ): ResponseEntity<SubscriptionPlanResponse> {
        authentication.nexusContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.createPlan(request))
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'subscription:read')")
    @Operation(summary = "List all active subscription plans")
    fun listActive(authentication: Authentication): ResponseEntity<List<SubscriptionPlanResponse>> {
        authentication.nexusContext()
        return ResponseEntity.ok(subscriptionService.listActivePlans())
    }

    @PatchMapping("/{planId}")
    @PreAuthorize("hasPermission(null, 'subscription:manage')")
    @Operation(summary = "Update a subscription plan")
    fun update(
        @PathVariable planId: UUID,
        @Valid @RequestBody request: UpdatePlanRequest,
        authentication: Authentication,
    ): ResponseEntity<SubscriptionPlanResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(subscriptionService.updatePlan(planId, request))
    }

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasPermission(null, 'subscription:manage')")
    @Operation(summary = "Soft-delete a subscription plan")
    fun delete(
        @PathVariable planId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        authentication.nexusContext()
        subscriptionService.deletePlan(planId)
        return ResponseEntity.noContent().build()
    }
}
