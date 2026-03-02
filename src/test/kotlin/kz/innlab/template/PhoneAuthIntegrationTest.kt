package kz.innlab.template

import kz.innlab.template.authentication.model.SmsVerification
import kz.innlab.template.authentication.repository.RefreshTokenRepository
import kz.innlab.template.authentication.repository.SmsVerificationRepository
import kz.innlab.template.authentication.service.SmsService
import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.model.User
import kz.innlab.template.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doNothing
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class PhoneAuthIntegrationTest {

    @MockitoBean
    private lateinit var smsService: SmsService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var smsVerificationRepository: SmsVerificationRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private val testPhone = "+77001234567"

    @BeforeEach
    fun cleanUp() {
        // Delete refresh tokens first to avoid FK constraint violation (Phase 04-01 decision)
        refreshTokenRepository.deleteAll()
        smsVerificationRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `request OTP success returns 204`() {
        doNothing().`when`(smsService).sendCode(anyString(), anyString())

        mockMvc.perform(
            post("/api/v1/auth/phone/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "$testPhone"}""")
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `verify OTP success for new user creates user and returns tokens`() {
        // Pre-seed H2 with a known code hash (real verification path exercised against H2)
        val knownCode = "123456"
        smsVerificationRepository.save(
            SmsVerification(
                phone = testPhone,
                codeHash = passwordEncoder.encode(knownCode)!!,
                expiresAt = Instant.now().plusSeconds(300)
            )
        )

        mockMvc.perform(
            post("/api/v1/auth/phone/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "$testPhone", "code": "$knownCode"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())

        // User created with correct fields
        val user = userRepository.findByPhone(testPhone)
        assert(user != null) { "Phone user should be created in DB" }
        assert(AuthProvider.LOCAL in user!!.providers) { "Providers should contain LOCAL" }
        assert(user.phone == testPhone) { "Phone field should be set to E.164 phone" }
    }

    @Test
    fun `verify OTP success for returning user finds existing user and returns tokens`() {
        // Pre-create a phone user
        val existingUser = userRepository.save(
            User(email = "").also {
                it.providers.add(AuthProvider.LOCAL)
                it.phone = testPhone
            }
        )

        // Pre-seed H2 with a known code hash
        val knownCode = "654321"
        smsVerificationRepository.save(
            SmsVerification(
                phone = testPhone,
                codeHash = passwordEncoder.encode(knownCode)!!,
                expiresAt = Instant.now().plusSeconds(300)
            )
        )

        mockMvc.perform(
            post("/api/v1/auth/phone/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "$testPhone", "code": "$knownCode"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())

        // No duplicate user created — exactly one LOCAL phone user
        val users = userRepository.findAll().filter { AuthProvider.LOCAL in it.providers && it.phone == testPhone }
        assert(users.size == 1) { "Should be exactly 1 phone user, found ${users.size}" }
        assert(users[0].id == existingUser.id) { "Should be the same existing user" }
    }

    @Test
    fun `verify OTP failure returns 401`() {
        // Pre-seed H2 with a code hash for a different code
        smsVerificationRepository.save(
            SmsVerification(
                phone = testPhone,
                codeHash = passwordEncoder.encode("999999")!!,
                expiresAt = Instant.now().plusSeconds(300)
            )
        )

        mockMvc.perform(
            post("/api/v1/auth/phone/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "$testPhone", "code": "000000"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `request OTP with empty phone returns 400`() {
        mockMvc.perform(
            post("/api/v1/auth/phone/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": ""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `verify OTP with invalid phone format returns 400`() {
        mockMvc.perform(
            post("/api/v1/auth/phone/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phone": "not-a-number", "code": "123456"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
    }
}
