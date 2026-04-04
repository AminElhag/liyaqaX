package com.liyaqa.rbac

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface RolePermissionRepository : JpaRepository<RolePermission, Long> {
    fun findAllByRoleId(roleId: Long): List<RolePermission>

    @Transactional
    fun deleteAllByRoleId(roleId: Long)

    @Query(
        value = """
            SELECT p.code
            FROM permissions p
            INNER JOIN role_permissions rp ON p.id = rp.permission_id
            INNER JOIN roles r ON rp.role_id = r.id
            WHERE r.public_id = :rolePublicId
              AND r.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findPermissionCodesByRolePublicId(
        @Param("rolePublicId") rolePublicId: UUID,
    ): List<String>
}
