package kz.innlab.template.authentication.service

import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.repository.UserRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class LocalUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    /**
     * Load a LOCAL user by email.
     * Scoped lookup to (LOCAL, email) — prevents Google/Apple users with the same email
     * from being returned as local auth candidates.
     */
    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email)
            ?: throw UsernameNotFoundException("No local account for email: $email")

        if (user.passwordHash == null) {
            throw BadCredentialsException("No password set")
        }

        return org.springframework.security.core.userdetails.User
            .withUsername(email)
            .password(user.passwordHash!!)
            .roles(*user.roles.map { it.name }.toTypedArray())
            .build()
    }
}
