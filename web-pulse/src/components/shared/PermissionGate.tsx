import type { ReactNode } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { hasPermission } from '@/lib/permissions'
import type { PermissionCode } from '@/types/permissions'

interface PermissionGateProps {
  permission: PermissionCode
  fallback?: ReactNode
  children: ReactNode
}

/**
 * Removes children from DOM if the current user lacks the required permission.
 * Never uses CSS display:none — unauthorized elements are not rendered at all.
 */
export function PermissionGate({
  permission,
  fallback = null,
  children,
}: PermissionGateProps) {
  const permissions = useAuthStore((s) => s.permissions)

  if (!hasPermission(permissions, permission)) {
    return <>{fallback}</>
  }

  return <>{children}</>
}
