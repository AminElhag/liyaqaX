package com.liyaqa.cashdrawer

import com.liyaqa.audit.AuditService
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.cashdrawer.dto.CloseSessionRequest
import com.liyaqa.cashdrawer.dto.CreateEntryRequest
import com.liyaqa.cashdrawer.dto.OpenSessionRequest
import com.liyaqa.cashdrawer.dto.ReconcileSessionRequest
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.Payment
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CashDrawerSessionServiceTest {
    @Mock lateinit var sessionRepository: CashDrawerSessionRepository

    @Mock lateinit var entryRepository: CashDrawerEntryRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var paymentRepository: PaymentRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var auditService: AuditService

    @InjectMocks lateinit var service: CashDrawerSessionService

    private val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "o@t.com")
    private val club = Club(organizationId = org.id, nameAr = "نادي", nameEn = "Club")
    private val branch = Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch")
    private val testUser =
        User(
            email = "test@test.com",
            passwordHash = "encoded",
            organizationId = org.id,
            clubId = club.id,
        )
    private val staff =
        StaffMember(
            organizationId = org.id,
            clubId = club.id,
            userId = testUser.id,
            roleId = 1L,
            firstNameAr = "فاطمة",
            firstNameEn = "Fatima",
            lastNameAr = "الزهراني",
            lastNameEn = "Al-Zahrani",
            joinedAt = LocalDate.now(),
        )

    private fun stubOrgAndClub() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
    }

    private fun stubUserAndStaff() {
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(testUser.publicId))
            .thenReturn(Optional.of(testUser))
        whenever(staffMemberRepository.findByUserIdAndClubIdAndDeletedAtIsNull(testUser.id, club.id))
            .thenReturn(Optional.of(staff))
    }

    private fun stubBranch() {
        whenever(
            branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                branch.publicId,
                org.id,
                club.id,
            ),
        ).thenReturn(Optional.of(branch))
    }

    private fun makeSession(status: String = "open"): CashDrawerSession =
        CashDrawerSession(
            organizationId = org.id,
            clubId = club.id,
            branchId = branch.id,
            openedByStaffId = staff.id,
            openingFloatHalalas = 50000,
            status = status,
        )

    // ── Rule 1: One open session per branch ─────────────────────────────────

    @Test
    fun `openSession happy path`() {
        stubOrgAndClub()
        stubUserAndStaff()
        stubBranch()
        whenever(sessionRepository.findByBranchIdAndStatusAndDeletedAtIsNull(branch.id, "open"))
            .thenReturn(Optional.empty())
        whenever(sessionRepository.save(any<CashDrawerSession>())).thenAnswer { it.arguments[0] as CashDrawerSession }
        whenever(entryRepository.findAllBySessionIdOrderByRecordedAtAsc(any())).thenReturn(emptyList())
        whenever(branchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(staffMemberRepository.findById(staff.id)).thenReturn(Optional.of(staff))

        val result =
            service.openSession(
                org.publicId,
                club.publicId,
                testUser.publicId,
                branch.publicId,
                OpenSessionRequest(openingFloatHalalas = 50000),
            )

        assertThat(result.status).isEqualTo("open")
        assertThat(result.openingFloat.halalas).isEqualTo(50000)
    }

    @Test
    fun `openSession returns 409 when session already open for branch`() {
        stubOrgAndClub()
        stubUserAndStaff()
        stubBranch()
        whenever(sessionRepository.findByBranchIdAndStatusAndDeletedAtIsNull(branch.id, "open"))
            .thenReturn(Optional.of(makeSession()))

        assertThatThrownBy {
            service.openSession(
                org.publicId,
                club.publicId,
                testUser.publicId,
                branch.publicId,
                OpenSessionRequest(openingFloatHalalas = 50000),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.CONFLICT)
            })
    }

    // ── Rule 3: Opening float non-negative ──────────────────────────────────

    @Test
    fun `openSession rejects negative float`() {
        stubOrgAndClub()
        stubUserAndStaff()

        assertThatThrownBy {
            service.openSession(
                org.publicId,
                club.publicId,
                testUser.publicId,
                branch.publicId,
                OpenSessionRequest(openingFloatHalalas = -100),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            })
    }

    // ── Rule 4: Entries only on open sessions ───────────────────────────────

    @Test
    fun `addEntry happy path cash_in`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession()
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))
        whenever(entryRepository.save(any<CashDrawerEntry>())).thenAnswer { it.arguments[0] as CashDrawerEntry }

        val result =
            service.addEntry(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                CreateEntryRequest(entryType = "cash_in", amountHalalas = 15000, description = "Test payment"),
            )

        assertThat(result.entryType).isEqualTo("cash_in")
        assertThat(result.amount.halalas).isEqualTo(15000)
    }

    @Test
    fun `addEntry happy path cash_out`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession()
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))
        whenever(entryRepository.save(any<CashDrawerEntry>())).thenAnswer { it.arguments[0] as CashDrawerEntry }

        val result =
            service.addEntry(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                CreateEntryRequest(entryType = "cash_out", amountHalalas = 4000, description = "Supplies"),
            )

        assertThat(result.entryType).isEqualTo("cash_out")
    }

    @Test
    fun `addEntry happy path float_adjustment`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession()
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))
        whenever(entryRepository.save(any<CashDrawerEntry>())).thenAnswer { it.arguments[0] as CashDrawerEntry }

        val result =
            service.addEntry(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                CreateEntryRequest(entryType = "float_adjustment", amountHalalas = 5000, description = "Float top-up"),
            )

        assertThat(result.entryType).isEqualTo("float_adjustment")
    }

    @Test
    fun `addEntry rejects on closed session`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession(status = "closed")
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))

        assertThatThrownBy {
            service.addEntry(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                CreateEntryRequest(entryType = "cash_in", amountHalalas = 1000, description = "Test"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.CONFLICT)
            })
    }

    // ── Rule 5: Entry amount positive ───────────────────────────────────────

    @Test
    fun `addEntry rejects zero amount`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession()
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))

        assertThatThrownBy {
            service.addEntry(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                CreateEntryRequest(entryType = "cash_in", amountHalalas = 0, description = "Test"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            })
    }

    // ── Rule 6: Payment ref must belong to same club ────────────────────────

    @Test
    fun `addEntry rejects payment from other club`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession()
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))

        val otherClubPayment =
            Payment(
                organizationId = org.id,
                clubId = 999L,
                branchId = branch.id,
                memberId = 1L,
                amountHalalas = 15000,
                paymentMethod = "cash",
                collectedById = 1L,
            )
        whenever(paymentRepository.findByPublicIdAndOrganizationId(otherClubPayment.publicId, org.id))
            .thenReturn(Optional.of(otherClubPayment))

        assertThatThrownBy {
            service.addEntry(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                CreateEntryRequest(
                    entryType = "cash_in",
                    amountHalalas = 15000,
                    description = "Test",
                    paymentId = otherClubPayment.publicId,
                ),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            })
    }

    // ── Rule 7 + 8: Close + expected balance computation ────────────────────

    @Test
    fun `closeSession computes expected balance correctly`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession()
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any<CashDrawerSession>())).thenAnswer { it.arguments[0] as CashDrawerSession }

        // opening_float = 50000
        // cash_in = 61000, cash_out = 4000, float_adjustment = 0
        // expected = 50000 + 61000 - 4000 + 0 = 107000
        whenever(entryRepository.sumBySessionIdAndEntryType(session.id, "cash_in")).thenReturn(61000L)
        whenever(entryRepository.sumBySessionIdAndEntryType(session.id, "cash_out")).thenReturn(4000L)
        whenever(entryRepository.sumBySessionIdAndEntryType(session.id, "float_adjustment")).thenReturn(0L)
        whenever(entryRepository.findAllBySessionIdOrderByRecordedAtAsc(session.id)).thenReturn(emptyList())
        whenever(branchRepository.findById(session.branchId)).thenReturn(Optional.of(branch))
        whenever(staffMemberRepository.findById(session.openedByStaffId)).thenReturn(Optional.of(staff))
        whenever(staffMemberRepository.findById(staff.id)).thenReturn(Optional.of(staff))

        val result =
            service.closeSession(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                CloseSessionRequest(countedClosingHalalas = 105500),
            )

        assertThat(result.status).isEqualTo("closed")
        assertThat(result.expectedClosing!!.halalas).isEqualTo(107000L)
        assertThat(result.difference!!.halalas).isEqualTo(-1500L)
        assertThat(result.countedClosing!!.halalas).isEqualTo(105500L)
    }

    @Test
    fun `closeSession rejects already closed session`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession(status = "closed")
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))

        assertThatThrownBy {
            service.closeSession(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                CloseSessionRequest(countedClosingHalalas = 50000),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.CONFLICT)
            })
    }

    // ── Rule 9: Reconcile requires closed session ───────────────────────────

    @Test
    fun `reconcileSession approve happy path`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession(status = "closed")
        session.expectedClosingHalalas = 107000
        session.countedClosingHalalas = 105500
        session.differenceHalalas = -1500
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any<CashDrawerSession>())).thenAnswer { it.arguments[0] as CashDrawerSession }
        whenever(entryRepository.findAllBySessionIdOrderByRecordedAtAsc(session.id)).thenReturn(emptyList())
        whenever(branchRepository.findById(session.branchId)).thenReturn(Optional.of(branch))
        whenever(staffMemberRepository.findById(session.openedByStaffId)).thenReturn(Optional.of(staff))
        whenever(staffMemberRepository.findById(staff.id)).thenReturn(Optional.of(staff))

        val result =
            service.reconcileSession(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                ReconcileSessionRequest(reconciliationStatus = "approved"),
            )

        assertThat(result.status).isEqualTo("reconciled")
        assertThat(result.reconciliationStatus).isEqualTo("approved")
    }

    @Test
    fun `reconcileSession rejects open session`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession(status = "open")
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))

        assertThatThrownBy {
            service.reconcileSession(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                ReconcileSessionRequest(reconciliationStatus = "approved"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.CONFLICT)
            })
    }

    // ── Rule 10: Flagged reconciliation requires notes ──────────────────────

    @Test
    fun `reconcileSession flag with notes succeeds`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession(status = "closed")
        session.expectedClosingHalalas = 50000
        session.countedClosingHalalas = 48000
        session.differenceHalalas = -2000
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any<CashDrawerSession>())).thenAnswer { it.arguments[0] as CashDrawerSession }
        whenever(entryRepository.findAllBySessionIdOrderByRecordedAtAsc(session.id)).thenReturn(emptyList())
        whenever(branchRepository.findById(session.branchId)).thenReturn(Optional.of(branch))
        whenever(staffMemberRepository.findById(session.openedByStaffId)).thenReturn(Optional.of(staff))
        whenever(staffMemberRepository.findById(staff.id)).thenReturn(Optional.of(staff))

        val result =
            service.reconcileSession(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                ReconcileSessionRequest(
                    reconciliationStatus = "flagged",
                    reconciliationNotes = "Shortage needs investigation",
                ),
            )

        assertThat(result.status).isEqualTo("reconciled")
        assertThat(result.reconciliationStatus).isEqualTo("flagged")
    }

    @Test
    fun `reconcileSession flag without notes rejected`() {
        stubOrgAndClub()
        stubUserAndStaff()
        val session = makeSession(status = "closed")
        whenever(sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(session.publicId, club.id))
            .thenReturn(Optional.of(session))

        assertThatThrownBy {
            service.reconcileSession(
                org.publicId,
                club.publicId,
                session.publicId,
                testUser.publicId,
                ReconcileSessionRequest(reconciliationStatus = "flagged"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            })
    }
}
