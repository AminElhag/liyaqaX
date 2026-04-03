package com.liyaqa.rbac

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.common.exception.ArenaException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class PermissionService(
    private val rolePermissionRepository: RolePermissionRepository,
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        const val CACHE_TTL_SECONDS = 300L

        fun cacheKey(roleId: UUID) = "role_permissions:$roleId"
    }

    private val permissionSetType = object : TypeReference<Set<String>>() {}

    fun getPermissions(roleId: UUID): Set<String> {
        val key = cacheKey(roleId)
        val cached = stringRedisTemplate.opsForValue().get(key)
        if (cached != null) {
            return objectMapper.readValue(cached, permissionSetType)
        }
        val permissions =
            rolePermissionRepository
                .findPermissionCodesByRolePublicId(roleId)
                .toSet()
        stringRedisTemplate.opsForValue().set(
            key,
            objectMapper.writeValueAsString(permissions),
            Duration.ofSeconds(CACHE_TTL_SECONDS),
        )
        return permissions
    }

    fun hasPermission(
        roleId: UUID,
        permissionCode: String,
    ): Boolean = getPermissions(roleId).contains(permissionCode)

    fun requirePermission(
        roleId: UUID,
        permissionCode: String,
    ) {
        if (!hasPermission(roleId, permissionCode)) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "https://arena.app/errors/forbidden",
                "Permission denied: $permissionCode",
            )
        }
    }

    fun invalidateCache(roleId: UUID) {
        stringRedisTemplate.delete(cacheKey(roleId))
    }
}
