package kz.innlab.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [
    org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration::class
])
class AuthStarterApplication

fun main(args: Array<String>) {
    runApplication<AuthStarterApplication>(*args)
}
