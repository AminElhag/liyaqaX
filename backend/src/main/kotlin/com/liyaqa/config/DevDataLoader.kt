package com.liyaqa.config

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.permission.Permission
import com.liyaqa.permission.PermissionConstants
import com.liyaqa.permission.PermissionRepository
import com.liyaqa.rbac.RolePermission
import com.liyaqa.rbac.RolePermissionRepository
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("dev")
class DevDataLoader(
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val userRepository: UserRepository,
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val userRoleRepository: UserRoleRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    private val log = LoggerFactory.getLogger(DevDataLoader::class.java)

    // ── Permission sets per role ───────────────────────────────────────────────

    private val platformAll =
        setOf(
            PermissionConstants.ORGANIZATION_CREATE, PermissionConstants.ORGANIZATION_READ,
            PermissionConstants.ORGANIZATION_UPDATE, PermissionConstants.ORGANIZATION_DELETE,
            PermissionConstants.CLUB_CREATE, PermissionConstants.CLUB_READ,
            PermissionConstants.CLUB_UPDATE, PermissionConstants.CLUB_DELETE,
            PermissionConstants.BRANCH_CREATE, PermissionConstants.BRANCH_READ,
            PermissionConstants.BRANCH_UPDATE, PermissionConstants.BRANCH_DELETE,
            PermissionConstants.STAFF_CREATE, PermissionConstants.STAFF_READ,
            PermissionConstants.STAFF_UPDATE, PermissionConstants.STAFF_DELETE,
            PermissionConstants.ROLE_CREATE, PermissionConstants.ROLE_READ,
            PermissionConstants.ROLE_UPDATE, PermissionConstants.ROLE_DELETE,
            PermissionConstants.INTEGRATION_CONFIGURE, PermissionConstants.INTEGRATION_READ,
            PermissionConstants.SYSTEM_IMPERSONATE, PermissionConstants.AUDIT_READ,
        )

    private val platformReadPlusImpersonate =
        setOf(
            PermissionConstants.ORGANIZATION_READ,
            PermissionConstants.CLUB_READ,
            PermissionConstants.BRANCH_READ,
            PermissionConstants.STAFF_READ,
            PermissionConstants.ROLE_READ,
            PermissionConstants.INTEGRATION_READ,
            PermissionConstants.AUDIT_READ,
            PermissionConstants.SYSTEM_IMPERSONATE,
        )

    private val platformIntegration =
        setOf(
            PermissionConstants.INTEGRATION_CONFIGURE,
            PermissionConstants.INTEGRATION_READ,
        )

    private val platformReadOnly =
        setOf(
            PermissionConstants.ORGANIZATION_READ,
            PermissionConstants.CLUB_READ,
            PermissionConstants.BRANCH_READ,
            PermissionConstants.STAFF_READ,
            PermissionConstants.ROLE_READ,
            PermissionConstants.INTEGRATION_READ,
            PermissionConstants.AUDIT_READ,
        )

    private val clubAll =
        setOf(
            PermissionConstants.STAFF_CREATE, PermissionConstants.STAFF_READ,
            PermissionConstants.STAFF_UPDATE, PermissionConstants.STAFF_DELETE,
            PermissionConstants.ROLE_CREATE, PermissionConstants.ROLE_READ,
            PermissionConstants.ROLE_UPDATE, PermissionConstants.ROLE_DELETE,
            PermissionConstants.MEMBER_CREATE, PermissionConstants.MEMBER_READ,
            PermissionConstants.MEMBER_UPDATE, PermissionConstants.MEMBER_DELETE,
            PermissionConstants.MEMBERSHIP_CREATE, PermissionConstants.MEMBERSHIP_READ,
            PermissionConstants.MEMBERSHIP_UPDATE, PermissionConstants.MEMBERSHIP_FREEZE,
            PermissionConstants.MEMBERSHIP_UNFREEZE, PermissionConstants.MEMBERSHIP_TRANSFER,
            PermissionConstants.PAYMENT_COLLECT, PermissionConstants.PAYMENT_READ,
            PermissionConstants.PAYMENT_REFUND,
            PermissionConstants.INVOICE_READ, PermissionConstants.INVOICE_GENERATE,
            PermissionConstants.REPORT_REVENUE_VIEW, PermissionConstants.REPORT_RETENTION_VIEW,
            PermissionConstants.REPORT_UTILIZATION_VIEW,
            PermissionConstants.PT_PACKAGE_CREATE, PermissionConstants.PT_PACKAGE_READ,
            PermissionConstants.PT_SESSION_CREATE, PermissionConstants.PT_SESSION_READ,
            PermissionConstants.PT_SESSION_UPDATE, PermissionConstants.PT_SESSION_MARK_ATTENDANCE,
            PermissionConstants.GX_CLASS_CREATE, PermissionConstants.GX_CLASS_READ,
            PermissionConstants.GX_CLASS_UPDATE, PermissionConstants.GX_CLASS_MANAGE_BOOKINGS,
            PermissionConstants.LEAD_CREATE, PermissionConstants.LEAD_READ,
            PermissionConstants.LEAD_UPDATE, PermissionConstants.LEAD_CONVERT,
            PermissionConstants.CASH_DRAWER_OPEN, PermissionConstants.CASH_DRAWER_CLOSE,
            PermissionConstants.CASH_DRAWER_READ, PermissionConstants.BRANCH_READ,
        )

    private val clubBranchManager =
        clubAll -
            setOf(
                PermissionConstants.ROLE_CREATE, PermissionConstants.ROLE_READ,
                PermissionConstants.ROLE_UPDATE, PermissionConstants.ROLE_DELETE,
            )

    private val clubReceptionist =
        setOf(
            PermissionConstants.MEMBER_CREATE, PermissionConstants.MEMBER_READ,
            PermissionConstants.MEMBER_UPDATE, PermissionConstants.MEMBER_DELETE,
            PermissionConstants.MEMBERSHIP_CREATE, PermissionConstants.MEMBERSHIP_READ,
            PermissionConstants.MEMBERSHIP_UPDATE, PermissionConstants.MEMBERSHIP_FREEZE,
            PermissionConstants.MEMBERSHIP_UNFREEZE, PermissionConstants.MEMBERSHIP_TRANSFER,
            PermissionConstants.PAYMENT_COLLECT, PermissionConstants.PAYMENT_READ,
            PermissionConstants.INVOICE_READ, PermissionConstants.INVOICE_GENERATE,
            PermissionConstants.LEAD_READ,
            PermissionConstants.CASH_DRAWER_OPEN, PermissionConstants.CASH_DRAWER_CLOSE,
            PermissionConstants.CASH_DRAWER_READ, PermissionConstants.BRANCH_READ,
        )

    private val clubSalesAgent =
        setOf(
            PermissionConstants.LEAD_CREATE,
            PermissionConstants.LEAD_READ,
            PermissionConstants.LEAD_UPDATE,
            PermissionConstants.LEAD_CONVERT,
            PermissionConstants.MEMBER_CREATE,
            PermissionConstants.MEMBER_READ,
            PermissionConstants.PAYMENT_COLLECT,
            PermissionConstants.BRANCH_READ,
        )

    private val trainerPt =
        setOf(
            PermissionConstants.PT_SESSION_READ, PermissionConstants.PT_SESSION_UPDATE,
            PermissionConstants.PT_SESSION_MARK_ATTENDANCE,
            PermissionConstants.MEMBER_READ,
            PermissionConstants.GX_CLASS_READ, PermissionConstants.GX_CLASS_MARK_ATTENDANCE,
            PermissionConstants.MESSAGE_SEND, PermissionConstants.AVAILABILITY_MANAGE,
            PermissionConstants.PROFILE_UPDATE, PermissionConstants.EARNINGS_READ,
        )

    private val trainerGx =
        setOf(
            PermissionConstants.GX_CLASS_READ,
            PermissionConstants.GX_CLASS_MARK_ATTENDANCE,
            PermissionConstants.MEMBER_READ,
            PermissionConstants.MESSAGE_SEND,
            PermissionConstants.AVAILABILITY_MANAGE,
            PermissionConstants.PROFILE_UPDATE,
            PermissionConstants.EARNINGS_READ,
        )

    private val memberPerms =
        setOf(
            PermissionConstants.MEMBERSHIP_READ, PermissionConstants.MEMBERSHIP_FREEZE_REQUEST,
            PermissionConstants.PT_SESSION_READ, PermissionConstants.PT_SESSION_RESCHEDULE_REQUEST,
            PermissionConstants.GX_CLASS_READ, PermissionConstants.GX_CLASS_BOOK,
            PermissionConstants.GX_CLASS_CANCEL_BOOKING,
            PermissionConstants.PAYMENT_MAKE, PermissionConstants.INVOICE_READ,
            PermissionConstants.PROFILE_UPDATE, PermissionConstants.MESSAGE_SEND,
            PermissionConstants.PROGRESS_READ, PermissionConstants.PROGRESS_UPDATE,
            PermissionConstants.NOTIFICATION_READ,
        )

    // ── Seed orchestration ────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun seed() {
        if (organizationRepository.count() > 0) {
            log.info("Seed data already exists — skipping.")
            return
        }

        log.info("Seeding dev data...")

        val org = seedOrg()
        val club = seedClub(org)
        val (riyadhBranch) = seedBranches(org, club)
        val users = seedUsers(org, club, riyadhBranch)
        val permissions = seedPermissions()
        val roles = seedRoles(org, club, permissions)
        seedUserRoles(users, roles)

        log.info(
            "Seeded 1 org, 1 club, 2 branches, {} users, {} permissions, {} roles.",
            users.size,
            permissions.size,
            roles.size,
        )
    }

    // ── Infrastructure ────────────────────────────────────────────────────────

    private fun seedOrg(): Organization =
        organizationRepository.save(
            Organization(
                nameAr = "مؤسسة لياقة التجريبية",
                nameEn = "Liyaqa Demo Org",
                email = "demo@liyaqa.com",
                country = "SA",
                timezone = "Asia/Riyadh",
            ),
        )

    private fun seedClub(org: Organization): Club =
        clubRepository.save(
            Club(
                organizationId = org.id,
                nameAr = "\u0646\u0627\u062F\u064A \u0625\u0643\u0633\u064A\u0631",
                nameEn = "Elixir Gym",
                email = "info@elixir.com",
            ),
        )

    private fun seedBranches(
        org: Organization,
        club: Club,
    ): List<Branch> {
        val riyadh =
            branchRepository.save(
                Branch(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "\u0641\u0631\u0639 \u0627\u0644\u0631\u064a\u0627\u0636",
                    nameEn = "Elixir Gym - Riyadh",
                    city = "Riyadh",
                ),
            )
        val jeddah =
            branchRepository.save(
                Branch(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "\u0641\u0631\u0639 \u062c\u062f\u0629",
                    nameEn = "Elixir Gym - Jeddah",
                    city = "Jeddah",
                ),
            )
        return listOf(riyadh, jeddah)
    }

    private fun seedUsers(
        org: Organization,
        club: Club,
        riyadhBranch: Branch,
    ): List<User> {
        val users =
            listOf(
                user("admin@liyaqa.com", "Admin1234!", null, null, null),
                user("owner@elixir.com", "Owner1234!", org.id, club.id, null),
                user("manager@elixir.com", "Manager1234!", org.id, club.id, riyadhBranch.id),
                user("reception@elixir.com", "Recept1234!", org.id, club.id, riyadhBranch.id),
                user("sales@elixir.com", "Sales1234!", org.id, club.id, riyadhBranch.id),
                user("pt@elixir.com", "Trainer1234!", org.id, club.id, riyadhBranch.id),
                user("gx@elixir.com", "Trainer1234!", org.id, club.id, riyadhBranch.id),
                user("member@elixir.com", "Member1234!", org.id, club.id, riyadhBranch.id),
            )
        return userRepository.saveAll(users)
    }

    private fun user(
        email: String,
        rawPassword: String,
        organizationId: Long?,
        clubId: Long?,
        branchId: Long?,
    ) = User(
        email = email,
        passwordHash = passwordEncoder.encode(rawPassword),
        organizationId = organizationId,
        clubId = clubId,
        branchId = branchId,
    )

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun seedPermissions(): Map<String, Permission> {
        val allCodes =
            platformAll + platformReadPlusImpersonate + platformIntegration +
                platformReadOnly + clubAll + trainerPt + trainerGx + memberPerms

        val saved =
            permissionRepository.saveAll(
                allCodes.map { code ->
                    val colonIdx = code.indexOf(':')
                    Permission(
                        code = code,
                        resource = code.substring(0, colonIdx),
                        action = code.substring(colonIdx + 1),
                    )
                },
            )
        return saved.associateBy { it.code }
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    private fun seedRoles(
        org: Organization,
        club: Club,
        permissions: Map<String, Permission>,
    ): Map<String, Role> {
        val roles = mutableMapOf<String, Role>()

        // Platform roles
        roles["Super Admin"] =
            seedRole("مدير النظام", "Super Admin", "platform", null, null, true, platformAll, permissions)
        roles["Support Agent"] =
            seedRole("وكيل الدعم", "Support Agent", "platform", null, null, true, platformReadPlusImpersonate, permissions)
        roles["Integration Specialist"] =
            seedRole("متخصص التكاملات", "Integration Specialist", "platform", null, null, true, platformIntegration, permissions)
        roles["Read-Only Auditor"] =
            seedRole("مراقب للقراءة فقط", "Read-Only Auditor", "platform", null, null, true, platformReadOnly, permissions)

        // Club roles (scoped to Elixir Gym)
        roles["Owner"] = seedRole("مالك", "Owner", "club", org.id, club.id, true, clubAll, permissions)
        roles["Branch Manager"] = seedRole("مدير الفرع", "Branch Manager", "club", org.id, club.id, true, clubBranchManager, permissions)
        roles["Receptionist"] = seedRole("موظف الاستقبال", "Receptionist", "club", org.id, club.id, true, clubReceptionist, permissions)
        roles["Sales Agent"] = seedRole("موظف المبيعات", "Sales Agent", "club", org.id, club.id, true, clubSalesAgent, permissions)
        roles["PT Trainer"] = seedRole("مدرب شخصي", "PT Trainer", "club", org.id, club.id, true, trainerPt, permissions)
        roles["GX Instructor"] = seedRole("مدرب جماعي", "GX Instructor", "club", org.id, club.id, true, trainerGx, permissions)

        // Member role
        roles["Member"] = seedRole("عضو", "Member", "member", null, null, true, memberPerms, permissions)

        return roles
    }

    private fun seedRole(
        nameAr: String,
        nameEn: String,
        scope: String,
        organizationId: Long?,
        clubId: Long?,
        isSystem: Boolean,
        permissionCodes: Set<String>,
        permissions: Map<String, Permission>,
    ): Role {
        val role =
            roleRepository.save(
                Role(
                    nameAr = nameAr,
                    nameEn = nameEn,
                    scope = scope,
                    organizationId = organizationId,
                    clubId = clubId,
                    isSystem = isSystem,
                ),
            )
        val rolePermissions =
            permissionCodes.mapNotNull { code ->
                permissions[code]?.let { RolePermission(roleId = role.id, permissionId = it.id) }
            }
        rolePermissionRepository.saveAll(rolePermissions)
        return role
    }

    // ── User → Role assignments ───────────────────────────────────────────────

    private fun seedUserRoles(
        users: List<User>,
        roles: Map<String, Role>,
    ) {
        val usersByEmail = users.associateBy { it.email }

        val assignments =
            mapOf(
                "admin@liyaqa.com" to "Super Admin",
                "owner@elixir.com" to "Owner",
                "manager@elixir.com" to "Branch Manager",
                "reception@elixir.com" to "Receptionist",
                "sales@elixir.com" to "Sales Agent",
                "pt@elixir.com" to "PT Trainer",
                "gx@elixir.com" to "GX Instructor",
                "member@elixir.com" to "Member",
            )

        val userRoles =
            assignments.mapNotNull { (email, roleName) ->
                val user = usersByEmail[email] ?: return@mapNotNull null
                val role = roles[roleName] ?: return@mapNotNull null
                UserRole(userId = user.id, roleId = role.id)
            }

        userRoleRepository.saveAll(userRoles)
    }
}
