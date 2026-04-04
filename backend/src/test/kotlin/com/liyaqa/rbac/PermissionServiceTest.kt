package com.liyaqa.rbac

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionServiceTest {
    @Mock
    lateinit var rolePermissionRepository: RolePermissionRepository

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    lateinit var valueOps: ValueOperations<String, String>

    private val objectMapper = ObjectMapper()
    private lateinit var permissionService: PermissionService

    private val roleId: UUID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
    private val cacheKey = "role_permissions:$roleId"

    @BeforeEach
    fun setup() {
        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOps)
        permissionService = PermissionService(rolePermissionRepository, stringRedisTemplate, objectMapper)
    }

    @Test
    fun `getPermissions fetches from DB and caches when cache misses`() {
        whenever(valueOps.get(cacheKey)).thenReturn(null)
        whenever(rolePermissionRepository.findPermissionCodesByRolePublicId(roleId))
            .thenReturn(listOf("organization:read", "organization:create"))

        val result = permissionService.getPermissions(roleId)

        assertThat(result).containsExactlyInAnyOrder("organization:read", "organization:create")
        verify(rolePermissionRepository).findPermissionCodesByRolePublicId(roleId)
        verify(valueOps).set(eq(cacheKey), any(), eq(Duration.ofSeconds(PermissionService.CACHE_TTL_SECONDS)))
    }

    @Test
    fun `getPermissions returns cached value without hitting DB`() {
        val cached = objectMapper.writeValueAsString(setOf("organization:read"))
        whenever(valueOps.get(cacheKey)).thenReturn(cached)

        val result = permissionService.getPermissions(roleId)

        assertThat(result).containsExactly("organization:read")
        verify(rolePermissionRepository, never()).findPermissionCodesByRolePublicId(any())
    }

    @Test
    fun `hasPermission returns true when permission is in the set`() {
        whenever(valueOps.get(cacheKey)).thenReturn(null)
        whenever(rolePermissionRepository.findPermissionCodesByRolePublicId(roleId))
            .thenReturn(listOf("organization:read"))

        assertThat(permissionService.hasPermission(roleId, "organization:read")).isTrue()
    }

    @Test
    fun `hasPermission returns false when permission is not in the set`() {
        whenever(valueOps.get(cacheKey)).thenReturn(null)
        whenever(rolePermissionRepository.findPermissionCodesByRolePublicId(roleId))
            .thenReturn(listOf("organization:read"))

        assertThat(permissionService.hasPermission(roleId, "organization:delete")).isFalse()
    }

    @Test
    fun `invalidateCache deletes the Redis key`() {
        permissionService.invalidateCache(roleId)

        verify(stringRedisTemplate).delete(cacheKey)
    }
}
