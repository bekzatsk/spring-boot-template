package kz.innlab.starter.authentication.service

interface SmsService {
    fun sendCode(phone: String, code: String)
}
