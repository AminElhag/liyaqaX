/**
 * Permission constants -- mirrors backend PermissionConstants.kt
 * Only includes permissions relevant to web-nexus (platform scope).
 */
export const Permission = {
  ORGANIZATION_CREATE: 'organization:create',
  ORGANIZATION_READ: 'organization:read',
  ORGANIZATION_UPDATE: 'organization:update',
  CLUB_CREATE: 'club:create',
  CLUB_READ: 'club:read',
  CLUB_UPDATE: 'club:update',
  BRANCH_CREATE: 'branch:create',
  BRANCH_READ: 'branch:read',
  BRANCH_UPDATE: 'branch:update',
  MEMBER_READ: 'member:read',
  PLATFORM_STATS_VIEW: 'platform:stats:view',
  AUDIT_READ: 'audit:read',
  ROLE_CREATE: 'role:create',
  ROLE_READ: 'role:read',
  ROLE_UPDATE: 'role:update',
  ROLE_DELETE: 'role:delete',
  ZATCA_ONBOARD: 'zatca:onboard',
  ZATCA_READ: 'zatca:read',
  ZATCA_RETRY: 'zatca:retry',
  MEMBER_IMPORT: 'member:import',
  SUBSCRIPTION_MANAGE: 'subscription:manage',
  SUBSCRIPTION_READ: 'subscription:read',
} as const

export type PermissionCode = (typeof Permission)[keyof typeof Permission]

/**
 * Allowed scopes for web-nexus login.
 * Only "platform" scope users can access this app.
 */
export const ALLOWED_SCOPES = ['platform'] as const
export type AllowedScope = (typeof ALLOWED_SCOPES)[number]
