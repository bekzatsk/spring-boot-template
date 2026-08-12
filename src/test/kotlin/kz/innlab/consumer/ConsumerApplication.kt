package kz.innlab.consumer

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * A consumer application: it lives outside `kz.innlab.starter`, so its component scan covers only
 * its own package and gets the starter purely through auto-configuration — the position from which
 * 0.1.0's cookie regression was visible while the starter's own test suite stayed green.
 *
 * Kept minimal on purpose. Anything added here that the starter should have provided would hide the
 * next regression the same way `AuthStarterApplication` hid this one.
 */
@SpringBootApplication(
    exclude = [
        org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet
            .OAuth2AuthorizationServerAutoConfiguration::class
    ]
)
class ConsumerApplication
