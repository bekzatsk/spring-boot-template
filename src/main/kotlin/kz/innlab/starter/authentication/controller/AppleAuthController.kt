package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.AppleAuthRequest
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.service.AppleOAuth2Service
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = ["app.auth.apple.enabled"], havingValue = "true", matchIfMissing = true)
class AppleAuthController(
    private val appleOAuth2Service: AppleOAuth2Service
) {

    @Operation(summary = "Authenticate with Apple ID token", security = [])
    @PostMapping("/apple")
    fun appleLogin(@Valid @RequestBody request: AppleAuthRequest): ResponseEntity<AuthResponse> {
        val response = appleOAuth2Service.authenticate(request)
        return ResponseEntity.ok(response)
    }
}
