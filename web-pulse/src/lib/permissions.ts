import type { PermissionCode } from '@/types/permissions'

/**
 * Check if a set of user permissions includes the required permission.
 * Permissions are fetched from GET /api/v1/auth/me and stored in useAuthStore.
 */
export function hasPermission(
  userPermissions: Set<string>,
  required: PermissionCode,
): boolean {
  return userPermissions.has(required)
}

/**
 * Check if a set of user permissions includes ALL of the required permissions.
 */
export function hasAllPermissions(
  userPermissions: Set<string>,
  required: PermissionCode[],
): boolean {
  return required.every((p) => userPermissions.has(p))
}

/**
 * Check if a set of user permissions includes ANY of the required permissions.
 */
export function hasAnyPermission(
  userPermissions: Set<string>,
  required: PermissionCode[],
): boolean {
  return required.some((p) => userPermissions.has(p))
}
