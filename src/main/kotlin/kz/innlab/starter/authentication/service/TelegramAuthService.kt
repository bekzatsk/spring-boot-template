package kz.innlab.starter.authentication.service

import kz.innlab.starter.authentication.dto.TelegramInitResponse
import kz.innlab.starter.authentication.dto.TelegramResendResponse
import kz.innlab.starter.authentication.dto.TelegramStatusResponse
import kz.innlab.starter.authentication.dto.TelegramVerifyResponse
import kz.innlab.starter.authentication.model.TelegramAuthSession
import kz.innlab.starter.authentication.model.TelegramSessionStatus
import kz.innlab.starter.authentication.repository.TelegramAuthSessionRepository
import kz.innlab.starter.shared.transaction.AfterCommitRunner
import kz.innlab.starter.user.service.UserService
import kz.innlab.starter.config.TelegramAuthProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["app.auth.telegram.enabled"], havingValue = "true")
class TelegramAuthService(
    private val sessionRepository: TelegramAuthSessionRepository,
    private val telegramBotService: TelegramBotService,
    private val userService: UserService,
    private val authTokenIssuer: AuthTokenIssuer,
    private val passwordEncoder: PasswordEncoder,
    private val afterCommitRunner: AfterCommitRunner,
    private val messages: TelegramBotMessages,
    private val telegramProperties: TelegramAuthProperties
) {


    @Transactional
    fun initSession(ipAddress: String?): TelegramInitResponse {
        if (ipAddress != null) {
            val oneHourAgo = Instant.now().minusSeconds(3600)
            val count = sessionRepository.countByIpAddressAndCreatedAtAfter(ipAddress, oneHourAgo)
            if (count >= telegramProperties.maxSessionsPerIpPerHour) {
                throw IllegalStateException("Too many sessions. Please try again later.")
            }
        }

        val sessionId = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusSeconds(telegramProperties.sessionTtlSeconds)

        val session = TelegramAuthSession(
            sessionId = sessionId,
            expiresAt = expiresAt
        ).apply {
            this.ipAddress = ipAddress
            this.maxAttempts = telegramProperties.maxAttempts
        }
        sessionRepository.save(session)

        val normalizedBotUsername = telegramProperties.botUsername.removePrefix("@")
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
            // Telegram API calls are deferred to after commit so the HTTP round-trip never holds
            // the DB transaction open (and never fires for rolled-back state).
            afterCommitRunner.run {
                telegramBotService.sendMessage(chatId, messages.sessionExpired())
            }
            return
        }

        val oneHourAgo = Instant.now().minusSeconds(3600)
        val telegramSessionCount = sessionRepository.countByTelegramUserIdAndCreatedAtAfter(telegramUserId, oneHourAgo)
        if (telegramSessionCount >= telegramProperties.maxSessionsPerTelegramUserPerHour) {
            afterCommitRunner.run {
                telegramBotService.sendMessage(chatId, messages.tooManyRequests())
            }
            return
        }

        val code = generateCode()
        val codeHash = passwordEncoder.encode(code)

        session.telegramUserId = telegramUserId
        session.telegramUsername = telegramUsername
        session.telegramChatId = chatId
        session.codeHash = codeHash
        session.codeSentAt = Instant.now()
        session.status = TelegramSessionStatus.CODE_SENT
        sessionRepository.save(session)

        afterCommitRunner.run {
            telegramBotService.sendMessage(
                chatId,
                messages.verificationCode(code, telegramProperties.sessionTtlSeconds / 60)
            )
        }
    }

    fun handleWebhookDefault(chatId: Long) {
        telegramBotService.sendMessage(
            chatId,
            messages.welcome()
        )
    }

    fun handleWebhookHelp(chatId: Long) {
        telegramBotService.sendMessage(
            chatId,
            messages.help()
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

        val telegramUserId = requireNotNull(session.telegramUserId) {
            "Verified Telegram session missing telegramUserId: ${session.sessionId}"
        }
        val user = userService.findOrCreateTelegramUser(
            telegramUserId = telegramUserId,
            telegramUsername = session.telegramUsername
        )
        val tokens = authTokenIssuer.issue(user)

        return TelegramVerifyResponse(
            verified = true,
            telegramUserId = telegramUserId,
            telegramUsername = session.telegramUsername,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken
        )
    }

    @Transactional
    fun resendCode(sessionId: String): TelegramResendResponse {
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

        // Server-side throttling. Without it an attacker could loop verify (3 guesses) -> resend
        // (attempts reset to 0) -> repeat, brute-forcing the code and spamming the victim's chat.
        val now = Instant.now()
        val lastSentAt = session.codeSentAt
        if (lastSentAt != null && lastSentAt.plusSeconds(telegramProperties.resendCooldownSeconds) > now) {
            throw IllegalStateException("Please wait before requesting a new code")
        }
        if (session.resendCount >= telegramProperties.maxResendsPerSession) {
            throw IllegalStateException("Resend limit reached for this session. Start a new session.")
        }

        val code = generateCode()
        val codeHash = passwordEncoder.encode(code)

        session.codeHash = codeHash
        session.attempts = 0
        session.codeSentAt = now
        session.resendCount++
        session.status = TelegramSessionStatus.CODE_SENT
        sessionRepository.save(session)

        val chatId = requireNotNull(session.telegramChatId)
        afterCommitRunner.run {
            telegramBotService.sendMessage(
                chatId,
                messages.resentVerificationCode(code, telegramProperties.sessionTtlSeconds / 60)
            )
        }

        return TelegramResendResponse(
            sent = true,
            cooldown = telegramProperties.resendCooldownSeconds,
            message = "Жаңа код Telegram-ға жіберілді"
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

    private fun generateCode(): String = OneTimeCodes.generate(telegramProperties.devCode, telegramProperties.codeLength)
}
