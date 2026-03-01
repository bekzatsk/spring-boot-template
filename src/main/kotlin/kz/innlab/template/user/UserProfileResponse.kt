package kz.innlab.template.user

import java.time.Instant

data class UserProfileResponse(
    val id: String,
    val email: String,
    val name: String?,
    val picture: String?,
    val provider: String,
    val roles: List<String>,
    val createdAt: Instant?
) {
    companion object {
        fun from(user: User): UserProfileResponse = UserProfileResponse(
            id = user.id.toString(),
            email = user.email,
            name = user.name,
            picture = user.picture,
            provider = user.provider.name,
            roles = user.roles.map { it.name },
            createdAt = user.createdAt
        )
    }
}
