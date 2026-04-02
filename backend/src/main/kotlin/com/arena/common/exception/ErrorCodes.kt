package com.arena.common.exception

object ErrorCodes {
    // Auth
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
    const val TOKEN_INVALID = "TOKEN_INVALID"
    const val TOKEN_REVOKED = "TOKEN_REVOKED"
    const val ACCOUNT_DISABLED = "ACCOUNT_DISABLED"

    // General
    const val RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val ACCESS_DENIED = "ACCESS_DENIED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}
