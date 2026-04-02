package com.arena.common.exception

import org.springframework.http.HttpStatus

open class ArenaException(
    val errorCode: String,
    val httpStatus: HttpStatus,
    override val message: String,
) : RuntimeException(message)

class UnauthorizedException(
    errorCode: String = ErrorCodes.INVALID_CREDENTIALS,
    message: String = "Invalid credentials",
) : ArenaException(errorCode, HttpStatus.UNAUTHORIZED, message)

class ForbiddenException(
    message: String = "Access denied",
) : ArenaException(ErrorCodes.ACCESS_DENIED, HttpStatus.FORBIDDEN, message)

class NotFoundException(
    message: String = "Resource not found",
) : ArenaException(ErrorCodes.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message)

class BusinessRuleException(
    errorCode: String,
    message: String,
) : ArenaException(errorCode, HttpStatus.UNPROCESSABLE_ENTITY, message)
