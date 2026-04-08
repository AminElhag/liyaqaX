package com.liyaqa.membership

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.membership.dto.CreateMembershipPlanRequest
import com.liyaqa.membership.dto.MembershipPlanResponse
import com.liyaqa.membership.dto.MembershipPlanSummaryResponse
import com.liyaqa.membership.dto.UpdateMembershipPlanRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MembershipPlanService(
    private val membershipPlanRepository: MembershipPlanRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
) {
    @Transactional
    fun create(
        orgPublicId: UUID,
        clubPublicId: UUID,
        request: CreateMembershipPlanRequest,
    ): MembershipPlanResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        validateBusinessRules(
            priceHalalas = request.priceHalalas,
            durationDays = request.durationDays,
            gracePeriodDays = request.gracePeriodDays,
            freezeAllowed = request.freezeAllowed,
            maxFreezeDays = request.maxFreezeDays,
        )

        // Rule 7 — name uniqueness within club
        if (membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNull(club.id, request.nameEn)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A plan with the name '${request.nameEn}' already exists in this club.",
            )
        }

        val plan =
            membershipPlanRepository.save(
                MembershipPlan(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = request.nameAr,
                    nameEn = request.nameEn,
                    descriptionAr = request.descriptionAr,
                    descriptionEn = request.descriptionEn,
                    priceHalalas = request.priceHalalas,
                    durationDays = request.durationDays,
                    gracePeriodDays = request.gracePeriodDays,
                    freezeAllowed = request.freezeAllowed,
                    maxFreezeDays = request.maxFreezeDays,
                    gxClassesIncluded = request.gxClassesIncluded,
                    ptSessionsIncluded = request.ptSessionsIncluded,
                    sortOrder = request.sortOrder,
                ),
            )

        return plan.toResponse(org.publicId, club.publicId)
    }

    fun getByPublicId(
        orgPublicId: UUID,
        clubPublicId: UUID,
        planPublicId: UUID,
    ): MembershipPlanResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val plan = findPlanOrThrow(planPublicId, org.id)
        verifyPlanBelongsToClub(plan, club)
        return plan.toResponse(org.publicId, club.publicId)
    }

    fun getAll(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<MembershipPlanSummaryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        return membershipPlanRepository
            .findAllByClubIdAndDeletedAtIsNull(club.id, pageable)
            .map { it.toSummaryResponse() }
            .toPageResponse()
    }

    fun getAllForNexus(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<MembershipPlanSummaryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        return membershipPlanRepository
            .findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(org.id, club.id, pageable)
            .map { it.toSummaryResponse() }
            .toPageResponse()
    }

    fun getByPublicIdForNexus(
        orgPublicId: UUID,
        clubPublicId: UUID,
        planPublicId: UUID,
    ): MembershipPlanResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val plan = findPlanOrThrow(planPublicId, org.id)
        verifyPlanBelongsToClub(plan, club)
        return plan.toResponse(org.publicId, club.publicId)
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        planPublicId: UUID,
        request: UpdateMembershipPlanRequest,
    ): MembershipPlanResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val plan = findPlanOrThrow(planPublicId, org.id)
        verifyPlanBelongsToClub(plan, club)

        request.nameAr?.let { plan.nameAr = it }
        request.nameEn?.let { plan.nameEn = it }
        request.descriptionAr?.let { plan.descriptionAr = it }
        request.descriptionEn?.let { plan.descriptionEn = it }
        request.priceHalalas?.let { plan.priceHalalas = it }
        request.durationDays?.let { plan.durationDays = it }
        request.gracePeriodDays?.let { plan.gracePeriodDays = it }
        request.freezeAllowed?.let { plan.freezeAllowed = it }
        request.maxFreezeDays?.let { plan.maxFreezeDays = it }
        request.gxClassesIncluded?.let { plan.gxClassesIncluded = it }
        request.ptSessionsIncluded?.let { plan.ptSessionsIncluded = it }
        request.isActive?.let { plan.isActive = it }
        request.sortOrder?.let { plan.sortOrder = it }

        validateBusinessRules(
            priceHalalas = plan.priceHalalas,
            durationDays = plan.durationDays,
            gracePeriodDays = plan.gracePeriodDays,
            freezeAllowed = plan.freezeAllowed,
            maxFreezeDays = plan.maxFreezeDays,
        )

        // Rule 7 — name uniqueness (exclude self)
        if (membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNullAndIdNot(
                club.id,
                plan.nameEn,
                plan.id,
            )
        ) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A plan with the name '${plan.nameEn}' already exists in this club.",
            )
        }

        return membershipPlanRepository.save(plan).toResponse(org.publicId, club.publicId)
    }

    @Transactional
    fun delete(
        orgPublicId: UUID,
        clubPublicId: UUID,
        planPublicId: UUID,
    ) {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val plan = findPlanOrThrow(planPublicId, org.id)
        verifyPlanBelongsToClub(plan, club)

        // TODO(#8): check active memberships before allowing delete
        plan.softDelete()
        membershipPlanRepository.save(plan)
    }

    // ── Business rule validation ────────────────────────────────────────────

    private fun validateBusinessRules(
        priceHalalas: Long,
        durationDays: Int,
        gracePeriodDays: Int,
        freezeAllowed: Boolean,
        maxFreezeDays: Int,
    ) {
        // Rule 1 — price must be positive
        if (priceHalalas <= 0) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Plan price must be greater than zero.",
            )
        }

        // Rule 2 — duration must be positive
        if (durationDays <= 0) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Plan duration must be greater than zero.",
            )
        }

        // Rule 3 — grace period cannot exceed duration
        if (gracePeriodDays > durationDays) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Grace period cannot exceed the plan duration.",
            )
        }

        // Rule 4 — max freeze days consistency
        if (!freezeAllowed && maxFreezeDays != 0) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Max freeze days must be zero when freeze is not allowed.",
            )
        }
        if (freezeAllowed && maxFreezeDays <= 0) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Max freeze days must be greater than zero when freeze is allowed.",
            )
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

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

    private fun findPlanOrThrow(
        planPublicId: UUID,
        organizationId: Long,
    ): MembershipPlan =
        membershipPlanRepository
            .findByPublicIdAndOrganizationIdAndDeletedAtIsNull(planPublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Membership plan not found.")
            }

    private fun verifyPlanBelongsToClub(
        plan: MembershipPlan,
        club: Club,
    ) {
        if (plan.clubId != club.id) {
            throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Membership plan not found.")
        }
    }

    // ── Mapping helpers ─────────────────────────────────────────────────────

    private fun MembershipPlan.toResponse(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ) = MembershipPlanResponse(
        id = publicId,
        organizationId = orgPublicId,
        clubId = clubPublicId,
        nameAr = nameAr,
        nameEn = nameEn,
        descriptionAr = descriptionAr,
        descriptionEn = descriptionEn,
        priceHalalas = priceHalalas,
        priceSar = formatPriceSar(priceHalalas),
        durationDays = durationDays,
        gracePeriodDays = gracePeriodDays,
        freezeAllowed = freezeAllowed,
        maxFreezeDays = maxFreezeDays,
        gxClassesIncluded = gxClassesIncluded,
        ptSessionsIncluded = ptSessionsIncluded,
        isActive = isActive,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MembershipPlan.toSummaryResponse() =
        MembershipPlanSummaryResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            priceHalalas = priceHalalas,
            priceSar = formatPriceSar(priceHalalas),
            durationDays = durationDays,
            isActive = isActive,
        )

    private fun formatPriceSar(halalas: Long): String = "%.2f".format(halalas / 100.0)
}
