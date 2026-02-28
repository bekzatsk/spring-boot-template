# Architecture Research

**Domain:** Spring Boot 4 + Spring Security 7 JWT authentication API (Kotlin)
**Researched:** 2026-03-01
**Confidence:** HIGH (Spring Security 7 docs + multiple verified implementations)

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         HTTP Clients                                │
│              (iOS / Android / Web SPA)                              │
└────────────────────────────┬────────────────────────────────────────┘
                             │  HTTPS
┌────────────────────────────▼────────────────────────────────────────┐
│                    Spring Security Filter Chain                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  CorsFilter → JwtAuthenticationFilter → (rest of chain)      │  │
│  │                        ↓                                      │  │
│  │              SecurityContextHolder                            │  │
│  └──────────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────────┤
│                         Controller Layer                            │
│  ┌──────────────────────┐  ┌──────────────────────────────────┐    │
│  │  AuthController      │  │  UserController                  │    │
│  │  POST /auth/google   │  │  GET /users/me                   │    │
│  │  POST /auth/apple    │  └──────────────────────────────────┘    │
│  │  POST /auth/refresh  │                                           │
│  │  POST /auth/revoke   │                                           │
│  └──────────┬───────────┘                                           │
├─────────────┼───────────────────────────────────────────────────────┤
│             │               Service Layer                           │
│  ┌──────────▼──────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  AuthService        │  │  JwtService  │  │  UserService     │  │
│  │  (Google/Apple ID   │  │  (encode,    │  │  (find/create    │  │
│  │   token exchange,   │  │   decode,    │  │   user record)   │  │
│  │   token issuance)   │  │   validate)  │  │                  │  │
│  └──────────┬──────────┘  └──────┬───────┘  └────────┬─────────┘  │
├─────────────┼────────────────────┼────────────────────┼────────────┤
│             │       External Verification              │            │
│  ┌──────────▼──────────┐  ┌──────▼───────┐  ┌────────▼─────────┐  │
│  │  GoogleVerifier     │  │  JWKSource   │  │  UserRepository  │  │
│  │  (google-api-client │  │  (RSA .jks   │  │  RefreshToken    │  │
│  │   GoogleIdToken     │  │   keystore)  │  │  Repository      │  │
│  │   Verifier)         │  └──────────────┘  └────────┬─────────┘  │
│  │  AppleVerifier      │                              │            │
│  │  (fetch JWK from    │                              │            │
│  │  appleid.apple.com/ │                    ┌─────────▼──────────┐ │
│  │  auth/keys, nimbus  │                    │    PostgreSQL       │ │
│  │  validate)          │                    │  users table        │ │
│  └─────────────────────┘                    │  refresh_tokens     │ │
│                                             └────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `JwtAuthenticationFilter` | Intercepts every request, extracts Bearer token, validates it, populates `SecurityContextHolder` | `OncePerRequestFilter` subclass — reads `Authorization` header, calls `JwtService.validate()`, sets `UsernamePasswordAuthenticationToken` |
| `SecurityConfig` | Declares the `SecurityFilterChain` bean; disables sessions/CSRF/form-login; registers `JwtAuthenticationFilter`; defines permit-all vs authenticated path rules; exposes `JwtEncoder`/`JwtDecoder`/`JWKSource` beans | `@Configuration @EnableWebSecurity` class using Kotlin DSL (`http { ... }`) |
| `JwtService` | Encodes access tokens (RS256) and decodes/validates incoming Bearer tokens | Uses Spring Authorization Server's `NimbusJwtEncoder` + `JWKSource`; also `NimbusJwtDecoder` for validation |
| `AuthController` | Exposes `/api/v1/auth/*` endpoints; delegates entirely to `AuthService` | `@RestController` — thin; no business logic |
| `AuthService` | Orchestrates the auth flow: verify ID token with provider, upsert user, issue access + refresh tokens | Calls `GoogleVerifier` or `AppleVerifier`, then `UserService`, then `JwtService`, then `RefreshTokenService` |
| `GoogleVerifier` | Validates a Google ID token using Google's public keys | Wraps `GoogleIdTokenVerifier` from `google-api-client`; returns parsed claims (email, name, picture, sub) |
| `AppleVerifier` | Validates an Apple ID token by fetching Apple's JWK set and verifying signature + claims | HTTP GET to `https://appleid.apple.com/auth/keys`, parse JWK set, find key by `kid`, verify RS256 signature with Nimbus |
| `UserService` | Finds an existing user by `(provider, providerId)` or creates a new one | Calls `UserRepository`; returns `User` entity |
| `RefreshTokenService` | Issues opaque refresh tokens, rotates them on each use, detects reuse | Creates UUID tokens, persists with `used=false`; on refresh, marks old token `used=true`, issues new one; reuse detected when `used=true` token presented — revokes all user tokens |
| `UserController` | Exposes `/api/v1/users/me`; reads authenticated principal from `SecurityContextHolder` | `@RestController`; injects `@AuthenticationPrincipal` |
| `UserRepository` | Spring Data JPA repository for `User` entity | `JpaRepository<User, UUID>` with `findByEmail`, `findByProviderAndProviderId` |
| `RefreshTokenRepository` | Spring Data JPA repository for `RefreshToken` entity | `JpaRepository<RefreshToken, UUID>` with `findByToken`, `deleteAllByUser` |
| `GlobalExceptionHandler` | Converts domain exceptions to consistent `{ error, message, status }` JSON responses | `@ControllerAdvice` with `@ExceptionHandler` methods |
| `RsaKeyConfig` | Loads RSA key pair from `.jks` keystore at startup; exposes `JWKSource` bean | Reads `security.keystore.*` from `application.yml`; falls back to runtime-generated key in dev |

## Recommended Project Structure

```
src/main/kotlin/kz/innlab/template/
├── TemplateApplication.kt          # Entry point
│
├── authentication/                 # Auth domain (vertical slice)
│   ├── controller/
│   │   └── AuthController.kt       # POST /auth/google, /apple, /refresh, /revoke
│   ├── service/
│   │   ├── AuthService.kt          # Orchestrates ID token → JWT issuance
│   │   ├── JwtService.kt           # JWT encode / decode / validate
│   │   └── RefreshTokenService.kt  # Opaque refresh token lifecycle
│   ├── model/
│   │   └── RefreshToken.kt         # @Entity: token, userId, used, expiresAt
│   ├── repository/
│   │   └── RefreshTokenRepository.kt
│   ├── dto/
│   │   ├── AuthRequest.kt          # { idToken: String }
│   │   ├── AuthResponse.kt         # { accessToken, refreshToken, expiresIn }
│   │   └── RefreshRequest.kt       # { refreshToken: String }
│   └── provider/
│       ├── GoogleIdTokenVerifier.kt  # Wraps google-api-client verifier
│       └── AppleIdTokenVerifier.kt   # Fetches JWK, validates with Nimbus
│
├── user/                           # User domain (vertical slice)
│   ├── controller/
│   │   └── UserController.kt       # GET /users/me
│   ├── service/
│   │   └── UserService.kt          # Upsert user, find by provider
│   ├── model/
│   │   └── User.kt                 # @Entity: id, email, name, picture, provider, providerId, roles
│   ├── repository/
│   │   └── UserRepository.kt
│   └── dto/
│       └── UserResponse.kt         # { id, email, name, picture }
│
└── config/                         # Cross-cutting configuration
    ├── SecurityConfig.kt           # SecurityFilterChain, CORS, permit/deny rules
    ├── RsaKeyConfig.kt             # JWKSource, JwtEncoder, JwtDecoder beans
    ├── JwtAuthenticationFilter.kt  # OncePerRequestFilter for Bearer validation
    └── GlobalExceptionHandler.kt   # @ControllerAdvice error mapping
```

### Structure Rationale

- **authentication/ and user/** are vertical slices — all layers for each domain are co-located. This is the explicitly chosen layout per `PROJECT.md` (`domain-based package layout`). Each slice is self-contained and can be extracted or renamed independently when using the template.
- **config/** holds cross-cutting infrastructure that is not domain-specific: security wiring, RSA keys, exception handling. These have no business logic and exist only to wire the application.
- Controllers are thin (no logic). Services own all business logic. Repositories own all persistence queries. This follows the standard Spring MVC layering within each domain slice.

## Architectural Patterns

### Pattern 1: OncePerRequestFilter for JWT Validation

**What:** A filter that runs once per HTTP request, extracts the `Authorization: Bearer <token>` header, validates the JWT signature and expiry, loads user authorities, and populates `SecurityContextHolder` — before any controller executes.
**When to use:** Every protected request path. This is the standard Spring Security 7 pattern for stateless JWT APIs. Does NOT call the database on every request (authorities are baked into the token).
**Trade-offs:** Pros: stateless, horizontally scalable, no session store needed. Cons: cannot revoke access tokens mid-lifetime — design access tokens with short expiry (15 min) to contain blast radius.

**Example:**
```kotlin
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response)
            return
        }
        val token = header.removePrefix("Bearer ")
        val authentication = jwtService.toAuthentication(token)  // returns UsernamePasswordAuthenticationToken
        SecurityContextHolder.getContext().authentication = authentication
        chain.doFilter(request, response)
    }
}
```

### Pattern 2: SecurityFilterChain with Kotlin DSL (Stateless)

**What:** The single `SecurityFilterChain` bean that declares all security rules: CSRF off, sessions stateless, CORS configured, public vs authenticated endpoints, and the JWT filter registered before `UsernamePasswordAuthenticationFilter`.
**When to use:** All Spring Boot 3+ / 4 applications — `WebSecurityConfigurerAdapter` is removed.
**Trade-offs:** Kotlin DSL is more readable than Java; requires `import org.springframework.security.config.annotation.web.invoke` to unlock the DSL.

**Example:**
```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val corsConfigurationSource: CorsConfigurationSource
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            cors { configurationSource = corsConfigurationSource }
            authorizeHttpRequests {
                authorize("/api/v1/auth/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtAuthenticationFilter)
        }
        return http.build()
    }
}
```

### Pattern 3: Refresh Token Rotation with Reuse Detection

**What:** Each refresh token is opaque (UUID), stored in the database with a `used` flag. When a client calls `/auth/refresh`, the old token is marked `used=true` and a new token is issued. If a token with `used=true` is ever presented, ALL refresh tokens for that user are revoked immediately — the user must re-authenticate.
**When to use:** Any stateful refresh token system. This is the Auth0 "token family" approach — it detects stolen tokens because the legitimate client and the attacker cannot both use the same one-time token without triggering revocation.
**Trade-offs:** Requires database access on every refresh call (not access token validation). Single-device workflows are straightforward; multi-device requires issuing separate token families per device session.

**Example (entity and rotation logic):**
```kotlin
@Entity
data class RefreshToken(
    @Id val id: UUID = UUID.randomUUID(),
    val token: String = UUID.randomUUID().toString(),
    @ManyToOne val user: User,
    val used: Boolean = false,
    val expiresAt: Instant
)

// In RefreshTokenService:
fun rotate(rawToken: String): Pair<String, String> {
    val existing = repo.findByToken(rawToken)
        ?: throw InvalidTokenException("Unknown refresh token")
    if (existing.used) {
        // Reuse detected — revoke entire user's token set
        repo.deleteAllByUser(existing.user)
        throw TokenReuseException("Refresh token reuse detected — all sessions revoked")
    }
    repo.save(existing.copy(used = true))
    val newRefresh = repo.save(RefreshToken(user = existing.user, expiresAt = Instant.now().plus(30, DAYS)))
    val newAccess = jwtService.issueAccessToken(existing.user)
    return newAccess to newRefresh.token
}
```

### Pattern 4: Provider-Specific ID Token Verification

**What:** Two dedicated verifier components that each handle one external provider. The `AuthService` selects the correct verifier based on the endpoint called (`/auth/google` vs `/auth/apple`). Both return a normalized `ProviderIdentity(email, name, picture, providerId)` value object.
**When to use:** Any mobile-first backend accepting external ID tokens from multiple providers. Keeps provider-specific SDK dependencies isolated.
**Trade-offs:** Google uses `google-api-client` which handles JWK fetching internally; Apple requires manual JWK fetch from `https://appleid.apple.com/auth/keys` and Nimbus JWT validation. Apple's approach is slightly more brittle (network call at verification time — cache the JWK set).

## Data Flow

### Flow 1: Google/Apple Sign-In (Initial Authentication)

```
Mobile Client
    │  POST /api/v1/auth/google (or /apple)
    │  Body: { idToken: "eyJ..." }
    ↓
CorsFilter → JwtAuthenticationFilter (no Bearer header → passes through)
    ↓
AuthController.authenticate(idToken)
    ↓
AuthService.authenticate(idToken, provider)
    ↓
GoogleVerifier.verify(idToken)           [or AppleVerifier]
    │  → Calls Google's verification endpoint / Apple's JWK endpoint
    │  → Validates signature, expiry, audience
    │  → Returns: email, name, picture, providerId
    ↓
UserService.findOrCreate(email, provider, providerId, name, picture)
    │  → UserRepository.findByProviderAndProviderId()
    │  → If not found: UserRepository.save(User(...))
    ↓
JwtService.issueAccessToken(user)
    │  → Builds JwtClaimsSet (sub=userId, email, roles, iat, exp=+15min)
    │  → Signs with RSA private key via NimbusJwtEncoder + JWKSource
    │  → Returns signed JWT string
    ↓
RefreshTokenService.issue(user)
    │  → Creates RefreshToken(user, UUID, expiresAt=+30days, used=false)
    │  → Saves to DB
    ↓
AuthController returns AuthResponse { accessToken, refreshToken, expiresIn: 900 }
```

### Flow 2: Protected Request (Authenticated User)

```
Mobile Client
    │  GET /api/v1/users/me
    │  Header: Authorization: Bearer eyJ...
    ↓
JwtAuthenticationFilter.doFilterInternal()
    │  → Extracts token from header
    │  → JwtService.decode(token)
    │     → NimbusJwtDecoder validates RS256 signature, exp claim
    │     → Extracts sub (userId), roles
    │  → Creates UsernamePasswordAuthenticationToken with authorities
    │  → SecurityContextHolder.getContext().authentication = auth
    ↓
SecurityFilterChain authorizeHttpRequests (requires authenticated)
    ↓
UserController.getMe(@AuthenticationPrincipal userId: UUID)
    ↓
UserService.findById(userId)
    ↓
UserRepository.findById(userId)
    ↓
Returns UserResponse { id, email, name, picture }
```

### Flow 3: Token Refresh

```
Mobile Client
    │  POST /api/v1/auth/refresh
    │  Body: { refreshToken: "some-uuid-string" }
    ↓
JwtAuthenticationFilter (no Bearer → passes through)
    ↓
AuthController.refresh(refreshToken)
    ↓
RefreshTokenService.rotate(refreshToken)
    │  → RefreshTokenRepository.findByToken(refreshToken)
    │  → IF token.used == true:
    │       RefreshTokenRepository.deleteAllByUser(token.user)
    │       throw TokenReuseException  ← 401 response
    │  → IF token.expiresAt < now:
    │       throw TokenExpiredException  ← 401 response
    │  → RefreshTokenRepository.save(token.copy(used = true))
    │  → Issues new RefreshToken (new UUID, expiresAt = +30 days)
    │  → JwtService.issueAccessToken(token.user)
    ↓
Returns AuthResponse { accessToken, refreshToken, expiresIn: 900 }
```

### Flow 4: Token Revoke (Logout)

```
Mobile Client
    │  POST /api/v1/auth/revoke
    │  Header: Authorization: Bearer eyJ...
    │  Body: { refreshToken: "some-uuid-string" }  [optional]
    ↓
JwtAuthenticationFilter (validates Bearer, sets SecurityContext)
    ↓
AuthController.revoke(@AuthenticationPrincipal userId, refreshToken)
    ↓
RefreshTokenService.revokeAll(userId)
    │  → RefreshTokenRepository.deleteAllByUser(userId)
    ↓
Returns 204 No Content
```

## Build Order (Component Dependencies)

The components form a dependency graph. Build in this order to avoid blocking:

```
Stage 1 — Foundation (no dependencies on other custom components)
  ├── User entity (model only)
  ├── RefreshToken entity (model only — depends on User)
  ├── UserRepository
  ├── RefreshTokenRepository
  └── RsaKeyConfig (JWKSource, JwtEncoder, JwtDecoder beans)

Stage 2 — Core Services (depend on Stage 1)
  ├── JwtService (depends on JwtEncoder, JwtDecoder from RsaKeyConfig)
  ├── UserService (depends on UserRepository)
  ├── GoogleVerifier (depends on google-api-client only)
  └── AppleVerifier (depends on Nimbus JWT library, HTTP client)

Stage 3 — Auth Orchestration (depends on Stage 1 + 2)
  ├── RefreshTokenService (depends on RefreshTokenRepository, JwtService)
  └── AuthService (depends on UserService, JwtService, RefreshTokenService, GoogleVerifier, AppleVerifier)

Stage 4 — Security Wiring (depends on Stage 2)
  ├── JwtAuthenticationFilter (depends on JwtService)
  └── SecurityConfig (depends on JwtAuthenticationFilter, CORS config)

Stage 5 — API Layer (depends on Stage 3 + 4)
  ├── AuthController (depends on AuthService)
  ├── UserController (depends on UserService)
  └── GlobalExceptionHandler (depends on exception hierarchy)

Stage 6 — Configuration Files (no code dependencies)
  ├── application.yml (dev/prod profiles, env var placeholders)
  ├── .env.example
  └── docker-compose.yml (PostgreSQL)
```

**Critical dependency:** `SecurityConfig` MUST be wired after `JwtAuthenticationFilter` — Spring will fail to start if the filter bean is missing when the security chain is assembled.

**Critical dependency:** `RsaKeyConfig` and the `.jks` keystore MUST exist before `JwtService` can be instantiated — the `JWKSource` bean depends on the RSA key pair being loaded.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-10k users | Current monolith is fine. Single PostgreSQL. Access tokens are stateless — no scaling concern. Refresh token DB queries are rare (1 per 15 min per user). |
| 10k-100k users | Add connection pool tuning (HikariCP). Index `refresh_tokens.token` and `users.email`. Consider caching the Apple JWK set (it changes rarely — 60min TTL is safe). |
| 100k+ users | Access token validation is already stateless (no DB hit) — handles high load naturally. Refresh token DB may become a bottleneck: consider partitioning by `user_id`. Virtual Threads (Java 25) handle concurrent I/O without thread pool exhaustion. |

### Scaling Priorities

1. **First bottleneck:** `refresh_tokens` table under heavy concurrent refresh calls — add DB index on `token` column (unique) and `user_id` foreign key.
2. **Second bottleneck:** Apple JWK fetch latency — cache the JWK set in-process (Caffeine or similar) with a 60-minute TTL to avoid external HTTP on every Apple auth call.

## Anti-Patterns

### Anti-Pattern 1: Loading User from DB on Every Access Token Request

**What people do:** In `JwtAuthenticationFilter`, call `UserRepository.findById(userId)` on every request to get fresh user details before setting `SecurityContextHolder`.
**Why it's wrong:** Turns a stateless, O(1) token validation into a DB read on every API call. At 1000 req/s, this is 1000 extra DB queries/second — defeats the purpose of JWTs.
**Do this instead:** Bake `userId`, `email`, and `roles` into the JWT claims. The filter reads claims from the token, constructs the `Authentication` object from claims alone. Only load from DB when you actually need fresh data (e.g., `GET /users/me`).

### Anti-Pattern 2: Using `WebSecurityConfigurerAdapter`

**What people do:** Extend `WebSecurityConfigurerAdapter` and override `configure(HttpSecurity)`.
**Why it's wrong:** `WebSecurityConfigurerAdapter` was deprecated in Spring Security 5.7 and removed in Spring Security 6+. Spring Security 7 (shipped with Spring Boot 4) has no trace of it.
**Do this instead:** Declare `SecurityFilterChain` as a `@Bean` using the Kotlin DSL or Java lambda-style API.

### Anti-Pattern 3: Storing Access Tokens in the Database

**What people do:** Persist access tokens in a `tokens` table to support revocation.
**Why it's wrong:** Requires a DB lookup on every request — kills statelessness. Also a maintenance burden (cleanup job needed).
**Do this instead:** Keep access tokens short-lived (15 min). Use refresh token rotation + reuse detection for security. For immediate revocation, use a small in-memory token blacklist seeded at logout, evicted at access token expiry (15 min). In this template, revocation is handled at the refresh token layer — the access token expires naturally.

### Anti-Pattern 4: Hardcoding the RSA Private Key

**What people do:** Paste a PEM-encoded private key string directly in `application.yml` or in source code.
**Why it's wrong:** Key leaks via git history, logs, or environment dumps. Violates the 12-factor app secret management rule.
**Do this instead:** Use a `.jks` keystore file loaded via filesystem path, with keystore password and alias in environment variables (`${KEYSTORE_PASSWORD}`). The `.jks` file itself is excluded from git. Provide a helper script to generate it.

### Anti-Pattern 5: Trusting Apple/Google ID Token Claims Without Signature Verification

**What people do:** Base64-decode the JWT payload and read the `email` and `sub` claims without verifying the RS256 signature.
**Why it's wrong:** Anyone can craft a JWT with any claims if the signature is not checked. This bypasses the entire auth model.
**Do this instead:** Always verify the signature against the provider's published public keys. For Google: use `GoogleIdTokenVerifier` which fetches and caches Google's JWK set. For Apple: fetch `https://appleid.apple.com/auth/keys`, match by `kid` header claim, and verify with Nimbus.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Google Identity | `GoogleIdTokenVerifier` (google-api-client library) | Library handles JWK caching internally. Requires `audience` parameter set to your Google Client ID. |
| Apple Identity | Manual JWK fetch from `https://appleid.apple.com/auth/keys` + Nimbus JWT | Cache the JWK set for 60 min. Apple tokens use RS256. Validate `iss=https://appleid.apple.com`, `aud=<your-apple-bundle-id>`. |
| PostgreSQL | Spring Data JPA + HikariCP | Two tables: `users`, `refresh_tokens`. Hibernate auto-DDL usable in dev; use Flyway/Liquibase for prod migrations. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `authentication/` ↔ `user/` | Direct service call — `AuthService` calls `UserService` | Acceptable for monolith. If extracted to separate service later, replace with HTTP/Kafka call. |
| `config/` ↔ `authentication/` | Spring bean injection — `JwtAuthenticationFilter` injected into `SecurityConfig` | One-way: config knows about auth filter, auth filter knows about JwtService. |
| `JwtAuthenticationFilter` → Spring Security | Sets `SecurityContextHolder.getContext().authentication` | Standard Spring Security contract. Controllers access via `@AuthenticationPrincipal`. |

## Sources

- [Spring Security Kotlin DSL Configuration](https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html) — HIGH confidence (official Spring docs)
- [OAuth2 Resource Server JWT — Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — HIGH confidence (official Spring docs)
- [Spring Security JWT Architecture — bootify.io](https://bootify.io/spring-security/rest-api-spring-security-with-jwt.html) — MEDIUM confidence (verified pattern matches official docs)
- [Stateless JWT Authentication with Spring Security — skryvets.com (Dec 2024)](https://skryvets.com/blog/2024/12/15/spring-auth-jwt/) — MEDIUM confidence (recent, multi-component example)
- [Refresh Token Rotation — Auth0 Docs](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation) — HIGH confidence (canonical definition of token family revocation algorithm)
- [Spring Boot 4 Security 7 JWT — GitHub MossaabFrifita](https://github.com/MossaabFrifita/spring-boot-4-security-7-jwt) — MEDIUM confidence (working example, matches research)
- [Managing JWT Refresh Tokens — Java Code Geeks (Dec 2024)](https://www.javacodegeeks.com/2024/12/managing-jwt-refresh-tokens-in-spring-security-a-complete-guide.html) — MEDIUM confidence
- [Apple JWK public key fetch — Apple Developer Documentation](https://developer.apple.com/documentation/signinwithapplerestapi/fetch_apple_s_public_key_for_verifying_token_signature) — HIGH confidence (official Apple docs)
- [Spring Tips: Kotlin and Spring Security — spring.io blog](https://spring.io/blog/2020/03/04/spring-tips-kotlin-and-spring-security/) — MEDIUM confidence (official blog, patterns still current)

---
*Architecture research for: Spring Boot 4 + Spring Security 7 + JWT auth (Kotlin)*
*Researched: 2026-03-01*
