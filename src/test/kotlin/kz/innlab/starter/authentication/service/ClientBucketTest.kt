package kz.innlab.starter.authentication.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/** The per-client send limit keys IPv6 on its /64, so rotating addresses inside it does not help. */
class ClientBucketTest {

    private val service = VerificationCodeService(
        mock(), mock(), mock(), mock(), mock(), mock()
    )

    @Test
    fun `addresses in one IPv6 slash 64 share a bucket`() {
        val a = service.clientBucket("2001:db8:1:2:aaaa::1")
        val b = service.clientBucket("2001:db8:1:2:ffff:ffff:ffff:fffe")
        val other = service.clientBucket("2001:db8:1:3::1")

        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(other)
    }

    @Test
    fun `IPv4 and unparsable values are kept as they are`() {
        assertThat(service.clientBucket("203.0.113.7")).isEqualTo("203.0.113.7")
        assertThat(service.clientBucket("not-an-address")).isEqualTo("not-an-address")
    }
}
