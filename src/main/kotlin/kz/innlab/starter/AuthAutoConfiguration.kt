package kz.innlab.starter

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@AutoConfiguration
@AutoConfigurationPackage(basePackages = ["kz.innlab.starter"])
@ComponentScan("kz.innlab.starter")
@EnableJpaRepositories("kz.innlab.starter")
class AuthAutoConfiguration
