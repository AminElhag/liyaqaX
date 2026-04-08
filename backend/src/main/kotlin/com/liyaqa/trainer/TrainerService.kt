package com.liyaqa.trainer

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
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
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.trainer.dto.CreateTrainerRequest
import com.liyaqa.trainer.dto.TrainerBranchResponse
import com.liyaqa.trainer.dto.TrainerCertificationResponse
import com.liyaqa.trainer.dto.TrainerResponse
import com.liyaqa.trainer.dto.TrainerSpecializationResponse
import com.liyaqa.trainer.dto.TrainerSummaryResponse
import com.liyaqa.trainer.dto.UpdateTrainerRequest
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
class TrainerService(
    private val trainerRepository: TrainerRepository,
    private val trainerBranchAssignmentRepository: TrainerBranchAssignmentRepository,
    private val trainerCertificationRepository: TrainerCertificationRepository,
    private val trainerSpecializationRepository: TrainerSpecializationRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val branchRepository: BranchRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val staffMemberRepository: StaffMemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditService: AuditService,
) {
    @Transactional
    fun create(
        orgPublicId: UUID,
        clubPublicId: UUID,
        callerUserPublicId: UUID,
        request: CreateTrainerRequest,
    ): TrainerResponse {
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

        // Rule 2 — minimum one trainerType, must be "pt" or "gx"
        validateTrainerTypes(request.trainerTypes)

        // Rule 3 — branch scope validation: branches must belong to the club
        val branches = validateAndLoadBranches(request.branchIds, org.id, club.id)

        // Rule 4 — minimum one branch (covered by @NotEmpty on DTO, enforced here as well)
        if (branches.isEmpty()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "A trainer must be assigned to at least one branch.",
            )
        }

        // Create the User account
        val user =
            userRepository.save(
                User(
                    email = request.email,
                    passwordHash = passwordEncoder.encode(request.password),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        // Rule 7 — a user cannot hold both StaffMember and Trainer in the same club
        if (staffMemberRepository.existsByUserIdAndDeletedAtIsNull(user.id)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "This user already has a staff profile. A user cannot be both staff and trainer in the same club.",
            )
        }

        // Assign trainer role(s) based on trainerTypes
        val trainerRoles = resolveTrainerRoles(request.trainerTypes, org.id, club.id)
        trainerRoles.forEach { role ->
            userRoleRepository.save(UserRole(userId = user.id, roleId = role.id))
        }

        val trainer =
            trainerRepository.save(
                Trainer(
                    organizationId = org.id,
                    clubId = club.id,
                    userId = user.id,
                    firstNameAr = request.firstNameAr,
                    firstNameEn = request.firstNameEn,
                    lastNameAr = request.lastNameAr,
                    lastNameEn = request.lastNameEn,
                    phone = request.phone,
                    nationalId = request.nationalId,
                    bioAr = request.bioAr,
                    bioEn = request.bioEn,
                    joinedAt = request.joinedAt,
                ),
            )

        trainerBranchAssignmentRepository.saveAll(
            branches.map {
                TrainerBranchAssignment(
                    trainerId = trainer.id,
                    branchId = it.id,
                    organizationId = org.id,
                )
            },
        )

        auditService.logFromContext(
            action = AuditAction.TRAINER_CREATED,
            entityType = "Trainer",
            entityId = trainer.publicId.toString(),
        )

        return trainer.toResponse(
            user = user,
            trainerTypes = request.trainerTypes,
            branches = branches,
            certifications = emptyList(),
            specializations = emptyList(),
            orgPublicId = org.publicId,
            clubPublicId = club.publicId,
        )
    }

    fun getByPublicId(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
    ): TrainerResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)
        return buildFullResponse(trainer, org, club)
    }

    fun getAll(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<TrainerSummaryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainerPage =
            trainerRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(org.id, club.id, pageable)

        val usersById = userRepository.findAllById(trainerPage.map { it.userId }).associateBy { it.id }

        return trainerPage
            .map { trainer ->
                val user = usersById[trainer.userId] ?: throw internalError("User missing for trainer ${trainer.publicId}")
                val types = resolveTrainerTypesForUser(trainer.userId)
                trainer.toSummaryResponse(user, types)
            }
            .toPageResponse()
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
        request: UpdateTrainerRequest,
    ): TrainerResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)

        request.firstNameAr?.let { trainer.firstNameAr = it }
        request.firstNameEn?.let { trainer.firstNameEn = it }
        request.lastNameAr?.let { trainer.lastNameAr = it }
        request.lastNameEn?.let { trainer.lastNameEn = it }
        request.phone?.let { trainer.phone = it }
        request.nationalId?.let { trainer.nationalId = it }
        request.bioAr?.let { trainer.bioAr = it }
        request.bioEn?.let { trainer.bioEn = it }
        request.isActive?.let { trainer.isActive = it }

        trainerRepository.save(trainer)

        auditService.logFromContext(
            action = AuditAction.TRAINER_UPDATED,
            entityType = "Trainer",
            entityId = trainer.publicId.toString(),
        )

        return buildFullResponse(trainer, org, club)
    }

    @Transactional
    fun delete(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
        callerUserPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)

        // Rule 6 — self-deletion prevention
        val callerUser =
            userRepository.findByPublicIdAndDeletedAtIsNull(callerUserPublicId)
                .orElseThrow { internalError("Caller user not found.") }
        if (trainer.userId == callerUser.id) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "You cannot delete your own trainer account.",
            )
        }

        // Rule 5 — soft delete cascade: deactivate the linked User
        val user = findUserByIdOrThrow(trainer.userId)
        user.isActive = false
        userRepository.save(user)

        trainer.softDelete()
        trainerRepository.save(trainer)

        auditService.logFromContext(
            action = AuditAction.TRAINER_DELETED,
            entityType = "Trainer",
            entityId = trainer.publicId.toString(),
        )
    }

    // ── Branch assignment ────────────────────────────────────────────────────

    @Transactional
    fun assignBranch(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
        branchPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)

        // Rule 3 — branch must belong to this club
        val branch = findBranchOrThrow(branchPublicId, org.id, club.id)

        if (trainerBranchAssignmentRepository.existsByTrainerIdAndBranchId(trainer.id, branch.id)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Trainer is already assigned to this branch.",
            )
        }

        trainerBranchAssignmentRepository.save(
            TrainerBranchAssignment(
                trainerId = trainer.id,
                branchId = branch.id,
                organizationId = org.id,
            ),
        )
    }

    @Transactional
    fun removeBranch(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
        branchPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)
        val branch = findBranchOrThrow(branchPublicId, org.id, club.id)

        if (!trainerBranchAssignmentRepository.existsByTrainerIdAndBranchId(trainer.id, branch.id)) {
            throw ArenaException(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "Trainer is not assigned to this branch.",
            )
        }

        // Rule 4 — minimum one branch
        if (trainerBranchAssignmentRepository.countByTrainerId(trainer.id) <= 1) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "A trainer must be assigned to at least one branch.",
            )
        }

        trainerBranchAssignmentRepository.deleteByTrainerIdAndBranchId(trainer.id, branch.id)
    }

    fun listBranches(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
    ): List<TrainerBranchResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)
        return loadBranchesForTrainer(trainer.id).map { it.toBranchResponse() }
    }

    // ── Certification approval/rejection ─────────────────────────────────────

    @Transactional
    fun approveCertification(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
        certPublicId: UUID,
    ): TrainerCertificationResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)
        val cert = findCertOrThrow(certPublicId, trainer.id)

        cert.status = "approved"
        cert.rejectionReason = null
        trainerCertificationRepository.save(cert)
        return cert.toResponse()
    }

    @Transactional
    fun rejectCertification(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
        certPublicId: UUID,
        reason: String?,
    ): TrainerCertificationResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)
        val cert = findCertOrThrow(certPublicId, trainer.id)

        cert.status = "rejected"
        cert.rejectionReason = reason
        trainerCertificationRepository.save(cert)
        return cert.toResponse()
    }

    fun listCertifications(
        orgPublicId: UUID,
        clubPublicId: UUID,
        trainerPublicId: UUID,
    ): List<TrainerCertificationResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val trainer = findTrainerOrThrow(trainerPublicId, org.id, club.id)
        return trainerCertificationRepository.findAllByTrainerIdAndDeletedAtIsNull(trainer.id)
            .map { it.toResponse() }
    }

    // ── Private validation helpers ───────────────────────────────────────────

    private fun validateTrainerTypes(types: List<String>) {
        val validTypes = setOf("pt", "gx")
        if (types.isEmpty()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "A trainer must have at least one trainer type (pt or gx).",
            )
        }
        val invalid = types.filter { it !in validTypes }
        if (invalid.isNotEmpty()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Invalid trainer type(s): ${invalid.joinToString()}. Allowed: pt, gx.",
            )
        }
    }

    private fun resolveTrainerRoles(
        trainerTypes: List<String>,
        organizationId: Long,
        clubId: Long,
    ): List<Role> {
        val clubTrainerRoles =
            roleRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(organizationId, clubId)
                .filter { it.scope == "trainer" }

        return trainerTypes.map { type ->
            val roleName =
                when (type) {
                    "pt" -> "PT Trainer"
                    "gx" -> "GX Instructor"
                    else -> throw internalError("Unknown trainer type: $type")
                }
            clubTrainerRoles.firstOrNull { it.nameEn == roleName }
                ?: throw ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Trainer role '$roleName' not found for this club.",
                )
        }
    }

    private fun resolveTrainerTypesForUser(userId: Long): List<String> {
        val userRoles = userRoleRepository.findAllByUserId(userId)
        val roles = roleRepository.findAllById(userRoles.map { it.roleId })
        return roles
            .filter { it.scope == "trainer" }
            .mapNotNull { role ->
                when {
                    role.nameEn.contains("PT", ignoreCase = true) -> "pt"
                    role.nameEn.contains("GX", ignoreCase = true) -> "gx"
                    else -> null
                }
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

    // ── Private lookup helpers ───────────────────────────────────────────────

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

    private fun findTrainerOrThrow(
        trainerPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Trainer =
        trainerRepository
            .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(trainerPublicId, organizationId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.")
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

    private fun findCertOrThrow(
        certPublicId: UUID,
        trainerId: Long,
    ): TrainerCertification =
        trainerCertificationRepository
            .findByPublicIdAndTrainerIdAndDeletedAtIsNull(certPublicId, trainerId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Certification not found.")
            }

    private fun findUserByIdOrThrow(userId: Long): User =
        userRepository
            .findById(userId)
            .orElseThrow { internalError("User not found for trainer.") }

    private fun loadBranchesForTrainer(trainerId: Long): List<Branch> {
        val assignments = trainerBranchAssignmentRepository.findAllByTrainerId(trainerId)
        return branchRepository.findAllById(assignments.map { it.branchId })
    }

    private fun internalError(detail: String) =
        ArenaException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            detail,
        )

    // ── Response builders ────────────────────────────────────────────────────

    private fun buildFullResponse(
        trainer: Trainer,
        org: Organization,
        club: Club,
    ): TrainerResponse {
        val user = findUserByIdOrThrow(trainer.userId)
        val types = resolveTrainerTypesForUser(trainer.userId)
        val branches = loadBranchesForTrainer(trainer.id)
        val certifications = trainerCertificationRepository.findAllByTrainerIdAndDeletedAtIsNull(trainer.id)
        val specializations = trainerSpecializationRepository.findAllByTrainerIdAndDeletedAtIsNull(trainer.id)
        return trainer.toResponse(user, types, branches, certifications, specializations, org.publicId, club.publicId)
    }

    private fun Trainer.toResponse(
        user: User,
        trainerTypes: List<String>,
        branches: List<Branch>,
        certifications: List<TrainerCertification>,
        specializations: List<TrainerSpecialization>,
        orgPublicId: UUID,
        clubPublicId: UUID,
    ) = TrainerResponse(
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
        bioAr = bioAr,
        bioEn = bioEn,
        trainerTypes = trainerTypes,
        branches = branches.map { it.toBranchResponse() },
        certifications = certifications.map { it.toResponse() },
        specializations = specializations.map { it.toSpecResponse() },
        isActive = isActive,
        joinedAt = joinedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Trainer.toSummaryResponse(
        user: User,
        trainerTypes: List<String>,
    ) = TrainerSummaryResponse(
        id = publicId,
        firstNameAr = firstNameAr,
        firstNameEn = firstNameEn,
        lastNameAr = lastNameAr,
        lastNameEn = lastNameEn,
        email = user.email,
        trainerTypes = trainerTypes,
        isActive = isActive,
    )

    private fun Branch.toBranchResponse() = TrainerBranchResponse(id = publicId, nameAr = nameAr, nameEn = nameEn)

    private fun TrainerCertification.toResponse() =
        TrainerCertificationResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            issuingBody = issuingBody,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            status = status,
            rejectionReason = rejectionReason,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun TrainerSpecialization.toSpecResponse() =
        TrainerSpecializationResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
        )
}
