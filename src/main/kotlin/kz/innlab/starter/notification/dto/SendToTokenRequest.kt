package kz.innlab.starter.notification.dto

import jakarta.validation.constraints.AssertTrue
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendToTokenRequest(
    @field:NotBlank
    val token: String,

    @field:NotBlank
    @field:Size(max = 255)
    val title: String,

    // FCM caps a message at 4 KB; bounding the input keeps oversized payloads from reaching it.
    @field:NotBlank
    @field:Size(max = 2000)
    val body: String,

    @field:Size(max = 20)
    val data: Map<String, String> = emptyMap()
) {
    @get:AssertTrue(message = "data keys must be at most 64 and values at most 1024 characters")
    @get:JsonIgnore
    val isDataWithinLimits: Boolean
        get() = PushPayloadLimits.dataWithinLimits(data)
}
