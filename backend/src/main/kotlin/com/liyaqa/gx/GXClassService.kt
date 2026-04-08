package com.liyaqa.gx

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.dto.CreateGXClassInstanceRequest
import com.liyaqa.gx.dto.CreateGXClassTypeRequest
import com.liyaqa.gx.dto.GXClassInstanceResponse
import com.liyaqa.gx.dto.GXClassTypeResponse
import com.liyaqa.gx.dto.GXClassTypeSummary
import com.liyaqa.gx.dto.GXInstructorSummary
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.trainer.Trainer
import com.liyaqa.trainer.TrainerRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class GXClassService(
    private val classTypeRepository: GXClassTypeRepository,
    private val classInstanceRepository: GXClassInstanceRepository,
    private val bookingRepository: GXBookingRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val trainerRepository: TrainerRepository,
    private val waitlistService: GXWaitlistService,
) {
    // ── Class Type CRUD ────────────────────────────────────────────────────

    @Transactional
    fun createClassType(
        orgPublicId: UUID,
        clubPublicId: UUID,
        request: CreateGXClassTypeRequest,
    ): GXClassTypeResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        val classType =
            classTypeRepository.save(
                GXClassType(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = request.nameAr,
                    nameEn = request.nameEn,
                    descriptionAr = request.descriptionAr,
                    descriptionEn = request.descriptionEn,
                    defaultDurationMinutes = request.defaultDurationMinutes,
                    defaultCapacity = request.defaultCapacity,
                    color = request.color,
                ),
            )

        return toClassTypeResponse(classType)
    }

    fun getClassType(
        orgPublicId: UUID,
        clubPublicId: UUID,
        classTypePublicId: UUID,
    ): GXClassTypeResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val classType = findClassTypeOrThrow(classTypePublicId, org.id, club.id)
        return toClassTypeResponse(classType)
    }

    fun listClassTypes(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<GXClassTypeResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        return classTypeRepository
            .findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(org.id, club.id, pageable)
            .map { toClassTypeResponse(it) }
            .toPageResponse()
    }

    @Transactional
    fun updateClassType(
        orgPublicId: UUID,
        clubPublicId: UUID,
        classTypePublicId: UUID,
        request: CreateGXClassTypeRequest,
    ): GXClassTypeResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val classType = findClassTypeOrThrow(classTypePublicId, org.id, club.id)

        classType.nameAr = request.nameAr
        classType.nameEn = request.nameEn
        classType.descriptionAr = request.descriptionAr
        classType.descriptionEn = request.descriptionEn
        classType.defaultDurationMinutes = request.defaultDurationMinutes
        classType.defaultCapacity = request.defaultCapacity
        classType.color = request.color

        return toClassTypeResponse(classTypeRepository.save(classType))
    }

    @Transactional
    fun deleteClassType(
        orgPublicId: UUID,
        clubPublicId: UUID,
        classTypePublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val classType = findClassTypeOrThrow(classTypePublicId, org.id, club.id)
        classType.softDelete()
        classTypeRepository.save(classType)
    }

    // ── Class Instance scheduling ──────────────────────────────────────────

    @Transactional
    fun createClassInstance(
        orgPublicId: UUID,
        clubPublicId: UUID,
        branchPublicId: UUID,
        request: CreateGXClassInstanceRequest,
    ): GXClassInstanceResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val branch = findBranchOrThrow(branchPublicId, org.id, club.id)
        val classType = findClassTypeOrThrow(request.classTypeId, org.id, club.id)
        val instructor = findInstructorOrThrow(request.instructorId, org.id, club.id)

        // Business rule 5 — Instructor must belong to the same club
        if (instructor.clubId != club.id) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Instructor does not belong to this club.",
            )
        }

        val durationMinutes = request.durationMinutes ?: classType.defaultDurationMinutes
        val capacity = request.capacity ?: classType.defaultCapacity
        val startAt = request.scheduledAt
        val endAt = startAt.plusSeconds(durationMinutes.toLong() * 60)

        // Business rule 10 — Instructor double-booking check
        if (classInstanceRepository.existsOverlappingInstance(instructor.id, startAt, endAt) > 0) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Instructor is already scheduled for another class at this time.",
            )
        }

        val instance =
            classInstanceRepository.save(
                GXClassInstance(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    classTypeId = classType.id,
                    instructorId = instructor.id,
                    scheduledAt = request.scheduledAt,
                    durationMinutes = durationMinutes,
                    capacity = capacity,
                    room = request.room,
                    notes = request.notes,
                ),
            )

        return toInstanceResponse(instance, classType, instructor)
    }

    fun getClassInstance(
        orgPublicId: UUID,
        instancePublicId: UUID,
    ): GXClassInstanceResponse {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)
        val classType = classTypeRepository.findById(instance.classTypeId).orElseThrow()
        val instructor = trainerRepository.findById(instance.instructorId).orElseThrow()
        return toInstanceResponse(instance, classType, instructor)
    }

    fun listClassInstances(
        orgPublicId: UUID,
        branchPublicId: UUID,
        from: Instant?,
        to: Instant?,
        pageable: Pageable,
    ): PageResponse<GXClassInstanceResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val branch =
            branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, org.id)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.") }

        val page =
            if (from != null && to != null) {
                classInstanceRepository.findAllByOrganizationIdAndBranchIdAndScheduledAtBetweenAndDeletedAtIsNull(
                    org.id,
                    branch.id,
                    from,
                    to,
                    pageable,
                )
            } else {
                classInstanceRepository.findAllByOrganizationIdAndBranchIdAndDeletedAtIsNull(
                    org.id,
                    branch.id,
                    pageable,
                )
            }

        return page
            .map { instance ->
                val classType = classTypeRepository.findById(instance.classTypeId).orElseThrow()
                val instructor = trainerRepository.findById(instance.instructorId).orElseThrow()
                toInstanceResponse(instance, classType, instructor)
            }
            .toPageResponse()
    }

    @Transactional
    fun updateClassInstance(
        orgPublicId: UUID,
        instancePublicId: UUID,
        request: CreateGXClassInstanceRequest,
    ): GXClassInstanceResponse {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)
        val club = clubRepository.findById(instance.clubId).orElseThrow()
        val classType = findClassTypeOrThrow(request.classTypeId, org.id, club.id)
        val instructor = findInstructorOrThrow(request.instructorId, org.id, club.id)

        val durationMinutes = request.durationMinutes ?: classType.defaultDurationMinutes
        val startAt = request.scheduledAt
        val endAt = startAt.plusSeconds(durationMinutes.toLong() * 60)

        // Business rule 10 — check overlap excluding self
        if (instructor.id != instance.instructorId ||
            startAt != instance.scheduledAt ||
            durationMinutes != instance.durationMinutes
        ) {
            if (classInstanceRepository.existsOverlappingInstance(instructor.id, startAt, endAt) > 0) {
                throw ArenaException(
                    HttpStatus.CONFLICT,
                    "conflict",
                    "Instructor is already scheduled for another class at this time.",
                )
            }
        }

        val oldCapacity = instance.capacity
        val newCapacity = request.capacity ?: classType.defaultCapacity

        instance.instructorId = instructor.id
        instance.scheduledAt = request.scheduledAt
        instance.durationMinutes = durationMinutes
        instance.capacity = newCapacity
        instance.room = request.room
        instance.notes = request.notes

        val saved = classInstanceRepository.save(instance)

        // Rule 12 — capacity increased: promote waitlist entries for each new spot
        if (newCapacity > oldCapacity) {
            val newSpots = newCapacity - instance.bookingsCount
            if (newSpots > 0) {
                val spotsToPromote = newCapacity - oldCapacity
                repeat(spotsToPromote.coerceAtMost(newSpots)) {
                    waitlistService.promoteNext(instance.id)
                }
            }
        }

        return toInstanceResponse(saved, classType, instructor)
    }

    @Transactional
    fun cancelClassInstance(
        orgPublicId: UUID,
        instancePublicId: UUID,
    ): GXClassInstanceResponse {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)

        if (instance.instanceStatus == "cancelled") {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "This class is already cancelled.",
            )
        }

        instance.instanceStatus = "cancelled"

        // Business rule 9 — cascade cancel all bookings
        val activeBookings =
            bookingRepository.findAllByInstanceIdAndBookingStatusIn(
                instance.id,
                listOf("confirmed", "waitlist", "promoted"),
            )
        for (booking in activeBookings) {
            booking.bookingStatus = "cancelled"
            booking.cancelledAt = Instant.now()
            booking.cancellationReason = "Class cancelled"
        }
        bookingRepository.saveAll(activeBookings)
        instance.bookingsCount = 0
        instance.waitlistCount = 0
        classInstanceRepository.save(instance)

        // Rule 13 — cancel all waitlist entries and notify
        waitlistService.cancelAllForClass(instance.id)

        val classType = classTypeRepository.findById(instance.classTypeId).orElseThrow()
        val instructor = trainerRepository.findById(instance.instructorId).orElseThrow()
        return toInstanceResponse(instance, classType, instructor)
    }

    // ── Internal lookups (used by GXBookingService) ────────────────────────

    fun findInstanceOrThrow(
        instancePublicId: UUID,
        organizationId: Long,
    ): GXClassInstance =
        classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instancePublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class instance not found.")
            }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ): Club =
        clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }

    private fun findBranchOrThrow(
        branchPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Branch =
        branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, organizationId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.") }

    private fun findClassTypeOrThrow(
        classTypePublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): GXClassType =
        classTypeRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(classTypePublicId, organizationId, clubId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class type not found.") }

    private fun findInstructorOrThrow(
        instructorPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Trainer =
        trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(instructorPublicId, organizationId, clubId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Instructor not found.") }

    private fun toClassTypeResponse(classType: GXClassType): GXClassTypeResponse =
        GXClassTypeResponse(
            id = classType.publicId,
            nameAr = classType.nameAr,
            nameEn = classType.nameEn,
            descriptionAr = classType.descriptionAr,
            descriptionEn = classType.descriptionEn,
            defaultDurationMinutes = classType.defaultDurationMinutes,
            defaultCapacity = classType.defaultCapacity,
            color = classType.color,
            isActive = classType.isActive,
            createdAt = classType.createdAt,
            updatedAt = classType.updatedAt,
        )

    fun toInstanceResponse(
        instance: GXClassInstance,
        classType: GXClassType,
        instructor: Trainer,
    ): GXClassInstanceResponse =
        GXClassInstanceResponse(
            id = instance.publicId,
            classType =
                GXClassTypeSummary(
                    id = classType.publicId,
                    nameAr = classType.nameAr,
                    nameEn = classType.nameEn,
                    color = classType.color,
                ),
            instructor =
                GXInstructorSummary(
                    id = instructor.publicId,
                    firstNameAr = instructor.firstNameAr,
                    firstNameEn = instructor.firstNameEn,
                    lastNameAr = instructor.lastNameAr,
                    lastNameEn = instructor.lastNameEn,
                ),
            scheduledAt = instance.scheduledAt,
            durationMinutes = instance.durationMinutes,
            capacity = instance.capacity,
            bookingsCount = instance.bookingsCount,
            waitlistCount = instance.waitlistCount,
            availableSpots = (instance.capacity - instance.bookingsCount).coerceAtLeast(0),
            room = instance.room,
            status = instance.instanceStatus,
            notes = instance.notes,
            createdAt = instance.createdAt,
        )
}
