package com.liyaqa.staff

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.dto.CreateStaffMemberRequest
import com.liyaqa.staff.dto.StaffBranchResponse
import com.liyaqa.staff.dto.StaffMemberResponse
import com.liyaqa.staff.dto.StaffMemberSummaryResponse
import com.liyaqa.staff.dto.StaffRoleSummary
import com.liyaqa.staff.dto.UpdateStaffMemberRequest
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StaffMemberService(
    private val staffMemberRepository: StaffMemberRepository,
    private val staffBranchAssignmentRepository: StaffBranchAssignmentRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val branchRepository: BranchRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val permissionService: PermissionService,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun create(
        orgPublicId: UUID,
        clubPublicId: UUID,
        callerUserPublicId: UUID,
        callerRolePublicId: UUID,
        callerScope: String,
        request: CreateStaffMemberRequest,
    ): StaffMemberResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        // Rule 1 — email uniqueness
        if (userRepository.findByEmailAndDeletedAtIsNull(request.email).isPresent) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A user with this email already exists.",
            )
        }

        // Rule 2 — role scope validation
        val targetRole = findRoleOrThrow(request.roleId)
        validateRoleScope(targetRole, org.id, club.id)

        // Rule 3 — permission elevation prevention
        if (callerScope != "platform") {
            validateNoPermissionElevation(callerRolePublicId, targetRole.publicId)
        }

        // Rule 4 — branch scope validation
        val branches = validateAndLoadBranches(request.branchIds, org.id, club.id)

        val user =
            userRepository.save(
                User(
                    email = request.email,
                    passwordHash = passwordEncoder.encode(request.password),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        userRoleRepository.save(UserRole(userId = user.id, roleId = targetRole.id))

        val staff =
            staffMemberRepository.save(
                StaffMember(
                    organizationId = org.id,
                    clubId = club.id,
                    userId = user.id,
                    roleId = targetRole.id,
                    firstNameAr = request.firstNameAr,
                    firstNameEn = request.firstNameEn,
                    lastNameAr = request.lastNameAr,
                    lastNameEn = request.lastNameEn,
                    phone = request.phone,
                    nationalId = request.nationalId,
                    employmentType = request.employmentType,
                    joinedAt = request.joinedAt,
                ),
            )

        staffBranchAssignmentRepository.saveAll(
            branches.map {
                StaffBranchAssignment(
                    staffMemberId = staff.id,
                    branchId = it.id,
                    organizationId = org.id,
                )
            },
        )

        return staff.toResponse(user, targetRole, branches, org.publicId, club.publicId)
    }

    fun getByPublicId(
        orgPublicId: UUID,
        clubPublicId: UUID,
        staffPublicId: UUID,
    ): StaffMemberResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val staff = findStaffOrThrow(staffPublicId, org.id, club.id)
        val user = findUserByIdOrThrow(staff.userId)
        val role = findRoleByIdOrThrow(staff.roleId)
        val branches = loadBranchesForStaff(staff.id)
        return staff.toResponse(user, role, branches, org.publicId, club.publicId)
    }

    fun getAll(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<StaffMemberSummaryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val staffPage =
            staffMemberRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(org.id, club.id, pageable)

        val usersById = userRepository.findAllById(staffPage.map { it.userId }).associateBy { it.id }
        val rolesById = roleRepository.findAllById(staffPage.map { it.roleId }).associateBy { it.id }

        return staffPage
            .map { staff ->
                val user = usersById[staff.userId] ?: throw internalError("User missing for staff ${staff.publicId}")
                val role = rolesById[staff.roleId] ?: throw internalError("Role missing for staff ${staff.publicId}")
                staff.toSummaryResponse(user, role)
            }
            .toPageResponse()
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        staffPublicId: UUID,
        callerRolePublicId: UUID,
        callerScope: String,
        request: UpdateStaffMemberRequest,
    ): StaffMemberResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val staff = findStaffOrThrow(staffPublicId, org.id, club.id)

        if (request.roleId != null) {
            val newRole = findRoleOrThrow(request.roleId)
            // Rule 2 — role scope validation on new role
            validateRoleScope(newRole, org.id, club.id)
            // Rule 3 — permission elevation prevention on new role
            if (callerScope != "platform") {
                validateNoPermissionElevation(callerRolePublicId, newRole.publicId)
            }
            // Update role on staff entity and user_roles
            staff.roleId = newRole.id
            userRoleRepository.deleteByUserId(staff.userId)
            userRoleRepository.save(UserRole(userId = staff.userId, roleId = newRole.id))
        }

        request.firstNameAr?.let { staff.firstNameAr = it }
        request.firstNameEn?.let { staff.firstNameEn = it }
        request.lastNameAr?.let { staff.lastNameAr = it }
        request.lastNameEn?.let { staff.lastNameEn = it }
        request.phone?.let { staff.phone = it }
        request.nationalId?.let { staff.nationalId = it }
        request.employmentType?.let { staff.employmentType = it }
        request.isActive?.let { staff.isActive = it }

        staffMemberRepository.save(staff)

        val user = findUserByIdOrThrow(staff.userId)
        val role = findRoleByIdOrThrow(staff.roleId)
        val branches = loadBranchesForStaff(staff.id)
        return staff.toResponse(user, role, branches, org.publicId, club.publicId)
    }

    @Transactional
    fun delete(
        orgPublicId: UUID,
        clubPublicId: UUID,
        staffPublicId: UUID,
        callerUserPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val staff = findStaffOrThrow(staffPublicId, org.id, club.id)

        // Rule 7 — self-deletion prevention
        val callerUser =
            userRepository.findByPublicIdAndDeletedAtIsNull(callerUserPublicId)
                .orElseThrow { internalError("Caller user not found.") }
        if (staff.userId == callerUser.id) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "You cannot delete your own staff account.",
            )
        }

        // Rule 6 — soft delete cascade: deactivate the linked User
        val user = findUserByIdOrThrow(staff.userId)
        user.isActive = false
        userRepository.save(user)

        staff.softDelete()
        staffMemberRepository.save(staff)
    }

    @Transactional
    fun assignBranch(
        orgPublicId: UUID,
        clubPublicId: UUID,
        staffPublicId: UUID,
        branchPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val staff = findStaffOrThrow(staffPublicId, org.id, club.id)

        // Rule 4 — branch must belong to this club
        val branch = findBranchOrThrow(branchPublicId, org.id, club.id)

        if (staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(staff.id, branch.id)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Staff member is already assigned to this branch.",
            )
        }

        staffBranchAssignmentRepository.save(
            StaffBranchAssignment(
                staffMemberId = staff.id,
                branchId = branch.id,
                organizationId = org.id,
            ),
        )
    }

    @Transactional
    fun removeBranch(
        orgPublicId: UUID,
        clubPublicId: UUID,
        staffPublicId: UUID,
        branchPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val staff = findStaffOrThrow(staffPublicId, org.id, club.id)
        val branch = findBranchOrThrow(branchPublicId, org.id, club.id)

        if (!staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(staff.id, branch.id)) {
            throw ArenaException(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "Staff member is not assigned to this branch.",
            )
        }

        // Rule 5 — minimum one branch
        if (staffBranchAssignmentRepository.countByStaffMemberId(staff.id) <= 1) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "A staff member must be assigned to at least one branch.",
            )
        }

        staffBranchAssignmentRepository.deleteByStaffMemberIdAndBranchId(staff.id, branch.id)
    }

    fun listBranches(
        orgPublicId: UUID,
        clubPublicId: UUID,
        staffPublicId: UUID,
    ): List<StaffBranchResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val staff = findStaffOrThrow(staffPublicId, org.id, club.id)
        return loadBranchesForStaff(staff.id).map { it.toStaffBranchResponse() }
    }

    // ── Private validation helpers ────────────────────────────────────────────

    private fun validateRoleScope(
        role: Role,
        organizationId: Long,
        clubId: Long,
    ) {
        if (role.scope != "club" || role.organizationId != organizationId || role.clubId != clubId) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "The assigned role must be a club-scoped role belonging to this club.",
            )
        }
    }

    private fun validateNoPermissionElevation(
        callerRolePublicId: UUID,
        targetRolePublicId: UUID,
    ) {
        val callerPermissions = permissionService.getPermissions(callerRolePublicId)
        val targetPermissions = permissionService.getPermissions(targetRolePublicId)
        if (!callerPermissions.containsAll(targetPermissions)) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Cannot assign a role whose permissions exceed your own.",
            )
        }
    }

    private fun validateAndLoadBranches(
        branchPublicIds: List<UUID>,
        organizationId: Long,
        clubId: Long,
    ): List<Branch> =
        branchPublicIds.map { branchPublicId ->
            branchRepository
                .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branchPublicId, organizationId, clubId)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "business-rule-violation",
                        "Branch $branchPublicId not found or does not belong to this club.",
                    )
                }
        }

    private fun loadBranchesForStaff(staffMemberId: Long): List<Branch> {
        val assignments = staffBranchAssignmentRepository.findAllByStaffMemberId(staffMemberId)
        return branchRepository.findAllById(assignments.map { it.branchId })
    }

    // ── Private lookup helpers ────────────────────────────────────────────────

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository
            .findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.")
            }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ): Club =
        clubRepository
            .findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
            }

    private fun findStaffOrThrow(
        staffPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): StaffMember =
        staffMemberRepository
            .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(staffPublicId, organizationId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Staff member not found.")
            }

    private fun findBranchOrThrow(
        branchPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Branch =
        branchRepository
            .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branchPublicId, organizationId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.")
            }

    private fun findRoleOrThrow(rolePublicId: UUID): Role =
        roleRepository
            .findByPublicIdAndDeletedAtIsNull(rolePublicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Role not found.")
            }

    private fun findUserByIdOrThrow(userId: Long): User =
        userRepository
            .findById(userId)
            .orElseThrow { internalError("User not found for staff member.") }

    private fun findRoleByIdOrThrow(roleId: Long): Role =
        roleRepository
            .findById(roleId)
            .orElseThrow { internalError("Role not found for staff member.") }

    private fun internalError(detail: String) =
        ArenaException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            detail,
        )

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun StaffMember.toResponse(
        user: User,
        role: Role,
        branches: List<Branch>,
        orgPublicId: UUID,
        clubPublicId: UUID,
    ) = StaffMemberResponse(
        id = publicId,
        userId = user.publicId,
        organizationId = orgPublicId,
        clubId = clubPublicId,
        firstNameAr = firstNameAr,
        firstNameEn = firstNameEn,
        lastNameAr = lastNameAr,
        lastNameEn = lastNameEn,
        email = user.email,
        phone = phone,
        nationalId = nationalId,
        role = StaffRoleSummary(id = role.publicId, nameAr = role.nameAr, nameEn = role.nameEn),
        branches = branches.map { it.toStaffBranchResponse() },
        employmentType = employmentType,
        joinedAt = joinedAt,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun StaffMember.toSummaryResponse(
        user: User,
        role: Role,
    ) = StaffMemberSummaryResponse(
        id = publicId,
        firstNameAr = firstNameAr,
        firstNameEn = firstNameEn,
        lastNameAr = lastNameAr,
        lastNameEn = lastNameEn,
        email = user.email,
        role = StaffRoleSummary(id = role.publicId, nameAr = role.nameAr, nameEn = role.nameEn),
        isActive = isActive,
    )

    private fun Branch.toStaffBranchResponse() = StaffBranchResponse(id = publicId, nameAr = nameAr, nameEn = nameEn)
}
