package kz.innlab.starter.config

import kz.innlab.starter.authentication.service.DirectoryClient
import kz.innlab.starter.authentication.service.JgssKerberosTicketValidator
import kz.innlab.starter.authentication.service.JndiDirectoryClient
import kz.innlab.starter.authentication.service.KerberosTicketValidator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Connection to the directory; nothing connects until the first login. */
@Configuration
@ConditionalOnProperty(name = ["app.auth.active-directory.enabled"], havingValue = "true")
class ActiveDirectoryConfig {

    @Bean
    @ConditionalOnMissingBean
    fun directoryClient(properties: ActiveDirectoryProperties): DirectoryClient = JndiDirectoryClient(properties)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = ["app.auth.active-directory.kerberos.enabled"], havingValue = "true")
    fun kerberosTicketValidator(properties: ActiveDirectoryProperties): KerberosTicketValidator =
        JgssKerberosTicketValidator(properties.kerberos)
}
