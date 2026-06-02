package kz.innlab.starter.user.dto

import kz.innlab.starter.user.model.User
import java.time.Instant

data class UserSummaryResponse(
    val id: String,
    val email: String,
    val name: String?,
    val phone: String?,
    val createdAt: Instant?
) {
    companion object {
        fun from(user: User): UserSummaryResponse = UserSummaryResponse(
            id = user.id.toString(),
            email = user.email,
            name = user.name,
            phone = user.phone,
            createdAt = user.createdAt
        )
    }
}
