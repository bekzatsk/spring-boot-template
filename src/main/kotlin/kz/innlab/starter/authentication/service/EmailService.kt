package kz.innlab.starter.authentication.service

interface EmailService {
    fun sendCode(to: String, code: String, purpose: String)
}
