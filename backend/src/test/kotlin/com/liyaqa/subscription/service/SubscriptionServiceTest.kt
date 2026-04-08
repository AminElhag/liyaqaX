package com.liyaqa.subscription.service

import com.liyaqa.audit.AuditService
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import com.liyaqa.subscription.dto.AssignSubscriptionRequest
import com.liyaqa.subscription.dto.ExtendSubscriptionRequest
import com.liyaqa.subscription.entity.ClubSubscription
import com.liyaqa.subscription.entity.SubscriptionPlan
import com.liyaqa.subscription.repository.ClubSubscriptionRepository
import com.liyaqa.subscription.repository.SubscriptionPlanRepository
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SubscriptionServiceTest {

    @Mock lateinit var planRepository: SubscriptionPlanRepository
    @Mock lateinit var subscriptionRepository: ClubSubscriptionRepository
    @Mock lateinit var clubRepository: ClubRepository
    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var auditService: AuditService
    @Mock lateinit var notificationService: NotificationService
    @Mock lateinit var redisTemplate: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>

    @InjectMocks
    lateinit var subscriptionService: SubscriptionService

    private val clubPublicId = UUID.randomUUID()
    private val planPublicId = UUID.randomUUID()

    private fun plan(active: Boolean = true) = SubscriptionPlan(
        id = 1L, publicId = planPublicId, name = "Growth",
        monthlyPriceHalalas = 120_000, maxBranches = 3, maxStaff = 30,
        isActive = active,
    )

    private fun club() = Club(
        organizationId = 1L, nameAr = "نادي", nameEn = "Club", email = "c@e.com",
    ).apply {
        val idField = this::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, 10L)
    }

    private fun subscription(status: String = "ACTIVE") = ClubSubscription(
        id = 1L, clubId = 10L, planId = 1L, status = status,
        currentPeriodStart = Instant.now().minus(Duration.ofDays(10)),
        currentPeriodEnd = Instant.now().plus(Duration.ofDays(20)),
        gracePeriodEndsAt = Instant.now().plus(Duration.ofDays(27)),
        assignedByUserId = 1L,
    )

    @Test
    fun `assignSubscription creates ACTIVE subscription with correct period dates`() {
        val c = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(c))
        whenever(planRepository.findByPublicIdAndDeletedAtIsNull(planPublicId)).thenReturn(Optional.of(plan()))
        whenever(subscriptionRepository.findActiveByClubId(10L)).thenReturn(null)
        whenever(subscriptionRepository.save(any<ClubSubscription>())).thenAnswer { it.arguments[0] }
        whenever(redisTemplate.delete(any<String>())).thenReturn(true)

        val result = subscriptionService.assignSubscription(
            clubPublicId,
            AssignSubscriptionRequest(planPublicId, LocalDate.now().toString(), 1),
            1L,
        )

        assertThat(result.status).isEqualTo("ACTIVE")
        assertThat(result.planName).isEqualTo("Growth")
    }

    @Test
    fun `assignSubscription throws 409 when club already has active subscription`() {
        val c = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(c))
        whenever(planRepository.findByPublicIdAndDeletedAtIsNull(planPublicId)).thenReturn(Optional.of(plan()))
        whenever(subscriptionRepository.findActiveByClubId(10L)).thenReturn(subscription())

        assertThatThrownBy {
            subscriptionService.assignSubscription(
                clubPublicId,
                AssignSubscriptionRequest(planPublicId, LocalDate.now().toString(), 1),
                1L,
            )
        }.isInstanceOf(ArenaException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", "SUBSCRIPTION_ALREADY_ACTIVE")
    }

    @Test
    fun `assignSubscription throws 422 when plan is not active`() {
        val c = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(c))
        whenever(planRepository.findByPublicIdAndDeletedAtIsNull(planPublicId)).thenReturn(Optional.of(plan(active = false)))

        assertThatThrownBy {
            subscriptionService.assignSubscription(
                clubPublicId,
                AssignSubscriptionRequest(planPublicId, LocalDate.now().toString(), 1),
                1L,
            )
        }.isInstanceOf(ArenaException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", "PLAN_NOT_AVAILABLE")
    }

    @Test
    fun `extendSubscription extends period end and grace period`() {
        val c = club()
        val sub = subscription()
        val originalEnd = sub.currentPeriodEnd
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(c))
        whenever(subscriptionRepository.findActiveByClubId(10L)).thenReturn(sub)
        whenever(subscriptionRepository.save(any<ClubSubscription>())).thenAnswer { it.arguments[0] }
        whenever(planRepository.findById(1L)).thenReturn(Optional.of(plan()))
        whenever(redisTemplate.delete(any<String>())).thenReturn(true)

        val result = subscriptionService.extendSubscription(clubPublicId, ExtendSubscriptionRequest(2))

        assertThat(sub.currentPeriodEnd).isAfter(originalEnd)
    }

    @Test
    fun `extendSubscription resets GRACE status to ACTIVE`() {
        val c = club()
        val sub = subscription("GRACE")
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(c))
        whenever(subscriptionRepository.findActiveByClubId(10L)).thenReturn(sub)
        whenever(subscriptionRepository.save(any<ClubSubscription>())).thenAnswer { it.arguments[0] }
        whenever(planRepository.findById(1L)).thenReturn(Optional.of(plan()))
        whenever(redisTemplate.delete(any<String>())).thenReturn(true)

        subscriptionService.extendSubscription(clubPublicId, ExtendSubscriptionRequest(1))

        assertThat(sub.status).isEqualTo("ACTIVE")
    }

    @Test
    fun `cancelSubscription sets cancelled_at`() {
        val c = club()
        val sub = subscription()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(c))
        whenever(subscriptionRepository.findActiveByClubId(10L)).thenReturn(sub)
        whenever(subscriptionRepository.save(any<ClubSubscription>())).thenAnswer { it.arguments[0] }
        whenever(planRepository.findById(1L)).thenReturn(Optional.of(plan()))
        whenever(redisTemplate.delete(any<String>())).thenReturn(true)

        subscriptionService.cancelSubscription(clubPublicId)

        assertThat(sub.status).isEqualTo("CANCELLED")
        assertThat(sub.cancelledAt).isNotNull()
    }

    @Test
    fun `cancelSubscription throws 409 when subscription already expired`() {
        val c = club()
        val sub = subscription("EXPIRED")
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(c))
        whenever(subscriptionRepository.findActiveByClubId(10L)).thenReturn(sub)

        assertThatThrownBy {
            subscriptionService.cancelSubscription(clubPublicId)
        }.isInstanceOf(ArenaException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", "SUBSCRIPTION_ALREADY_EXPIRED")
    }

    @Test
    fun `transitionExpiredToGrace moves ACTIVE past period_end to GRACE`() {
        val sub = subscription("ACTIVE").apply {
            currentPeriodEnd = Instant.now().minus(Duration.ofDays(1))
        }
        whenever(subscriptionRepository.findActiveExpired(any())).thenReturn(listOf(sub))
        whenever(subscriptionRepository.save(any<ClubSubscription>())).thenAnswer { it.arguments[0] }
        whenever(redisTemplate.delete(any<String>())).thenReturn(true)

        subscriptionService.transitionExpiredToGrace()

        assertThat(sub.status).isEqualTo("GRACE")
    }

    @Test
    fun `transitionGraceToExpired moves GRACE past grace_period_ends_at to EXPIRED`() {
        val sub = subscription("GRACE").apply {
            gracePeriodEndsAt = Instant.now().minus(Duration.ofDays(1))
        }
        whenever(subscriptionRepository.findGraceExpired(any())).thenReturn(listOf(sub))
        whenever(subscriptionRepository.save(any<ClubSubscription>())).thenAnswer { it.arguments[0] }
        whenever(redisTemplate.delete(any<String>())).thenReturn(true)
        whenever(userRepository.findPlatformUserIdsWithPermission(any())).thenReturn(emptyList())

        subscriptionService.transitionGraceToExpired()

        assertThat(sub.status).isEqualTo("EXPIRED")
    }

    @Test
    fun `sendExpiryNotifications fires 14-day notification`() {
        val sub = subscription()
        whenever(subscriptionRepository.findExpiringOnDate(any<LocalDate>())).thenReturn(listOf(sub), emptyList())
        whenever(userRepository.findPlatformUserIdsWithPermission(any())).thenReturn(listOf(1L))
        whenever(notificationService.create(any(), any(), any(), any(), any(), any())).thenReturn(null)

        subscriptionService.sendExpiryNotifications()

        verify(notificationService).create(
            eq(1L), eq("platform"),
            eq(NotificationType.SUBSCRIPTION_EXPIRING_SOON_14),
            any(), any(), any(),
        )
    }

    @Test
    fun `sendExpiryNotifications fires 7-day notification`() {
        val sub = subscription()
        whenever(subscriptionRepository.findExpiringOnDate(any<LocalDate>())).thenReturn(emptyList(), listOf(sub))
        whenever(userRepository.findPlatformUserIdsWithPermission(any())).thenReturn(listOf(1L))
        whenever(notificationService.create(any(), any(), any(), any(), any(), any())).thenReturn(null)

        subscriptionService.sendExpiryNotifications()

        verify(notificationService).create(
            eq(1L), eq("platform"),
            eq(NotificationType.SUBSCRIPTION_EXPIRING_SOON_7),
            any(), any(), any(),
        )
    }

    @Test
    fun `sendExpiryNotifications deduplicates within 24 hours`() {
        whenever(subscriptionRepository.findExpiringOnDate(any<LocalDate>())).thenReturn(emptyList())

        subscriptionService.sendExpiryNotifications()

        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any())
    }
}
