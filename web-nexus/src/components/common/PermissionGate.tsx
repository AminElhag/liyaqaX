import { useAuthStore } from '@/stores/useAuthStore'
import { hasPermission } from '@/lib/permissions'
import type { PermissionCode } from '@/types/permissions'
import type { ReactNode } from 'react'

export function PermissionGate({ permission, children }: { permission: PermissionCode; children: ReactNode }) {
  const permissions = useAuthStore((s) => s.permissions)
  if (!hasPermission(permissions, permission)) return null
  return <>{children}</>
}
