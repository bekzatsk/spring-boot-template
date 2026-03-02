package kz.innlab.template.authentication.service

interface SmsService {
    fun sendCode(phone: String, code: String)
}
