package kz.innlab.starter

import kz.innlab.starter.config.AuthTokenProperties
import kz.innlab.starter.config.DeviceTokenProperties
import kz.innlab.starter.config.TelegramAuthProperties
import kz.innlab.starter.config.VerificationProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.context.annotation.ComponentScan

@AutoConfiguration(before = [ServletWebSecurityAutoConfiguration::class])
@AutoConfigurationPackage(basePackages = ["kz.innlab.starter"])
@ComponentScan("kz.innlab.starter")
@EnableConfigurationProperties(
    AuthTokenProperties::class,
    VerificationProperties::class,
    TelegramAuthProperties::class,
    DeviceTokenProperties::class
)
class AuthAutoConfiguration
