package kz.innlab.template.authentication.service

interface EmailService {
    fun sendCode(to: String, code: String, purpose: String)
}
