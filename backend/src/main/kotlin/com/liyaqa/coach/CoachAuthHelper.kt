package com.liyaqa.coach

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.security.JwtClaims
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import java.util.UUID

fun Authentication.coachContext(): JwtClaims {
    val claims =
        details as? JwtClaims
            ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")
    if (claims.scope != "trainer") {
        throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "This app is for trainers only.")
    }
    return claims
}

fun JwtClaims.requireTrainerId(): UUID =
    trainerId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No trainer scope in token.")

fun JwtClaims.requireCoachClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")

fun JwtClaims.requireCoachOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

fun JwtClaims.requireTrainerType(type: String) {
    if (type !in trainerTypes) {
        val label = if (type == "pt") "PT trainer" else "GX instructor"
        throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "Not a $label.")
    }
}
