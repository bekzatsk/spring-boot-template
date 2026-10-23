package kz.innlab.starter.authentication.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.ActiveDirectoryLoginRequest
import kz.innlab.starter.authentication.dto.AuthResponse
import kz.innlab.starter.authentication.service.ActiveDirectoryAuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication", description = "Public auth endpoints - no JWT required")
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = ["app.auth.active-directory.enabled"], havingValue = "true")
@ConditionalOnProperty(name = ["app.auth.active-directory.password-login.enabled"], havingValue = "true", matchIfMissing = true)
class ActiveDirectoryAuthController(
    private val activeDirectoryAuthService: ActiveDirectoryAuthService
) {

    @Operation(summary = "Login with an Active Directory username and password", security = [])
    @PostMapping("/active-directory/login")
    fun login(@Valid @RequestBody request: ActiveDirectoryLoginRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(activeDirectoryAuthService.login(request.username, request.password))
}
