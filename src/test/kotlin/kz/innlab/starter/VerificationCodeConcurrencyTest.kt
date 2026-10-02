package kz.innlab.starter

import kz.innlab.starter.authentication.model.VerificationPurpose
import kz.innlab.starter.authentication.repository.VerificationCodeRepository
import kz.innlab.starter.authentication.service.VerificationCodeService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class VerificationCodeConcurrencyTest {
    @Autowired lateinit var codes: VerificationCodeService
    @Autowired lateinit var repository: VerificationCodeRepository
    @MockitoSpyBean lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun cleanUp() = repository.deleteAll()

    @Test
    fun `concurrent verification consumes one code at most once`() {
        val issued = codes.createCode("concurrent@example.com", VerificationPurpose.EMAIL_LOGIN)
        val matching = CountDownLatch(2)
        doAnswer { invocation ->
            matching.countDown()
            check(matching.await(10, TimeUnit.SECONDS))
            invocation.callRealMethod()
        }.`when`(passwordEncoder).matches(issued.code, repository.findById(issued.verificationId).orElseThrow().codeHash)

        val pool = Executors.newFixedThreadPool(2)
        try {
            val attempts = (1..2).map {
                pool.submit<Boolean> {
                    try {
                        codes.verifyCode(issued.verificationId, "concurrent@example.com", VerificationPurpose.EMAIL_LOGIN, issued.code)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            assertEquals(1, attempts.count { it.get(15, TimeUnit.SECONDS) })
        } finally {
            pool.shutdownNow()
        }
    }
}
