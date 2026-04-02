package com.arena.common.tenant

import com.arena.security.JwtService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class TenantInterceptor(
    private val jwtService: JwtService,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            try {
                val claims = jwtService.parseToken(token)
                TenantContext.set(
                    TenantScope(
                        userId = jwtService.getUserId(claims),
                        role = jwtService.getRole(claims),
                        organizationId = jwtService.getOrganizationId(claims),
                        clubId = jwtService.getClubId(claims),
                        branchIds = jwtService.getBranchIds(claims),
                        memberId = jwtService.getMemberId(claims),
                    ),
                )
            } catch (_: Exception) {
                // Token parsing failures are handled by JwtAuthFilter — not here
            }
        }
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        TenantContext.clear()
    }
}
