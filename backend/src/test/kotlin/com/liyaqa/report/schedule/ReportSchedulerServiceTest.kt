package com.liyaqa.report.schedule

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.report.builder.ReportBuilderService
import com.liyaqa.report.builder.ReportTemplate
import com.liyaqa.report.builder.ReportTemplateRepository
import com.liyaqa.report.builder.dto.ReportResultResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
class ReportSchedulerServiceTest {
    @Mock lateinit var reportScheduleRepository: ReportScheduleRepository

    @Mock lateinit var reportTemplateRepository: ReportTemplateRepository

    @Mock lateinit var reportBuilderService: ReportBuilderService

    @Mock lateinit var reportPdfService: ReportPdfService

    @Mock lateinit var reportEmailService: ReportEmailService

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var redisTemplate: StringRedisTemplate

    @Mock lateinit var valueOps: ValueOperations<String, String>

    private val objectMapper = ObjectMapper()
    private lateinit var service: ReportSchedulerService

    private val riyadh = ZoneId.of("Asia/Riyadh")

    @BeforeEach
    fun setUp() {
        service =
            ReportSchedulerService(
                reportScheduleRepository, reportTemplateRepository, reportBuilderService,
                reportPdfService, reportEmailService, clubRepository, redisTemplate, objectMapper,
            )
    }

    private fun makeSchedule(
        frequency: String,
        templateId: Long = 1L,
    ) = ReportSchedule(
        templateId = templateId,
        clubId = 10L,
        frequency = frequency,
        recipientsJson = """["owner@test.com"]""",
    )

    private fun makeTemplate() =
        ReportTemplate(
            clubId = 10L,
            name = "Test Report",
            metrics = """["revenue"]""",
            dimensions = """["month"]""",
        )

    private fun makeResult() =
        ReportResultResponse(
            templateId = UUID.randomUUID(),
            runAt = "2026-04-01T04:00:00Z",
            dateFrom = "2026-03-01",
            dateTo = "2026-03-31",
            columns = listOf("month", "revenue"),
            rows = listOf(mapOf("month" to "2026-03", "revenue" to 100000)),
            rowCount = 1,
            truncated = false,
            fromCache = false,
        )

    private fun stubLockAcquired() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.setIfAbsent(any(), any(), any(), any<TimeUnit>())).thenReturn(true)
        whenever(redisTemplate.delete(any<String>())).thenReturn(true)
    }

    private fun stubFullRun() {
        stubLockAcquired()
        whenever(reportTemplateRepository.findById(1L)).thenReturn(Optional.of(makeTemplate()))
        whenever(reportBuilderService.runReport(any(), any(), eq(10L), any())).thenReturn(makeResult())
        whenever(clubRepository.findById(10L)).thenReturn(
            Optional.of(
                Club(organizationId = 1L, nameAr = "نادي", nameEn = "Test Club"),
            ),
        )
        whenever(reportPdfService.generatePdf(any(), any(), any(), any(), any(), any())).thenReturn(ByteArray(100))
        whenever(reportScheduleRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `daily schedule isDue returns true every day`() {
        val monday = ZonedDateTime.of(2026, 4, 6, 7, 0, 0, 0, riyadh)
        val wednesday = ZonedDateTime.of(2026, 4, 8, 7, 0, 0, 0, riyadh)
        assertEquals(true, service.isDue("daily", monday))
        assertEquals(true, service.isDue("daily", wednesday))
    }

    @Test
    fun `weekly schedule isDue on Monday only`() {
        val monday = ZonedDateTime.of(2026, 4, 6, 7, 0, 0, 0, riyadh)
        val tuesday = ZonedDateTime.of(2026, 4, 7, 7, 0, 0, 0, riyadh)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(true, service.isDue("weekly", monday))
        assertEquals(false, service.isDue("weekly", tuesday))
    }

    @Test
    fun `monthly schedule isDue on 1st only`() {
        val first = ZonedDateTime.of(2026, 4, 1, 7, 0, 0, 0, riyadh)
        val fifteenth = ZonedDateTime.of(2026, 4, 15, 7, 0, 0, 0, riyadh)
        assertEquals(true, service.isDue("monthly", first))
        assertEquals(false, service.isDue("monthly", fifteenth))
    }

    @Test
    fun `Redis lock held skips with no processing`() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.setIfAbsent(any(), any(), any(), any<TimeUnit>())).thenReturn(false)

        val schedule = makeSchedule("daily")
        val now = ZonedDateTime.of(2026, 4, 7, 7, 0, 0, 0, riyadh)

        service.processSchedule(schedule, now)

        verify(reportBuilderService, never()).runReport(any(), any(), any(), any())
    }

    @Test
    fun `run failure sets lastRunStatus to failed without throwing`() {
        stubLockAcquired()
        whenever(reportTemplateRepository.findById(1L)).thenReturn(Optional.of(makeTemplate()))
        whenever(reportBuilderService.runReport(any(), any(), eq(10L), any()))
            .thenThrow(RuntimeException("SQL error"))
        whenever(reportScheduleRepository.save(any())).thenAnswer { it.arguments[0] }

        val schedule = makeSchedule("daily")
        val now = ZonedDateTime.of(2026, 4, 7, 7, 0, 0, 0, riyadh)

        service.processSchedule(schedule, now)

        assertEquals("failed", schedule.lastRunStatus)
        assertEquals("SQL error", schedule.lastError)
    }

    @Test
    fun `email failure after result stored persists result and sets failed`() {
        stubLockAcquired()
        whenever(reportTemplateRepository.findById(1L)).thenReturn(Optional.of(makeTemplate()))
        whenever(reportBuilderService.runReport(any(), any(), eq(10L), any())).thenReturn(makeResult())
        whenever(clubRepository.findById(10L)).thenReturn(
            Optional.of(
                Club(organizationId = 1L, nameAr = "نادي", nameEn = "Test Club"),
            ),
        )
        whenever(reportPdfService.generatePdf(any(), any(), any(), any(), any(), any())).thenReturn(ByteArray(100))
        whenever(reportEmailService.sendReportEmail(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("SMTP timeout"))
        whenever(reportScheduleRepository.save(any())).thenAnswer { it.arguments[0] }

        val schedule = makeSchedule("daily")
        val now = ZonedDateTime.of(2026, 4, 7, 7, 0, 0, 0, riyadh)

        service.processSchedule(schedule, now)

        verify(reportBuilderService).runReport(any(), any(), eq(10L), any())
        assertEquals("failed", schedule.lastRunStatus)
        assertEquals("SMTP timeout", schedule.lastError)
    }

    @Test
    fun `successful run sets lastRunStatus to success`() {
        stubFullRun()

        val schedule = makeSchedule("daily")
        val now = ZonedDateTime.of(2026, 4, 7, 7, 0, 0, 0, riyadh)

        service.processSchedule(schedule, now)

        assertEquals("success", schedule.lastRunStatus)
        assertNull(schedule.lastError)
    }

    @Test
    fun `daily date range is yesterday`() {
        val now = ZonedDateTime.of(2026, 4, 7, 7, 0, 0, 0, riyadh)
        val (from, to) = service.computeDateRange("daily", now)
        assertEquals("2026-04-06", from.toString())
        assertEquals("2026-04-06", to.toString())
    }

    @Test
    fun `weekly date range is last 7 days`() {
        val now = ZonedDateTime.of(2026, 4, 6, 7, 0, 0, 0, riyadh)
        val (from, to) = service.computeDateRange("weekly", now)
        assertEquals("2026-03-30", from.toString())
        assertEquals("2026-04-05", to.toString())
    }

    @Test
    fun `monthly date range is previous calendar month`() {
        val now = ZonedDateTime.of(2026, 4, 1, 7, 0, 0, 0, riyadh)
        val (from, to) = service.computeDateRange("monthly", now)
        assertEquals("2026-03-01", from.toString())
        assertEquals("2026-03-31", to.toString())
    }
}
