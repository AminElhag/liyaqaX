/**
 * Permission constants — mirrors backend PermissionConstants.kt
 * Only includes permissions relevant to web-pulse (club scope).
 */
export const Permission = {
  // Staff
  STAFF_CREATE: 'staff:create',
  STAFF_READ: 'staff:read',
  STAFF_UPDATE: 'staff:update',
  STAFF_DELETE: 'staff:delete',

  // Branch
  BRANCH_CREATE: 'branch:create',
  BRANCH_READ: 'branch:read',
  BRANCH_UPDATE: 'branch:update',
  BRANCH_DELETE: 'branch:delete',

  // Member
  MEMBER_CREATE: 'member:create',
  MEMBER_READ: 'member:read',
  MEMBER_UPDATE: 'member:update',
  MEMBER_DELETE: 'member:delete',

  // Membership Plan
  MEMBERSHIP_PLAN_CREATE: 'membership-plan:create',
  MEMBERSHIP_PLAN_READ: 'membership-plan:read',
  MEMBERSHIP_PLAN_UPDATE: 'membership-plan:update',
  MEMBERSHIP_PLAN_DELETE: 'membership-plan:delete',

  // Membership
  MEMBERSHIP_CREATE: 'membership:create',
  MEMBERSHIP_READ: 'membership:read',
  MEMBERSHIP_UPDATE: 'membership:update',
  MEMBERSHIP_FREEZE: 'membership:freeze',
  MEMBERSHIP_UNFREEZE: 'membership:unfreeze',
  MEMBERSHIP_TRANSFER: 'membership:transfer',

  // Payment
  PAYMENT_COLLECT: 'payment:collect',
  PAYMENT_READ: 'payment:read',
  PAYMENT_REFUND: 'payment:refund',

  // Invoice
  INVOICE_READ: 'invoice:read',
  INVOICE_GENERATE: 'invoice:generate',

  // Reports
  REPORT_REVENUE_VIEW: 'report:revenue:view',
  REPORT_RETENTION_VIEW: 'report:retention:view',
  REPORT_UTILIZATION_VIEW: 'report:utilization:view',
  REPORT_LEADS_VIEW: 'report:leads:view',
  REPORT_CASH_DRAWER_VIEW: 'report:cash-drawer:view',

  // PT
  PT_PACKAGE_CREATE: 'pt-package:create',
  PT_PACKAGE_READ: 'pt-package:read',
  PT_SESSION_CREATE: 'pt-session:create',
  PT_SESSION_READ: 'pt-session:read',
  PT_SESSION_UPDATE: 'pt-session:update',
  PT_SESSION_MARK_ATTENDANCE: 'pt-session:mark-attendance',

  // GX
  GX_CLASS_CREATE: 'gx-class:create',
  GX_CLASS_READ: 'gx-class:read',
  GX_CLASS_UPDATE: 'gx-class:update',
  GX_CLASS_MANAGE_BOOKINGS: 'gx-class:manage-bookings',
  GX_CLASS_MARK_ATTENDANCE: 'gx-class:mark-attendance',

  // Lead
  LEAD_CREATE: 'lead:create',
  LEAD_READ: 'lead:read',
  LEAD_UPDATE: 'lead:update',
  LEAD_CONVERT: 'lead:convert',
  LEAD_DELETE: 'lead:delete',
  LEAD_ASSIGN: 'lead:assign',

  // Lead Source
  LEAD_SOURCE_CREATE: 'lead-source:create',
  LEAD_SOURCE_READ: 'lead-source:read',
  LEAD_SOURCE_UPDATE: 'lead-source:update',

  // Cash drawer
  CASH_DRAWER_OPEN: 'cash-drawer:open',
  CASH_DRAWER_CLOSE: 'cash-drawer:close',
  CASH_DRAWER_READ: 'cash-drawer:read',
  CASH_DRAWER_ENTRY_CREATE: 'cash-drawer:entry:create',
  CASH_DRAWER_RECONCILE: 'cash-drawer:reconcile',

  // Role management
  ROLE_CREATE: 'role:create',
  ROLE_READ: 'role:read',
  ROLE_UPDATE: 'role:update',
  ROLE_DELETE: 'role:delete',
} as const

export type PermissionCode = (typeof Permission)[keyof typeof Permission]

/**
 * Allowed scopes for web-pulse login.
 * Only "club" scope users can access this app.
 */
export const ALLOWED_SCOPES = ['club'] as const
export type AllowedScope = (typeof ALLOWED_SCOPES)[number]
