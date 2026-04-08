package com.liyaqa.report.schedule

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ReportPdfServiceTest {
    private val service = ReportPdfService()

    private fun extractText(pdf: ByteArray): String {
        val reader = PdfReader(ByteArrayInputStream(pdf))
        val doc = PdfDocument(reader)
        val sb = StringBuilder()
        for (i in 1..doc.numberOfPages) {
            sb.append(PdfTextExtractor.getTextFromPage(doc.getPage(i)))
        }
        doc.close()
        return sb.toString()
    }

    @Test
    fun `generates non-empty PDF bytes for sample data`() {
        val columns = listOf("month", "revenue", "new_members")
        val rows =
            listOf(
                mapOf("month" to "2026-01", "revenue" to 50000, "new_members" to 12),
                mapOf("month" to "2026-02", "revenue" to 62000, "new_members" to 15),
            )

        val pdf = service.generatePdf("Monthly Revenue", "Elixir Gym", "2026-01-01", "2026-02-28", columns, rows)

        assertNotNull(pdf)
        assertTrue(pdf.size > 100, "PDF should be non-trivial size")
        assertTrue(pdf.copyOfRange(0, 5).decodeToString().startsWith("%PDF"), "Should start with PDF magic bytes")

        val text = extractText(pdf)
        assertTrue(text.contains("Elixir Gym"), "Should contain club name")
        assertTrue(text.contains("Monthly Revenue"), "Should contain report name")
        assertTrue(text.contains("2026-01-01"), "Should contain date range")
    }

    @Test
    fun `caps at 1000 rows and includes truncation note`() {
        val columns = listOf("day", "revenue")
        val rows =
            (1..1001).map { i ->
                mapOf("day" to "2026-01-${(i % 28 + 1).toString().padStart(2, '0')}", "revenue" to i * 100)
            }

        val pdf = service.generatePdf("Daily Revenue", "Elixir Gym", "2026-01-01", "2026-12-31", columns, rows)

        assertNotNull(pdf)
        assertTrue(pdf.size > 100)

        val text = extractText(pdf)
        assertTrue(text.contains("1,000"), "Should contain truncation count")
        assertTrue(text.contains("CSV"), "Should reference CSV export")
    }

    @Test
    fun `empty result produces PDF with no-data message`() {
        val columns = listOf("month", "revenue")
        val rows = emptyList<Map<String, Any?>>()

        val pdf = service.generatePdf("Empty Report", "Elixir Gym", "2026-01-01", "2026-01-31", columns, rows)

        assertNotNull(pdf)
        assertTrue(pdf.size > 100)

        val text = extractText(pdf)
        assertTrue(text.contains("No data"), "Should contain no-data message")
    }
}
