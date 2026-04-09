package kz.innlab.starter.user.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import kz.innlab.starter.user.dto.UserProfileResponse
import kz.innlab.starter.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Users", description = "User profile endpoints")
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/me")
    fun getCurrentUser(@Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt): ResponseEntity<UserProfileResponse> {
        val userId = UUID.fromString(jwt.subject)
        val user = userService.findById(userId)
        return ResponseEntity.ok(UserProfileResponse.from(user))
    }
}
