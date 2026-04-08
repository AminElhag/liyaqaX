package com.liyaqa.report.schedule

import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class ReportEmailService(
    private val mailSender: JavaMailSender,
    @Value("\${arena-mail.from}") private val fromAddress: String,
) {
    fun sendReportEmail(
        recipients: List<String>,
        reportName: String,
        clubName: String,
        dateFrom: String,
        dateTo: String,
        pdfBytes: ByteArray,
        rowCount: Int,
    ) {
        val subject = "[Liyaqa] $reportName — $dateFrom to $dateTo"
        val body = buildHtmlBody(clubName, reportName, dateFrom, dateTo, rowCount)
        val filename = "${reportName}_${dateFrom}_$dateTo.pdf"

        val message: MimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, "UTF-8")

        helper.setFrom(fromAddress)
        helper.setTo(recipients.toTypedArray())
        helper.setSubject(subject)
        helper.setText(body, true)
        helper.addAttachment(filename, ByteArrayResource(pdfBytes), "application/pdf")

        mailSender.send(message)
    }

    private fun buildHtmlBody(
        clubName: String,
        reportName: String,
        dateFrom: String,
        dateTo: String,
        rowCount: Int,
    ): String =
        """
        <html>
        <body style="font-family: Arial, sans-serif; color: #333;">
            <h2>$clubName</h2>
            <p><strong>Report:</strong> $reportName</p>
            <p><strong>Period:</strong> $dateFrom to $dateTo</p>
            <p><strong>Rows:</strong> $rowCount</p>
            <hr/>
            <p>Please find the report attached as a PDF.</p>
            <p style="color: #888; font-size: 12px;">This is an automated email from Liyaqa. Do not reply.</p>
        </body>
        </html>
        """.trimIndent()
}
