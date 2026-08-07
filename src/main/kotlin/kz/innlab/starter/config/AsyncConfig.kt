package kz.innlab.starter.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

/**
 * Async support for the starter's dispatchers ([kz.innlab.starter.notification.service.MailDispatcher],
 * [kz.innlab.starter.notification.service.NotificationDispatcher]).
 *
 * A dedicated, bounded executor is used instead of the default per-task executor so a mail/push
 * backlog (e.g. SMTP outage with retries) cannot spawn unbounded concurrent work. CallerRunsPolicy
 * applies backpressure instead of silently dropping dispatches. The bean is name-qualified so it
 * never hijacks a consumer application's own async configuration.
 */
@Configuration
@EnableAsync
class AsyncConfig {

    companion object {
        const val STARTER_EXECUTOR = "authStarterTaskExecutor"
    }

    @Bean(STARTER_EXECUTOR)
    @ConditionalOnMissingBean(name = [STARTER_EXECUTOR])
    fun authStarterTaskExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 8
        queueCapacity = 200
        setThreadNamePrefix("auth-starter-async-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
    }
}
