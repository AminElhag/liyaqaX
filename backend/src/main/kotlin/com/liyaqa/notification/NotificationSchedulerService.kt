package com.liyaqa.notification

import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.pt.PTSessionRepository
import com.liyaqa.trainer.TrainerRepository
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class NotificationSchedulerService(
    private val notificationService: NotificationService,
    private val notificationEmailService: NotificationEmailService,
    private val membershipRepository: MembershipRepository,
    private val memberRepository: MemberRepository,
    private val ptSessionRepository: PTSessionRepository,
    private val gxClassInstanceRepository: GXClassInstanceRepository,
    private val trainerRepository: TrainerRepository,
    private val userRepository: UserRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(NotificationSchedulerService::class.java)
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "UTC")
    @Transactional
    fun runDailyNotifications() {
        log.info("Starting daily notification scheduler")
        try {
            generateMembershipExpiringNotifications()
        } catch (e: Exception) {
            log.warn("Error generating membership expiring notifications: {}", e.message)
        }
        try {
            generatePtSessionReminders()
        } catch (e: Exception) {
            log.warn("Error generating PT session reminders: {}", e.message)
        }
        try {
            generateLowGxSpotsNotifications()
        } catch (e: Exception) {
            log.warn("Error generating low GX spots notifications: {}", e.message)
        }
        try {
            cleanupOldNotifications()
        } catch (e: Exception) {
            log.warn("Error cleaning up old notifications: {}", e.message)
        }
        log.info("Daily notification scheduler completed")
    }

    private fun generateMembershipExpiringNotifications() {
        val sevenDaysFromNow = java.time.LocalDate.now().plusDays(7)
        val memberships =
            membershipRepository.findAllByEndDateAndMembershipStatusAndDeletedAtIsNull(
                sevenDaysFromNow,
                "active",
            )
        var count = 0
        for (membership in memberships) {
            val member = memberRepository.findById(membership.memberId).orElse(null) ?: continue
            val notification =
                notificationService.create(
                    recipientUserId = member.userId,
                    recipientScope = "member",
                    type = NotificationType.MEMBERSHIP_EXPIRING_SOON,
                    paramsJson = """{"daysRemaining":7}""",
                    entityType = "Membership",
                    entityId = membership.publicId.toString(),
                )
            if (notification != null) {
                count++
                val user = userRepository.findById(member.userId).orElse(null)
                if (user != null) {
                    sendEmailSafely(notification, user.email)
                }
            }
        }
        if (count > 0) log.info("Created {} MEMBERSHIP_EXPIRING_SOON notifications", count)
    }

    private fun generatePtSessionReminders() {
        val tomorrowStart = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
        val tomorrowEnd = tomorrowStart.plus(1, ChronoUnit.DAYS)
        val sessions =
            ptSessionRepository.findAllByScheduledAtBetweenAndSessionStatusAndDeletedAtIsNull(
                tomorrowStart,
                tomorrowEnd,
                "scheduled",
            )
        var count = 0
        for (session in sessions) {
            val member = memberRepository.findById(session.memberId).orElse(null) ?: continue
            val trainer = trainerRepository.findById(session.trainerId).orElse(null)
            val trainerName = trainer?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: ""
            val notification =
                notificationService.create(
                    recipientUserId = member.userId,
                    recipientScope = "member",
                    type = NotificationType.PT_SESSION_REMINDER,
                    paramsJson = """{"trainerName":"$trainerName","time":"${session.scheduledAt}"}""",
                    entityType = "PTSession",
                    entityId = session.publicId.toString(),
                )
            if (notification != null) {
                count++
                val user = userRepository.findById(member.userId).orElse(null)
                if (user != null) {
                    sendEmailSafely(notification, user.email)
                }
            }
        }
        if (count > 0) log.info("Created {} PT_SESSION_REMINDER notifications", count)
    }

    private fun generateLowGxSpotsNotifications() {
        val now = Instant.now()
        val twentyFourHoursLater = now.plus(24, ChronoUnit.HOURS)
        val instances =
            gxClassInstanceRepository.findAllByScheduledAtBetweenAndInstanceStatusAndDeletedAtIsNull(
                now,
                twentyFourHoursLater,
                "scheduled",
            )
        var count = 0
        for (instance in instances) {
            val spotsRemaining = instance.capacity - instance.bookingsCount
            if (spotsRemaining >= 3) continue

            val trainer = trainerRepository.findById(instance.instructorId).orElse(null) ?: continue
            notificationService.create(
                recipientUserId = trainer.userId,
                recipientScope = "trainer",
                type = NotificationType.LOW_GX_SPOTS,
                paramsJson = """{"spotsRemaining":$spotsRemaining}""",
                entityType = "GXClassInstance",
                entityId = instance.publicId.toString(),
            )?.let { count++ }
        }
        if (count > 0) log.info("Created {} LOW_GX_SPOTS notifications", count)
    }

    private fun cleanupOldNotifications() {
        val cutoff = Instant.now().minus(Duration.ofDays(90))
        val deleted = notificationService.deleteOlderThan(cutoff)
        if (deleted > 0) log.info("Cleaned up {} notifications older than 90 days", deleted)
    }

    private fun sendEmailSafely(
        notification: Notification,
        recipientEmail: String,
    ) {
        try {
            notificationEmailService.sendNotificationEmail(
                recipientEmail = recipientEmail,
                type = notification.type,
                paramsJson = notification.paramsJson,
            )
            notification.emailSentAt = Instant.now()
        } catch (e: Exception) {
            log.error("Failed to send notification email for {}: {}", notification.type, e.message)
        }
    }
}
