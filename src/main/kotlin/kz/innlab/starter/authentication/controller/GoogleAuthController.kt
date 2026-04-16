package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.AuthRequest
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.service.GoogleOAuth2Service
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = ["app.auth.google.enabled"], havingValue = "true")
class GoogleAuthController(
    private val googleOAuth2Service: GoogleOAuth2Service
) {

    @Operation(summary = "Authenticate with Google ID token", security = [])
    @PostMapping("/google")
    fun googleLogin(@Valid @RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        val response = googleOAuth2Service.authenticate(request.idToken, request.name, request.picture)
        return ResponseEntity.ok(response)
    }
}
