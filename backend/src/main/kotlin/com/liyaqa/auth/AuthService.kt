package com.liyaqa.auth

import com.liyaqa.auth.dto.LoginRequest
import com.liyaqa.auth.dto.LoginResponse
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.security.JwtService
import com.liyaqa.trainer.TrainerBranchAssignmentRepository
import com.liyaqa.trainer.TrainerRepository
import com.liyaqa.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val trainerRepository: TrainerRepository,
    private val trainerBranchAssignmentRepository: TrainerBranchAssignmentRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {
    fun login(request: LoginRequest): LoginResponse {
        val user =
            userRepository.findByEmailAndDeletedAtIsNull(request.email)
                .orElseThrow { invalidCredentials() }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw invalidCredentials()
        }

        val userRole =
            userRoleRepository.findByUserId(user.id)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "https://arena.app/errors/internal-error",
                        "No role assigned to user.",
                    )
                }

        val role =
            roleRepository.findById(userRole.roleId)
                .orElseThrow {
                    ArenaException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "https://arena.app/errors/internal-error",
                        "Role not found.",
                    )
                }

        val orgPublicId =
            user.organizationId?.let { orgId ->
                organizationRepository.findById(orgId).map { it.publicId }.orElse(null)
            }

        val clubPublicId =
            user.clubId?.let { cId ->
                clubRepository.findById(cId).map { it.publicId }.orElse(null)
            }

        val claims =
            mutableMapOf<String, Any>(
                "roleId" to role.publicId.toString(),
                "scope" to role.scope,
            )
        orgPublicId?.let { claims["organizationId"] = it.toString() }
        clubPublicId?.let { claims["clubId"] = it.toString() }

        var trainerPublicId: java.util.UUID? = null
        var trainerTypes: List<String> = emptyList()
        var branchIds: List<java.util.UUID> = emptyList()

        if (role.scope == "trainer") {
            val trainer =
                trainerRepository.findByUserIdAndDeletedAtIsNull(user.id)
                    .orElse(null)

            if (trainer != null) {
                trainerPublicId = trainer.publicId
                claims["trainerId"] = trainer.publicId.toString()

                val assignments = trainerBranchAssignmentRepository.findAllByTrainerId(trainer.id)
                branchIds =
                    assignments.mapNotNull { assignment ->
                        branchRepository.findById(assignment.branchId)
                            .map { it.publicId }.orElse(null)
                    }
                if (branchIds.isNotEmpty()) {
                    claims["branchIds"] = branchIds.map { it.toString() }
                }

                trainerTypes = deriveTrainerTypes(role.nameEn)
                if (trainerTypes.isNotEmpty()) {
                    claims["trainerTypes"] = trainerTypes
                }
            }
        }

        var memberPublicId: java.util.UUID? = null
        var memberBranchPublicId: java.util.UUID? = null

        if (role.scope == "member") {
            val member =
                memberRepository.findByUserIdAndDeletedAtIsNull(user.id)
                    .orElse(null)

            if (member != null) {
                memberPublicId = member.publicId
                claims["memberId"] = member.publicId.toString()

                memberBranchPublicId =
                    branchRepository.findById(member.branchId)
                        .map { it.publicId }.orElse(null)
                memberBranchPublicId?.let { claims["branchId"] = it.toString() }
            }
        }

        val token =
            jwtService.generateToken(
                subject = user.publicId.toString(),
                claims = claims,
            )

        return LoginResponse(
            accessToken = token,
            userId = user.publicId,
            scope = role.scope,
            roleId = role.publicId,
            roleName = role.nameEn,
            organizationId = orgPublicId,
            clubId = clubPublicId,
            trainerId = trainerPublicId,
            trainerTypes = trainerTypes.ifEmpty { null },
            branchIds = branchIds.ifEmpty { null },
            memberId = memberPublicId,
            branchId = memberBranchPublicId,
        )
    }

    private fun deriveTrainerTypes(roleNameEn: String): List<String> {
        val types = mutableListOf<String>()
        val normalized = roleNameEn.lowercase()
        if ("pt" in normalized || "personal" in normalized) types.add("pt")
        if ("gx" in normalized || "group" in normalized) types.add("gx")
        return types
    }

    private fun invalidCredentials() =
        ArenaException(
            HttpStatus.UNAUTHORIZED,
            "unauthorized",
            "Invalid email or password.",
        )
}
