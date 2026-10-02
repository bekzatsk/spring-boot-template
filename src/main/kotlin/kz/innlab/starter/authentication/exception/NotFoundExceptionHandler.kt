package kz.innlab.starter.authentication.exception

import kz.innlab.starter.shared.error.ErrorResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * JSON 404 for paths no controller maps — including the endpoints of a disabled provider.
 *
 * Split from [AuthExceptionHandler] because an unmapped path belongs to no controller, so the
 * package-scoped handler never sees it. Lowest precedence, so any advice the consumer application
 * declares for the same exception wins.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class NotFoundExceptionHandler {

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(error = "Not Found", message = "Endpoint not found", status = 404)
        )
}
