package com.liyaqa.audit

import com.liyaqa.security.JwtClaims
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository,
) {
    private val log = LoggerFactory.getLogger(AuditService::class.java)

    companion object {
        private const val MAX_CHANGES_LENGTH = 4000
        private const val TRUNCATION_SUFFIX = "...(truncated)"
    }

    fun log(
        action: AuditAction,
        entityType: String,
        entityId: String,
        actorId: String,
        actorScope: String,
        organizationId: String? = null,
        clubId: String? = null,
        changesJson: String? = null,
        ipAddress: String? = null,
    ) {
        try {
            val truncatedChanges = truncateChanges(changesJson)

            auditLogRepository.save(
                AuditLog(
                    actorId = actorId,
                    actorScope = actorScope,
                    action = action.code,
                    entityType = entityType,
                    entityId = entityId,
                    organizationId = organizationId,
                    clubId = clubId,
                    changesJson = truncatedChanges,
                    ipAddress = ipAddress,
                ),
            )
        } catch (e: Exception) {
            log.warn("Failed to write audit log: action={}, entityType={}, entityId={}", action, entityType, entityId, e)
        }
    }

    fun logFromContext(
        action: AuditAction,
        entityType: String,
        entityId: String,
        changesJson: String? = null,
    ) {
        val ctx = extractContext()
        log(
            action = action,
            entityType = entityType,
            entityId = entityId,
            actorId = ctx.actorId,
            actorScope = ctx.actorScope,
            organizationId = ctx.organizationId,
            clubId = ctx.clubId,
            changesJson = changesJson,
        )
    }

    private fun extractContext(): AuditContext {
        val auth = SecurityContextHolder.getContext().authentication
        val claims = auth?.details as? JwtClaims
        return AuditContext(
            actorId = claims?.userPublicId?.toString() ?: auth?.name ?: "system",
            actorScope = claims?.scope ?: "system",
            organizationId = claims?.organizationId?.toString(),
            clubId = claims?.clubId?.toString(),
        )
    }

    private fun truncateChanges(changesJson: String?): String? {
        if (changesJson == null) return null
        if (changesJson.length <= MAX_CHANGES_LENGTH) return changesJson
        return changesJson.substring(0, MAX_CHANGES_LENGTH - TRUNCATION_SUFFIX.length) + TRUNCATION_SUFFIX
    }

    private data class AuditContext(
        val actorId: String,
        val actorScope: String,
        val organizationId: String?,
        val clubId: String?,
    )
}
