package com.arena.auth

import com.arena.auth.dto.LoginResponse
import com.arena.common.exception.ErrorCodes
import com.arena.common.exception.UnauthorizedException
import com.arena.security.JwtService
import com.arena.token.RefreshTokenService
import com.arena.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun login(
        email: String,
        password: String,
        deviceInfo: String? = null,
    ): LoginResponse {
        val user =
            userRepository.findByEmailAndDeletedAtIsNull(email)
                ?: throw UnauthorizedException()

        if (!user.isActive) {
            throw UnauthorizedException(
                errorCode = ErrorCodes.ACCOUNT_DISABLED,
                message = "Invalid credentials",
            )
        }

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw UnauthorizedException()
        }

        user.lastLoginAt = Instant.now()
        userRepository.save(user)

        val accessToken =
            jwtService.generateAccessToken(
                userId = user.publicId,
                role = user.role,
            )

        val refreshToken = refreshTokenService.createToken(user, deviceInfo)

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtService.getAccessTokenExpiry().seconds,
        )
    }

    @Transactional
    fun refresh(
        rawRefreshToken: String,
        deviceInfo: String? = null,
    ): LoginResponse {
        val (newToken, newRawToken) = refreshTokenService.rotateToken(rawRefreshToken, deviceInfo)
        val user = newToken.user

        if (!user.isActive) {
            throw UnauthorizedException(
                errorCode = ErrorCodes.ACCOUNT_DISABLED,
                message = "Invalid credentials",
            )
        }

        val accessToken =
            jwtService.generateAccessToken(
                userId = user.publicId,
                role = user.role,
            )

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = newRawToken,
            expiresIn = jwtService.getAccessTokenExpiry().seconds,
        )
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        refreshTokenService.revokeToken(rawRefreshToken)
    }
}
