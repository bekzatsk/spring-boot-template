package kz.innlab.starter.authentication.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.dto.MicrosoftAuthRequest
import kz.innlab.starter.authentication.service.MicrosoftOAuth2Service
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = ["app.auth.microsoft.enabled"], havingValue = "true")
class MicrosoftAuthController(
    private val microsoftOAuth2Service: MicrosoftOAuth2Service
) {

    @Operation(summary = "Authenticate with a Microsoft Entra ID or ADFS ID token", security = [])
    @PostMapping("/microsoft")
    fun microsoftLogin(@Valid @RequestBody request: MicrosoftAuthRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(microsoftOAuth2Service.authenticate(request.idToken))
}
