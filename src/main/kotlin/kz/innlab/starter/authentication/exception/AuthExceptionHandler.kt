package kz.innlab.starter.authentication.exception

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.access.AccessDeniedException
import kz.innlab.starter.shared.error.ErrorResponse
import kz.innlab.starter.shared.error.ForbiddenOperationException
import kz.innlab.starter.shared.error.ResourceNotFoundException
import kz.innlab.starter.shared.ratelimit.RateLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

// Scoped to the starter's controllers: unscoped, it would answer for the consumer application too
// and echo the message of any IllegalStateException/IllegalArgumentException its code throws.
@RestControllerAdvice(basePackages = ["kz.innlab.starter"])
class AuthExceptionHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(AuthExceptionHandler::class.java)
        private const val STARTER_PACKAGE = "kz.innlab.starter."
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

    /**
     * Unparseable body, wrong types, or a missing field that maps to a non-nullable Kotlin
     * property. Jackson throws before validation runs, so without this the caller got a 500.
     * The reason is deliberately generic: parser messages leak type and field internals.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.debug("Rejected unreadable request body: {}", ex.message)
        return ResponseEntity.badRequest().body(
            ErrorResponse(error = "Bad Request", message = "Malformed or incomplete request body", status = 400)
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

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(ex: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(error = "Conflict", message = clientMessage(ex, "Resource conflict"), status = 409)
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(
            ErrorResponse(error = "Bad Request", message = clientMessage(ex, "Invalid request"), status = 400)
        )

    // Malformed path variables (/users/not-a-uuid) and missing query parameters are client errors;
    // the catch-all below answered them with 500 and an ERROR log.
    @ExceptionHandler(MethodArgumentTypeMismatchException::class, MissingServletRequestParameterException::class)
    fun handleBadParameter(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(
            ErrorResponse(error = "Bad Request", message = "Invalid or missing request parameter", status = 400)
        )

    // A concurrent update (optimistic lock) or a unique-constraint race is a conflict the client
    // can retry, not a server failure.
    @ExceptionHandler(OptimisticLockingFailureException::class, DataIntegrityViolationException::class)
    fun handleConcurrentConflict(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(error = "Conflict", message = "The resource changed concurrently; retry", status = 409)
        )

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimited(ex: RateLimitExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(
                ErrorResponse(
                    error = "Too Many Requests",
                    message = ex.message ?: "Too many attempts",
                    status = 429
                )
            )

    // @PreAuthorize refusals reach the controller advice, not the security filter's access-denied
    // handler; without this the catch-all below turned them into 500.
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(error = "Forbidden", message = "Access denied", status = 403)
        )

    @ExceptionHandler(ForbiddenOperationException::class)
    fun handleForbidden(ex: ForbiddenOperationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(error = "Forbidden", message = ex.message ?: "Operation not allowed", status = 403)
        )

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(error = "Not Found", message = ex.message ?: "Resource not found", status = 404)
        )

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception: {}", ex.message, ex)
        return ResponseEntity.internalServerError().body(
            ErrorResponse(error = "Internal Server Error", message = "An unexpected error occurred", status = 500)
        )
    }

    /**
     * The message of an IllegalState/IllegalArgument exception is meant for the client only when
     * the starter threw it (`require`, `check`, `throw` in starter code — all of which put a
     * starter frame on top). Thrown inside a library (Firebase, mail, libphonenumber, Nimbus,
     * Enum.valueOf) it can describe internals, so the client gets the generic text.
     */
    private fun clientMessage(ex: RuntimeException, fallback: String): String {
        val thrownByStarter = ex.stackTrace.firstOrNull()?.className?.startsWith(STARTER_PACKAGE) == true
        return ex.message?.takeIf { thrownByStarter } ?: fallback
    }
}
