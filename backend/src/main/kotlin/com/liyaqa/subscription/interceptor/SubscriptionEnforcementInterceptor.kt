package com.liyaqa.subscription.interceptor

import com.liyaqa.club.ClubRepository
import com.liyaqa.security.JwtClaims
import com.liyaqa.subscription.service.SubscriptionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.time.Instant

@Component
class SubscriptionEnforcementInterceptor(
    private val subscriptionService: SubscriptionService,
    private val clubRepository: ClubRepository,
    private val redisTemplate: StringRedisTemplate,
) : HandlerInterceptor {

    companion object {
        private const val CACHE_PREFIX = "subscription_status:"
        private val CACHE_TTL = Duration.ofMinutes(5)
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val claims = extractClaims() ?: return true
        val clubPublicId = claims.clubId ?: return true

        val club = clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId).orElse(null)
            ?: return true
        val clubId = club.id

        val status = getCachedStatus(clubId) ?: fetchAndCacheStatus(clubId)

        return when (status) {
            "ACTIVE" -> true
            "GRACE" -> {
                response.setHeader("X-Subscription-Grace", "true")
                val subscription = subscriptionService.findActiveByClubId(clubId)
                if (subscription != null) {
                    val daysRemaining = Duration.between(Instant.now(), subscription.gracePeriodEndsAt)
                        .toDays().coerceAtLeast(0)
                    response.setHeader("X-Grace-Days-Remaining", daysRemaining.toString())
                }
                true
            }
            "EXPIRED", "CANCELLED" -> {
                response.status = 402
                response.contentType = "application/json"
                response.writer.write(
                    """{"errorCode":"SUBSCRIPTION_EXPIRED","message":"Club subscription has expired. Please contact support to renew."}""",
                )
                false
            }
            else -> true
        }
    }

    private fun extractClaims(): JwtClaims? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        return auth.details as? JwtClaims
    }

    private fun getCachedStatus(clubId: Long): String? =
        redisTemplate.opsForValue().get("$CACHE_PREFIX$clubId")

    private fun fetchAndCacheStatus(clubId: Long): String? {
        val subscription = subscriptionService.findActiveByClubId(clubId) ?: return null
        val status = subscription.status
        redisTemplate.opsForValue().set("$CACHE_PREFIX$clubId", status, CACHE_TTL)
        return status
    }
}
