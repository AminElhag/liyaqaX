package com.liyaqa.shift.service

import com.liyaqa.audit.AuditService
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.shift.dto.CreateShiftRequest
import com.liyaqa.shift.entity.StaffShift
import com.liyaqa.shift.repository.ShiftSwapRequestRepository
import com.liyaqa.shift.repository.StaffShiftRepository
import com.liyaqa.staff.StaffBranchAssignmentRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class StaffShiftServiceTest {
    @Mock lateinit var shiftRepository: StaffShiftRepository

    @Mock lateinit var swapRepository: ShiftSwapRequestRepository

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var staffBranchAssignmentRepository: StaffBranchAssignmentRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var auditService: AuditService

    @InjectMocks lateinit var service: StaffShiftService

    private val clubId = 1L
    private val staffPublicId: UUID = UUID.randomUUID()
    private val branchPublicId: UUID = UUID.randomUUID()

    private fun makeStaff(id: Long = 10L) =
        StaffMember(
            organizationId = 1L,
            clubId = clubId,
            userId = 100L,
            roleId = 1L,
            firstNameAr = "خالد",
            firstNameEn = "Khalid",
            lastNameAr = "العتيبي",
            lastNameEn = "Al-Otaibi",
            joinedAt = LocalDate.now(),
        ).apply {
            val idField = StaffMember::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }

    private fun makeBranch(id: Long = 5L) =
        Branch(
            publicId = branchPublicId,
            organizationId = 1L,
            clubId = clubId,
            nameAr = "فرع الرياض",
            nameEn = "Riyadh Branch",
        ).apply {
            val idField = Branch::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }

    private fun validRequest() =
        CreateShiftRequest(
            staffMemberPublicId = staffPublicId,
            branchPublicId = branchPublicId,
            startAt = Instant.parse("2026-04-14T06:00:00Z"),
            endAt = Instant.parse("2026-04-14T14:00:00Z"),
        )

    @Test
    fun `createShift succeeds for valid staff at assigned branch`() {
        val staff = makeStaff()
        val branch = makeBranch()

        whenever(staffMemberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(staff))
        whenever(branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(branch))
        whenever(staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(staff.id, branch.id)).thenReturn(true)
        whenever(shiftRepository.countOverlapping(any(), any(), any(), any())).thenReturn(0L)
        whenever(shiftRepository.save(any<StaffShift>())).thenAnswer { it.arguments[0] }

        val result = service.createShift(clubId, 100L, validRequest())

        assertThat(result.staffMemberName).isEqualTo("Khalid Al-Otaibi")
        assertThat(result.branchName).isEqualTo("Riyadh Branch")
        verify(auditService).logFromContext(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `createShift throws 422 when staff is not assigned to branch`() {
        val staff = makeStaff()
        val branch = makeBranch()

        whenever(staffMemberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(staff))
        whenever(branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(branch))
        whenever(staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(staff.id, branch.id)).thenReturn(false)

        assertThatThrownBy { service.createShift(clubId, 100L, validRequest()) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status", "errorCode")
            .containsExactly(HttpStatus.UNPROCESSABLE_ENTITY, "STAFF_NOT_AT_BRANCH")
    }

    @Test
    fun `createShift throws 422 when endAt is before startAt`() {
        val staff = makeStaff()
        val branch = makeBranch()

        whenever(staffMemberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(staff))
        whenever(branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(branch))
        whenever(staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(any(), any())).thenReturn(true)

        val request =
            validRequest().copy(
                startAt = Instant.parse("2026-04-14T14:00:00Z"),
                endAt = Instant.parse("2026-04-14T06:00:00Z"),
            )

        assertThatThrownBy { service.createShift(clubId, 100L, request) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `createShift throws 409 SHIFT_OVERLAP when staff has overlapping shift`() {
        val staff = makeStaff()
        val branch = makeBranch()

        whenever(staffMemberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(staff))
        whenever(branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(branch))
        whenever(staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(any(), any())).thenReturn(true)
        whenever(shiftRepository.countOverlapping(any(), any(), any(), any())).thenReturn(1L)

        assertThatThrownBy { service.createShift(clubId, 100L, validRequest()) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status", "errorCode")
            .containsExactly(HttpStatus.CONFLICT, "SHIFT_OVERLAP")
    }

    @Test
    fun `createShift allows non-overlapping shift at different time same branch`() {
        val staff = makeStaff()
        val branch = makeBranch()

        whenever(staffMemberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(staff))
        whenever(branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.of(branch))
        whenever(staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(any(), any())).thenReturn(true)
        whenever(shiftRepository.countOverlapping(any(), any(), any(), any())).thenReturn(0L)
        whenever(shiftRepository.save(any<StaffShift>())).thenAnswer { it.arguments[0] }

        val request =
            validRequest().copy(
                startAt = Instant.parse("2026-04-14T16:00:00Z"),
                endAt = Instant.parse("2026-04-14T22:00:00Z"),
            )

        val result = service.createShift(clubId, 100L, request)
        assertThat(result.startAt).isEqualTo(Instant.parse("2026-04-14T16:00:00Z"))
    }

    @Test
    fun `deleteShift throws 409 when shift has pending swap request`() {
        val shift =
            StaffShift(
                staffMemberId = 10L,
                branchId = 5L,
                startAt = Instant.parse("2026-04-14T06:00:00Z"),
                endAt = Instant.parse("2026-04-14T14:00:00Z"),
                createdByUserId = 100L,
            )
        val branch = makeBranch()

        whenever(shiftRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(shift))
        whenever(branchRepository.findById(shift.branchId)).thenReturn(Optional.of(branch))
        whenever(swapRepository.countOpenSwapsForShift(any())).thenReturn(1L)

        assertThatThrownBy { service.deleteShift(clubId, shift.publicId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status", "errorCode")
            .containsExactly(HttpStatus.CONFLICT, "SHIFT_HAS_PENDING_SWAP")
    }

    @Test
    fun `deleteShift soft-deletes and logs audit`() {
        val shift =
            StaffShift(
                staffMemberId = 10L,
                branchId = 5L,
                startAt = Instant.parse("2026-04-14T06:00:00Z"),
                endAt = Instant.parse("2026-04-14T14:00:00Z"),
                createdByUserId = 100L,
            )
        val branch = makeBranch()

        whenever(shiftRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(shift))
        whenever(branchRepository.findById(shift.branchId)).thenReturn(Optional.of(branch))
        whenever(swapRepository.countOpenSwapsForShift(any())).thenReturn(0L)
        whenever(shiftRepository.save(any<StaffShift>())).thenAnswer { it.arguments[0] }

        service.deleteShift(clubId, shift.publicId)

        assertThat(shift.deletedAt).isNotNull()
        verify(auditService).logFromContext(any(), any(), any(), anyOrNull())
    }
}
