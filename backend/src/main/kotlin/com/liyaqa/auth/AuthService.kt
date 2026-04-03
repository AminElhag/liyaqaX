package com.liyaqa.auth

import com.liyaqa.auth.dto.LoginRequest
import com.liyaqa.auth.dto.LoginResponse
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.security.JwtService
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
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
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
        )
    }

    private fun invalidCredentials() =
        ArenaException(
            HttpStatus.UNAUTHORIZED,
            "unauthorized",
            "Invalid email or password.",
        )
}
