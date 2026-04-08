package com.liyaqa.member

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.ClubRepository
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Component
class MemberImportProcessor(
    private val jobRepository: MemberImportJobRepository,
    private val memberRepository: MemberRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val branchRepository: BranchRepository,
    private val clubRepository: ClubRepository,
    private val notificationService: NotificationService,
    private val auditService: AuditService,
    private val mailSender: JavaMailSender,
    private val passwordEncoder: PasswordEncoder,
    private val objectMapper: ObjectMapper,
    @Value("\${arena-mail.from}") private val fromAddress: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(MemberImportProcessor::class.java)
        private val PHONE_REGEX = Regex("""^(\+966\s?\d{8,9}|05\d{8})$""")
        private val EMAIL_REGEX = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
        private const val QUEUED_DELAY_MS = 60_000L
    }

    @Async
    fun process(
        jobId: Long,
        csvBytes: ByteArray,
        clubId: Long,
        clubPublicId: UUID,
    ) {
        try {
            Thread.sleep(QUEUED_DELAY_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return
        }

        val job = jobRepository.findById(jobId).orElse(null) ?: return

        if (job.status == MemberImportJobStatus.CANCELLED) {
            log.info("Import job {} was cancelled during queued window", job.publicId)
            return
        }

        job.status = MemberImportJobStatus.PROCESSING
        job.startedAt = Instant.now()
        jobRepository.save(job)

        try {
            val result = executeImport(csvBytes, clubId, clubPublicId, jobId)
            job.totalRows = result.totalRows
            job.importedCount = result.importedCount
            job.skippedCount = result.skippedCount
            job.errorCount = result.errorCount
            job.errorDetail = result.errorDetail.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            log.error("Import job {} failed with unexpected exception", job.publicId, e)
            job.totalRows = 0
            job.importedCount = 0
            job.skippedCount = 0
            job.errorCount = 1
            job.errorDetail = "Import failed: ${e.message}"
        }

        job.status = MemberImportJobStatus.COMPLETED
        job.completedAt = Instant.now()
        jobRepository.save(job)

        auditService.log(
            action = AuditAction.MEMBER_IMPORT_COMPLETED,
            entityType = "MemberImportJob",
            entityId = job.publicId.toString(),
            actorId = "system",
            actorScope = "system",
            clubId = clubPublicId.toString(),
            changesJson = """{"importedCount":${job.importedCount},"skippedCount":${job.skippedCount},"errorCount":${job.errorCount}}""",
        )

        sendCompletionNotification(job)
        sendCompletionEmail(job)
    }

    @Transactional
    fun executeImport(
        csvBytes: ByteArray,
        clubId: Long,
        clubPublicId: UUID,
        jobId: Long,
    ): ImportResult {
        val club = clubRepository.findById(clubId).orElseThrow()
        val orgId = club.organizationId

        val branch =
            branchRepository.findFirstByClubIdAndDeletedAtIsNullOrderByIdAsc(clubId)
                .orElseThrow { IllegalStateException("Club has no branches") }

        val memberRole =
            roleRepository.findByScopeAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                "member",
                orgId,
                clubId,
            ).orElse(null)

        val reader = InputStreamReader(csvBytes.inputStream(), Charsets.UTF_8)
        val format =
            CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()

        var totalRows = 0
        var importedCount = 0
        var skippedCount = 0
        var errorCount = 0
        val errorLines = mutableListOf<String>()

        CSVParser(reader, format).use { parser ->
            for (record in parser) {
                totalRows++
                val rowNum = record.recordNumber + 1

                val validation = validateRow(record, rowNum)
                if (validation != null) {
                    errorCount++
                    errorLines.add(validation)
                    continue
                }

                val nameAr = record.get("name_ar").trim()
                val phone = normalisePhone(record.get("phone").trim())
                val gender = record.get("gender").trim().lowercase()

                val nameEn = getOptional(record, "name_en")
                val email = getOptional(record, "email")
                val dobStr = getOptional(record, "date_of_birth")

                val dob =
                    dobStr?.let {
                        try {
                            val parsed = LocalDate.parse(it)
                            if (parsed.isAfter(LocalDate.now())) {
                                errorCount++
                                errorLines.add("Row $rowNum: date_of_birth $it is in the future")
                                null
                            } else {
                                parsed
                            }
                        } catch (e: Exception) {
                            errorCount++
                            errorLines.add("Row $rowNum: invalid date_of_birth format '$it'")
                            null
                        }
                    }
                if (dobStr != null && dob == null) continue

                if (memberRepository.countByPhoneAndClubId(phone, clubId) > 0) {
                    skippedCount++
                    errorLines.add("Row $rowNum: phone $phone already exists")
                    continue
                }

                val (firstName, lastName) = splitName(nameAr)
                val (firstNameEn, lastNameEn) = if (nameEn != null) splitName(nameEn) else Pair(firstName, lastName)

                val userEmail = email ?: "import-${UUID.randomUUID()}@imported.liyaqa.local"
                val user =
                    userRepository.save(
                        User(
                            email = userEmail,
                            passwordHash = passwordEncoder.encode(UUID.randomUUID().toString()),
                            organizationId = orgId,
                            clubId = clubId,
                        ),
                    )

                if (memberRole != null) {
                    userRoleRepository.save(UserRole(userId = user.id, roleId = memberRole.id))
                }

                memberRepository.save(
                    Member(
                        organizationId = orgId,
                        clubId = clubId,
                        branchId = branch.id,
                        userId = user.id,
                        firstNameAr = firstName,
                        firstNameEn = firstNameEn,
                        lastNameAr = lastName,
                        lastNameEn = lastNameEn,
                        phone = phone,
                        gender = gender,
                        dateOfBirth = dob,
                        membershipStatus = "pending",
                        memberImportJobId = jobId,
                    ),
                )

                importedCount++
            }
        }

        return ImportResult(
            totalRows = totalRows,
            importedCount = importedCount,
            skippedCount = skippedCount,
            errorCount = errorCount,
            errorDetail = errorLines.joinToString("\n"),
        )
    }

    private fun validateRow(
        record: CSVRecord,
        rowNum: Long,
    ): String? {
        val nameAr = getOptional(record, "name_ar")
        if (nameAr.isNullOrBlank()) return "Row $rowNum: name_ar is required"
        if (nameAr.length < 2 || nameAr.length > 100) return "Row $rowNum: name_ar must be 2-100 characters"

        val phone = getOptional(record, "phone")
        if (phone.isNullOrBlank()) return "Row $rowNum: phone is required"
        val normalised = normalisePhone(phone.trim())
        if (!PHONE_REGEX.matches(phone.trim()) && !normalised.matches(Regex("""^\+966\s?\d{8,9}$"""))) {
            return "Row $rowNum: invalid phone format '$phone'"
        }

        val gender = getOptional(record, "gender")
        if (gender.isNullOrBlank()) return "Row $rowNum: gender is required"
        if (gender.trim().lowercase() !in listOf("male", "female")) {
            return "Row $rowNum: invalid gender value '$gender'"
        }

        val nameEn = getOptional(record, "name_en")
        if (nameEn != null && (nameEn.length < 2 || nameEn.length > 100)) {
            return "Row $rowNum: name_en must be 2-100 characters"
        }

        val email = getOptional(record, "email")
        if (email != null && !EMAIL_REGEX.matches(email)) {
            return "Row $rowNum: invalid email format '$email'"
        }

        return null
    }

    private fun normalisePhone(phone: String): String {
        val cleaned = phone.replace(Regex("""[\s\-]"""), "")
        return if (cleaned.startsWith("05") && cleaned.length == 10) {
            "+966 ${cleaned.substring(1)}"
        } else if (cleaned.startsWith("+966")) {
            "+966 ${cleaned.substring(4)}"
        } else {
            cleaned
        }
    }

    private fun splitName(fullName: String): Pair<String, String> {
        val parts = fullName.trim().split(" ", limit = 2)
        return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(parts[0], "-")
    }

    private fun getOptional(
        record: CSVRecord,
        header: String,
    ): String? {
        return try {
            val value = record.get(header)?.trim()
            if (value.isNullOrBlank()) null else value
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun sendCompletionNotification(job: MemberImportJob) {
        try {
            val paramsMap =
                mapOf(
                    "fileName" to job.fileName,
                    "importedCount" to (job.importedCount ?: 0).toString(),
                    "skippedCount" to (job.skippedCount ?: 0).toString(),
                    "errorCount" to (job.errorCount ?: 0).toString(),
                )
            notificationService.create(
                recipientUserId = job.createdByUserId,
                recipientScope = "platform",
                type = NotificationType.MEMBER_IMPORT_COMPLETED,
                paramsJson = objectMapper.writeValueAsString(paramsMap),
                entityType = "MemberImportJob",
                entityId = job.publicId.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to send import completion notification for job {}", job.publicId, e)
        }
    }

    private fun sendCompletionEmail(job: MemberImportJob) {
        try {
            val user = userRepository.findById(job.createdByUserId).orElse(null) ?: return
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, false, "UTF-8")
            helper.setFrom(fromAddress)
            helper.setTo(user.email)
            helper.setSubject("Liyaqa — Member Import Complete: ${job.fileName}")
            helper.setText(
                """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #1a1a1a;">Liyaqa — Member Import Complete</h2>
                    <p>Your CSV import <strong>${job.fileName}</strong> has finished processing.</p>
                    <ul>
                        <li>Total rows: ${job.totalRows ?: 0}</li>
                        <li>Imported: ${job.importedCount ?: 0}</li>
                        <li>Skipped (duplicates): ${job.skippedCount ?: 0}</li>
                        <li>Errors: ${job.errorCount ?: 0}</li>
                    </ul>
                    <hr style="border: none; border-top: 1px solid #e5e5e5; margin: 20px 0;">
                    <p style="font-size: 12px; color: #666;">This is an automated notification from Liyaqa.</p>
                </body>
                </html>
                """.trimIndent(),
                true,
            )
            mailSender.send(message)
            log.info("Import completion email sent to {}", user.email)
        } catch (e: Exception) {
            log.warn("Failed to send import completion email for job {}", job.publicId, e)
        }
    }

    data class ImportResult(
        val totalRows: Int,
        val importedCount: Int,
        val skippedCount: Int,
        val errorCount: Int,
        val errorDetail: String,
    )
}
