package com.arena.common.exception

import com.arena.common.dto.FieldError
import com.arena.common.dto.ProblemDetailResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ArenaException::class)
    fun handleArenaException(
        ex: ArenaException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetailResponse> {
        val body =
            ProblemDetailResponse(
                type = "https://arena.app/errors/${ex.errorCode.lowercase().replace('_', '-')}",
                title = ex.httpStatus.reasonPhrase,
                status = ex.httpStatus.value(),
                detail = ex.message,
                instance = request.getDescription(false).removePrefix("uri="),
            )
        return ResponseEntity.status(ex.httpStatus).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetailResponse> {
        val fieldErrors =
            ex.bindingResult.fieldErrors.map { error ->
                FieldError(
                    field = error.field,
                    code = error.code?.uppercase() ?: "INVALID",
                    message = error.defaultMessage ?: "Invalid value",
                )
            }
        val body =
            ProblemDetailResponse(
                type = "https://arena.app/errors/validation-failed",
                title = "Validation failed",
                status = 400,
                detail = "One or more fields failed validation.",
                instance = request.getDescription(false).removePrefix("uri="),
                errors = fieldErrors,
            )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedRequest(
        ex: HttpMessageNotReadableException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetailResponse> {
        val body =
            ProblemDetailResponse(
                type = "https://arena.app/errors/validation-failed",
                title = "Bad Request",
                status = 400,
                detail = "Malformed or unreadable request body.",
                instance = request.getDescription(false).removePrefix("uri="),
            )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<ProblemDetailResponse> {
        log.error("Unhandled exception", ex)
        val body =
            ProblemDetailResponse(
                type = "https://arena.app/errors/internal-error",
                title = "Internal Server Error",
                status = 500,
                detail = "An unexpected error occurred. Our team has been notified.",
                instance = request.getDescription(false).removePrefix("uri="),
            )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}
