package com.liyaqa.report.builder

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.report.builder.dto.RunReportRequest
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ReportBuilderServiceTest {
    @Mock lateinit var em: EntityManager

    @Mock lateinit var reportResultRepository: ReportResultRepository

    @Spy var objectMapper: ObjectMapper = jacksonObjectMapper()

    @Mock lateinit var redisTemplate: StringRedisTemplate

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var valueOps: ValueOperations<String, String>

    @InjectMocks lateinit var service: ReportBuilderService

    private val clubId = 1L
    private val actorId = "test-actor"

    @Test
    fun `runReport happy path returns result`() {
        val template = createTemplate()
        val request =
            RunReportRequest(
                dateFrom = LocalDate.of(2026, 1, 1),
                dateTo = LocalDate.of(2026, 3, 31),
            )

        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        whenever(valueOps.get(any())).thenReturn(null)

        val mockQuery = org.mockito.Mockito.mock(Query::class.java)
        whenever(em.createNativeQuery(any<String>())).thenReturn(mockQuery)
        whenever(mockQuery.setParameter(any<String>(), anyOrNull())).thenReturn(mockQuery)
        whenever(mockQuery.resultList).thenReturn(
            listOf(
                arrayOf("2026-01-01", "Riyadh", 150000L, 42L) as Array<*>,
            ),
        )

        whenever(reportResultRepository.save(any<ReportResult>()))
            .thenAnswer { it.arguments[0] as ReportResult }

        val result = service.runReport(template, request, clubId, actorId)

        assertThat(result.fromCache).isFalse()
        assertThat(result.rowCount).isEqualTo(1)
        assertThat(result.columns).contains("month", "branch", "revenue", "new_members")
    }

    @Test
    fun `runReport cache hit returns fromCache true`() {
        val template = createTemplate()
        val request =
            RunReportRequest(
                dateFrom = LocalDate.of(2026, 1, 1),
                dateTo = LocalDate.of(2026, 3, 31),
            )

        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        val cachedResponse =
            """{"templateId":"${template.publicId}",""" +
                """"runAt":"2026-01-01T00:00:00Z","dateFrom":"2026-01-01",""" +
                """"dateTo":"2026-03-31","columns":["month","branch","revenue","new_members"],""" +
                """"rows":[],"rowCount":0,"truncated":false,"fromCache":false}"""
        whenever(valueOps.get(any())).thenReturn(cachedResponse)

        val result = service.runReport(template, request, clubId, actorId)

        assertThat(result.fromCache).isTrue()
    }

    @Test
    fun `runReport date range exceeding 366 days returns 422`() {
        val template = createTemplate()
        val request =
            RunReportRequest(
                dateFrom = LocalDate.of(2025, 1, 1),
                dateTo = LocalDate.of(2026, 3, 1),
            )

        assertThatThrownBy { service.runReport(template, request, clubId, actorId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat(it.message).contains("366 days")
            })
    }

    @Test
    fun `getLastResult with no result returns 404`() {
        val template = createTemplate()

        whenever(reportResultRepository.findFirstByTemplateIdAndDeletedAtIsNullOrderByRunAtDesc(template.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.getLastResult(template) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.NOT_FOUND)
                assertThat(it.message).contains("No result available")
            })
    }

    @Test
    fun `exportCsv with no result returns 404`() {
        val template = createTemplate()

        whenever(reportResultRepository.findFirstByTemplateIdAndDeletedAtIsNullOrderByRunAtDesc(template.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.exportCsv(template) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.NOT_FOUND)
            })
    }

    @Test
    fun `runReport with dateFrom after dateTo returns 422`() {
        val template = createTemplate()
        val request =
            RunReportRequest(
                dateFrom = LocalDate.of(2026, 3, 31),
                dateTo = LocalDate.of(2026, 1, 1),
            )

        assertThatThrownBy { service.runReport(template, request, clubId, actorId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat(it.message).contains("cannot be after")
            })
    }

    private fun createTemplate(): ReportTemplate =
        ReportTemplate(
            clubId = clubId,
            name = "Test Report",
            metrics = """["revenue","new_members"]""",
            dimensions = """["month","branch"]""",
            metricScope = "revenue",
        )
}
