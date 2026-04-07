package com.liyaqa.member

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.dto.MemberImportAcceptedResponse
import com.liyaqa.member.dto.MemberImportJobResponse
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStreamReader
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberImportService(
    private val jobRepository: MemberImportJobRepository,
    private val clubRepository: ClubRepository,
    private val auditService: AuditService,
    private val processor: MemberImportProcessor,
) {
    companion object {
        private val log = LoggerFactory.getLogger(MemberImportService::class.java)
        private val REQUIRED_HEADERS = setOf("name_ar", "phone", "gender")
        private val OPTIONAL_HEADERS = setOf("name_en", "email", "date_of_birth")
        private val ALL_HEADERS = REQUIRED_HEADERS + OPTIONAL_HEADERS
    }

    @Transactional
    fun importMembers(
        clubPublicId: UUID,
        file: MultipartFile,
        actorUserId: Long,
        actorPublicId: String,
        actorScope: String,
    ): MemberImportAcceptedResponse {
        val club =
            clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
                }

        val csvBytes = file.bytes
        val fileName = file.originalFilename ?: "import.csv"

        validateFile(csvBytes)

        val job =
            jobRepository.save(
                MemberImportJob(
                    clubId = club.id,
                    createdByUserId = actorUserId,
                    fileName = fileName,
                ),
            )

        auditService.log(
            action = AuditAction.MEMBER_IMPORT_STARTED,
            entityType = "MemberImportJob",
            entityId = job.publicId.toString(),
            actorId = actorPublicId,
            actorScope = actorScope,
            clubId = club.publicId.toString(),
        )

        processor.process(job.id, csvBytes, club.id, club.publicId)

        return MemberImportAcceptedResponse(
            jobId = job.publicId,
            status = job.status.name,
            fileName = job.fileName,
            message = "Import queued. Processing will begin in ~60 seconds.",
        )
    }

    fun getJob(jobPublicId: UUID): MemberImportJobResponse {
        val job =
            jobRepository.findByPublicId(jobPublicId)
                ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Import job not found.")
        return job.toResponse()
    }

    fun listJobs(clubPublicId: UUID): List<MemberImportJobResponse> {
        val club =
            clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
                }
        return jobRepository.findTop10ByClubIdOrderByCreatedAtDesc(club.id).map { it.toResponse() }
    }

    @Transactional
    fun cancelJob(
        jobPublicId: UUID,
        actorPublicId: String,
        actorScope: String,
    ) {
        val job =
            jobRepository.findByPublicId(jobPublicId)
                ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Import job not found.")

        if (job.status != MemberImportJobStatus.QUEUED) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Only queued jobs can be cancelled (current: ${job.status}).",
            )
        }

        job.status = MemberImportJobStatus.CANCELLED
        jobRepository.save(job)

        auditService.log(
            action = AuditAction.MEMBER_IMPORT_CANCELLED,
            entityType = "MemberImportJob",
            entityId = job.publicId.toString(),
            actorId = actorPublicId,
            actorScope = actorScope,
        )
    }

    private fun validateFile(csvBytes: ByteArray) {
        if (csvBytes.isEmpty()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "CSV file is empty.",
            )
        }

        val records =
            try {
                val reader = InputStreamReader(csvBytes.inputStream(), Charsets.UTF_8)
                val format =
                    CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreHeaderCase(true)
                        .setTrim(true)
                        .build()
                CSVParser(reader, format).use { parser ->
                    val headerMap = parser.headerMap
                    val normalizedHeaders = headerMap.keys.map { it.lowercase().trim() }.toSet()

                    val missingHeaders = REQUIRED_HEADERS - normalizedHeaders
                    if (missingHeaders.isNotEmpty()) {
                        throw ArenaException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "business-rule-violation",
                            "Missing required CSV headers: ${missingHeaders.joinToString(", ")}.",
                        )
                    }

                    parser.records
                }
            } catch (e: ArenaException) {
                throw e
            } catch (e: Exception) {
                throw ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "File is not a valid CSV: ${e.message}",
                )
            }

        if (records.isEmpty()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "CSV file has no data rows.",
            )
        }
    }

    private fun MemberImportJob.toResponse() =
        MemberImportJobResponse(
            jobId = publicId,
            status = status.name,
            fileName = fileName,
            totalRows = totalRows,
            importedCount = importedCount,
            skippedCount = skippedCount,
            errorCount = errorCount,
            errorDetail = errorDetail,
            startedAt = startedAt,
            completedAt = completedAt,
            createdAt = createdAt,
        )
}
