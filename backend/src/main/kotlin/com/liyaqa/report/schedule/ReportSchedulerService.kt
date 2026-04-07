package com.liyaqa.report.schedule

import com.liyaqa.club.ClubRepository
import com.liyaqa.report.builder.ReportBuilderService
import com.liyaqa.report.builder.ReportTemplateRepository
import com.liyaqa.report.builder.dto.RunReportRequest
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

@Service
class ReportSchedulerService(
    private val reportScheduleRepository: ReportScheduleRepository,
    private val reportTemplateRepository: ReportTemplateRepository,
    private val reportBuilderService: ReportBuilderService,
    private val reportPdfService: ReportPdfService,
    private val reportEmailService: ReportEmailService,
    private val clubRepository: ClubRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ReportSchedulerService::class.java)
        private val RIYADH_ZONE = ZoneId.of("Asia/Riyadh")
        private const val LOCK_PREFIX = "report_schedule_lock:"
        private const val LOCK_TTL_SECONDS = 300L
        private const val SCHEDULER_ACTOR = "system:scheduler"
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    fun runDueSchedules() {
        val now = ZonedDateTime.now(RIYADH_ZONE)
        val schedules = reportScheduleRepository.findAllByIsActiveTrueAndDeletedAtIsNull()

        for (schedule in schedules) {
            if (!isDue(schedule.frequency, now)) continue
            processSchedule(schedule, now)
        }
    }

    @Transactional
    fun processSchedule(
        schedule: ReportSchedule,
        now: ZonedDateTime,
    ) {
        val lockKey = "$LOCK_PREFIX${schedule.id}"
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", LOCK_TTL_SECONDS, TimeUnit.SECONDS)
        if (acquired != true) {
            log.info("Skipping {} — already running", schedule.id)
            return
        }

        try {
            val template = reportTemplateRepository.findById(schedule.templateId).orElse(null)
            if (template == null) {
                updateStatus(schedule, "failed", "Template not found for id=${schedule.templateId}")
                return
            }

            val (dateFrom, dateTo) = computeDateRange(schedule.frequency, now)
            val request = RunReportRequest(dateFrom = dateFrom, dateTo = dateTo)

            val result = reportBuilderService.runReport(template, request, schedule.clubId, SCHEDULER_ACTOR)

            try {
                val clubName =
                    clubRepository.findById(schedule.clubId)
                        .map { it.nameEn }
                        .orElse("Club")

                val pdfBytes =
                    reportPdfService.generatePdf(
                        reportName = template.name,
                        clubName = clubName,
                        dateFrom = dateFrom.toString(),
                        dateTo = dateTo.toString(),
                        columns = result.columns,
                        rows = result.rows,
                    )

                val recipients: List<String> =
                    objectMapper.readValue(
                        schedule.recipientsJson,
                        objectMapper.typeFactory.constructCollectionType(List::class.java, String::class.java),
                    )

                reportEmailService.sendReportEmail(
                    recipients = recipients,
                    reportName = template.name,
                    clubName = clubName,
                    dateFrom = dateFrom.toString(),
                    dateTo = dateTo.toString(),
                    pdfBytes = pdfBytes,
                    rowCount = result.rowCount,
                )

                updateStatus(schedule, "success", null)
            } catch (e: Exception) {
                log.error("Email/PDF failed for schedule {} after result stored: {}", schedule.id, e.message, e)
                updateStatus(schedule, "failed", e.message?.take(500))
            }
        } catch (e: Exception) {
            log.error("Run failed for schedule {}: {}", schedule.id, e.message, e)
            updateStatus(schedule, "failed", e.message?.take(500))
        } finally {
            redisTemplate.delete(lockKey)
        }
    }

    fun nextRunAt(
        frequency: String,
        now: Instant = Instant.now(),
    ): Instant {
        val riyadhNow = ZonedDateTime.ofInstant(now, RIYADH_ZONE)
        val todayAt7 = riyadhNow.toLocalDate().atTime(7, 0).atZone(RIYADH_ZONE)
        val hasTodayRun = riyadhNow.isAfter(todayAt7) || riyadhNow.isEqual(todayAt7)

        return when (frequency) {
            "daily" -> {
                if (hasTodayRun) {
                    todayAt7.plusDays(1).toInstant()
                } else {
                    todayAt7.toInstant()
                }
            }
            "weekly" -> {
                val nextMonday =
                    if (riyadhNow.dayOfWeek == DayOfWeek.MONDAY && !hasTodayRun) {
                        riyadhNow.toLocalDate()
                    } else {
                        riyadhNow.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    }
                nextMonday.atTime(7, 0).atZone(RIYADH_ZONE).toInstant()
            }
            "monthly" -> {
                val nextFirst =
                    if (riyadhNow.dayOfMonth == 1 && !hasTodayRun) {
                        riyadhNow.toLocalDate()
                    } else {
                        riyadhNow.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth())
                    }
                nextFirst.atTime(7, 0).atZone(RIYADH_ZONE).toInstant()
            }
            else -> todayAt7.plusDays(1).toInstant()
        }
    }

    internal fun isDue(
        frequency: String,
        now: ZonedDateTime,
    ): Boolean =
        when (frequency) {
            "daily" -> true
            "weekly" -> now.dayOfWeek == DayOfWeek.MONDAY
            "monthly" -> now.dayOfMonth == 1
            else -> false
        }

    internal fun computeDateRange(
        frequency: String,
        now: ZonedDateTime,
    ): Pair<LocalDate, LocalDate> {
        val today = now.toLocalDate()
        return when (frequency) {
            "daily" -> {
                val yesterday = today.minusDays(1)
                yesterday to yesterday
            }
            "weekly" -> {
                today.minusDays(7) to today.minusDays(1)
            }
            "monthly" -> {
                val previousMonth = today.minusMonths(1)
                val firstDay = previousMonth.withDayOfMonth(1)
                val lastDay = previousMonth.with(TemporalAdjusters.lastDayOfMonth())
                firstDay to lastDay
            }
            else -> today.minusDays(1) to today.minusDays(1)
        }
    }

    private fun updateStatus(
        schedule: ReportSchedule,
        status: String,
        error: String?,
    ) {
        schedule.lastRunAt = Instant.now()
        schedule.lastRunStatus = status
        schedule.lastError = error
        reportScheduleRepository.save(schedule)
    }
}
