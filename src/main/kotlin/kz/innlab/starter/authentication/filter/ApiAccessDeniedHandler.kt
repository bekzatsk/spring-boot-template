package kz.innlab.starter.authentication.filter

import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kz.innlab.starter.shared.error.ErrorResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class ApiAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json"
        val errorResponse = ErrorResponse(
            error = "Forbidden",
            message = "Insufficient permissions",
            status = 403
        )
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
