# Installation Guide

> ## ⛔ Do not use 0.1.0 — use 0.1.5
>
> **0.1.0 ships with cookie authentication dead.** `AuthCookieWriter` is not registered by the
> auto-configuration, and every component injects it optionally, so the application starts clean,
> logs nothing, answers `200` — and never sends `Set-Cookie`. `app.auth.cookie.enabled=true` has no
> effect in that release, whatever you set it through (yaml, env, `SPRING_APPLICATION_JSON`).
>
> 0.1.0 stays on Maven Central because Central is immutable; treat it as withdrawn. Version 0.1.1
> fixed cookie authentication; current **0.1.5** contains that fix plus Spring Boot 4.1.1,
> Java 25, Kotlin 2.3.21 and configurable email/phone OTP. 0.1.4 and 0.1.5 are security releases
> with breaking changes and migrations V14–V16; read their CHANGELOG.md entries before upgrading.
> **0.1.4 and earlier carry `application-dev.yaml` (fixed `123456` codes) inside the jar** — use 0.1.5.
> The 0.0.x upgrade steps below still apply.
>
> Check your deployment with:
>
> ```bash
> curl -D - -s -o /dev/null -X POST https://<host>/api/v1/auth/local/login \
>   -H 'Content-Type: application/json' \
>   -d '{"email":"...","password":"..."}' | grep -i set-cookie
> ```
>
> Two `Set-Cookie` lines (`access_token`, `refresh_token`) means the fix is in place. No output on
> 0.1.0 with cookie mode on is the bug.

> ## ⚠ Upgrading from 0.0.x to 0.1.x
>
> Five breaking changes — [CHANGELOG.md](CHANGELOG.md) has the full list. These either stop the
> application from starting or change behaviour silently:
>
> 1. **`SPRING_PROFILES_ACTIVE` must be set explicitly.** There is no default profile any more.
>    It used to fall back to `dev`, which enabled the fixed verification code `123456` on every
>    OTP flow — a deployment that forgot the variable accepted that code in production.
> 2. **The filter chain ends in `authenticated`, not `permitAll`.** Any path served outside
>    `/api/**` now needs a token unless listed in `app.auth.security.public-paths`.
>    `/actuator/health` stays public.
> 3. **`POST /api/v1/notifications/send/topic` and `/api/v1/mail/inbox/**` require `ROLE_ADMIN`.**
> 4. **Signatures changed**: `AdminUserService.list()` returns `Page<UserSummaryResponse>`;
>    `TopicService.subscribe/unsubscribe` take a `userId` first.
> 5. **Apply migrations V8–V10.** V9 merges `sms_verifications` into `verification_codes` and
>    drops the old table; V10 adds optimistic-locking columns.
>
> Settings added in 0.1.x:
>
> | Property | Default | Purpose |
> |---|---|---|
> | `app.auth.rate-limit.enabled` | `true` | Attempt limits on login and change-password |
> | `app.auth.rate-limit.login.max-attempts` | `10` | Per email, per window |
> | `app.auth.rate-limit.login.window-seconds` | `300` | Window length |
> | `app.auth.rate-limit.change-password.max-attempts` | `5` | Per user, per window |
> | `app.auth.telegram.max-resends-per-session` | `3` | Cap on code resends per session |
> | `app.auth.telegram.trust-forwarded-headers` | `false` | Honour `X-Forwarded-For` — only behind a trusted proxy |
> | `app.security.console-fallbacks.allow-sms` | `false` | Let `prod` start on the console SMS stub — set it when the application sends no SMS |
> | `app.security.console-fallbacks.allow-email` | `false` | Same for verification email |
> | `app.security.console-fallbacks.allow-mail` | `false` | Same for outgoing mail (`/api/v1/mail`) |
> | `app.security.allow-console-fallbacks` | `false` | Waives all three at once. Kept for compatibility — prefer the per-channel switches, since a blanket waiver set to get past a missing SMS provider disarms the mail guards too |
>
> Rate limiting is in-memory, so limits apply **per instance**. Declare a `RateLimiter` bean
> backed by a shared store for a clustered deployment.

> ## ⚠ Upgrading to 0.1.4 / 0.1.5
>
> Security releases with breaking changes — [CHANGELOG.md](CHANGELOG.md) has the full list. The ones
> that change behaviour for clients or operators:
>
> 1. **0.1.4 and earlier carry `application*.yaml` inside the jar.** From 0.1.5 the starter ships no
>    configuration: settings you inherited from it (for example `email-verification.enabled: true`,
>    the `${JWT_KEYSTORE_LOCATION}`-style env mappings, `server.port: 7070`) now come from your own
>    files or the code defaults. Map the env vars you use in your own `application-prod.yml`.
> 2. **Mail send and the shared inbox are ADMIN-only**, and admin password/email/phone/role/delete/
>    create calls need a login younger than `app.auth.security.admin-fresh-login-seconds` (900 s).
> 3. **`change-email` / `change-phone` need proof**: `currentPassword`, or a code from
>    `POST /api/v1/users/me/reauth/request`. Accounts with an unverified email get `403`.
> 4. **`forgot-password` and `verify-email/resend` always return a `verificationId`**; `verify-email`
>    is no longer idempotent (a repeat call answers `401`).
> 5. **Swagger/OpenAPI and actuator (except health) are no longer public.** Set
>    `app.auth.security.public-api-docs=true` to open the docs.
> 6. **Migrations V14–V16 run** (unique FCM token; `refresh_tokens.authenticated_at`).
> 7. **Behind a reverse proxy, set `server.forward-headers-strategy`**, or the per-client code-send
>    limit sees one address for everyone.
>
> | Property | Default | Purpose |
> |---|---|---|
> | `app.auth.rate-limit.otp-verify.*` | `10` / `86400` s | Code checks per identifier and purpose, across codes |
> | `app.auth.rate-limit.code-send-per-client.*` | `20` / `3600` s | Codes sent per client address (IPv6 per /64) |
> | `app.auth.rate-limit.code-send-per-purpose.*` | `1000` / `3600` s | Codes sent per purpose, all clients |
> | `app.auth.security.public-api-docs` | `false` | Serve Swagger UI and `/v3/api-docs` without auth |
> | `app.auth.security.admin-fresh-login-seconds` | `900` | Recent-login window for account-handover admin actions |
> | `app.security.production-profiles` | `prod,production` | Profiles that trigger the production guards |

> **TL;DR — почему новый проект не стартует.**
> 1. Не добавляй `spring-boot-starter-security` явно — он приходит транзитивно через `auth-spring-boot-starter`. Явное добавление ломает autoconfig в Boot 4.
> 2. Свой `SecurityFilterChain` **не обязателен**: цепочка starter-а покрывает приложение (проверено тестом с приложением без своей цепочки). Добавляй свою с узким `securityMatcher` только для путей с другими правилами.
> 3. JVM **25** с Kotlin **2.3.21**.
> 4. Starter (≥ 0.1.5) не везёт `application*.yaml` — вся конфигурация твоя. Без своих файлов действуют дефолты из кода (порт 8080, БД не задана).
> 5. У starter-а свой Flyway: схема `auth`, миграции `classpath:db/migration-auth`. Твои миграции — отдельно в `db/migration/`. Не перемешивай.
> 6. `spring-boot-starter-actuator` идёт транзитивно → `/actuator/health` доступен из коробки, остальные actuator-эндпоинты — только `ROLE_ADMIN`.



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
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>kz.innlab</groupId>
    <artifactId>{projectName}</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>25</java.version>
        <kotlin.version>2.3.21</kotlin.version>
        <uuid-creator.version>6.1.1</uuid-creator.version>
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
        <!-- Maven Central — no extra repository config required -->
        <dependency>
            <groupId>kz.innlab</groupId>
            <artifactId>auth-spring-boot-starter</artifactId>
            <version>0.1.5</version>
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
  # Профиль НЕ задаём здесь: локально SPRING_PROFILES_ACTIVE=dev, на сервере — prod. Дефолт `dev`
  # в базовом файле включил бы dev-коды на любом деплое, где забыли переменную.
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
    locations: classpath:db/migration   # ТВОИ миграции; auth-starter поднимает свой Flyway (схема auth, db/migration-auth)
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

# Фиксированные коды для локальной разработки — ТОЛЬКО в dev-файле.
# Под prod/production starter откажется стартовать, если они заданы.
app:
  auth:
    sms:
      dev-code: "123456"
    email-otp:
      dev-code: "123456"
    verification:
      dev-code: "123456"

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

server:
  # За reverse proxy: иначе лимит отправки кодов "на клиента" видит один адрес на всех.
  forward-headers-strategy: framework

# Starter не везёт yaml — связывай env-переменные здесь сам. Без keystore, issuer/audience
# и CORS приложение под prod не стартует (ProductionSafetyConfig / RsaKeyConfig).
app:
  security:
    jwt:
      keystore-location: ${JWT_KEYSTORE_LOCATION}
      keystore-password: ${JWT_KEYSTORE_PASSWORD}
      key-alias: ${JWT_KEY_ALIAS:jwt}
      issuer: ${JWT_ISSUER}
      audience: ${JWT_AUDIENCE}
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}
  firebase:
    enabled: ${FIREBASE_ENABLED:false}
```

### Свой `SecurityFilterChain` — не обязателен

Цепочка starter-а покрывает всё приложение: `anyRequest().authenticated()`, `/actuator/health`
открыт, остальной `/actuator/**` и `/api/v1/admin/**` — только `ROLE_ADMIN`. Публичные пути
добавляй через `app.auth.security.public-paths` (админские правила проверяются раньше — `/api/**`
их не откроет).

Своя цепочка нужна только для путей с другими правилами. Делай её с узким `securityMatcher` и
`@Order < 100` и **не открывай `/actuator/**` целиком**: если включишь `heapdump`, `env` или
`loggers`, они станут публичными (в heap dump — ключ подписи JWT).

```kotlin
@Configuration
class AppSecurityConfig {

    @Bean
    @Order(80)
    fun webhookFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/webhooks/payments/**")   // только этот путь
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

Приложение слушает `server.port` из твоего конфига (по умолчанию Spring — `:8080`). `/api/*` → 401 без JWT — это норма.

---

## 1. Publish Starter

> **Текущая версия starter:** `kz.innlab:auth-spring-boot-starter:0.1.5`.
> Если ты **используешь** starter — переходи к §2. Эта секция нужна только если ты **форкнул** его и публикуешь свой вариант.

### Option A: Maven Central (canonical, no extra config for consumers)

Текущий релиз уже на Maven Central. Для consumers — никакой `<repositories>`/`<repository>` не нужен (mavenCentral в Gradle/Maven по умолчанию). Просто добавь dependency из §2.

Чтобы публиковать **свои** релизы под `kz.innlab`:

1. **Sonatype Central account** + namespace `kz.innlab` уже verified (DNS TXT на `innlab.kz`). Generate user token на https://central.sonatype.com → "View Account" → "Generate User Token".

2. **GPG key** опубликован на keyservers:
   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

3. **`~/.m2/settings.xml`**:
   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>SONATYPE_TOKEN_USERNAME</username>
         <password>SONATYPE_TOKEN_PASSWORD</password>
       </server>
     </servers>
     <profiles>
       <profile>
         <id>gpg</id>
         <properties>
           <gpg.keyname>YOUR_KEY_ID</gpg.keyname>
           <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
         </properties>
         <activation><activeByDefault>true</activeByDefault></activation>
       </profile>
     </profiles>
   </settings>
   ```

4. **Bump version** в `pom.xml` (no `-SNAPSHOT` — Maven Central rejects snapshots).

5. **Deploy** (JDK 25 required; Kotlin 2.3.21):
   ```bash
   export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
   ./mvnw clean deploy -P release -DskipTests
   ```

6. **Manual publish** на https://central.sonatype.com/publishing/deployments → find deployment → click **Publish**.

7. Verify:
   ```bash
   curl -sI https://repo.maven.apache.org/maven2/kz/innlab/auth-spring-boot-starter/<VERSION>/auth-spring-boot-starter-<VERSION>.pom | head -1
   ```
   `HTTP/2 200` = live. mvnrepository.com index ~few hours later.

### Option B: Local Maven Repository (quick start)

```bash
git clone <repo-url> auth-starter
cd auth-starter
./mvnw clean install -DskipTests
```

Artifact goes to `~/.m2/repository`. Works only on your machine.

### Option C: GitHub Packages

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

### Option D: Nexus / Artifactory

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

**pom.xml — dependency (Maven Central, no extra `<repositories>` needed):**
```xml
<dependency>
    <groupId>kz.innlab</groupId>
    <artifactId>auth-spring-boot-starter</artifactId>
    <version>0.1.5</version>
</dependency>
```

**pom.xml — repository (только если ставишь из GitHub Packages / Nexus, а не Maven Central):**
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
    mavenCentral() // starter живёт здесь — ничего больше не нужно

    // Опционально: альтернативные источники, если форкнул и хостишь сам
    // mavenLocal()
    // maven {
    //     url = uri("https://maven.pkg.github.com/OWNER/REPO")
    //     credentials {
    //         username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
    //         password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
    //     }
    // }
}

dependencies {
    implementation("kz.innlab:auth-spring-boot-starter:0.1.5")
}
```

### Gradle (Groovy DSL)

**build.gradle:**
```groovy
repositories {
    mavenCentral() // starter живёт здесь

    // Опционально:
    // mavenLocal()
    // maven {
    //     url = uri('https://maven.pkg.github.com/OWNER/REPO')
    //     credentials {
    //         username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_USERNAME')
    //         password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
    //     }
    // }
}

dependencies {
    implementation 'kz.innlab:auth-spring-boot-starter:0.1.5'
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

# Actuator: только /actuator/health открыт по умолчанию (Spring Boot default).
# Чтобы выставить ещё (metrics, info, env, ...) — раскомментируй и перечисли:
# management:
#   endpoints:
#     web:
#       exposure:
#         include: health,info
  mail:
    enabled: false
  cors:
    allowed-origins:
      - http://localhost:3000
```

This gives you working JWT auth with email+password registration and login. RSA keys are generated in-memory. Console fallbacks log that a code would have been sent, never the code itself — set `app.auth.*.dev-code` in your `application-dev.yml` for a fixed code locally.

### Full (Production)

```yaml
server:
  port: 8080

# Активируй профилем окружения (SPRING_PROFILES_ACTIVE=prod), а не в файле.
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
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
    access-token:
      expiry-minutes: 15
    refresh-token:
      expiry-days: 30

  # --- JWT keystore (required for prod) ---
  security:
    jwt:
      keystore-location: ${JWT_KEYSTORE_LOCATION}
      keystore-password: ${JWT_KEYSTORE_PASSWORD}
      key-alias: ${JWT_KEY_ALIAS:jwt}
      issuer: ${JWT_ISSUER}        # уникально для окружения; дефолт template-app отклоняется
      audience: ${JWT_AUDIENCE}

  # --- CORS ---
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}

  # --- Firebase push notifications ---
  firebase:
    enabled: true
    # Set env: FIREBASE_CREDENTIALS_JSON=<service-account JSON, base64-encoded>

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

  # --- Twilio (WhatsApp-first OTP delivery with SMS fallback) ---
  # Starter (≥ 0.1.5) не везёт yaml: задай ключи здесь (можно через ${TWILIO_...})
  # или стандартными env-именами APP_TWILIO_ENABLED, APP_TWILIO_ACCOUNT_SID, ...
  # См. §4 «Twilio (WhatsApp + SMS OTP)».
```

> **Конфиг starter-а.** С 0.1.5 starter не везёт `application*.yaml` — всё, что не задано в твоих файлах, берёт дефолты из кода (таблицы в §4). В 0.1.4 и ниже внутри jar был `application-dev.yaml` с кодами `123456`: на этих версиях свой `application-dev.yml` обязателен.

---

## 4. Property Reference

### Auth Providers

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.auth.local.enabled` | boolean | `true` | Email + password registration/login |
| `app.auth.google.enabled` | boolean | `false` | Google OAuth2 login |
| `app.auth.google.client-id` | string | — | Google OAuth2 client ID |
| `app.auth.apple.enabled` | boolean | `false` | Apple Sign In |
| `app.auth.apple.bundle-id` | string | — | Apple app bundle ID (required when Apple is enabled) |
| `app.auth.phone.enabled` | boolean | `true` | Phone + SMS OTP login |
| `app.auth.telegram.enabled` | boolean | `false` | Telegram bot authentication |
| `app.auth.telegram.bot-token` | string | — | Telegram Bot API token |
| `app.auth.telegram.bot-username` | string | — | Telegram bot username (for deep link URL). Also returned in `TelegramInitResponse.botUsername` (any leading `@` stripped) so frontends can render `@<username>` without parsing the URL. |
| `app.auth.telegram.webhook-secret` | string | — | Secret token for webhook validation |
| `app.auth.telegram.session-ttl-seconds` | int | `300` | Auth session TTL (5 min) |
| `app.auth.telegram.max-attempts` | int | `3` | Max code verification attempts per session |
| `app.auth.telegram.resend-cooldown-seconds` | int | `60` | Cooldown between code resends |
| `app.auth.telegram.max-sessions-per-ip-per-hour` | int | `5` | IP rate limit |
| `app.auth.telegram.max-sessions-per-telegram-user-per-hour` | int | `3` | Per-user rate limit |
| `app.auth.access-token.expiry-minutes` | long | `15` | JWT access token TTL in minutes. Set to `1440` for 1 day, `60` for 1 hour. Env: `ACCESS_TOKEN_EXPIRY_MINUTES`. |
| `app.auth.refresh-token.expiry-days` | int | `30` | Refresh token TTL in days. Env: `REFRESH_TOKEN_EXPIRY_DAYS`. |
| `app.auth.registration.enabled` | boolean | `true` | Public self-registration. When `false`, social/phone/email signup paths reject new accounts — only ADMIN can create users via `/api/v1/admin/users`. |
| `app.auth.email-verification.enabled` | boolean | `false` | Требовать подтверждение email для **новых LOCAL-регистраций**. `true` → `register()` выдаёт токены, но добавляет required-action `VERIFY_EMAIL` + шлёт код; доступ к защищённым API блокируется `RequiredActionFilter` (403) до `POST /verify-email`. Соц/телефон/существующие юзеры не затронуты. |
| `app.auth.security.required-action.enabled` | boolean | `true` | Hard-enforce JWT `required_actions` claim. When `true`, every authenticated request whose token carries a non-empty `required_actions` list is rejected with `403` unless the path matches an allowlist entry. |
| `app.auth.security.required-action.allowed-paths` | list | see below | Ant-pattern paths bypassed by the required-action filter. Default: `/api/v1/users/me`, `/api/v1/users/me/change-password`, `/api/v1/auth/refresh`, `/api/v1/auth/revoke`, `/api/v1/auth/logout`, `/api/v1/auth/verify-email`, `/api/v1/auth/verify-email/resend`. |
| `app.auth.security.public-paths` | list | — | Additional consumer-defined public paths that skip JWT auth. Admin rules are evaluated first, so these cannot open admin endpoints. |
| `app.auth.security.public-api-docs` | boolean | `false` | Serve Swagger UI and `/v3/api-docs` without authentication. |
| `app.auth.security.admin-fresh-login-seconds` | long | `900` | How recent the admin's login (`auth_time`) must be for password/email/phone/role changes, deletion and creation of users. |
| `app.security.production-profiles` | list | `prod,production` | Profiles under which `ProductionSafetyConfig` and the keystore check run. |

### Rate limits (`app.auth.rate-limit`)

Each rule has `max-attempts` and `window-seconds`; exceeded limits answer `429` with `Retry-After`. In-memory by default, so per instance — declare a `RateLimiter` bean on a shared store for a cluster.

| Rule | Default | Keyed by |
|------|---------|----------|
| `enabled` | `true` | Master switch |
| `login` | `10` / `300` s | Email |
| `change-password` | `5` / `300` s | User (also re-authentication with `currentPassword`) |
| `otp-verify` | `10` / `86400` s | Identifier + purpose, across codes |
| `code-send-per-client` | `20` / `3600` s | Client address (IPv6 per /64) — behind a proxy set `server.forward-headers-strategy` |
| `code-send-per-purpose` | `1000` / `3600` s | Purpose, all clients — a circuit breaker; size it to your traffic |

### httpOnly Cookie Auth (`app.auth.cookie`) — опционально, по умолчанию OFF

Режим, при котором access + refresh токены кладутся в `httpOnly`+`Secure`+`SameSite` cookie, а backend сам читает access-токен из cookie (заголовок `Authorization` **всегда** в приоритете — bearer/API-key клиенты не трогаются). SPA больше не хранит токены в `localStorage` → защита от XSS. **Полная обратная совместимость: при `enabled=false` (дефолт) `Set-Cookie` не появляется, resolver не активен.**

Когда `enabled=true`, cookie ставятся централизованно (`AuthResponseCookieAdvice`) на всех точках выдачи `AuthResponse` — `local/login`, `local/register`, `google`, `apple`, `phone/verify`, `telegram/verify`, `refresh`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `app.auth.cookie.enabled` | boolean | `false` | Мастер-переключатель. |
| `app.auth.cookie.secure` | boolean | `true` | `Secure` (только HTTPS). |
| `app.auth.cookie.same-site` | string | `Strict` | `Strict` \| `Lax` \| `None`. Основная защита от CSRF для same-origin SPA. |
| `app.auth.cookie.domain` | string | `""` | Домен cookie. Пусто = host-only. |
| `app.auth.cookie.path` | string | `/` | Path cookie. |
| `app.auth.cookie.access-cookie-name` | string | `access_token` | Имя access-cookie. |
| `app.auth.cookie.refresh-cookie-name` | string | `refresh_token` | Имя refresh-cookie. |
| `app.auth.cookie.access-max-age-seconds` | long | `-1` (= TTL access-JWT) | `Max-Age` access-cookie. Отрицательное = взять из `access-token.expiry-minutes`. |
| `app.auth.cookie.refresh-max-age-days` | long | `30` | `Max-Age` refresh-cookie в днях. |
| `app.auth.cookie.suppress-body-tokens` | boolean | `false` | `true` → НЕ включать токены в JSON body (только `requiredActions`). Cookie становятся единственным транспортом. |

**Refresh/revoke/logout из cookie:**
- `POST /api/v1/auth/refresh` и `/revoke` берут refresh-токен из body **или** из refresh-cookie (приоритет body → cookie).
- `POST /api/v1/auth/logout` — читает refresh-cookie → `revoke()`, чистит обе cookie (`Max-Age=0`). Идемпотентно (нет cookie → `204`). Добавлен в дефолтный `required-action.allowed-paths`.

**CSRF:** в cookie-режиме CSRF-защита включена для всех запросов, которые несут auth-cookie и меняют состояние (включая refresh и logout). Получи токен через `GET /api/v1/auth/csrf` (он же ставит cookie `XSRF-TOKEN`) и отправляй его в заголовке `X-XSRF-TOKEN`. Bearer-запросы без auth-cookie работают без CSRF-токена. `SameSite` — дополнительная защита, не замена. В prod starter откажется стартовать с `secure=false`.

**Аутентификация по cookie** реализована кастомным `BearerTokenResolver` (`CookieBearerTokenResolver`), зарегистрированным в `oauth2ResourceServer` только при `enabled=true`. Downstream JWT-логика не меняется.

### Actuator (bundled)

`spring-boot-starter-actuator` идёт транзитивно через auth-starter. Публичен только `/actuator/health`; остальные actuator-эндпоинты — только `ROLE_ADMIN`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `management.endpoints.web.exposure.include` | csv | `health` | Какие endpoint'ы доступны по HTTP. Spring Boot default. |
| `management.endpoints.web.exposure.exclude` | csv | — | Что скрыть (применяется поверх include). |
| `management.endpoint.health.show-details` | string | `never` | `never` / `when-authorized` / `always`. Детализация health-чеков. |
| `management.endpoint.health.probes.enabled` | boolean | `false` | Включить `/actuator/health/liveness` + `/readiness` (k8s probes). |
| `management.server.port` | int | (main port) | Отдельный порт для actuator (если нужно изолировать от публичного API). |

⚠ Starter делает `permitAll` только на `/actuator/health` (и вложенные пути) — для healthcheck'ов контейнера. Остальные actuator-эндпоинты требуют `ROLE_ADMIN`: в `heapdump` лежит ключ подписи JWT, в `env` — секреты. Чтобы открыть конкретный эндпоинт (например, `/actuator/prometheus` для скрейпа), добавь именно его в `app.auth.security.public-paths` или вынеси actuator на отдельный `management.server.port`, закрытый сетью.

### OpenAPI / Swagger UI

Swagger UI — `/swagger-ui.html`, спецификация — `/v3/api-docs`. **Оба требуют аутентификации**, пока не задан `app.auth.security.public-api-docs=true` (спецификация описывает каждый эндпоинт — в prod держи закрытой). Title по умолчанию берётся из `spring.application.name` консьюмера — **не** хардкодится как "Spring Boot Auth Template API".

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `spring.application.name` | string | `API` | Используется как fallback title если `app.openapi.title` не задан. |
| `app.openapi.title` | string | `${spring.application.name}` | Title в Swagger UI. |
| `app.openapi.version` | string | `1.0.0` | Версия API в Swagger UI. |
| `app.openapi.description` | string | (auth template description) | Описание API в Swagger UI. |

Пример консьюмерского `application.yml`:
```yaml
spring:
  application:
    name: MathHub
app:
  openapi:
    title: MathHub API
    version: 2.0.0
    description: MathHub backend service
```

Полный override: зарегистрируй свой `@Bean OpenAPI` — starter использует `@ConditionalOnMissingBean(OpenAPI::class)`, твой bean заменит дефолтный.

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

Set the `FIREBASE_CREDENTIALS_JSON` env variable to the service-account JSON, **base64-encoded** (`base64 -i service-account.json`).

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
| `app.mail.encryption-key` | string | — | AES-256 key for encrypting SMTP passwords at rest (если хранишь в БД) |
| `app.mail.external.base-url` | string | — | URL внешнего mail-микросервиса (см. `MAIL_API_README.md`). Если задан — активируется `ExternalMailService` вместо SMTP |
| `app.mail.external.master-key` | string | — | Master API key для external mail service, шлётся в header `X-Api-Key` |

**Приоритет backend'ов** (от старшего к младшему):

1. `app.mail.external.base-url` задан → `ExternalMailService` (HTTP → внешний сервис)
2. `app.mail.enabled=true` + SMTP конфиг → `SmtpMailService` (прямой SMTP через JavaMailSender)
3. Дефолт → `ConsoleMailService` (логирует в console, не шлёт реально)

Все три bean реализуют `MailService` + `EmailService` — код потребителя не меняется при переключении.

### Twilio (WhatsApp + SMS OTP)

OTP delivery идёт через `OtpDeliveryService`: сначала WhatsApp, при ошибке (`RuntimeException` от Twilio) — fallback на SMS. Если `app.twilio.whatsapp.enabled=false` или bean не зарегистрирован — звонят сразу в `SmsService` (`TwilioSmsService` при `app.twilio.sms.enabled=true`, иначе `ConsoleSmsService`).

**Связка с env.** С 0.1.5 starter не везёт yaml, поэтому `TWILIO_*`-переменные работают, только если связать их в своём `application-prod.yml` (дефолты в коде — всё выключено):

```yaml
app:
  twilio:
    enabled: ${TWILIO_ENABLED:false}
    account-sid: ${TWILIO_ACCOUNT_SID:}
    auth-token: ${TWILIO_AUTH_TOKEN:}
    whatsapp:
      enabled: ${TWILIO_WHATSAPP_ENABLED:false}
      from: ${TWILIO_WHATSAPP_FROM:}
      content-sid: ${TWILIO_WHATSAPP_TEMPLATE_SID:}
    sms:
      enabled: ${TWILIO_SMS_ENABLED:false}
      from: ${TWILIO_SMS_FROM:}
```

| Property | Env var (со связкой выше) | Type | Default | Description |
|----------|---------------------------|------|---------|-------------|
| `app.twilio.enabled` | `TWILIO_ENABLED` | boolean | `false` | Master toggle. Без него `TwilioConfig` не загружается и Twilio SDK не инициализируется. |
| `app.twilio.account-sid` | `TWILIO_ACCOUNT_SID` | string | `""` | Twilio Account SID (AC…). Required when `enabled=true`. |
| `app.twilio.auth-token` | `TWILIO_AUTH_TOKEN` | string | `""` | Twilio Auth Token. Required when `enabled=true`. |
| `app.twilio.whatsapp.enabled` | `TWILIO_WHATSAPP_ENABLED` | boolean | `false` | Регистрирует `TwilioWhatsAppService` как `WhatsAppService` bean. `OtpDeliveryService` пытается его первым. |
| `app.twilio.whatsapp.from` | `TWILIO_WHATSAPP_FROM` | string | `""` | E.164-номер, зарегистрированный в Twilio Console под WhatsApp. **Без** префикса `whatsapp:` — добавляется автоматически. |
| `app.twilio.whatsapp.content-sid` | `TWILIO_WHATSAPP_TEMPLATE_SID` | string | `""` | Approved Content Template SID (`HX…`). Business-initiated WhatsApp требует pre-approved template — раздать произвольный текст нельзя. Шаблон должен иметь одну переменную `{{1}}` под OTP-код. |
| `app.twilio.sms.enabled` | `TWILIO_SMS_ENABLED` | boolean | `false` | Регистрирует `TwilioSmsService` как `SmsService`. Console-default (`ConsoleSmsService`) при этом backoff'ится через `@ConditionalOnMissingBean`. |
| `app.twilio.sms.from` | `TWILIO_SMS_FROM` | string | `""` | E.164 sender или alphanumeric sender ID (где разрешено). |

**Prod-минимум — env vars (при связке выше):**
```bash
export TWILIO_ENABLED=true
export TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_WHATSAPP_ENABLED=true
export TWILIO_WHATSAPP_FROM=+14155238886
export TWILIO_WHATSAPP_TEMPLATE_SID=HXxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export TWILIO_SMS_ENABLED=true
export TWILIO_SMS_FROM=+14155551234
```

> **WhatsApp Content Template.** Создай в Twilio Console → Content Editor → Authentication template, одна переменная `{{1}}` — код OTP. После approval скопируй SID (`HX…`) в `TWILIO_WHATSAPP_TEMPLATE_SID`.
> **Sandbox dev.** Для теста используй Twilio WhatsApp Sandbox — `from` = `+14155238886`, user должен «join <sandbox-code>» со своего номера. Sandbox не требует approved template.
> **Fallback семантика.** Любой `RuntimeException` (включая `com.twilio.exception.ApiException`) от WhatsApp triggers SMS. `Error` (OOM и пр.) НЕ перехватывается — propagates наружу.
> **IDE warnings.** IntelliJ может ругаться "Cannot resolve configuration property" на `app.twilio.*` ключи — это cosmetic, runtime binding работает. Чтобы убрать — нужен kapt + `spring-boot-configuration-processor` на стороне starter-а.

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

**Любой bean стартера можно заменить своим.** Все bean'ы регистрируются явно с
`@ConditionalOnMissingBean` (starter не сканирует свой пакет), поэтому достаточно объявить
`@Bean` того же типа в своём приложении — победит твой:

```kotlin
@Configuration
class MyOverrides {
    // Заменяет kz.innlab.starter.authentication.service.AuthTokenIssuer целиком
    @Bean
    fun authTokenIssuer(tokenService: TokenService, refreshTokenService: RefreshTokenService) =
        MyAuthTokenIssuer(tokenService, refreshTokenService)
}
```

⚠ **Имена bean'ов — часть контракта.** Они совпадают с decapitalized именем класса
(`authTokenIssuer`, `telegramAuthService`, …), и `@Qualifier` / `@Async` / 
`@ConditionalOnMissingBean(name = …)` резолвятся по ним. Переименование = breaking change;
контракт зафиксирован в `AutoConfigurationContractTest`.

Ниже — частный случай: console-logging defaults для SMS, email и push.

OTP delivery теперь идёт через `OtpDeliveryService` — оркестратор с цепочкой **WhatsApp → SMS**:
- если зарегистрирован bean `WhatsAppService` — пытаемся послать через WhatsApp;
- если он бросает `RuntimeException` — fallback на `SmsService`;
- если WhatsApp bean отсутствует — звонок сразу в SMS.

Twilio implementations (`TwilioWhatsAppService`, `TwilioSmsService`) уже встроены в starter — включаются конфигом из §4. Если нужен другой провайдер (Vonage, MessageBird, in-house gateway) — зарегистрируй собственные beans:

```kotlin
@Configuration
class MyServiceOverrides {

    // WhatsApp provider — пробуется первым в OtpDeliveryService.
    // ДОЛЖЕН throw на сбое доставки, иначе fallback на SMS не сработает.
    @Bean
    fun whatsAppService(): WhatsAppService = object : WhatsAppService {
        override fun sendCode(phone: String, code: String) {
            // your implementation. Throw RuntimeException on delivery failure.
        }
    }

    // SMS provider (fallback channel)
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

The default implementations use `@ConditionalOnMissingBean`, so your beans take priority automatically. `WhatsAppService` имеет **no default bean** — `OtpDeliveryService` инжектит его как `Optional<WhatsAppService>` и тихо skip'ает WhatsApp-leg, если bean не найден.

---

## 5.1 Использование `MailService` из downstream-кода

Starter регистрирует bean `MailService` через auto-config. Любой `@Service` / `@Component` в твоём проекте может его инжектить — конкретная реализация выбирается из конфига (см. §4 → "Приоритет backend'ов").

### Интерфейсы

```kotlin
// kz.innlab.starter.notification.service.MailService
interface MailService {
    fun send(to: String, subject: String, textBody: String? = null,
             htmlBody: String? = null, attachments: List<EmailAttachment> = emptyList())

    fun sendEmail(userId: UUID, to: String, subject: String, textBody: String? = null,
                  htmlBody: String? = null, attachments: List<EmailAttachment> = emptyList()): UUID
}

// kz.innlab.starter.authentication.service.EmailService
interface EmailService {
    fun sendCode(to: String, code: String, purpose: String)
}
```

| Метод | Запись в `auth.mail_history` | Требует `userId` из `auth.users` |
|---|---|---|
| `MailService.send()` | нет | нет |
| `MailService.sendEmail()` | **да** (возвращает `UUID`) | **да** (FK constraint) |
| `EmailService.sendCode()` | нет | нет |

### Пример: отправка письма с tracking

```kotlin
package com.example.myapp

import kz.innlab.starter.notification.service.MailService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderNotifier(private val mailService: MailService) {

    fun notifyOrderShipped(userId: UUID, userEmail: String, orderId: String) {
        val mailId: UUID = mailService.sendEmail(
            userId = userId,
            to = userEmail,
            subject = "Order $orderId shipped",
            textBody = "Your order is on the way.",
            htmlBody = "<h1>Order $orderId</h1><p>Shipped!</p>"
        )
        // mailId сохранён в auth.mail_history
        // user может посмотреть историю через GET /api/v1/mail/history
    }
}
```

### Пример: системный алерт без tracking

```kotlin
@Service
class SystemAlerts(private val mailService: MailService) {
    fun alertAdmins(message: String) {
        mailService.send(
            to = "admin@example.com",
            subject = "[ALERT]",
            textBody = message
        )
    }
}
```

### Пример: с attachments

```kotlin
import kz.innlab.starter.notification.service.EmailAttachment

val pdf = EmailAttachment(
    filename = "invoice.pdf",
    contentType = "application/pdf",
    bytes = pdfBytes
)
mailService.sendEmail(
    userId = userId,
    to = "user@example.com",
    subject = "Invoice",
    htmlBody = "<b>Your invoice attached</b>",
    attachments = listOf(pdf)
)
```

### Какой backend сработает

| `application.yml` downstream | Активный bean | Поведение |
|---|---|---|
| `app.mail.external.base-url: http://...` + `master-key` | `ExternalMailService` | HTTP POST на external сервис (см. `MAIL_API_README.md`) |
| `app.mail.enabled: true` + `app.mail.smtp.*` | `SmtpMailService` | прямой SMTP через JavaMailSender, retry по `app.mail.retry.*` |
| Без конфига | `ConsoleMailService` | логирует `[MAIL] Sending email to ...` в stdout, реально не шлёт |

**Важно:** при `ConsoleMailService` метод **не бросает исключение** — `sendEmail()` возвращает валидный `UUID`, но письмо никуда не уйдёт. Используется только для dev/test. На проде задай один из реальных backend'ов.

### Конфиг external mail microservice

```yaml
app:
  mail:
    external:
      base-url: ${MAIL_SERVICE_URL}            # напр. http://mail.internal:8080
      master-key: ${MAIL_SERVICE_MASTER_KEY}   # ключ из .env mail-сервиса
```

### Конфиг прямого SMTP

```yaml
app:
  mail:
    enabled: true
    smtp:
      host: ${SMTP_HOST}
      port: ${SMTP_PORT:587}
      username: ${SMTP_USERNAME}
      password: ${SMTP_PASSWORD}
      from: ${SMTP_FROM:noreply@example.com}
      ssl-enabled: ${SMTP_SSL_ENABLED:false}
    retry:
      max-attempts: 3
      delay-ms: 5000
```

### Ограничения

- `MailService.sendEmail(userId, ...)` падает с FK violation если `userId` не существует в `auth.users`. Для писем не привязанных к auth user — используй `send()`
- `ExternalMailService` использует один master key — все письма идут под одним client'ом во внешнем сервисе
- При SMTP retry — exception пробрасывается **только после исчерпания** `max-attempts`
- HTML auto-detect: `MailService.send()` определяет HTML по наличию `<tags>` если `htmlBody` не задан явно

### Минимальный smoke-test (downstream)

```kotlin
@RestController
class DebugController(private val mailService: MailService) {
    @GetMapping("/debug/mail-test")
    fun testMail(): String {
        mailService.send(
            to = "test@example.com",
            subject = "Test from starter",
            textBody = "Works."
        )
        return "Check logs (console mode) or inbox (smtp/external mode)"
    }
}
```

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

Auth-starter поднимает **отдельный** `Flyway`-bean (`authFlyway`) на `classpath:db/migration-auth` (schema `auth`) — не нужно прописывать его в свой `locations`. Не клади свои миграции в `db/migration-auth` и наоборот.

Migrations V1–V16. Never edit a starter migration in your database history: a changed checksum stops Flyway. Tables created by starter (schema `auth`): `users`, `user_providers`, `user_provider_ids`, `user_roles`, `user_required_actions`, `refresh_tokens`, `verification_codes`, `telegram_auth_sessions`, `device_tokens`, `notification_history`, `notification_topics`, `notification_preferences`, `mail_history`, `admin_audit_log`.

---

## 7. Admin User Management

When `app.auth.registration.enabled=false`, public signup paths reject new users. Only ADMIN-role principals can provision accounts through `/api/v1/admin/users/**`. All mutating endpoints write to the `admin_audit_log` table (with before/after values) and emit an `ADMIN_AUDIT` SLF4J line (admin, action and target only — no personal data). The endpoints are protected by URL rules **and** `@PreAuthorize("hasRole('ADMIN')")`, so they stay closed if you disable or replace the starter's filter chain.

### Endpoints (all require `ROLE_ADMIN`)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/admin/users?q=&page=&size=&sort=` | Paginated user list, brief representation. `q` matches email/name/phone substring. `sort` only by `email`, `name`, `phone`, `createdAt` (anything else → 400); `size` capped at 100. |
| `GET` | `/api/v1/admin/users/{id}` | Full user profile. |
| `POST` | `/api/v1/admin/users` | ⏱ Create user — bypasses `registration.enabled`. Body: `{email, password, name?, roles?, temporary?}`. |
| `PATCH` | `/api/v1/admin/users/{id}/password` | ⏱ Reset password. Revokes all refresh tokens. Body: `{newPassword, temporary?}`. |
| `PATCH` | `/api/v1/admin/users/{id}/email` | ⏱ Change email — skips OTP, revokes the user's refresh tokens. Body: `{email}`. |
| `PATCH` | `/api/v1/admin/users/{id}/phone` | ⏱ Change phone — skips SMS. Normalizes to E.164. Body: `{phone}`. |
| `PATCH` | `/api/v1/admin/users/{id}/profile` | Update name (≤ 255) + picture (http(s) URL). Body: `{name?, picture?}`. |
| `PATCH` | `/api/v1/admin/users/{id}/roles` | ⏱ Replace role set; revokes the user's refresh tokens. Body: `{roles: [USER, ADMIN]}`. |
| `DELETE` | `/api/v1/admin/users/{id}` | ⏱ Delete user — revokes refresh tokens. |

⏱ — нужен свежий вход: `auth_time` токена не старше `app.auth.security.admin-fresh-login-seconds` (900 с), иначе `403 "Log in again to make this change"`. Украденный старый токен админа не может передать аккаунт.

**Guardrails:**
- Last-admin lockout blocked on role downgrade and delete (`countAdmins() <= 1` → 409). Admin rows are locked before counting, so two admins demoting each other at once cannot leave none.
- Self-demotion of ADMIN role blocked (409).
- Self-delete blocked (409).
- Email/phone collisions → 409.

### Temporary Password Flow (Keycloak-style)

Set `"temporary": true` when creating a user or resetting password. Starter:

1. Saves `users.password_temporary = true`.
2. Adds `UPDATE_PASSWORD` to `user_required_actions`.
3. Issues access tokens with claim `required_actions: ["UPDATE_PASSWORD"]`.
4. `AuthResponse` now carries `requiredActions: ["UPDATE_PASSWORD"]` — frontend redirects to change-password screen.
5. `RequiredActionFilter` returns `403 {"requiredActions":[…]}` on every endpoint outside the allowlist (see property `app.auth.security.required-action.allowed-paths`).
6. User calls `POST /api/v1/users/me/change-password` — `AccountManagementService` clears the flag + action and revokes all refresh tokens.
7. User re-logins → JWT clean → unlocked.

### Bootstrap First Admin

Starter ships **no** seed migration — consumer decides how to mint the initial admin:

```sql
-- consumer migration in classpath:db/migration/
INSERT INTO auth.users (id, email, password_hash, password_temporary)
VALUES ('019300a0-0000-7000-8000-000000000001', 'admin@example.com',
        '$2a$10$…bcrypt hash…', true);

INSERT INTO auth.user_providers (user_id, provider) VALUES
  ('019300a0-0000-7000-8000-000000000001', 'LOCAL');

INSERT INTO auth.user_roles (user_id, role) VALUES
  ('019300a0-0000-7000-8000-000000000001', 'ADMIN');

INSERT INTO auth.user_required_actions (user_id, action) VALUES
  ('019300a0-0000-7000-8000-000000000001', 'UPDATE_PASSWORD');
```

Generate the bcrypt hash with `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder` or `htpasswd -bnBC 10 "" "$PW" | tr -d ':\n'`.

---

## 8. Troubleshooting (что обычно ломает старт)

| Симптом | Причина | Фикс |
|---------|---------|------|
| `UnreachableFilterChainException` на старте | Своя цепочка без `securityMatcher` (тоже `anyRequest`) конфликтует с цепочкой starter-а | Дай своей цепочке узкий `securityMatcher` (§0) или убери её — она не обязательна |
| `Unsupported class file major version` / Kotlin compile error | Java/Kotlin не совпадают с baseline проекта | Используй JVM 25 + Kotlin 2.3.21 |
| Подключается к БД `template` с юзером `postgres` (starter ≤ 0.1.4) | Нет своего `application-dev.yml` → подхватился bundled из jar starter-а (вместе с кодами `123456`) | Обновись до 0.1.5 или создай `src/main/resources/application-dev.yml` |
| Под prod не стартует: «Refusing to start under a production profile» | `ProductionSafetyConfig` нашёл dev-code, дефолтный issuer/audience, Telegram без токена/секрета, cookie без `Secure` или console-fallback | Исправь перечисленное в сообщении; console-fallback — waiver per channel (`app.security.console-fallbacks.allow-*`) |
| Под prod не стартует: «Production JWT signing key is required» | Не задан keystore | `app.security.jwt.keystore-location` + `keystore-password` (`./scripts/generate-keystore.sh`) |
| `BeanDefinitionOverrideException` для security-конфига | Явно добавлен `spring-boot-starter-security` | Убери — приходит транзитивно |
| Flyway: «migration checksum mismatch» на auth-таблицах | Свои миграции положены в `db/migration-auth` | Перенеси свои в `db/migration/`, оставь `db/migration-auth` и схему `auth` за starter-ом |
| `creator/editor` всегда `null` в `Auditable` | Не зарегистрирован `AuditorAware<UUID>` | Подожди, пока auth-starter положит principal в `SecurityContext`, и регистрируй `AuditorAware`, читающий из него |
| 401 на `/api/**` без JWT в dev | Это норма — starter защищает `anyRequest` | Получи токен через `/auth/login` |
| `403 {"requiredActions":["UPDATE_PASSWORD"]}` на любом endpoint | JWT carries non-empty `required_actions` claim — `RequiredActionFilter` блокирует всё кроме allowlist | Юзер должен вызвать `POST /api/v1/users/me/change-password`. После — re-login, claim очистится |
| `Email already registered` (409) при `POST /admin/users` | Email занят в `auth.users` | Используй другой email, или PATCH существующего юзера |
| `Cannot remove last ADMIN` (409) | Попытка снять ADMIN-роль / удалить единственного админа | Сначала promote другого юзера в ADMIN, затем повтори |
| `NoUniqueBeanDefinitionException: EmailService` на старте (версии **0.0.6 / 0.0.7**) | В `MailConfig` было два fallback bean (`consoleMailService` + `consoleEmailService`), а `ConsoleMailService` уже реализует `EmailService` → Spring Boot 4 видит 2 кандидата на `EmailService`. На проде каскад `EntityManagerFactory closed` во всех `@Scheduled`/async тредах | Обнови starter до **0.0.11+**. Fallback разделён на два single-interface bean (`ConsoleMailService`=`MailService`, `ConsoleEmailService`=`EmailService`) — по одному кандидату на интерфейс |
| `BeanNotOfRequiredTypeException: consoleMailService ожидался MailService, но EmailService$MockitoMock` / `Failed to load ApplicationContext` в тестах с `@MockitoBean EmailService` (**0.0.8 – 0.0.10**) | Объединённый dual-interface bean 0.0.8: mock `EmailService` подменял единственный bean → `MailController` терял `MailService` | Обнови до **0.0.11+** (fallback снова разделён) |

---

## Версии и breaking changes

Для 0.1.x — [CHANGELOG.md](CHANGELOG.md) и блок «Upgrading» в начале этого файла. Ниже — история 0.0.x.

### 0.0.12 (2026-07-16) — FEAT: подтверждение email при регистрации (опционально)

**Что добавлено:**
- Новый флаг `app.auth.email-verification.enabled` (дефолт `false` — **регистрация не меняется**).
- При `true`: `POST /api/v1/auth/local/register` для нового email+password юзера → `emailVerified=false` + required-action `VERIFY_EMAIL` + код на почту (`EmailService.sendCode(email, code, "VERIFY_EMAIL")`). Токены выдаются (soft gate), но JWT несёт `required_actions:["VERIFY_EMAIL"]` → `RequiredActionFilter` даёт `403` на защищённые эндпоинты вне allowlist.
- `POST /api/v1/auth/verify-email {email, verificationId, code}` → `emailVerified=true`, снимает action. После re-login/refresh JWT чистый. `verificationId` возвращается прямо в ответе `register` (nullable, `@JsonInclude(NON_NULL)` — присутствует только когда верификация pending) → клиент шлёт verify без промежуточного `/verify-email/resend`:

```json
POST /api/v1/auth/local/register  →  201
{
  "accessToken": "...",
  "refreshToken": "...",
  "requiredActions": ["VERIFY_EMAIL"],
  "verificationId": "0198c3f2-0000-7000-8000-000000000001"
}
```
- `POST /api/v1/auth/verify-email/resend {email}` → новый код (rate-limit 1/60с, анти-энумерация).
- Линковка LOCAL к соц-аккаунту НЕ требует повторной верификации. `emailVerified` в `GET /api/v1/users/me`.

**Новые/изменённые классы:**
- `VerificationPurpose` +`VERIFY_EMAIL`. `User` +`emailVerified: Boolean = true` (дефолт true — старые/соц/телефон юзеры не блокируются). Миграция `db/migration-auth/V7__add_email_verified.sql` (`email_verified BOOLEAN NOT NULL DEFAULT TRUE`).
- `LocalAuthService.register` — гейт нового юзера + прокидывает `verificationId` в ответ. `AccountManagementService` +`verifyEmail`/`resendEmailVerification`. `LocalAuthController` +2 эндпоинта. DTO: `VerifyEmailRequest`, `ResendEmailVerificationRequest`. `AuthResponse` +`verificationId: UUID? = null` + `@JsonInclude(NON_NULL)`. `AuthSecurityProperties` — verify-email пути в `required-action.allowed-paths`. `UserProfileResponse` +`emailVerified`.

**Что делать downstream:** ничего обязательного. `RequiredAction.VERIFY_EMAIL` уже был в enum. Включить — `app.auth.email-verification.enabled=true` + реальный `EmailService` (иначе код только в console-логах).

---

### 0.0.11 (2026-07-13) — FEAT: httpOnly-cookie аутентификация (опционально)

**Что добавлено:**
- Новый блок конфига `app.auth.cookie.*` (см. §4 → «httpOnly Cookie Auth»). По умолчанию `enabled=false` — **никаких изменений** для существующих body-token потребителей.
- При `enabled=true`: access + refresh кладутся в `httpOnly`+`Secure`+`SameSite` cookie на всех точках выдачи `AuthResponse` (login/register/google/apple/phone-verify/telegram-verify/refresh) через централизованный `AuthResponseCookieAdvice` — без дублирования в контроллерах.
- Access-токен читается из cookie кастомным `CookieBearerTokenResolver` (заголовок `Authorization` всегда в приоритете).
- `POST /api/v1/auth/refresh` и `/revoke` принимают refresh из body **или** cookie (приоритет body → cookie).
- Новый `POST /api/v1/auth/logout`: revoke refresh-cookie + очистка обеих cookie. Идемпотентно (204).
- `suppress-body-tokens=true` — убрать токены из JSON body (cookie как единственный транспорт).

**Новые/изменённые классы:**
- `config/AuthCookieProperties.kt` (new), `authentication/cookie/{AuthCookieWriter, AuthResponseCookieAdvice, CookieBearerTokenResolver}.kt` (new).
- `AuthController` — refresh/revoke из cookie + `logout`. `SecurityConfig` — регистрация resolver. `RefreshRequest` — `refreshToken` теперь опционален (снят `@NotBlank`; пустой body → cookie). `AuthSecurityProperties` — `/api/v1/auth/logout` добавлен в `required-action.allowed-paths`.

**Что делать downstream:** ничего обязательного. Чтобы включить — выставь `app.auth.cookie.enabled=true` (+ `same-site`, `secure`).

**Также в 0.0.11 — FIX: разделены console-fallback bean для mail.**
- В 0.0.8 `MailService` + `EmailService` покрывались одним bean `ConsoleMailService` (dual-interface). Это ломало тесты с `@MockitoBean EmailService`: mock подменял единственный bean → `MailController` (ждёт `MailService`) падал с `BeanNotOfRequiredTypeException` / `Failed to load ApplicationContext`.
- Теперь два раздельных single-interface bean: `ConsoleMailService` реализует только `MailService`, новый `ConsoleEmailService` — только `EmailService`. Каждый под своим `@ConditionalOnMissingBean`:
  ```kotlin
  @Bean @ConditionalOnMissingBean(MailService::class)
  fun consoleMailService(): ConsoleMailService = ConsoleMailService()

  @Bean @ConditionalOnMissingBean(EmailService::class)
  fun consoleEmailService(): ConsoleEmailService = ConsoleEmailService()
  ```
- `NoUniqueBeanDefinition` (баг 0.0.6/0.0.7) НЕ возвращается: на каждый интерфейс по-прежнему ровно один кандидат — `Smtp`/`External` (dual-interface) отступают console-bean через `@ConditionalOnMissingBean`.
- Prod-пути (SMTP / External) не тронуты. Downstream действий не требуется.

---

### 0.0.10 (2026-07-10) — FEAT: resend-cooldown в ответе `/auth/phone/request`

**Что изменилось:**
- `POST /api/v1/auth/phone/request` теперь возвращает не только `verificationId`, но и когда можно переотправить код — фронт рисует countdown-таймер без хардкода 60с.

**Новый response body:**
```json
{
  "verificationId": "0198e2c0-0000-7000-8000-000000000001",
  "resendAvailableAt": "2026-07-10T12:34:56Z",
  "retryAfterSeconds": 60
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| `verificationId` | UUID | Передаётся обратно в `/auth/phone/verify` (как раньше). |
| `resendAvailableAt` | ISO-8601 instant (UTC) | Абсолютное время, когда разрешён следующий запрос кода. Переживает reload/сдвиг часов клиента — предпочтительнее для UI. |
| `retryAfterSeconds` | long | Длина cooldown-окна в секундах (сейчас `60` = `RATE_LIMIT_SECONDS`). Для countdown-таймера. |

**Rate limit семантика (без изменений):** повторный запрос до истечения cooldown → `409` c `IllegalStateException("Please wait before requesting a new code")`. `resendAvailableAt` даёт фронту точное время, чтобы не ловить 409.

**Что делать downstream:** ничего обязательного. Старые клиенты продолжают читать `verificationId`, лишние поля игнорируют. Новые — используют `resendAvailableAt` / `retryAfterSeconds` для UX.

---

### 0.0.8 (2026-06-18) — FIX: дубликат `EmailService` bean

**Проблема (0.0.6, 0.0.7):**
- `MailConfig` регистрировал два fallback bean: `consoleMailService(): MailService` и `consoleEmailService(): EmailService`.
- `ConsoleMailService` реализует **оба** интерфейса (`MailService, EmailService`). Spring регистрировал его под обоими супер-типами + отдельный `ConsoleEmailService` под `EmailService` → 2 кандидата на `EmailService` → `NoUniqueBeanDefinitionException`.
- На Spring Boot 4 контекст не поднимается при `app.mail.enabled=false` (дефолт), любой consumer с `private val emailService: EmailService` в конструкторе валится.

**Фикс в 0.0.8:**
```kotlin
// MailConfig.kt
@Bean
@ConditionalOnMissingBean(value = [MailService::class, EmailService::class])
fun consoleMailService(): ConsoleMailService = ConsoleMailService()
```
- Возвращаемый тип — конкретный класс → Spring регистрирует под обоими интерфейсами.
- `ConsoleEmailService` удалён.

> ⚠ **Отменено в 0.0.11.** Объединённый dual-interface bean 0.0.8 ломал тесты с `@MockitoBean EmailService` (mock подменял единственный bean → `MailController` терял `MailService`). В 0.0.11 fallback снова разделён на два single-interface bean (`ConsoleMailService` = только `MailService`, `ConsoleEmailService` = только `EmailService`), но `NoUniqueBeanDefinition` не возвращается — на каждый интерфейс ровно один кандидат. См. changelog 0.0.11.

**Что делать downstream:**
- Подняться на `0.0.8` в `pom.xml`.
- Никаких изменений в коде потребителя не нужно — `EmailService` инжектится по-прежнему.

---

## Quick Checklist

- [ ] JVM **25**
- [ ] `pom.xml` из §0 — БЕЗ явного `spring-boot-starter-security`
- [ ] `application.yml` + `application-dev.yml` + `application-prod.yml` лежат в `src/main/resources/`
- [ ] `SPRING_PROFILES_ACTIVE` задан окружением (не в `application.yml`); `dev-code` — только в `application-dev.yml`
- [ ] Своя `SecurityFilterChain` (если есть) — с узким `securityMatcher`, `/actuator/**` целиком не открыт
- [ ] Kotlin compiler plugins (`spring + jpa + all-open + no-arg`) с extension на `@Entity/@MappedSuperclass/@Embeddable`
- [ ] Свои миграции — в `db/migration/`, starter-овские — в `db/migration-auth`, схема `auth` (не трогать)
- [ ] PostgreSQL поднят (`docker compose up -d postgres`)
- [ ] (Prod) сгенерирован JWT keystore, `app.security.jwt.*` (включая `issuer` и `audience`) связаны с env vars в своём `application-prod.yml`
- [ ] (Prod) за reverse proxy: `server.forward-headers-strategy` задан
- [ ] (Prod) `APP_CORS_ALLOWED_ORIGINS` выставлен
- [ ] (Optional) Реализованы `SmsService` / `WhatsAppService` / `EmailService` / `TelegramBotService` для реальной доставки
- [ ] (Optional) Twilio WhatsApp+SMS: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_WHATSAPP_FROM`, `TWILIO_WHATSAPP_TEMPLATE_SID` (HX…), `TWILIO_SMS_FROM` через env vars + связка в своём yaml (§4)
- [ ] (Optional) Telegram bot: `app.auth.telegram.bot-token`, `bot-username`, `webhook-secret` (через env vars со связкой в своём yaml)
- [ ] (Optional) Firebase для push (`FIREBASE_CREDENTIALS_JSON`, base64)
