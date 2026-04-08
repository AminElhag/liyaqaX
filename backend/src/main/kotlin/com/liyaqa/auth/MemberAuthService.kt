package com.liyaqa.auth

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.auth.dto.OtpMemberSummary
import com.liyaqa.auth.dto.OtpRequestRequest
import com.liyaqa.auth.dto.OtpVerifyRequest
import com.liyaqa.auth.dto.OtpVerifyResponse
import com.liyaqa.auth.dto.RegistrationOtpRequestRequest
import com.liyaqa.auth.dto.RegistrationOtpVerifyRequest
import com.liyaqa.auth.dto.RegistrationOtpVerifyResponse
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

@Service
@Transactional(readOnly = true)
class MemberAuthService(
    private val memberRepository: MemberRepository,
    private val otpRepository: MemberOtpRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val jwtService: JwtService,
    private val auditService: AuditService,
    private val portalSettingsService: ClubPortalSettingsService,
) {
    private val log = LoggerFactory.getLogger(MemberAuthService::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        private const val OTP_LENGTH = 6
        private const val OTP_EXPIRY_MINUTES = 10L
        private const val MAX_OTP_REQUESTS = 3L
    }

    @Transactional
    fun requestOtp(request: OtpRequestRequest) {
        val now = Instant.now()

        // Rule 2 — rate limit
        val activeCount = otpRepository.countByPhoneAndUsedFalseAndExpiresAtAfter(request.phone, now)
        if (activeCount >= MAX_OTP_REQUESTS) {
            throw ArenaException(
                HttpStatus.TOO_MANY_REQUESTS,
                "rate-limit-exceeded",
                "Too many OTP requests. Please wait.",
            )
        }

        // Generate 6-digit OTP
        val otp = generateOtp()
        val otpHash = sha256(otp)

        // Store OTP
        otpRepository.save(
            MemberOtp(
                phone = request.phone,
                otpHash = otpHash,
                expiresAt = now.plusSeconds(OTP_EXPIRY_MINUTES * 60),
            ),
        )

        // Rule 1 — never reveal whether phone exists. Always return 200.
        // TODO(#arena-sms): replace with SMS gateway (Twilio/Unifonic)
        val member = memberRepository.findByPhoneAndDeletedAtIsNull(request.phone).orElse(null)
        if (member != null) {
            log.info("DEV OTP for {}: {}", request.phone, otp)
        }
    }

    @Transactional
    fun verifyOtp(request: OtpVerifyRequest): OtpVerifyResponse {
        val now = Instant.now()
        val otpHash = sha256(request.otp)

        // Rules 3, 4 — find unexpired, unused OTP with matching hash
        val otpRecord =
            otpRepository.findByPhoneAndOtpHashAndUsedFalseAndExpiresAtAfter(
                request.phone,
                otpHash,
                now,
            ).orElseThrow {
                ArenaException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "OTP expired or invalid.",
                )
            }

        // Mark as used immediately
        otpRecord.used = true

        // Find member by phone
        val member =
            memberRepository.findByPhoneAndDeletedAtIsNull(request.phone)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.UNAUTHORIZED,
                        "unauthorized",
                        "OTP expired or invalid.",
                    )
                }

        // Business rule 10 — pending_activation members cannot log in
        if (member.membershipStatus == "pending_activation") {
            otpRepository.save(otpRecord)
            throw ArenaException(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Your registration is pending staff approval.",
            )
        }

        // Link member to OTP record
        otpRecord.memberId = member.id
        otpRepository.save(otpRecord)

        // Build JWT claims
        val org =
            organizationRepository.findById(member.organizationId)
                .orElseThrow { internalError("Organization not found.") }
        val club =
            clubRepository.findById(member.clubId)
                .orElseThrow { internalError("Club not found.") }
        val branch =
            branchRepository.findById(member.branchId)
                .orElseThrow { internalError("Branch not found.") }

        val claims =
            mutableMapOf<String, Any>(
                "scope" to "member",
                "memberId" to member.publicId.toString(),
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
                "branchId" to branch.publicId.toString(),
            )

        val token =
            jwtService.generateToken(
                subject = member.publicId.toString(),
                claims = claims,
            )

        auditService.log(
            action = AuditAction.MEMBER_LOGIN,
            entityType = "Member",
            entityId = member.publicId.toString(),
            actorId = member.publicId.toString(),
            actorScope = "member",
            organizationId = org.publicId.toString(),
            clubId = club.publicId.toString(),
        )

        return OtpVerifyResponse(
            accessToken = token,
            member =
                OtpMemberSummary(
                    id = member.publicId,
                    firstName = member.firstNameEn,
                    lastName = member.lastNameEn,
                    preferredLanguage = member.preferredLanguage,
                ),
        )
    }

    @Transactional
    fun requestRegistrationOtp(request: RegistrationOtpRequestRequest) {
        val club =
            clubRepository.findByPublicIdAndDeletedAtIsNull(request.clubId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }

        val settings = portalSettingsService.getOrCreateSettings(club.id)
        if (!settings.selfRegistrationEnabled) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Self-registration is not enabled for this club.",
            )
        }

        val now = Instant.now()
        val activeCount = otpRepository.countByPhoneAndUsedFalseAndExpiresAtAfter(request.phone, now)
        if (activeCount >= MAX_OTP_REQUESTS) {
            throw ArenaException(
                HttpStatus.TOO_MANY_REQUESTS,
                "rate-limit-exceeded",
                "Too many OTP requests. Please wait.",
            )
        }

        val otp = generateOtp()
        val otpHash = sha256(otp)

        otpRepository.save(
            MemberOtp(
                phone = request.phone,
                otpHash = otpHash,
                expiresAt = now.plusSeconds(OTP_EXPIRY_MINUTES * 60),
            ),
        )

        log.info("DEV REGISTRATION OTP for {}: {}", request.phone, otp)
    }

    @Transactional
    fun verifyRegistrationOtp(request: RegistrationOtpVerifyRequest): RegistrationOtpVerifyResponse {
        val club =
            clubRepository.findByPublicIdAndDeletedAtIsNull(request.clubId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }

        val settings = portalSettingsService.getOrCreateSettings(club.id)
        if (!settings.selfRegistrationEnabled) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Self-registration is not enabled for this club.",
            )
        }

        val now = Instant.now()
        val otpHash = sha256(request.otp)

        val otpRecord =
            otpRepository.findByPhoneAndOtpHashAndUsedFalseAndExpiresAtAfter(
                request.phone,
                otpHash,
                now,
            ).orElseThrow {
                ArenaException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-otp", "OTP expired or invalid.")
            }

        // Check for existing member with this phone in this club
        val existingMember =
            memberRepository.findByPhoneAndClubIdAndDeletedAtIsNull(request.phone, club.id).orElse(null)

        if (existingMember != null) {
            when (existingMember.membershipStatus) {
                "active", "frozen" -> {
                    otpRecord.used = true
                    otpRepository.save(otpRecord)
                    throw ArenaException(
                        HttpStatus.CONFLICT,
                        "conflict",
                        "Phone number already registered at this club. Please log in.",
                    )
                }
                "pending_activation" -> {
                    otpRecord.used = true
                    otpRepository.save(otpRecord)
                    throw ArenaException(
                        HttpStatus.CONFLICT,
                        "conflict",
                        "Registration already pending staff review.",
                    )
                }
            }
        }

        otpRecord.used = true
        otpRepository.save(otpRecord)

        val token = jwtService.generateRegistrationToken(phone = request.phone, clubId = club.id)
        return RegistrationOtpVerifyResponse(registrationToken = token)
    }

    private fun generateOtp(): String {
        val otp = secureRandom.nextInt(999999)
        return otp.toString().padStart(OTP_LENGTH, '0')
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun internalError(detail: String) = ArenaException(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", detail)
}
