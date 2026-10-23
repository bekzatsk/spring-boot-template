package kz.innlab.starter.authentication.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.innlab.starter.authentication.dto.LinkProviderRequest
import kz.innlab.starter.authentication.service.GoogleOAuth2Service
import kz.innlab.starter.user.dto.UserProfileResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Account Management", description = "Change password, email, and phone for authenticated users")
@RequestMapping("/api/v1/users/me/providers")
@ConditionalOnProperty(name = ["app.auth.google.enabled"], havingValue = "true")
class GoogleAccountLinkController(
    private val googleOAuth2Service: GoogleOAuth2Service
) {

    @Operation(
        summary = "Link a Google account to the signed-in user",
        description = "The Google account's verified email must equal the user's email. " +
            "409 when that Google account is linked to another user."
    )
    @PostMapping("/google")
    fun linkGoogle(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: LinkProviderRequest
    ): ResponseEntity<UserProfileResponse> =
        ResponseEntity.ok(UserProfileResponse.from(googleOAuth2Service.link(UUID.fromString(jwt.subject), request.idToken)))
}
