# Spring Boot Auth Template

JWT-based authentication template with multi-provider social login, local email+password, phone+SMS OTP, token rotation, account management, push notifications via Firebase Cloud Messaging, and email service with SMTP/IMAP support.

**Stack:** Spring Boot 4.0.3 / Kotlin / Java 25 / PostgreSQL 18 / Flyway / Firebase Admin SDK

## Quick Start

```bash
# Start PostgreSQL
docker compose up -d

# Run (dev profile — in-memory RSA keys, console SMS/email)
./mvnw spring-boot:run

# Tests (H2 in-memory, no Docker needed)
./mvnw test
```

Server: http://localhost:7070

## Authentication Providers

| Provider | Endpoint | How it works |
|----------|----------|--------------|
| Google | `POST /api/v1/auth/google` | Verify Google ID token, create/link user |
| Apple | `POST /api/v1/auth/apple` | Verify Apple ID token via JWKS |
| Email+Password | `POST /api/v1/auth/local/register` | Register with bcrypt-hashed password |
| Email+Password | `POST /api/v1/auth/local/login` | Authenticate existing user |
| Phone+SMS OTP | `POST /api/v1/auth/phone/request` | Send 6-digit code via SMS |
| Phone+SMS OTP | `POST /api/v1/auth/phone/verify` | Verify code, issue tokens |

All auth endpoints return:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "KXKvgX..."
}
```

## API Reference

### Auth — Public (`/api/v1/auth`)

#### `POST /auth/google`
```json
{ "idToken": "string", "name": "string?", "picture": "string?" }
```
**200 OK** — AuthResponse

#### `POST /auth/apple`
```json
{ "idToken": "string", "givenName": "string?", "familyName": "string?" }
```
**200 OK** — AuthResponse. Name fields only available on first sign-in.

#### `POST /auth/local/register`
```json
{ "email": "user@example.com", "password": "min8chars", "name": "string?" }
```
**201 Created** — AuthResponse | **409** email already registered

#### `POST /auth/local/login`
```json
{ "email": "user@example.com", "password": "string" }
```
**200 OK** — AuthResponse | **401** invalid credentials

#### `POST /auth/phone/request`
```json
{ "phone": "+77001234567" }
```
**200 OK** — `{ "verificationId": "uuid" }` | **409** rate limit (1/phone/60s)

Phone is normalized to E.164. Dev profile logs code `123456` to console.

#### `POST /auth/phone/verify`
```json
{ "verificationId": "uuid", "phone": "+77001234567", "code": "123456" }
```
**200 OK** — AuthResponse | **401** invalid/expired code (max 3 attempts)

#### `POST /auth/refresh`
```json
{ "refreshToken": "string" }
```
**200 OK** — new AuthResponse (token rotation) | **409** grace window (retry with new token) | **401** expired/reuse detected

#### `POST /auth/revoke`
```json
{ "refreshToken": "string" }
```
**204 No Content** — idempotent logout

#### `POST /auth/forgot-password`
```json
{ "email": "user@example.com" }
```
**202 Accepted** — `{ "verificationId": "uuid | null" }`. Always 202 regardless of email existence (anti-enumeration).

#### `POST /auth/reset-password`
```json
{ "verificationId": "uuid", "email": "string", "code": "string", "newPassword": "string" }
```
**200 OK** — password updated, all refresh tokens revoked | **401** invalid code

### Account Management — Authenticated (`/api/v1/users/me`)

All endpoints require `Authorization: Bearer {accessToken}`.

#### `POST /users/me/change-password`
```json
{ "currentPassword": "string", "newPassword": "string" }
```
**200 OK** — all refresh tokens revoked | **401** wrong current password

#### `POST /users/me/change-email/request`
```json
{ "newEmail": "new@example.com" }
```
**200 OK** — `{ "verificationId": "uuid" }` | **409** email taken

#### `POST /users/me/change-email/verify`
```json
{ "verificationId": "uuid", "code": "string" }
```
**200 OK** — email updated | **409** email taken (race condition re-check)

#### `POST /users/me/change-phone/request`
```json
{ "phone": "+77009876543" }
```
**200 OK** — `{ "verificationId": "uuid" }` | **409** phone taken

#### `POST /users/me/change-phone/verify`
```json
{ "verificationId": "uuid", "phone": "+77009876543", "code": "string" }
```
**200 OK** — phone updated | **409** phone taken (race condition re-check)

### User Profile — Authenticated (`/api/v1/users`)

#### `GET /users/me`
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "name": "John Doe",
  "picture": "https://...",
  "providers": ["GOOGLE", "LOCAL"],
  "roles": ["USER"],
  "createdAt": "2026-03-03T04:00:00Z"
}
```

### Push Notifications — Authenticated (`/api/v1/notifications`)

Firebase Cloud Messaging integration for sending push notifications to mobile apps (Android/iOS) and web browsers.

#### `POST /notifications/push/token`
Register or update a device token for the authenticated user.
```json
{ "token": "fcm-device-token", "deviceType": "ANDROID | IOS | WEB" }
```
**200 OK** — token stored | **400** invalid token

#### `DELETE /notifications/push/token`
Remove a device token (e.g., on logout).
```json
{ "token": "fcm-device-token" }
```
**204 No Content**

#### `POST /notifications/push/subscribe`
Subscribe a device token to a topic.
```json
{ "token": "fcm-device-token", "topic": "news" }
```
**200 OK** — subscribed

#### `POST /notifications/push/unsubscribe`
Unsubscribe a device token from a topic.
```json
{ "token": "fcm-device-token", "topic": "news" }
```
**200 OK** — unsubscribed

#### `POST /notifications/push/send`
Send a push notification to a single device, multiple devices, or a topic.
```json
{
  "target": {
    "type": "TOKEN | TOKENS | TOPIC",
    "value": "fcm-token | [tokens] | topic-name"
  },
  "notification": {
    "title": "New message",
    "body": "You have a new notification"
  },
  "data": {
    "orderId": "12345",
    "action": "OPEN_ORDER"
  }
}
```
**200 OK** — `{ "successCount": 1, "failureCount": 0 }` | **400** invalid payload

**Notification payload:**
- `title` — notification title (displayed in system tray)
- `body` — notification body text
- `data` — custom key-value pairs (JSON object, delivered to app even in background)

**Target types:**
- `TOKEN` — single device token
- `TOKENS` — array of device tokens (max 500 per request)
- `TOPIC` — all devices subscribed to a topic

### Email — Authenticated (`/api/v1/notifications`)

Email service with SMTP sending and IMAP/POP3 receiving.

#### `POST /notifications/email/send`
Send an email.
```json
{
  "to": ["recipient@example.com"],
  "cc": ["cc@example.com"],
  "bcc": [],
  "subject": "Hello",
  "body": "<h1>Hello</h1><p>World</p>",
  "contentType": "HTML | TEXT",
  "attachments": [
    {
      "filename": "report.pdf",
      "content": "base64-encoded-content",
      "contentType": "application/pdf"
    }
  ]
}
```
**202 Accepted** — `{ "messageId": "uuid" }` | **400** invalid payload

Supports plain text, HTML, and file attachments. Failed sends are retried automatically (configurable retry count and delay).

#### `GET /notifications/email/inbox`
Fetch emails from the configured mailbox via IMAP/POP3.
```json
// Query params: ?folder=INBOX&unreadOnly=true&limit=20&offset=0
```
**200 OK**
```json
{
  "emails": [
    {
      "messageId": "string",
      "from": "sender@example.com",
      "to": ["recipient@example.com"],
      "subject": "Hello",
      "body": "...",
      "receivedAt": "2026-03-03T10:00:00Z",
      "read": false,
      "attachments": [
        { "filename": "file.pdf", "contentType": "application/pdf", "size": 1024 }
      ]
    }
  ],
  "total": 42,
  "unread": 5
}
```

#### `GET /notifications/email/{messageId}`
Fetch a single email with full body and attachments.

**200 OK** — full email object | **404** message not found

#### `PATCH /notifications/email/{messageId}`
Mark email as read/unread.
```json
{ "read": true }
```
**200 OK** — updated

## Token Management

| Token | Format | Expiry | Storage |
|-------|--------|--------|---------|
| Access | RS256 JWT | 15 min | Client only |
| Refresh | Opaque (32 bytes, base64url) | 30 days | SHA-256 hash in DB |
| Verification | 6-digit code | 15 min (email) / 5 min (SMS) | BCrypt hash in DB |

**JWT claims:** `iss` (template-app), `sub` (user UUID), `roles` (["USER"]), `exp`, `iat`

### Refresh Token Rotation

```
Client sends refresh token
  -> Hash and look up in DB
  -> If valid: issue new pair, mark old as used
  -> If used within 10s (grace window): 409 — client should use new token
  -> If used after 10s: reuse attack — revoke ALL tokens for user
```

## Account Linking

One email = one user across all providers. Email is the universal identity key.

- Google user signs in with email X -> user created with GOOGLE provider
- Same user does local register with email X -> LOCAL provider **linked** to same account
- Apple sign-in with email X -> APPLE provider **linked** to same account
- `GET /users/me` shows `"providers": ["GOOGLE", "LOCAL", "APPLE"]`

Phone-only users have `email = ""` (NOT NULL constraint) and are keyed on `(LOCAL, phoneE164)`.

## Database Schema

Tables managed by Flyway:

```
users                   — id, email, name, picture, password_hash, phone
user_providers          — user_id, provider (GOOGLE|APPLE|LOCAL)
user_provider_ids       — user_id, provider, provider_id (Google sub, Apple sub)
user_roles              — user_id, role (USER|ADMIN)
refresh_tokens          — id, user_id, token_hash, expires_at, revoked, used_at
sms_verifications       — id, phone, code_hash, expires_at, used, attempts
verification_codes      — id, user_id, identifier, purpose, code_hash, new_value, expires_at
device_tokens           — id, user_id, token, device_type, created_at, updated_at
```

All primary keys are UUID v7 (time-ordered, RFC 9562) via `uuid-creator`.

Partial unique index: `CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email != ''` — allows multiple phone-only users with empty email.

## Project Structure

```
src/main/kotlin/kz/innlab/template/
├── authentication/
│   ├── controller/         AuthController, AccountManagementController
│   ├── dto/                Request/response data classes
│   ├── exception/          AuthExceptionHandler, TokenGracePeriodException
│   ├── filter/             ApiAuthenticationEntryPoint, ApiAccessDeniedHandler
│   ├── model/              RefreshToken, SmsVerification, VerificationCode
│   ├── repository/         JPA repositories
│   └── service/            TokenService, RefreshTokenService, LocalAuthService,
│                           GoogleOAuth2Service, AppleOAuth2Service, PhoneOtpService,
│                           AccountManagementService, VerificationCodeService,
│                           SmsVerificationService, EmailService, SmsService
├── notification/
│   ├── controller/         NotificationController
│   ├── dto/                Push/email request/response data classes
│   ├── model/              DeviceToken, DeviceType
│   ├── repository/         DeviceTokenRepository
│   └── service/            FirebaseMessagingService, EmailSenderService,
│                           EmailReceiverService, DeviceTokenService
├── user/
│   ├── controller/         UserController
│   ├── dto/                UserProfileResponse
│   ├── model/              User, AuthProvider, Role
│   ├── repository/         UserRepository
│   └── service/            UserService
├── config/                 SecurityConfig, RsaKeyConfig, CorsProperties,
│                           GoogleAuthConfig, AppleAuthConfig, LocalAuthConfig,
│                           SmsSchedulerConfig, FirebaseConfig, EmailConfig
├── shared/
│   ├── error/              ErrorResponse
│   └── model/              BaseEntity (UUID v7 + Persistable)
└── TemplateApplication.kt
```

## Rename Project

Use the included script to rename the package, application class, and all configs:

```bash
./rename-project.sh <new_package> <new_project_name>

# Example:
./rename-project.sh com.innlab.cakeup cakeup
```

This will:
- Move `src/main/kotlin/kz/innlab/template/` -> `src/main/kotlin/com/innlab/cakeup/`
- Update all `package` and `import` declarations in Kotlin files
- Rename `TemplateApplication` -> `CakeupApplication`
- Update `pom.xml` (groupId, artifactId, name)
- Update `application.yaml`, `.env`, `docker-compose.yml`
- Clean the `target/` directory

After renaming:
```bash
# Create the new database
createdb cakeup

# Verify build
./mvnw clean compile

# Run tests
./mvnw test
```

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | No | `dev` | Active profile |
| `DATABASE_URL` | Prod | — | JDBC PostgreSQL URL |
| `DB_USERNAME` | No | `postgres` (dev) | Database user |
| `DB_PASSWORD` | No | `postgres` (dev) | Database password |
| `GOOGLE_CLIENT_ID` | Prod | placeholder | Google OAuth2 client ID |
| `APPLE_BUNDLE_ID` | Prod | placeholder | Apple app bundle ID |
| `JWT_KEYSTORE_LOCATION` | Prod | — | PKCS12 keystore path |
| `JWT_KEYSTORE_PASSWORD` | Prod | — | Keystore password |
| `JWT_KEY_ALIAS` | No | `jwt` | Key alias in keystore |
| `REFRESH_TOKEN_EXPIRY_DAYS` | No | `30` | Refresh token TTL |
| `APP_CORS_ALLOWED_ORIGINS` | Prod | localhost:3000,5173,8082 | Allowed CORS origins |
| `FIREBASE_CREDENTIALS_PATH` | Prod | — | Path to Firebase service account JSON |
| `SMTP_HOST` | Prod | — | SMTP server host |
| `SMTP_PORT` | No | `587` | SMTP server port |
| `SMTP_USERNAME` | Prod | — | SMTP username |
| `SMTP_PASSWORD` | Prod | — | SMTP password |
| `SMTP_SSL_ENABLED` | No | `true` | Enable SSL/TLS for SMTP |
| `IMAP_HOST` | Prod | — | IMAP/POP3 server host |
| `IMAP_PORT` | No | `993` | IMAP server port |
| `IMAP_USERNAME` | Prod | — | IMAP username |
| `IMAP_PASSWORD` | Prod | — | IMAP password |
| `IMAP_PROTOCOL` | No | `imaps` | Protocol: `imaps` or `pop3s` |
| `EMAIL_FROM` | Prod | — | Default sender email address |
| `EMAIL_RETRY_MAX_ATTEMPTS` | No | `3` | Max retry attempts for failed sends |
| `EMAIL_RETRY_DELAY_MS` | No | `5000` | Delay between retries (ms) |

### Firebase Setup

1. Go to [Firebase Console](https://console.firebase.google.com/) → Project Settings → Service Accounts
2. Click **Generate new private key** → download JSON file
3. Set `FIREBASE_CREDENTIALS_PATH` to the file path

Dev profile: Firebase is optional — notifications are logged to console if credentials are not configured.

### Email Setup

Configure SMTP for sending and IMAP/POP3 for receiving:

```yaml
# application.yaml (dev profile example)
app:
  email:
    smtp:
      host: smtp.gmail.com
      port: 587
      username: ${SMTP_USERNAME:}
      password: ${SMTP_PASSWORD:}
      ssl-enabled: true
    imap:
      host: imap.gmail.com
      port: 993
      username: ${IMAP_USERNAME:}
      password: ${IMAP_PASSWORD:}
      protocol: imaps
    from: noreply@example.com
    retry:
      max-attempts: 3
      delay-ms: 5000
```

Dev profile: emails are logged to console if SMTP is not configured.

### Dev Profile

- In-memory RSA keypair (regenerated on restart)
- PostgreSQL at `localhost:5432/template`
- Flyway `clean-on-validation-error: true`
- SMS/email codes logged to console (`dev-code: "123456"`)
- Firebase notifications logged to console (no credentials required)
- Email sending logged to console (no SMTP required)

### Prod Profile

- RSA keypair from PKCS12 keystore
- Flyway strict validation (`out-of-order: false`)
- No dev codes — SecureRandom 6-digit codes
- Email/SMS delivery via pluggable services (`@ConditionalOnMissingBean`)
- Firebase Cloud Messaging via service account credentials
- SMTP/IMAP via configured mail server

### Generate JWT Keystore (production)

```bash
keytool -genkey -alias jwt -keyalg RSA -keysize 2048 \
  -keystore jwt.p12 -storetype PKCS12
```

## Extending SMS/Email Services

Both `SmsService` and `EmailService` are interfaces with console-logging defaults.

To plug in a real provider (e.g., Twilio, SendGrid), create a `@Bean` that implements the interface. `@ConditionalOnMissingBean` on the console implementation means your bean takes priority:

```kotlin
@Bean
fun smsService(): SmsService = TwilioSmsService(accountSid, authToken, fromNumber)
```

## Error Responses

All errors follow a consistent format:

```json
{ "error": "Bad Request", "message": "email: must be valid", "status": 400 }
```

| Status | When |
|--------|------|
| 400 | Validation failure, invalid phone format, invalid notification payload |
| 401 | Invalid credentials, invalid/expired token or code |
| 403 | Authenticated but insufficient permissions |
| 404 | User not found, email message not found |
| 409 | Email/phone taken, rate limit, grace window, duplicate registration |
| 500 | Unexpected server error (logged) |

## Tests

37 integration tests across 5 test classes:

| Class | Tests | Coverage |
|-------|-------|----------|
| `LocalAuthIntegrationTest` | 7 | Register, login, validation, duplicate |
| `PhoneAuthIntegrationTest` | 7 | OTP request, verify, rate limit, invalid code |
| `AppleAuthIntegrationTest` | 4 | Token verify, first sign-in, account linking |
| `SecurityIntegrationTest` | 4 | JWT auth, unauthorized, /users/me |
| `AccountManagementIntegrationTest` | 14 | All 4 flows + anti-enumeration + race condition |
| `TemplateApplicationTests` | 1 | Context loads |

Test config: H2 in-memory, Flyway disabled, `create-drop` DDL, `@MockitoBean` for external boundaries (GoogleIdTokenVerifier, AppleJwtDecoder, SmsService, EmailService).

```bash
./mvnw test                    # Run all tests
./mvnw test -pl . -Dtest=AccountManagementIntegrationTest  # Single class
```
