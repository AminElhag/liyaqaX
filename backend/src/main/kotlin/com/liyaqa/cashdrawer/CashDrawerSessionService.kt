package com.liyaqa.cashdrawer

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.cashdrawer.dto.BranchSummary
import com.liyaqa.cashdrawer.dto.CashDrawerEntryResponse
import com.liyaqa.cashdrawer.dto.CashDrawerSessionResponse
import com.liyaqa.cashdrawer.dto.CashDrawerSessionSummaryResponse
import com.liyaqa.cashdrawer.dto.CloseSessionRequest
import com.liyaqa.cashdrawer.dto.CreateEntryRequest
import com.liyaqa.cashdrawer.dto.MoneyResponse
import com.liyaqa.cashdrawer.dto.OpenSessionRequest
import com.liyaqa.cashdrawer.dto.ReconcileSessionRequest
import com.liyaqa.cashdrawer.dto.StaffSummary
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CashDrawerSessionService(
    private val sessionRepository: CashDrawerSessionRepository,
    private val entryRepository: CashDrawerEntryRepository,
    private val branchRepository: BranchRepository,
    private val staffMemberRepository: StaffMemberRepository,
    private val paymentRepository: PaymentRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val auditService: AuditService,
) {
    // ── Open session ────���────────────────────────────────────────────────────

    @Transactional
    fun openSession(
        orgPublicId: UUID,
        clubPublicId: UUID,
        userPublicId: UUID,
        branchPublicId: UUID,
        request: OpenSessionRequest,
    ): CashDrawerSessionResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val staff = findStaffByUserOrThrow(userPublicId, club.id)

        // Rule 3: Opening float non-negative (also validated by @Min on DTO)
        if (request.openingFloatHalalas < 0) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Opening float must be non-negative.",
            )
        }

        // Rule 2: Branch belongs to club
        val branch = findBranchOrThrow(branchPublicId, org.id, club.id)

        // Rule 1: One open session per branch
        if (sessionRepository.findByBranchIdAndStatusAndDeletedAtIsNull(branch.id, "open").isPresent) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A session is already open for this branch.",
            )
        }

        val session =
            sessionRepository.save(
                CashDrawerSession(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    openedByStaffId = staff.id,
                    openingFloatHalalas = request.openingFloatHalalas,
                ),
            )

        auditService.logFromContext(
            action = AuditAction.CASH_DRAWER_SESSION_OPENED,
            entityType = "CashDrawerSession",
            entityId = session.publicId.toString(),
        )

        return toFullResponse(session, club)
    }

    // ── Add entry ────────────────────────────────────────���───────────────────

    @Transactional
    fun addEntry(
        orgPublicId: UUID,
        clubPublicId: UUID,
        sessionPublicId: UUID,
        userPublicId: UUID,
        request: CreateEntryRequest,
    ): CashDrawerEntryResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val session = findSessionOrThrow(sessionPublicId, club.id)
        val staff = findStaffByUserOrThrow(userPublicId, club.id)

        // Rule 4: Entries only on open sessions
        if (session.status != "open") {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Session is not open.",
            )
        }

        // Rule 5: Entry amount positive (also validated by @Min on DTO)
        if (request.amountHalalas <= 0) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Entry amount must be greater than zero.",
            )
        }

        // Rule 6: Optional payment reference must belong to same club
        var paymentInternalId: Long? = null
        if (request.paymentId != null) {
            val payment =
                paymentRepository.findByPublicIdAndOrganizationId(request.paymentId, org.id)
                    .orElseThrow {
                        ArenaException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "business-rule-violation",
                            "Payment not found or does not belong to this club.",
                        )
                    }
            if (payment.clubId != club.id) {
                throw ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Payment not found or does not belong to this club.",
                )
            }
            paymentInternalId = payment.id
        }

        val entry =
            entryRepository.save(
                CashDrawerEntry(
                    sessionId = session.id,
                    staffId = staff.id,
                    paymentId = paymentInternalId,
                    entryType = request.entryType,
                    amountHalalas = request.amountHalalas,
                    description = request.description,
                    recordedAt = request.recordedAt ?: Instant.now(),
                ),
            )

        auditService.logFromContext(
            action = AuditAction.CASH_DRAWER_ENTRY_ADDED,
            entityType = "CashDrawerEntry",
            entityId = entry.publicId.toString(),
        )

        return toEntryResponse(entry, staff)
    }

    // ── Close session ───────────��────────────────────────────────────────────

    @Transactional
    fun closeSession(
        orgPublicId: UUID,
        clubPublicId: UUID,
        sessionPublicId: UUID,
        userPublicId: UUID,
        request: CloseSessionRequest,
    ): CashDrawerSessionResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val session = findSessionOrThrow(sessionPublicId, club.id)
        val staff = findStaffByUserOrThrow(userPublicId, club.id)

        // Rule 7: Close requires open session
        if (session.status != "open") {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Session is not open.",
            )
        }

        // Rule 8: Expected balance computed server-side
        val totalCashIn = entryRepository.sumBySessionIdAndEntryType(session.id, "cash_in")
        val totalCashOut = entryRepository.sumBySessionIdAndEntryType(session.id, "cash_out")
        val totalFloatAdj = entryRepository.sumBySessionIdAndEntryType(session.id, "float_adjustment")

        val expectedClosing = session.openingFloatHalalas + totalCashIn - totalCashOut + totalFloatAdj
        val difference = request.countedClosingHalalas - expectedClosing

        session.status = "closed"
        session.closedByStaffId = staff.id
        session.countedClosingHalalas = request.countedClosingHalalas
        session.expectedClosingHalalas = expectedClosing
        session.differenceHalalas = difference
        session.closedAt = Instant.now()

        sessionRepository.save(session)

        auditService.logFromContext(
            action = AuditAction.CASH_DRAWER_SESSION_CLOSED,
            entityType = "CashDrawerSession",
            entityId = session.publicId.toString(),
        )

        return toFullResponse(session, club)
    }

    // ── Reconcile session ────────────────────────────────────────────────────

    @Transactional
    fun reconcileSession(
        orgPublicId: UUID,
        clubPublicId: UUID,
        sessionPublicId: UUID,
        userPublicId: UUID,
        request: ReconcileSessionRequest,
    ): CashDrawerSessionResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val session = findSessionOrThrow(sessionPublicId, club.id)
        val staff = findStaffByUserOrThrow(userPublicId, club.id)

        // Rule 9: Reconcile requires closed session
        if (session.status != "closed") {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Session must be closed before reconciliation.",
            )
        }

        // Rule 10: Flagged reconciliation requires notes
        if (request.reconciliationStatus == "flagged" && request.reconciliationNotes.isNullOrBlank()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Reconciliation notes are required when flagging a session.",
            )
        }

        session.status = "reconciled"
        session.reconciledByStaffId = staff.id
        session.reconciliationStatus = request.reconciliationStatus
        session.reconciliationNotes = request.reconciliationNotes
        session.reconciledAt = Instant.now()

        sessionRepository.save(session)

        return toFullResponse(session, club)
    }

    // ��─ Read operations ───────��──────────────────────────────────────────────

    fun getCurrentSession(
        orgPublicId: UUID,
        clubPublicId: UUID,
        branchPublicId: UUID,
    ): CashDrawerSessionResponse? {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val branch = findBranchOrThrow(branchPublicId, org.id, club.id)

        val session =
            sessionRepository.findByBranchIdAndStatusAndDeletedAtIsNull(branch.id, "open")
                .orElse(null) ?: return null

        return toFullResponse(session, club)
    }

    fun getSession(
        orgPublicId: UUID,
        clubPublicId: UUID,
        sessionPublicId: UUID,
    ): CashDrawerSessionResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val session = findSessionOrThrow(sessionPublicId, club.id)
        return toFullResponse(session, club)
    }

    fun listSessions(
        orgPublicId: UUID,
        clubPublicId: UUID,
        branchPublicId: UUID?,
        status: String?,
        pageable: Pageable,
    ): PageResponse<CashDrawerSessionSummaryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        val branchId =
            if (branchPublicId != null) {
                findBranchOrThrow(branchPublicId, org.id, club.id).id
            } else {
                null
            }

        return sessionRepository
            .findAllFiltered(club.id, branchId, status, pageable)
            .map { toSummaryResponse(it, club) }
            .toPageResponse()
    }

    fun listEntries(
        orgPublicId: UUID,
        clubPublicId: UUID,
        sessionPublicId: UUID,
    ): List<CashDrawerEntryResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val session = findSessionOrThrow(sessionPublicId, club.id)

        val entries = entryRepository.findAllBySessionIdOrderByRecordedAtAsc(session.id)
        return entries.map { entry ->
            val staff = staffMemberRepository.findById(entry.staffId).orElse(null)
            toEntryResponse(entry, staff)
        }
    }

    // ── Private helpers ─────��──────────────────────��─────────────────────────

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.")
            }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ): Club =
        clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
            }

    private fun findBranchOrThrow(
        branchPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Branch =
        branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branchPublicId, organizationId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.")
            }

    private fun findSessionOrThrow(
        sessionPublicId: UUID,
        clubId: Long,
    ): CashDrawerSession =
        sessionRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(sessionPublicId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Cash drawer session not found.")
            }

    private fun findStaffByUserOrThrow(
        userPublicId: UUID,
        clubId: Long,
    ): StaffMember {
        val user =
            userRepository.findByPublicIdAndDeletedAtIsNull(userPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "User not found.")
                }
        return staffMemberRepository.findByUserIdAndClubIdAndDeletedAtIsNull(user.id, clubId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "business-rule-violation",
                    "Current user is not a staff member for this club.",
                )
            }
    }

    // ── Response mapping ────────��────────────────────────────────────────────

    private fun toFullResponse(
        session: CashDrawerSession,
        club: Club,
    ): CashDrawerSessionResponse {
        val branch = branchRepository.findById(session.branchId).orElse(null)
        val openedByStaff = staffMemberRepository.findById(session.openedByStaffId).orElse(null)
        val closedByStaff = session.closedByStaffId?.let { staffMemberRepository.findById(it).orElse(null) }
        val reconciledByStaff = session.reconciledByStaffId?.let { staffMemberRepository.findById(it).orElse(null) }

        val entries = entryRepository.findAllBySessionIdOrderByRecordedAtAsc(session.id)
        val totalCashIn = entries.filter { it.entryType == "cash_in" }.sumOf { it.amountHalalas }
        val totalCashOut = entries.filter { it.entryType == "cash_out" }.sumOf { it.amountHalalas }

        return CashDrawerSessionResponse(
            id = session.publicId,
            status = session.status,
            branch = branch.toBranchSummary(),
            openedBy = openedByStaff.toStaffSummary(),
            closedBy = closedByStaff?.toStaffSummary(),
            reconciledBy = reconciledByStaff?.toStaffSummary(),
            openingFloat = session.openingFloatHalalas.toMoney(),
            countedClosing = session.countedClosingHalalas?.toMoney(),
            expectedClosing = session.expectedClosingHalalas?.toMoney(),
            difference = session.differenceHalalas?.toMoney(),
            reconciliationStatus = session.reconciliationStatus,
            reconciliationNotes = session.reconciliationNotes,
            openedAt = session.openedAt,
            closedAt = session.closedAt,
            reconciledAt = session.reconciledAt,
            totalCashIn = totalCashIn.toMoney(),
            totalCashOut = totalCashOut.toMoney(),
            entryCount = entries.size,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
        )
    }

    private fun toSummaryResponse(
        session: CashDrawerSession,
        club: Club,
    ): CashDrawerSessionSummaryResponse {
        val branch = branchRepository.findById(session.branchId).orElse(null)
        val openedByStaff = staffMemberRepository.findById(session.openedByStaffId).orElse(null)

        return CashDrawerSessionSummaryResponse(
            id = session.publicId,
            status = session.status,
            branch = branch.toBranchSummary(),
            openedBy = openedByStaff.toStaffSummary(),
            openingFloat = session.openingFloatHalalas.toMoney(),
            difference = session.differenceHalalas?.toMoney(),
            reconciliationStatus = session.reconciliationStatus,
            openedAt = session.openedAt,
            closedAt = session.closedAt,
        )
    }

    private fun toEntryResponse(
        entry: CashDrawerEntry,
        staff: StaffMember?,
    ): CashDrawerEntryResponse {
        val paymentPublicId =
            entry.paymentId?.let { pid ->
                paymentRepository.findById(pid).map { it.publicId }.orElse(null)
            }

        return CashDrawerEntryResponse(
            id = entry.publicId,
            entryType = entry.entryType,
            amount = entry.amountHalalas.toMoney(),
            description = entry.description,
            paymentId = paymentPublicId,
            recordedBy = staff?.toStaffSummary() ?: StaffSummary(UUID.randomUUID(), "Unknown", "Staff"),
            recordedAt = entry.recordedAt,
        )
    }

    private fun Long.toMoney(): MoneyResponse =
        MoneyResponse(
            halalas = this,
            sar = "%.2f".format(this / 100.0),
        )

    private fun Branch?.toBranchSummary(): BranchSummary =
        if (this != null) BranchSummary(id = publicId, name = nameEn) else BranchSummary(UUID.randomUUID(), "Unknown")

    private fun StaffMember?.toStaffSummary(): StaffSummary =
        if (this != null) {
            StaffSummary(id = publicId, firstName = firstNameEn, lastName = lastNameEn)
        } else {
            StaffSummary(UUID.randomUUID(), "Unknown", "Staff")
        }
}
