# Phase 3: Google Auth and Token Management - Research

**Researched:** 2026-03-01
**Domain:** Google ID Token Verification / Opaque Refresh Token Lifecycle / JWT Auth Controller / JPA Find-or-Create
**Confidence:** HIGH (core stack verified via official Google API docs and authoritative sources; refresh token grace window pattern verified via multiple implementations)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUTH-01 | Client can send Google ID token to POST /api/v1/auth/google and receive JWT access + refresh tokens | `GoogleIdTokenVerifier.verify(idTokenString)` returns `GoogleIdToken` or null; on success, call find-or-create user, then `JwtTokenService.generateAccessToken()` + `RefreshTokenService.create()`. Return `{ accessToken, refreshToken }` JSON. |
| AUTH-03 | Backend verifies Google ID token directly with Google (via google-api-client) including `aud` claim validation | `GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory()).setAudience(listOf(clientId)).build()` — verifies RS256 signature, `iss`, `aud`, `exp` automatically. Public keys are cached in-memory with expiry. |
| TOKN-02 | Refresh tokens are opaque, stored in DB as SHA-256 hashes, with single-use rotation | Generate 32 raw bytes via `SecureRandom`, Base64url-encode → opaque token string. Store SHA-256 hash. On use: find by hash, issue new token, mark old as revoked. |
| TOKN-03 | Reuse detection revokes all refresh tokens for the user when a used token is replayed | If token is `revoked = true` AND `usedAt` is outside grace window → `deleteAllByUser(user)` to revoke entire family. |
| TOKN-04 | 10-second grace window on refresh token rotation to handle concurrent mobile retries | Add `usedAt: Instant?` and `replacedByTokenHash: String?` to `RefreshToken`. Within 10s of `usedAt`, return the replacement token instead of throwing. |
| TOKN-05 | POST /api/v1/auth/refresh rotates refresh token and returns new access + refresh tokens | Lookup token by hash → check expiry + revoke state → create new pair → revoke old (set `revoked=true`, `usedAt`, `replacedByTokenHash`) → return `{ accessToken, refreshToken }`. |
| TOKN-06 | POST /api/v1/auth/revoke invalidates the refresh token (logout) | Lookup token by hash → set `revoked=true` (no `usedAt` / no new token needed) → return 204. |
| USER-03 | GET /api/v1/users/me returns the current authenticated user's profile | Replace stub: look up `User` by `UUID.fromString(jwt.subject)`, return `UserProfileResponse` DTO with id, email, name, picture, provider, roles, createdAt. |
| USER-04 | New users are created automatically on first successful authentication (find-or-create) | `UserRepository.findByProviderAndProviderId(GOOGLE, sub)` → if null, save new `User` → return existing or new. Must be `@Transactional` with unique constraint on (provider, provider_id) as safety net. |
</phase_requirements>

---

## Summary

Phase 3 wires three independent concerns together: (1) Google ID token verification using the official `google-api-client` library, (2) a complete opaque refresh token lifecycle with rotation, reuse detection, and a 10-second grace window, and (3) promoting the stub `GET /api/v1/users/me` to a real database-backed endpoint. The phase depends entirely on Phase 2's security infrastructure — all Bearer token validation, error handling, and the security filter chain are already in place. Phase 3 only adds auth endpoints and service logic.

The critical non-trivial areas are the refresh token grace window and reuse detection. The pattern is: on first use, immediately rotate (mark old token with `revoked=true`, `usedAt=now`, `replacedByTokenHash=newHash`). On replay within 10 seconds, detect that `revoked=true` but `usedAt` is recent — return the replacement token (`replacedByTokenHash`) to the retrying mobile client without triggering reuse detection. On replay after 10 seconds, treat as token theft and delete all refresh tokens for the user. This pattern is used by Supabase, Directus, and documented by multiple OAuth providers (Okta, Auth0).

`GoogleIdTokenVerifier` from `google-api-client` 2.9.0 handles all cryptographic verification (RS256 signature, `iss`, `aud`, `exp` claims) with automatic public key caching against Google's JWKS endpoint. It is a thread-safe singleton that should be registered as a Spring `@Bean`. The `STATE.md` flags its builder pattern and multi-audience config as MEDIUM confidence — this research confirms the API at HIGH confidence: `.Builder(NetHttpTransport(), GsonFactory()).setAudience(listOf(clientId)).build()` is the documented pattern.

**Primary recommendation:** Use `GoogleIdTokenVerifier` as a singleton `@Bean` in `SecurityConfig` (or a dedicated `GoogleAuthConfig`), implement `RefreshTokenService` with full grace window logic using `usedAt` + `replacedByTokenHash` fields, and run all token operations inside `@Transactional` methods to prevent partial state.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `google-api-client` | 2.9.0 | `GoogleIdTokenVerifier` — verifies Google ID token signature, `iss`, `aud`, `exp`; caches Google public keys in-memory | Official Google client library; strongly recommended by Google's own docs over hand-rolling JWT verification |
| `spring-boot-starter-data-jpa` | managed by Boot 4.0.3 BOM | JPA repository methods for `@Modifying @Query` delete operations, `@Transactional` scope | Already on classpath (Phase 1) |
| `spring-boot-starter-security` | managed by Boot 4.0.3 BOM | `SecurityContextHolder`, `@AuthenticationPrincipal`, JWT subject extraction | Already on classpath (Phase 2) |
| `spring-boot-starter-oauth2-authorization-server` | managed by Boot 4.0.3 BOM | `JwtTokenService` already uses `NimbusJwtEncoder` | Already on classpath; no new work needed for access token minting |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `google-http-client-gson` | transitive via `google-api-client` | `GsonFactory` for JSON parsing in `GoogleIdTokenVerifier` | Required; included automatically |
| `google-oauth-client` | transitive via `google-api-client` | `IdTokenVerifier` base class | Required; included automatically |
| `java.security.SecureRandom` (JDK) | JDK 24 (already used) | Generating 32 raw random bytes for opaque refresh token value | JDK built-in; no dep needed |
| `java.security.MessageDigest` (JDK) | JDK 24 | SHA-256 hashing of refresh token for DB storage | JDK built-in; no dep needed |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `google-api-client` + `GoogleIdTokenVerifier` | Manual JWT verification against Google's JWKS endpoint using `NimbusJwtDecoder` | `NimbusJwtDecoder` works but requires custom `aud` + `iss` validation logic, manual key refresh scheduling, and custom clock skew handling. `GoogleIdTokenVerifier` handles all of this automatically. Requirement AUTH-03 explicitly calls for `google-api-client`. |
| `google-api-client` + `GoogleIdTokenVerifier` | Firebase Admin SDK `FirebaseAuth.verifyIdToken()` | Firebase Admin is a heavyweight SDK that requires a service account credential file; it adds significant complexity for a pure token verification use case. Explicitly excluded from requirements (Out of Scope table). |
| `SecureRandom` + `Base64url` for opaque token | `UUID.randomUUID().toString()` | UUID v4 uses `SecureRandom` internally but wastes 6 bits on version/variant markers, producing only 122 random bits. `SecureRandom` + 32 raw bytes produces 256 bits of entropy with clearer cryptographic intent. Both are acceptable; `SecureRandom` direct is preferred for tokens per security best practices. |
| SHA-256 token hash in DB | Plaintext token in DB | SHA-256 hash prevents DB dump from yielding usable tokens. TOKN-02 explicitly requires hash storage. |

**Installation — new Maven dependency (one new dependency for this phase):**

```xml
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.9.0</version>
</dependency>
```

Add to `pom.xml` `<dependencies>` section. No version tag needed if Spring Boot BOM manages it; but as of Spring Boot 4.0.3, `google-api-client` is NOT in the Boot BOM — pin the version explicitly to `2.9.0`.

---

## Architecture Patterns

### Recommended Project Structure

```
src/main/kotlin/kz/innlab/template/
├── config/
│   ├── RsaKeyConfig.kt                     # Phase 1: JWKSource, JwtEncoder, JwtDecoder (DONE)
│   ├── SecurityConfig.kt                   # Phase 2: SecurityFilterChain (DONE)
│   ├── CorsProperties.kt                   # Phase 2: CORS properties (DONE)
│   └── GoogleAuthConfig.kt                 # Phase 3: NEW — GoogleIdTokenVerifier @Bean
├── authentication/
│   ├── RefreshToken.kt                     # Phase 1: entity — NEEDS 2 new fields
│   ├── RefreshTokenRepository.kt           # Phase 1: repository — NEEDS 2 new methods
│   ├── RefreshTokenService.kt              # Phase 3: NEW — full token lifecycle
│   ├── GoogleAuthService.kt                # Phase 3: NEW — Google ID token → user + tokens
│   ├── AuthController.kt                   # Phase 3: NEW — POST /auth/google, /auth/refresh, /auth/revoke
│   ├── JwtTokenService.kt                  # Phase 2: access token minting (DONE)
│   ├── dto/
│   │   ├── AuthRequest.kt                  # Phase 2: { idToken } DTO (DONE)
│   │   ├── AuthResponse.kt                 # Phase 3: NEW — { accessToken, refreshToken }
│   │   └── RefreshRequest.kt               # Phase 3: NEW — { refreshToken }
│   └── error/
│       ├── ApiAuthenticationEntryPoint.kt  # Phase 2 (DONE)
│       └── ApiAccessDeniedHandler.kt       # Phase 2 (DONE)
├── user/
│   ├── User.kt                             # Phase 1: entity (DONE)
│   ├── UserRepository.kt                   # Phase 1: repository (DONE)
│   ├── UserService.kt                      # Phase 3: NEW — findOrCreate, findById
│   ├── UserController.kt                   # Phase 2: stub — REPLACE with real lookup
│   ├── UserProfileResponse.kt              # Phase 3: NEW — response DTO
│   ├── AuthProvider.kt                     # Phase 1 (DONE)
│   └── Role.kt                             # Phase 1 (DONE)
└── shared/
    └── error/
        ├── ErrorResponse.kt                # Phase 2 (DONE)
        └── GlobalExceptionHandler.kt       # Phase 2 (DONE)
```

### Pattern 1: GoogleIdTokenVerifier as Singleton Spring Bean

**What:** Thread-safe singleton registered as `@Bean`, reused on every auth request. Caches Google's public keys in-memory with expiry.
**When to use:** AUTH-03 — all Google token verification routes through this single bean.

```kotlin
// Source: https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
// Source: https://docs.cloud.google.com/java/docs/reference/google-api-client/latest/com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
// File: config/GoogleAuthConfig.kt
package kz.innlab.template.config

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GoogleAuthConfig(
    @Value("\${app.auth.google.client-id}")
    private val googleClientId: String
) {
    @Bean
    fun googleIdTokenVerifier(): GoogleIdTokenVerifier =
        GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(listOf(googleClientId))
            .build()
}
```

In `application.yml`:
```yaml
app:
  auth:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
```

In `.env.example`:
```
GOOGLE_CLIENT_ID=123456789-abc.apps.googleusercontent.com
```

**Key points:**
- `GoogleIdTokenVerifier` is thread-safe — safe as a singleton `@Bean`
- `.setAudience(listOf(clientId))` validates the `aud` claim — critical for AUTH-03
- Public keys are cached in-memory; only refreshed when cache expires (no per-request network call)
- `verify(idTokenString)` returns `GoogleIdToken` or `null` (not an exception) — null means invalid
- `verifier.verify(idTokenString)` internally verifies: RS256 signature, `iss` (`accounts.google.com` or `https://accounts.google.com`), `aud`, `exp` (with 5-minute clock skew allowance)

### Pattern 2: GoogleIdToken.Payload — Extracting User Claims

**What:** After successful verification, extract user identity from the token payload.
**When to use:** Inside `GoogleAuthService` to create or find the user.

```kotlin
// Source: https://docs.cloud.google.com/java/docs/reference/google-api-client/latest/com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload
val payload: GoogleIdToken.Payload = idToken.payload

val providerId: String = payload.subject          // getSubject() — unique per Google Account, never reused
val email: String = payload.email                  // getEmail() — may be null if scope not granted
val name: String? = payload["name"] as String?     // Map-based access for non-standard OIDC claims
val picture: String? = payload["picture"] as String?
// NOTE: Do NOT use payload.email as a unique key — use payload.subject (providerId)
// The User entity is already keyed on (provider, providerId) per USER-02
```

**Critical:** Always use `payload.subject` (the `sub` claim) as the stable Google user identifier. `email` can change if the user changes their Google address. This matches the existing `User` entity design (USER-02).

### Pattern 3: Find-or-Create User (USER-04)

**What:** Atomic find-or-create within a `@Transactional` method. The unique constraint on `(provider, provider_id)` in the DB serves as the definitive conflict guard.
**When to use:** Every call to `POST /api/v1/auth/google`.

```kotlin
// File: user/UserService.kt
package kz.innlab.template.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(private val userRepository: UserRepository) {

    @Transactional
    fun findOrCreateGoogleUser(
        providerId: String,
        email: String,
        name: String?,
        picture: String?
    ): User {
        return userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
            ?: userRepository.save(
                User(
                    email = email,
                    provider = AuthProvider.GOOGLE,
                    providerId = providerId
                ).also { user ->
                    user.name = name
                    user.picture = picture
                }
            )
    }

    @Transactional(readOnly = true)
    fun findById(id: java.util.UUID): User =
        userRepository.findById(id).orElseThrow {
            org.springframework.security.access.AccessDeniedException("User not found")
        }
}
```

**Race condition note:** Concurrent first-logins from the same Google account (rare but possible) can produce a `DataIntegrityViolationException` from the unique constraint. For a template, the simple approach is acceptable: let the constraint violation propagate as a 500 (the user can retry). If higher concurrency is needed, add a `try/catch DataIntegrityViolationException` → retry `findByProviderAndProviderId`. This is NOT needed for initial implementation but documented as an edge case.

### Pattern 4: Opaque Refresh Token — Generation and Hashing

**What:** Generate a cryptographically secure opaque token, store its SHA-256 hash in the DB.
**When to use:** Every time a new refresh token is issued (on login and on rotation).

```kotlin
// Source: JDK SecureRandom + MessageDigest — standard Java cryptography
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private val secureRandom = SecureRandom()

fun generateRawToken(): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

fun hashToken(rawToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(hashBytes)
}
```

**Flow:** Generate raw → give raw to client → store `hashToken(raw)` in DB. On refresh: hash the incoming token → look up by hash.

### Pattern 5: RefreshToken Entity — Additions for Grace Window (TOKN-04)

**What:** Two new fields are needed on the existing `RefreshToken` entity to support the grace window and reuse detection.
**When to use:** Required for TOKN-03 and TOKN-04.

```kotlin
// Additions to existing RefreshToken.kt — two new nullable columns
@Column(name = "used_at")
var usedAt: Instant? = null          // Set when token is first consumed for rotation

@Column(name = "replaced_by_token_hash")
var replacedByTokenHash: String? = null   // Hash of the new token issued during rotation
```

`revoked = true` already exists on the entity (Phase 1). The complete state machine:
- **Active:** `revoked=false`, `usedAt=null`, `replacedByTokenHash=null`
- **Used (within grace window):** `revoked=true`, `usedAt=<timestamp>`, `replacedByTokenHash=<newHash>`
- **Expired:** `expiresAt` has passed
- **Revoked (logout):** `revoked=true`, `usedAt=null` (no replacement)

### Pattern 6: RefreshTokenRepository — New Query Methods

**What:** Two new query methods required by `RefreshTokenService`.
**When to use:** Called during rotation (delete family) and revocation (delete family on reuse detection).

```kotlin
// Additions to RefreshTokenRepository.kt
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

// Bulk delete all tokens for a user (reuse detection + logout cleanup)
@Modifying
@Transactional
@Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
fun deleteAllByUser(@Param("user") user: User)

// Find by the replacement hash (for grace window lookup)
fun findByReplacedByTokenHash(hash: String): RefreshToken?
```

**Important:** `@Modifying` + JPQL `DELETE` executes as a single bulk statement (efficient). Using `deleteBy` derived methods instead would load entities first then delete one-by-one (N+1 problem on a user with many tokens).

### Pattern 7: RefreshTokenService — Full Lifecycle

**What:** The central service managing all refresh token operations. All methods must be `@Transactional`.

```kotlin
// File: authentication/RefreshTokenService.kt
package kz.innlab.template.authentication

import kz.innlab.template.user.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${app.auth.refresh-token.expiry-days:30}")
    private val expiryDays: Long
) {
    private val secureRandom = SecureRandom()
    private val graceWindowSeconds = 10L

    /** Generate a raw opaque token and persist its hash. Returns the raw token (sent to client). */
    @Transactional
    fun createToken(user: User): String {
        val rawToken = generateRawToken()
        val tokenHash = hashToken(rawToken)
        refreshTokenRepository.save(
            RefreshToken(
                user = user,
                tokenHash = tokenHash,
                expiresAt = Instant.now().plus(expiryDays, ChronoUnit.DAYS)
            )
        )
        return rawToken
    }

    /**
     * Rotate a refresh token. Returns a pair (newAccessTokenUserId, newRawRefreshToken).
     * Handles grace window for concurrent mobile retries (TOKN-04).
     * Triggers reuse detection and full revocation if token is replayed outside grace window (TOKN-03).
     */
    @Transactional
    fun rotate(rawToken: String): Pair<User, String> {
        val hash = hashToken(rawToken)
        val stored = refreshTokenRepository.findByTokenHash(hash)
            ?: throw org.springframework.security.authentication.BadCredentialsException("Invalid refresh token")

        // Expired (wall-clock past expiresAt)
        if (stored.expiresAt.isBefore(Instant.now())) {
            throw org.springframework.security.authentication.BadCredentialsException("Refresh token expired")
        }

        if (stored.revoked) {
            val usedAt = stored.usedAt
            val replacedBy = stored.replacedByTokenHash
            if (usedAt != null && replacedBy != null &&
                Instant.now().isBefore(usedAt.plusSeconds(graceWindowSeconds))) {
                // Within grace window: concurrent retry — return the already-issued replacement
                val replacement = refreshTokenRepository.findByTokenHash(replacedBy)
                    ?: throw org.springframework.security.authentication.BadCredentialsException("Replacement token not found")
                return Pair(replacement.user, /* raw token not stored — client must use existing */ "")
                // NOTE: in practice, the client should already have the replacement from the first call.
                // Return the same user so the controller can re-issue an access token.
                // See Open Questions for the full grace window response strategy.
            }
            // Outside grace window: reuse detected — revoke entire family
            refreshTokenRepository.deleteAllByUser(stored.user)
            throw org.springframework.security.authentication.BadCredentialsException("Refresh token reuse detected")
        }

        // Normal rotation: issue new token, mark old as used
        val newRawToken = generateRawToken()
        val newHash = hashToken(newRawToken)
        refreshTokenRepository.save(
            RefreshToken(
                user = stored.user,
                tokenHash = newHash,
                expiresAt = Instant.now().plus(expiryDays, ChronoUnit.DAYS)
            )
        )
        stored.revoked = true
        stored.usedAt = Instant.now()
        stored.replacedByTokenHash = newHash
        refreshTokenRepository.save(stored)

        return Pair(stored.user, newRawToken)
    }

    /** Revoke a refresh token (logout). Does NOT delete other tokens (user may have other sessions). */
    @Transactional
    fun revoke(rawToken: String) {
        val hash = hashToken(rawToken)
        val stored = refreshTokenRepository.findByTokenHash(hash) ?: return  // Idempotent
        stored.revoked = true
        refreshTokenRepository.save(stored)
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(
            digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        )
    }
}
```

### Pattern 8: AuthController — Three Endpoints

**What:** Controller wiring the three auth endpoints. All are under `/api/v1/auth/**` (already `permitAll` in SecurityConfig).

```kotlin
// File: authentication/AuthController.kt
package kz.innlab.template.authentication

import jakarta.validation.Valid
import kz.innlab.template.authentication.dto.AuthRequest
import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.authentication.dto.RefreshRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val googleAuthService: GoogleAuthService,
    private val refreshTokenService: RefreshTokenService,
    private val jwtTokenService: JwtTokenService
) {
    @PostMapping("/google")
    fun googleLogin(@Valid @RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        val response = googleAuthService.authenticate(request.idToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val (user, newRawToken) = refreshTokenService.rotate(request.refreshToken)
        val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
        return ResponseEntity.ok(AuthResponse(accessToken = accessToken, refreshToken = newRawToken))
    }

    @PostMapping("/revoke")
    fun revoke(@Valid @RequestBody request: RefreshRequest): ResponseEntity<Void> {
        refreshTokenService.revoke(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
```

### Pattern 9: UserController — Real /users/me Endpoint (USER-03)

**What:** Replace the stub `UserController` to return the actual user profile from the DB.
**When to use:** JWT `sub` claim contains the user UUID; look up by ID.

```kotlin
// File: user/UserController.kt — replace existing stub
package kz.innlab.template.user

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @GetMapping("/me")
    fun getCurrentUser(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<UserProfileResponse> {
        val userId = UUID.fromString(jwt.subject)
        val user = userService.findById(userId)
        return ResponseEntity.ok(UserProfileResponse.from(user))
    }
}
```

**Note:** `@AuthenticationPrincipal` is cleaner than `authentication.principal as Jwt` — it auto-resolves the typed principal and makes intent clear.

### Pattern 10: DTO Definitions

```kotlin
// File: authentication/dto/AuthResponse.kt
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String
)

// File: authentication/dto/RefreshRequest.kt
data class RefreshRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String = ""
)

// File: user/UserProfileResponse.kt
data class UserProfileResponse(
    val id: String,
    val email: String,
    val name: String?,
    val picture: String?,
    val provider: String,
    val roles: List<String>,
    val createdAt: java.time.Instant?
) {
    companion object {
        fun from(user: User) = UserProfileResponse(
            id = user.id.toString(),
            email = user.email,
            name = user.name,
            picture = user.picture,
            provider = user.provider.name,
            roles = user.roles.map { it.name },
            createdAt = user.createdAt
        )
    }
}
```

### Anti-Patterns to Avoid

- **Calling `googleIdTokenVerifier.verify()` inside a `@Bean` method or constructor:** The verifier makes network calls on first use to fetch Google's public keys. Creating it inside a request handler creates a new HTTP connection pool on every request. Always register as a singleton `@Bean`.

- **Storing the raw refresh token in the DB:** TOKN-02 explicitly requires SHA-256 hash storage. A DB dump should not yield usable tokens.

- **Using `UUID.fromString(jwt.subject)` without error handling:** If the JWT was tampered and `sub` is not a valid UUID, `UUID.fromString` throws `IllegalArgumentException`. The `GlobalExceptionHandler` will return 500 instead of 401. Add a try/catch or validate in the service layer.

- **Running reuse detection and token operations in separate transactions:** If `deleteAllByUser` and the caller's error response are in different transactions, a crash between them can leave partially revoked state. Keep all token operations in a single `@Transactional` method.

- **Not validating token expiry before reuse detection:** A revoked-but-expired token could reach the reuse detection branch. Always check `expiresAt` before the `revoked` check.

- **Not returning 401 from `BadCredentialsException` — defaulting to 500:** `GlobalExceptionHandler` currently has a catch-all `Exception::class` handler that returns 500. `BadCredentialsException` thrown from `RefreshTokenService` will be caught there and return 500. Either: (a) add a specific handler for `BadCredentialsException` → 401, or (b) throw a custom domain exception. Option (a) is simpler.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Google ID token signature verification | Custom RSA verify against `https://www.googleapis.com/oauth2/v3/certs` | `GoogleIdTokenVerifier` | Handles key caching, expiry, RS256 algorithm, clock skew (5 min), `iss`/`aud`/`exp` checks automatically |
| Public key refresh for Google JWKS | Custom scheduled HTTP call to update key cache | `GoogleIdTokenVerifier` built-in caching | Keys expire when Google says they do; the verifier fetches new ones on demand |
| SHA-256 hashing | External library | `java.security.MessageDigest.getInstance("SHA-256")` | JDK built-in; no extra dependency |
| Secure random token generation | `UUID.randomUUID()` or `Math.random()` | `java.security.SecureRandom` | Full 256-bit entropy; cryptographically secure; explicit intent |
| Bulk refresh token revocation | Delete tokens one-by-one in a loop | `@Modifying @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")` | Single SQL statement; avoids N+1 loading |

**Key insight:** This phase is about orchestrating existing pieces — `GoogleIdTokenVerifier`, `JwtTokenService`, `RefreshTokenRepository`, `UserRepository` — into a coherent auth flow. The only "new" algorithmic logic is the grace window state machine. Everything else is wiring.

---

## Common Pitfalls

### Pitfall 1: GoogleIdTokenVerifier Returns null (Not Exception)

**What goes wrong:** Code calls `verifier.verify(idToken)` and doesn't null-check the return value, causing `NullPointerException` or treating invalid tokens as valid.
**Why it happens:** `verify(String)` returns `null` on any verification failure (invalid signature, expired, wrong audience). It does NOT throw an exception for verification failure.
**How to avoid:**
```kotlin
val idToken = googleIdTokenVerifier.verify(idTokenString)
    ?: throw BadCredentialsException("Invalid Google ID token")
```
**Warning signs:** NullPointerException in `GoogleAuthService`; successful auth with an expired Google token.

### Pitfall 2: `aud` Claim Mismatch — Android vs Web Client IDs

**What goes wrong:** Mobile clients send tokens with the Android OAuth2 client ID, but the verifier is configured with only the Web client ID. `verify()` returns null even for valid mobile tokens.
**Why it happens:** Google generates separate OAuth2 client IDs for Android, iOS, and Web apps within the same project. Each token's `aud` claim contains the client ID of the originating app.
**How to avoid:** Configure `setAudience(listOf(webClientId, androidClientId, iosClientId))` for all intended client IDs. Start with a single ID (the one used in development) and expand as needed.
**Warning signs:** Tokens from a mobile app verifier consistently return null; tokens from a web client work fine.

### Pitfall 3: Google Public Key Fetch Fails at Startup

**What goes wrong:** `GoogleIdTokenVerifier` bean creation succeeds but first verification call fails because the app is behind a firewall/proxy that blocks outbound HTTP to Google's JWKS endpoint.
**Why it happens:** `NetHttpTransport` uses JDK's default HTTP client; no proxy configuration is applied by default.
**How to avoid:** In dev, ensure the machine has internet access. In prod, ensure the container/VM can reach `https://www.googleapis.com`. The verifier lazily fetches keys on first use, not at bean creation, so startup won't fail but first requests will.
**Warning signs:** `IOError` or `SocketTimeoutException` inside `GoogleIdTokenVerifier.verify()` in production logs.

### Pitfall 4: Grace Window — Returning the Right Token to the Mobile Client

**What goes wrong:** Within the grace window, the original raw token is not stored (only the hash). The server cannot return the previously issued new raw token to the retrying client.
**Why it happens:** Raw tokens are not stored (TOKN-02). When a concurrent retry comes in within 10s, the server knows which hash was issued as the replacement (`replacedByTokenHash`) but not the corresponding raw bytes.
**How to avoid:** Two options:
1. **Accept the limitation:** Within the grace window, throw 409 Conflict or 425 Too Early — client should retry after a brief delay. This is simpler and sufficient for most mobile retry patterns.
2. **Store replacement raw token temporarily (in cache / encrypted column):** More complex; only needed if clients cannot handle a retry response.
The simplest correct behavior for a template: within the grace window, return 409 with `Retry-After: 1` header. The mobile client retries and gets a fresh rotation from the new token.
**Warning signs:** Mobile clients report intermittent 400/401 during concurrent refresh flows.

**Note:** The `STATE.md` mentions "10-second grace window for concurrent mobile retry handling." The most pragmatic implementation at template level is to detect concurrent reuse (revoked within 10s, has `replacedByTokenHash`) and return a `409 Conflict` rather than a 401, signaling the client to retry. Full grace window passthrough (returning the new raw token) requires storing raw tokens — not compatible with TOKN-02.

### Pitfall 5: @Transactional on RefreshTokenService Not Taking Effect

**What goes wrong:** Token rotation leaves partial state — old token marked revoked but new token not saved, or vice versa.
**Why it happens:** `@Transactional` on a method is only effective when called through the Spring proxy, i.e., from another bean. Self-calls within the same class bypass the proxy.
**How to avoid:** Never call `RefreshTokenService` methods from within `RefreshTokenService` itself. Call only from `AuthController` (different bean). All public methods of `RefreshTokenService` that modify state must be `@Transactional`.
**Warning signs:** Database inconsistencies: tokens marked revoked but no replacement token in DB; or new token created but old not revoked.

### Pitfall 6: GlobalExceptionHandler Catches BadCredentialsException as 500

**What goes wrong:** `POST /api/v1/auth/refresh` with an invalid token returns 500 instead of 401.
**Why it happens:** The current `GlobalExceptionHandler` has a catch-all `Exception::class` handler that returns 500. `BadCredentialsException` (thrown by `RefreshTokenService`) is a subtype of `Exception`.
**How to avoid:** Add a specific handler in `GlobalExceptionHandler`:
```kotlin
@ExceptionHandler(BadCredentialsException::class)
fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(401).body(
        ErrorResponse(error = "Unauthorized", message = ex.message ?: "Invalid credentials", status = 401)
    )
```
**Warning signs:** Invalid refresh token requests return 500; valid requests return 200.

### Pitfall 7: Hibernate Lazy Loading on User in RefreshToken After Transaction

**What goes wrong:** `refreshToken.user` is accessed outside a transaction (e.g., in the controller after the service returns), causing `LazyInitializationException`.
**Why it happens:** `RefreshToken.user` is `@ManyToOne(fetch = FetchType.LAZY)`. After `RefreshTokenService`'s `@Transactional` method completes, the Hibernate session closes. Accessing `refreshToken.user.roles` outside this transaction throws.
**How to avoid:** Return the `User` entity directly from `RefreshTokenService.rotate()` (not `RefreshToken`). The service fetches the user inside the transaction and returns it as a non-proxy object. This is already the design in Pattern 7 above (`Pair<User, String>`).
**Warning signs:** `LazyInitializationException: could not initialize proxy` in controller layer.

### Pitfall 8: `open-in-view: false` Already Set — Good

**What goes wrong (if not set):** The open-in-view anti-pattern would keep a database connection open for the entire HTTP request lifecycle, masking lazy loading issues.
**Current state:** `spring.jpa.open-in-view: false` is already set in `application.yml` (Phase 1). This is correct and means any lazy loading outside a transaction will fail fast with `LazyInitializationException` rather than silently working. Treat this as a linter for the design in Pattern 7.

---

## Code Examples

Verified patterns from official sources:

### Google Auth Service (Complete Flow)

```kotlin
// File: authentication/GoogleAuthService.kt
// Source: https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
package kz.innlab.template.authentication

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.user.UserService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoogleAuthService(
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val userService: UserService,
    private val jwtTokenService: JwtTokenService,
    private val refreshTokenService: RefreshTokenService
) {
    @Transactional
    fun authenticate(idTokenString: String): AuthResponse {
        val idToken = googleIdTokenVerifier.verify(idTokenString)
            ?: throw BadCredentialsException("Invalid Google ID token")

        val payload = idToken.payload
        val providerId = payload.subject         // Stable Google user ID (sub claim)
        val email = payload.email ?: throw BadCredentialsException("Email not present in Google token")
        val name = payload["name"] as String?
        val picture = payload["picture"] as String?

        val user = userService.findOrCreateGoogleUser(
            providerId = providerId,
            email = email,
            name = name,
            picture = picture
        )

        val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
        val rawRefreshToken = refreshTokenService.createToken(user)

        return AuthResponse(accessToken = accessToken, refreshToken = rawRefreshToken)
    }
}
```

### RefreshToken Entity — Complete with Grace Window Fields

```kotlin
// Additions to existing RefreshToken.kt
// (existing fields: id, user, tokenHash, expiresAt, revoked, createdAt — kept as-is)

@Column(name = "used_at")
var usedAt: Instant? = null

@Column(name = "replaced_by_token_hash")
var replacedByTokenHash: String? = null
```

Schema DDL (auto-generated by `ddl-auto: create-drop` in dev):
```sql
ALTER TABLE refresh_tokens ADD COLUMN used_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE refresh_tokens ADD COLUMN replaced_by_token_hash VARCHAR(255);
```

### GlobalExceptionHandler Addition for BadCredentialsException

```kotlin
// Add to GlobalExceptionHandler.kt
import org.springframework.security.authentication.BadCredentialsException

@ExceptionHandler(BadCredentialsException::class)
fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
        ErrorResponse(error = "Unauthorized", message = ex.message ?: "Invalid credentials", status = 401)
    )
```

### Application YAML Additions

```yaml
# Add to common section of application.yml
app:
  auth:
    google:
      client-id: ${GOOGLE_CLIENT_ID}
    refresh-token:
      expiry-days: ${REFRESH_TOKEN_EXPIRY_DAYS:30}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Firebase Admin SDK for Google token verification | `google-api-client` `GoogleIdTokenVerifier` | N/A — always separate | Firebase adds service account dependency; direct verification is lighter |
| Storing refresh tokens as plaintext | SHA-256 hash storage | Security community standard (c. 2018+) | DB dump does not expose usable tokens |
| Single-use refresh tokens with no grace window | Grace window (10s) + replacement token tracking | Auth0/Okta adopted c. 2020 | Handles mobile concurrent retry without triggering false-positive reuse detection |
| `@AuthenticationPrincipal` as `UserDetails` | `@AuthenticationPrincipal Jwt` | Spring Security 5.1 (OAuth2 resource server) | No `UserDetails` needed; JWT claims are the principal |
| `Authentication authentication` parameter in controller | `@AuthenticationPrincipal Jwt jwt` | Spring Security 5.1 | Typed injection; more expressive; avoids cast |

**Deprecated/outdated:**
- `GoogleIdToken.Payload.getUserId()`: Deprecated — use `getSubject()` instead.
- Refresh tokens as JWTs: Opaque tokens stored as hashes are the current best practice; JWT refresh tokens cannot be individually revoked without a blocklist.

---

## Open Questions

1. **Grace Window — Response Strategy**
   - What we know: TOKN-04 requires a 10-second grace window. Within that window, the original raw token is no longer available (only the hash is stored). Returning the replacement raw token requires either storing it plaintext or caching it.
   - What's unclear: The exact expected behavior when the same refresh token is replayed within the grace window. Should the server return 409 (retry), re-issue a new rotation, or return the original replacement?
   - Recommendation: For this template, the safest behavior is: within grace window + has `replacedByTokenHash` → return `409 Conflict` with `Retry-After: 1`. The mobile client retries with the new token it received from the first successful call. This avoids storing raw tokens and is consistent with TOKN-02. Document this behavior in the API contract. If the user later needs full passthrough, they can add a short-lived Redis cache keyed by `tokenHash → rawNewToken`.

2. **Multi-Audience Google Client IDs**
   - What we know: `setAudience(listOf(...))` accepts multiple client IDs. Android, iOS, and Web apps in the same Google Cloud project each have a separate client ID.
   - What's unclear: Whether the initial template should accept one or multiple audience values.
   - Recommendation: Start with a single `GOOGLE_CLIENT_ID` env var (the one client the template consumer is developing). Document in `.env.example` that additional client IDs can be comma-separated and split in code: `setAudience(clientId.split(","))`.

3. **`ddl-auto: create-drop` in dev — New Columns**
   - What we know: The dev profile uses `ddl-auto: create-drop`. Adding two new fields to `RefreshToken` will cause Hibernate to drop and recreate the schema automatically — no migration needed for dev.
   - What's unclear: Nothing; this is expected behavior. Prod profile uses `ddl-auto: validate`, so a manual migration would be needed before a real deployment. For a template, this is acceptable — the consumer adds Flyway in v2 (SCHM-01).
   - Recommendation: No action needed for Phase 3; the two new columns are picked up by `create-drop` in dev.

4. **Token Expiry Value — 30 Days Default**
   - What we know: Auth0 recommends 7-30 days for mobile apps; Okta defaults to ~90 days. TOKN-02 doesn't specify a value.
   - What's unclear: The right default for a template.
   - Recommendation: Default to 30 days via `${REFRESH_TOKEN_EXPIRY_DAYS:30}`. Document in `.env.example`. Template consumers can adjust.

---

## Sources

### Primary (HIGH confidence)

- [Google Identity — Verify the Google ID Token on your server side](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token) — Official Google verification flow, claims verified, library recommendation
- [GoogleIdTokenVerifier Javadoc (2.9.0)](https://docs.cloud.google.com/java/docs/reference/google-api-client/latest/com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier) — Builder API, `setAudience()`, `verify()` return type, thread-safety
- [GoogleIdToken.Payload Javadoc (2.9.0)](https://docs.cloud.google.com/java/docs/reference/google-api-client/latest/com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload) — `getSubject()`, `getEmail()`, `getEmailVerified()`, `getHostedDomain()`, map-based access for `name`, `picture`
- [google-api-client 2.9.0 on Maven Central](https://central.sonatype.com/artifact/com.google.api-client/google-api-client) — Latest stable version confirmed: 2.9.0
- [Mihai Andrei — Refresh Token Reuse Interval and Reuse Detection](https://mihai-andrei.com/blog/refresh-token-reuse-interval-and-reuse-detection/) — `usedAt` + `replacedByTokenHash` pattern; `for("update")` row locking; immediate rotation with grace window return; detailed DB field analysis
- [Spring Data JPA @Modifying Annotation — Baeldung](https://www.baeldung.com/spring-data-jpa-modifying-annotation) — `@Modifying`, `clearAutomatically`, JPQL DELETE vs derived deleteBy
- [Auth0 — Refresh Token Rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/configure-refresh-token-rotation) — Grace period (overlap period) concept; reuse detection family revocation
- [Okta — Refresh Tokens](https://developer.okta.com/docs/guides/refresh-tokens/main/) — Grace period 0-60 seconds; default 30s; concurrent request problem explanation

### Secondary (MEDIUM confidence)

- [Journey to a WebApp — OAuth token verification in Spring backends](https://www.journeytoawebapp.com/posts/oauth-google-spring-verification/) — Kotlin Spring Boot `GoogleIdTokenVerifier` bean pattern; verified against official Javadoc
- [Secure Refresh Token Rotation with Theft Detection — Mihai Andrei](https://mihai-andrei.com/blog/refresh-token-reuse-interval-and-reuse-detection/) — Database implementation of `usedAt` field and grace window logic (corroborated by Auth0/Okta documentation)
- [Ory Hydra — Graceful Token Refresh](https://www.ory.sh/docs/hydra/guides/graceful-token-refresh) — Industry reference for grace window behavior

### Tertiary (LOW confidence — verify before use)

- WebSearch: `SecureRandom` preferred over `UUID.randomUUID()` for refresh tokens — confirmed by cryptography best practice sources at MEDIUM; both are acceptable but `SecureRandom` has clearer intent
- WebSearch: `DataIntegrityViolationException` catch-and-retry for find-or-create race condition — mentioned in multiple sources; LOW confidence on frequency of occurrence; not required for initial implementation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — `google-api-client` 2.9.0 verified on Maven Central; API confirmed via official Javadoc; no alternatives needed per requirements
- Architecture: HIGH — service/controller/repository pattern follows established Phase 1-2 patterns; grace window mechanism verified across multiple OAuth provider implementations
- Pitfalls: HIGH — Pitfalls 1, 2, 6, 7 verified against official API docs or Spring framework behavior; Pitfall 4 (grace window response strategy) is a design decision with multiple valid answers, documented as Open Question 1

**Research date:** 2026-03-01
**Valid until:** 2026-04-01 (Google API client is stable; Spring Boot 4.0.3 patterns are stable; refresh token rotation patterns are well-established)
