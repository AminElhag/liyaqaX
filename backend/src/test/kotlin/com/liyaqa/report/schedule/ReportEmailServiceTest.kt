package com.liyaqa.report.schedule

import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.mail.javamail.JavaMailSender

@ExtendWith(MockitoExtension::class)
class ReportEmailServiceTest {
    @Mock
    lateinit var mailSender: JavaMailSender

    @Mock
    lateinit var mimeMessage: MimeMessage

    @Captor
    lateinit var messageCaptor: ArgumentCaptor<MimeMessage>

    @Test
    fun `sends email to all recipients`() {
        whenever(mailSender.createMimeMessage()).thenReturn(mimeMessage)

        val service = ReportEmailService(mailSender, "noreply@liyaqa.com")
        val recipients = listOf("owner@elixir.com", "manager@elixir.com")

        service.sendReportEmail(
            recipients = recipients,
            reportName = "Monthly Revenue",
            clubName = "Elixir Gym",
            dateFrom = "2026-01-01",
            dateTo = "2026-01-31",
            pdfBytes = ByteArray(100),
            rowCount = 42,
        )

        org.mockito.kotlin.verify(mailSender).send(messageCaptor.capture())
        assertEquals(mimeMessage, messageCaptor.value)
    }

    @Test
    fun `subject line formatted correctly`() {
        whenever(mailSender.createMimeMessage()).thenReturn(mimeMessage)

        val service = ReportEmailService(mailSender, "noreply@liyaqa.com")

        service.sendReportEmail(
            recipients = listOf("test@test.com"),
            reportName = "Daily PT Sessions",
            clubName = "Test Club",
            dateFrom = "2026-03-01",
            dateTo = "2026-03-01",
            pdfBytes = ByteArray(50),
            rowCount = 10,
        )

        org.mockito.kotlin.verify(mailSender).send(org.mockito.kotlin.any<MimeMessage>())
    }

    @Test
    fun `attachment filename formatted correctly`() {
        val realMessage =
            jakarta.mail.Session.getDefaultInstance(java.util.Properties()).let {
                jakarta.mail.internet.MimeMessage(it)
            }
        whenever(mailSender.createMimeMessage()).thenReturn(realMessage)

        val service = ReportEmailService(mailSender, "noreply@liyaqa.com")

        service.sendReportEmail(
            recipients = listOf("test@test.com"),
            reportName = "Weekly Revenue",
            clubName = "Test Club",
            dateFrom = "2026-03-01",
            dateTo = "2026-03-07",
            pdfBytes = ByteArray(50),
            rowCount = 5,
        )

        org.mockito.kotlin.verify(mailSender).send(org.mockito.kotlin.any<MimeMessage>())

        val content = realMessage.content as jakarta.mail.internet.MimeMultipart
        assertTrue(realMessage.subject.contains("[Liyaqa]"), "Subject should contain [Liyaqa]")
        assertTrue(realMessage.subject.contains("Weekly Revenue"), "Subject should contain report name")
        assertTrue(realMessage.subject.contains("2026-03-01"), "Subject should contain dateFrom")

        var foundPdf = false
        for (i in 0 until content.count) {
            val part = content.getBodyPart(i)
            if (part.fileName != null && part.fileName.endsWith(".pdf")) {
                assertEquals("Weekly Revenue_2026-03-01_2026-03-07.pdf", part.fileName)
                foundPdf = true
            }
        }
        assertTrue(foundPdf, "Should have PDF attachment")
    }
}
