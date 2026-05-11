package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.TelegramInitResponse
import kz.innlab.starter.authentication.dto.TelegramResendRequest
import kz.innlab.starter.authentication.dto.TelegramStatusResponse
import kz.innlab.starter.authentication.dto.TelegramVerifyRequest
import kz.innlab.starter.authentication.dto.TelegramVerifyResponse
import kz.innlab.starter.authentication.service.TelegramAuthService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth/telegram")
@ConditionalOnProperty(name = ["app.auth.telegram.enabled"], havingValue = "true")
class TelegramAuthController(
    private val telegramAuthService: TelegramAuthService
) {

    @Operation(summary = "Initialize Telegram auth session", security = [])
    @PostMapping("/init")
    fun initSession(request: HttpServletRequest): ResponseEntity<TelegramInitResponse> {
        val ipAddress = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
        val response = telegramAuthService.initSession(ipAddress)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "Verify Telegram auth code", security = [])
    @PostMapping("/verify")
    fun verifyCode(@Valid @RequestBody request: TelegramVerifyRequest): ResponseEntity<TelegramVerifyResponse> {
        val response = telegramAuthService.verifyCode(request.sessionId, request.code)
        val status = when (response.error) {
            null -> HttpStatus.OK
            "MAX_ATTEMPTS" -> HttpStatus.TOO_MANY_REQUESTS
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(response)
    }

    @Operation(summary = "Resend verification code to Telegram", security = [])
    @PostMapping("/resend")
    fun resendCode(@Valid @RequestBody request: TelegramResendRequest): ResponseEntity<Map<String, Any>> {
        val response = telegramAuthService.resendCode(request.sessionId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Check Telegram auth session status", security = [])
    @GetMapping("/status/{sessionId}")
    fun getSessionStatus(@PathVariable sessionId: String): ResponseEntity<TelegramStatusResponse> {
        val response = telegramAuthService.getSessionStatus(sessionId)
        return ResponseEntity.ok(response)
    }
}
