package com.liyaqa.auth

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.auth.dto.OtpMemberSummary
import com.liyaqa.auth.dto.OtpRequestRequest
import com.liyaqa.auth.dto.OtpVerifyRequest
import com.liyaqa.auth.dto.OtpVerifyResponse
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.OrganizationRepository
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
