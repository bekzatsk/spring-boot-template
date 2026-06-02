package kz.innlab.starter.user.dto

import kz.innlab.starter.user.model.User
import java.time.Instant

data class UserProfileResponse(
    val id: String,
    val email: String,
    val name: String?,
    val picture: String?,
    val phone: String?,
    val providers: List<String>,
    val roles: List<String>,
    val passwordTemporary: Boolean,
    val requiredActions: List<String>,
    val createdAt: Instant?
) {
    companion object {
        fun from(user: User): UserProfileResponse = UserProfileResponse(
            id = user.id.toString(),
            email = user.email,
            name = user.name,
            picture = user.picture,
            phone = user.phone,
            providers = user.providers.map { it.name },
            roles = user.roles.map { it.name },
            passwordTemporary = user.passwordTemporary,
            requiredActions = user.requiredActions.map { it.name },
            createdAt = user.createdAt
        )
    }
}
