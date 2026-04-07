package com.liyaqa.report.builder

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.report.builder.dto.CreateReportTemplateRequest
import com.liyaqa.report.builder.dto.UpdateReportTemplateRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReportTemplateServiceTest {
    @Mock lateinit var reportTemplateRepository: ReportTemplateRepository

    @Mock lateinit var reportResultRepository: ReportResultRepository

    @Mock lateinit var auditService: AuditService

    @Spy var objectMapper: ObjectMapper = jacksonObjectMapper()

    @InjectMocks lateinit var service: ReportTemplateService

    private val clubId = 1L

    @Test
    fun `create template with valid codes succeeds`() {
        val request =
            CreateReportTemplateRequest(
                name = "Revenue by Branch",
                metrics = listOf("revenue", "new_members"),
                dimensions = listOf("month", "branch"),
            )

        whenever(reportTemplateRepository.existsByNameAndClubIdAndDeletedAtIsNull(request.name, clubId))
            .thenReturn(false)
        whenever(reportTemplateRepository.save(any<ReportTemplate>()))
            .thenAnswer { it.arguments[0] as ReportTemplate }
        whenever(reportResultRepository.findFirstByTemplateIdAndDeletedAtIsNullOrderByRunAtDesc(any()))
            .thenReturn(Optional.empty())

        val result = service.createTemplate(request, clubId)

        assertThat(result.name).isEqualTo("Revenue by Branch")
        assertThat(result.metrics).containsExactly("revenue", "new_members")
        assertThat(result.dimensions).containsExactly("month", "branch")
    }

    @Test
    fun `create template with unknown metric returns 422`() {
        val request =
            CreateReportTemplateRequest(
                name = "Bad",
                metrics = listOf("revenue", "nonexistent_metric"),
                dimensions = listOf("month"),
            )

        assertThatThrownBy { service.createTemplate(request, clubId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat(it.message).contains("Unknown metric: nonexistent_metric")
            })
    }

    @Test
    fun `create template with 11 metrics returns 422`() {
        val request =
            CreateReportTemplateRequest(
                name = "Too Many",
                metrics = List(11) { "revenue" },
                dimensions = listOf("month"),
            )

        assertThatThrownBy { service.createTemplate(request, clubId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat(it.message).contains("Maximum 10 metrics allowed")
            })
    }

    @Test
    fun `create template with incompatible metric and dimension returns 422`() {
        val request =
            CreateReportTemplateRequest(
                name = "Incompatible",
                metrics = listOf("revenue"),
                dimensions = listOf("class_type"),
            )

        assertThatThrownBy { service.createTemplate(request, clubId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat(it.message).contains("not compatible")
            })
    }

    @Test
    fun `create template with empty metrics returns 422`() {
        val request =
            CreateReportTemplateRequest(
                name = "Empty",
                metrics = emptyList(),
                dimensions = listOf("month"),
            )

        assertThatThrownBy { service.createTemplate(request, clubId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat(it.message).contains("At least one metric")
            })
    }

    @Test
    fun `create template with empty dimensions returns 422`() {
        val request =
            CreateReportTemplateRequest(
                name = "Empty",
                metrics = listOf("revenue"),
                dimensions = emptyList(),
            )

        assertThatThrownBy { service.createTemplate(request, clubId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat(it.message).contains("At least one dimension")
            })
    }

    @Test
    fun `create template with duplicate name returns 409`() {
        val request =
            CreateReportTemplateRequest(
                name = "Existing Report",
                metrics = listOf("revenue"),
                dimensions = listOf("month"),
            )

        whenever(reportTemplateRepository.existsByNameAndClubIdAndDeletedAtIsNull(request.name, clubId))
            .thenReturn(true)

        assertThatThrownBy { service.createTemplate(request, clubId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.CONFLICT)
            })
    }

    @Test
    fun `delete template soft deletes`() {
        val template = createTemplate()
        whenever(reportTemplateRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(template.publicId, clubId))
            .thenReturn(Optional.of(template))
        whenever(reportTemplateRepository.save(any<ReportTemplate>()))
            .thenAnswer { it.arguments[0] }

        service.deleteTemplate(template.publicId, clubId)

        assertThat(template.deletedAt).isNotNull()
    }

    @Test
    fun `delete template with wrong club returns 404`() {
        val templateId = UUID.randomUUID()
        val otherClubId = 999L

        whenever(reportTemplateRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(templateId, otherClubId))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.deleteTemplate(templateId, otherClubId) }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({
                assertThat((it as ArenaException).status).isEqualTo(HttpStatus.NOT_FOUND)
            })
    }

    @Test
    fun `update template partially updates fields`() {
        val template = createTemplate()
        whenever(reportTemplateRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(template.publicId, clubId))
            .thenReturn(Optional.of(template))
        whenever(reportTemplateRepository.save(any<ReportTemplate>()))
            .thenAnswer { it.arguments[0] }
        whenever(reportResultRepository.findFirstByTemplateIdAndDeletedAtIsNullOrderByRunAtDesc(any()))
            .thenReturn(Optional.empty())

        val result =
            service.updateTemplate(
                template.publicId,
                UpdateReportTemplateRequest(name = "Updated Name"),
                clubId,
            )

        assertThat(result.name).isEqualTo("Updated Name")
        assertThat(result.metrics).containsExactly("revenue", "new_members")
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
