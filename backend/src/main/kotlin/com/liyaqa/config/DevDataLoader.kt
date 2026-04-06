package com.liyaqa.config

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.gx.GXBooking
import com.liyaqa.gx.GXBookingRepository
import com.liyaqa.gx.GXClassInstance
import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.gx.GXClassType
import com.liyaqa.gx.GXClassTypeRepository
import com.liyaqa.invoice.Invoice
import com.liyaqa.invoice.InvoiceCounter
import com.liyaqa.invoice.InvoiceCounterRepository
import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.lead.Lead
import com.liyaqa.lead.LeadRepository
import com.liyaqa.lead.LeadSource
import com.liyaqa.lead.LeadSourceRepository
import com.liyaqa.member.EmergencyContact
import com.liyaqa.member.EmergencyContactRepository
import com.liyaqa.member.HealthWaiver
import com.liyaqa.member.HealthWaiverRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.member.WaiverSignature
import com.liyaqa.member.WaiverSignatureRepository
import com.liyaqa.membership.Membership
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.Payment
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.permission.Permission
import com.liyaqa.permission.PermissionConstants
import com.liyaqa.permission.PermissionRepository
import com.liyaqa.rbac.RolePermission
import com.liyaqa.rbac.RolePermissionRepository
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.StaffBranchAssignment
import com.liyaqa.staff.StaffBranchAssignmentRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.trainer.Trainer
import com.liyaqa.trainer.TrainerBranchAssignment
import com.liyaqa.trainer.TrainerBranchAssignmentRepository
import com.liyaqa.trainer.TrainerCertification
import com.liyaqa.trainer.TrainerCertificationRepository
import com.liyaqa.trainer.TrainerRepository
import com.liyaqa.trainer.TrainerSpecialization
import com.liyaqa.trainer.TrainerSpecializationRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

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
    private val staffMemberRepository: StaffMemberRepository,
    private val staffBranchAssignmentRepository: StaffBranchAssignmentRepository,
    private val trainerRepository: TrainerRepository,
    private val trainerBranchAssignmentRepository: TrainerBranchAssignmentRepository,
    private val trainerCertificationRepository: TrainerCertificationRepository,
    private val trainerSpecializationRepository: TrainerSpecializationRepository,
    private val memberRepository: MemberRepository,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val healthWaiverRepository: HealthWaiverRepository,
    private val waiverSignatureRepository: WaiverSignatureRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val membershipRepository: MembershipRepository,
    private val paymentRepository: PaymentRepository,
    private val invoiceRepository: InvoiceRepository,
    private val invoiceCounterRepository: InvoiceCounterRepository,
    private val gxClassTypeRepository: GXClassTypeRepository,
    private val gxClassInstanceRepository: GXClassInstanceRepository,
    private val gxBookingRepository: GXBookingRepository,
    private val leadSourceRepository: LeadSourceRepository,
    private val leadRepository: LeadRepository,
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
            PermissionConstants.MEMBERSHIP_PLAN_CREATE, PermissionConstants.MEMBERSHIP_PLAN_READ,
            PermissionConstants.MEMBERSHIP_PLAN_UPDATE, PermissionConstants.MEMBERSHIP_PLAN_DELETE,
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
            PermissionConstants.LEAD_DELETE, PermissionConstants.LEAD_ASSIGN,
            PermissionConstants.LEAD_SOURCE_CREATE, PermissionConstants.LEAD_SOURCE_READ,
            PermissionConstants.LEAD_SOURCE_UPDATE,
            PermissionConstants.CASH_DRAWER_OPEN, PermissionConstants.CASH_DRAWER_CLOSE,
            PermissionConstants.CASH_DRAWER_READ, PermissionConstants.BRANCH_READ,
        )

    private val clubBranchManager =
        clubAll -
            setOf(
                PermissionConstants.ROLE_CREATE, PermissionConstants.ROLE_READ,
                PermissionConstants.ROLE_UPDATE, PermissionConstants.ROLE_DELETE,
                PermissionConstants.MEMBERSHIP_PLAN_DELETE,
            )

    private val clubReceptionist =
        setOf(
            PermissionConstants.MEMBER_CREATE, PermissionConstants.MEMBER_READ,
            PermissionConstants.MEMBER_UPDATE, PermissionConstants.MEMBER_DELETE,
            PermissionConstants.MEMBERSHIP_PLAN_READ,
            PermissionConstants.MEMBERSHIP_CREATE, PermissionConstants.MEMBERSHIP_READ,
            PermissionConstants.MEMBERSHIP_UPDATE, PermissionConstants.MEMBERSHIP_FREEZE,
            PermissionConstants.MEMBERSHIP_UNFREEZE, PermissionConstants.MEMBERSHIP_TRANSFER,
            PermissionConstants.PAYMENT_COLLECT, PermissionConstants.PAYMENT_READ,
            PermissionConstants.INVOICE_READ, PermissionConstants.INVOICE_GENERATE,
            PermissionConstants.LEAD_CREATE, PermissionConstants.LEAD_READ,
            PermissionConstants.LEAD_SOURCE_READ,
            PermissionConstants.CASH_DRAWER_OPEN, PermissionConstants.CASH_DRAWER_CLOSE,
            PermissionConstants.CASH_DRAWER_READ, PermissionConstants.BRANCH_READ,
        )

    private val clubSalesAgent =
        setOf(
            PermissionConstants.LEAD_CREATE,
            PermissionConstants.LEAD_READ,
            PermissionConstants.LEAD_UPDATE,
            PermissionConstants.LEAD_CONVERT,
            PermissionConstants.LEAD_SOURCE_READ,
            PermissionConstants.MEMBER_CREATE,
            PermissionConstants.MEMBER_READ,
            PermissionConstants.MEMBERSHIP_PLAN_READ,
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
    @Order(1)
    @Transactional
    fun seed() {
        if (organizationRepository.count() > 0) {
            log.info("Seed data already exists — skipping.")
            return
        }

        log.info("Seeding dev data...")

        val org = seedOrg()
        val club = seedClub(org)
        invoiceCounterRepository.save(InvoiceCounter(clubId = club.id))
        val (riyadhBranch, jeddahBranch) = seedBranches(org, club)
        val users = seedUsers(org, club)
        val permissions = seedPermissions()
        val roles = seedRoles(org, club, permissions)
        seedUserRoles(users, roles)
        seedStaffMembers(org, club, users, roles, riyadhBranch, jeddahBranch)
        seedTrainers(org, club, users, riyadhBranch)
        val member = seedMember(org, club, users, riyadhBranch)
        val plans = seedMembershipPlans(org, club)
        seedMemberMembership(org, club, riyadhBranch, member, plans, users)
        seedGXClasses(org, club, riyadhBranch, users, member)
        val staffByEmail = staffMemberRepository.findAll().associateBy { userRepository.findById(it.userId).get().email }
        seedLeads(org, club, riyadhBranch, staffByEmail)

        log.info(
            "Seeded 1 org, 1 club, 2 branches, {} users, {} permissions, {} roles, " +
                "4 staff, 2 trainers, 1 member, 3 plans, 1 membership, 3 GX types, 5 instances, " +
                "1 booking, 4 lead sources, 3 leads.",
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
                vatNumber = "300000000000003",
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
    ): List<User> {
        val users =
            listOf(
                user("admin@liyaqa.com", "Admin1234!", null, null),
                user("owner@elixir.com", "Owner1234!", org.id, club.id),
                user("manager@elixir.com", "Manager1234!", org.id, club.id),
                user("reception@elixir.com", "Recept1234!", org.id, club.id),
                user("sales@elixir.com", "Sales1234!", org.id, club.id),
                user("pt@elixir.com", "Trainer1234!", org.id, club.id),
                user("gx@elixir.com", "Trainer1234!", org.id, club.id),
                user("member@elixir.com", "Member1234!", org.id, club.id),
            )
        return userRepository.saveAll(users)
    }

    private fun user(
        email: String,
        rawPassword: String,
        organizationId: Long?,
        clubId: Long?,
    ) = User(
        email = email,
        passwordHash = passwordEncoder.encode(rawPassword),
        organizationId = organizationId,
        clubId = clubId,
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
        roles["PT Trainer"] = seedRole("مدرب شخصي", "PT Trainer", "trainer", org.id, club.id, true, trainerPt, permissions)
        roles["GX Instructor"] = seedRole("مدرب جماعي", "GX Instructor", "trainer", org.id, club.id, true, trainerGx, permissions)

        // Member role (scoped to club so it can be looked up during registration)
        roles["Member"] = seedRole("عضو", "Member", "member", org.id, club.id, true, memberPerms, permissions)

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

    // ── Staff members ─────────────────────────────────────────────────────────

    private fun seedStaffMembers(
        org: Organization,
        club: Club,
        users: List<User>,
        roles: Map<String, Role>,
        riyadhBranch: Branch,
        jeddahBranch: Branch,
    ) {
        val usersByEmail = users.associateBy { it.email }
        val joinedAt = LocalDate.of(2024, 1, 1)

        data class StaffSeed(
            val email: String,
            val roleName: String,
            val firstNameAr: String,
            val firstNameEn: String,
            val lastNameAr: String,
            val lastNameEn: String,
            val branches: List<Branch>,
        )

        val seeds =
            listOf(
                StaffSeed("owner@elixir.com", "Owner", "أحمد", "Ahmed", "العمري", "Al-Omari", listOf(riyadhBranch, jeddahBranch)),
                StaffSeed("manager@elixir.com", "Branch Manager", "سارة", "Sarah", "المنصوري", "Al-Mansouri", listOf(riyadhBranch)),
                StaffSeed("reception@elixir.com", "Receptionist", "فاطمة", "Fatima", "الزهراني", "Al-Zahrani", listOf(riyadhBranch)),
                StaffSeed("sales@elixir.com", "Sales Agent", "محمد", "Mohammed", "القحطاني", "Al-Qahtani", listOf(riyadhBranch)),
            )

        for (seed in seeds) {
            val user = usersByEmail[seed.email] ?: continue
            val role = roles[seed.roleName] ?: continue

            val staff =
                staffMemberRepository.save(
                    StaffMember(
                        organizationId = org.id,
                        clubId = club.id,
                        userId = user.id,
                        roleId = role.id,
                        firstNameAr = seed.firstNameAr,
                        firstNameEn = seed.firstNameEn,
                        lastNameAr = seed.lastNameAr,
                        lastNameEn = seed.lastNameEn,
                        employmentType = "full-time",
                        joinedAt = joinedAt,
                    ),
                )

            staffBranchAssignmentRepository.saveAll(
                seed.branches.map {
                    StaffBranchAssignment(
                        staffMemberId = staff.id,
                        branchId = it.id,
                        organizationId = org.id,
                    )
                },
            )
        }
    }

    // ── Trainers ──────────────────────────────────────────────────────────────

    private fun seedTrainers(
        org: Organization,
        club: Club,
        users: List<User>,
        riyadhBranch: Branch,
    ) {
        val usersByEmail = users.associateBy { it.email }
        val joinedAt = LocalDate.of(2024, 3, 1)

        data class TrainerSeed(
            val email: String,
            val firstNameAr: String,
            val firstNameEn: String,
            val lastNameAr: String,
            val lastNameEn: String,
            val bioAr: String?,
            val bioEn: String?,
        )

        val seeds =
            listOf(
                TrainerSeed(
                    "pt@elixir.com",
                    "خالد",
                    "Khalid",
                    "الشمري",
                    "Al-Shammari",
                    "مدرب شخصي معتمد متخصص في فقدان الوزن وبناء العضلات",
                    "Certified personal trainer specializing in weight loss and muscle building",
                ),
                TrainerSeed(
                    "gx@elixir.com",
                    "نورة",
                    "Noura",
                    "الحربي",
                    "Al-Harbi",
                    "مدربة جماعية متخصصة في اليوغا والبيلاتس",
                    "Group exercise instructor specializing in yoga and pilates",
                ),
            )

        for (seed in seeds) {
            val user = usersByEmail[seed.email] ?: continue

            val trainer =
                trainerRepository.save(
                    Trainer(
                        organizationId = org.id,
                        clubId = club.id,
                        userId = user.id,
                        firstNameAr = seed.firstNameAr,
                        firstNameEn = seed.firstNameEn,
                        lastNameAr = seed.lastNameAr,
                        lastNameEn = seed.lastNameEn,
                        bioAr = seed.bioAr,
                        bioEn = seed.bioEn,
                        joinedAt = joinedAt,
                    ),
                )

            trainerBranchAssignmentRepository.save(
                TrainerBranchAssignment(
                    trainerId = trainer.id,
                    branchId = riyadhBranch.id,
                    organizationId = org.id,
                ),
            )

            seedTrainerCertifications(trainer, org)
            seedTrainerSpecializations(trainer, org, seed.email)
        }
    }

    private fun seedTrainerCertifications(
        trainer: Trainer,
        org: Organization,
    ) {
        trainerCertificationRepository.saveAll(
            listOf(
                TrainerCertification(
                    trainerId = trainer.id,
                    organizationId = org.id,
                    nameAr = "شهادة مدرب لياقة بدنية",
                    nameEn = "Certified Fitness Trainer",
                    issuingBody = "ACE",
                    issuedAt = LocalDate.of(2022, 6, 15),
                    expiresAt = LocalDate.of(2025, 6, 15),
                    status = "approved",
                ),
                TrainerCertification(
                    trainerId = trainer.id,
                    organizationId = org.id,
                    nameAr = "شهادة الإسعافات الأولية",
                    nameEn = "First Aid Certificate",
                    issuingBody = "Red Crescent",
                    issuedAt = LocalDate.of(2023, 1, 10),
                    expiresAt = LocalDate.of(2026, 1, 10),
                    status = "pending-review",
                ),
            ),
        )
    }

    private fun seedTrainerSpecializations(
        trainer: Trainer,
        org: Organization,
        email: String,
    ) {
        val specs =
            if (email == "pt@elixir.com") {
                listOf(
                    "فقدان الوزن" to "Weight Loss",
                    "بناء العضلات" to "Muscle Building",
                    "إعادة التأهيل" to "Rehabilitation",
                )
            } else {
                listOf(
                    "يوغا" to "Yoga",
                    "بيلاتس" to "Pilates",
                )
            }

        trainerSpecializationRepository.saveAll(
            specs.map { (ar, en) ->
                TrainerSpecialization(
                    trainerId = trainer.id,
                    organizationId = org.id,
                    nameAr = ar,
                    nameEn = en,
                )
            },
        )
    }

    // ── Membership plans ───────────────────────────────────────────────────────

    private fun seedMembershipPlans(
        org: Organization,
        club: Club,
    ): List<MembershipPlan> =
        membershipPlanRepository.saveAll(
            listOf(
                MembershipPlan(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "اشتراك شهري أساسي",
                    nameEn = "Basic Monthly",
                    priceHalalas = 15000,
                    durationDays = 30,
                    gracePeriodDays = 3,
                    freezeAllowed = true,
                    maxFreezeDays = 14,
                    gxClassesIncluded = true,
                    ptSessionsIncluded = false,
                    sortOrder = 1,
                ),
                MembershipPlan(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "اشتراك ربع سنوي",
                    nameEn = "Quarterly Standard",
                    priceHalalas = 39900,
                    durationDays = 90,
                    gracePeriodDays = 5,
                    freezeAllowed = true,
                    maxFreezeDays = 21,
                    gxClassesIncluded = true,
                    ptSessionsIncluded = false,
                    sortOrder = 2,
                ),
                MembershipPlan(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "اشتراك سنوي مميز",
                    nameEn = "Annual Premium",
                    priceHalalas = 129900,
                    durationDays = 365,
                    gracePeriodDays = 7,
                    freezeAllowed = true,
                    maxFreezeDays = 30,
                    gxClassesIncluded = true,
                    ptSessionsIncluded = true,
                    sortOrder = 3,
                ),
            ),
        )

    // ── Member ───────────────────────────────────────────────────────────────

    private fun seedMember(
        org: Organization,
        club: Club,
        users: List<User>,
        riyadhBranch: Branch,
    ): Member {
        val user = users.first { it.email == "member@elixir.com" }

        val member =
            memberRepository.save(
                Member(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = riyadhBranch.id,
                    userId = user.id,
                    firstNameAr = "أحمد",
                    firstNameEn = "Ahmed",
                    lastNameAr = "الرشيدي",
                    lastNameEn = "Al-Rashidi",
                    phone = "+966501234567",
                    membershipStatus = "pending",
                ),
            )

        emergencyContactRepository.save(
            EmergencyContact(
                memberId = member.id,
                organizationId = org.id,
                nameAr = "محمد الرشيدي",
                nameEn = "Mohammed Al-Rashidi",
                phone = "+966507654321",
                relationship = "Brother",
            ),
        )

        val waiver =
            healthWaiverRepository.save(
                HealthWaiver(
                    organizationId = org.id,
                    clubId = club.id,
                    contentAr = "أقر بأنني على دراية بالمخاطر المحتملة المرتبطة بالتمارين البدنية وأتحمل المسؤولية الكاملة.",
                    contentEn =
                        "I acknowledge the potential risks associated with physical exercise " +
                            "and assume full responsibility for my participation.",
                    version = 1,
                    isActive = true,
                ),
            )

        waiverSignatureRepository.save(
            WaiverSignature(
                memberId = member.id,
                waiverId = waiver.id,
                organizationId = org.id,
            ),
        )

        return member
    }

    // ── Membership assignment ─────────────────────────────────────────────────

    private fun seedMemberMembership(
        org: Organization,
        club: Club,
        branch: Branch,
        member: Member,
        plans: List<MembershipPlan>,
        users: List<User>,
    ) {
        val basicPlan = plans.first { it.nameEn == "Basic Monthly" }
        val receptionUser = users.first { it.email == "reception@elixir.com" }
        val today = LocalDate.now()

        val membership =
            membershipRepository.save(
                Membership(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    memberId = member.id,
                    planId = basicPlan.id,
                    membershipStatus = "active",
                    startDate = today,
                    endDate = today.plusDays(basicPlan.durationDays.toLong()),
                    graceEndDate = today.plusDays((basicPlan.durationDays + basicPlan.gracePeriodDays).toLong()),
                ),
            )

        val payment =
            paymentRepository.save(
                Payment(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    memberId = member.id,
                    membershipId = membership.id,
                    amountHalalas = basicPlan.priceHalalas,
                    paymentMethod = "cash",
                    collectedById = receptionUser.id,
                    paidAt = Instant.now(),
                ),
            )

        val subtotalHalalas = basicPlan.priceHalalas
        val vatAmountHalalas =
            BigDecimal(subtotalHalalas)
                .multiply(BigDecimal("0.1500"))
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
        val totalHalalas = subtotalHalalas + vatAmountHalalas
        val year = today.year
        val clubCode = club.nameEn.take(3).uppercase()

        invoiceRepository.save(
            Invoice(
                organizationId = org.id,
                clubId = club.id,
                branchId = branch.id,
                memberId = member.id,
                paymentId = payment.id,
                invoiceNumber = "INV-$year-$clubCode-00001",
                subtotalHalalas = subtotalHalalas,
                vatAmountHalalas = vatAmountHalalas,
                totalHalalas = totalHalalas,
            ),
        )

        member.membershipStatus = "active"
        memberRepository.save(member)
    }

    // ── GX Classes ────────────────────────────────────────────────────────

    private fun seedGXClasses(
        org: Organization,
        club: Club,
        riyadhBranch: Branch,
        users: List<User>,
        member: Member,
    ) {
        val gxUser = users.first { it.email == "gx@elixir.com" }
        val instructor =
            trainerRepository.findByUserIdAndDeletedAtIsNull(gxUser.id)
                .orElseThrow()

        val yoga =
            gxClassTypeRepository.save(
                GXClassType(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "\u064a\u0648\u063a\u0627",
                    nameEn = "Yoga",
                    defaultDurationMinutes = 60,
                    defaultCapacity = 15,
                    color = "#8B5CF6",
                ),
            )

        val hiit =
            gxClassTypeRepository.save(
                GXClassType(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "\u0647\u064a\u062a",
                    nameEn = "HIIT",
                    defaultDurationMinutes = 45,
                    defaultCapacity = 20,
                    color = "#EF4444",
                ),
            )

        val spinning =
            gxClassTypeRepository.save(
                GXClassType(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "\u0633\u0628\u064a\u0646\u064a\u0646\u062c",
                    nameEn = "Spinning",
                    defaultDurationMinutes = 50,
                    defaultCapacity = 15,
                    color = "#F59E0B",
                ),
            )

        val nextMonday = nextWeekday(java.time.DayOfWeek.MONDAY)
        val nextWednesday = nextWeekday(java.time.DayOfWeek.WEDNESDAY)
        val nextFriday = nextWeekday(java.time.DayOfWeek.FRIDAY)
        val riyadhZone = java.time.ZoneId.of("Asia/Riyadh")

        val mondayYoga =
            gxClassInstanceRepository.save(
                GXClassInstance(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = riyadhBranch.id,
                    classTypeId = yoga.id,
                    instructorId = instructor.id,
                    scheduledAt = nextMonday.atTime(7, 0).atZone(riyadhZone).toInstant(),
                    durationMinutes = 60,
                    capacity = 15,
                    room = "Room 1",
                ),
            )

        gxClassInstanceRepository.save(
            GXClassInstance(
                organizationId = org.id,
                clubId = club.id,
                branchId = riyadhBranch.id,
                classTypeId = hiit.id,
                instructorId = instructor.id,
                scheduledAt = nextMonday.atTime(9, 0).atZone(riyadhZone).toInstant(),
                durationMinutes = 45,
                capacity = 20,
                room = "Room 2",
            ),
        )

        gxClassInstanceRepository.save(
            GXClassInstance(
                organizationId = org.id,
                clubId = club.id,
                branchId = riyadhBranch.id,
                classTypeId = yoga.id,
                instructorId = instructor.id,
                scheduledAt = nextWednesday.atTime(7, 0).atZone(riyadhZone).toInstant(),
                durationMinutes = 60,
                capacity = 15,
                room = "Room 1",
            ),
        )

        gxClassInstanceRepository.save(
            GXClassInstance(
                organizationId = org.id,
                clubId = club.id,
                branchId = riyadhBranch.id,
                classTypeId = hiit.id,
                instructorId = instructor.id,
                scheduledAt = nextWednesday.atTime(9, 0).atZone(riyadhZone).toInstant(),
                durationMinutes = 45,
                capacity = 20,
                room = "Room 2",
            ),
        )

        gxClassInstanceRepository.save(
            GXClassInstance(
                organizationId = org.id,
                clubId = club.id,
                branchId = riyadhBranch.id,
                classTypeId = spinning.id,
                instructorId = instructor.id,
                scheduledAt = nextFriday.atTime(8, 0).atZone(riyadhZone).toInstant(),
                durationMinutes = 50,
                capacity = 15,
                room = "Room 3",
            ),
        )

        // Book Ahmed into Monday Yoga
        mondayYoga.bookingsCount = 1
        gxClassInstanceRepository.save(mondayYoga)

        gxBookingRepository.save(
            GXBooking(
                organizationId = org.id,
                clubId = club.id,
                instanceId = mondayYoga.id,
                memberId = member.id,
                bookingStatus = "confirmed",
            ),
        )
    }

    private fun nextWeekday(dayOfWeek: java.time.DayOfWeek): LocalDate {
        var date = LocalDate.now().plusDays(1)
        while (date.dayOfWeek != dayOfWeek) {
            date = date.plusDays(1)
        }
        return date
    }

    // ── Lead sources & leads ─────────────────────────────────────────────────

    private fun seedLeads(
        org: Organization,
        club: Club,
        riyadhBranch: Branch,
        staffByEmail: Map<String, StaffMember>,
    ) {
        val walkIn =
            leadSourceRepository.save(
                LeadSource(
                    organizationId = org.id,
                    clubId = club.id,
                    name = "Walk-in",
                    nameAr = "حضور مباشر",
                    color = "#10B981",
                    displayOrder = 1,
                ),
            )
        val phoneWhatsapp =
            leadSourceRepository.save(
                LeadSource(
                    organizationId = org.id,
                    clubId = club.id,
                    name = "Phone/WhatsApp",
                    nameAr = "هاتف/واتساب",
                    color = "#3B82F6",
                    displayOrder = 2,
                ),
            )
        val socialMedia =
            leadSourceRepository.save(
                LeadSource(
                    organizationId = org.id,
                    clubId = club.id,
                    name = "Social Media",
                    nameAr = "وسائل التواصل",
                    color = "#8B5CF6",
                    displayOrder = 3,
                ),
            )
        val referral =
            leadSourceRepository.save(
                LeadSource(
                    organizationId = org.id,
                    clubId = club.id,
                    name = "Referral",
                    nameAr = "إحالة",
                    color = "#F59E0B",
                    displayOrder = 4,
                ),
            )

        val salesAgent = staffByEmail["sales@elixir.com"]

        leadRepository.save(
            Lead(
                organizationId = org.id, clubId = club.id, branchId = riyadhBranch.id,
                leadSourceId = walkIn.id,
                assignedStaffId = salesAgent?.id,
                firstName = "Sara", lastName = "Al-Ghamdi",
                firstNameAr = "سارة", lastNameAr = "الغامدي",
                phone = "+966501234001",
                stage = "new",
            ),
        )
        leadRepository.save(
            Lead(
                organizationId = org.id, clubId = club.id, branchId = riyadhBranch.id,
                leadSourceId = socialMedia.id,
                assignedStaffId = salesAgent?.id,
                firstName = "Fatima", lastName = "Al-Zahrani",
                firstNameAr = "فاطمة", lastNameAr = "الزهراني",
                phone = "+966501234002",
                stage = "contacted",
                contactedAt = Instant.now(),
            ),
        )
        leadRepository.save(
            Lead(
                organizationId = org.id, clubId = club.id, branchId = riyadhBranch.id,
                leadSourceId = referral.id,
                firstName = "Reem", lastName = "Al-Dosari",
                firstNameAr = "ريم", lastNameAr = "الدوسري",
                phone = "+966501234003",
                stage = "interested",
                contactedAt = Instant.now(),
                interestedAt = Instant.now(),
            ),
        )
    }
}
