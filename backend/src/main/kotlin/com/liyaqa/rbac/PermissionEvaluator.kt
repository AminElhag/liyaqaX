package com.liyaqa.rbac

import com.liyaqa.security.JwtClaims
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.io.Serializable
import org.springframework.security.access.PermissionEvaluator as SpringPermissionEvaluator

@Component
class PermissionEvaluator(
    private val permissionService: PermissionService,
) : SpringPermissionEvaluator {
    // Called by hasPermission(null, 'staff:create') in @PreAuthorize
    override fun hasPermission(
        authentication: Authentication,
        targetDomainObject: Any?,
        permission: Any,
    ): Boolean {
        val roleId = (authentication.details as? JwtClaims)?.roleId ?: return false
        val permissionCode = permission as? String ?: return false
        return permissionService.hasPermission(roleId, permissionCode)
    }

    // Called by hasPermission(targetId, 'Type', 'action') — not used in this project
    override fun hasPermission(
        authentication: Authentication,
        targetId: Serializable?,
        targetType: String?,
        permission: Any,
    ): Boolean = false
}
