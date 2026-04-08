package com.liyaqa.role

import com.liyaqa.audit.AuditService
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.permission.Permission
import com.liyaqa.permission.PermissionRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.rbac.RolePermission
import com.liyaqa.rbac.RolePermissionRepository
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.dto.CreateRoleRequest
import com.liyaqa.role.dto.UpdateRolePermissionsRequest
import com.liyaqa.role.dto.UpdateRoleRequest
import com.liyaqa.staff.StaffMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.HttpStatus
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleManagementServiceTest {
    @Mock
    lateinit var roleRepository: RoleRepository

    @Mock
    lateinit var rolePermissionRepository: RolePermissionRepository

    @Mock
    lateinit var permissionRepository: PermissionRepository

    @Mock
    lateinit var userRoleRepository: UserRoleRepository

    @Mock
    lateinit var staffMemberRepository: StaffMemberRepository

    @Mock
    lateinit var organizationRepository: OrganizationRepository

    @Mock
    lateinit var clubRepository: ClubRepository

    @Mock
    lateinit var permissionService: PermissionService

    @Mock
    lateinit var auditService: AuditService

    @InjectMocks
    lateinit var service: RoleManagementService

    private fun role(
        name: String = "Custom Role",
        scope: String = "platform",
        isSystem: Boolean = false,
        id: Long = 1L,
    ): Role {
        val r =
            Role(
                nameAr = name,
                nameEn = name,
                scope = scope,
                isSystem = isSystem,
            )
        setEntityId(r, id)
        return r
    }

    private fun permission(
        code: String = "member:read",
        id: Long = 10L,
    ): Permission {
        val p =
            Permission(
                code = code,
                resource = code.substringBefore(':'),
                action = code.substringAfter(':'),
            )
        setEntityId(p, id)
        return p
    }

    private fun setEntityId(
        entity: Any,
        id: Long,
    ) {
        val field = entity.javaClass.superclass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }

    // ── createRole ───────────────────────────────────────────────────────────

    @Test
    fun `createRole succeeds for platform scope`() {
        whenever(roleRepository.findByNameEnAndScopeAndDeletedAtIsNull("New Role", "platform"))
            .thenReturn(Optional.empty())
        whenever(roleRepository.save(any<Role>())).thenAnswer { it.arguments[0] as Role }

        val result = service.createRole(CreateRoleRequest(name = "New Role"), scope = "platform")

        assertThat(result.nameEn).isEqualTo("New Role")
        assertThat(result.scope).isEqualTo("platform")
        assertThat(result.isSystem).isFalse()
        verify(roleRepository).save(any<Role>())
    }

    @Test
    fun `createRole returns 409 for duplicate name in same scope`() {
        val existing = role(name = "Duplicate", scope = "platform")
        whenever(roleRepository.findByNameEnAndScopeAndDeletedAtIsNull("Duplicate", "platform"))
            .thenReturn(Optional.of(existing))

        assertThatThrownBy {
            service.createRole(CreateRoleRequest(name = "Duplicate"), scope = "platform")
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── deleteRole ───────────────────────────────────────────────────────────

    @Test
    fun `deleteRole returns 409 for system role`() {
        val systemRole = role(isSystem = true)
        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(systemRole.publicId))
            .thenReturn(Optional.of(systemRole))

        assertThatThrownBy {
            service.deleteRole(systemRole.publicId)
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `deleteRole returns 409 when staff are assigned`() {
        val r = role(isSystem = false)
        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(r.publicId))
            .thenReturn(Optional.of(r))
        whenever(userRoleRepository.countByRoleId(r.id)).thenReturn(3)

        assertThatThrownBy {
            service.deleteRole(r.publicId)
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `deleteRole succeeds for clean custom role`() {
        val r = role(isSystem = false)
        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(r.publicId))
            .thenReturn(Optional.of(r))
        whenever(userRoleRepository.countByRoleId(r.id)).thenReturn(0)
        whenever(roleRepository.save(any<Role>())).thenAnswer { it.arguments[0] as Role }

        service.deleteRole(r.publicId)

        assertThat(r.deletedAt).isNotNull()
        verify(roleRepository).save(r)
    }

    // ── removePermission ─────────────────────────────────────────────────────

    @Test
    fun `removePermission returns 422 when last permission`() {
        val r = role()
        val p = permission()
        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(r.publicId))
            .thenReturn(Optional.of(r))
        whenever(permissionRepository.findByPublicId(p.publicId))
            .thenReturn(Optional.of(p))
        whenever(rolePermissionRepository.countByRoleId(r.id)).thenReturn(1)

        assertThatThrownBy {
            service.removePermission(r.publicId, p.publicId)
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `removePermission succeeds and invalidates cache`() {
        val r = role()
        val p = permission()
        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(r.publicId))
            .thenReturn(Optional.of(r))
        whenever(permissionRepository.findByPublicId(p.publicId))
            .thenReturn(Optional.of(p))
        whenever(rolePermissionRepository.countByRoleId(r.id)).thenReturn(2)
        whenever(rolePermissionRepository.findAllByRoleId(r.id)).thenReturn(emptyList())

        service.removePermission(r.publicId, p.publicId)

        verify(rolePermissionRepository).deleteByRoleIdAndPermissionId(r.id, p.id)
        verify(permissionService).invalidateCache(r.publicId)
    }

    // ── replacePermissions ───────────────────────────────────────────────────

    @Test
    fun `replacePermissions computes diff and invalidates cache once`() {
        val r = role()
        val pOld = permission(code = "member:read", id = 10L)
        val pKeep = permission(code = "member:create", id = 11L)
        val pNew = permission(code = "member:update", id = 12L)

        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(r.publicId))
            .thenReturn(Optional.of(r))
        whenever(permissionRepository.findByPublicId(pKeep.publicId))
            .thenReturn(Optional.of(pKeep))
        whenever(permissionRepository.findByPublicId(pNew.publicId))
            .thenReturn(Optional.of(pNew))

        val existingRp1 = RolePermission(roleId = r.id, permissionId = pOld.id)
        val existingRp2 = RolePermission(roleId = r.id, permissionId = pKeep.id)
        whenever(rolePermissionRepository.findAllByRoleId(r.id))
            .thenReturn(listOf(existingRp1, existingRp2))
            .thenReturn(emptyList())
        whenever(rolePermissionRepository.save(any<RolePermission>()))
            .thenAnswer { it.arguments[0] as RolePermission }

        service.replacePermissions(
            r.publicId,
            UpdateRolePermissionsRequest(permissionIds = listOf(pKeep.publicId, pNew.publicId)),
        )

        verify(rolePermissionRepository).delete(existingRp1)
        verify(rolePermissionRepository, never()).delete(existingRp2)
        verify(rolePermissionRepository).save(any<RolePermission>())
        verify(permissionService).invalidateCache(r.publicId)
    }

    // ── updateRole ───────────────────────────────────────────────────────────

    @Test
    fun `updateRole changes name`() {
        val r = role(name = "Old Name")
        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(r.publicId))
            .thenReturn(Optional.of(r))
        whenever(roleRepository.findByNameEnAndScopeAndDeletedAtIsNull("New Name", "platform"))
            .thenReturn(Optional.empty())
        whenever(roleRepository.save(any<Role>())).thenAnswer { it.arguments[0] as Role }
        whenever(userRoleRepository.countByRoleId(r.id)).thenReturn(0)
        whenever(rolePermissionRepository.findAllByRoleId(r.id)).thenReturn(emptyList())

        val result = service.updateRole(r.publicId, UpdateRoleRequest(name = "New Name"))

        assertThat(result.nameEn).isEqualTo("New Name")
    }

    // ── addPermission ────────────────────────────────────────────────────────

    @Test
    fun `addPermission adds and invalidates cache`() {
        val r = role()
        val p = permission()
        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(r.publicId))
            .thenReturn(Optional.of(r))
        whenever(permissionRepository.findByPublicId(p.publicId))
            .thenReturn(Optional.of(p))
        whenever(rolePermissionRepository.findByRoleIdAndPermissionId(r.id, p.id))
            .thenReturn(null)
        whenever(rolePermissionRepository.save(any<RolePermission>()))
            .thenAnswer { it.arguments[0] as RolePermission }
        whenever(rolePermissionRepository.findAllByRoleId(r.id)).thenReturn(emptyList())

        service.addPermission(r.publicId, p.publicId)

        verify(rolePermissionRepository).save(any<RolePermission>())
        verify(permissionService).invalidateCache(r.publicId)
    }
}
