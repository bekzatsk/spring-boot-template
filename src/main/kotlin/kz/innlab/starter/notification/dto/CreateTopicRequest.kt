package kz.innlab.starter.notification.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Topic names are passed straight to FCM, which only accepts `[a-zA-Z0-9-_.~%]+`.
 * The endpoint used to take a raw Map, so nothing was validated before persisting.
 */
data class CreateTopicRequest(
    @field:NotBlank
    @field:Size(max = 100)
    @field:Pattern(
        regexp = "[a-zA-Z0-9\\-_.~%]+",
        message = "Topic name may only contain letters, digits and - _ . ~ %"
    )
    val name: String
)
