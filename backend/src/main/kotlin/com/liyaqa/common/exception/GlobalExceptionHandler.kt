package com.liyaqa.common.exception

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ArenaException::class)
    fun handleArenaException(
        ex: ArenaException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(ex.status, ex.message)
        problem.type = URI.create("https://arena.app/errors/${ex.errorType}")
        problem.title = ex.errorType.replace("-", " ").replaceFirstChar { it.uppercase() }
        problem.setProperty("instance", requestUri(request))
        return ResponseEntity.status(ex.status).body(problem)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val fieldErrors =
            ex.bindingResult.fieldErrors.map { error ->
                mapOf(
                    "field" to error.field,
                    "code" to (error.code ?: "INVALID"),
                    "message" to (error.defaultMessage ?: "Invalid value"),
                )
            }
        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more fields failed validation.",
            )
        problem.type = URI.create("https://arena.app/errors/validation-failed")
        problem.title = "Validation failed"
        problem.setProperty("instance", requestUri(request))
        problem.setProperty("errors", fieldErrors)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action.",
            )
        problem.type = URI.create("https://arena.app/errors/forbidden")
        problem.title = "Forbidden"
        problem.setProperty("instance", requestUri(request))
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<ProblemDetail> {
        logger.error("Unexpected error", ex)
        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
            )
        problem.type = URI.create("https://arena.app/errors/internal-error")
        problem.title = "Internal error"
        problem.setProperty("instance", requestUri(request))
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }

    private fun requestUri(request: WebRequest): String = (request as? ServletWebRequest)?.request?.requestURI ?: "unknown"
}
