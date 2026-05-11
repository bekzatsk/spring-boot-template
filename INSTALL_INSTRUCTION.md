# Installation Guide

> **TL;DR — почему новый проект не стартует.**
> 1. Не добавляй `spring-boot-starter-security` явно — он приходит транзитивно через `auth-spring-boot-starter`. Явное добавление ломает autoconfig в Boot 4.
> 2. Зарегистрируй **хотя бы один свой `@Bean SecurityFilterChain`** с узким `securityMatcher` (например, `/actuator/**`). Без этого `ServletWebSecurityAutoConfiguration` публикует default form-login chain → `UnreachableFilterChainException` на старте.
> 3. JVM **24** (Kotlin 2.2.x не поддерживает JVM 25).
> 4. Auth-starter тянет свой `application-dev.yaml` внутри jar. Твои `application-*.yml` в `src/main/resources/` его перекрывают — они **обязательны**, иначе подхватится starter-овский DB (`template/postgres`).
> 5. У starter-а свой Flyway на `db/migration/auth`. Твои миграции — отдельно в `db/migration/`. Не перемешивай.

---

## 0. Spring Boot 4 + Kotlin Quick Start (copy-paste)

Минимальный рабочий набор. Подставь свои `groupId/artifactId/{projectName}`.

### `backend/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.5</version>
        <relativePath/>
    </parent>
    <groupId>kz.innlab</groupId>
    <artifactId>{projectName}</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>24</java.version>             <!-- НЕ 25 — Kotlin 2.2 не поддерживает -->
        <kotlin.version>2.2.21</kotlin.version>
        <uuid-creator.version>6.0.0</uuid-creator.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- ВАЖНО: НЕ добавляй spring-boot-starter-security напрямую.
             Он прилетит транзитивно через auth-spring-boot-starter.
             Явное добавление ломает ServletWebSecurityAutoConfiguration в Boot 4. -->
        <dependency>
            <groupId>kz.innlab</groupId>
            <artifactId>auth-spring-boot-starter</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>

        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-reflect</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.module</groupId>
            <artifactId>jackson-module-kotlin</artifactId>
        </dependency>

        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>com.github.f4b6a3</groupId>
            <artifactId>uuid-creator</artifactId>
            <version>${uuid-creator.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-test-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <configuration>
                    <args>
                        <arg>-Xjsr305=strict</arg>
                        <arg>-Xannotation-default-target=param-property</arg>
                    </args>
                    <compilerPlugins>
                        <plugin>spring</plugin>
                        <plugin>jpa</plugin>
                        <plugin>all-open</plugin>
                        <plugin>no-arg</plugin>
                    </compilerPlugins>
                    <!-- all-open / no-arg на JPA-аннотациях, чтобы Kotlin-классы
                         работали как @Entity без явных open / default-конструктора -->
                    <pluginOptions>
                        <option>all-open:annotation=jakarta.persistence.Entity</option>
                        <option>all-open:annotation=jakarta.persistence.MappedSuperclass</option>
                        <option>all-open:annotation=jakarta.persistence.Embeddable</option>
                        <option>no-arg:annotation=jakarta.persistence.Entity</option>
                        <option>no-arg:annotation=jakarta.persistence.MappedSuperclass</option>
                        <option>no-arg:annotation=jakarta.persistence.Embeddable</option>
                    </pluginOptions>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-allopen</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-noarg</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

### `backend/src/main/resources/application.yml` (общий)

```yaml
spring:
  application:
    name: {projectName}
  profiles:
    active: dev
  jackson:
    default-property-inclusion: non_null
  jpa:
    open-in-view: false
    properties:
      hibernate:
        jdbc.time_zone: UTC
        format_sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration   # ТВОИ миграции; auth-starter поднимает свой Flyway на db/migration/auth
```

### `backend/src/main/resources/application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/{projectName}
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE

# (опционально) включить локальные провайдеры авторизации
# app:
#   auth:
#     local: { enabled: true }
#   firebase: { enabled: false }
#   mail:     { enabled: false }
#   cors:
#     allowed-origins:
#       - http://localhost:4200
```

### `backend/src/main/resources/application-prod.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate

# JWT keystore — обязательно в prod
# app:
#   security:
#     jwt:
#       keystore-location: ${JWT_KEYSTORE_LOCATION}
#       keystore-password: ${JWT_KEYSTORE_PASSWORD}
#       key-alias: ${JWT_KEY_ALIAS:jwt}
#   cors:
#     allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}
#   firebase:
#     enabled: ${FIREBASE_ENABLED:false}
```

### Обязательный `SecurityFilterChain` (иначе Boot 4 валится на старте)

`backend/src/main/kotlin/kz/innlab/{projectName}/common/AppSecurityConfig.kt`:

```kotlin
package kz.innlab.{projectName}.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Назначение двойное:
 *  1. Открыть /actuator (под k8s-пробы и мониторинг).
 *  2. Зарегистрировать user SecurityFilterChain в обычной @Configuration-фазе,
 *     чтобы Boot 4 ServletWebSecurityAutoConfiguration backoff'нулся и НЕ публиковал
 *     default form-login chain — иначе старт падает с UnreachableFilterChainException
 *     (он конфликтует с JWT-chain'ом из kz.innlab.starter.config.SecurityConfig (any-request)).
 *
 * Все свои app-chain'ы делай с конкретным `securityMatcher` и @Order < 100.
 * `anyRequest`-chain остаётся за starter'ом.
 */
@Configuration
class AppSecurityConfig {

    @Bean
    @Order(80)
    fun actuatorFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/actuator/**")
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
        return http.build()
    }
}
```

### Точка входа

`backend/src/main/kotlin/kz/innlab/{projectName}/Application.kt`:

```kotlin
package kz.innlab.{projectName}

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

### Чек первого запуска

```bash
# 1) Поднять Postgres
docker compose up -d postgres

# 2) Поставить starter в локальный ~/.m2 (если ещё не делал)
cd /path/to/auth-starter && ./mvnw clean install -DskipTests

# 3) Запустить
cd /path/to/{projectName}/backend && ./mvnw spring-boot:run
```

Приложение слушает порт из bundled `application-dev.yaml` starter-а (обычно `:7070`). `/api/*` → 401 без JWT — это норма.

---

## 1. Publish Starter

### Option A: Local Maven Repository (quick start)

```bash
git clone <repo-url> auth-starter
cd auth-starter
./mvnw clean install -DskipTests
```

Artifact goes to `~/.m2/repository`. Works only on your machine.

### Option B: GitHub Packages

**1) Add `distributionManagement` to starter's `pom.xml`:**

```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/OWNER/REPO</url>
    </repository>
</distributionManagement>
```

**2) Configure credentials in `~/.m2/settings.xml`:**

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password> <!-- ghp_... с правами write:packages -->
        </server>
    </servers>
</settings>
```

**3) Publish:**

```bash
./mvnw clean deploy -DskipTests
```

### Option C: Nexus / Artifactory

**1) Add `distributionManagement` to starter's `pom.xml`:**

```xml
<distributionManagement>
    <repository>
        <id>nexus-releases</id>
        <url>https://nexus.example.com/repository/maven-releases/</url>
    </repository>
    <snapshotRepository>
        <id>nexus-snapshots</id>
        <url>https://nexus.example.com/repository/maven-snapshots/</url>
    </snapshotRepository>
</distributionManagement>
```

**2) Configure credentials in `~/.m2/settings.xml`:**

```xml
<settings>
    <servers>
        <server>
            <id>nexus-releases</id>
            <username>admin</username>
            <password>your-password</password>
        </server>
        <server>
            <id>nexus-snapshots</id>
            <username>admin</username>
            <password>your-password</password>
        </server>
    </servers>
</settings>
```

**3) Publish:**

```bash
# SNAPSHOT version (0.0.1-SNAPSHOT) -> snapshots repo
./mvnw clean deploy -DskipTests

# Release version (убрать -SNAPSHOT из pom.xml) -> releases repo
./mvnw clean deploy -DskipTests
```

---

## 2. Add Dependency in Your Project

### Maven

**pom.xml — dependency:**
```xml
<dependency>
    <groupId>kz.innlab</groupId>
    <artifactId>auth-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

**pom.xml — repository (skip if using local `~/.m2`):**
```xml
<repositories>
    <!-- GitHub Packages -->
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/OWNER/REPO</url>
    </repository>

    <!-- OR Nexus -->
    <repository>
        <id>nexus-snapshots</id>
        <url>https://nexus.example.com/repository/maven-snapshots/</url>
    </repository>
</repositories>
```

### Gradle (Kotlin DSL)

**build.gradle.kts:**
```kotlin
repositories {
    mavenCentral()
    mavenLocal() // если установлен локально

    // GitHub Packages
    maven {
        url = uri("https://maven.pkg.github.com/OWNER/REPO")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }

    // OR Nexus / Artifactory
    // maven("https://nexus.example.com/repository/maven-snapshots/")
}

dependencies {
    implementation("kz.innlab:auth-spring-boot-starter:0.0.1-SNAPSHOT")
}
```

### Gradle (Groovy DSL)

**build.gradle:**
```groovy
repositories {
    mavenCentral()
    mavenLocal()

    // GitHub Packages
    maven {
        url = uri('https://maven.pkg.github.com/OWNER/REPO')
        credentials {
            username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_USERNAME')
            password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
        }
    }

    // OR Nexus / Artifactory
    // maven { url 'https://nexus.example.com/repository/maven-snapshots/' }
}

dependencies {
    implementation 'kz.innlab:auth-spring-boot-starter:0.0.1-SNAPSHOT'
}
```

> **Gradle + GitHub Packages credentials** — добавь в `~/.gradle/gradle.properties`:
> ```properties
> gpr.user=YOUR_GITHUB_USERNAME
> gpr.token=ghp_YOUR_TOKEN
> ```

---

## 3. Configure `application.yaml`

### Minimal (Dev)

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    username: postgres
    password: postgres
  flyway:
    enabled: true
    locations: classpath:db/migration/auth
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate

app:
  auth:
    local:
      enabled: true       # email + password
    google:
      enabled: false
    apple:
      enabled: false
    phone:
      enabled: false
    telegram:
      enabled: false
  firebase:
    enabled: false
  mail:
    enabled: false
  cors:
    allowed-origins:
      - http://localhost:3000
```

This gives you working JWT auth with email+password registration and login. RSA keys are generated in-memory, verification codes are logged to console (`123456`).

### Full (Production)

```yaml
server:
  port: 8080

spring:
  profiles:
    active: prod
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  flyway:
    enabled: true
    locations: classpath:db/migration/auth
    baseline-on-migrate: false
    validate-on-migrate: true
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate

app:
  # --- Auth providers ---
  auth:
    local:
      enabled: true
    google:
      enabled: true
      client-id: ${GOOGLE_CLIENT_ID}
    apple:
      enabled: true
      bundle-id: ${APPLE_BUNDLE_ID}
    phone:
      enabled: true
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN}
      bot-username: ${TELEGRAM_BOT_USERNAME}
      webhook-secret: ${TELEGRAM_WEBHOOK_SECRET}
    refresh-token:
      expiry-days: 30

  # --- JWT keystore (required for prod) ---
  security:
    jwt:
      keystore-location: ${JWT_KEYSTORE_LOCATION}
      keystore-password: ${JWT_KEYSTORE_PASSWORD}
      key-alias: ${JWT_KEY_ALIAS:jwt}

  # --- CORS ---
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}

  # --- Firebase push notifications ---
  firebase:
    enabled: true
    # Set env: FIREBASE_CREDENTIALS_PATH=/path/to/service-account.json

  # --- Email (SMTP sending + IMAP receiving) ---
  mail:
    enabled: true
    smtp:
      host: ${SMTP_HOST}
      port: ${SMTP_PORT:587}
      username: ${SMTP_USERNAME}
      password: ${SMTP_PASSWORD}
      from: ${SMTP_FROM:noreply@example.com}
      ssl-enabled: ${SMTP_SSL_ENABLED:false}
    imap:
      host: ${IMAP_HOST}
      port: ${IMAP_PORT:993}
      username: ${IMAP_USERNAME}
      password: ${IMAP_PASSWORD}
      ssl-enabled: ${IMAP_SSL_ENABLED:true}
    retry:
      max-attempts: 3
      delay-ms: 5000

  # --- Notifications ---
  notification:
    token:
      max-per-user: 5
```

> **Перекрытие bundled-конфига starter-а.** Auth-starter везёт внутри jar свой `application-dev.yaml` с `DB_NAME:template/DB_USERNAME:postgres`. Твой `application-dev.yml` в `src/main/resources/` его перекрывает — поэтому он **обязателен**, даже если ты ничего не дополняешь сверху. Если файла нет — приложение полезет в `template` базу и упадёт.

---

## 4. Property Reference

### Auth Providers

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.auth.local.enabled` | boolean | `true` | Email + password registration/login |
| `app.auth.google.enabled` | boolean | `false` | Google OAuth2 login |
| `app.auth.google.client-id` | string | — | Google OAuth2 client ID |
| `app.auth.apple.enabled` | boolean | `true` | Apple Sign In |
| `app.auth.apple.bundle-id` | string | `com.example.app` | Apple app bundle ID |
| `app.auth.phone.enabled` | boolean | `true` | Phone + SMS OTP login |
| `app.auth.telegram.enabled` | boolean | `false` | Telegram bot authentication |
| `app.auth.telegram.bot-token` | string | — | Telegram Bot API token |
| `app.auth.telegram.bot-username` | string | `MathHubBot` | Telegram bot username (for deep link URL). Also returned in `TelegramInitResponse.botUsername` (any leading `@` stripped) so frontends can render `@<username>` without parsing the URL. |
| `app.auth.telegram.webhook-secret` | string | — | Secret token for webhook validation |
| `app.auth.telegram.session-ttl-seconds` | int | `300` | Auth session TTL (5 min) |
| `app.auth.telegram.max-attempts` | int | `3` | Max code verification attempts per session |
| `app.auth.telegram.resend-cooldown-seconds` | int | `60` | Cooldown between code resends |
| `app.auth.telegram.max-sessions-per-ip-per-hour` | int | `5` | IP rate limit |
| `app.auth.telegram.max-sessions-per-telegram-user-per-hour` | int | `3` | Per-user rate limit |
| `app.auth.refresh-token.expiry-days` | int | `30` | Refresh token TTL in days |

### JWT (Production Only)

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `app.security.jwt.keystore-location` | string | Yes | Path to PKCS12 keystore file |
| `app.security.jwt.keystore-password` | string | Yes | Keystore password |
| `app.security.jwt.key-alias` | string | No (`jwt`) | Key alias in keystore |

Generate keystore:
```bash
keytool -genkey -alias jwt -keyalg RSA -keysize 2048 \
  -keystore jwt.p12 -storetype PKCS12 -validity 3650
```

### Firebase

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.firebase.enabled` | boolean | `false` | Enable Firebase push notifications |

Set `FIREBASE_CREDENTIALS_PATH` env variable pointing to the service account JSON file.

### Email

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.mail.enabled` | boolean | `false` | Enable mail service |
| `app.mail.smtp.host` | string | — | SMTP server host |
| `app.mail.smtp.port` | int | `587` | SMTP server port |
| `app.mail.smtp.username` | string | — | SMTP username |
| `app.mail.smtp.password` | string | — | SMTP password |
| `app.mail.smtp.from` | string | `noreply@example.com` | Sender email address |
| `app.mail.smtp.ssl-enabled` | boolean | `false` | Enable SSL for SMTP |
| `app.mail.imap.host` | string | — | IMAP server host |
| `app.mail.imap.port` | int | `993` | IMAP server port |
| `app.mail.imap.username` | string | — | IMAP username |
| `app.mail.imap.password` | string | — | IMAP password |
| `app.mail.imap.ssl-enabled` | boolean | `true` | Enable SSL for IMAP |
| `app.mail.retry.max-attempts` | int | `3` | Max retry attempts for failed sends |
| `app.mail.retry.delay-ms` | long | `5000` | Delay between retries (ms) |

### CORS

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.cors.allowed-origins` | list | localhost | Allowed CORS origins |

### Notifications

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.notification.token.max-per-user` | int | `5` | Max device tokens per user |

### Dev-Only

| Property | Type | Description |
|----------|------|-------------|
| `app.auth.sms.dev-code` | string | Fixed SMS code for dev (e.g. `123456`) |
| `app.auth.verification.dev-code` | string | Fixed email verification code for dev |
| `app.auth.telegram.dev-code` | string | Fixed Telegram verification code for dev |

---

## 5. Override Default Services

The starter provides console-logging defaults for SMS, email, and push. Override them by declaring your own beans:

```kotlin
@Configuration
class MyServiceOverrides {

    // SMS provider (e.g., Twilio)
    @Bean
    fun smsService(): SmsService = object : SmsService {
        override fun sendCode(phone: String, code: String) {
            // your implementation
        }
    }

    // Email verification sender (e.g., SendGrid)
    @Bean
    fun emailService(): EmailService = object : EmailService {
        override fun sendCode(to: String, code: String, purpose: String) {
            // your implementation
        }
    }

    // Telegram bot message sender (real Telegram Bot API)
    @Bean
    fun telegramBotService(): TelegramBotService = object : TelegramBotService {
        override fun sendMessage(chatId: Long, text: String) {
            // call Telegram Bot API: POST https://api.telegram.org/bot<token>/sendMessage
        }
    }
}
```

The default implementations use `@ConditionalOnMissingBean`, so your beans take priority automatically.

---

## 6. Database

The starter requires **PostgreSQL**. Flyway migrations are bundled and create all tables on first startup.

Your `application.yml` should set Flyway location for **your** migrations:
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration   # ТВОИ миграции
```

Auth-starter поднимает **отдельный** `Flyway`-bean (`authFlyway`) на `classpath:db/migration/auth` — не нужно прописывать его в свой `locations`. Не клади свои миграции в `db/migration/auth` и наоборот.

Tables created by starter: `users`, `user_providers`, `user_provider_ids`, `user_roles`, `refresh_tokens`, `sms_verifications`, `verification_codes`, `telegram_auth_sessions`, `device_tokens`, `notification_history`, `notification_topics`, `notification_preferences`, `mail_history`.

---

## 7. Troubleshooting (что обычно ломает старт)

| Симптом | Причина | Фикс |
|---------|---------|------|
| `UnreachableFilterChainException` на старте | Нет своего `SecurityFilterChain` → Boot 4 публикует default form-login chain поверх starter-овского `anyRequest`-chain | Добавь bean из §0 (actuator chain с узким `securityMatcher`) |
| `Unsupported class file major version` / Kotlin compile error | JVM 25 + Kotlin 2.2 | Откати на JVM 24 |
| Подключается к БД `template` с юзером `postgres` | Нет своего `application-dev.yml` → подхватился bundled из jar starter-а | Создай `src/main/resources/application-dev.yml` с твоей БД |
| `BeanDefinitionOverrideException` для security-конфига | Явно добавлен `spring-boot-starter-security` | Убери — приходит транзитивно |
| Flyway: «migration checksum mismatch» на auth-таблицах | Свои миграции положены в `db/migration/auth` | Перенеси свои в `db/migration/`, оставь `auth` за starter-ом |
| `creator/editor` всегда `null` в `Auditable` | Не зарегистрирован `AuditorAware<UUID>` | Подожди, пока auth-starter положит principal в `SecurityContext`, и регистрируй `AuditorAware`, читающий из него |
| 401 на `/api/**` без JWT в dev | Это норма — starter защищает `anyRequest` | Получи токен через `/auth/login` |

---

## Quick Checklist

- [ ] JVM **24** (не 25)
- [ ] `pom.xml` из §0 — БЕЗ явного `spring-boot-starter-security`
- [ ] `application.yml` + `application-dev.yml` + `application-prod.yml` лежат в `src/main/resources/`
- [ ] Свой `SecurityFilterChain`-bean с узким `securityMatcher` зарегистрирован
- [ ] Kotlin compiler plugins (`spring + jpa + all-open + no-arg`) с extension на `@Entity/@MappedSuperclass/@Embeddable`
- [ ] Свои миграции — в `db/migration/`, starter-овские — в `db/migration/auth` (не трогать)
- [ ] PostgreSQL поднят (`docker compose up -d postgres`)
- [ ] (Prod) сгенерирован JWT keystore, `app.security.jwt.*` через env vars
- [ ] (Prod) `APP_CORS_ALLOWED_ORIGINS` выставлен
- [ ] (Optional) Реализованы `SmsService` / `EmailService` / `TelegramBotService` для реальной доставки
- [ ] (Optional) Telegram bot: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `TELEGRAM_WEBHOOK_SECRET` через env vars
- [ ] (Optional) Firebase для push (`FIREBASE_CREDENTIALS_PATH`)
