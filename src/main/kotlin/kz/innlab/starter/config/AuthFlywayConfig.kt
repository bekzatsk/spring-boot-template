package kz.innlab.starter.config

import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
@ConditionalOnClass(Flyway::class)
@ConditionalOnProperty("spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
class AuthFlywayConfig {

    @Bean(initMethod = "migrate")
    fun authFlyway(dataSource: DataSource): Flyway = Flyway.configure()
        .dataSource(dataSource)
        .schemas("auth")
        .defaultSchema("auth")
        .locations("classpath:db/migration-auth")
        .table("flyway_schema_history")
        .createSchemas(true)
        .baselineOnMigrate(true)
        .load()
}
