# Phase 1: Add LOCAL Authentication (Email+Password and Phone+SMS OTP) - Research

**Researched:** 2026-03-02
**Domain:** Spring Security 7 local authentication, password hashing, SMS OTP, Flyway migrations
**Confidence:** HIGH (core patterns verified against official Spring Security 7.0.x docs)

---

## Summary

This phase adds two new authentication methods to an existing Spring Boot 4.0.3 / Spring Security 7 template that already has Google and Apple OAuth2 login. The existing architecture keys users on a `(provider, providerId)` composite — a foundational design decision that must be preserved.

**Email+password** is the simpler of the two paths. Spring Security 7's `DaoAuthenticationProvider` + `UserDetailsService` pattern is the correct mechanism. Because the app is a stateless REST API with no form login, authentication is done programmatically: a `POST /api/v1/auth/local/register` and `POST /api/v1/auth/local/login` endpoint call `AuthenticationManager.authenticate()` directly and issue JWT + refresh tokens using the existing `TokenService` + `RefreshTokenService`. Password storage uses `BCryptPasswordEncoder` from `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.

**Phone+SMS OTP** is handled as a two-step stateless flow: (1) `POST /api/v1/auth/phone/request-otp` sends a 6-digit OTP via Twilio Verify API and returns nothing (or a session correlation id if Redis is unavailable); (2) `POST /api/v1/auth/phone/verify-otp` validates the OTP against Twilio's check endpoint and, on success, finds-or-creates the user and issues tokens. No Redis dependency is needed if using Twilio Verify as the OTP store.

**Schema impact** is the largest architectural concern. The `User` entity currently stores `provider` (enum: GOOGLE, APPLE) and `providerId` (opaque string). Adding LOCAL requires: (1) a new `LOCAL` enum value; (2) a nullable `password_hash` column on `users`; (3) a nullable `phone` column on `users` (unique); (4) Flyway migration setup (currently `ddl-auto: create-drop` in dev — production needs Flyway). For LOCAL email users, `providerId` = email; for LOCAL phone users, `providerId` = phone number in E.164 format.

**Primary recommendation:** Use `DaoAuthenticationProvider` + `BCrypt` for email/password; use Twilio Verify API for SMS OTP (no OTP storage on our side); add `LOCAL` enum value to `AuthProvider`; add nullable `passwordHash` and `phone` columns; introduce Flyway migrations.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Security | 7.0.x (via Boot 4.0.3) | `DaoAuthenticationProvider`, `PasswordEncoder`, `AuthenticationManager` | Built-in — already on classpath |
| BCryptPasswordEncoder | Spring Security built-in | Password hashing with salt | OWASP recommended; DelegatingPasswordEncoder default; adaptive cost |
| spring-boot-starter-validation | Boot 4.0.3 (already in pom.xml) | `@Valid`, `@Email`, `@NotBlank`, custom `@PhoneNumber` | Already on classpath |
| spring-boot-starter-flyway | Boot 4.0.3 | Database schema migrations for prod | Boot 4.0 requires starter (breaking change from 3.x) |
| Twilio SDK | 11.x (latest stable: ~11.3.x) | SMS OTP send+verify via Twilio Verify API | Official Twilio Java SDK; abstracted provider; no OTP storage needed |
| google/libphonenumber | 8.13.x | Parse and normalize phone numbers to E.164 | Google-maintained; handles all country codes; required before passing to Twilio |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-mail | Boot 4.0.3 | JavaMailSender for email confirmation | If email verification step is added (optional MVP scope) |
| com.googlecode.libphonenumber | 8.13.x | E.164 normalization + validation | Always for phone input — regex is insufficient for international numbers |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Twilio Verify API | Redis + self-generated OTP + SMS gateway | Redis adds infrastructure dependency; Twilio manages OTP TTL, rate-limiting, and retry policy out of the box |
| BCryptPasswordEncoder | Argon2PasswordEncoder | Argon2 is memory-hard (better); BCrypt is simpler, widely understood, and `DelegatingPasswordEncoder` default. Argon2 is a valid upgrade if the team prefers. |
| libphonenumber | regex pattern | Regex can't handle all country codes correctly; libphonenumber is authoritative |
| Flyway | Liquibase | Flyway is simpler for small teams; both work. Decision: use Flyway (lighter) |

**Installation additions to pom.xml:**
```xml
<!-- Flyway — required in Boot 4.0 (replaces direct flyway-core dependency) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>

<!-- Twilio SDK for SMS OTP -->
<dependency>
    <groupId>com.twilio.sdk</groupId>
    <artifactId>twilio</artifactId>
    <version>11.3.3</version>
</dependency>

<!-- Phone number parsing and E.164 normalization -->
<dependency>
    <groupId>com.googlecode.libphonenumber</groupId>
    <artifactId>libphonenumber</artifactId>
    <version>8.13.52</version>
</dependency>
```

---

## Architecture Patterns

### Recommended Project Structure

The existing structure uses `authentication/` for auth flows. New local auth fits within the same package structure, mirroring the existing `GoogleOAuth2Service` / `AppleOAuth2Service` pattern:

```
src/main/kotlin/kz/innlab/template/
├── authentication/
│   ├── controller/
│   │   └── AuthController.kt                   # Add: register, login, phone/request-otp, phone/verify-otp endpoints
│   ├── dto/
│   │   ├── LocalRegisterRequest.kt              # NEW: email, password, name?
│   │   ├── LocalLoginRequest.kt                 # NEW: email, password
│   │   ├── PhoneOtpRequest.kt                   # NEW: phoneNumber
│   │   └── PhoneVerifyRequest.kt                # NEW: phoneNumber, code
│   └── service/
│       ├── LocalAuthService.kt                  # NEW: register + login logic
│       └── PhoneOtpService.kt                   # NEW: send OTP + verify OTP (Twilio)
├── config/
│   ├── SecurityConfig.kt                        # MODIFY: expose AuthenticationManager bean
│   ├── LocalAuthConfig.kt                       # NEW: DaoAuthenticationProvider, PasswordEncoder beans
│   └── TwilioConfig.kt                          # NEW: Twilio init + properties
├── user/
│   └── model/
│       └── AuthProvider.kt                      # MODIFY: add LOCAL enum value
│       └── User.kt                              # MODIFY: add passwordHash, phone columns
└── src/main/resources/
    └── db/
        └── migration/
            └── V1__initial_schema.sql            # NEW (Flyway): existing tables
            └── V2__add_local_auth_fields.sql     # NEW (Flyway): LOCAL provider fields
```

### Pattern 1: Stateless REST API Login with DaoAuthenticationProvider

**What:** Expose `AuthenticationManager` as a bean, call `authenticate()` programmatically in the controller/service. No form login or session involved.

**When to use:** Stateless JWT API — exactly this project's model.

**Example (verified from official Spring Security 7 docs):**
```kotlin
// Source: https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/index.html

// SecurityConfig.kt — expose AuthenticationManager bean
@Bean
fun authenticationManager(
    userDetailsService: UserDetailsService,
    passwordEncoder: PasswordEncoder
): AuthenticationManager {
    val provider = DaoAuthenticationProvider(userDetailsService)
    provider.setPasswordEncoder(passwordEncoder)
    return ProviderManager(provider)
}

@Bean
fun passwordEncoder(): PasswordEncoder =
    PasswordEncoderFactories.createDelegatingPasswordEncoder()
```

```kotlin
// LocalAuthService.kt — programmatic authentication
@Transactional
fun login(email: String, password: String): AuthResponse {
    val authToken = UsernamePasswordAuthenticationToken.unauthenticated(email, password)
    val authenticated = authenticationManager.authenticate(authToken)
    // authenticated.principal is UserDetails; map to our User
    val user = userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email)
        ?: throw BadCredentialsException("User not found")
    val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
    val refreshToken = refreshTokenService.createToken(user)
    return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
}
```

**Key point:** `DaoAuthenticationProvider` throws `BadCredentialsException` (not `UsernameNotFoundException` by default due to `hideUserNotFoundExceptions = true`). The existing `AuthExceptionHandler` already handles `BadCredentialsException` and returns HTTP 401.

### Pattern 2: UserDetailsService Loading by Email

**What:** Spring Security's `UserDetailsService` interface has one method: `loadUserByUsername(String)`. We use email as the "username".

**When to use:** DaoAuthenticationProvider integration — mandatory when using Spring Security's built-in provider.

```kotlin
// Source: https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/user-details-service.html

@Service
class LocalUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email)
            ?: throw UsernameNotFoundException("No LOCAL user with email: $email")

        return org.springframework.security.core.userdetails.User
            .withUsername(email)
            .password(user.passwordHash ?: throw BadCredentialsException("No password set"))
            .roles(*user.roles.map { it.name }.toTypedArray())
            .build()
    }
}
```

**Why use `findByProviderAndProviderId(LOCAL, email)` not `findByEmail`?** Because the existing unique constraint is on `(provider, provider_id)`, not email alone. The same email may exist under GOOGLE provider. This is the correct scoped lookup.

### Pattern 3: Phone OTP with Twilio Verify API (Two-Step Stateless)

**What:** Twilio Verify manages the OTP lifecycle (generation, delivery, TTL, rate-limiting). No OTP is stored in our database.

**When to use:** Whenever SMS OTP is needed without adding Redis.

```kotlin
// Source: https://www.twilio.com/en-us/blog/phone-number-verification-java-spring-boot-verify-totp

// Step 1: Request OTP
@Service
class PhoneOtpService(
    @Value("\${app.auth.twilio.account-sid}") private val accountSid: String,
    @Value("\${app.auth.twilio.auth-token}") private val authToken: String,
    @Value("\${app.auth.twilio.verify-service-sid}") private val serviceSid: String,
    private val userService: UserService,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {
    fun sendOtp(phoneE164: String) {
        Twilio.init(accountSid, authToken)
        Verification.creator(serviceSid, phoneE164, "sms").create()
        // No return value — OTP stored at Twilio, not our DB
    }

    @Transactional
    fun verifyOtp(phoneE164: String, code: String): AuthResponse {
        Twilio.init(accountSid, authToken)
        val check = VerificationCheck.creator(serviceSid)
            .setTo(phoneE164)
            .setCode(code)
            .create()
        if (check.status != "approved") {
            throw BadCredentialsException("Invalid or expired OTP")
        }
        val user = userService.findOrCreatePhoneUser(phoneE164)
        val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)
        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
```

### Pattern 4: Schema Migration with Flyway

**What:** Spring Boot 4.0 requires `spring-boot-starter-flyway` (not bare `flyway-core`). Migrations go in `src/main/resources/db/migration/`. Current dev profile uses `ddl-auto: create-drop` — this must be switched to `validate` once Flyway is active in all environments.

**Critical Boot 4.0 change:** Direct `flyway-core` dependency is NOT auto-configured. Must use the starter.

```sql
-- V1__initial_schema.sql (captures existing schema that Hibernate currently creates)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    picture VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_provider_id UNIQUE (provider, provider_id)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMPTZ,
    replaced_by_token_hash VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- V2__add_local_auth_fields.sql (new LOCAL provider fields)
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN phone VARCHAR(30) UNIQUE;
```

**application.yaml changes for Flyway:**
```yaml
# dev profile
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate   # was: create-drop

# prod profile
spring:
  flyway:
    enabled: true
    baseline-on-migrate: false
    validate-on-migrate: true
    out-of-order: false
  jpa:
    hibernate:
      ddl-auto: validate
```

**Test profile** — H2 uses `ddl-auto: create-drop` and Flyway disabled (H2 dialect differences make migration scripts incompatible without special handling).

### Pattern 5: User Entity and AuthProvider Changes

```kotlin
// AuthProvider.kt — add LOCAL
enum class AuthProvider {
    GOOGLE,
    APPLE,
    LOCAL   // NEW
}

// User.kt — add nullable fields
@Column(name = "password_hash")
var passwordHash: String? = null  // Only set for LOCAL provider

@Column(name = "phone", unique = true)
var phone: String? = null  // Set for LOCAL phone users; stored in E.164 format
```

**Key decisions that follow from existing architecture:**
- LOCAL email users: `provider = LOCAL`, `providerId = email`
- LOCAL phone users: `provider = LOCAL`, `providerId = phoneE164` (+ `phone = phoneE164`)
- Same email as Google user: treated as SEPARATE accounts (consistent with existing "keyed on provider+providerId" decision — do NOT silently merge)
- `passwordHash` is nullable (existing OAuth users have none)

### Anti-Patterns to Avoid
- **Do NOT use `email` as the global unique key:** The existing `(provider, providerId)` uniqueness model must be preserved. `userRepository.findByEmail()` must NOT be used for authentication — it would return the wrong account when the same email exists under multiple providers.
- **Do NOT merge OAuth and LOCAL accounts automatically:** If john@example.com exists as a Google user and registers locally with the same email, these are two separate accounts. Account linking is a separate feature.
- **Do NOT store OTP codes in our database:** Use Twilio Verify as the OTP authority. Adding a `one_time_codes` table couples us to OTP TTL management.
- **Do NOT enable `ddl-auto: create-drop` in any environment once Flyway is active:** Schema drift between Hibernate and Flyway migrations causes hard-to-debug inconsistencies.
- **Do NOT inline Twilio credentials:** `accountSid`, `authToken`, `serviceSid` must be environment variables (`TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_VERIFY_SERVICE_SID`).
- **Do NOT use `hideUserNotFoundExceptions = false`:** The default `true` prevents user enumeration attacks (attackers can't distinguish "wrong email" from "wrong password").

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Password hashing | Custom SHA-256 with salt | BCryptPasswordEncoder (via DelegatingPasswordEncoder) | BCrypt has built-in salting, correct cost factor, OWASP recommended |
| OTP generation + storage + TTL + retry limit | Redis + UUID + expiry logic | Twilio Verify API | Twilio handles code generation, SMS delivery, TTL (10 min default), max attempts, and rate-limiting |
| Phone number normalization | Regex patterns | google/libphonenumber | Regex fails for many country formats; libphonenumber is authoritative |
| Database schema management | Hibernate ddl-auto create | Flyway migrations | ddl-auto is not safe for production; Flyway provides versioned, auditable migrations |
| AuthenticationManager wiring | Custom filter chain | DaoAuthenticationProvider + ProviderManager | Spring Security's built-in provider handles timing-safe comparison, credential clearing, account state checks |

**Key insight:** Spring Security's `DaoAuthenticationProvider` is specifically designed for database-backed username/password auth. Building a custom `AuthenticationProvider` is only justified if the credential format is non-standard (e.g., phone+OTP, which we correctly handle in its own service without using `DaoAuthenticationProvider`).

---

## Common Pitfalls

### Pitfall 1: NoUniqueBeanDefinitionException for UserDetailsService
**What goes wrong:** Spring Security auto-wires `UserDetailsService`. When you expose a custom one as a `@Bean`, it works. But if you accidentally expose two (e.g., one from auto-configuration and one custom), the app fails to start with `NoUniqueBeanDefinitionException`.
**Why it happens:** Spring Boot's security auto-config looks for exactly one `UserDetailsService` bean.
**How to avoid:** Annotate the custom `LocalUserDetailsService` with `@Primary` or ensure only one `UserDetailsService` bean exists. The project has no auto-configured `InMemoryUserDetailsManager` (it was never added), so this should be clean.
**Warning signs:** App fails to start mentioning `UserDetailsService` bean conflict.

### Pitfall 2: DaoAuthenticationProvider conflicts with existing ProviderManager
**What goes wrong:** If `DaoAuthenticationProvider` is added to the default `ProviderManager` used by the resource server JWT filter chain, it may intercept token refresh requests or API calls.
**Why it happens:** `AuthenticationManager` is shared unless explicitly scoped.
**How to avoid:** Expose a separate `AuthenticationManager` bean (`ProviderManager` wrapping only `DaoAuthenticationProvider`) and inject it directly into `LocalAuthService`. The `SecurityConfig.securityFilterChain` continues to use `oauth2ResourceServer { jwt {} }` for token validation — these are independent paths.
**Warning signs:** 401 responses on `/api/v1/auth/refresh` after adding local auth.

### Pitfall 3: Flyway migration script incompatible with H2 (test profile)
**What goes wrong:** PostgreSQL-specific SQL (e.g., `gen_random_uuid()`, `TIMESTAMPTZ`, `DEFAULT gen_random_uuid()`) fails on H2 in tests.
**Why it happens:** H2 is not fully PostgreSQL-compatible by default.
**How to avoid:** Disable Flyway in the test profile (`spring.flyway.enabled: false`) and keep `ddl-auto: create-drop` for tests. This matches the existing test `application.yaml` pattern.
**Warning signs:** Tests fail on startup with SQL syntax errors.

### Pitfall 4: UserDetailsService `loadUserByUsername` returns stale password hash
**What goes wrong:** If `UserDetails` is cached (Spring Security's `UserDetailsService` can be wrapped in `UserDetailsServiceDelegator` with caching), a password change does not take effect until cache expires.
**Why it happens:** Spring Security optionally caches `UserDetails` for performance.
**How to avoid:** Do not enable `UserDetailsService` caching (it's off by default). Do not use Spring Cache on this service.
**Warning signs:** User can log in with old password after password change.

### Pitfall 5: Phone number format inconsistency
**What goes wrong:** Users submit `+1 (555) 123-4567` or `5551234567` (no country code). Twilio requires E.164 format (`+15551234567`). If stored without normalization, lookup fails on next OTP request.
**Why it happens:** Phone number input is inherently inconsistent.
**How to avoid:** Always normalize with libphonenumber before passing to Twilio and before storing: `PhoneNumberUtil.getInstance().format(parsed, PhoneNumberFormat.E164)`. Require users provide country code in input or default to a configured region.
**Warning signs:** Twilio returns 400 errors or "not a valid phone number".

### Pitfall 6: Missing `passwordHash` column validation before login
**What goes wrong:** A Google user (who has no `passwordHash`) calls the local login endpoint with their email. `DaoAuthenticationProvider` calls `loadUserByUsername`, which returns a `UserDetails` with a null password. BCrypt comparison throws NPE or returns false with misleading error.
**Why it happens:** Same email may exist under GOOGLE and LOCAL providers.
**How to avoid:** `LocalUserDetailsService.loadUserByUsername()` looks up by `(LOCAL, email)` — not by email alone. If no LOCAL user exists for that email, throw `UsernameNotFoundException`. The `DaoAuthenticationProvider` converts this to `BadCredentialsException` (due to `hideUserNotFoundExceptions`), returning a clean 401.

### Pitfall 7: Twilio SDK initialization on every request
**What goes wrong:** Calling `Twilio.init(accountSid, authToken)` on every OTP request is wasteful and technically thread-unsafe if called concurrently during class loading.
**Why it happens:** Tutorial examples call init inline.
**How to avoid:** Call `Twilio.init()` once in a `@PostConstruct` method or `@Bean` in `TwilioConfig`. The Twilio SDK is designed for single initialization.

---

## Code Examples

Verified patterns from official sources:

### Email Registration Flow
```kotlin
// Source: Spring Security 7 docs pattern + project conventions

@Service
class LocalAuthService(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val refreshTokenService: RefreshTokenService
) {

    @Transactional
    fun register(email: String, rawPassword: String, name: String?): AuthResponse {
        // Ensure no LOCAL user with this email already exists
        if (userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email) != null) {
            throw IllegalStateException("Email already registered")  // 409 Conflict
        }
        val user = User(
            email = email,
            provider = AuthProvider.LOCAL,
            providerId = email  // providerId = email for LOCAL users
        ).also {
            it.name = name
            it.passwordHash = passwordEncoder.encode(rawPassword)
        }
        userRepository.save(user)
        val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)
        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }

    fun login(email: String, rawPassword: String): AuthResponse {
        // Throws BadCredentialsException on failure (caught by AuthExceptionHandler -> 401)
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)
        )
        val user = userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email)
            ?: throw BadCredentialsException("User not found")
        val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
        val refreshToken = refreshTokenService.createToken(user)
        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
}
```

### AuthenticationManager Bean (in SecurityConfig or LocalAuthConfig)
```kotlin
// Source: https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/index.html

@Bean
fun passwordEncoder(): PasswordEncoder =
    PasswordEncoderFactories.createDelegatingPasswordEncoder()  // Default: BCrypt

@Bean
fun localAuthenticationManager(
    localUserDetailsService: LocalUserDetailsService,
    passwordEncoder: PasswordEncoder
): AuthenticationManager {
    val provider = DaoAuthenticationProvider(localUserDetailsService)
    provider.setPasswordEncoder(passwordEncoder)
    return ProviderManager(provider)
}
```

### Phone E.164 Normalization
```kotlin
// Source: https://www.baeldung.com/java-libphonenumber

fun normalizeToE164(rawPhone: String, defaultRegion: String = "KZ"): String {
    val util = PhoneNumberUtil.getInstance()
    val parsed = try {
        util.parse(rawPhone, defaultRegion)
    } catch (e: NumberParseException) {
        throw IllegalArgumentException("Invalid phone number: $rawPhone")
    }
    if (!util.isValidNumber(parsed)) {
        throw IllegalArgumentException("Phone number not valid: $rawPhone")
    }
    return util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
}
```

### Twilio Config Bean
```kotlin
// Pitfall 7 prevention: init once

@Configuration
class TwilioConfig(
    @Value("\${app.auth.twilio.account-sid}") private val accountSid: String,
    @Value("\${app.auth.twilio.auth-token}") private val authToken: String
) {
    @PostConstruct
    fun init() {
        Twilio.init(accountSid, authToken)
    }
}
```

### AuthController — New Endpoints
```kotlin
// Follows existing AuthController pattern

@PostMapping("/local/register")
fun localRegister(@Valid @RequestBody request: LocalRegisterRequest): ResponseEntity<AuthResponse> {
    val response = localAuthService.register(request.email, request.password, request.name)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
}

@PostMapping("/local/login")
fun localLogin(@Valid @RequestBody request: LocalLoginRequest): ResponseEntity<AuthResponse> {
    val response = localAuthService.login(request.email, request.password)
    return ResponseEntity.ok(response)
}

@PostMapping("/phone/request-otp")
fun requestPhoneOtp(@Valid @RequestBody request: PhoneOtpRequest): ResponseEntity<Void> {
    phoneOtpService.sendOtp(request.phoneNumber)
    return ResponseEntity.noContent().build()  // 204 — don't leak OTP status
}

@PostMapping("/phone/verify-otp")
fun verifyPhoneOtp(@Valid @RequestBody request: PhoneVerifyRequest): ResponseEntity<AuthResponse> {
    val response = phoneOtpService.verifyOtp(request.phoneNumber, request.code)
    return ResponseEntity.ok(response)
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ddl-auto: create`/`create-drop` for prod | Flyway versioned migrations | Best practice; Boot 4.0 has starter | Schema drift eliminated; rollback possible |
| Direct `flyway-core` dependency | `spring-boot-starter-flyway` | Spring Boot 4.0 (breaking) | Must use starter for auto-configuration |
| MD5/SHA-1 password hashing | BCrypt / Argon2 via DelegatingPasswordEncoder | Spring Security 5.0+ | Timing-safe, salted, configurable cost |
| Custom OTP table + scheduler for TTL | Twilio Verify API | Industry shift ~2018 | No OTP storage on server; Twilio manages TTL and rate-limits |
| Spring Security `formLogin()` | Programmatic `AuthenticationManager.authenticate()` | Stateless JWT pattern | No session, no redirect — pure REST |
| Spring Security MFA (`@EnableMultiFactorAuthentication`) | Out of scope (Spring Security 7, Oct 2025) | Spring Security 7.0 | The new native MFA in Spring Security 7 is for second-factor flows AFTER primary auth. Phone OTP here is PRIMARY authentication (not a second factor). This feature is NOT used in this phase. |

**Deprecated / avoid:**
- `NoOpPasswordEncoder` — never in production
- `UserDetails.withDefaultPasswordEncoder()` — deprecated, logs a warning, for in-memory demos only
- `AuthenticationManagerBuilder` pattern — superseded by direct `ProviderManager` bean
- `spring.security.user.*` properties — default in-memory user, conflicts with custom `UserDetailsService`

---

## Open Questions

1. **Email verification step for registration?**
   - What we know: Email verification (send token → click link → activate account) is a common pattern but requires `spring-boot-starter-mail`, SMTP config, and a token storage table or signed JWT link.
   - What's unclear: Is email verification required for MVP, or can users register and immediately get tokens? The phase description is silent on this.
   - Recommendation: Skip email verification in this phase. Add as a separate enhancement. Users who register with a fake email can't receive password resets — acceptable tradeoff for MVP. Add a `// TODO: email verification` comment.

2. **Password reset flow?**
   - What we know: Password reset requires: (a) generate a time-limited signed token, (b) email it, (c) endpoint to consume token and set new password. Requires `spring-boot-starter-mail`.
   - What's unclear: Required in this phase?
   - Recommendation: Out of scope for this phase. Phase adds login, not account management. Add `// TODO: password reset` endpoint.

3. **Default region for phone number parsing?**
   - What we know: libphonenumber requires a `defaultRegion` hint when the number lacks a `+` country prefix (e.g., `"7071234567"` is ambiguous without knowing the country).
   - What's unclear: What is the primary user base? Kazakhstan (`"KZ"`)?
   - Recommendation: Require users to include `+` prefix (E.164 intent required). Reject numbers without `+`. This eliminates ambiguity without needing a default region config.

4. **Account linking: same email under GOOGLE and LOCAL?**
   - What we know: Existing decision — users are keyed on `(provider, providerId)`, never by email alone. So `john@gmail.com` under GOOGLE and `john@gmail.com` under LOCAL are separate accounts.
   - What's unclear: Is silent account merging required?
   - Recommendation: Separate accounts — consistent with existing architecture. Add a note to API docs. Do not attempt merging.

5. **Flyway baseline for existing dev environment?**
   - What we know: Dev currently uses `ddl-auto: create-drop` — no existing schema. When switching to Flyway, dev environment starts fresh (fine for a template).
   - What's unclear: Is there any existing data to preserve in dev?
   - Recommendation: For a template project, drop and recreate is acceptable. V1 migration creates fresh schema.

---

## Sources

### Primary (HIGH confidence)
- [DaoAuthenticationProvider — Spring Security 7.0.x docs](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html) — DaoAuthenticationProvider flow, PasswordEncoder wiring
- [Username/Password Authentication — Spring Security 7.0.x docs](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/index.html) — AuthenticationManager bean pattern, programmatic auth in REST controller
- [UserDetailsService — Spring Security 7.0.x docs](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/user-details-service.html) — loadUserByUsername pattern, custom implementation
- [Password Storage — Spring Security 7.0.x docs](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) — DelegatingPasswordEncoder, BCryptPasswordEncoder, recommended algorithms
- [Spring Boot 4.0 Migration Guide (GitHub wiki)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) — Flyway starter requirement breaking change

### Secondary (MEDIUM confidence)
- [Twilio Verify + Spring Boot tutorial (official Twilio blog)](https://www.twilio.com/en-us/blog/phone-number-verification-java-spring-boot-verify-totp) — Verification.creator() and VerificationCheck.creator() API calls
- [libphonenumber — Baeldung](https://www.baeldung.com/java-libphonenumber) — E.164 formatting with PhoneNumberUtil
- [Flyway Spring Boot 4.x (Medium, Jan 2026)](https://pranavkhodanpur.medium.com/flyway-migrations-in-spring-boot-4-x-what-changed-and-how-to-configure-it-correctly-dbe290fa4d47) — confirms starter requirement; paywalled, verified via Spring Boot migration guide
- [Multi-Factor Authentication in Spring Security 7 (spring.io blog, Oct 2025)](https://spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7/) — confirms MFA is for second-factor, not phone-as-primary-login

### Tertiary (LOW confidence — not verified against official source)
- [OpenRewrite Spring Boot Flyway recipe](https://docs.openrewrite.org/recipes/java/spring/boot4/addspringbootstarterflyway) — confirms starter is the required migration path

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Spring Security 7 DaoAuthenticationProvider verified from official docs; Twilio Verify API verified from official Twilio blog; BCrypt from official Spring Security password storage docs; Flyway starter requirement from official Spring Boot 4.0 migration guide
- Architecture: HIGH — Pattern matches existing project conventions (same TokenService, RefreshTokenService reuse); new endpoints follow existing AuthController shape
- Pitfalls: HIGH for items derived from the existing project's accumulated decisions (STATE.md); MEDIUM for Twilio-specific pitfalls (based on official docs + community patterns)
- Schema design: HIGH — follows from the existing `(provider, providerId)` keying decision in STATE.md

**Research date:** 2026-03-02
**Valid until:** 2026-04-02 (Spring Security / Boot APIs are stable; Twilio SDK minor versions may update)
