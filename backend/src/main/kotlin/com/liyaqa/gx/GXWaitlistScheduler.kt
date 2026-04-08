package com.liyaqa.gx

import com.liyaqa.member.MemberRepository
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class GXWaitlistScheduler(
    private val waitlistRepository: GXWaitlistRepository,
    private val waitlistService: GXWaitlistService,
    private val memberRepository: MemberRepository,
    private val classInstanceRepository: GXClassInstanceRepository,
    private val classTypeRepository: GXClassTypeRepository,
    private val notificationService: NotificationService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(GXWaitlistScheduler::class.java)
        private const val OFFER_WINDOW_HOURS = 2L
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    @Transactional
    fun expireStaleOffers() {
        val threshold = Instant.now().minus(OFFER_WINDOW_HOURS, ChronoUnit.HOURS)
        val expired = waitlistRepository.findExpiredOffers(threshold)

        if (expired.isEmpty()) return

        log.info("Expiring {} stale waitlist offers", expired.size)

        for (entry in expired) {
            entry.status = GXWaitlistStatus.EXPIRED
            waitlistRepository.save(entry)

            // Send expiry notification
            try {
                val member = memberRepository.findById(entry.memberId).orElse(null)
                val instance = classInstanceRepository.findById(entry.classInstanceId).orElse(null)
                val classType = instance?.let { classTypeRepository.findById(it.classTypeId).orElse(null) }
                if (member != null) {
                    notificationService.create(
                        recipientUserId = member.userId,
                        recipientScope = "member",
                        type = NotificationType.GX_WAITLIST_EXPIRED,
                        paramsJson = """{"className":"${classType?.nameEn ?: ""}"}""",
                        entityType = "GXWaitlistEntry",
                        entityId = entry.publicId.toString(),
                    )
                }
            } catch (e: Exception) {
                log.warn("Failed to create GX_WAITLIST_EXPIRED notification: {}", e.message)
            }

            // Immediately promote next person
            waitlistService.promoteNext(entry.classInstanceId)
        }
    }
}
