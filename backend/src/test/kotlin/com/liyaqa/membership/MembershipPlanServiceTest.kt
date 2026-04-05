package com.liyaqa.membership

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.membership.dto.CreateMembershipPlanRequest
import com.liyaqa.membership.dto.UpdateMembershipPlanRequest
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

@ExtendWith(MockitoExtension::class)
class MembershipPlanServiceTest {
    @Mock
    lateinit var membershipPlanRepository: MembershipPlanRepository

    @Mock
    lateinit var organizationRepository: OrganizationRepository

    @Mock
    lateinit var clubRepository: ClubRepository

    @InjectMocks
    lateinit var service: MembershipPlanService

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

    private fun validRequest() =
        CreateMembershipPlanRequest(
            nameAr = "شهري أساسي",
            nameEn = "Basic Monthly",
            priceHalalas = 15000,
            durationDays = 30,
            gracePeriodDays = 3,
            freezeAllowed = true,
            maxFreezeDays = 14,
        )

    private fun plan() =
        MembershipPlan(
            organizationId = org.id,
            clubId = club.id,
            nameAr = "شهري أساسي",
            nameEn = "Basic Monthly",
            priceHalalas = 15000,
            durationDays = 30,
            gracePeriodDays = 3,
            freezeAllowed = true,
            maxFreezeDays = 14,
        )

    private fun stubLookups() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
    }

    // ── Create ──────────────────────────────────────────────────────────────

    @Test
    fun `create plan successfully`() {
        stubLookups()
        whenever(membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNull(club.id, "Basic Monthly"))
            .thenReturn(false)
        whenever(membershipPlanRepository.save(any<MembershipPlan>()))
            .thenAnswer { it.arguments[0] as MembershipPlan }

        val response = service.create(org.publicId, club.publicId, validRequest())

        assertThat(response.nameEn).isEqualTo("Basic Monthly")
        assertThat(response.priceHalalas).isEqualTo(15000)
        assertThat(response.priceSar).isEqualTo("150.00")
        assertThat(response.organizationId).isEqualTo(org.publicId)
        assertThat(response.clubId).isEqualTo(club.publicId)
        verify(membershipPlanRepository).save(any<MembershipPlan>())
    }

    // ── Rule 1: Price must be positive ──────────────────────────────────────

    @Test
    fun `create plan with zero price returns 422`() {
        stubLookups()

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, validRequest().copy(priceHalalas = 0))
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create plan with negative price returns 422`() {
        stubLookups()

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, validRequest().copy(priceHalalas = -100))
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 2: Duration must be positive ───────────────────────────────────

    @Test
    fun `create plan with zero duration returns 422`() {
        stubLookups()

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, validRequest().copy(durationDays = 0))
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 3: Grace period cannot exceed duration ─────────────────────────

    @Test
    fun `create plan with grace period exceeding duration returns 422`() {
        stubLookups()

        assertThatThrownBy {
            service.create(
                org.publicId,
                club.publicId,
                validRequest().copy(durationDays = 30, gracePeriodDays = 31),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create plan with grace period equal to duration succeeds`() {
        stubLookups()
        whenever(membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNull(club.id, "Basic Monthly"))
            .thenReturn(false)
        whenever(membershipPlanRepository.save(any<MembershipPlan>()))
            .thenAnswer { it.arguments[0] as MembershipPlan }

        val response =
            service.create(
                org.publicId,
                club.publicId,
                validRequest().copy(durationDays = 30, gracePeriodDays = 30),
            )

        assertThat(response.gracePeriodDays).isEqualTo(30)
    }

    // ── Rule 4: Max freeze days consistency ─────────────────────────────────

    @Test
    fun `create plan with freeze not allowed but maxFreezeDays greater than zero returns 422`() {
        stubLookups()

        assertThatThrownBy {
            service.create(
                org.publicId,
                club.publicId,
                validRequest().copy(freezeAllowed = false, maxFreezeDays = 10),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create plan with freeze allowed but maxFreezeDays zero returns 422`() {
        stubLookups()

        assertThatThrownBy {
            service.create(
                org.publicId,
                club.publicId,
                validRequest().copy(freezeAllowed = true, maxFreezeDays = 0),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create plan with freeze not allowed and maxFreezeDays zero succeeds`() {
        stubLookups()
        whenever(membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNull(club.id, "Basic Monthly"))
            .thenReturn(false)
        whenever(membershipPlanRepository.save(any<MembershipPlan>()))
            .thenAnswer { it.arguments[0] as MembershipPlan }

        val response =
            service.create(
                org.publicId,
                club.publicId,
                validRequest().copy(freezeAllowed = false, maxFreezeDays = 0),
            )

        assertThat(response.freezeAllowed).isFalse()
        assertThat(response.maxFreezeDays).isEqualTo(0)
    }

    // ── Rule 5: Club scope ──────────────────────────────────────────────────

    @Test
    fun `get plan from different club returns 404`() {
        stubLookups()
        val otherClub =
            Club(
                organizationId = org.id,
                nameAr = "نادي آخر",
                nameEn = "Other Club",
            )
        val planInOtherClub =
            MembershipPlan(
                organizationId = org.id,
                clubId = otherClub.id + 999,
                nameAr = "خطة",
                nameEn = "Plan",
                priceHalalas = 10000,
                durationDays = 30,
            )
        whenever(membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(planInOtherClub.publicId, org.id))
            .thenReturn(Optional.of(planInOtherClub))

        assertThatThrownBy {
            service.getByPublicId(org.publicId, club.publicId, planInOtherClub.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    // ── Rule 6: Soft delete ─────────────────────────────────────────────────

    @Test
    fun `delete plan soft-deletes successfully`() {
        stubLookups()
        val plan = plan()
        whenever(membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(plan.publicId, org.id))
            .thenReturn(Optional.of(plan))
        whenever(membershipPlanRepository.save(any<MembershipPlan>()))
            .thenAnswer { it.arguments[0] as MembershipPlan }

        service.delete(org.publicId, club.publicId, plan.publicId)

        assertThat(plan.deletedAt).isNotNull()
        verify(membershipPlanRepository).save(plan)
    }

    // ── Rule 7: Name uniqueness within club ─────────────────────────────────

    @Test
    fun `create plan with duplicate nameEn returns 409`() {
        stubLookups()
        whenever(membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNull(club.id, "Basic Monthly"))
            .thenReturn(true)

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, validRequest())
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `update plan with duplicate nameEn returns 409`() {
        stubLookups()
        val plan = plan()
        whenever(membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(plan.publicId, org.id))
            .thenReturn(Optional.of(plan))
        whenever(membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNullAndIdNot(club.id, "Duplicate Name", plan.id))
            .thenReturn(true)

        assertThatThrownBy {
            service.update(
                org.publicId,
                club.publicId,
                plan.publicId,
                UpdateMembershipPlanRequest(nameEn = "Duplicate Name"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Read operations ─────────────────────────────────────────────────────

    @Test
    fun `get plan by public id returns response`() {
        stubLookups()
        val plan = plan()
        whenever(membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(plan.publicId, org.id))
            .thenReturn(Optional.of(plan))

        val response = service.getByPublicId(org.publicId, club.publicId, plan.publicId)

        assertThat(response.id).isEqualTo(plan.publicId)
        assertThat(response.nameEn).isEqualTo("Basic Monthly")
        assertThat(response.priceSar).isEqualTo("150.00")
    }

    @Test
    fun `get plan not found throws exception`() {
        stubLookups()
        val randomId = java.util.UUID.randomUUID()
        whenever(membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(randomId, org.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.getByPublicId(org.publicId, club.publicId, randomId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `get all plans returns page`() {
        stubLookups()
        val pageable = PageRequest.of(0, 20)
        val plan = plan()
        whenever(membershipPlanRepository.findAllByClubIdAndDeletedAtIsNull(club.id, pageable))
            .thenReturn(PageImpl(listOf(plan), pageable, 1))

        val response = service.getAll(org.publicId, club.publicId, pageable)

        assertThat(response.items).hasSize(1)
        assertThat(response.pagination.totalElements).isEqualTo(1)
    }

    // ── Update ──────────────────────────────────────────────────────────────

    @Test
    fun `update plan successfully`() {
        stubLookups()
        val plan = plan()
        whenever(membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(plan.publicId, org.id))
            .thenReturn(Optional.of(plan))
        whenever(membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNullAndIdNot(club.id, "Updated Plan", plan.id))
            .thenReturn(false)
        whenever(membershipPlanRepository.save(any<MembershipPlan>()))
            .thenAnswer { it.arguments[0] as MembershipPlan }

        val response =
            service.update(
                org.publicId,
                club.publicId,
                plan.publicId,
                UpdateMembershipPlanRequest(nameEn = "Updated Plan", priceHalalas = 20000),
            )

        assertThat(response.nameEn).isEqualTo("Updated Plan")
        assertThat(response.priceHalalas).isEqualTo(20000)
        assertThat(response.priceSar).isEqualTo("200.00")
    }

    @Test
    fun `update plan re-validates business rules`() {
        stubLookups()
        val plan = plan()
        whenever(membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(plan.publicId, org.id))
            .thenReturn(Optional.of(plan))

        assertThatThrownBy {
            service.update(
                org.publicId,
                club.publicId,
                plan.publicId,
                UpdateMembershipPlanRequest(gracePeriodDays = 999),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── priceSar formatting ─────────────────────────────────────────────────

    @Test
    fun `priceSar is formatted correctly for various amounts`() {
        stubLookups()
        whenever(membershipPlanRepository.existsByClubIdAndNameEnAndDeletedAtIsNull(club.id, "Basic Monthly"))
            .thenReturn(false)
        whenever(membershipPlanRepository.save(any<MembershipPlan>()))
            .thenAnswer { it.arguments[0] as MembershipPlan }

        val response =
            service.create(
                org.publicId,
                club.publicId,
                validRequest().copy(priceHalalas = 39900),
            )

        assertThat(response.priceSar).isEqualTo("399.00")
    }
}
