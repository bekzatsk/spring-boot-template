---
phase: 02-security-wiring
verified: 2026-03-01T00:05:00Z
status: passed
score: 10/10 must-haves verified
re_verification: false
---

# Phase 2: Security Wiring — Verification Report

**Phase Goal:** The Spring Security filter chain is configured stateless with CORS, JWT Bearer validation, and consistent JSON error responses — all endpoints return correct HTTP status codes before any provider auth exists
**Verified:** 2026-03-01T00:05:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria + Plan must_haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A request to GET /api/v1/users/me without a Bearer token returns 401 with JSON body { error, message, status } | VERIFIED | `SecurityIntegrationTest.noToken returns 401 with JSON body` passes; `ApiAuthenticationEntryPoint` writes `ErrorResponse(error="Unauthorized", message="Authentication required", status=401)` |
| 2 | An OPTIONS preflight request to /api/v1/** returns 200 with CORS headers and is not blocked by JWT validation | VERIFIED | `SecurityIntegrationTest.corsPreflight returns 200 with CORS headers` passes; `SecurityConfig` has `authorize(HttpMethod.OPTIONS, "/**", permitAll)` before JWT evaluation |
| 3 | A request with a valid RS256 Bearer token passes authentication and reaches the controller without DB hit | VERIFIED | `SecurityIntegrationTest.validToken returns 200 with JWT claims` passes; `UserController.getCurrentUser` casts `authentication.principal as Jwt` with no repository calls |
| 4 | CORS allowed origins are read from app.cors.allowed-origins configuration, not hardcoded | VERIFIED | `CorsProperties` is `@ConfigurationProperties(prefix = "app.cors")`; `SecurityConfig.corsConfigurationSource()` uses `corsProperties.allowedOrigins`; prod profile sets `app.cors.allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}` |
| 5 | Spring Security is stateless: no session is created, CSRF is disabled, httpBasic/formLogin/logout are disabled | VERIFIED | `SecurityConfig`: `csrf { disable() }`, `httpBasic { disable() }`, `formLogin { disable() }`, `logout { disable() }`, `sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }` all present |
| 6 | Security exceptions (401/403) return consistent { error, message, status } JSON, not default Spring error pages | VERIFIED | `ApiAuthenticationEntryPoint` writes 401 JSON via `ObjectMapper`; `ApiAccessDeniedHandler` writes 403 JSON; both registered in both `oauth2ResourceServer { }` and `exceptionHandling { }` DSL blocks |
| 7 | Controller-layer exceptions (validation 400, unexpected 500) return consistent { error, message, status } JSON | VERIFIED | `GlobalExceptionHandler` handles `MethodArgumentNotValidException` (400), `HandlerMethodValidationException` (400), and `Exception` (500) — all return `ErrorResponse` |
| 8 | JwtTokenService mints RS256-signed access tokens with 15-minute expiry using the NimbusJwtEncoder from Phase 1 | VERIFIED | `JwtTokenService` constructor-injects `JwtEncoder`; calls `.expiresAt(now.plus(15, ChronoUnit.MINUTES))`; uses `jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue` |
| 9 | Jakarta Validation annotations on auth request DTOs reject malformed input with 400 { error, message, status } JSON | VERIFIED | `AuthRequest` has `@field:NotBlank(message = "ID token is required")`; `SecurityIntegrationTest.blankIdToken returns 400 with validation error` passes both blank and missing field cases |
| 10 | Integration test proves the full security chain works end-to-end | VERIFIED | All 5 tests pass (`mvn test` BUILD SUCCESS): 4 in `SecurityIntegrationTest` + 1 in `TemplateApplicationTests` |

**Score:** 10/10 truths verified

---

## Required Artifacts

### Plan 02-01 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/kz/innlab/template/config/SecurityConfig.kt` | SecurityFilterChain with stateless session, CORS, OAuth2 resource server JWT, exception handling | VERIFIED | 89 lines; contains `SecurityFilterChain`, `@EnableConfigurationProperties(CorsProperties::class)`, full DSL config |
| `src/main/kotlin/kz/innlab/template/config/CorsProperties.kt` | Externalized CORS allowed-origins configuration | VERIFIED | `@ConfigurationProperties(prefix = "app.cors")` with `val allowedOrigins: List<String>` |
| `src/main/kotlin/kz/innlab/template/authentication/error/ApiAuthenticationEntryPoint.kt` | 401 JSON error response for missing/invalid Bearer tokens | VERIFIED | Implements `AuthenticationEntryPoint`; writes 401 + `application/json` via `ObjectMapper` |
| `src/main/kotlin/kz/innlab/template/authentication/error/ApiAccessDeniedHandler.kt` | 403 JSON error response for insufficient permissions | VERIFIED | Implements `AccessDeniedHandler`; writes 403 + `application/json` via `ObjectMapper` |
| `src/main/kotlin/kz/innlab/template/shared/error/ErrorResponse.kt` | Shared { error, message, status } data class | VERIFIED | `data class ErrorResponse(val error: String, val message: String, val status: Int)` |
| `src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt` | Controller-layer exception handling for 400 and 500 | VERIFIED | `@RestControllerAdvice` with three `@ExceptionHandler` methods covering 400/400/500 |

### Plan 02-02 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/kz/innlab/template/authentication/JwtTokenService.kt` | RS256 JWT access token minting with 15-minute expiry | VERIFIED | `@Service`; injects `JwtEncoder`; `expiresAt(now.plus(15, ChronoUnit.MINUTES))`; 28 lines, fully substantive |
| `src/main/kotlin/kz/innlab/template/authentication/dto/AuthRequest.kt` | Auth request DTO with Jakarta Validation @NotBlank | VERIFIED | `@field:NotBlank(message = "ID token is required")` on `idToken`; default value for Jackson deserialization |
| `src/main/kotlin/kz/innlab/template/user/UserController.kt` | Stub GET /api/v1/users/me returning JWT claims | VERIFIED | `@RestController @RequestMapping("/api/v1/users")`; casts `authentication.principal as Jwt`; returns `sub` + `roles` |
| `src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt` | 4-test MockMvc integration test suite | VERIFIED | All 4 tests present and passing; uses correct Spring Boot 4.x `@AutoConfigureMockMvc` import |
| `src/test/resources/application.yaml` | H2 in-memory datasource override for test isolation | VERIFIED | H2 datasource, `ddl-auto: create-drop`, `app.cors.allowed-origins` for CORS test |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SecurityConfig.kt` | `ApiAuthenticationEntryPoint.kt` | Constructor injection + `exceptionHandling`/`oauth2ResourceServer` DSL | WIRED | `authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint` in both DSL blocks (lines 55, 59) |
| `SecurityConfig.kt` | `CorsProperties.kt` | Constructor injection + `cors` DSL | WIRED | `corsProperties.allowedOrigins` used in `corsConfigurationSource()` (line 69) |
| `SecurityConfig.kt` | `JwtDecoder bean (RsaKeyConfig)` | Constructor injection + `oauth2ResourceServer jwt` DSL | WIRED | `jwtDecoder = this@SecurityConfig.jwtDecoder` (line 52) |
| `ApiAuthenticationEntryPoint.kt` | `ErrorResponse.kt` | Direct instantiation + ObjectMapper serialization | WIRED | `ErrorResponse(error = "Unauthorized", message = "Authentication required", status = 401)` at line 23; `objectMapper.writeValueAsString(errorResponse)` at line 28 |
| `GlobalExceptionHandler.kt` | `ErrorResponse.kt` | Direct instantiation in exception handler methods | WIRED | All three `@ExceptionHandler` methods return `ErrorResponse(...)` |
| `JwtTokenService.kt` | `JwtEncoder bean (RsaKeyConfig)` | Constructor injection | WIRED | `private val jwtEncoder: JwtEncoder`; `jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue` (line 26) |
| `UserController.kt` | `SecurityFilterChain (SecurityConfig)` | OAuth2 resource server JWT authentication | WIRED | `authentication.principal as Jwt` (line 15); reachable only after JWT validation passes; integration test `validToken returns 200` confirms path |
| `AuthRequest.kt` | `GlobalExceptionHandler` | `@Valid @RequestBody` triggers `MethodArgumentNotValidException` | WIRED | `@field:NotBlank` triggers constraint violation; `GlobalExceptionHandler.handleValidation` handles it; confirmed by `blankIdToken returns 400` test |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SECU-01 | 02-01 | Spring Security configured as stateless (STATELESS session policy, CSRF disabled) | SATISFIED | `SessionCreationPolicy.STATELESS`, `csrf { disable() }`, `httpBasic { disable() }`, `formLogin { disable() }`, `logout { disable() }` all in `SecurityConfig.kt` |
| SECU-02 | 02-01 | JwtAuthenticationFilter validates Bearer token and sets SecurityContext from JWT claims only (no DB hit) | SATISFIED | `oauth2ResourceServer { jwt { jwtDecoder = ... } }` uses Spring's built-in JWT validation; `UserController` uses `authentication.principal as Jwt` — no repository calls anywhere in the auth path |
| SECU-03 | 02-01 | All /api/** endpoints require authentication except /api/v1/auth/** | SATISFIED | `authorize("/api/v1/auth/**", permitAll)` then `authorize("/api/**", authenticated)` in correct order |
| SECU-04 | 02-01 | CORS configured inside SecurityFilterChain so OPTIONS pre-flight requests pass correctly | SATISFIED | `cors { configurationSource = corsConfigurationSource() }` inside `http { }` DSL; `HttpMethod.OPTIONS, "/**"` is `permitAll`; integration test verifies 200 on OPTIONS |
| SECU-05 | 02-01 | CORS allowed origins are configurable (not hardcoded) | SATISFIED | `CorsProperties` with `@ConfigurationProperties`; prod profile uses `${APP_CORS_ALLOWED_ORIGINS}`; `.env.example` documents the variable |
| SECU-07 | 02-01 | Consistent JSON error responses: `{ error, message, status }` via @RestControllerAdvice | SATISFIED | `ErrorResponse` data class; `GlobalExceptionHandler` annotated `@RestControllerAdvice`; 401/403/400/500 all use same shape |
| SECU-08 | 02-02 | Input validation with Jakarta Validation on auth request DTOs | SATISFIED | `AuthRequest` has `@field:NotBlank`; `GlobalExceptionHandler.handleValidation` catches `MethodArgumentNotValidException`; `blankIdToken` integration test proves 400 response |
| TOKN-01 | 02-02 | JWT access tokens are RS256-signed with 15-minute expiry via NimbusJwtEncoder + JWKSource | SATISFIED | `JwtTokenService.generateAccessToken` uses `expiresAt(now.plus(15, ChronoUnit.MINUTES))`; uses `JwtEncoder` bean (NimbusJwtEncoder from RsaKeyConfig); no explicit JwsHeader — RS256 inferred from RSA JWKSource |

**All 8 phase-2 requirements: SATISFIED**

**Orphaned requirement check:** REQUIREMENTS.md traceability table maps SECU-01 through SECU-08, TOKN-01 to Phase 2. Plans 02-01 and 02-02 explicitly claim all 8. No orphaned requirements.

---

## Anti-Patterns Scan

Scanned files: all 10 phase-2 source and test files.

| File | Pattern | Finding |
|------|---------|---------|
| All `.kt` files | TODO/FIXME/PLACEHOLDER | None found |
| All `.kt` files | `return null`, `return {}`, stub bodies | None found |
| `UserController.kt` | Stub indicator | Intentional stub — documented in plan as "Phase 3 will replace with actual UserService lookup". Does not block phase goal; correctly returns JWT claims from authentication principal |
| `GlobalExceptionHandler.kt` | Exception message exposure | Correctly suppresses `ex.message` in the general 500 handler per plan spec |
| `application.yaml` | `spring.security.user` conflict | Correctly absent — removed in commit a2dd845 |

No blockers or warnings found.

---

## Human Verification Required

None required. All phase-2 success criteria are verifiable programmatically:

- HTTP status codes: verified by `mvn test` (5/5 tests pass)
- JSON response shape: verified by MockMvc `jsonPath` assertions
- CORS header presence: verified by MockMvc `header().string(...)` assertion
- Configuration-driven origins: verified by code inspection (no hardcoded strings in `SecurityConfig.kt`)
- Session statelessness: verified by code inspection (`SessionCreationPolicy.STATELESS`)

---

## Build and Test Results

```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 20.233 s
```

- `TemplateApplicationTests.contextLoads` — PASS
- `SecurityIntegrationTest.noToken returns 401 with JSON body` — PASS
- `SecurityIntegrationTest.validToken returns 200 with JWT claims` — PASS
- `SecurityIntegrationTest.corsPreflight returns 200 with CORS headers` — PASS
- `SecurityIntegrationTest.blankIdToken returns 400 with validation error` — PASS

---

## Summary

Phase 2 goal is fully achieved. The Spring Security filter chain is configured stateless (SECU-01), validates RS256 Bearer tokens via the JwtDecoder from Phase 1 without any database access (SECU-02), enforces /api/** authentication with /api/v1/auth/** as a public exception (SECU-03), passes OPTIONS preflight before JWT validation (SECU-04), reads CORS origins from configurable properties (SECU-05), returns consistent { error, message, status } JSON for all error statuses (SECU-07), validates auth request DTOs with Jakarta Validation (SECU-08), and mints RS256 tokens with 15-minute expiry (TOKN-01).

All 10 observable truths verified. All 8 requirements satisfied. All 11 key artifacts exist, are substantive, and are correctly wired. All 5 integration tests pass.

---

_Verified: 2026-03-01T00:05:00Z_
_Verifier: Claude (gsd-verifier)_
