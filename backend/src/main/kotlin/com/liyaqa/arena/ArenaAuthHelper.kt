package com.liyaqa.arena

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.Member
import com.liyaqa.security.JwtClaims
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import java.util.UUID

fun Authentication.arenaContext(): JwtClaims {
    val claims =
        details as? JwtClaims
            ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")
    if (claims.scope != "member") {
        throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "This app is for gym members only.")
    }
    return claims
}

fun Member.requireNotLapsed() {
    if (membershipStatus == "lapsed") {
        throw ArenaException(
            status = HttpStatus.FORBIDDEN,
            errorType = "membership-lapsed",
            message = "Your membership has expired. Please contact the gym to renew.",
            errorCode = "MEMBERSHIP_LAPSED",
        )
    }
}

fun JwtClaims.requireMemberId(): UUID =
    memberId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No member scope in token.")

fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")

fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

fun JwtClaims.requireBranchId(): UUID =
    branchId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No branch scope in token.")
