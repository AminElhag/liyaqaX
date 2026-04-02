package com.arena.security

object Roles {
    // Nexus — internal platform team
    const val NEXUS_SUPER_ADMIN = "nexus:super-admin"
    const val NEXUS_SUPPORT_AGENT = "nexus:support-agent"
    const val NEXUS_INTEGRATION_SPECIALIST = "nexus:integration-specialist"
    const val NEXUS_READ_ONLY_AUDITOR = "nexus:read-only-auditor"

    // Club — club operations staff
    const val CLUB_OWNER = "club:owner"
    const val CLUB_BRANCH_MANAGER = "club:branch-manager"
    const val CLUB_RECEPTIONIST = "club:receptionist"
    const val CLUB_SALES_AGENT = "club:sales-agent"

    // Trainer
    const val TRAINER_PT = "trainer:pt"
    const val TRAINER_GX = "trainer:gx"

    // Member
    const val MEMBER = "member"

    val ALL: Set<String> =
        setOf(
            NEXUS_SUPER_ADMIN, NEXUS_SUPPORT_AGENT,
            NEXUS_INTEGRATION_SPECIALIST, NEXUS_READ_ONLY_AUDITOR,
            CLUB_OWNER, CLUB_BRANCH_MANAGER,
            CLUB_RECEPTIONIST, CLUB_SALES_AGENT,
            TRAINER_PT, TRAINER_GX,
            MEMBER,
        )
}
