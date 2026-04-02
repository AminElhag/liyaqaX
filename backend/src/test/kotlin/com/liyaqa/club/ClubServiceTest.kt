package com.liyaqa.club

import com.liyaqa.club.dto.CreateClubRequest
import com.liyaqa.club.dto.UpdateClubRequest
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ClubServiceTest {
    @Mock
    lateinit var clubRepository: ClubRepository

    @Mock
    lateinit var organizationRepository: OrganizationRepository

    @InjectMocks
    lateinit var clubService: ClubService

    private val org =
        Organization(
            nameAr = "منظمة",
            nameEn = "Test Org",
            email = "org@test.com",
        )

    private fun club() =
        Club(
            organizationId = org.id,
            nameAr = "نادي",
            nameEn = "Test Club",
        )

    private fun stubOrgLookup() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
    }

    @Test
    fun `create club successfully`() {
        stubOrgLookup()
        whenever(clubRepository.save(any<Club>()))
            .thenAnswer { it.arguments[0] as Club }

        val response =
            clubService.create(
                org.publicId,
                CreateClubRequest(nameAr = "نادي", nameEn = "New Club"),
            )

        assertThat(response.nameEn).isEqualTo("New Club")
        assertThat(response.organizationId).isEqualTo(org.publicId)
        verify(clubRepository).save(any<Club>())
    }

    @Test
    fun `create club with non-existent org throws not found`() {
        val randomId = UUID.randomUUID()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(randomId))
            .thenReturn(Optional.empty())

        assertThatThrownBy {
            clubService.create(randomId, CreateClubRequest(nameAr = "نادي", nameEn = "Club"))
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `get club by public id successfully`() {
        stubOrgLookup()
        val club = club()
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))

        val response = clubService.getByPublicId(org.publicId, club.publicId)

        assertThat(response.id).isEqualTo(club.publicId)
        assertThat(response.nameEn).isEqualTo("Test Club")
    }

    @Test
    fun `get club not found throws exception`() {
        stubOrgLookup()
        val randomId = UUID.randomUUID()
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(randomId, org.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy { clubService.getByPublicId(org.publicId, randomId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `get all clubs returns page`() {
        stubOrgLookup()
        val pageable = PageRequest.of(0, 20)
        val club = club()
        whenever(clubRepository.findAllByOrganizationIdAndDeletedAtIsNull(org.id, pageable))
            .thenReturn(PageImpl(listOf(club), pageable, 1))

        val response = clubService.getAll(org.publicId, pageable)

        assertThat(response.items).hasSize(1)
        assertThat(response.pagination.totalElements).isEqualTo(1)
    }

    @Test
    fun `update club successfully`() {
        stubOrgLookup()
        val club = club()
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(clubRepository.save(any<Club>()))
            .thenAnswer { it.arguments[0] as Club }

        val response =
            clubService.update(
                org.publicId,
                club.publicId,
                UpdateClubRequest(nameEn = "Updated Club"),
            )

        assertThat(response.nameEn).isEqualTo("Updated Club")
    }

    @Test
    fun `delete club successfully`() {
        stubOrgLookup()
        val club = club()
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(clubRepository.save(any<Club>()))
            .thenAnswer { it.arguments[0] as Club }

        clubService.delete(org.publicId, club.publicId)

        assertThat(club.deletedAt).isNotNull()
        verify(clubRepository).save(club)
    }
}
