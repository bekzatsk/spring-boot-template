---
phase: 02-security-wiring
plan: 02
subsystem: auth
tags: [jwt, spring-security, oauth2-resource-server, mockmvc, integration-testing, validation, kotlin]

dependency_graph:
  requires:
    - phase: 02-01
      provides: SecurityFilterChain, JwtDecoder, GlobalExceptionHandler, ApiAuthenticationEntryPoint
    - phase: 01-02
      provides: NimbusJwtEncoder bean from RsaKeyConfig
  provides:
    - JwtTokenService (RS256 access token minting with 15-minute expiry)
    - AuthRequest DTO (Jakarta Validation @NotBlank on idToken)
    - Stub UserController (GET /api/v1/users/me returns JWT claims without DB)
    - SecurityIntegrationTest (4 MockMvc tests proving full chain)
  affects: [03-google-auth, 04-apple-auth, 05-token-management]

tech-stack:
  added:
    - H2 in-memory database (test scope only — enables SpringBootTest without PostgreSQL)
  patterns:
    - JwtTokenService uses NimbusJwtEncoder.encode(JwtEncoderParameters.from(claims)) — no JwsHeader needed (Nimbus defaults RS256 from JWKSource RSA key)
    - @TestConfiguration + @RestController inner class pattern for endpoint-specific validation testing
    - Test application.yaml overrides datasource with H2 for full SpringBootTest isolation
    - Spring Boot 4.x uses org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc (not boot.test.autoconfigure.web.servlet)

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/authentication/JwtTokenService.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/AuthRequest.kt
    - src/main/kotlin/kz/innlab/template/user/UserController.kt
    - src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt
    - src/test/resources/application.yaml
  modified:
    - pom.xml

key-decisions:
  - "Spring Boot 4.x @AutoConfigureMockMvc is in org.springframework.boot.webmvc.test.autoconfigure — not the Spring Boot 3.x boot.test.autoconfigure.web.servlet package"
  - "H2 test-scoped dependency added to pom.xml — enables full SpringBootTest with JPA without a running PostgreSQL instance"
  - "JwtTokenService omits explicit JwsHeader in JwtEncoderParameters — NimbusJwtEncoder infers RS256 from the RSA JWKSource key"

patterns-established:
  - "@TestConfiguration @RestController inner class: registers a test-only endpoint within the test's application context to prove validation behavior without polluting main code"
  - "Test resource override: src/test/resources/application.yaml overrides datasource to H2 — all SpringBootTest classes benefit automatically"

requirements-completed: [TOKN-01, SECU-08]

duration: 7min
completed: 2026-03-01
---

# Phase 2 Plan 2: Security Wiring — JWT Token Service and End-to-End Security Chain Summary

**RS256 JWT access token minting via JwtTokenService, AuthRequest DTO with Jakarta Validation, stub UserController returning claims, and 4 MockMvc integration tests verifying the full security chain.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-28T23:55:29Z
- **Completed:** 2026-03-01T00:02:30Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- JwtTokenService mints RS256-signed access tokens with 15-minute expiry using NimbusJwtEncoder (TOKN-01 complete)
- Stub UserController at GET /api/v1/users/me proves SECU-02: auth passes without hitting the database
- 4 MockMvc integration tests verify the full chain: 401 without token, 200 with valid token + claims, CORS preflight, 400 for blank idToken (SECU-08 complete)
- H2 test dependency and test application.yaml enable full SpringBootTest isolation without a running PostgreSQL

## Task Commits

Each task was committed atomically:

1. **Task 1: Create JwtTokenService, AuthRequest DTO, and stub UserController** - `a7b9b44` (feat)
2. **Task 2: Create MockMvc integration test proving end-to-end security chain** - `083c0f1` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `src/main/kotlin/kz/innlab/template/authentication/JwtTokenService.kt` - RS256 JWT minting with 15-minute expiry via NimbusJwtEncoder
- `src/main/kotlin/kz/innlab/template/authentication/dto/AuthRequest.kt` - Data class with @NotBlank on idToken for Phase 3/4 social auth controllers
- `src/main/kotlin/kz/innlab/template/user/UserController.kt` - Stub GET /api/v1/users/me returning sub + roles from JWT claims (no DB hit)
- `src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt` - 4-test MockMvc suite proving full security chain
- `src/test/resources/application.yaml` - H2 in-memory datasource override for test isolation
- `pom.xml` - Added H2 test dependency

## Decisions Made

1. **Spring Boot 4.x @AutoConfigureMockMvc package:** The annotation moved to `org.springframework.boot.webmvc.test.autoconfigure` in Spring Boot 4.x. The old `boot.test.autoconfigure.web.servlet` package does not exist. Applied as Rule 1 auto-fix during Task 2 compilation.

2. **H2 for test datasource:** The app uses JPA with PostgreSQL. Running `@SpringBootTest` without a database causes context load failure. Added H2 test dependency and `src/test/resources/application.yaml` to override datasource — all test classes benefit without changes to each test.

3. **JwsHeader omitted in JwtTokenService:** Per research Pitfall 6, NimbusJwtEncoder automatically selects RS256 when the JWKSource contains an RSA key. Explicit JwsHeader is unnecessary and potentially error-prone.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added H2 test dependency and test application.yaml**
- **Found during:** Task 2 (running SecurityIntegrationTest with @SpringBootTest)
- **Issue:** @SpringBootTest loads the full application context which requires a PostgreSQL datasource. No test database was configured — context load fails with HikariPool connection refused.
- **Fix:** Added `com.h2database:h2` (test scope) to pom.xml; created `src/test/resources/application.yaml` configuring H2 in-memory datasource.
- **Files modified:** pom.xml, src/test/resources/application.yaml (created)
- **Verification:** All 5 tests pass (4 SecurityIntegrationTest + 1 TemplateApplicationTests contextLoads)
- **Committed in:** 083c0f1 (Task 2 commit)

**2. [Rule 1 - Bug] Fixed AutoConfigureMockMvc import for Spring Boot 4.x**
- **Found during:** Task 2 — first test compile
- **Issue:** Plan instructed `@AutoConfigureMockMvc` without specifying the package. The Spring Boot 3.x package `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` does not exist in Spring Boot 4.x (which uses `spring-boot-webmvc-test` artifact). Compiler error: `Unresolved reference 'web'`.
- **Fix:** Changed import to `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- **Files modified:** SecurityIntegrationTest.kt
- **Verification:** Test compiles and all 4 tests pass
- **Committed in:** 083c0f1 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking infrastructure, 1 package name bug)
**Impact on plan:** Both fixes necessary for test execution. No scope creep — all work directly supports Task 2 requirements.

## Issues Encountered

None beyond the auto-fixed deviations above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 2 is now complete. All Phase 2 success criteria met:
- SecurityFilterChain with stateless JWT auth (Plan 1)
- RS256 token minting with 15-minute expiry (Plan 2 — TOKN-01)
- CORS configured with allowed origins (Plan 1)
- 401 JSON for unauthenticated requests (Plan 1 + Plan 2 tests)
- Jakarta Validation on auth DTOs returning 400 (Plan 2 — SECU-08)
- Full chain verified via MockMvc integration tests (Plan 2)

Ready for Phase 3: Google Auth — GoogleIdTokenVerifier, social login endpoint, and UserService.

## Self-Check: PASSED

All 5 created files exist. Both task commits (a7b9b44, 083c0f1) verified in git log. All 5 tests pass (4 SecurityIntegrationTest + 1 TemplateApplicationTests).

---
*Phase: 02-security-wiring*
*Completed: 2026-03-01*
