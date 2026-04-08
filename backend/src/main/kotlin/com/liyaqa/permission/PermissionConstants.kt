package com.liyaqa.permission

object PermissionConstants {
    // ── Organization ─────────────────────────────────────────────────────────
    const val ORGANIZATION_CREATE = "organization:create"
    const val ORGANIZATION_READ = "organization:read"
    const val ORGANIZATION_UPDATE = "organization:update"
    const val ORGANIZATION_DELETE = "organization:delete"

    // ── Club ──────────────────────────────────────────────────────────────────
    const val CLUB_CREATE = "club:create"
    const val CLUB_READ = "club:read"
    const val CLUB_UPDATE = "club:update"
    const val CLUB_DELETE = "club:delete"

    // ── Branch ────────────────────────────────────────────────────────────────
    const val BRANCH_CREATE = "branch:create"
    const val BRANCH_READ = "branch:read"
    const val BRANCH_UPDATE = "branch:update"
    const val BRANCH_DELETE = "branch:delete"

    // ── Staff ─────────────────────────────────────────────────────────────────
    const val STAFF_CREATE = "staff:create"
    const val STAFF_READ = "staff:read"
    const val STAFF_UPDATE = "staff:update"
    const val STAFF_DELETE = "staff:delete"

    // ── Role ──────────────────────────────────────────────────────────────────
    const val ROLE_CREATE = "role:create"
    const val ROLE_READ = "role:read"
    const val ROLE_UPDATE = "role:update"
    const val ROLE_DELETE = "role:delete"

    // ── Integration ───────────────────────────────────────────────────────────
    const val INTEGRATION_CONFIGURE = "integration:configure"
    const val INTEGRATION_READ = "integration:read"

    // ── System ────────────────────────────────────────────────────────────────
    const val SYSTEM_IMPERSONATE = "system:impersonate"

    // ── Audit ─────────────────────────────────────────────────────────────────
    const val AUDIT_READ = "audit:read"

    // ── Member ────────────────────────────────────────────────────────────────
    const val MEMBER_CREATE = "member:create"
    const val MEMBER_READ = "member:read"
    const val MEMBER_UPDATE = "member:update"
    const val MEMBER_DELETE = "member:delete"
    const val MEMBER_IMPORT = "member:import"

    // ── Membership Plan ──────────────────────────────────────────────────────
    const val MEMBERSHIP_PLAN_CREATE = "membership-plan:create"
    const val MEMBERSHIP_PLAN_READ = "membership-plan:read"
    const val MEMBERSHIP_PLAN_UPDATE = "membership-plan:update"
    const val MEMBERSHIP_PLAN_DELETE = "membership-plan:delete"

    // ── Membership ────────────────────────────────────────────────────────────
    const val MEMBERSHIP_CREATE = "membership:create"
    const val MEMBERSHIP_READ = "membership:read"
    const val MEMBERSHIP_UPDATE = "membership:update"
    const val MEMBERSHIP_FREEZE = "membership:freeze"
    const val MEMBERSHIP_UNFREEZE = "membership:unfreeze"
    const val MEMBERSHIP_TRANSFER = "membership:transfer"
    const val MEMBERSHIP_FREEZE_REQUEST = "membership:freeze-request"

    // ── Payment ───────────────────────────────────────────────────────────────
    const val PAYMENT_COLLECT = "payment:collect"
    const val PAYMENT_READ = "payment:read"
    const val PAYMENT_REFUND = "payment:refund"
    const val PAYMENT_MAKE = "payment:make"

    // ── Invoice ───────────────────────────────────────────────────────────────
    const val INVOICE_READ = "invoice:read"
    const val INVOICE_GENERATE = "invoice:generate"

    // ── Reports ───────────────────────────────────────────────────────────────
    const val REPORT_REVENUE_VIEW = "report:revenue:view"
    const val REPORT_RETENTION_VIEW = "report:retention:view"
    const val REPORT_UTILIZATION_VIEW = "report:utilization:view"
    const val REPORT_LEADS_VIEW = "report:leads:view"
    const val REPORT_CASH_DRAWER_VIEW = "report:cash-drawer:view"
    const val REPORT_CUSTOM_RUN = "report:custom:run"

    // ── PT Package ────────────────────────────────────────────────────────────
    const val PT_PACKAGE_CREATE = "pt-package:create"
    const val PT_PACKAGE_READ = "pt-package:read"

    // ── PT Session ────────────────────────────────────────────────────────────
    const val PT_SESSION_CREATE = "pt-session:create"
    const val PT_SESSION_READ = "pt-session:read"
    const val PT_SESSION_UPDATE = "pt-session:update"
    const val PT_SESSION_MARK_ATTENDANCE = "pt-session:mark-attendance"
    const val PT_SESSION_RESCHEDULE_REQUEST = "pt-session:reschedule-request"

    // ── GX Class ──────────────────────────────────────────────────────────────
    const val GX_CLASS_CREATE = "gx-class:create"
    const val GX_CLASS_READ = "gx-class:read"
    const val GX_CLASS_UPDATE = "gx-class:update"
    const val GX_CLASS_MANAGE_BOOKINGS = "gx-class:manage-bookings"
    const val GX_CLASS_MARK_ATTENDANCE = "gx-class:mark-attendance"
    const val GX_CLASS_BOOK = "gx-class:book"
    const val GX_CLASS_CANCEL_BOOKING = "gx-class:cancel-booking"

    // ── Lead ──────────────────────────────────────────────────────────────────
    const val LEAD_CREATE = "lead:create"
    const val LEAD_READ = "lead:read"
    const val LEAD_UPDATE = "lead:update"
    const val LEAD_CONVERT = "lead:convert"
    const val LEAD_DELETE = "lead:delete"
    const val LEAD_ASSIGN = "lead:assign"

    // ── Lead Source ───────────────────────────────────────────────────────────
    const val LEAD_SOURCE_CREATE = "lead-source:create"
    const val LEAD_SOURCE_READ = "lead-source:read"
    const val LEAD_SOURCE_UPDATE = "lead-source:update"

    // ── Cash Drawer ───────────────────────────────────────────────────────────
    const val CASH_DRAWER_OPEN = "cash-drawer:open"
    const val CASH_DRAWER_CLOSE = "cash-drawer:close"
    const val CASH_DRAWER_READ = "cash-drawer:read"
    const val CASH_DRAWER_ENTRY_CREATE = "cash-drawer:entry:create"
    const val CASH_DRAWER_RECONCILE = "cash-drawer:reconcile"

    // ── Availability ──────────────────────────────────────────────────────────
    const val AVAILABILITY_MANAGE = "availability:manage"

    // ── Profile ───────────────────────────────────────────────────────────────
    const val PROFILE_UPDATE = "profile:update"

    // ── Earnings ──────────────────────────────────────────────────────────────
    const val EARNINGS_READ = "earnings:read"

    // ── Message ───────────────────────────────────────────────────────────────
    const val MESSAGE_SEND = "message:send"

    // ── Progress ──────────────────────────────────────────────────────────────
    const val PROGRESS_READ = "progress:read"
    const val PROGRESS_UPDATE = "progress:update"

    // ── Notification ──────────────────────────────────────────────────────────
    const val NOTIFICATION_READ = "notification:read"

    // ── Portal Settings ──────────────────────────────────────────────────────
    const val PORTAL_SETTINGS_UPDATE = "portal-settings:update"

    // ── Platform ──────────────────────────────────────────────────────────────
    const val PLATFORM_STATS_VIEW = "platform:stats:view"

    // ── ZATCA ─────────────────────────────────────────────────────────────────
    const val ZATCA_ONBOARD = "zatca:onboard"
    const val ZATCA_READ = "zatca:read"
    const val ZATCA_RETRY = "zatca:retry"

    // ── Member Note ──────────────────────────────────────────────────────────
    const val MEMBER_NOTE_CREATE = "member-note:create"
    const val MEMBER_NOTE_READ = "member-note:read"
    const val MEMBER_NOTE_DELETE = "member-note:delete"
    const val MEMBER_NOTE_FOLLOW_UP_READ = "member-note:follow-up:read"

    // ── Branding ─────────────────────────────────────────────────────────
    const val BRANDING_UPDATE = "branding:update"

    // ── Check-In ─────────────────────────────────────────────────────────
    const val CHECK_IN_CREATE = "check-in:create"
    const val CHECK_IN_READ = "check-in:read"

    // ── Shift ─────────────────────────────────────────────────────────────
    const val SHIFT_MANAGE = "shift:manage"
    const val SHIFT_READ = "shift:read"

    // ── Online Payment ───────────────────────────────────────────────────
    const val ONLINE_PAYMENT_READ = "online-payment:read"
}
