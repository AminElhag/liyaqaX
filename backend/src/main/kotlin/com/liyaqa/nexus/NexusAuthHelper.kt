package com.liyaqa.nexus

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.security.JwtClaims
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication

fun Authentication.nexusContext(): JwtClaims {
    val claims =
        details as? JwtClaims
            ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")
    if (claims.scope != "platform") {
        throw ArenaException(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "This app is for Liyaqa platform staff only.",
        )
    }
    return claims
}
