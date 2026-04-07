package com.liyaqa.member

import com.liyaqa.arena.dto.RegistrationCompleteResponse
import com.liyaqa.arena.dto.SelfRegistrationRequest
import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.notification.events.MemberCreatedEvent
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import io.jsonwebtoken.Claims
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SelfRegistrationService(
    private val memberRepository: MemberRepository,
    private val memberRegistrationIntentRepository: MemberRegistrationIntentRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val clubRepository: ClubRepository,
    private val organizationRepository: OrganizationRepository,
    private val branchRepository: BranchRepository,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val portalSettingsService: ClubPortalSettingsService,
    private val passwordEncoder: PasswordEncoder,
    private val auditService: AuditService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun register(
        claims: Claims,
        request: SelfRegistrationRequest,
    ): RegistrationCompleteResponse {
        val phone =
            claims["phone"] as? String
                ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid registration token.")
        val clubInternalId =
            (claims["clubId"] as? Number)?.toLong()
                ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid registration token.")

        val club =
            clubRepository.findById(clubInternalId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }
        val org =
            organizationRepository.findById(club.organizationId)
                .orElseThrow { internalError("Organization not found.") }

        // Business rule 1 — self-registration must be enabled
        val settings = portalSettingsService.getOrCreateSettings(club.id)
        if (!settings.selfRegistrationEnabled) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Self-registration is not enabled for this club.",
            )
        }

        // Business rule 2 — phone uniqueness per club
        val existingMember = memberRepository.findByPhoneAndClubIdAndDeletedAtIsNull(phone, club.id).orElse(null)
        if (existingMember != null && existingMember.membershipStatus in listOf("active", "frozen", "pending_activation")) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Phone number already registered at this club.",
            )
        }

        // Business rule 5 — name required in at least one language
        if (request.nameEn.isNullOrBlank() && request.nameAr.isNullOrBlank()) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation-failed",
                "At least one name (English or Arabic) is required.",
            )
        }

        // Business rule 6 — plan must belong to this club (if provided)
        val plan =
            request.desiredMembershipPlanId?.let { planId ->
                membershipPlanRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(planId, club.id)
                    .orElseThrow {
                        ArenaException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "business-rule-violation",
                            "Membership plan not found or does not belong to this club.",
                        )
                    }
            }

        // Find default branch for this club
        val branch =
            branchRepository.findFirstByClubIdAndDeletedAtIsNullOrderByIdAsc(club.id)
                .orElseThrow { internalError("No branches found for this club.") }

        // Create User with placeholder credentials (OTP-only auth)
        val email =
            request.email?.takeIf { it.isNotBlank() }
                ?: "${phone.replace("+", "")}@self-registration.internal"

        // Check email uniqueness
        if (userRepository.findByEmailAndDeletedAtIsNull(email).isPresent) {
            if (request.email != null) {
                throw ArenaException(
                    HttpStatus.CONFLICT,
                    "conflict",
                    "A user with this email already exists.",
                )
            }
            // Placeholder email collision — use UUID-based fallback
            val fallbackEmail = "${UUID.randomUUID()}@self-registration.internal"
            return createMemberAndIntent(fallbackEmail, phone, club, org.id, branch.id, request, plan, claims)
        }

        return createMemberAndIntent(email, phone, club, org.id, branch.id, request, plan, claims)
    }

    private fun createMemberAndIntent(
        email: String,
        phone: String,
        club: Club,
        organizationId: Long,
        branchId: Long,
        request: SelfRegistrationRequest,
        plan: com.liyaqa.membership.MembershipPlan?,
        claims: Claims,
    ): RegistrationCompleteResponse {
        val randomPassword = generateRandomPassword()
        val user =
            userRepository.save(
                User(
                    email = email,
                    passwordHash = passwordEncoder.encode(randomPassword),
                    organizationId = organizationId,
                    clubId = club.id,
                ),
            )

        // Assign member role
        val memberRole =
            roleRepository
                .findByScopeAndOrganizationIdAndClubIdAndDeletedAtIsNull("member", organizationId, club.id)
                .orElseThrow { internalError("Member role not found for this club.") }
        userRoleRepository.save(UserRole(userId = user.id, roleId = memberRole.id))

        // Split name into first/last (use full name as first name if no space)
        val nameEn = request.nameEn ?: ""
        val nameAr = request.nameAr ?: ""
        val (firstNameEn, lastNameEn) = splitName(nameEn)
        val (firstNameAr, lastNameAr) = splitName(nameAr)

        val member =
            memberRepository.save(
                Member(
                    organizationId = organizationId,
                    clubId = club.id,
                    branchId = branchId,
                    userId = user.id,
                    firstNameAr = firstNameAr,
                    firstNameEn = firstNameEn,
                    lastNameAr = lastNameAr,
                    lastNameEn = lastNameEn,
                    phone = phone,
                    dateOfBirth = request.dateOfBirth,
                    gender = request.gender,
                    membershipStatus = "pending_activation",
                ),
            )

        // Create emergency contact if provided
        if (!request.emergencyContactName.isNullOrBlank() && !request.emergencyContactPhone.isNullOrBlank()) {
            emergencyContactRepository.save(
                EmergencyContact(
                    memberId = member.id,
                    organizationId = organizationId,
                    nameEn = request.emergencyContactName,
                    nameAr = request.emergencyContactName,
                    phone = request.emergencyContactPhone,
                    relationship = null,
                ),
            )
        }

        // Create registration intent if plan was selected
        if (plan != null) {
            memberRegistrationIntentRepository.save(
                MemberRegistrationIntent(
                    memberId = member.id,
                    memberPublicId = member.publicId,
                    membershipPlanId = plan.id,
                    membershipPlanPublicId = plan.publicId,
                    membershipPlanNameEn = plan.nameEn,
                    membershipPlanNameAr = plan.nameAr,
                    membershipPlanPriceHalalas = plan.priceHalalas,
                    clubId = club.id,
                ),
            )
        }

        // Audit
        auditService.log(
            action = AuditAction.MEMBER_SELF_REGISTERED,
            entityType = "Member",
            entityId = member.publicId.toString(),
            actorId = member.publicId.toString(),
            actorScope = "registration",
            organizationId =
                club.organizationId.let {
                    organizationRepository.findById(it).map { o -> o.publicId.toString() }.orElse(null)
                },
            clubId = club.publicId.toString(),
        )

        // Publish event — triggers staff notification via existing MemberCreatedEvent listener
        eventPublisher.publishEvent(
            MemberCreatedEvent(
                memberPublicId = member.publicId,
                memberName = "${member.firstNameEn} ${member.lastNameEn}".trim(),
                branchId = member.branchId,
                clubId = member.clubId,
            ),
        )

        return RegistrationCompleteResponse(
            memberId = member.publicId,
            status = "pending_activation",
        )
    }

    private fun splitName(fullName: String): Pair<String, String> {
        if (fullName.isBlank()) return "" to ""
        val parts = fullName.trim().split(" ", limit = 2)
        return parts[0] to (parts.getOrNull(1) ?: "")
    }

    private fun generateRandomPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#"
        return (1..32).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    private fun internalError(detail: String) = ArenaException(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", detail)
}
