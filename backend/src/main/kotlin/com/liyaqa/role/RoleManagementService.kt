package com.liyaqa.role

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.permission.Permission
import com.liyaqa.permission.PermissionRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.rbac.RolePermission
import com.liyaqa.rbac.RolePermissionRepository
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.dto.CreateRoleRequest
import com.liyaqa.role.dto.PermissionResponse
import com.liyaqa.role.dto.RoleDetailResponse
import com.liyaqa.role.dto.RoleListItemResponse
import com.liyaqa.role.dto.UpdateRolePermissionsRequest
import com.liyaqa.role.dto.UpdateRoleRequest
import com.liyaqa.staff.StaffMemberRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class RoleManagementService(
    private val roleRepository: RoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val permissionRepository: PermissionRepository,
    private val userRoleRepository: UserRoleRepository,
    private val staffMemberRepository: StaffMemberRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val permissionService: PermissionService,
    private val auditService: AuditService,
) {
    // ── List roles ───────────────────────────────────────────────────────────

    fun listPlatformRoles(): List<RoleListItemResponse> {
        val roles = roleRepository.findAllByScopeAndDeletedAtIsNull("platform")
        return roles.map { toListItem(it) }
    }

    fun listClubRoles(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ): List<RoleListItemResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val roles = roleRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(org.id, club.id)
        return roles.map { toListItem(it) }
    }

    // ── Get role detail ──────────────────────────────────────────────────────

    fun getRoleDetail(rolePublicId: UUID): RoleDetailResponse {
        val role = findRoleOrThrow(rolePublicId)
        val permissions = loadPermissions(role.id)
        val staffCount = userRoleRepository.countByRoleId(role.id)
        return RoleDetailResponse(
            id = role.publicId,
            nameAr = role.nameAr,
            nameEn = role.nameEn,
            description = null,
            scope = role.scope,
            isSystem = role.isSystem,
            permissions = permissions,
            staffCount = staffCount,
        )
    }

    // ── Create role ──────────────────────────────────────────────────────────

    @Transactional
    fun createRole(
        request: CreateRoleRequest,
        scope: String,
        orgPublicId: UUID? = null,
        clubPublicId: UUID? = null,
    ): RoleDetailResponse {
        val organizationId =
            orgPublicId?.let { findOrgOrThrow(it).id }
        val clubId =
            if (orgPublicId != null && clubPublicId != null) {
                findClubOrThrow(clubPublicId, organizationId!!).id
            } else {
                null
            }

        checkNameUniqueness(request.name, scope, clubId)

        val role =
            roleRepository.save(
                Role(
                    nameAr = request.name,
                    nameEn = request.name,
                    scope = scope,
                    organizationId = organizationId,
                    clubId = clubId,
                    isSystem = false,
                ),
            )

        auditService.logFromContext(
            action = AuditAction.ROLE_CREATED,
            entityType = "Role",
            entityId = role.publicId.toString(),
            changesJson = """{"name":"${request.name}","scope":"$scope"}""",
        )

        return RoleDetailResponse(
            id = role.publicId,
            nameAr = role.nameAr,
            nameEn = role.nameEn,
            description = null,
            scope = role.scope,
            isSystem = role.isSystem,
            permissions = emptyList(),
            staffCount = 0,
        )
    }

    // ── Update role ──────────────────────────────────────────────────────────

    @Transactional
    fun updateRole(
        rolePublicId: UUID,
        request: UpdateRoleRequest,
    ): RoleDetailResponse {
        val role = findRoleOrThrow(rolePublicId)

        if (request.name != null) {
            checkNameUniqueness(request.name, role.scope, role.clubId, excludeRoleId = role.id)
            role.nameAr = request.name
            role.nameEn = request.name
        }

        roleRepository.save(role)

        auditService.logFromContext(
            action = AuditAction.ROLE_UPDATED,
            entityType = "Role",
            entityId = role.publicId.toString(),
            changesJson = """{"name":"${request.name}"}""",
        )

        val permissions = loadPermissions(role.id)
        val staffCount = userRoleRepository.countByRoleId(role.id)
        return RoleDetailResponse(
            id = role.publicId,
            nameAr = role.nameAr,
            nameEn = role.nameEn,
            description = null,
            scope = role.scope,
            isSystem = role.isSystem,
            permissions = permissions,
            staffCount = staffCount,
        )
    }

    // ── Delete role ──────────────────────────────────────────────────────────

    @Transactional
    fun deleteRole(rolePublicId: UUID) {
        val role = findRoleOrThrow(rolePublicId)

        // Rule 3: cannot delete system roles
        if (role.isSystem) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "System roles cannot be deleted.",
            )
        }

        // Rule 2: cannot delete role with active staff
        val staffCount = userRoleRepository.countByRoleId(role.id)
        if (staffCount > 0) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Cannot delete role: $staffCount staff members are currently assigned to this role.",
            )
        }

        role.softDelete()
        roleRepository.save(role)

        auditService.logFromContext(
            action = AuditAction.ROLE_DELETED,
            entityType = "Role",
            entityId = role.publicId.toString(),
        )
    }

    // ── Permission management ────────────────────────────────────────────────

    fun getPermissionsForRole(rolePublicId: UUID): List<PermissionResponse> {
        val role = findRoleOrThrow(rolePublicId)
        return loadPermissions(role.id)
    }

    @Transactional
    fun addPermission(
        rolePublicId: UUID,
        permissionPublicId: UUID,
    ): List<PermissionResponse> {
        val role = findRoleOrThrow(rolePublicId)
        val permission = findPermissionOrThrow(permissionPublicId)

        val existing = rolePermissionRepository.findByRoleIdAndPermissionId(role.id, permission.id)
        if (existing == null) {
            rolePermissionRepository.save(RolePermission(roleId = role.id, permissionId = permission.id))

            auditService.logFromContext(
                action = AuditAction.ROLE_PERMISSION_ADDED,
                entityType = "RolePermission",
                entityId = role.publicId.toString(),
                changesJson = """{"permissionCode":"${permission.code}"}""",
            )
        }

        // Rule 6: invalidate Redis cache
        permissionService.invalidateCache(role.publicId)

        return loadPermissions(role.id)
    }

    @Transactional
    fun removePermission(
        rolePublicId: UUID,
        permissionPublicId: UUID,
    ): List<PermissionResponse> {
        val role = findRoleOrThrow(rolePublicId)
        val permission = findPermissionOrThrow(permissionPublicId)

        // Rule 4: cannot remove last permission
        val currentCount = rolePermissionRepository.countByRoleId(role.id)
        if (currentCount <= 1) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "A role must have at least one permission.",
            )
        }

        rolePermissionRepository.deleteByRoleIdAndPermissionId(role.id, permission.id)

        auditService.logFromContext(
            action = AuditAction.ROLE_PERMISSION_REMOVED,
            entityType = "RolePermission",
            entityId = role.publicId.toString(),
            changesJson = """{"permissionCode":"${permission.code}"}""",
        )

        // Rule 6: invalidate Redis cache
        permissionService.invalidateCache(role.publicId)

        return loadPermissions(role.id)
    }

    @Transactional
    fun replacePermissions(
        rolePublicId: UUID,
        request: UpdateRolePermissionsRequest,
    ): List<PermissionResponse> {
        val role = findRoleOrThrow(rolePublicId)

        val newPermissions =
            request.permissionIds.map { pid ->
                findPermissionOrThrow(pid)
            }

        if (newPermissions.isEmpty()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "A role must have at least one permission.",
            )
        }

        val currentRps = rolePermissionRepository.findAllByRoleId(role.id)
        val currentPermIds = currentRps.map { it.permissionId }.toSet()
        val newPermIds = newPermissions.map { it.id }.toSet()

        // Remove permissions no longer in the set
        val toRemove = currentRps.filter { it.permissionId !in newPermIds }
        for (rp in toRemove) {
            rolePermissionRepository.delete(rp)
        }

        // Add new permissions not in the current set
        val toAdd = newPermIds - currentPermIds
        for (permId in toAdd) {
            rolePermissionRepository.save(RolePermission(roleId = role.id, permissionId = permId))
        }

        if (toRemove.isNotEmpty() || toAdd.isNotEmpty()) {
            auditService.logFromContext(
                action = AuditAction.ROLE_PERMISSION_ADDED,
                entityType = "RolePermission",
                entityId = role.publicId.toString(),
                changesJson = """{"added":${toAdd.size},"removed":${toRemove.size}}""",
            )
        }

        // Rule 6: invalidate Redis cache once
        permissionService.invalidateCache(role.publicId)

        return loadPermissions(role.id)
    }

    // ── Staff role assignment ────────────────────────────────────────────────

    @Transactional
    fun assignStaffRole(
        staffPublicId: UUID,
        newRolePublicId: UUID,
        callerOrgPublicId: UUID,
        callerClubPublicId: UUID,
    ) {
        val org = findOrgOrThrow(callerOrgPublicId)
        val club = findClubOrThrow(callerClubPublicId, org.id)

        val staff =
            staffMemberRepository
                .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                    staffPublicId,
                    org.id,
                    club.id,
                ).orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Staff member not found.")
                }

        val newRole = findRoleOrThrow(newRolePublicId)

        // Rule 8: club role must belong to same club
        if (newRole.clubId != club.id) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Role does not belong to this club.",
            )
        }

        val oldRole = roleRepository.findById(staff.roleId).orElse(null)
        val oldRolePublicId = oldRole?.publicId

        // Update role on staff member
        staff.roleId = newRole.id
        staffMemberRepository.save(staff)

        // Update UserRole assignment
        val existingUserRole = userRoleRepository.findByUserId(staff.userId).orElse(null)
        if (existingUserRole != null) {
            userRoleRepository.delete(existingUserRole)
        }
        userRoleRepository.save(UserRole(userId = staff.userId, roleId = newRole.id))

        auditService.logFromContext(
            action = AuditAction.STAFF_ROLE_ASSIGNED,
            entityType = "StaffMember",
            entityId = staff.publicId.toString(),
            changesJson = """{"oldRoleId":"$oldRolePublicId","newRoleId":"${newRole.publicId}"}""",
        )

        // Rule 7: invalidate BOTH old and new role caches
        if (oldRolePublicId != null) {
            permissionService.invalidateCache(oldRolePublicId)
        }
        permissionService.invalidateCache(newRole.publicId)
    }

    // ── List all permissions (read-only, for checkbox UI) ────────────────────

    fun listAllPermissions(): List<PermissionResponse> = permissionRepository.findAll().map { toPermissionResponse(it) }

    // ── Scope enforcement ────────────────────────────────────────────────────

    fun requirePlatformScope(role: Role) {
        if (role.scope != "platform") {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "This endpoint only manages platform roles.",
            )
        }
    }

    fun requireClubScope(
        role: Role,
        callerClubId: Long,
    ) {
        if (role.scope != "club") {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "This endpoint only manages club roles.",
            )
        }
        if (role.clubId != callerClubId) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Role does not belong to this club.",
            )
        }
    }

    fun findRoleOrThrow(publicId: UUID): Role =
        roleRepository.findByPublicIdAndDeletedAtIsNull(publicId).orElseThrow {
            ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Role not found.")
        }

    fun resolveClubInternalId(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ): Long {
        val org = findOrgOrThrow(orgPublicId)
        return findClubOrThrow(clubPublicId, org.id).id
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun findOrgOrThrow(orgPublicId: UUID) =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId).orElseThrow {
            ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.")
        }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ) = clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId).orElseThrow {
        ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
    }

    private fun findPermissionOrThrow(publicId: UUID): Permission =
        permissionRepository.findByPublicId(publicId).orElseThrow {
            ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Permission not found.")
        }

    private fun loadPermissions(roleId: Long): List<PermissionResponse> {
        val rps = rolePermissionRepository.findAllByRoleId(roleId)
        val permIds = rps.map { it.permissionId }
        if (permIds.isEmpty()) return emptyList()
        val permissions = permissionRepository.findAllById(permIds)
        return permissions.map { toPermissionResponse(it) }
    }

    private fun toListItem(role: Role): RoleListItemResponse {
        val permCount = rolePermissionRepository.countByRoleId(role.id)
        val staffCount = userRoleRepository.countByRoleId(role.id)
        return RoleListItemResponse(
            id = role.publicId,
            nameAr = role.nameAr,
            nameEn = role.nameEn,
            description = null,
            scope = role.scope,
            isSystem = role.isSystem,
            permissionCount = permCount,
            staffCount = staffCount,
        )
    }

    private fun toPermissionResponse(p: Permission) =
        PermissionResponse(
            id = p.publicId,
            code = p.code,
            description = p.descriptionEn,
        )

    private fun checkNameUniqueness(
        name: String,
        scope: String,
        clubId: Long?,
        excludeRoleId: Long? = null,
    ) {
        val existing =
            if (scope == "platform") {
                roleRepository.findByNameEnAndScopeAndDeletedAtIsNull(name, scope)
            } else {
                roleRepository.findByNameEnAndScopeAndClubIdAndDeletedAtIsNull(name, scope, clubId!!)
            }

        if (existing.isPresent && (excludeRoleId == null || existing.get().id != excludeRoleId)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A role with this name already exists.",
            )
        }
    }
}
