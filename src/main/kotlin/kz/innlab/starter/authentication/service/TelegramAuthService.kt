package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.TelegramInitResponse
import kz.innlab.starter.authentication.dto.TelegramStatusResponse
import kz.innlab.starter.authentication.dto.TelegramVerifyResponse
import kz.innlab.starter.authentication.model.TelegramAuthSession
import kz.innlab.starter.authentication.model.TelegramSessionStatus
import kz.innlab.starter.authentication.repository.TelegramAuthSessionRepository
import kz.innlab.starter.user.service.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["app.auth.telegram.enabled"], havingValue = "true")
class TelegramAuthService(
    private val sessionRepository: TelegramAuthSessionRepository,
    private val telegramBotService: TelegramBotService,
    private val userService: UserService,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.auth.telegram.bot-username:MathHubBot}") private val botUsername: String = "MathHubBot",
    @Value("\${app.auth.telegram.session-ttl-seconds:300}") private val sessionTtlSeconds: Long = 300,
    @Value("\${app.auth.telegram.code-length:6}") private val codeLength: Int = 6,
    @Value("\${app.auth.telegram.max-attempts:3}") private val maxAttempts: Int = 3,
    @Value("\${app.auth.telegram.resend-cooldown-seconds:60}") private val resendCooldownSeconds: Long = 60,
    @Value("\${app.auth.telegram.max-sessions-per-ip-per-hour:5}") private val maxSessionsPerIpPerHour: Int = 5,
    @Value("\${app.auth.telegram.max-sessions-per-telegram-user-per-hour:3}") private val maxSessionsPerTelegramUserPerHour: Int = 3,
    @Value("\${app.auth.telegram.dev-code:}") private val devCode: String = ""
) {

    companion object {
        private const val CODE_BOUND = 1_000_000
        private val random = SecureRandom()
    }

    @Transactional
    fun initSession(ipAddress: String?): TelegramInitResponse {
        if (ipAddress != null) {
            val oneHourAgo = Instant.now().minusSeconds(3600)
            val count = sessionRepository.countByIpAddressAndCreatedAtAfter(ipAddress, oneHourAgo)
            if (count >= maxSessionsPerIpPerHour) {
                throw IllegalStateException("Too many sessions. Please try again later.")
            }
        }

        val sessionId = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusSeconds(sessionTtlSeconds)

        val session = TelegramAuthSession(
            sessionId = sessionId,
            expiresAt = expiresAt
        ).apply {
            this.ipAddress = ipAddress
            this.maxAttempts = this@TelegramAuthService.maxAttempts
        }
        sessionRepository.save(session)

        val normalizedBotUsername = botUsername.removePrefix("@")
        val botUrl = "https://t.me/$normalizedBotUsername?start=$sessionId"
        return TelegramInitResponse(
            sessionId = sessionId,
            botUrl = botUrl,
            botUsername = normalizedBotUsername,
            expiresAt = expiresAt
        )
    }

    @Transactional
    fun handleWebhookStart(sessionId: String, telegramUserId: Long, telegramUsername: String?, chatId: Long) {
        val session = sessionRepository.findBySessionId(sessionId) ?: return
        if (session.status != TelegramSessionStatus.PENDING) return
        if (session.expiresAt <= Instant.now()) {
            session.status = TelegramSessionStatus.EXPIRED
            sessionRepository.save(session)
            telegramBotService.sendMessage(chatId, "Сессия мерзімі өтіп кетті. Сайтта қайтадан бастаңыз.")
            return
        }

        val oneHourAgo = Instant.now().minusSeconds(3600)
        val telegramSessionCount = sessionRepository.countByTelegramUserIdAndCreatedAtAfter(telegramUserId, oneHourAgo)
        if (telegramSessionCount >= maxSessionsPerTelegramUserPerHour) {
            telegramBotService.sendMessage(chatId, "Тым көп сұраныс. Кейінірек қайтадан көріңіз.")
            return
        }

        val code = generateCode()
        val codeHash = passwordEncoder.encode(code)

        session.telegramUserId = telegramUserId
        session.telegramUsername = telegramUsername
        session.telegramChatId = chatId
        session.codeHash = codeHash
        session.status = TelegramSessionStatus.CODE_SENT
        sessionRepository.save(session)

        telegramBotService.sendMessage(
            chatId,
            "\uD83D\uDD10 Сіздің растау кодыңыз: $code\n\nОсы кодты MathHub сайтына енгізіңіз.\n⏰ Код 5 минут жарамды."
        )
    }

    fun handleWebhookDefault(chatId: Long) {
        telegramBotService.sendMessage(
            chatId,
            "MathHub ботына қош келдіңіз! Тіркелу үшін сайтқа өтіңіз."
        )
    }

    fun handleWebhookHelp(chatId: Long) {
        telegramBotService.sendMessage(
            chatId,
            "MathHub — математика платформасы. Тіркелу үшін mathhub.kz сайтына кіріңіз және \"Telegram арқылы тіркелу\" батырмасын басыңыз."
        )
    }

    @Transactional
    fun verifyCode(sessionId: String, code: String): TelegramVerifyResponse {
        val session = sessionRepository.findBySessionId(sessionId)
            ?: return TelegramVerifyResponse(
                verified = false,
                error = "INVALID_SESSION",
                message = "Сессия табылмады."
            )

        if (session.status == TelegramSessionStatus.EXPIRED || session.expiresAt <= Instant.now()) {
            session.status = TelegramSessionStatus.EXPIRED
            sessionRepository.save(session)
            return TelegramVerifyResponse(
                verified = false,
                error = "SESSION_EXPIRED",
                message = "Сессия мерзімі өтіп кетті. Жаңадан бастаңыз."
            )
        }

        if (session.status == TelegramSessionStatus.VERIFIED) {
            return TelegramVerifyResponse(
                verified = false,
                error = "ALREADY_VERIFIED",
                message = "Бұл сессия бұрыннан расталған."
            )
        }

        if (session.status != TelegramSessionStatus.CODE_SENT) {
            return TelegramVerifyResponse(
                verified = false,
                error = "CODE_NOT_SENT",
                message = "Алдымен Telegram ботқа /start жіберіңіз."
            )
        }

        if (session.attempts >= session.maxAttempts) {
            return TelegramVerifyResponse(
                verified = false,
                error = "MAX_ATTEMPTS",
                message = "Әрекеттер саны таусылды. Жаңа код сұраңыз."
            )
        }

        session.attempts++
        sessionRepository.save(session)

        if (!passwordEncoder.matches(code, session.codeHash)) {
            val attemptsLeft = session.maxAttempts - session.attempts
            return if (attemptsLeft <= 0) {
                TelegramVerifyResponse(
                    verified = false,
                    error = "MAX_ATTEMPTS",
                    message = "Әрекеттер саны таусылды. Жаңа код сұраңыз."
                )
            } else {
                TelegramVerifyResponse(
                    verified = false,
                    error = "INVALID_CODE",
                    attemptsLeft = attemptsLeft,
                    message = "Код дұрыс емес. Қайтадан енгізіп көріңіз."
                )
            }
        }

        session.status = TelegramSessionStatus.VERIFIED
        session.verifiedAt = Instant.now()
        sessionRepository.save(session)

        val user = userService.findOrCreateTelegramUser(
            telegramUserId = session.telegramUserId!!,
            telegramUsername = session.telegramUsername
        )
        val accessToken = tokenService.generateAccessToken(user.id, user.roles)
        val refreshToken = refreshTokenService.createToken(user)

        return TelegramVerifyResponse(
            verified = true,
            telegramUserId = session.telegramUserId,
            telegramUsername = session.telegramUsername,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    @Transactional
    fun resendCode(sessionId: String): Map<String, Any> {
        val session = sessionRepository.findBySessionId(sessionId)
            ?: throw IllegalArgumentException("Session not found")

        if (session.status == TelegramSessionStatus.EXPIRED || session.expiresAt <= Instant.now()) {
            session.status = TelegramSessionStatus.EXPIRED
            sessionRepository.save(session)
            throw IllegalStateException("Session expired")
        }

        if (session.status == TelegramSessionStatus.VERIFIED) {
            throw IllegalStateException("Session already verified")
        }

        if (session.telegramChatId == null) {
            throw IllegalStateException("Telegram bot not connected yet. Please open the bot first.")
        }

        val code = generateCode()
        val codeHash = passwordEncoder.encode(code)

        session.codeHash = codeHash
        session.attempts = 0
        session.status = TelegramSessionStatus.CODE_SENT
        sessionRepository.save(session)

        telegramBotService.sendMessage(
            session.telegramChatId!!,
            "\uD83D\uDD10 Сіздің жаңа растау кодыңыз: $code\n\nОсы кодты MathHub сайтына енгізіңіз.\n⏰ Код 5 минут жарамды."
        )

        return mapOf(
            "sent" to true,
            "cooldown" to resendCooldownSeconds,
            "message" to "Жаңа код Telegram-ға жіберілді"
        )
    }

    fun getSessionStatus(sessionId: String): TelegramStatusResponse {
        val session = sessionRepository.findBySessionId(sessionId)
            ?: throw IllegalArgumentException("Session not found")

        val effectiveStatus = if (session.expiresAt <= Instant.now() && session.status != TelegramSessionStatus.VERIFIED) {
            TelegramSessionStatus.EXPIRED
        } else {
            session.status
        }

        return TelegramStatusResponse(
            status = effectiveStatus.name.lowercase(),
            telegramConnected = session.telegramUserId != null,
            expiresAt = session.expiresAt
        )
    }

    private fun generateCode(): String =
        if (devCode.isNotBlank()) devCode
        else String.format("%0${codeLength}d", random.nextInt(CODE_BOUND))
}
