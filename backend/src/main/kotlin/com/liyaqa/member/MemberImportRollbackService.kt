package com.liyaqa.member

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MemberImportRollbackService(
    private val jobRepository: MemberImportJobRepository,
    private val memberRepository: MemberRepository,
    private val auditService: AuditService,
) {
    @Transactional
    fun rollback(
        jobPublicId: UUID,
        actorPublicId: String,
        actorScope: String,
    ): Int {
        val job =
            jobRepository.findByPublicId(jobPublicId)
                ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Import job not found.")

        if (job.status != MemberImportJobStatus.COMPLETED) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Only completed jobs can be rolled back (current: ${job.status}).",
            )
        }

        val deletedCount = memberRepository.softDeleteByImportJobId(job.id)
        job.status = MemberImportJobStatus.ROLLED_BACK
        jobRepository.save(job)

        auditService.log(
            action = AuditAction.MEMBER_IMPORT_ROLLED_BACK,
            entityType = "MemberImportJob",
            entityId = job.publicId.toString(),
            actorId = actorPublicId,
            actorScope = actorScope,
            changesJson = """{"deletedCount":$deletedCount}""",
        )

        return deletedCount
    }
}
