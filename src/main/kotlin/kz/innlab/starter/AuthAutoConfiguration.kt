package kz.innlab.starter

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.context.annotation.ComponentScan

@AutoConfiguration(before = [ServletWebSecurityAutoConfiguration::class])
@AutoConfigurationPackage(basePackages = ["kz.innlab.starter"])
@ComponentScan("kz.innlab.starter")
class AuthAutoConfiguration
