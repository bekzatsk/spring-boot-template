package kz.innlab.starter.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Spring Boot Auth Template API")
                .version("1.0.0")
                .description("JWT-authenticated REST API with Google, Apple, local (email+password), and phone (SMS OTP) authentication. Includes FCM push notifications, email service (SMTP/IMAP), and notification preferences.")
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste your JWT access token here (without 'Bearer ' prefix)")
            )
        )
}
