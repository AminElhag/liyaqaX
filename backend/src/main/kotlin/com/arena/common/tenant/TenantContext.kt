package com.arena.common.tenant

import java.util.UUID

data class TenantScope(
    val userId: UUID,
    val role: String,
    val organizationId: UUID? = null,
    val clubId: UUID? = null,
    val branchIds: List<UUID> = emptyList(),
    val memberId: UUID? = null,
)

object TenantContext {
    private val current = ThreadLocal<TenantScope>()

    fun set(scope: TenantScope) {
        current.set(scope)
    }

    fun get(): TenantScope = current.get() ?: throw IllegalStateException("TenantContext is not set")

    fun getOrNull(): TenantScope? = current.get()

    fun clear() {
        current.remove()
    }
}
