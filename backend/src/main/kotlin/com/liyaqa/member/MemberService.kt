package com.liyaqa.member

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
import com.liyaqa.member.dto.CreateMemberRequest
import com.liyaqa.member.dto.EmergencyContactRequest
import com.liyaqa.member.dto.EmergencyContactResponse
import com.liyaqa.member.dto.MemberBranchResponse
import com.liyaqa.member.dto.MemberResponse
import com.liyaqa.member.dto.MemberSummaryResponse
import com.liyaqa.member.dto.UpdateMemberRequest
import com.liyaqa.member.dto.WaiverStatusResponse
import com.liyaqa.notification.events.MemberCreatedEvent
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.trainer.TrainerRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val healthWaiverRepository: HealthWaiverRepository,
    private val waiverSignatureRepository: WaiverSignatureRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val branchRepository: BranchRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val staffMemberRepository: StaffMemberRepository,
    private val trainerRepository: TrainerRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditService: AuditService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun create(
        orgPublicId: UUID,
        clubPublicId: UUID,
        request: CreateMemberRequest,
    ): MemberResponse {
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

        // Rule 2 — branch must belong to this club
        val branch = findBranchOrThrow(request.branchId, org.id, club.id)

        // Create User account
        val user =
            userRepository.save(
                User(
                    email = request.email,
                    passwordHash = passwordEncoder.encode(request.password),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        // Rule 4 — single user type: cannot be StaffMember or Trainer
        if (staffMemberRepository.existsByUserIdAndDeletedAtIsNull(user.id)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "This user already has a staff member profile in this club.",
            )
        }
        if (trainerRepository.existsByUserIdAndDeletedAtIsNull(user.id)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "This user already has a trainer profile in this club.",
            )
        }

        // Assign member role
        val memberRole =
            roleRepository.findByScopeAndOrganizationIdAndClubIdAndDeletedAtIsNull("member", org.id, club.id)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "internal-error",
                        "Member role not found for this club.",
                    )
                }
        userRoleRepository.save(UserRole(userId = user.id, roleId = memberRole.id))

        // Rule 6 — status always "pending" on creation
        val member =
            memberRepository.save(
                Member(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    userId = user.id,
                    firstNameAr = request.firstNameAr,
                    firstNameEn = request.firstNameEn,
                    lastNameAr = request.lastNameAr,
                    lastNameEn = request.lastNameEn,
                    phone = request.phone,
                    nationalId = request.nationalId,
                    dateOfBirth = request.dateOfBirth,
                    gender = request.gender,
                    membershipStatus = "pending",
                    notes = request.notes,
                ),
            )

        // Rule 3 — emergency contact required at registration
        val contact =
            emergencyContactRepository.save(
                EmergencyContact(
                    memberId = member.id,
                    organizationId = org.id,
                    nameAr = request.emergencyContact.nameAr,
                    nameEn = request.emergencyContact.nameEn,
                    phone = request.emergencyContact.phone,
                    relationship = request.emergencyContact.relationship,
                ),
            )

        val hasSignedWaiver = checkWaiverSigned(member.id, club.id)

        auditService.logFromContext(
            action = AuditAction.MEMBER_CREATED,
            entityType = "Member",
            entityId = member.publicId.toString(),
        )

        eventPublisher.publishEvent(
            MemberCreatedEvent(
                memberPublicId = member.publicId,
                memberName = "${member.firstNameEn} ${member.lastNameEn}",
                branchId = member.branchId,
                clubId = member.clubId,
            ),
        )

        return member.toResponse(
            user = user,
            branch = branch,
            orgPublicId = org.publicId,
            clubPublicId = club.publicId,
            emergencyContacts = listOf(contact),
            hasSignedWaiver = hasSignedWaiver,
        )
    }

    fun getByPublicId(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
    ): MemberResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        val user = findUserByIdOrThrow(member.userId)
        val branch = findBranchByIdOrThrow(member.branchId)
        val contacts = emergencyContactRepository.findAllByMemberIdAndOrganizationId(member.id, org.id)
        val hasSignedWaiver = checkWaiverSigned(member.id, club.id)

        return member.toResponse(
            user = user,
            branch = branch,
            orgPublicId = org.publicId,
            clubPublicId = club.publicId,
            emergencyContacts = contacts,
            hasSignedWaiver = hasSignedWaiver,
        )
    }

    fun getAll(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<MemberSummaryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val memberPage =
            memberRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(org.id, club.id, pageable)

        val branchIds = memberPage.map { it.branchId }.toSet()
        val branchesById = branchRepository.findAllById(branchIds).associateBy { it.id }
        val userIds = memberPage.map { it.userId }.toSet()
        val usersById = userRepository.findAllById(userIds).associateBy { it.id }

        return memberPage
            .map { member ->
                val branch =
                    branchesById[member.branchId]
                        ?: throw internalError("Branch missing for member ${member.publicId}")
                val user =
                    usersById[member.userId]
                        ?: throw internalError("User missing for member ${member.publicId}")
                member.toSummaryResponse(user, branch)
            }
            .toPageResponse()
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        request: UpdateMemberRequest,
    ): MemberResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        request.firstNameAr?.let { member.firstNameAr = it }
        request.firstNameEn?.let { member.firstNameEn = it }
        request.lastNameAr?.let { member.lastNameAr = it }
        request.lastNameEn?.let { member.lastNameEn = it }
        request.phone?.let { member.phone = it }
        request.nationalId?.let { member.nationalId = it }
        request.dateOfBirth?.let { member.dateOfBirth = it }
        request.gender?.let { member.gender = it }
        request.notes?.let { member.notes = it }

        memberRepository.save(member)

        auditService.logFromContext(
            action = AuditAction.MEMBER_UPDATED,
            entityType = "Member",
            entityId = member.publicId.toString(),
        )

        val user = findUserByIdOrThrow(member.userId)
        val branch = findBranchByIdOrThrow(member.branchId)
        val contacts = emergencyContactRepository.findAllByMemberIdAndOrganizationId(member.id, org.id)
        val hasSignedWaiver = checkWaiverSigned(member.id, club.id)

        return member.toResponse(
            user = user,
            branch = branch,
            orgPublicId = org.publicId,
            clubPublicId = club.publicId,
            emergencyContacts = contacts,
            hasSignedWaiver = hasSignedWaiver,
        )
    }

    // Rule 5 — soft delete sets deleted_at and deactivates User
    @Transactional
    fun delete(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        val user = findUserByIdOrThrow(member.userId)
        user.isActive = false
        userRepository.save(user)

        member.softDelete()
        memberRepository.save(member)

        auditService.logFromContext(
            action = AuditAction.MEMBER_DELETED,
            entityType = "Member",
            entityId = member.publicId.toString(),
        )
    }

    // ── Emergency contact operations ─────────────────────────────────────────

    fun listEmergencyContacts(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
    ): List<EmergencyContactResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        return emergencyContactRepository
            .findAllByMemberIdAndOrganizationId(member.id, org.id)
            .map { it.toResponse() }
    }

    @Transactional
    fun addEmergencyContact(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        request: EmergencyContactRequest,
    ): EmergencyContactResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        val contact =
            emergencyContactRepository.save(
                EmergencyContact(
                    memberId = member.id,
                    organizationId = org.id,
                    nameAr = request.nameAr,
                    nameEn = request.nameEn,
                    phone = request.phone,
                    relationship = request.relationship,
                ),
            )
        return contact.toResponse()
    }

    @Transactional
    fun deleteEmergencyContact(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        contactPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        val contact =
            emergencyContactRepository.findByPublicIdAndOrganizationId(contactPublicId, org.id)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Emergency contact not found.")
                }

        if (contact.memberId != member.id) {
            throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Emergency contact not found.")
        }

        // Rule 3 — must keep at least one emergency contact
        val contactCount = emergencyContactRepository.findAllByMemberIdAndOrganizationId(member.id, org.id).size
        if (contactCount <= 1) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "A member must have at least one emergency contact.",
            )
        }

        emergencyContactRepository.delete(contact)
    }

    // ── Waiver operations ────────────────────────────────────────────────────

    fun getWaiverStatus(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
    ): WaiverStatusResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        val activeWaiver =
            healthWaiverRepository.findByClubIdAndIsActiveTrueAndDeletedAtIsNull(club.id).orElse(null)
                ?: return WaiverStatusResponse(
                    hasSignedCurrentWaiver = false,
                    waiverId = null,
                    waiverVersion = null,
                    signedAt = null,
                )

        val signature = waiverSignatureRepository.findByMemberIdAndWaiverId(member.id, activeWaiver.id).orElse(null)

        return WaiverStatusResponse(
            hasSignedCurrentWaiver = signature != null,
            waiverId = activeWaiver.publicId,
            waiverVersion = activeWaiver.version,
            signedAt = signature?.signedAt,
        )
    }

    // Rule 7 — staff can record waiver signature on behalf of member
    @Transactional
    fun signWaiver(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        ipAddress: String?,
    ): WaiverStatusResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        val activeWaiver =
            healthWaiverRepository.findByClubIdAndIsActiveTrueAndDeletedAtIsNull(club.id)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.NOT_FOUND,
                        "resource-not-found",
                        "No active health waiver found for this club.",
                    )
                }

        if (waiverSignatureRepository.existsByMemberIdAndWaiverId(member.id, activeWaiver.id)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Member has already signed the current waiver.",
            )
        }

        val signature =
            waiverSignatureRepository.save(
                WaiverSignature(
                    memberId = member.id,
                    waiverId = activeWaiver.id,
                    organizationId = org.id,
                    ipAddress = ipAddress,
                ),
            )

        return WaiverStatusResponse(
            hasSignedCurrentWaiver = true,
            waiverId = activeWaiver.publicId,
            waiverVersion = activeWaiver.version,
            signedAt = signature.signedAt,
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun checkWaiverSigned(
        memberId: Long,
        clubId: Long,
    ): Boolean {
        val activeWaiver =
            healthWaiverRepository.findByClubIdAndIsActiveTrueAndDeletedAtIsNull(clubId).orElse(null)
                ?: return false
        return waiverSignatureRepository.existsByMemberIdAndWaiverId(memberId, activeWaiver.id)
    }

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

    private fun findMemberOrThrow(
        memberPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Member =
        memberRepository
            .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberPublicId, organizationId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.")
            }

    private fun findBranchOrThrow(
        branchPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Branch =
        branchRepository
            .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branchPublicId, organizationId, clubId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Branch not found or does not belong to this club.",
                )
            }

    private fun findBranchByIdOrThrow(branchId: Long): Branch =
        branchRepository
            .findById(branchId)
            .orElseThrow { internalError("Branch not found.") }

    private fun findUserByIdOrThrow(userId: Long): User =
        userRepository
            .findById(userId)
            .orElseThrow { internalError("User not found for member.") }

    private fun internalError(detail: String) = ArenaException(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", detail)

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private fun Member.toResponse(
        user: User,
        branch: Branch,
        orgPublicId: UUID,
        clubPublicId: UUID,
        emergencyContacts: List<EmergencyContact>,
        hasSignedWaiver: Boolean,
    ) = MemberResponse(
        id = publicId,
        userId = user.publicId,
        organizationId = orgPublicId,
        clubId = clubPublicId,
        branch = branch.toBranchResponse(),
        firstNameAr = firstNameAr,
        firstNameEn = firstNameEn,
        lastNameAr = lastNameAr,
        lastNameEn = lastNameEn,
        email = user.email,
        phone = phone,
        nationalId = nationalId,
        dateOfBirth = dateOfBirth,
        gender = gender,
        membershipStatus = membershipStatus,
        notes = notes,
        joinedAt = joinedAt,
        emergencyContacts = emergencyContacts.map { it.toResponse() },
        hasSignedWaiver = hasSignedWaiver,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Member.toSummaryResponse(
        user: User,
        branch: Branch,
    ) = MemberSummaryResponse(
        id = publicId,
        firstNameAr = firstNameAr,
        firstNameEn = firstNameEn,
        lastNameAr = lastNameAr,
        lastNameEn = lastNameEn,
        email = user.email,
        phone = phone,
        membershipStatus = membershipStatus,
        branch = branch.toBranchResponse(),
        joinedAt = joinedAt,
    )

    private fun EmergencyContact.toResponse() =
        EmergencyContactResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            phone = phone,
            relationship = relationship,
            createdAt = createdAt,
        )

    private fun Branch.toBranchResponse() = MemberBranchResponse(id = publicId, nameAr = nameAr, nameEn = nameEn)
}
