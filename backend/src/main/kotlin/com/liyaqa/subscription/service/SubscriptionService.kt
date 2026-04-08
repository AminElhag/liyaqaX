package com.liyaqa.subscription.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import com.liyaqa.permission.PermissionConstants
import com.liyaqa.subscription.dto.AssignSubscriptionRequest
import com.liyaqa.subscription.dto.ClubSubscriptionResponse
import com.liyaqa.subscription.dto.CreatePlanRequest
import com.liyaqa.subscription.dto.ExpiringSubscriptionItem
import com.liyaqa.subscription.dto.ExpiringSubscriptionsResponse
import com.liyaqa.subscription.dto.ExtendSubscriptionRequest
import com.liyaqa.subscription.dto.SubscriptionDashboardItem
import com.liyaqa.subscription.dto.SubscriptionDashboardResponse
import com.liyaqa.subscription.dto.SubscriptionPlanResponse
import com.liyaqa.subscription.dto.UpdatePlanRequest
import com.liyaqa.subscription.entity.ClubSubscription
import com.liyaqa.subscription.entity.SubscriptionPlan
import com.liyaqa.subscription.repository.ClubSubscriptionRepository
import com.liyaqa.subscription.repository.SubscriptionPlanRepository
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SubscriptionService(
    private val planRepository: SubscriptionPlanRepository,
    private val subscriptionRepository: ClubSubscriptionRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val auditService: AuditService,
    private val notificationService: NotificationService,
    private val redisTemplate: StringRedisTemplate,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SubscriptionService::class.java)
        private const val CACHE_PREFIX = "subscription_status:"
        private val CACHE_TTL = Duration.ofMinutes(5)
        private val RIYADH = ZoneId.of("Asia/Riyadh")
        private const val DAYS_PER_MONTH = 30L
        private const val GRACE_DAYS = 7L
    }

    // ── Plan CRUD ────────────────────────────────────────────────────────────

    @Transactional
    fun createPlan(request: CreatePlanRequest): SubscriptionPlanResponse {
        val plan = planRepository.save(
            SubscriptionPlan(
                name = request.name,
                monthlyPriceHalalas = request.monthlyPriceHalalas,
                maxBranches = request.maxBranches,
                maxStaff = request.maxStaff,
                features = request.features,
            ),
        )
        auditService.logFromContext(
            AuditAction.SUBSCRIPTION_PLAN_CREATED,
            "SubscriptionPlan",
            plan.publicId.toString(),
        )
        return plan.toResponse()
    }

    fun listActivePlans(): List<SubscriptionPlanResponse> =
        planRepository.findAllActivePlans().map { it.toResponse() }

    @Transactional
    fun updatePlan(planPublicId: UUID, request: UpdatePlanRequest): SubscriptionPlanResponse {
        val plan = findPlanOrThrow(planPublicId)
        request.name?.let { plan.name = it }
        request.monthlyPriceHalalas?.let { plan.monthlyPriceHalalas = it }
        request.maxBranches?.let { plan.maxBranches = it }
        request.maxStaff?.let { plan.maxStaff = it }
        request.features?.let { plan.features = it }
        val saved = planRepository.save(plan)
        auditService.logFromContext(
            AuditAction.SUBSCRIPTION_PLAN_UPDATED,
            "SubscriptionPlan",
            saved.publicId.toString(),
        )
        return saved.toResponse()
    }

    @Transactional
    fun deletePlan(planPublicId: UUID) {
        val plan = findPlanOrThrow(planPublicId)
        val activeCount = planRepository.countActiveSubscriptionsForPlan(plan.id)
        if (activeCount > 0) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Cannot delete a plan with active subscriptions.",
            )
        }
        plan.deletedAt = Instant.now()
        plan.isActive = false
        planRepository.save(plan)
    }

    // ── Subscription management ──────────────────────────────────────────────

    @Transactional
    fun assignSubscription(
        clubPublicId: UUID,
        request: AssignSubscriptionRequest,
        assignedByUserId: Long,
    ): ClubSubscriptionResponse {
        val club = findClubOrThrow(clubPublicId)
        val plan = findPlanOrThrow(request.planPublicId)

        if (!plan.isActive || plan.deletedAt != null) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Selected plan is not available.",
                "PLAN_NOT_AVAILABLE",
            )
        }

        val existing = subscriptionRepository.findActiveByClubId(club.id)
        if (existing != null) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "This club already has an active subscription. Cancel it before assigning a new one.",
                "SUBSCRIPTION_ALREADY_ACTIVE",
            )
        }

        val periodStart = LocalDate.parse(request.periodStartDate)
            .atStartOfDay(RIYADH).toInstant()
        val periodEnd = periodStart.plus(Duration.ofDays(request.periodMonths * DAYS_PER_MONTH))
        val graceEnd = periodEnd.plus(Duration.ofDays(GRACE_DAYS))

        val subscription = subscriptionRepository.save(
            ClubSubscription(
                clubId = club.id,
                planId = plan.id,
                status = "ACTIVE",
                currentPeriodStart = periodStart,
                currentPeriodEnd = periodEnd,
                gracePeriodEndsAt = graceEnd,
                assignedByUserId = assignedByUserId,
            ),
        )

        invalidateCache(club.id)
        auditService.logFromContext(
            AuditAction.SUBSCRIPTION_ASSIGNED,
            "ClubSubscription",
            subscription.publicId.toString(),
            """{"clubId":"${club.publicId}","planName":"${plan.name}"}""",
        )
        return subscription.toResponse(plan)
    }

    fun getClubSubscription(clubPublicId: UUID): ClubSubscriptionResponse {
        val club = findClubOrThrow(clubPublicId)
        val subscription = subscriptionRepository.findActiveByClubId(club.id)
            ?: throw ArenaException(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "No active subscription found for this club.",
            )
        val plan = planRepository.findById(subscription.planId).orElseThrow()
        return subscription.toResponse(plan)
    }

    @Transactional
    fun extendSubscription(clubPublicId: UUID, request: ExtendSubscriptionRequest): ClubSubscriptionResponse {
        val club = findClubOrThrow(clubPublicId)
        val subscription = subscriptionRepository.findActiveByClubId(club.id)
            ?: throw ArenaException(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "No active subscription found for this club.",
            )

        subscription.currentPeriodEnd = subscription.currentPeriodEnd
            .plus(Duration.ofDays(request.additionalMonths * DAYS_PER_MONTH))
        subscription.gracePeriodEndsAt = subscription.currentPeriodEnd
            .plus(Duration.ofDays(GRACE_DAYS))

        if (subscription.status == "GRACE") {
            subscription.status = "ACTIVE"
        }

        subscriptionRepository.save(subscription)
        invalidateCache(club.id)

        val plan = planRepository.findById(subscription.planId).orElseThrow()
        auditService.logFromContext(
            AuditAction.SUBSCRIPTION_EXTENDED,
            "ClubSubscription",
            subscription.publicId.toString(),
            """{"additionalMonths":${request.additionalMonths}}""",
        )
        return subscription.toResponse(plan)
    }

    @Transactional
    fun cancelSubscription(clubPublicId: UUID): ClubSubscriptionResponse {
        val club = findClubOrThrow(clubPublicId)
        val subscription = subscriptionRepository.findActiveByClubId(club.id)
            ?: throw ArenaException(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "No active subscription found for this club.",
            )

        if (subscription.status == "EXPIRED") {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Subscription is already expired.",
                "SUBSCRIPTION_ALREADY_EXPIRED",
            )
        }

        subscription.status = "CANCELLED"
        subscription.cancelledAt = Instant.now()
        subscriptionRepository.save(subscription)
        invalidateCache(club.id)

        val plan = planRepository.findById(subscription.planId).orElseThrow()
        auditService.logFromContext(
            AuditAction.SUBSCRIPTION_CANCELLED,
            "ClubSubscription",
            subscription.publicId.toString(),
        )
        return subscription.toResponse(plan)
    }

    // ── Dashboard queries ────────────────────────────────────────────────────

    fun getDashboard(page: Int, pageSize: Int): SubscriptionDashboardResponse {
        val offset = page * pageSize
        val projections = subscriptionRepository.findAllForDashboard(pageSize, offset)
        val total = subscriptionRepository.countForDashboard()
        val now = Instant.now()

        return SubscriptionDashboardResponse(
            subscriptions = projections.map { p ->
                SubscriptionDashboardItem(
                    clubId = findClubPublicId(p.getClubId()),
                    clubName = p.getClubName(),
                    planName = p.getPlanName(),
                    status = p.getStatus(),
                    currentPeriodEnd = p.getCurrentPeriodEnd(),
                    gracePeriodEndsAt = p.getGracePeriodEndsAt(),
                    daysUntilExpiry = Duration.between(now, p.getCurrentPeriodEnd()).toDays().coerceAtLeast(0),
                    monthlyPriceSar = "%.2f".format(p.getMonthlyPriceHalalas() / 100.0),
                )
            },
            totalCount = total,
            page = page,
            pageSize = pageSize,
        )
    }

    fun getExpiring(): ExpiringSubscriptionsResponse {
        val cutoff = Instant.now().plus(Duration.ofDays(30))
        val now = Instant.now()
        val projections = subscriptionRepository.findExpiringSoon(cutoff)

        return ExpiringSubscriptionsResponse(
            expiringSoon = projections.map { p ->
                ExpiringSubscriptionItem(
                    clubId = findClubPublicId(p.getClubId()),
                    clubName = p.getClubName(),
                    planName = p.getPlanName(),
                    status = p.getStatus(),
                    currentPeriodEnd = p.getCurrentPeriodEnd(),
                    daysUntilExpiry = Duration.between(now, p.getCurrentPeriodEnd()).toDays().coerceAtLeast(0),
                )
            },
        )
    }

    fun getSubscriptionHistory(clubPublicId: UUID): List<ClubSubscriptionResponse> {
        val club = findClubOrThrow(clubPublicId)
        return subscriptionRepository.findAllByClubIdOrderByCreatedAtDesc(club.id).map { sub ->
            val plan = planRepository.findById(sub.planId).orElse(null)
            sub.toResponse(plan)
        }
    }

    // ── Enforcement helpers (called by interceptor) ──────────────────────────

    fun findActiveByClubId(clubId: Long): ClubSubscription? =
        subscriptionRepository.findActiveByClubId(clubId)

    fun findPlanById(planId: Long): SubscriptionPlan? =
        planRepository.findById(planId).orElse(null)

    // ── Scheduler transitions ────────────────────────────────────────────────

    @Transactional
    fun transitionExpiredToGrace() {
        val now = Instant.now()
        val subscriptions = subscriptionRepository.findActiveExpired(now)
        for (sub in subscriptions) {
            sub.status = "GRACE"
            subscriptionRepository.save(sub)
            invalidateCache(sub.clubId)
            log.info("Subscription {} transitioned ACTIVE -> GRACE for club {}", sub.publicId, sub.clubId)
        }
    }

    @Transactional
    fun transitionGraceToExpired() {
        val now = Instant.now()
        val subscriptions = subscriptionRepository.findGraceExpired(now)
        for (sub in subscriptions) {
            sub.status = "EXPIRED"
            subscriptionRepository.save(sub)
            invalidateCache(sub.clubId)
            log.info("Subscription {} transitioned GRACE -> EXPIRED for club {}", sub.publicId, sub.clubId)

            fireNotificationToAdmins(
                NotificationType.SUBSCRIPTION_EXPIRED,
                sub.clubId,
                sub.publicId.toString(),
                """{"clubId":"${sub.clubId}"}""",
            )
        }
    }

    @Transactional
    fun sendExpiryNotifications() {
        val today = LocalDate.now(RIYADH)

        val fourteenDaysOut = today.plusDays(14)
        val sevenDaysOut = today.plusDays(7)

        val expiring14 = subscriptionRepository.findExpiringOnDate(fourteenDaysOut)
        for (sub in expiring14) {
            fireNotificationToAdmins(
                NotificationType.SUBSCRIPTION_EXPIRING_SOON_14,
                sub.clubId,
                sub.publicId.toString(),
                """{"clubId":"${sub.clubId}","daysRemaining":14}""",
            )
        }

        val expiring7 = subscriptionRepository.findExpiringOnDate(sevenDaysOut)
        for (sub in expiring7) {
            fireNotificationToAdmins(
                NotificationType.SUBSCRIPTION_EXPIRING_SOON_7,
                sub.clubId,
                sub.publicId.toString(),
                """{"clubId":"${sub.clubId}","daysRemaining":7}""",
            )
        }
    }

    // ── Cache management ─────────────────────────────────────────────────────

    fun invalidateCache(clubId: Long) {
        redisTemplate.delete("$CACHE_PREFIX$clubId")
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun findPlanOrThrow(publicId: UUID): SubscriptionPlan =
        planRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Subscription plan not found.")
            }

    private fun findClubOrThrow(clubPublicId: UUID): Club =
        clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
            }

    private fun findClubPublicId(clubId: Long): UUID =
        clubRepository.findById(clubId).map { it.publicId }.orElse(UUID.randomUUID())

    private fun fireNotificationToAdmins(
        type: NotificationType,
        clubId: Long,
        entityId: String,
        paramsJson: String,
    ) {
        try {
            val adminUserIds = userRepository.findPlatformUserIdsWithPermission(
                PermissionConstants.SUBSCRIPTION_READ,
            )
            for (userId in adminUserIds) {
                notificationService.create(
                    recipientUserId = userId,
                    recipientScope = "platform",
                    type = type,
                    paramsJson = paramsJson,
                    entityType = "ClubSubscription",
                    entityId = entityId,
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to fire {} notification for club {}: {}", type, clubId, e.message)
        }
    }

    private fun SubscriptionPlan.toResponse() = SubscriptionPlanResponse(
        id = publicId,
        name = name,
        monthlyPriceHalalas = monthlyPriceHalalas,
        monthlyPriceSar = "%.2f".format(monthlyPriceHalalas / 100.0),
        maxBranches = maxBranches,
        maxStaff = maxStaff,
        features = features,
        isActive = isActive,
    )

    private fun ClubSubscription.toResponse(plan: SubscriptionPlan?) = ClubSubscriptionResponse(
        id = publicId,
        clubId = findClubPublicId(clubId),
        planName = plan?.name ?: "Unknown",
        monthlyPriceHalalas = plan?.monthlyPriceHalalas ?: 0,
        monthlyPriceSar = "%.2f".format((plan?.monthlyPriceHalalas ?: 0) / 100.0),
        status = status,
        currentPeriodStart = currentPeriodStart,
        currentPeriodEnd = currentPeriodEnd,
        gracePeriodEndsAt = gracePeriodEndsAt,
        cancelledAt = cancelledAt,
        createdAt = createdAt,
    )
}
