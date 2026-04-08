package com.liyaqa.report.schedule

import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class ReportPdfService {
    companion object {
        private const val MAX_PDF_ROWS = 1000
        private val RIYADH_ZONE = ZoneId.of("Asia/Riyadh")
        private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(RIYADH_ZONE)
    }

    fun generatePdf(
        reportName: String,
        clubName: String,
        dateFrom: String,
        dateTo: String,
        columns: List<String>,
        rows: List<Map<String, Any?>>,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(baos)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument, PageSize.A4.rotate(), false)
        document.setMargins(36f, 36f, 50f, 36f)

        addHeader(document, clubName, reportName, dateFrom, dateTo)

        if (rows.isEmpty()) {
            document.add(Paragraph("No data available for the selected period.").setFontSize(12f))
        } else {
            val truncated = rows.size > MAX_PDF_ROWS
            val displayRows = if (truncated) rows.take(MAX_PDF_ROWS) else rows
            addDataTable(document, columns, displayRows)

            if (truncated) {
                document.add(
                    Paragraph(
                        "Showing first 1,000 of ${rows.size} rows. " +
                            "Download the full dataset via CSV export.",
                    )
                        .setFontSize(9f)
                        .setItalic()
                        .setMarginTop(8f),
                )
            }
        }

        addPageNumbers(pdfDocument, document)
        document.close()

        return baos.toByteArray()
    }

    private fun addHeader(
        document: Document,
        clubName: String,
        reportName: String,
        dateFrom: String,
        dateTo: String,
    ) {
        document.add(
            Paragraph(clubName)
                .setFontSize(16f)
                .setBold()
                .setMarginBottom(2f),
        )
        document.add(
            Paragraph(reportName)
                .setFontSize(14f)
                .setMarginBottom(2f),
        )
        document.add(
            Paragraph("Period: $dateFrom to $dateTo")
                .setFontSize(10f)
                .setMarginBottom(2f),
        )
        document.add(
            Paragraph("Generated: ${TIMESTAMP_FMT.format(Instant.now())}")
                .setFontSize(9f)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(16f),
        )
    }

    private fun addDataTable(
        document: Document,
        columns: List<String>,
        rows: List<Map<String, Any?>>,
    ) {
        val table =
            Table(UnitValue.createPercentArray(columns.size))
                .useAllAvailableWidth()

        for (col in columns) {
            table.addHeaderCell(
                Cell().add(Paragraph(col).setBold().setFontSize(9f))
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setPadding(4f),
            )
        }

        for (row in rows) {
            for (col in columns) {
                table.addCell(
                    Cell().add(Paragraph(row[col]?.toString() ?: "").setFontSize(8f))
                        .setPadding(3f),
                )
            }
        }

        document.add(table)
    }

    private fun addPageNumbers(
        pdfDocument: PdfDocument,
        document: Document,
    ) {
        val totalPages = pdfDocument.numberOfPages
        for (i in 1..totalPages) {
            document.showTextAligned(
                Paragraph("Page $i of $totalPages").setFontSize(8f).setFontColor(ColorConstants.GRAY),
                559f,
                20f,
                i,
                TextAlignment.RIGHT,
                com.itextpdf.layout.properties.VerticalAlignment.BOTTOM,
                0f,
            )
        }
    }
}
