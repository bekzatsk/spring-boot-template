# Auth Spring Boot Starter

Ready-to-use Spring Boot starter for JWT authentication with multi-provider social login (Google, Apple, email+password, phone+SMS OTP), push notifications (Firebase), and email (SMTP/IMAP).

**Stack:** Spring Boot 4.0.5 / Kotlin / Java 24 / PostgreSQL 18 / Flyway / Firebase Admin SDK

---

## Usage in Another Project

The starter is published to **GitHub Packages** at [`bekzatsk/spring-boot-template`](https://github.com/bekzatsk/spring-boot-template/packages).

> GitHub Packages requires authentication even for read access on public packages. You need a Personal Access Token (classic) with the `read:packages` scope.

### 1. Create a GitHub PAT (Classic)

1. Open https://github.com/settings/tokens → **Generate new token (classic)**.
2. Scopes: `read:packages` (and `write:packages` if you also publish).
3. Copy the token (starts with `ghp_…`). GitHub shows it only once.

### 2. Configure `~/.m2/settings.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>ghp_xxxxxxxxxxxxxxxxxxxxxxxx</password>
    </server>
  </servers>
</settings>
```

Lock down permissions: `chmod 600 ~/.m2/settings.xml`.

### 3. Add Repository + Dependency to your `pom.xml`

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/bekzatsk/spring-boot-template</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>kz.innlab</groupId>
    <artifactId>auth-spring-boot-starter</artifactId>
    <version>0.0.3-SNAPSHOT</version>
  </dependency>
</dependencies>
```

### Alternative: Build from Source

Skip GitHub Packages and install to local Maven repo:

```bash
git clone https://github.com/bekzatsk/spring-boot-template.git auth-starter
cd auth-starter
./mvnw clean install -DskipTests
```

This publishes `kz.innlab:auth-spring-boot-starter:0.0.3-SNAPSHOT` to `~/.m2/repository`.

### 4. Configure `application.yaml`

Minimal configuration for dev:

```yaml
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

app:
  auth:
    local:
      enabled: true
    google:
      enabled: false
    apple:
      enabled: false
    phone:
      enabled: false
  firebase:
    enabled: false
  mail:
    enabled: false
  cors:
    allowed-origins:
      - http://localhost:3000
```

The starter auto-configures everything else. In dev profile, RSA keys are generated in-memory and SMS/email codes are logged to console.

### 5. Run

```bash
# Start PostgreSQL
docker compose up -d

# Run your app
./mvnw spring-boot:run
```

Flyway migrations run automatically and create all required tables.

---

## What You Get

The starter auto-registers these endpoints:

### Auth — Public (`/api/v1/auth`)

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/auth/local/register` | Register with email + password |
| `POST /api/v1/auth/local/login` | Login with email + password |
| `POST /api/v1/auth/google` | Google ID token auth |
| `POST /api/v1/auth/apple` | Apple ID token auth |
| `POST /api/v1/auth/phone/request` | Request SMS OTP |
| `POST /api/v1/auth/phone/verify` | Verify SMS OTP |
| `POST /api/v1/auth/telegram/init` | Init Telegram auth session → returns `sessionId`, `botUrl`, `botUsername`, `expiresAt` |
| `POST /api/v1/auth/telegram/verify` | Verify Telegram code |
| `POST /api/v1/auth/telegram/resend` | Resend Telegram code |
| `GET /api/v1/auth/telegram/status/{sessionId}` | Poll Telegram session status |
| `POST /api/v1/auth/refresh` | Refresh access token |
| `POST /api/v1/auth/revoke` | Revoke refresh token (logout) |
| `POST /api/v1/auth/forgot-password` | Request password reset code |
| `POST /api/v1/auth/reset-password` | Reset password with code |

### Account Management — Authenticated (`/api/v1/users/me`)

| Endpoint | Description |
|----------|-------------|
| `POST /users/me/change-password` | Change password |
| `POST /users/me/change-email/request` | Request email change |
| `POST /users/me/change-email/verify` | Verify email change |
| `POST /users/me/change-phone/request` | Request phone change |
| `POST /users/me/change-phone/verify` | Verify phone change |

### User Profile — Authenticated

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/users/me` | Get current user profile |

### Notifications — Authenticated (`/api/v1/notifications`)

| Endpoint | Description |
|----------|-------------|
| `POST /notifications/tokens` | Register device token |
| `GET /notifications/tokens` | List device tokens |
| `DELETE /notifications/tokens/{deviceId}` | Delete device token |
| `POST /notifications/send/token` | Send push to device |
| `POST /notifications/send/multicast` | Send push to multiple devices |
| `POST /notifications/send/topic` | Send push to topic |
| `POST /notifications/topics/{name}/subscribe` | Subscribe to topic |

### Email — Authenticated (`/api/v1/mail`)

| Endpoint | Description |
|----------|-------------|
| `POST /mail/send` | Send email |
| `POST /mail/send/with-attachments` | Send email with attachments |
| `GET /mail/inbox` | List inbox messages (IMAP) |
| `GET /mail/inbox/{messageNumber}` | Get single message |
| `GET /mail/history` | Get mail history |

All auth endpoints return:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "KXKvgX..."
}
```

Swagger UI is available at `/swagger-ui.html`.

---

## Customization

### Plugging in Real SMS/Email Providers

`SmsService` and `EmailService` are interfaces with console-logging defaults (`@ConditionalOnMissingBean`). Define your own bean to override:

```kotlin
@Configuration
class MyServicesConfig {

    @Bean
    fun smsService(): SmsService = object : SmsService {
        override fun sendCode(phone: String, code: String) {
            // Send via Twilio, Nexmo, etc.
        }
    }

    @Bean
    fun emailService(): EmailService = object : EmailService {
        override fun sendCode(to: String, code: String, purpose: String) {
            // Send via SendGrid, SES, etc.
        }
    }
}
```

Similarly, `PushService` and `MailService` can be overridden.

### Adding Your Own Secured Endpoints

The starter configures Spring Security with JWT. Your endpoints are secured by default. To allow public access to specific paths, define a `SecurityFilterChain` bean:

```kotlin
@Configuration
class MySecurityConfig {

    @Bean
    @Order(90) // before the starter's filter chain
    fun mySecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/public/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
```

To access the current user in your controllers:

```kotlin
@GetMapping("/api/v1/my-endpoint")
fun myEndpoint(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<Any> {
    val userId = UUID.fromString(jwt.subject)
    val roles = jwt.getClaimAsStringList("roles")
    // ...
}
```

### Accessing Starter's Repositories and Services

You can inject any starter service or repository:

```kotlin
@Service
class MyService(
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val tokenService: TokenService,
) {
    fun findUser(id: UUID) = userRepository.findById(id)
}
```

---

## Configuration Reference

### Auth Providers

| Property | Default | Description |
|----------|---------|-------------|
| `app.auth.local.enabled` | `true` | Email + password auth |
| `app.auth.google.enabled` | `false` | Google OAuth2 |
| `app.auth.google.client-id` | — | Google OAuth2 client ID |
| `app.auth.apple.enabled` | `true` | Apple Sign In |
| `app.auth.apple.bundle-id` | `com.example.app` | Apple app bundle ID |
| `app.auth.phone.enabled` | `true` | Phone + SMS OTP |
| `app.auth.telegram.enabled` | `false` | Telegram bot authentication |
| `app.auth.telegram.bot-token` | — | Telegram Bot API token |
| `app.auth.telegram.bot-username` | `MathHubBot` | Bot username for deep link URL. Also exposed in `TelegramInitResponse.botUsername` (any leading `@` stripped). |
| `app.auth.telegram.webhook-secret` | — | Secret token for webhook validation |
| `app.auth.refresh-token.expiry-days` | `30` | Refresh token TTL |

### Database

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | Prod | — | JDBC PostgreSQL URL |
| `DB_USERNAME` | No | `postgres` | Database user |
| `DB_PASSWORD` | No | `postgres` | Database password |

### JWT (Production)

| Variable | Required | Description |
|----------|----------|-------------|
| `JWT_KEYSTORE_LOCATION` | Yes | Path to PKCS12 keystore |
| `JWT_KEYSTORE_PASSWORD` | Yes | Keystore password |
| `JWT_KEY_ALIAS` | No (`jwt`) | Key alias |

Generate a keystore:

```bash
keytool -genkey -alias jwt -keyalg RSA -keysize 2048 \
  -keystore jwt.p12 -storetype PKCS12

# Or use the included script:
./scripts/generate-keystore.sh
```

Dev profile uses in-memory RSA keys — no keystore needed.

### Firebase

| Variable | Required | Description |
|----------|----------|-------------|
| `FIREBASE_ENABLED` | No (`false`) | Enable Firebase push |
| `FIREBASE_CREDENTIALS_PATH` | When enabled | Path to service account JSON |

### Email (SMTP/IMAP)

| Variable | Default | Description |
|----------|---------|-------------|
| `MAIL_ENABLED` | `false` | Enable mail service |
| `SMTP_HOST` | — | SMTP server |
| `SMTP_PORT` | `587` | SMTP port |
| `SMTP_USERNAME` | — | SMTP user |
| `SMTP_PASSWORD` | — | SMTP password |
| `SMTP_FROM` | `noreply@example.com` | Sender address |
| `SMTP_SSL_ENABLED` | `false` | SSL for SMTP |
| `IMAP_HOST` | — | IMAP server |
| `IMAP_PORT` | `993` | IMAP port |
| `IMAP_USERNAME` | — | IMAP user |
| `IMAP_PASSWORD` | — | IMAP password |
| `IMAP_SSL_ENABLED` | `true` | SSL for IMAP |

### Other

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_CORS_ALLOWED_ORIGINS` | localhost | Allowed CORS origins |
| `NOTIFICATION_MAX_TOKENS_PER_USER` | `5` | Max device tokens per user |

---

## Database Schema

Flyway migrations create these tables automatically:

```
users                   — id, email, name, picture, password_hash, phone
user_providers          — user_id, provider (GOOGLE|APPLE|LOCAL|PHONE)
user_provider_ids       — user_id, provider, provider_id
user_roles              — user_id, role (USER|ADMIN)
refresh_tokens          — id, user_id, token_hash, expires_at, revoked, used_at
sms_verifications       — id, phone, code_hash, expires_at, used, attempts
verification_codes      — id, user_id, identifier, purpose, code_hash, expires_at
device_tokens           — id, user_id, token, device_type, created_at, updated_at
notification_history    — sent notification records
notification_topics     — topic management
notification_preferences — user channel preferences
mail_history            — sent email records
```

All primary keys are UUID v7 (time-ordered, RFC 9562).

Migrations location: `classpath:db/migration/auth` (V1 through V4).

---

## Token Management

| Token | Format | Expiry | Storage |
|-------|--------|--------|---------|
| Access | RS256 JWT | 15 min | Client only |
| Refresh | Opaque (32 bytes, base64url) | 30 days | SHA-256 hash in DB |
| Verification | 6-digit code | 15 min (email) / 5 min (SMS) | BCrypt hash in DB |

**JWT claims:** `iss`, `sub` (user UUID), `roles`, `exp`, `iat`

**Refresh token rotation:** used tokens within 10s grace window return 409; reuse after grace revokes all user tokens.

---

## Account Linking

One email = one user across all providers. Google, Apple, local, and phone auth with the same email are linked to a single account. `GET /users/me` returns `"providers": ["GOOGLE", "LOCAL", "APPLE"]`.

Phone-only users have `email = ""` with a partial unique index.

---

## Dev vs Prod

| Feature | Dev | Prod |
|---------|-----|------|
| RSA keys | In-memory (regenerated) | PKCS12 keystore |
| Verification codes | Hardcoded `123456` | SecureRandom 6-digit |
| SMS | Console log | Pluggable `SmsService` |
| Email | Console log | Pluggable `EmailService` |
| Push | Console log | Firebase FCM |
| Flyway | `clean-on-validation-error` | Strict validation |

---

## Running Starter Tests

```bash
# All tests (H2 in-memory, no Docker)
./mvnw test

# Single test class
./mvnw test -pl . -Dtest=AccountManagementIntegrationTest

# Single test method
./mvnw test -pl . -Dtest=AccountManagementIntegrationTest#testChangePasswordSuccess
```

---

## Full Example: Minimal App Using the Starter

**pom.xml:**
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
    </parent>

    <groupId>com.example</groupId>
    <artifactId>myapp</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>24</java.version>
        <kotlin.version>2.2.21</kotlin.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>kz.innlab</groupId>
            <artifactId>auth-spring-boot-starter</artifactId>
            <version>0.0.3-SNAPSHOT</version>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/kotlin/com/example/myapp/MyApp.kt:**
```kotlin
package com.example.myapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MyApp

fun main(args: Array<String>) {
    runApplication<MyApp>(*args)
}
```

**src/main/resources/application.yaml:**
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
      enabled: true
    google:
      enabled: false
    apple:
      enabled: false
    phone:
      enabled: false
  firebase:
    enabled: false
  mail:
    enabled: false
  cors:
    allowed-origins:
      - http://localhost:3000
```

Run:

```bash
createdb myapp
./mvnw spring-boot:run
```

Done. You now have full JWT auth at `http://localhost:8080/api/v1/auth/*`.

---

## Error Responses

All errors follow a consistent format:
```json
{ "error": "Bad Request", "message": "email: must be valid", "status": 400 }
```

| Status | When |
|--------|------|
| 400 | Validation failure, invalid format |
| 401 | Invalid credentials, expired token/code |
| 403 | Insufficient permissions |
| 404 | Not found |
| 409 | Email/phone taken, rate limit, grace window |

---

## Publishing (Maintainers)

The starter publishes to **GitHub Packages** via `scripts/publish.sh`.

### One-time setup

1. Create a GitHub PAT (classic) with `write:packages` + `read:packages` scopes: https://github.com/settings/tokens
2. Either export it per shell:
   ```bash
   export GITHUB_TOKEN=ghp_xxx
   ```
   …or store it in `~/.m2/settings.xml` under `<server><id>github</id>` (the script falls back to this if `GITHUB_TOKEN` is unset).

### Publish commands

```bash
# Republish current pom version (e.g. 0.0.3-SNAPSHOT)
./scripts/publish.sh --skip-tests

# Bump to next snapshot
./scripts/publish.sh --snapshot 0.0.3 --skip-tests

# Cut an immutable release (bumps pom, deploys, tags v0.0.2, pushes)
./scripts/publish.sh --release 0.0.2 --skip-tests
```

After publish, package appears at: https://github.com/bekzatsk/spring-boot-template/packages

---

## Changelog

### 0.0.3-SNAPSHOT (unreleased)

**Added**
- `spring-boot-starter-actuator` is now bundled transitively. `/actuator/health` is available out of the box. Consumers no longer need to add the dependency explicitly for docker healthchecks / k8s probes.
  - Only `health` is exposed via web by default (Spring Boot default). Opt in to more endpoints via `management.endpoints.web.exposure.include`.
  - The starter does **not** set `@Order(1)` permitAll on `/actuator/**` — consumers still register their own actuator security filter chain when needed.

### 0.0.2-SNAPSHOT

**Telegram auth**
- `TelegramInitResponse` now includes a `botUsername` field (additive, non-breaking) alongside the existing `botUrl`. Value comes from `app.auth.telegram.bot-username` with any leading `@` stripped. Frontends can render `@MyBot` text without parsing the URL.
