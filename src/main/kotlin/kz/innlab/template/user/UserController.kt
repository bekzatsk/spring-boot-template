package kz.innlab.template.user

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    @GetMapping("/me")
    fun getCurrentUser(authentication: Authentication): Map<String, Any?> {
        val jwt = authentication.principal as Jwt
        return mapOf(
            "sub" to jwt.subject,
            "roles" to jwt.getClaimAsStringList("roles")
        )
    }
}
