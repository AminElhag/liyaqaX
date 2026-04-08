package com.liyaqa.nexus

import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.staff.StaffMemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OrganizationNexusServiceTest {
    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var memberRepository: MemberRepository

    @InjectMocks lateinit var service: OrganizationNexusService

    @Test
    fun `create throws 409 for duplicate name`() {
        whenever(organizationRepository.existsByNameEnAndDeletedAtIsNull("Existing Org")).thenReturn(true)

        val ex =
            assertThrows(ArenaException::class.java) {
                service.create(
                    com.liyaqa.nexus.dto.CreateOrganizationNexusRequest(
                        nameAr = "موجودة",
                        nameEn = "Existing Org",
                        email = "new@test.com",
                    ),
                )
            }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    @Test
    fun `getById returns org with clubs`() {
        val org = Organization(nameAr = "م", nameEn = "Test", email = "t@t.com")
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(any<UUID>())).thenReturn(Optional.of(org))
        whenever(clubRepository.findAllByOrganizationIdAndDeletedAtIsNull(org.id)).thenReturn(emptyList())

        val result = service.getById(org.publicId)
        assertEquals("Test", result.nameEn)
        assertEquals(0, result.clubs.size)
    }
}

@ExtendWith(MockitoExtension::class)
class ClubNexusMrrEstimationTest {
    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var membershipRepository: MembershipRepository

    @Mock lateinit var membershipPlanRepository: MembershipPlanRepository

    @InjectMocks lateinit var service: ClubNexusService

    @Test
    fun `estimateMrrHalalas delegates to native query`() {
        whenever(membershipRepository.estimateMrrHalalasForClub(1L)).thenReturn(50000L)
        val result = service.estimateMrrHalalas(1L)
        assertEquals(50000L, result)
    }

    @Test
    fun `club detail includes MRR formatted as SAR`() {
        val org = Organization(nameAr = "م", nameEn = "Org", email = "o@t.com")
        val club = Club(organizationId = org.id, nameAr = "ن", nameEn = "Club")
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(any<UUID>())).thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(any<UUID>(), any())).thenReturn(Optional.of(club))
        whenever(branchRepository.countByClubIdAndDeletedAtIsNull(club.id)).thenReturn(2)
        whenever(staffMemberRepository.countByClubIdAndDeletedAtIsNull(club.id)).thenReturn(3)
        whenever(memberRepository.countByClubIdAndMembershipStatusAndDeletedAtIsNull(club.id, "active")).thenReturn(10)
        whenever(membershipRepository.estimateMrrHalalasForClub(club.id)).thenReturn(31500000L)

        val result = service.getById(org.publicId, club.publicId)
        assertEquals(31500000L, result.estimatedMrrHalalas)
        assertEquals("315000.00", result.estimatedMrrSar)
    }
}
