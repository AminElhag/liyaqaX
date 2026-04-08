package com.liyaqa.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class NotificationEmailService(
    private val mailSender: JavaMailSender,
    @Value("\${arena-mail.from}") private val fromAddress: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(NotificationEmailService::class.java)

        private val SUBJECT_MAP =
            mapOf(
                NotificationType.MEMBERSHIP_EXPIRING_SOON to "[Liyaqa] Your membership is expiring soon",
                NotificationType.PAYMENT_COLLECTED to "[Liyaqa] Payment received",
                NotificationType.PT_SESSION_REMINDER to "[Liyaqa] PT session reminder",
            )
    }

    fun sendNotificationEmail(
        recipientEmail: String,
        type: NotificationType,
        paramsJson: String?,
    ) {
        val subject = SUBJECT_MAP[type] ?: return
        val body = buildHtmlBody(type, paramsJson)

        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(fromAddress)
        helper.setTo(recipientEmail)
        helper.setSubject(subject)
        helper.setText(body, true)

        mailSender.send(message)
        log.info("Notification email sent: type={}, to={}", type, recipientEmail)
    }

    private fun buildHtmlBody(
        type: NotificationType,
        paramsJson: String?,
    ): String {
        val content =
            when (type) {
                NotificationType.MEMBERSHIP_EXPIRING_SOON ->
                    "<p>Your membership will expire in 7 days. Please renew to maintain access to the club.</p>"
                NotificationType.PAYMENT_COLLECTED ->
                    "<p>A payment has been successfully collected on your account.</p>"
                NotificationType.PT_SESSION_REMINDER ->
                    "<p>This is a reminder that you have a PT session scheduled for tomorrow.</p>"
                else -> "<p>You have a new notification.</p>"
            }

        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                <h2 style="color: #1a1a1a;">Liyaqa</h2>
                $content
                <hr style="border: none; border-top: 1px solid #e5e5e5; margin: 20px 0;">
                <p style="font-size: 12px; color: #666;">This is an automated notification from Liyaqa.</p>
            </body>
            </html>
            """.trimIndent()
    }
}
