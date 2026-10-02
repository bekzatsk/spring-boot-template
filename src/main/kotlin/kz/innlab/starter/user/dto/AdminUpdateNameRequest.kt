package kz.innlab.starter.user.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AdminUpdateNameRequest(
    @field:Size(max = 255)
    val name: String?,

    // http(s) only: a javascript: or data: URL here ends up in every client that renders avatars.
    @field:Size(max = 2048)
    @field:Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL")
    val picture: String?
)
