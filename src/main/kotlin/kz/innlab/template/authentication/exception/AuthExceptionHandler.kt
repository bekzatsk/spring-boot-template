package kz.innlab.template.authentication.exception

import kz.innlab.template.shared.error.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

@RestControllerAdvice
class AuthExceptionHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(AuthExceptionHandler::class.java)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(
            ErrorResponse(error = "Bad Request", message = message, status = 400)
        )
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(ex: HandlerMethodValidationException): ResponseEntity<ErrorResponse> {
        val message = ex.allErrors.joinToString("; ") { it.defaultMessage ?: "Invalid value" }
        return ResponseEntity.badRequest().body(
            ErrorResponse(error = "Bad Request", message = message, status = 400)
        )
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(error = "Unauthorized", message = ex.message ?: "Invalid credentials", status = 401)
        )

    @ExceptionHandler(TokenGracePeriodException::class)
    fun handleGracePeriod(ex: TokenGracePeriodException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(error = "Conflict", message = ex.message ?: "Token already rotated", status = 409)
        )

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception: {}", ex.message, ex)
        return ResponseEntity.internalServerError().body(
            ErrorResponse(error = "Internal Server Error", message = "An unexpected error occurred", status = 500)
        )
    }
}
