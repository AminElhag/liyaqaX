package com.arena.security

import com.arena.common.dto.ProblemDetailResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = header.substring(7)

        try {
            val claims = jwtService.parseToken(token)
            val userId = jwtService.getUserId(claims)
            val role = jwtService.getRole(claims)

            val authentication =
                UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_$role")),
                )
            SecurityContextHolder.getContext().authentication = authentication

            filterChain.doFilter(request, response)
        } catch (ex: ExpiredJwtException) {
            writeErrorResponse(
                response = response,
                status = 401,
                type = "https://arena.app/errors/token-expired",
                title = "Unauthorized",
                detail = "Access token has expired",
                instance = request.requestURI,
            )
        } catch (ex: JwtException) {
            writeErrorResponse(
                response = response,
                status = 401,
                type = "https://arena.app/errors/token-invalid",
                title = "Unauthorized",
                detail = "Invalid access token",
                instance = request.requestURI,
            )
        }
    }

    private fun writeErrorResponse(
        response: HttpServletResponse,
        status: Int,
        type: String,
        title: String,
        detail: String,
        instance: String,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(
            response.outputStream,
            ProblemDetailResponse(
                type = type,
                title = title,
                status = status,
                detail = detail,
                instance = instance,
            ),
        )
    }
}
