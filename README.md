# Auth Spring Boot Starter

Ready-to-use Spring Boot starter for JWT authentication with multi-provider login (Google, Apple, email+password, email OTP, phone OTP), push notifications (Firebase), and email (SMTP/IMAP).

**Stack:** Spring Boot 4.1.1 / Kotlin 2.3.21 / Java 25 / PostgreSQL 18 / Flyway / Firebase Admin SDK

---

Current release: **`0.1.7`**. **Do not use `0.1.0`** — cookie authentication is dead in it
(`Set-Cookie` is never sent, silently); see [CHANGELOG.md](CHANGELOG.md).

Upgrading from `0.0.x`? The `0.1.x` line carries five breaking changes and three new migrations —
[CHANGELOG.md](CHANGELOG.md) has the steps.

**0.1.4 and earlier ship `application-dev.yaml` inside the jar**, with fixed `123456` login codes:
an application running with the `dev` profile and no `application-dev.yaml` of its own accepts them.
Upgrade to `0.1.7`.

**0.1.5 and earlier skip CSRF for requests authenticated by the `access_token` cookie**, and apply
the admin recent-login rule only in the starter's own controller — fixed in `0.1.6`.

Upgrading to `0.1.6`? 0.1.4–0.1.6 are security releases with breaking changes — mail send and
admin actions are stricter, email/phone changes need the current password or a re-authentication
code, Swagger and actuator are no longer public, the jar no longer carries `application*.yaml`, and
migrations V14–V16 run — see [CHANGELOG.md](CHANGELOG.md).

## Usage in Another Project

The starter is published to **Maven Central** — no extra repository or credentials needed.

### 1. Add the dependency

```xml
<dependency>
  <groupId>kz.innlab</groupId>
  <artifactId>auth-spring-boot-starter</artifactId>
  <version>0.1.7</version>
</dependency>
```

Gradle: `implementation("kz.innlab:auth-spring-boot-starter:0.1.7")`.

### Alternative: Build from Source

```bash
git clone https://github.com/bekzatsk/spring-boot-template.git auth-starter
cd auth-starter
./mvnw clean install -DskipTests
```

This installs `kz.innlab:auth-spring-boot-starter:0.1.7` into `~/.m2/repository`.

### 2. Configure `application.yaml`

Minimal configuration for dev:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    username: postgres
    password: postgres
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

The starter auto-configures everything else. It runs its own Flyway migrations into the `auth`
schema (`classpath:db/migration-auth`), separate from your application's migrations.

**The starter ships no `application*.yaml`** (since 0.1.5): every setting comes from your
application, and anything you do not set uses the defaults in the property tables below. Without a
keystore, RSA keys are generated in-memory, and console fallbacks log that SMS/email/push would
have been sent (never the code). For a fixed code in local development, set it in your own
`application-dev.yaml`:

```yaml
app:
  auth:
    sms:
      dev-code: "123456"
    email-otp:
      dev-code: "123456"
    verification:
      dev-code: "123456"
```

Never set `dev-code` outside development — `ProductionSafetyConfig` refuses to start a production
profile with one.

**Behind a reverse proxy**, set `server.forward-headers-strategy: framework` (or `native`) so the
client address is the real one; the per-client code-send limit keys on it.

### 3. Run

```bash
# Start PostgreSQL
docker compose up -d

# Run your app
./mvnw spring-boot:run
```

Flyway migrations run automatically and create all required tables in the `auth` schema.

---

## What You Get

The starter auto-registers these endpoints:

### Auth — Public (`/api/v1/auth`)

| Endpoint | Description |
|----------|-------------|
| `POST /api/v1/auth/local/register` | Register with email + password |
| `GET /api/v1/auth/csrf` | Issue CSRF token for cookie-authenticated writes |
| `POST /api/v1/auth/local/login` | Login with email + password |
| `POST /api/v1/auth/google` | Google ID token auth |
| `POST /api/v1/auth/apple` | Apple ID token auth |
| `POST /api/v1/auth/email/request` | Request passwordless email OTP |
| `POST /api/v1/auth/email/verify` | Verify email OTP |
| `POST /api/v1/auth/phone/request` | Request SMS OTP |
| `POST /api/v1/auth/phone/verify` | Verify SMS OTP |
| `POST /api/v1/auth/telegram/init` | Init Telegram auth session → returns `sessionId`, `botUrl`, `botUsername`, `expiresAt` |
| `POST /api/v1/auth/telegram/verify` | Verify Telegram code |
| `POST /api/v1/auth/telegram/resend` | Resend Telegram code |
| `GET /api/v1/auth/telegram/status/{sessionId}` | Poll Telegram session status |
| `POST /api/v1/auth/refresh` | Refresh access token (body or refresh cookie) |
| `POST /api/v1/auth/revoke` | Revoke refresh token (body or refresh cookie) |
| `POST /api/v1/auth/logout` | Revoke refresh cookie + clear auth cookies (cookie mode) |
| `POST /api/v1/auth/forgot-password` | Request password reset code. Always returns a `verificationId`, whether or not the account exists |
| `POST /api/v1/auth/reset-password` | Reset password with code |
| `POST /api/v1/auth/verify-email` | Verify email with code. Not idempotent: a repeat call after success answers `401` |
| `POST /api/v1/auth/verify-email/resend` | Resend email-verification code. Always returns a `verificationId` |

### Account Management — Authenticated (`/api/v1/users/me`)

| Endpoint | Description |
|----------|-------------|
| `POST /users/me/change-password` | Change password (`currentPassword`, rate-limited) |
| `POST /users/me/reauth/request` | Send a re-authentication code to the current email (or phone) → `{verificationId, channel}` |
| `POST /users/me/change-email/request` | Request email change. Needs `currentPassword`, or `reauthVerificationId` + `reauthCode` |
| `POST /users/me/change-email/verify` | Verify email change; ends every session |
| `POST /users/me/change-phone/request` | Request phone change. Same proof as change-email |
| `POST /users/me/change-phone/verify` | Verify phone change; ends every session |

Email and phone changes are refused (`403`) while the account's email is unverified. Accounts with
no password, email or phone (Telegram-only) may add one within 5 minutes of logging in.

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
| `POST /notifications/send/topic` | Send push to topic — **ADMIN** |
| `POST /notifications/topics/{name}/subscribe` | Subscribe own token to topic |
| `DELETE /notifications/topics/{name}/subscribe` | Unsubscribe own token |
| `GET` / `PUT /notifications/preferences` | Channel preferences |
| `GET /notifications/history` | Own notification history |

Push sends accept only tokens registered to the caller; `body` up to 2,000 characters, `data` up to
20 entries (keys ≤ 64, values ≤ 1,024 characters).

### Email (`/api/v1/mail`)

| Endpoint | Description |
|----------|-------------|
| `POST /mail/send` | Send email (one recipient, subject ≤ 255) — **ADMIN** |
| `POST /mail/send/with-attachments` | Send email with attachments — **ADMIN** |
| `GET /mail/inbox` | List shared inbox messages (IMAP) — **ADMIN** |
| `GET /mail/inbox/{messageNumber}` | Get single message; HTML is sanitized (no scripts, handlers, `javascript:` links or images) — **ADMIN** |
| `PUT` / `DELETE /mail/inbox/{messageNumber}/read` | Mark read / unread — **ADMIN** |
| `GET /mail/history` | Own mail history |

### Admin (`/api/v1/admin`) — ADMIN role

| Endpoint | Description |
|----------|-------------|
| `GET /admin/users` | List users (search `q`; sort by `email`, `name`, `phone`, `createdAt`; ≤ 100 per page) |
| `GET /admin/users/{id}` | Get a user |
| `POST /admin/users` | Create a user ⏱ |
| `PATCH /admin/users/{id}/password` | Reset password; revokes sessions ⏱ |
| `PATCH /admin/users/{id}/email` | Change email; revokes sessions ⏱ |
| `PATCH /admin/users/{id}/phone` | Change phone ⏱ |
| `PATCH /admin/users/{id}/profile` | Name (≤ 255) and picture (http(s) URL) |
| `PATCH /admin/users/{id}/roles` | Change roles; revokes sessions; refuses to remove the last admin or self-demotion ⏱ |
| `DELETE /admin/users/{id}` | Delete; refuses the last admin and self-delete ⏱ |
| `POST /admin/topics`, `DELETE /admin/topics/{name}` | Manage push topics |

⏱ Needs a recent login: the token's `auth_time` within `app.auth.security.admin-fresh-login-seconds`
(default 900), otherwise `403 "Log in again to make this change"`. The rule is enforced in
`AdminUserService` / `UserService.createUserByAdmin` themselves, so it also applies when your own
endpoints call those services. For your own access-granting flows (e.g. invitations), inject
`FreshLoginGuard` and call `requireFreshLogin()`. Admin endpoints are protected
both by URL rules and by `@PreAuthorize`, so they stay closed if you disable or replace the
starter's filter chain. Admin changes to users are recorded in `admin_audit_log`.

All auth endpoints return:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "KXKvgX..."
}
```

Swagger UI is at `/swagger-ui.html` and the spec at `/v3/api-docs`. **Both require authentication**
unless `app.auth.security.public-api-docs=true` (the spec maps every endpoint; keep it private in
production). Title defaults to your `spring.application.name` (not the starter's name). Override via `app.openapi.title`, `app.openapi.version`, `app.openapi.description`:

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

For full control, register your own `@Bean OpenAPI` — the starter uses `@ConditionalOnMissingBean(OpenAPI::class)`.

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

The starter configures Spring Security with JWT. Your endpoints are secured by default. To allow
public access to specific paths, list them in `app.auth.security.public-paths` — admin rules are
evaluated first, so an entry such as `/api/**` cannot open the admin endpoints. For anything more
elaborate, define your own `SecurityFilterChain` bean:

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

Defaults are the code defaults — the starter ships no configuration file. Every property can also
be set through its standard environment variable (`app.auth.local.enabled` →
`APP_AUTH_LOCAL_ENABLED`).

| Property | Default | Description |
|----------|---------|-------------|
| `app.auth.local.enabled` | `true` | Email + password auth |
| `app.auth.registration.enabled` | `true` | Self-registration (admins can always create users) |
| `app.auth.google.enabled` | `false` | Google sign-in |
| `app.auth.google.client-id` | — | Google OAuth2 client ID |
| `app.auth.apple.enabled` | `false` | Apple Sign In |
| `app.auth.apple.bundle-id` | — | Apple app bundle ID |
| `app.auth.phone.enabled` | `true` | Phone + SMS OTP |
| `app.auth.phone.code-length` | `6` | Phone OTP length: `4` or `6` |
| `app.auth.email-otp.enabled` | `true` | Passwordless email OTP |
| `app.auth.email-otp.code-length` | `6` | Email OTP length: `4` or `6` |
| `app.auth.telegram.enabled` | `false` | Telegram bot authentication |
| `app.auth.telegram.bot-token` | — | Telegram Bot API token (required in production when Telegram is enabled) |
| `app.auth.telegram.bot-username` | — | Bot username for the deep link; also in `TelegramInitResponse.botUsername` (leading `@` stripped) |
| `app.auth.telegram.webhook-secret` | — | Secret token for webhook validation (required when Telegram is enabled) |
| `app.auth.access-token.expiry-minutes` | `15` | JWT access token TTL |
| `app.auth.refresh-token.expiry-days` | `30` | Refresh token TTL |
| `app.auth.email-verification.enabled` | `false` | Gate new LOCAL registrations behind email confirmation (see below) |

### Security and Limits

| Property | Default | Description |
|----------|---------|-------------|
| `app.auth.security.public-paths` | — | Extra paths that skip authentication. Admin rules are evaluated first, so these cannot open admin endpoints. |
| `app.auth.security.public-api-docs` | `false` | Serve Swagger UI and `/v3/api-docs` without authentication |
| `app.auth.security.admin-fresh-login-seconds` | `900` | How recent an admin's login must be for account-handover admin actions |
| `app.security.production-profiles` | `prod,production` | Profiles under which `ProductionSafetyConfig` and the keystore check run |
| `app.auth.rate-limit.enabled` | `true` | Master switch for the limits below (`false` only if a gateway enforces them) |
| `app.auth.rate-limit.login.*` | `10` / `300` s | Login attempts per email |
| `app.auth.rate-limit.change-password.*` | `5` / `300` s | Current-password checks per user (also used by re-authentication) |
| `app.auth.rate-limit.otp-verify.*` | `10` / `86400` s | One-time-code checks per identifier and purpose, across codes |
| `app.auth.rate-limit.code-send-per-client.*` | `20` / `3600` s | Codes sent per client address (IPv6 per /64) — stops SMS toll fraud |
| `app.auth.rate-limit.code-send-per-purpose.*` | `1000` / `3600` s | Codes sent per purpose, all clients — a circuit breaker; size it to your traffic |

Each `*` rule has `max-attempts` and `window-seconds`. Limits answer `429` with `Retry-After`. The
default limiter is in-memory, so limits are per instance; declare a `RateLimiter` bean backed by a
shared store for a cluster.

Under a production profile the application refuses to start with a `dev-code` set, the default JWT
issuer/audience, Telegram without a bot token or webhook secret, auth cookies without `Secure`, or
console SMS/email/mail fallbacks serving traffic (waivable per channel with
`app.security.console-fallbacks.allow-sms|allow-email|allow-mail`).

### Email Verification (`app.auth.email-verification`)

Every password registration starts with `emailVerified=false`, whatever this setting says. The
setting decides whether the account is **gated** until the address is confirmed. When
`enabled=true`:

1. `POST /local/register` creates the user, adds required action `VERIFY_EMAIL` and sends a code via
   `EmailService.sendCode(email, code, "VERIFY_EMAIL")`. Tokens are issued, but their JWT carries
   `required_actions: ["VERIFY_EMAIL"]`, so `RequiredActionFilter` answers `403` on any
   non-allowlisted endpoint.
2. `POST /api/v1/auth/verify-email` `{email, verificationId, code}` → `emailVerified=true`, action
   cleared. After refresh or re-login the JWT is clean.
3. `POST /api/v1/auth/verify-email/resend` `{email}` → new code. Always returns a `verificationId`
   (a random one when nothing is sent), so the response does not reveal whether the account exists.

Whether gated or not, an unverified account cannot change its email or phone. If someone else later
signs in to that address with an email code, the account is handed to them: the registrant's
password, phone, sessions and push devices are removed. Registration rejects an existing email,
including one created through a social provider. `emailVerified` is exposed on
`GET /api/v1/users/me`.

### httpOnly Cookie Auth (`app.auth.cookie`)

Optional mode — access + refresh tokens delivered as `httpOnly`+`Secure`+`SameSite` cookies. **Disabled by default.** When on, every auth endpoint (`login`, `register`, `google`, `apple`, `phone/verify`, `telegram/verify`, `refresh`) also sets cookies; the backend reads the access token from the cookie automatically (the `Authorization` header always wins for bearer/API-key clients). Set `suppress-body-tokens=true` if browser JavaScript must never receive token values in JSON responses.

| Property | Default | Description |
|----------|---------|-------------|
| `app.auth.cookie.enabled` | `false` | Master switch. `false` = classic body-token behavior, no `Set-Cookie`. |
| `app.auth.cookie.secure` | `true` | `Secure` attribute (HTTPS-only). |
| `app.auth.cookie.same-site` | `Strict` | `Strict` \| `Lax` \| `None`. `Strict`/`Lax` is the primary CSRF defense for same-origin SPAs. |
| `app.auth.cookie.domain` | `""` | Cookie domain. Empty = host-only. |
| `app.auth.cookie.path` | `/` | Cookie path. |
| `app.auth.cookie.access-cookie-name` | `access_token` | Access-token cookie name. |
| `app.auth.cookie.refresh-cookie-name` | `refresh_token` | Refresh-token cookie name. |
| `app.auth.cookie.access-max-age-seconds` | (= access JWT TTL) | Access cookie `Max-Age`. Negative = derive from `access-token.expiry-minutes`. |
| `app.auth.cookie.refresh-max-age-days` | `30` | Refresh cookie `Max-Age` in days. |
| `app.auth.cookie.suppress-body-tokens` | `false` | When `true`, omit access/refresh from the JSON body (only `requiredActions` remains) — cookies become the sole transport. |

**Refresh / revoke / logout:** `POST /refresh` and `/revoke` accept the refresh token from the body *or* the refresh cookie (body wins). `POST /logout` revokes the refresh cookie and clears both cookies (`Max-Age=0`); idempotent (no cookie → `204`).

**CSRF:** in cookie mode, every state-changing request that carries an auth cookie needs a CSRF
token — on your own endpoints as well as the starter's. Call `GET /api/v1/auth/csrf` to receive a token and an `XSRF-TOKEN` cookie. Send the returned token in the `X-XSRF-TOKEN` header for requests that carry access or refresh cookies and change state, including refresh and logout. The CSRF cookie must accompany the request. Bearer-only requests without auth cookies continue to work without a CSRF token. From `0.1.7` the token stays valid across requests, so fetch it once and reuse it for every write; earlier releases cleared `XSRF-TOKEN` in each cookie-authenticated response. `SameSite` adds defense but does not replace this check.

```yaml
app:
  auth:
    cookie:
      enabled: true
      same-site: Strict     # Strict for same-origin SPA
      secure: true          # HTTPS only
      # suppress-body-tokens: true   # optional: cookies become the only transport
```

### Database

Standard Spring Boot settings: `spring.datasource.url`, `spring.datasource.username`,
`spring.datasource.password` (env `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`). Set `spring.jpa.hibernate.ddl-auto: validate` in production; the
schema comes from the starter's migrations.

### JWT (Production)

| Property (env var) | Required | Description |
|--------------------|----------|-------------|
| `app.security.jwt.keystore-location` (`APP_SECURITY_JWT_KEYSTORE_LOCATION`) | Prod | `file:/absolute/path/to/keystore.p12` or a classpath resource |
| `app.security.jwt.keystore-password` (`APP_SECURITY_JWT_KEYSTORE_PASSWORD`) | Prod | Keystore password |
| `app.security.jwt.key-alias` (`APP_SECURITY_JWT_KEY_ALIAS`) | No (`jwt`) | Key alias |
| `app.security.jwt.issuer` (`APP_SECURITY_JWT_ISSUER`) | Prod | Issuer, unique to this environment. The default `template-app` is refused in production. |
| `app.security.jwt.audience` (`APP_SECURITY_JWT_AUDIENCE`) | Prod | The API accepting the token. The default `template-app` is refused in production. |

Shorter names such as `JWT_KEYSTORE_LOCATION` work only if your `application-prod.yaml` maps them
(`keystore-location: ${JWT_KEYSTORE_LOCATION}`); the starter no longer ships a file that does.

Generate a keystore:

```bash
# Writes secrets/jwt-keystore.p12 with a generated password and prints the settings:
./scripts/generate-keystore.sh
```

Never put the keystore under `src/main/resources`: it would be packaged into your jar. Without a
keystore, outside production profiles, RSA keys are generated in-memory and change on every restart.

### Firebase

| Setting | Required | Description |
|---------|----------|-------------|
| `app.firebase.enabled` | No (`false`) | Enable Firebase push |
| `FIREBASE_CREDENTIALS_JSON` (env var) | When enabled | Service-account JSON, **base64-encoded** |

### Email (SMTP/IMAP)

| Property | Default | Description |
|----------|---------|-------------|
| `app.mail.enabled` | `false` | Enable mail service |
| `app.mail.smtp.host` / `port` | — / `587` | SMTP server |
| `app.mail.smtp.username` / `password` | — | SMTP credentials |
| `app.mail.smtp.from` | `noreply@example.com` | Sender address |
| `app.mail.smtp.ssl-enabled` | `false` | SSL for SMTP |
| `app.mail.imap.host` / `port` | — / `993` | IMAP server (shared admin inbox) |
| `app.mail.imap.username` / `password` | — | IMAP credentials |
| `app.mail.imap.ssl-enabled` | `true` | SSL for IMAP |
| `app.mail.external.base-url` / `master-key` | — | Use an external mail HTTP service instead of SMTP |
| `app.mail.limits.per-user-per-hour` | `20` | Send quota per sender |
| `app.mail.limits.max-attachments` | `10` | Attachments per message |
| `app.mail.limits.max-attachment-bytes` | `5 MB` | Per attachment |
| `app.mail.limits.max-total-attachment-bytes` | `20 MB` | Per message |

### Other

| Property | Default | Description |
|----------|---------|-------------|
| `app.cors.allowed-origins` | — (none) | Allowed CORS origins; required in production |
| `app.notification.token.max-per-user` | `5` | Max device tokens per user |
| `app.auth.telegram.trust-forwarded-headers` | `false` | Honour `X-Forwarded-For` for Telegram session limits — only behind a trusted proxy |

---

## Database Schema

The starter's Flyway migrations (`classpath:db/migration-auth`, V1 through V16) create these tables
in the `auth` schema:

```
users                    — id, email, name, picture, password_hash, phone, email_verified, telegram ids, version
user_providers           — user_id, provider (GOOGLE|APPLE|LOCAL|TELEGRAM)
user_provider_ids        — user_id, provider, provider_id (unique per provider)
user_roles               — user_id, role (USER|ADMIN)
user_required_actions    — user_id, action (VERIFY_EMAIL|UPDATE_PASSWORD|VERIFY_PHONE)
refresh_tokens           — id, user_id, token_hash, expires_at, revoked, used_at, authenticated_at
verification_codes       — id, user_id, identifier, purpose, code_hash, expires_at, attempts
telegram_auth_sessions   — Telegram login sessions
device_tokens            — id, user_id, platform, fcm_token (unique), device_id
notification_history     — sent notifications
notification_topics      — push topics
notification_preferences — per-user channel preferences
mail_history             — sent mail
admin_audit_log          — admin changes to users
```

All primary keys are UUID v7 (time-ordered, RFC 9562). Entity tables carry a `version` column for
optimistic locking.

---

## Token Management

| Token | Format | Expiry | Storage |
|-------|--------|--------|---------|
| Access | RS256 JWT | 15 min (`app.auth.access-token.expiry-minutes`) | Client only |
| Refresh | Opaque (32 bytes, base64url) | 30 days | SHA-256 hash in DB |
| Verification | 4- or 6-digit code | 15 min (email) / 5 min (login OTP) | BCrypt hash in DB |

**JWT claims:** `iss`, `aud`, `sub` (user UUID), `roles`, `required_actions`, `auth_time`, `exp`,
`iat`. The resource server validates `iss` and `aud` against `app.security.jwt.issuer` and
`app.security.jwt.audience`; configure distinct values per environment and API. `auth_time` is the
time of the login the token descends from — refresh keeps it — and gates actions that need a recent
login.

**Refresh token rotation:** used tokens within a 10 s grace window answer `409`; reuse after the
grace window revokes all of the user's tokens. Password, email, phone and role changes also revoke
them.

**One-time codes:** 3 attempts per code, plus a budget per identifier and purpose across codes
(`app.auth.rate-limit.otp-verify`), so requesting new codes does not reset the guessing count. Email
identifiers are compared without letter case.

---

## Account Linking

Google and Apple returning users are identified by their provider subject. A new provider identity whose email matches an existing account receives 409; email matching alone never links credentials. Local registration also returns 409 for an existing email. Implement an explicit owner-verified linking flow if the product needs multi-provider accounts. Existing linked accounts remain usable by their saved provider IDs.

Phone-only users have `email = ""` with a partial unique index.

---

## Dev vs Prod

| Feature | Without production config | Production profile |
|---------|---------------------------|--------------------|
| RSA keys | In-memory (regenerated on restart) | PKCS12 keystore (required) |
| Verification codes | Random, unless your `application-dev.yaml` sets `dev-code` | Random; a `dev-code` is refused at startup |
| SMS / email / push | Console fallback (logs that a message would be sent, never its content) | Real providers; console fallbacks refused unless waived per channel |
| JWT issuer/audience | `template-app` | Must be set; the default is refused |

Production profiles are `prod` and `production` by default (`app.security.production-profiles`).

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
        <version>4.1.1</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>myapp</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>25</java.version>
        <kotlin.version>2.3.21</kotlin.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>kz.innlab</groupId>
            <artifactId>auth-spring-boot-starter</artifactId>
            <version>0.1.7</version>
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

All errors from the starter's endpoints follow one format:
```json
{ "error": "Bad Request", "message": "email: must be valid", "status": 400 }
```

| Status | When |
|--------|------|
| 400 | Validation failure, malformed path variable or missing parameter |
| 401 | Invalid credentials, expired or wrong token/code |
| 403 | Insufficient role, pending required action, unverified email for identity changes, admin login not recent enough |
| 404 | Not found, or a disabled provider's endpoint |
| 409 | Email/phone taken, resend cooldown, refresh grace window, concurrent update |
| 429 | Attempt or send limit reached — honour `Retry-After` |

Messages from exceptions thrown inside libraries are not passed through; the client gets a generic
message and the details stay in the server log.

---

## Publishing (Maintainers)

Releases go to **Maven Central** through the `release` profile (GPG-signed, uploaded to Sonatype).
Full steps and one-time setup: [DEPLOY_MVN.md](DEPLOY_MVN.md).

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw clean deploy -P release -DskipTests
```

The deployment stops at VALIDATED and is published by hand at
https://central.sonatype.com/publishing/deployments. Central is immutable: a published version can
never be replaced, and a migration that shipped must never be edited. Bump the version in
`pom.xml`, this README and `INSTALL_INSTRUCTION.md`, and add a `CHANGELOG.md` entry first.

---

## Changelog

Release notes for 0.1.x are in [CHANGELOG.md](CHANGELOG.md). The entries below cover the 0.0.x
snapshots.

### 0.0.5-SNAPSHOT (unreleased)

**Changed**
- Auto-configuration no longer `@ComponentScan`s the starter package. Every bean is registered
  explicitly with `@ConditionalOnMissingBean`, so a consumer application can now replace any
  starter bean — not just the `SmsService`/`EmailService`/`PushService` interfaces — by declaring
  a `@Bean` of the same type. Bean names are unchanged (they equal the decapitalized class name)
  and are covered by a contract test, so `@Qualifier`/`@Async` references keep resolving.
- Settings moved from scattered `@Value` injections to validated `@ConfigurationProperties`
  (`AuthTokenProperties`, `VerificationProperties`, `TelegramAuthProperties`,
  `DeviceTokenProperties`). Property names and defaults are unchanged.

**Added**
- `app.auth.access-token.expiry-minutes` (default `15`) — JWT access token TTL is now configurable. Env: `ACCESS_TOKEN_EXPIRY_MINUTES`. Set to `1440` for 1 day, `60` for 1 hour.
- `app.openapi.title` / `app.openapi.version` / `app.openapi.description` — Swagger UI metadata is now configurable. Title default is `spring.application.name` (was hardcoded "Spring Boot Auth Template API"). The `OpenAPI` bean uses `@ConditionalOnMissingBean(OpenAPI::class)` so consumers can fully replace it.

### 0.0.4-SNAPSHOT

**Telegram bot**
- Real Telegram Bot API integration (`RealTelegramBotService`) ships in the starter. Auto-selected when `app.auth.telegram.bot-token` is set; otherwise the console mock (`ConsoleTelegramBotService`) is used. Zero-config dev, production-ready when the token is present.

### 0.0.3-SNAPSHOT

**Added**
- `spring-boot-starter-actuator` is now bundled transitively. `/actuator/health` is available out of the box. Consumers no longer need to add the dependency explicitly for docker healthchecks / k8s probes.
  - Only `health` is exposed via web by default (Spring Boot default). Opt in to more endpoints via `management.endpoints.web.exposure.include`.
  - The starter permits `/actuator/health` (and its sub-paths) so container healthchecks work out of the box; every other actuator endpoint requires authentication. It does **not** set an `@Order(1)` chain on `/actuator/**` — consumers still register their own actuator security filter chain when they need broader access.

### 0.0.2-SNAPSHOT

**Telegram auth**
- `TelegramInitResponse` now includes a `botUsername` field (additive, non-breaking) alongside the existing `botUrl`. Value comes from `app.auth.telegram.bot-username` with any leading `@` stripped. Frontends can render `@MyBot` text without parsing the URL.
