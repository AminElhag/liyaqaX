package com.liyaqa.lead

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
import com.liyaqa.lead.dto.ConvertLeadRequest
import com.liyaqa.lead.dto.CreateLeadNoteRequest
import com.liyaqa.lead.dto.CreateLeadRequest
import com.liyaqa.lead.dto.LeadBranchSummary
import com.liyaqa.lead.dto.LeadNoteResponse
import com.liyaqa.lead.dto.LeadResponse
import com.liyaqa.lead.dto.LeadSourceSummary
import com.liyaqa.lead.dto.LeadStaffSummary
import com.liyaqa.lead.dto.LeadSummaryResponse
import com.liyaqa.lead.dto.StageTransitionRequest
import com.liyaqa.lead.dto.UpdateLeadRequest
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class LeadService(
    private val leadRepository: LeadRepository,
    private val leadNoteRepository: LeadNoteRepository,
    private val leadSourceRepository: LeadSourceRepository,
    private val memberRepository: MemberRepository,
    private val staffMemberRepository: StaffMemberRepository,
    private val branchRepository: BranchRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val auditService: AuditService,
) {
    companion object {
        private val STAGE_ORDER =
            mapOf(
                "new" to 0,
                "contacted" to 1,
                "interested" to 2,
                "converted" to 3,
                "lost" to 4,
            )
    }

    @Transactional
    fun createLead(
        orgPublicId: UUID,
        clubPublicId: UUID,
        staffPublicId: UUID,
        request: CreateLeadRequest,
    ): LeadResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        // Rule 1 — phone or email required
        if (request.phone.isNullOrBlank() && request.email.isNullOrBlank()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "At least one of phone or email must be provided.",
            )
        }

        // Rule 3 — active source only
        var sourceId: Long? = null
        if (request.leadSourceId != null) {
            val source =
                leadSourceRepository
                    .findByPublicIdAndClubIdAndDeletedAtIsNull(request.leadSourceId, club.id)
                    .orElseThrow {
                        ArenaException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "business-rule-violation",
                            "Lead source not found or does not belong to this club.",
                        )
                    }
            if (!source.isActive) {
                throw ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Lead source is inactive.",
                )
            }
            sourceId = source.id
        }

        // Rule 9 — staff assignment scope
        var assignedStaffId: Long? = null
        if (request.assignedStaffId != null) {
            val staff = findStaffInClubOrThrow(request.assignedStaffId, club.id)
            assignedStaffId = staff.id
        }

        var branchId: Long? = null
        if (request.branchId != null) {
            val branch = findBranchInClubOrThrow(request.branchId, org.id, club.id)
            branchId = branch.id
        }

        val lead =
            leadRepository.save(
                Lead(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branchId,
                    leadSourceId = sourceId,
                    assignedStaffId = assignedStaffId,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    firstNameAr = request.firstNameAr,
                    lastNameAr = request.lastNameAr,
                    phone = request.phone,
                    email = request.email,
                    gender = request.gender,
                    notes = request.notes,
                    stage = "new",
                ),
            )

        // If initial notes provided, also create a LeadNote
        if (!request.notes.isNullOrBlank()) {
            val creatorStaff = findStaffByUserPublicIdOrThrow(staffPublicId, club.id)
            leadNoteRepository.save(
                LeadNote(
                    leadId = lead.id,
                    staffId = creatorStaff.id,
                    body = request.notes,
                ),
            )
        }

        auditService.logFromContext(
            action = AuditAction.LEAD_CREATED,
            entityType = "Lead",
            entityId = lead.publicId.toString(),
        )

        return hydrateLeadResponse(lead, club.id)
    }

    fun getLead(
        orgPublicId: UUID,
        clubPublicId: UUID,
        leadPublicId: UUID,
    ): LeadResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val lead = findLeadOrThrow(leadPublicId, club.id)
        return hydrateLeadResponse(lead, club.id)
    }

    fun listLeads(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<LeadSummaryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        val leadPage = leadRepository.findAllByClubIdAndDeletedAtIsNull(club.id, pageable)

        val sourceIds = leadPage.content.mapNotNull { it.leadSourceId }.toSet()
        val sourcesById =
            if (sourceIds.isNotEmpty()) {
                leadSourceRepository.findAllById(sourceIds).associateBy { it.id }
            } else {
                emptyMap()
            }

        val staffIds = leadPage.content.mapNotNull { it.assignedStaffId }.toSet()
        val staffById =
            if (staffIds.isNotEmpty()) {
                staffMemberRepository.findAllById(staffIds).associateBy { it.id }
            } else {
                emptyMap()
            }

        return leadPage.map { lead ->
            lead.toSummaryResponse(
                source = lead.leadSourceId?.let { sourcesById[it] },
                staff = lead.assignedStaffId?.let { staffById[it] },
            )
        }.toPageResponse()
    }

    @Transactional
    fun updateLead(
        orgPublicId: UUID,
        clubPublicId: UUID,
        leadPublicId: UUID,
        request: UpdateLeadRequest,
    ): LeadResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val lead = findLeadOrThrow(leadPublicId, club.id)

        // Rule 6 — converted lead is immutable
        ensureNotConverted(lead)

        request.firstName?.let { lead.firstName = it }
        request.lastName?.let { lead.lastName = it }
        request.firstNameAr?.let { lead.firstNameAr = it }
        request.lastNameAr?.let { lead.lastNameAr = it }
        request.phone?.let { lead.phone = it }
        request.email?.let { lead.email = it }
        request.gender?.let { lead.gender = it }
        request.notes?.let { lead.notes = it }

        // Rule 3 — active source only
        if (request.leadSourceId != null) {
            val source =
                leadSourceRepository
                    .findByPublicIdAndClubIdAndDeletedAtIsNull(request.leadSourceId, club.id)
                    .orElseThrow {
                        ArenaException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "business-rule-violation",
                            "Lead source not found or does not belong to this club.",
                        )
                    }
            if (!source.isActive) {
                throw ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Lead source is inactive.",
                )
            }
            lead.leadSourceId = source.id
        }

        // Rule 9 — staff assignment scope
        if (request.assignedStaffId != null) {
            val staff = findStaffInClubOrThrow(request.assignedStaffId, club.id)
            lead.assignedStaffId = staff.id
        }

        if (request.branchId != null) {
            val branch = findBranchInClubOrThrow(request.branchId, org.id, club.id)
            lead.branchId = branch.id
        }

        leadRepository.save(lead)

        auditService.logFromContext(
            action = AuditAction.LEAD_UPDATED,
            entityType = "Lead",
            entityId = lead.publicId.toString(),
        )

        return hydrateLeadResponse(lead, club.id)
    }

    // Rule 4 — stage transitions forward only (except lost → new)
    @Transactional
    fun moveStage(
        orgPublicId: UUID,
        clubPublicId: UUID,
        leadPublicId: UUID,
        request: StageTransitionRequest,
    ): LeadResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val lead = findLeadOrThrow(leadPublicId, club.id)

        // Rule 6 — converted lead is immutable
        ensureNotConverted(lead)

        val currentOrder = STAGE_ORDER[lead.stage] ?: 0
        val targetOrder = STAGE_ORDER[request.stage] ?: 0

        // "lost" can be targeted from any non-converted stage (forward to terminal)
        val isForward = targetOrder > currentOrder || request.stage == "lost"
        val isReopenFromLost = lead.stage == "lost" && request.stage == "new"

        if (!isForward && !isReopenFromLost) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Invalid stage transition from '${lead.stage}' to '${request.stage}'.",
            )
        }

        // Rule 5 — lost requires reason
        if (request.stage == "lost" && request.lostReason.isNullOrBlank()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "A reason is required when marking a lead as lost.",
            )
        }

        lead.stage = request.stage
        when (request.stage) {
            "contacted" -> lead.contactedAt = lead.contactedAt ?: Instant.now()
            "interested" -> lead.interestedAt = lead.interestedAt ?: Instant.now()
            "lost" -> {
                lead.lostAt = Instant.now()
                lead.lostReason = request.lostReason
            }
            "new" -> {
                // Re-open from lost: clear lost fields
                lead.lostAt = null
                lead.lostReason = null
            }
        }

        leadRepository.save(lead)

        if (request.stage == "lost") {
            auditService.logFromContext(
                action = AuditAction.LEAD_LOST,
                entityType = "Lead",
                entityId = lead.publicId.toString(),
                changesJson = """{"lostReason":"${request.lostReason ?: ""}"}""",
            )
        }

        return hydrateLeadResponse(lead, club.id)
    }

    // Rule 8 — convert creates Member atomically
    @Transactional
    fun convertLead(
        orgPublicId: UUID,
        clubPublicId: UUID,
        leadPublicId: UUID,
        request: ConvertLeadRequest,
    ): LeadResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val lead = findLeadOrThrow(leadPublicId, club.id)

        // Rule 7 — convert idempotency
        if (lead.convertedMemberId != null) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Lead has already been converted.",
            )
        }

        // Rule 6 — converted lead is immutable (double-check)
        ensureNotConverted(lead)

        val branch = findBranchInClubOrThrow(request.branchId, org.id, club.id)

        // Create User account for the new member
        val email = lead.email ?: "lead-${lead.publicId}@placeholder.local"
        if (lead.email != null && userRepository.findByEmailAndDeletedAtIsNull(email).isPresent) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A user with email '${lead.email}' already exists.",
            )
        }

        val randomPassword = UUID.randomUUID().toString()
        val user =
            userRepository.save(
                User(
                    email = email,
                    passwordHash = passwordEncoder.encode(randomPassword),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        // Assign member role
        val memberRole =
            roleRepository
                .findByScopeAndOrganizationIdAndClubIdAndDeletedAtIsNull("member", org.id, club.id)
                .orElse(null)
        if (memberRole != null) {
            userRoleRepository.save(UserRole(userId = user.id, roleId = memberRole.id))
        }

        // Create Member from lead data
        val member =
            memberRepository.save(
                Member(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    userId = user.id,
                    firstNameAr = lead.firstNameAr ?: lead.firstName,
                    firstNameEn = lead.firstName,
                    lastNameAr = lead.lastNameAr ?: lead.lastName,
                    lastNameEn = lead.lastName,
                    phone = lead.phone ?: "",
                    gender = lead.gender,
                    notes = lead.notes,
                    membershipStatus = "pending",
                ),
            )

        // Update lead to converted state
        lead.stage = "converted"
        lead.convertedMemberId = member.id
        lead.convertedAt = Instant.now()
        leadRepository.save(lead)

        auditService.logFromContext(
            action = AuditAction.LEAD_CONVERTED,
            entityType = "Lead",
            entityId = lead.publicId.toString(),
            changesJson = """{"convertedMemberId":"${member.publicId}"}""",
        )

        return hydrateLeadResponse(lead, club.id)
    }

    // Rule 10 — notes are immutable (append-only)
    @Transactional
    fun addNote(
        orgPublicId: UUID,
        clubPublicId: UUID,
        leadPublicId: UUID,
        staffPublicId: UUID,
        request: CreateLeadNoteRequest,
    ): LeadNoteResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val lead = findLeadOrThrow(leadPublicId, club.id)
        val staff = findStaffByUserPublicIdOrThrow(staffPublicId, club.id)

        val note =
            leadNoteRepository.save(
                LeadNote(
                    leadId = lead.id,
                    staffId = staff.id,
                    body = request.body,
                ),
            )

        return note.toResponse(staff)
    }

    fun listNotes(
        orgPublicId: UUID,
        clubPublicId: UUID,
        leadPublicId: UUID,
    ): List<LeadNoteResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val lead = findLeadOrThrow(leadPublicId, club.id)

        val notes = leadNoteRepository.findAllByLeadIdOrderByCreatedAtAsc(lead.id)
        val staffIds = notes.map { it.staffId }.toSet()
        val staffById = staffMemberRepository.findAllById(staffIds).associateBy { it.id }

        return notes.map { note ->
            val staff =
                staffById[note.staffId]
                    ?: throw internalError("Staff member not found for note ${note.publicId}")
            note.toResponse(staff)
        }
    }

    @Transactional
    fun deleteLead(
        orgPublicId: UUID,
        clubPublicId: UUID,
        leadPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val lead = findLeadOrThrow(leadPublicId, club.id)

        // Rule 6 — converted lead is immutable
        ensureNotConverted(lead)

        lead.softDelete()
        leadRepository.save(lead)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun ensureNotConverted(lead: Lead) {
        if (lead.stage == "converted") {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Converted leads cannot be modified.",
            )
        }
    }

    private fun hydrateLeadResponse(
        lead: Lead,
        clubId: Long,
    ): LeadResponse {
        val source =
            lead.leadSourceId?.let {
                leadSourceRepository.findById(it).orElse(null)
            }
        val staff =
            lead.assignedStaffId?.let {
                staffMemberRepository.findById(it).orElse(null)
            }
        val branch =
            lead.branchId?.let {
                branchRepository.findById(it).orElse(null)
            }
        val convertedMemberPublicId =
            lead.convertedMemberId?.let {
                memberRepository.findById(it).orElse(null)?.publicId
            }

        return lead.toResponse(
            source = source,
            staff = staff,
            branch = branch,
            convertedMemberPublicId = convertedMemberPublicId,
        )
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

    private fun findLeadOrThrow(
        leadPublicId: UUID,
        clubId: Long,
    ): Lead =
        leadRepository
            .findByPublicIdAndClubIdAndDeletedAtIsNull(leadPublicId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Lead not found.")
            }

    private fun findStaffInClubOrThrow(
        staffPublicId: UUID,
        clubId: Long,
    ): StaffMember {
        val staff =
            staffMemberRepository.findByPublicIdAndDeletedAtIsNull(staffPublicId)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "business-rule-violation",
                        "Staff member not found.",
                    )
                }
        if (staff.clubId != clubId) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Staff member does not belong to this club.",
            )
        }
        return staff
    }

    private fun findStaffByUserPublicIdOrThrow(
        userPublicId: UUID,
        clubId: Long,
    ): StaffMember {
        val user =
            userRepository.findByPublicIdAndDeletedAtIsNull(userPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "User not found.")
                }
        return staffMemberRepository.findByUserIdAndClubIdAndDeletedAtIsNull(user.id, clubId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Current user is not a staff member for this club.",
                )
            }
    }

    private fun findBranchInClubOrThrow(
        branchPublicId: UUID,
        orgId: Long,
        clubId: Long,
    ): Branch =
        branchRepository
            .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branchPublicId, orgId, clubId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Branch not found or does not belong to this club.",
                )
            }

    private fun internalError(detail: String) = ArenaException(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", detail)

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private fun Lead.toResponse(
        source: LeadSource?,
        staff: StaffMember?,
        branch: Branch?,
        convertedMemberPublicId: UUID?,
    ) = LeadResponse(
        id = publicId,
        firstName = firstName,
        lastName = lastName,
        firstNameAr = firstNameAr,
        lastNameAr = lastNameAr,
        phone = phone,
        email = email,
        gender = gender,
        stage = stage,
        lostReason = lostReason,
        leadSource = source?.toSummary(),
        assignedStaff = staff?.toStaffSummary(),
        branch = branch?.toBranchSummary(),
        convertedMemberId = convertedMemberPublicId,
        contactedAt = contactedAt,
        interestedAt = interestedAt,
        convertedAt = convertedAt,
        lostAt = lostAt,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Lead.toSummaryResponse(
        source: LeadSource?,
        staff: StaffMember?,
    ) = LeadSummaryResponse(
        id = publicId,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        stage = stage,
        leadSource = source?.toSummary(),
        assignedStaff = staff?.toStaffSummary(),
        createdAt = createdAt,
    )

    private fun LeadSource.toSummary() =
        LeadSourceSummary(
            id = publicId,
            name = name,
            nameAr = nameAr,
            color = color,
        )

    private fun StaffMember.toStaffSummary() =
        LeadStaffSummary(
            id = publicId,
            firstName = firstNameEn,
            lastName = lastNameEn,
        )

    private fun Branch.toBranchSummary() =
        LeadBranchSummary(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
        )

    private fun LeadNote.toResponse(staff: StaffMember) =
        LeadNoteResponse(
            id = publicId,
            body = body,
            staff = staff.toStaffSummary(),
            createdAt = createdAt,
        )
}
