package kz.innlab.consumer

import com.google.firebase.FirebaseApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.stereotype.Component

/**
 * Audits the auto-configuration against the classes that carry a stereotype annotation, from the
 * consumer's position.
 *
 * The starter registers every bean explicitly; nothing scans `kz.innlab.starter` in a consumer
 * application. The stereotype annotations stay on the classes only so the Kotlin `spring` compiler
 * plugin opens them for proxying — which means a class can look registered (it is annotated
 * @Service) while no `@Bean` method declares it. That is how AuthCookieWriter disappeared in 0.1.0.
 *
 * This test compares the annotated classes against the beans a maximally-configured consumer
 * context actually holds, so the check is against a real context rather than against grep. A class
 * that is intentionally not registered has to be listed in [intentionallyUnregistered] with a
 * reason.
 */
class AutoConfigurationCoverageTest {

    /**
     * Every switch the starter has, turned on, so conditional beans are in scope. FirebaseApp is
     * supplied by the test: the real one reads FIREBASE_CREDENTIALS_JSON from the environment.
     */
    private fun maximalContext() = consumerContextRunner()
        .withPropertyValues(
            "app.auth.cookie.enabled=true",
            "app.auth.local.enabled=true",
            "app.auth.google.enabled=true",
            "app.auth.apple.enabled=true",
            "app.auth.phone.enabled=true",
            "app.auth.telegram.enabled=true",
            "app.firebase.enabled=true",
            "app.mail.enabled=true",
            "app.mail.smtp.host=localhost"
        )
        .withBean(FirebaseApp::class.java, { mock(FirebaseApp::class.java) })

    /**
     * Classes carrying a stereotype that the auto-configuration deliberately does not register.
     * Empty: every stereotype-annotated class in the starter is reachable from a consumer context.
     */
    private val intentionallyUnregistered: Map<String, String> = emptyMap()

    /**
     * Plain `ClassPathScanningCandidateComponentProvider` evaluates `@Conditional` while scanning,
     * against an environment that has none of the starter's properties — so `@ConditionalOnProperty`
     * classes (AuthCookieWriter among them) would silently drop out of the audit. Conditions are
     * evaluated by the context below, not here; the scan only asks "does this class carry a
     * stereotype".
     */
    private class StereotypeScanner : ClassPathScanningCandidateComponentProvider(false) {
        override fun isCandidateComponent(metadataReader: MetadataReader): Boolean =
            metadataReader.annotationMetadata.hasMetaAnnotation(Component::class.java.name) ||
                metadataReader.annotationMetadata.hasAnnotation(Component::class.java.name)
    }

    private fun stereotypeAnnotatedClasses(): List<Class<*>> {
        val scanner = StereotypeScanner()
        return scanner.findCandidateComponents("kz.innlab.starter")
            .mapNotNull { it.beanClassName }
            .map { Class.forName(it) }
            // @Configuration is meta-annotated with @Component; those are imported, not registered
            // as beans of their own type, and AuthAutoConfiguration pins them by @Import.
            .filterNot { AnnotatedElementUtils.hasAnnotation(it, Configuration::class.java) }
            .sortedBy { it.name }
    }

    @Test
    fun `every stereotype-annotated class is a bean in a consumer context`() {
        val candidates = stereotypeAnnotatedClasses()
        assertThat(candidates)
            .describedAs("component scan found nothing — the audit would pass vacuously")
            .isNotEmpty

        maximalContext().run { context ->
            val missing = candidates
                .filter { context.getBeanNamesForType(it).isEmpty() }
                .map { it.name }
                .filterNot { intentionallyUnregistered.containsKey(it) }

            assertThat(missing)
                .describedAs(
                    "annotated with a stereotype but never declared as a @Bean — invisible to a " +
                        "consumer application, and silent when injected through ObjectProvider"
                )
                .isEmpty()
        }
    }

    @Test
    fun `documented exceptions still exist and are still unregistered`() {
        val candidates = stereotypeAnnotatedClasses().map { it.name }.toSet()
        val stale = intentionallyUnregistered.keys.filterNot { candidates.contains(it) }
        assertThat(stale)
            .describedAs("listed as an intentional exception but no longer a stereotype in the starter")
            .isEmpty()

        maximalContext().run { context ->
            val actuallyRegistered = intentionallyUnregistered.keys.filter {
                context.getBeanNamesForType(Class.forName(it)).isNotEmpty()
            }
            assertThat(actuallyRegistered)
                .describedAs("registered after all — drop it from the exception list")
                .isEmpty()
        }
    }
}
