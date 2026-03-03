package kz.innlab.template.authentication.controller

import jakarta.validation.Valid
import kz.innlab.template.authentication.dto.ChangeEmailRequest
import kz.innlab.template.authentication.dto.ChangePasswordRequest
import kz.innlab.template.authentication.dto.ChangePhoneRequest
import kz.innlab.template.authentication.dto.VerifyChangeEmailRequest
import kz.innlab.template.authentication.dto.VerifyChangePhoneRequest
import kz.innlab.template.authentication.service.AccountManagementService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users/me")
class AccountManagementController(
    private val accountManagementService: AccountManagementService
) {

    @PostMapping("/change-password")
    fun changePassword(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<Void> {
        // TODO: rate limiting
        accountManagementService.changePassword(
            UUID.fromString(jwt.subject), request.currentPassword, request.newPassword
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/change-email/request")
    fun requestChangeEmail(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangeEmailRequest
    ): ResponseEntity<Map<String, Any>> {
        // TODO: rate limiting
        val verificationId = accountManagementService.requestEmailChange(
            UUID.fromString(jwt.subject), request.newEmail
        )
        return ResponseEntity.ok(mapOf("verificationId" to verificationId))
    }

    @PostMapping("/change-email/verify")
    fun verifyChangeEmail(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: VerifyChangeEmailRequest
    ): ResponseEntity<Void> {
        // TODO: rate limiting
        accountManagementService.verifyEmailChange(
            UUID.fromString(jwt.subject), request.verificationId, request.code
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/change-phone/request")
    fun requestChangePhone(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangePhoneRequest
    ): ResponseEntity<Map<String, Any>> {
        // TODO: rate limiting
        val verificationId = accountManagementService.requestPhoneChange(
            UUID.fromString(jwt.subject), request.phone
        )
        return ResponseEntity.ok(mapOf("verificationId" to verificationId))
    }

    @PostMapping("/change-phone/verify")
    fun verifyChangePhone(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: VerifyChangePhoneRequest
    ): ResponseEntity<Void> {
        // TODO: rate limiting
        accountManagementService.verifyPhoneChange(
            UUID.fromString(jwt.subject), request.verificationId, request.phone, request.code
        )
        return ResponseEntity.ok().build()
    }
}
