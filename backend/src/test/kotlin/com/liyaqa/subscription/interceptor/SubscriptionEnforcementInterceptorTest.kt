package com.liyaqa.subscription.interceptor

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.security.JwtClaims
import com.liyaqa.subscription.entity.ClubSubscription
import com.liyaqa.subscription.service.SubscriptionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SubscriptionEnforcementInterceptorTest {

    @Mock lateinit var subscriptionService: SubscriptionService
    @Mock lateinit var clubRepository: ClubRepository
    @Mock lateinit var redisTemplate: StringRedisTemplate
    @Mock lateinit var valueOps: ValueOperations<String, String>

    private lateinit var interceptor: SubscriptionEnforcementInterceptor

    private val clubPublicId = UUID.randomUUID()
    private val clubId = 10L
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse

    @BeforeEach
    fun setUp() {
        interceptor = SubscriptionEnforcementInterceptor(subscriptionService, clubRepository, redisTemplate)
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
    }

    private fun setAuth(clubId: UUID? = clubPublicId, scope: String = "club") {
        val claims = JwtClaims(
            userPublicId = UUID.randomUUID(),
            roleId = UUID.randomUUID(),
            scope = scope,
            organizationId = UUID.randomUUID(),
            clubId = clubId,
        )
        val auth = UsernamePasswordAuthenticationToken("user", null, emptyList())
        auth.details = claims
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun club(): Club = Club(
        organizationId = 1L, nameAr = "نادي", nameEn = "Club", email = "c@e.com",
    ).apply {
        val idField = this::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, clubId)
    }

    private fun subscription(status: String) = ClubSubscription(
        id = 1L, clubId = clubId, planId = 1L, status = status,
        currentPeriodStart = Instant.now().minus(Duration.ofDays(10)),
        currentPeriodEnd = Instant.now().plus(Duration.ofDays(20)),
        gracePeriodEndsAt = Instant.now().plus(Duration.ofDays(27)),
        assignedByUserId = 1L,
    )

    @Test
    fun `allows request when subscription is ACTIVE`() {
        setAuth()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club()))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("subscription_status:$clubId")).thenReturn(null)
        whenever(subscriptionService.findActiveByClubId(clubId)).thenReturn(subscription("ACTIVE"))

        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isTrue()
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `allows request with grace header when subscription is GRACE`() {
        setAuth()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club()))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("subscription_status:$clubId")).thenReturn(null)
        whenever(subscriptionService.findActiveByClubId(clubId)).thenReturn(subscription("GRACE"))

        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isTrue()
        assertThat(response.getHeader("X-Subscription-Grace")).isEqualTo("true")
        assertThat(response.getHeader("X-Grace-Days-Remaining")).isNotNull()
    }

    @Test
    fun `blocks request with 402 when subscription is EXPIRED`() {
        setAuth()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club()))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("subscription_status:$clubId")).thenReturn("EXPIRED")

        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isFalse()
        assertThat(response.status).isEqualTo(402)
        assertThat(response.contentAsString).contains("SUBSCRIPTION_EXPIRED")
    }

    @Test
    fun `blocks request with 402 when subscription is CANCELLED`() {
        setAuth()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club()))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("subscription_status:$clubId")).thenReturn("CANCELLED")

        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isFalse()
        assertThat(response.status).isEqualTo(402)
    }

    @Test
    fun `skips check for platform-scope JWT (no clubId claim)`() {
        setAuth(clubId = null, scope = "platform")

        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isTrue()
        verify(subscriptionService, never()).findActiveByClubId(any())
    }

    @Test
    fun `uses Redis cache and avoids DB call on cache hit`() {
        setAuth()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club()))
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get("subscription_status:$clubId")).thenReturn("ACTIVE")

        val result = interceptor.preHandle(request, response, Any())

        assertThat(result).isTrue()
        verify(subscriptionService, never()).findActiveByClubId(any())
    }
}
