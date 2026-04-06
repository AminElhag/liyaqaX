package com.liyaqa.nexus

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.PaginationMeta
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.nexus.dto.MemberDetailNexusResponse
import com.liyaqa.nexus.dto.MemberSearchItemResponse
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.user.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberNexusService(
    private val memberRepository: MemberRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
) {
    fun search(
        q: String,
        pageable: Pageable,
    ): PageResponse<MemberSearchItemResponse> {
        if (q.length < 2) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Search query must be at least 2 characters.",
            )
        }

        val page = memberRepository.searchAcrossOrgs(q, pageable)
        return PageResponse(
            items = page.content.map { it.toSearchItem() },
            pagination =
                PaginationMeta(
                    page = page.number,
                    size = page.size,
                    totalElements = page.totalElements,
                    totalPages = page.totalPages,
                    hasNext = page.hasNext(),
                    hasPrevious = page.hasPrevious(),
                ),
        )
    }

    fun getById(publicId: UUID): MemberDetailNexusResponse {
        val member =
            memberRepository.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.")
                }
        return member.toDetail()
    }

    private fun Member.toSearchItem(): MemberSearchItemResponse {
        val orgName = organizationRepository.findById(organizationId).map { it.nameEn }.orElse(null)
        val clubName = clubRepository.findById(clubId).map { it.nameEn }.orElse(null)
        val email = userRepository.findById(userId).map { it.email }.orElse(null)
        return MemberSearchItemResponse(
            id = publicId,
            firstNameAr = firstNameAr,
            firstNameEn = firstNameEn,
            lastNameAr = lastNameAr,
            lastNameEn = lastNameEn,
            phone = phone,
            email = email,
            clubName = clubName,
            organizationName = orgName,
            membershipStatus = membershipStatus,
        )
    }

    private fun Member.toDetail(): MemberDetailNexusResponse {
        val orgName = organizationRepository.findById(organizationId).map { it.nameEn }.orElse(null)
        val clubName = clubRepository.findById(clubId).map { it.nameEn }.orElse(null)
        val email = userRepository.findById(userId).map { it.email }.orElse(null)
        return MemberDetailNexusResponse(
            id = publicId,
            firstNameAr = firstNameAr,
            firstNameEn = firstNameEn,
            lastNameAr = lastNameAr,
            lastNameEn = lastNameEn,
            phone = phone,
            email = email,
            membershipStatus = membershipStatus,
            clubName = clubName,
            organizationName = orgName,
            createdAt = createdAt,
        )
    }
}
