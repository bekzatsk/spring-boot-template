package kz.innlab.starter.user.dto

import jakarta.validation.constraints.NotEmpty
import kz.innlab.starter.user.model.Role

data class AdminUpdateRolesRequest(
    @field:NotEmpty(message = "At least one role is required")
    val roles: Set<Role>
)
