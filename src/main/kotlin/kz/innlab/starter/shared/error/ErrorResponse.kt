package kz.innlab.starter.shared.error

data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)
