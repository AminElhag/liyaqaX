package com.arena.auth

import com.arena.auth.dto.LoginRequest
import com.arena.auth.dto.LoginResponse
import com.arena.auth.dto.LogoutRequest
import com.arena.auth.dto.RefreshRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<LoginResponse> {
        val response =
            authService.login(
                email = request.email,
                password = request.password,
            )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshRequest,
    ): ResponseEntity<LoginResponse> {
        val response =
            authService.refresh(
                rawRefreshToken = request.refreshToken,
            )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ): ResponseEntity<Void> {
        authService.logout(rawRefreshToken = request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
