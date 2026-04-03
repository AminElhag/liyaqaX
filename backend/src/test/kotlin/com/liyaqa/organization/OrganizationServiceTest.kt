package com.liyaqa.organization

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.dto.CreateOrganizationRequest
import com.liyaqa.organization.dto.UpdateOrganizationRequest
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
class OrganizationServiceTest {
    @Mock
    lateinit var organizationRepository: OrganizationRepository

    @InjectMocks
    lateinit var organizationService: OrganizationService

    private fun createRequest(email: String = "test@example.com") =
        CreateOrganizationRequest(
            nameAr = "منظمة تجريبية",
            nameEn = "Test Org",
            email = email,
        )

    private fun organization(email: String = "test@example.com") =
        Organization(
            nameAr = "منظمة تجريبية",
            nameEn = "Test Org",
            email = email,
        )

    @Test
    fun `create organization successfully`() {
        whenever(organizationRepository.existsByEmailAndDeletedAtIsNull("test@example.com"))
            .thenReturn(false)
        whenever(organizationRepository.save(any<Organization>()))
            .thenAnswer { it.arguments[0] as Organization }

        val response = organizationService.create(createRequest())

        assertThat(response.nameEn).isEqualTo("Test Org")
        assertThat(response.nameAr).isEqualTo("منظمة تجريبية")
        assertThat(response.email).isEqualTo("test@example.com")
        assertThat(response.country).isEqualTo("SA")
        verify(organizationRepository).save(any<Organization>())
    }

    @Test
    fun `create organization with duplicate email throws conflict`() {
        whenever(organizationRepository.existsByEmailAndDeletedAtIsNull("test@example.com"))
            .thenReturn(true)

        assertThatThrownBy { organizationService.create(createRequest()) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `get organization by public id successfully`() {
        val org = organization()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))

        val response = organizationService.getByPublicId(org.publicId)

        assertThat(response.id).isEqualTo(org.publicId)
        assertThat(response.nameEn).isEqualTo("Test Org")
    }

    @Test
    fun `get organization by public id not found throws exception`() {
        val randomId = UUID.randomUUID()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(randomId))
            .thenReturn(Optional.empty())

        assertThatThrownBy { organizationService.getByPublicId(randomId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `get all organizations returns page`() {
        val pageable = PageRequest.of(0, 20)
        val org = organization()
        whenever(organizationRepository.findAllByDeletedAtIsNull(pageable))
            .thenReturn(PageImpl(listOf(org), pageable, 1))

        val response = organizationService.getAll(pageable)

        assertThat(response.items).hasSize(1)
        assertThat(response.pagination.totalElements).isEqualTo(1)
    }

    @Test
    fun `update organization successfully`() {
        val org = organization()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(organizationRepository.save(any<Organization>()))
            .thenAnswer { it.arguments[0] as Organization }

        val response =
            organizationService.update(
                org.publicId,
                UpdateOrganizationRequest(nameEn = "Updated Name"),
            )

        assertThat(response.nameEn).isEqualTo("Updated Name")
    }

    @Test
    fun `update organization with duplicate email throws conflict`() {
        val org = organization()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(organizationRepository.existsByEmailAndDeletedAtIsNullAndIdNot("taken@example.com", org.id))
            .thenReturn(true)

        assertThatThrownBy {
            organizationService.update(
                org.publicId,
                UpdateOrganizationRequest(email = "taken@example.com"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `delete organization successfully`() {
        val org = organization()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(organizationRepository.save(any<Organization>()))
            .thenAnswer { it.arguments[0] as Organization }

        organizationService.delete(org.publicId)

        assertThat(org.deletedAt).isNotNull()
        verify(organizationRepository).save(org)
    }

    @Test
    fun `delete organization not found throws exception`() {
        val randomId = UUID.randomUUID()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(randomId))
            .thenReturn(Optional.empty())

        assertThatThrownBy { organizationService.delete(randomId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }
}
