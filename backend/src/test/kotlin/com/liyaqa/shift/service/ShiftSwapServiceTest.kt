package com.liyaqa.shift.service

import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.shift.dto.CreateSwapRequest
import com.liyaqa.shift.entity.ShiftSwapRequest
import com.liyaqa.shift.entity.StaffShift
import com.liyaqa.shift.repository.ShiftSwapRequestRepository
import com.liyaqa.shift.repository.StaffShiftRepository
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
class ShiftSwapServiceTest {
    @Mock lateinit var swapRepository: ShiftSwapRequestRepository

    @Mock lateinit var shiftRepository: StaffShiftRepository

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var staffShiftService: StaffShiftService

    @Mock lateinit var auditService: AuditService

    @InjectMocks lateinit var service: ShiftSwapService

    private val requesterStaffId = 10L
    private val targetStaffId = 20L
    private val shiftPublicId: UUID = UUID.randomUUID()
    private val targetPublicId: UUID = UUID.randomUUID()

    private fun makeShift() =
        StaffShift(
            staffMemberId = requesterStaffId,
            branchId = 5L,
            startAt = Instant.parse("2026-04-14T06:00:00Z"),
            endAt = Instant.parse("2026-04-14T14:00:00Z"),
            createdByUserId = 100L,
        )

    private fun makeStaff(
        id: Long,
        clubId: Long = 1L,
    ) = StaffMember(
        organizationId = 1L,
        clubId = clubId,
        userId = id + 100,
        roleId = 1L,
        firstNameAr = "اسم",
        firstNameEn = "Name",
        lastNameAr = "لقب",
        lastNameEn = "Last",
        joinedAt = LocalDate.now(),
    ).apply {
        val idField = StaffMember::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, id)
    }

    @Test
    fun `requestSwap creates PENDING_ACCEPTANCE request`() {
        val shift = makeShift()
        val requester = makeStaff(requesterStaffId)
        val target = makeStaff(targetStaffId)

        whenever(staffShiftService.findShiftOrThrow(any())).thenReturn(shift)
        whenever(staffMemberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(target))
        whenever(staffMemberRepository.findById(requesterStaffId)).thenReturn(Optional.of(requester))
        whenever(swapRepository.countOpenSwapsForShift(any())).thenReturn(0L)
        whenever(swapRepository.save(any<ShiftSwapRequest>())).thenAnswer { it.arguments[0] }

        val result =
            service.requestSwap(
                shiftPublicId,
                requesterStaffId,
                CreateSwapRequest(targetStaffPublicId = targetPublicId, requesterNote = "Doctor appointment"),
            )

        assertThat(result).isNotNull()
    }

    @Test
    fun `requestSwap throws 409 when duplicate open swap exists`() {
        val shift = makeShift()
        val requester = makeStaff(requesterStaffId)
        val target = makeStaff(targetStaffId)

        whenever(staffShiftService.findShiftOrThrow(any())).thenReturn(shift)
        whenever(staffMemberRepository.findByPublicIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(target))
        whenever(staffMemberRepository.findById(requesterStaffId)).thenReturn(Optional.of(requester))
        whenever(swapRepository.countOpenSwapsForShift(any())).thenReturn(1L)

        assertThatThrownBy {
            service.requestSwap(shiftPublicId, requesterStaffId, CreateSwapRequest(targetPublicId))
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status", "errorCode")
            .containsExactly(HttpStatus.CONFLICT, "SWAP_ALREADY_PENDING")
    }

    @Test
    fun `respondToSwap accept moves status to PENDING_APPROVAL`() {
        val swap =
            ShiftSwapRequest(
                shiftId = 1L,
                requesterStaffId = requesterStaffId,
                targetStaffId = targetStaffId,
                status = "PENDING_ACCEPTANCE",
            )
        val shift = makeShift()

        whenever(swapRepository.findByPublicId(any())).thenReturn(Optional.of(swap))
        whenever(shiftRepository.findById(swap.shiftId)).thenReturn(Optional.of(shift))
        whenever(swapRepository.save(any<ShiftSwapRequest>())).thenAnswer { it.arguments[0] }

        service.respondToSwap(swap.publicId, targetStaffId, "accept")

        assertThat(swap.status).isEqualTo("PENDING_APPROVAL")
    }

    @Test
    fun `respondToSwap accept throws 409 SHIFT_OVERLAP when target already has overlapping shift`() {
        val swap =
            ShiftSwapRequest(
                shiftId = 1L,
                requesterStaffId = requesterStaffId,
                targetStaffId = targetStaffId,
                status = "PENDING_ACCEPTANCE",
            )
        val shift = makeShift()

        whenever(swapRepository.findByPublicId(any())).thenReturn(Optional.of(swap))
        whenever(shiftRepository.findById(swap.shiftId)).thenReturn(Optional.of(shift))
        whenever(staffShiftService.checkOverlap(any(), any(), any(), any())).thenThrow(
            ArenaException(HttpStatus.CONFLICT, "conflict", "Overlap.", "SHIFT_OVERLAP"),
        )

        assertThatThrownBy { service.respondToSwap(swap.publicId, targetStaffId, "accept") }
            .isInstanceOf(ArenaException::class.java)
            .extracting("errorCode")
            .isEqualTo("SHIFT_OVERLAP")
    }

    @Test
    fun `respondToSwap decline moves status to DECLINED`() {
        val swap =
            ShiftSwapRequest(
                shiftId = 1L,
                requesterStaffId = requesterStaffId,
                targetStaffId = targetStaffId,
                status = "PENDING_ACCEPTANCE",
            )

        whenever(swapRepository.findByPublicId(any())).thenReturn(Optional.of(swap))
        whenever(swapRepository.save(any<ShiftSwapRequest>())).thenAnswer { it.arguments[0] }

        service.respondToSwap(swap.publicId, targetStaffId, "decline")

        assertThat(swap.status).isEqualTo("DECLINED")
    }

    @Test
    fun `resolveSwap approve transfers shift ownership and logs audit`() {
        val swap =
            ShiftSwapRequest(
                shiftId = 1L,
                requesterStaffId = requesterStaffId,
                targetStaffId = targetStaffId,
                status = "PENDING_APPROVAL",
            )
        val shift = makeShift()

        whenever(swapRepository.findByPublicId(any())).thenReturn(Optional.of(swap))
        whenever(shiftRepository.findById(swap.shiftId)).thenReturn(Optional.of(shift))
        whenever(shiftRepository.save(any<StaffShift>())).thenAnswer { it.arguments[0] }
        whenever(swapRepository.save(any<ShiftSwapRequest>())).thenAnswer { it.arguments[0] }

        service.resolveSwap(swap.publicId, 999L, "approve")

        assertThat(shift.staffMemberId).isEqualTo(targetStaffId)
        assertThat(swap.status).isEqualTo("APPROVED")
        assertThat(swap.resolvedByUserId).isEqualTo(999L)
        assertThat(swap.resolvedAt).isNotNull()
        verify(auditService).logFromContext(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `resolveSwap reject logs audit and leaves shift unchanged`() {
        val swap =
            ShiftSwapRequest(
                shiftId = 1L,
                requesterStaffId = requesterStaffId,
                targetStaffId = targetStaffId,
                status = "PENDING_APPROVAL",
            )
        val shift = makeShift()

        whenever(swapRepository.findByPublicId(any())).thenReturn(Optional.of(swap))
        whenever(swapRepository.save(any<ShiftSwapRequest>())).thenAnswer { it.arguments[0] }

        service.resolveSwap(swap.publicId, 999L, "reject")

        assertThat(shift.staffMemberId).isEqualTo(requesterStaffId)
        assertThat(swap.status).isEqualTo("REJECTED")
        verify(auditService).logFromContext(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `cancelSwap cancels PENDING_ACCEPTANCE request`() {
        val swap =
            ShiftSwapRequest(
                shiftId = 1L,
                requesterStaffId = requesterStaffId,
                targetStaffId = targetStaffId,
                status = "PENDING_ACCEPTANCE",
            )

        whenever(swapRepository.findByPublicId(any())).thenReturn(Optional.of(swap))
        whenever(swapRepository.save(any<ShiftSwapRequest>())).thenAnswer { it.arguments[0] }

        service.cancelSwap(swap.publicId, requesterStaffId)

        assertThat(swap.status).isEqualTo("CANCELLED")
    }
}
