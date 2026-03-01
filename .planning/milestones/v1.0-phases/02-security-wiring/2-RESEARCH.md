# Phase 2: Security Wiring - Research

**Researched:** 2026-03-01
**Domain:** Spring Security 7 / OAuth2 Resource Server / JWT Bearer / CORS / Jakarta Validation / Error Handling
**Confidence:** HIGH (all core patterns verified via official Spring Security docs + Context7; Spring Boot 4 / Spring Security 7 stable)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SECU-01 | Spring Security configured as stateless (STATELESS session policy, CSRF disabled) | Confirmed: `sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }` + `csrf { disable() }` in Kotlin DSL SecurityFilterChain. Must also disable `httpBasic`, `formLogin`, `logout`. |
| SECU-02 | JwtAuthenticationFilter validates Bearer token and sets SecurityContext from JWT claims only (no DB hit) | Confirmed: `oauth2ResourceServer { jwt { } }` wires `BearerTokenAuthenticationFilter` + `JwtAuthenticationProvider` automatically. SecurityContext is populated from `Jwt` claims via `JwtAuthenticationConverter` — no database is consulted. The existing `JwtDecoder` bean from Phase 1 is auto-wired. |
| SECU-03 | All /api/** endpoints require authentication except /api/v1/auth/** | Confirmed: `authorizeHttpRequests { authorize("/api/v1/auth/**", permitAll); authorize("/api/**", authenticated) }` in SecurityFilterChain DSL. |
| SECU-04 | CORS configured inside SecurityFilterChain so OPTIONS pre-flight requests pass correctly | Confirmed: `cors { configurationSource = corsConfigurationSource() }` inside SecurityFilterChain. Spring Security places CorsFilter before authentication filters; OPTIONS preflight is handled before JWT validation. |
| SECU-05 | CORS allowed origins are configurable (not hardcoded) | Confirmed: `@Value("\${app.cors.allowed-origins}")` injected as comma-separated string into `SecurityConfig`, split and passed to `CorsConfiguration.setAllowedOrigins()`. Or use `@ConfigurationProperties` for typed list binding. |
| SECU-07 | Consistent JSON error responses: `{ error, message, status }` via @RestControllerAdvice | Confirmed: Two-layer approach required: (1) Custom `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) in `exceptionHandling` of SecurityFilterChain — security exceptions never reach `@ControllerAdvice`; (2) `@RestControllerAdvice` handles controller-layer exceptions: `MethodArgumentNotValidException` (400), `HandlerMethodValidationException` (400), and general exceptions. |
| SECU-08 | Input validation with Jakarta Validation on auth request DTOs | Confirmed: `spring-boot-starter-validation` already on classpath (Phase 1). Add `@NotBlank`, `@Email` etc. to DTO fields. Add `@Valid` on `@RequestBody` parameter in controller. `MethodArgumentNotValidException` is thrown on failure (vs `HandlerMethodValidationException` for `@Constraint` on method parameters — use `@Valid` on body to stay consistent). |
| TOKN-01 | JWT access tokens are RS256-signed with 15-minute expiry via NimbusJwtEncoder + JWKSource | Confirmed: `NimbusJwtEncoder` and `JWKSource` beans already wired in Phase 1 (`RsaKeyConfig.kt`). Token service uses `JwtClaimsSet.builder().expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))` + `JwtEncoderParameters.from(claims)` + `encoder.encode(params).tokenValue`. RS256 is used automatically since `JWKSource` contains an RSA key — Nimbus picks RS256 by default. |
</phase_requirements>

---

## Summary

Phase 2 configures the Spring Security filter chain as a stateless OAuth2 resource server. All the JWT signing infrastructure (JWKSource, JwtEncoder, JwtDecoder) was created in Phase 1's `RsaKeyConfig.kt` — Phase 2 wires it into the security filter chain so that Bearer tokens are validated automatically on every request. No custom filter class needs to be written; Spring Security's `oauth2ResourceServer { jwt { } }` DSL handles the `BearerTokenAuthenticationFilter` and `JwtAuthenticationProvider` pipeline automatically.

The two critical non-trivial areas are: (1) error handling — Spring Security exceptions (401/403) are intercepted at the filter level by `ExceptionTranslationFilter` and never reach `@ControllerAdvice`, requiring dedicated `AuthenticationEntryPoint` and `AccessDeniedHandler` implementations that write JSON directly to the response; (2) CORS — CORS must be configured inside `SecurityFilterChain` (not via `@CrossOrigin` or `WebMvcConfigurer`) so that `CorsFilter` executes before authentication, allowing OPTIONS preflight requests to pass without a Bearer token.

The `JwtAuthenticationConverter` must be configured to read roles from a custom claim (the token minted in Phase 3+ will include a `roles` claim) and prefix them with `ROLE_`. The `SecurityConfig` class will live in `config/` and the JWT token service (for minting, not validating) in `authentication/`. A stub `GET /api/v1/users/me` controller is needed to satisfy success criterion 1 and 3 without requiring the Phase 3 user service.

**Primary recommendation:** Use `oauth2ResourceServer { jwt { } }` DSL (not a custom filter) with the `JwtDecoder` bean from Phase 1 auto-wired, and implement custom `AuthenticationEntryPoint` + `AccessDeniedHandler` as the authoritative JSON error writers for all 401/403 scenarios. Use `@RestControllerAdvice` only for controller-layer exceptions (400 validation, 500 unexpected).

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-security` | managed by Boot 4.0.3 BOM | Spring Security 7 — SecurityFilterChain, BearerTokenAuthenticationFilter, ExceptionTranslationFilter | Already on classpath (Phase 1). Official Spring Security 7 filter chain. |
| `spring-boot-starter-oauth2-authorization-server` | managed by Boot 4.0.3 BOM | Provides `NimbusJwtEncoder`, `NimbusJwtDecoder`, `JWKSource`, `JwtAuthenticationConverter` | Already on classpath (Phase 1). Used narrowly: JWT infra beans only — auto-config excluded. |
| `spring-boot-starter-validation` | managed by Boot 4.0.3 BOM | Jakarta Validation 3.1 / Hibernate Validator for DTO `@NotBlank`, `@Email`, etc. | Already on classpath (Phase 1). Provides `@Valid` support and `MethodArgumentNotValidException`. |
| `spring-boot-starter-webmvc` | managed by Boot 4.0.3 BOM | Spring MVC dispatcher, `@RestController`, `@RestControllerAdvice` | Already on classpath (Phase 1). |

No new Maven dependencies are needed for Phase 2 — all required libraries are already on the classpath from Phase 1.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.nimbus-jose-jwt` (transitive) | via auth-server starter | `JwtClaimsSet`, `JwtEncoderParameters`, `JwsHeader` for building JWT claims | Used in `JwtTokenService` to mint access tokens (TOKN-01). |
| `tools.jackson.module:jackson-module-kotlin` (already present) | managed | Serialize `ErrorResponse` data class to JSON in `AuthenticationEntryPoint` and `AccessDeniedHandler` | Already present — Jackson 3 auto-configured. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `oauth2ResourceServer { jwt { } }` DSL (auto-wires BearerTokenAuthenticationFilter) | Manual `OncePerRequestFilter` subclass with `JwtDecoder.decode()` | Custom filter gives more control but requires manual SecurityContext population, error response writing, and filter chain positioning. Spring's built-in path handles all edge cases correctly. |
| `AuthenticationEntryPoint` for 401 JSON | Delegating to `HandlerExceptionResolver` from within `AuthenticationEntryPoint` | Delegation to HandlerExceptionResolver works but adds indirection; direct write is simpler for a small fixed response shape. |
| `JwtGrantedAuthoritiesConverter` (configurable claim name) | Custom `Converter<Jwt, AbstractAuthenticationToken>` | Both work. `JwtGrantedAuthoritiesConverter` + `setAuthoritiesClaimName("roles")` is simpler for standard cases. Custom `Converter` is needed only if authority mapping logic requires non-trivial computation. |
| `@Value` for CORS origins as comma-separated string | `@ConfigurationProperties` with typed `List<String>` | `@ConfigurationProperties` gives type safety and IDE completion. `@Value` with `.split(",")` is simpler for a single string property. Either works; prefer `@ConfigurationProperties` for the CORS sub-config. |

---

## Architecture Patterns

### Recommended Project Structure

```
src/main/kotlin/kz/innlab/template/
├── config/
│   ├── RsaKeyConfig.kt              # Phase 1: JWKSource, JwtEncoder, JwtDecoder (DONE)
│   ├── SecurityConfig.kt            # Phase 2: SecurityFilterChain, CORS, error handlers
│   └── CorsProperties.kt            # Phase 2: @ConfigurationProperties for CORS origins
├── authentication/
│   ├── RefreshToken.kt              # Phase 1: entity (DONE)
│   ├── RefreshTokenRepository.kt    # Phase 1: repository (DONE)
│   ├── JwtTokenService.kt           # Phase 2: token minting (JwtEncoder → TOKN-01)
│   └── error/
│       ├── ApiAuthenticationEntryPoint.kt   # Phase 2: 401 JSON response
│       └── ApiAccessDeniedHandler.kt        # Phase 2: 403 JSON response
├── shared/
│   └── error/
│       ├── ErrorResponse.kt          # Phase 2: data class {error, message, status}
│       └── GlobalExceptionHandler.kt # Phase 2: @RestControllerAdvice (400, 500)
└── user/
    └── UserController.kt            # Phase 2: stub GET /api/v1/users/me (success criterion)
```

Note: `shared/error/` is a new package — follows INFR-06's domain-based layout by putting cross-cutting error types in `shared/`. The `authentication/error/` sub-package holds security-layer handlers (not controller-layer).

### Pattern 1: SecurityFilterChain (Stateless OAuth2 Resource Server)

**What:** Single `SecurityFilterChain` bean that configures stateless JWT authentication for all `/api/**` endpoints with CORS, custom error handlers, and explicit permit for auth endpoints.
**When to use:** This is the sole security configuration. The `OAuth2AuthorizationServerAutoConfiguration` is already excluded in `TemplateApplication.kt` (Phase 1 decision).

```kotlin
// Source: https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html
//         https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
import org.springframework.security.config.annotation.web.invoke  // REQUIRED import for Kotlin DSL

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val corsProperties: CorsProperties,
    private val jwtDecoder: JwtDecoder,
    private val authenticationEntryPoint: ApiAuthenticationEntryPoint,
    private val accessDeniedHandler: ApiAccessDeniedHandler
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }

            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }

            cors {
                configurationSource = corsConfigurationSource()
            }

            authorizeHttpRequests {
                authorize(HttpMethod.OPTIONS, "/**", permitAll)     // CORS preflight
                authorize("/api/v1/auth/**", permitAll)              // Public auth endpoints
                authorize("/api/**", authenticated)                  // All others require auth
                authorize(anyRequest, permitAll)                     // Non-api paths (health, etc.)
            }

            oauth2ResourceServer {
                jwt {
                    jwtDecoder = this@SecurityConfig.jwtDecoder
                    jwtAuthenticationConverter = jwtAuthenticationConverter()
                }
                authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
                accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
            }

            exceptionHandling {
                authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
                accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
            }
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsProperties.allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")     // Our custom claim name
            setAuthorityPrefix("ROLE_")           // Maps "USER" → GrantedAuthority("ROLE_USER")
        }
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authoritiesConverter)
            setPrincipalClaimName("sub")          // JWT subject = user UUID string
        }
    }
}
```

### Pattern 2: CORS Properties (Configurable via Environment)

**What:** `@ConfigurationProperties` class binding `app.cors.allowed-origins` as `List<String>`.
**When to use:** SECU-05 — origins must be configurable without code changes.

```kotlin
// Source: Spring Boot externalized configuration docs
@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList()
)

// In SecurityConfig: @EnableConfigurationProperties(CorsProperties::class)
// Or: @ConfigurationPropertiesScan on the main class
```

In `application.yaml` (dev profile section):
```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:5173
```

In `.env.example` / prod profile via env var:
```bash
# Spring Boot maps APP_CORS_ALLOWED_ORIGINS=http://example.com,https://app.example.com
# to a comma-delimited list for List<String> binding
APP_CORS_ALLOWED_ORIGINS=https://app.example.com
```

### Pattern 3: Custom AuthenticationEntryPoint (401 JSON)

**What:** Security-layer handler that intercepts `AuthenticationException` (missing/invalid token) and writes `{ error, message, status }` JSON directly to `HttpServletResponse`. `@ControllerAdvice` does NOT intercept these — they are caught by `ExceptionTranslationFilter` before MVC dispatch.
**When to use:** SECU-07 — consistent 401 responses.

```kotlin
// Source: Spring Security docs + https://www.naiyerasif.com/post/2021/01/19/error-handling-for-spring-security-resource-server/
@Component
class ApiAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = ErrorResponse(
            error = "Unauthorized",
            message = "Authentication required",
            status = 401
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
```

### Pattern 4: Custom AccessDeniedHandler (403 JSON)

**What:** Security-layer handler for `AccessDeniedException` (authenticated but lacks authority).
**When to use:** SECU-07 — consistent 403 responses.

```kotlin
@Component
class ApiAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = ErrorResponse(
            error = "Forbidden",
            message = "Insufficient permissions",
            status = 403
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
```

### Pattern 5: ErrorResponse Shape + GlobalExceptionHandler

**What:** Shared `ErrorResponse` data class matching the `{ error, message, status }` contract. `@RestControllerAdvice` handles controller-layer exceptions (validation, unexpected errors). Security exceptions (401, 403) are handled by the Entry Point / Denied Handler above.
**When to use:** SECU-07 (consistent format), SECU-08 (400 validation).

```kotlin
// Shared error shape — used by all layers
data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)

// Controller-layer exception handler
@RestControllerAdvice
class GlobalExceptionHandler {

    // SECU-08: Jakarta Validation on @RequestBody with @Valid
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(
            ErrorResponse(error = "Bad Request", message = message, status = 400)
        )
    }

    // Spring Boot 3.2+ may throw this instead of MethodArgumentNotValidException
    // for @Constraint annotations on method parameters (not @Valid on @RequestBody)
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(ex: HandlerMethodValidationException): ResponseEntity<ErrorResponse> {
        val message = ex.allErrors.joinToString("; ") { it.defaultMessage ?: "Invalid value" }
        return ResponseEntity.badRequest().body(
            ErrorResponse(error = "Bad Request", message = message, status = 400)
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.internalServerError().body(
            ErrorResponse(error = "Internal Server Error", message = "An unexpected error occurred", status = 500)
        )
    }
}
```

### Pattern 6: JwtTokenService (TOKN-01 — Token Minting)

**What:** Service that uses the `NimbusJwtEncoder` bean (from Phase 1's `RsaKeyConfig`) to mint RS256 JWT access tokens with 15-minute expiry.
**When to use:** Called by auth controllers in Phase 3+ to issue tokens. Phase 2 can stub this or implement it now since the infrastructure is ready.

```kotlin
// Source: https://www.danvega.dev/blog/spring-security-jwt
@Service
class JwtTokenService(private val jwtEncoder: JwtEncoder) {

    fun generateAccessToken(userId: UUID, roles: Set<Role>): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("template-app")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))   // TOKN-01: 15-minute expiry
            .subject(userId.toString())                       // sub = user UUID
            .claim("roles", roles.map { it.name })           // roles claim for JwtAuthenticationConverter
            .build()
        // JwsHeader not required — NimbusJwtEncoder defaults to RS256 when JWKSource contains RSA key
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
```

### Pattern 7: Stub UserController (Success Criterion 1 and 3)

**What:** Minimal `GET /api/v1/users/me` controller that demonstrates JWT authentication passes without a DB hit. The actual user lookup is Phase 3 work.
**When to use:** Phase 2 success criteria require this endpoint to exist.

```kotlin
@RestController
@RequestMapping("/api/v1/users")
class UserController {

    @GetMapping("/me")
    fun getCurrentUser(authentication: Authentication): Map<String, Any?> {
        val jwt = authentication.principal as Jwt
        // In Phase 3, this will call UserService.findById(UUID.fromString(jwt.subject))
        // For now, return JWT claims to prove auth passed without DB hit
        return mapOf(
            "sub" to jwt.subject,
            "roles" to jwt.getClaimAsStringList("roles")
        )
    }
}
```

### Anti-Patterns to Avoid

- **Configuring CORS in `WebMvcConfigurer` only (not in `SecurityFilterChain`):** Spring Security processes requests before Spring MVC. If CORS is only in `WebMvcConfigurer.addCorsMappings()`, the `CorsFilter` isn't present in the security filter chain, and OPTIONS preflight requests will receive a 401/403 before MVC sees them. Always configure CORS inside `http.cors { }` in the `SecurityFilterChain`.

- **Relying on `@RestControllerAdvice` for 401/403 from security filters:** `ExceptionTranslationFilter` catches `AuthenticationException` and `AccessDeniedException` and invokes the `AuthenticationEntryPoint` / `AccessDeniedHandler` directly — MVC exception resolvers are never called. Custom JSON error responses for 401/403 MUST be written in the security handlers.

- **Using `authorizeRequests` (deprecated):** Spring Security 6+ uses `authorizeHttpRequests` (HttpSecurity method). The old `authorizeRequests` uses the deprecated `FilterSecurityInterceptor`. Always use `authorizeHttpRequests`.

- **Not importing `org.springframework.security.config.annotation.web.invoke` in Kotlin:** The Kotlin DSL extension function requires this explicit import. Without it, `http { }` does not resolve and the code fails to compile with a confusing error.

- **Using `BearerTokenAuthenticationEntryPoint` default (no JSON body):** The default `BearerTokenAuthenticationEntryPoint` writes only the `WWW-Authenticate` header per RFC 6750, with no JSON body. SECU-07 requires `{ error, message, status }` body. Always wire a custom `AuthenticationEntryPoint`.

- **Setting `.sessionManagement` without `.csrf { disable() }`:** Even with `STATELESS` session policy, CSRF protection creates an HTTP session by default if not explicitly disabled. Stateless JWT APIs must disable both.

- **Exposing `spring.security.user.*` credentials in the running app:** The current `application.yaml` has `spring.security.user.*` properties (auto-configured basic auth credentials). These must be removed or overridden to avoid Spring Security's default `UserDetailsService` conflicting with the custom JWT security chain.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT Bearer token extraction from `Authorization` header | Custom `OncePerRequestFilter` that reads `Authorization: Bearer ...` | `oauth2ResourceServer { jwt { } }` DSL → `BearerTokenAuthenticationFilter` | Spring's built-in filter handles edge cases: malformed header, empty string tokens, concurrent requests, SecurityContext cleanup |
| JWT signature verification | Custom RSA/HMAC verification logic | `NimbusJwtDecoder.withJwkSource(jwkSource).build()` (already wired in Phase 1) | Nimbus handles algorithm negotiation, clock skew, kid matching, key rotation |
| Role-to-GrantedAuthority mapping from JWT claims | Custom `UserDetailsService` that hits the DB on every request | `JwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("roles")` | Authority extraction from self-contained JWT claims — zero DB calls |
| SecurityContext cleanup after request | Manual SecurityContext clearing in filter `finally` block | Spring Security's `SecurityContextHolderFilter` (built-in) | Already handled by the framework; manual clearing breaks async/virtual thread scenarios |
| CORS preflight response headers | Custom `OPTIONS` handler in a controller | `http.cors { configurationSource = ... }` | CorsFilter handles preflight before authentication; controller is never reached for OPTIONS |

**Key insight:** Phase 2's job is wiring existing Spring Security infrastructure, not building new auth logic. Every item that seems like it needs a custom implementation (`JwtFilter`, `UserDetailsService`, `CorsFilter`) already has a well-tested Spring abstraction. Use the abstractions.

---

## Common Pitfalls

### Pitfall 1: Missing `import org.springframework.security.config.annotation.web.invoke`

**What goes wrong:** `http { ... }` Kotlin DSL block does not resolve. Compiler error: "None of the following candidates is applicable..."
**Why it happens:** The Kotlin DSL for `HttpSecurity` is implemented as an extension function in the `invoke` file. Kotlin requires the import to be explicit — IDE auto-import sometimes misses it.
**How to avoid:** Always add `import org.springframework.security.config.annotation.web.invoke` as the first import in `SecurityConfig.kt`.
**Warning signs:** `Unresolved reference: invoke` or `None of the following candidates is applicable` on the `http { }` block.

### Pitfall 2: CORS Not Working — OPTIONS Returns 401

**What goes wrong:** Browser preflight (`OPTIONS`) request to any `/api/**` endpoint returns 401 instead of 200.
**Why it happens:** CORS is configured in `WebMvcConfigurer` or `@CrossOrigin` only, so Spring Security processes the OPTIONS request before MVC sees it. Without a Bearer token, the request is rejected by `BearerTokenAuthenticationFilter`.
**How to avoid:** Configure CORS inside `SecurityFilterChain` via `http.cors { configurationSource = ... }`. Additionally add `authorize(HttpMethod.OPTIONS, "/**", permitAll)` as an explicit rule. The `cors { }` configuration puts `CorsFilter` before `BearerTokenAuthenticationFilter` in the chain.
**Warning signs:** OPTIONS returns 401 with `WWW-Authenticate: Bearer` header; GET requests to the same endpoint work fine with a valid token.

### Pitfall 3: spring.security.user.* Conflict

**What goes wrong:** Spring Boot auto-configures a default `UserDetailsService` (in-memory user with `spring.security.user.name/password`). This may conflict with the custom `SecurityFilterChain` if Spring Security 7 tries to wire the `UserDetailsService` into an unexpected authentication provider.
**Why it happens:** The `application.yaml` currently has `spring.security.user.*` properties in the common section — these were added in Phase 1 to satisfy the empty security context before Phase 2.
**How to avoid:** Remove `spring.security.user.*` from `application.yaml` in Phase 2, since the custom `SecurityFilterChain` with `oauth2ResourceServer` does not use `UserDetailsService`. Alternatively, exclude `UserDetailsServiceAutoConfiguration`.
**Warning signs:** App starts but logs `Using generated security password` or BASIC auth prompt appears on certain requests.

### Pitfall 4: `exceptionHandling` Must Be Wired in BOTH Places

**What goes wrong:** 401 returns `WWW-Authenticate: Bearer` header but no JSON body (default `BearerTokenAuthenticationEntryPoint` behavior), or 403 returns a redirect to a login page.
**Why it happens:** The custom `AuthenticationEntryPoint` and `AccessDeniedHandler` must be registered in **two** places: (1) `oauth2ResourceServer { authenticationEntryPoint = ...; accessDeniedHandler = ... }` for authentication failures within the Bearer token processing, AND (2) `exceptionHandling { authenticationEntryPoint = ...; accessDeniedHandler = ... }` for the outer `ExceptionTranslationFilter`. If only one is registered, some failure modes fall through to the default handlers.
**How to avoid:** Register in both DSL blocks as shown in Pattern 1 above.
**Warning signs:** Some 401 scenarios return JSON, others return only HTTP headers or redirect.

### Pitfall 5: JwtAuthenticationConverter Claim Mismatch

**What goes wrong:** `/api/v1/users/me` returns 403 even with a valid JWT, because the granted authorities don't match the authorization rule.
**Why it happens:** The default `JwtGrantedAuthoritiesConverter` reads the `scope` or `scp` claim and prefixes with `SCOPE_`. Our tokens will have a `roles` claim (e.g., `["USER"]`). Without configuring `setAuthoritiesClaimName("roles")` and `setAuthorityPrefix("ROLE_")`, the authorities collection is empty.
**How to avoid:** Configure `JwtGrantedAuthoritiesConverter` as shown in Pattern 1. In Phase 2, test with a manually crafted JWT that includes `"roles": ["USER"]` to confirm mapping.
**Warning signs:** JWT decodes successfully (200 claims visible in debug log) but authorization check returns 403; `authentication.authorities` is empty.

### Pitfall 6: NimbusJwtEncoder RS256 Algorithm Not Automatically Selected

**What goes wrong:** `JwtEncoder.encode()` throws `JOSEException: No key candidates found for JWS algorithm: RS256` or similar.
**Why it happens:** `NimbusJwtEncoder` selects the algorithm from the JWK set. If `JwsHeader` is not set and the `JWKSource` contains only one RSA key, Nimbus should default to RS256 — but if the JwsHeader is explicitly set to NONE or an unsupported algorithm, it fails.
**How to avoid:** Do not set `JwsHeader` explicitly in `JwtEncoderParameters.from(claims)` for the simple case. Nimbus picks RS256 automatically from the RSA `JWKSource`. If you need to specify: `JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)`.
**Warning signs:** `JOSEException`, `IllegalStateException`, or `IllegalArgumentException` from Nimbus during `jwtEncoder.encode()`.

### Pitfall 7: HandlerMethodValidationException vs MethodArgumentNotValidException

**What goes wrong:** Validation errors return 400 from Spring's default handler (verbose `violations` structure) instead of the `{ error, message, status }` format.
**Why it happens:** Since Spring Boot 3.2.2, `HandlerMethodValidationException` is thrown for `@Constraint` annotations on method parameters directly. `MethodArgumentNotValidException` is thrown only when `@Valid` is used on a `@RequestBody` parameter. If `@RestControllerAdvice` only handles `MethodArgumentNotValidException`, `HandlerMethodValidationException` falls through to the default handler.
**How to avoid:** Handle both in `GlobalExceptionHandler`. For auth request DTOs: annotate the `@RequestBody` parameter with `@Valid` to ensure `MethodArgumentNotValidException` is always thrown for body validation. Also add a handler for `HandlerMethodValidationException` as a safety net.
**Warning signs:** Some 400 responses have `{ error, message, status }` format; others have Spring's default `{ timestamp, status, errors, message, path }` format.

### Pitfall 8: SecurityConfig `@Bean` Methods and `open` keyword

**What goes wrong:** Spring cannot proxy the `SecurityConfig` bean to intercept bean method calls, so calling `corsConfigurationSource()` from within `securityFilterChain()` may not return the Spring-managed singleton.
**Why it happens:** In Kotlin, class methods are `final` by default. With the `spring` compiler plugin, classes annotated with `@Configuration` are opened, but only when `kotlin-maven-allopen` is configured with the `spring` plugin preset (which is already done). Verify the `all-open` plugin includes `@Configuration`.
**How to avoid:** The existing Phase 1 `all-open` configuration uses the `spring` plugin preset (in `compilerPlugins`), which automatically opens `@Configuration`. No extra action needed. If in doubt, mark `SecurityConfig` methods as `open`.
**Warning signs:** `BeanCurrentlyInCreationException` or multiple instances of `CorsConfigurationSource` in context.

---

## Code Examples

Verified patterns from official sources:

### Complete SecurityConfig.kt (Kotlin DSL)

```kotlin
// Source: https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html
//         https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
package kz.innlab.template.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke   // REQUIRED - Kotlin DSL
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import kz.innlab.template.authentication.error.ApiAuthenticationEntryPoint
import kz.innlab.template.authentication.error.ApiAccessDeniedHandler

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val corsProperties: CorsProperties,
    private val jwtDecoder: JwtDecoder,
    private val authenticationEntryPoint: ApiAuthenticationEntryPoint,
    private val accessDeniedHandler: ApiAccessDeniedHandler
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            cors {
                configurationSource = corsConfigurationSource()
            }
            authorizeHttpRequests {
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                authorize("/api/v1/auth/**", permitAll)
                authorize("/api/**", authenticated)
                authorize(anyRequest, permitAll)
            }
            oauth2ResourceServer {
                jwt {
                    jwtDecoder = this@SecurityConfig.jwtDecoder
                    jwtAuthenticationConverter = jwtAuthenticationConverter()
                }
                authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
                accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
            }
            exceptionHandling {
                authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
                accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
            }
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsProperties.allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = 3600L
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val grantedAuthoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")
            setAuthorityPrefix("ROLE_")
        }
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter)
            setPrincipalClaimName("sub")
        }
    }
}
```

### JwtTokenService.kt (Token Minting — TOKN-01)

```kotlin
// Source: https://www.danvega.dev/blog/spring-security-jwt
package kz.innlab.template.authentication

import kz.innlab.template.user.Role
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class JwtTokenService(private val jwtEncoder: JwtEncoder) {

    fun generateAccessToken(userId: UUID, roles: Set<Role>): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("template-app")
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))    // TOKN-01: 15-minute expiry
            .subject(userId.toString())
            .claim("roles", roles.map { it.name })           // e.g., ["USER"]
            .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
```

### application.yaml — Phase 2 additions

Additions to the dev profile section and new top-level `app.cors` config:

```yaml
# Add to common section:
app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:5173

# Remove from common section (conflicts with custom SecurityFilterChain):
# spring.security.user.name and spring.security.user.password — DELETE THESE
```

For the prod profile, add via env var:
```bash
# .env.example additions:
APP_CORS_ALLOWED_ORIGINS=https://app.example.com
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Custom `OncePerRequestFilter` for JWT | `oauth2ResourceServer { jwt { } }` DSL | Spring Security 5.1 | Built-in `BearerTokenAuthenticationFilter` handles extraction, validation, SecurityContext |
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` `@Bean` | Spring Security 5.7 | Functional-style configuration, no class extension needed |
| `authorizeRequests` | `authorizeHttpRequests` | Spring Security 6.0 | Old uses `FilterSecurityInterceptor` (deprecated); new uses `AuthorizationFilter` |
| `antMatchers` / `mvcMatchers` | `requestMatchers` | Spring Security 6.0 | Unified matcher API; `antMatchers` removed in Security 7 |
| `@EnableGlobalMethodSecurity(prePostEnabled=true)` | `@EnableMethodSecurity` | Spring Security 6.0 | Simpler annotation; `prePostEnabled=true` is now the default |
| `csrf().disable()` (method chaining) | `csrf { disable() }` (Kotlin lambda DSL) | Spring Security 5.7 (Lambda DSL) | Kotlin-idiomatic; works in Spring Security 7 |
| `@MockBean` in security tests | `@MockitoBean` | Spring Boot 4.0 | `@MockBean` deprecated; use `@MockitoBean` / `@MockitoSpyBean` |

**Deprecated/outdated:**
- `WebSecurityConfigurerAdapter`: Removed in Spring Security 7. Do not use.
- `authorizeRequests()`: Deprecated in Spring Security 6, removed in 7. Use `authorizeHttpRequests()`.
- `antMatchers()`, `mvcMatchers()`: Removed in Spring Security 7. Use `requestMatchers()`.
- `http.cors().and().csrf()...` method chaining: Still works in Spring Security 7 but Lambda DSL (`csrf { disable() }`) is the idiomatic form.

---

## Open Questions

1. **`exceptionHandling` DSL vs `oauth2ResourceServer` DSL for entryPoint/handler registration**
   - What we know: The `BearerTokenAuthenticationFilter` wires its own `AuthenticationEntryPoint` via `oauth2ResourceServer { authenticationEntryPoint = ... }`. `ExceptionTranslationFilter` uses the one from `exceptionHandling { authenticationEntryPoint = ... }`.
   - What's unclear: Whether registering in both is strictly required or if `oauth2ResourceServer` registration automatically cascades to `ExceptionTranslationFilter` in Spring Security 7.
   - Recommendation: Register in both to be safe, as shown in Pattern 1. This is the documented approach and costs nothing extra.

2. **Removal of `spring.security.user.*` properties**
   - What we know: The current `application.yaml` has `spring.security.user.name=admin` in the common section. With a custom `SecurityFilterChain` that uses `oauth2ResourceServer`, the default `UserDetailsService` is irrelevant.
   - What's unclear: Whether leaving these properties causes any harmful side effect (a spurious `UserDetailsService` bean) or just extra logging.
   - Recommendation: Remove `spring.security.user.*` from `application.yaml` in Phase 2. If the `UserDetailsServiceAutoConfiguration` causes issues, exclude it in `@SpringBootApplication`.

3. **Test approach for Phase 2 success criteria without Phase 3 Google/Apple providers**
   - What we know: Success criterion 3 requires a "valid RS256 Bearer token signed with the loaded keystore." In dev mode, the keystore is an in-memory generated keypair that changes on every restart.
   - What's unclear: Whether integration tests can use `@SpringBootTest` + `MockMvc` with a `JwtEncoder` bean to generate a test token, or whether a separate test keypair is needed.
   - Recommendation: In Phase 2 verification, use `JwtTokenService.generateAccessToken()` programmatically in a test (or a small `@SpringBootTest` test) to mint a token with the running context's key, then call `/api/v1/users/me` with it. This validates the full chain without needing Phase 3.

---

## Sources

### Primary (HIGH confidence)
- [Spring Security Kotlin Configuration Docs](https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html) — Kotlin DSL syntax, `securityMatcher`, multiple filter chains, `import org.springframework.security.config.annotation.web.invoke` requirement
- [Spring Security OAuth2 Resource Server JWT Docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — `oauth2ResourceServer { jwt { } }` DSL, `BearerTokenAuthenticationFilter` architecture, custom `JwtDecoder` bean wiring, `JwtAuthenticationConverter`, `JwtGrantedAuthoritiesConverter`
- [Spring Security CORS Docs](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html) — CORS must precede Spring Security, `UrlBasedCorsConfigurationSource`, `http.cors { }` placement in filter chain

### Secondary (MEDIUM confidence)
- [Dan Vega - Spring Security JWT Blog](https://www.danvega.dev/blog/spring-security-jwt) — `NimbusJwtEncoder` + `JwtClaimsSet` + `JwtEncoderParameters` token minting pattern verified against official API docs
- [Dan Vega - Spring Security CORS Blog](https://www.danvega.dev/blog/spring-security-cors) — `CorsConfigurationSource` bean with externalized origins via `@Value`; pattern verified against Spring Security CORS docs
- [Naiyer Asif - Error Handling for Spring Security Resource Server](https://www.naiyerasif.com/post/2021/01/19/error-handling-for-spring-security-resource-server/) — Custom `AuthenticationEntryPoint` and `AccessDeniedHandler` pattern with `ObjectMapper` JSON serialization; pattern confirmed against Spring Security exception handling issue tracker
- [Spring Security GitHub Issue #5985](https://github.com/spring-projects/spring-security/issues/5985) — Confirms `BearerTokenAuthenticationEntryPoint` writes no JSON body by default; custom implementation required for SECU-07

### Tertiary (LOW confidence — verify before use)
- WebSearch: `HandlerMethodValidationException` vs `MethodArgumentNotValidException` behavioral split after Spring Boot 3.2.2 — confirmed in GitHub issue #40055 and Spring Framework docs at MEDIUM; recommend handling both
- WebSearch: Spring Boot env var `APP_CORS_ALLOWED_ORIGINS` comma-separated string auto-converts to `List<String>` in `@ConfigurationProperties` — confirmed by Spring Boot externalized config docs at MEDIUM but recommend testing

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already on classpath from Phase 1; no new dependencies
- Architecture: HIGH — SecurityFilterChain patterns verified from official Spring Security 7 docs; CORS and error handler patterns verified against multiple authoritative sources
- Pitfalls: HIGH — most pitfalls are either documented Spring Security behavior changes (Security 6→7 API renames) or reported in official issue tracker

**Research date:** 2026-03-01
**Valid until:** 2026-04-01 (Spring Security 7 / Spring Boot 4.0.3 is stable; patterns will not change within 30 days)
