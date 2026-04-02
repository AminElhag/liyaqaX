package com.liyaqa.security

object Roles {
    const val NEXUS_SUPER_ADMIN = "nexus:super-admin"
    const val NEXUS_SUPPORT_AGENT = "nexus:support-agent"
    const val NEXUS_INTEGRATION_SPECIALIST = "nexus:integration-specialist"
    const val NEXUS_READ_ONLY_AUDITOR = "nexus:read-only-auditor"

    const val CLUB_OWNER = "club:owner"
    const val CLUB_BRANCH_MANAGER = "club:branch-manager"
    const val CLUB_RECEPTIONIST = "club:receptionist"
    const val CLUB_SALES_AGENT = "club:sales-agent"

    const val TRAINER_PT = "trainer:pt"
    const val TRAINER_GX = "trainer:gx"

    const val MEMBER = "member"

    // SpEL expressions for @PreAuthorize — used as compile-time constants
    const val NEXUS_WRITE =
        "hasAnyAuthority('nexus:super-admin', 'nexus:support-agent')"
    const val NEXUS_READ =
        "hasAnyAuthority('nexus:super-admin', 'nexus:support-agent', " +
            "'nexus:integration-specialist', 'nexus:read-only-auditor')"
}
