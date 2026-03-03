    # Spring Boot Auth Template

JWT-based authentication template with multi-provider social login, local email+password, phone+SMS OTP, token rotation, and account management.

**Stack:** Spring Boot 4.0.3 / Kotlin 2.2.21 / Java 24 / PostgreSQL 18 / Flyway

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

6 tables managed by Flyway:

```
users                   — id, email, name, picture, password_hash, phone
user_providers          — user_id, provider (GOOGLE|APPLE|LOCAL)
user_provider_ids       — user_id, provider, provider_id (Google sub, Apple sub)
user_roles              — user_id, role (USER|ADMIN)
refresh_tokens          — id, user_id, token_hash, expires_at, revoked, used_at
sms_verifications       — id, phone, code_hash, expires_at, used, attempts
verification_codes      — id, user_id, identifier, purpose, code_hash, new_value, expires_at
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
├── user/
│   ├── controller/         UserController
│   ├── dto/                UserProfileResponse
│   ├── model/              User, AuthProvider, Role
│   ├── repository/         UserRepository
│   └── service/            UserService
├── config/                 SecurityConfig, RsaKeyConfig, CorsProperties,
│                           GoogleAuthConfig, AppleAuthConfig, LocalAuthConfig,
│                           SmsSchedulerConfig
├── shared/
│   ├── error/              ErrorResponse
│   └── model/              BaseEntity (UUID v7 + Persistable)
└── TemplateApplication.kt
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

### Dev Profile

- In-memory RSA keypair (regenerated on restart)
- PostgreSQL at `localhost:5432/template`
- Flyway `clean-on-validation-error: true`
- SMS/email codes logged to console (`dev-code: "123456"`)

### Prod Profile

- RSA keypair from PKCS12 keystore
- Flyway strict validation (`out-of-order: false`)
- No dev codes — SecureRandom 6-digit codes
- Email/SMS delivery via pluggable services (`@ConditionalOnMissingBean`)

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
| 400 | Validation failure, invalid phone format |
| 401 | Invalid credentials, invalid/expired token or code |
| 403 | Authenticated but insufficient permissions |
| 404 | User not found |
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
./mvnw test                    # Run all 37 tests
./mvnw test -pl . -Dtest=AccountManagementIntegrationTest  # Single class
```
