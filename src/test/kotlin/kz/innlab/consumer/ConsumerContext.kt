package kz.innlab.consumer

import kz.innlab.starter.AuthAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

/**
 * A context assembled the way a consumer application assembles it: the starter's auto-configuration
 * plus the Boot auto-configurations it builds on, and nothing else.
 *
 * There is deliberately no `@SpringBootApplication` over `kz.innlab.starter` here. The test suite's
 * own `AuthStarterApplication` scans that package, so any bean the auto-configuration forgets to
 * declare is still created for `@SpringBootTest` — which is how cookie mode could break in 0.1.0
 * with 174 green tests. A consumer application lives in a different package and gets no such
 * backfill; this runner reproduces that position.
 */
fun consumerContextRunner(): WebApplicationContextRunner = WebApplicationContextRunner()
    .withInitializer(
        ConfigDataApplicationContextInitializer()
            as ApplicationContextInitializer<ConfigurableApplicationContext>
    )
    .withConfiguration(
        AutoConfigurations.of(
            DataSourceAutoConfiguration::class.java,
            DataSourceTransactionManagerAutoConfiguration::class.java,
            TransactionAutoConfiguration::class.java,
            HibernateJpaAutoConfiguration::class.java,
            DataJpaRepositoriesAutoConfiguration::class.java,
            JacksonAutoConfiguration::class.java,
            ValidationAutoConfiguration::class.java,
            TaskExecutionAutoConfiguration::class.java,
            SecurityAutoConfiguration::class.java,
            ServletWebSecurityAutoConfiguration::class.java,
            SecurityFilterAutoConfiguration::class.java,
            WebMvcAutoConfiguration::class.java,
            AuthAutoConfiguration::class.java
        )
    )
