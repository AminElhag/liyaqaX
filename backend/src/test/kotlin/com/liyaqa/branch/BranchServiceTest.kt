package com.liyaqa.branch

import com.liyaqa.branch.dto.CreateBranchRequest
import com.liyaqa.branch.dto.UpdateBranchRequest
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.subscription.service.SubscriptionService
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
class BranchServiceTest {
    @Mock
    lateinit var branchRepository: BranchRepository

    @Mock
    lateinit var clubRepository: ClubRepository

    @Mock
    lateinit var organizationRepository: OrganizationRepository

    @Mock
    lateinit var subscriptionService: SubscriptionService

    @InjectMocks
    lateinit var branchService: BranchService

    private val org =
        Organization(
            nameAr = "منظمة",
            nameEn = "Test Org",
            email = "org@test.com",
        )

    private val club =
        Club(
            organizationId = org.id,
            nameAr = "نادي",
            nameEn = "Test Club",
        )

    private fun branch() =
        Branch(
            organizationId = org.id,
            clubId = club.id,
            nameAr = "فرع",
            nameEn = "Test Branch",
            city = "Riyadh",
        )

    private fun stubOrgLookup() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
    }

    private fun stubClubLookup() {
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
    }

    @Test
    fun `create branch successfully`() {
        stubOrgLookup()
        stubClubLookup()
        whenever(branchRepository.save(any<Branch>()))
            .thenAnswer { it.arguments[0] as Branch }

        val response =
            branchService.create(
                org.publicId,
                club.publicId,
                CreateBranchRequest(nameAr = "فرع الرياض", nameEn = "Riyadh Branch", city = "Riyadh"),
            )

        assertThat(response.nameEn).isEqualTo("Riyadh Branch")
        assertThat(response.city).isEqualTo("Riyadh")
        assertThat(response.organizationId).isEqualTo(org.publicId)
        assertThat(response.clubId).isEqualTo(club.publicId)
        verify(branchRepository).save(any<Branch>())
    }

    @Test
    fun `create branch with non-existent org throws not found`() {
        val randomId = UUID.randomUUID()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(randomId))
            .thenReturn(Optional.empty())

        assertThatThrownBy {
            branchService.create(randomId, club.publicId, CreateBranchRequest(nameAr = "فرع", nameEn = "Branch"))
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `create branch with non-existent club throws not found`() {
        stubOrgLookup()
        val randomId = UUID.randomUUID()
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(randomId, org.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy {
            branchService.create(org.publicId, randomId, CreateBranchRequest(nameAr = "فرع", nameEn = "Branch"))
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `get branch by public id successfully`() {
        stubOrgLookup()
        stubClubLookup()
        val branch = branch()
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.of(branch))

        val response = branchService.getByPublicId(org.publicId, club.publicId, branch.publicId)

        assertThat(response.id).isEqualTo(branch.publicId)
        assertThat(response.nameEn).isEqualTo("Test Branch")
    }

    @Test
    fun `get branch not found throws exception`() {
        stubOrgLookup()
        stubClubLookup()
        val randomId = UUID.randomUUID()
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(randomId, org.id, club.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy { branchService.getByPublicId(org.publicId, club.publicId, randomId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `get all branches returns page`() {
        stubOrgLookup()
        stubClubLookup()
        val pageable = PageRequest.of(0, 20)
        val branch = branch()
        whenever(branchRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(org.id, club.id, pageable))
            .thenReturn(PageImpl(listOf(branch), pageable, 1))

        val response = branchService.getAll(org.publicId, club.publicId, pageable)

        assertThat(response.items).hasSize(1)
        assertThat(response.items[0].nameEn).isEqualTo("Test Branch")
        assertThat(response.pagination.totalElements).isEqualTo(1)
    }

    @Test
    fun `update branch applies only supplied fields`() {
        stubOrgLookup()
        stubClubLookup()
        val branch = branch()
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.of(branch))
        whenever(branchRepository.save(any<Branch>()))
            .thenAnswer { it.arguments[0] as Branch }

        val response =
            branchService.update(
                org.publicId,
                club.publicId,
                branch.publicId,
                UpdateBranchRequest(nameEn = "Updated Branch"),
            )

        assertThat(response.nameEn).isEqualTo("Updated Branch")
        assertThat(response.nameAr).isEqualTo("فرع")
        assertThat(response.city).isEqualTo("Riyadh")
    }

    @Test
    fun `delete branch soft deletes`() {
        stubOrgLookup()
        stubClubLookup()
        val branch = branch()
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.of(branch))
        whenever(branchRepository.save(any<Branch>()))
            .thenAnswer { it.arguments[0] as Branch }

        branchService.delete(org.publicId, club.publicId, branch.publicId)

        assertThat(branch.deletedAt).isNotNull()
        verify(branchRepository).save(branch)
    }
}
