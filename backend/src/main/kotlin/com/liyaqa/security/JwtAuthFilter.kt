package com.liyaqa.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            try {
                val claims = jwtService.parseToken(header.substring(7))
                val jwtClaims =
                    JwtClaims(
                        userPublicId =
                            runCatching { UUID.fromString(claims.subject) }.getOrNull(),
                        roleId =
                            (claims["roleId"] as? String)
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        scope = claims["scope"] as? String,
                        organizationId =
                            (claims["organizationId"] as? String)
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        clubId =
                            (claims["clubId"] as? String)
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        trainerId =
                            (claims["trainerId"] as? String)
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        trainerTypes =
                            (claims["trainerTypes"] as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                        branchIds =
                            (claims["branchIds"] as? List<*>)
                                ?.mapNotNull { (it as? String)?.let { s -> runCatching { UUID.fromString(s) }.getOrNull() } }
                                ?: emptyList(),
                        memberId =
                            (claims["memberId"] as? String)
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        branchId =
                            (claims["branchId"] as? String)
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                    )
                val auth = UsernamePasswordAuthenticationToken(claims.subject, null, emptyList())
                auth.details = jwtClaims
                SecurityContextHolder.getContext().authentication = auth
            } catch (_: Exception) {
                // Invalid token — continue without authentication
            }
        }
        filterChain.doFilter(request, response)
    }
}
