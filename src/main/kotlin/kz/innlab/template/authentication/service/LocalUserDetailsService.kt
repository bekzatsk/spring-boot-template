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
     * Looks up by email, then checks LOCAL is in the user's providers set.
     * Prevents Google/Apple-only users from authenticating via local credentials.
     */
    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("No account for email: $email")

        if (AuthProvider.LOCAL !in user.providers) {
            throw UsernameNotFoundException("No local account for email: $email")
        }

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
