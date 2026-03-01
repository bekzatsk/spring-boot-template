---
phase: 04-apple-auth
plan: 01
subsystem: auth
tags: [apple-sign-in, jwt, jwks, nimbus, spring-security, oauth2, integration-testing]

# Dependency graph
requires:
  - phase: 03-google-auth-and-token-management
    provides: AuthController, GoogleAuthService, UserService, JwtTokenService, RefreshTokenService patterns
  - phase: 02-security-wiring
    provides: SecurityConfig with /api/v1/auth/** permitAll pattern, GlobalExceptionHandler

provides:
  - POST /api/v1/auth/apple endpoint for Apple ID token authentication
  - AppleAuthConfig: NimbusJwtDecoder bean with iss/aud/exp validators for Apple JWKS
  - AppleAuthService: Apple token decode + find-or-create user + token issuance
  - AppleAuthRequest DTO: idToken + optional givenName/familyName
  - UserService.findOrCreateAppleUser: nullable email handling for subsequent logins
  - 4 integration tests covering first login, subsequent login, private relay email, invalid token

affects: [05-final-polish, any phase adding new auth providers]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - NimbusJwtDecoder.withJwkSetUri() with DelegatingOAuth2TokenValidator for external JWKS validation
    - @Qualifier("appleJwtDecoder") bean name disambiguation when multiple JwtDecoder beans exist
    - @MockitoBean(name = "appleJwtDecoder") from org.springframework.test.context.bean.override.mockito — Spring 7.x annotation (not @MockBean)
    - Wrap JwtException as BadCredentialsException so GlobalExceptionHandler returns 401

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/config/AppleAuthConfig.kt
    - src/main/kotlin/kz/innlab/template/authentication/dto/AppleAuthRequest.kt
    - src/main/kotlin/kz/innlab/template/authentication/AppleAuthService.kt
    - src/test/kotlin/kz/innlab/template/AppleAuthIntegrationTest.kt
  modified:
    - src/main/kotlin/kz/innlab/template/user/UserService.kt
    - src/main/kotlin/kz/innlab/template/authentication/AuthController.kt
    - src/main/resources/application.yaml
    - src/test/resources/application.yaml
    - .env.example

key-decisions:
  - "@MockitoBean is from org.springframework.test.context.bean.override.mockito (Spring Framework 7.x) — not from spring-boot-test; mockito-kotlin library not on classpath so plain Mockito.when()/anyString() used instead"
  - "refreshTokenRepository.deleteAll() must precede userRepository.deleteAll() in @BeforeEach — FK constraint from refresh_tokens.user_id prevents deleting users with active tokens"
  - "appleJwtDecoder bean named explicitly via @Bean(name=['appleJwtDecoder']) and injected with @Qualifier('appleJwtDecoder') — prevents NoUniqueBeanDefinitionException with the resource server JwtDecoder from RsaKeyConfig"
  - "JwtException from NimbusJwtDecoder.decode() wrapped as BadCredentialsException — JwtException is not handled by GlobalExceptionHandler, would produce 500 without the wrap"

patterns-established:
  - "Pattern: External JWKS validation via NimbusJwtDecoder.withJwkSetUri() + DelegatingOAuth2TokenValidator — use this for any external OIDC provider"
  - "Pattern: Nullable email in find-or-create — check existing user by (provider, sub) first; only require email when creating new user"
  - "Pattern: Test cleanup order — always deleteAll() child tables before parent tables when FK constraints exist"

requirements-completed: [AUTH-02, AUTH-04, AUTH-05, AUTH-06]

# Metrics
duration: 13min
completed: 2026-03-01
---

# Phase 4 Plan 01: Apple Auth Wiring Summary

**NimbusJwtDecoder-based Apple Sign In with iss/aud/exp validation, nullable-email subsequent login support, and 4 integration tests via @MockitoBean-mocked JwtDecoder**

## Performance

- **Duration:** 13 min
- **Started:** 2026-03-01T01:32:41Z
- **Completed:** 2026-03-01T01:45:46Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- Apple Sign In end-to-end: POST /api/v1/auth/apple accepts Apple ID token, verifies via Apple JWKS, creates/finds user, returns accessToken + refreshToken
- First-login atomicity: givenName/familyName from request body + email from JWT persisted in single @Transactional call; Apple never sends name again after first login
- Subsequent-login correctness: email absent from JWT returns 200 (user looked up by APPLE + sub alone); only new users require email
- Private relay emails (user@privaterelay.appleid.com) accepted without validation errors (AUTH-06)
- All 9 tests pass: 4 new AppleAuthIntegrationTest + 4 existing SecurityIntegrationTest + 1 TemplateApplicationTests

## Task Commits

Each task was committed atomically:

1. **Task 1: Create AppleAuthConfig, AppleAuthRequest DTO, AppleAuthService, and extend UserService** - `84c5f64` (feat)
2. **Task 2: Wire AuthController /apple endpoint, update configs, and add integration tests** - `2aeee2f` (feat)

**Plan metadata:** (docs commit — see state updates below)

## Files Created/Modified

- `src/main/kotlin/kz/innlab/template/config/AppleAuthConfig.kt` - NimbusJwtDecoder @Bean named "appleJwtDecoder" with iss/aud/exp validators via DelegatingOAuth2TokenValidator
- `src/main/kotlin/kz/innlab/template/authentication/dto/AppleAuthRequest.kt` - DTO with @NotBlank idToken + optional givenName/familyName
- `src/main/kotlin/kz/innlab/template/authentication/AppleAuthService.kt` - Decodes Apple JWT, wraps JwtException as BadCredentialsException, delegates to UserService
- `src/main/kotlin/kz/innlab/template/user/UserService.kt` - Added findOrCreateAppleUser: lookup by (APPLE, sub), require email only for new users
- `src/main/kotlin/kz/innlab/template/authentication/AuthController.kt` - Added AppleAuthService injection and POST /apple endpoint
- `src/main/resources/application.yaml` - Added app.auth.apple.bundle-id config
- `src/test/resources/application.yaml` - Added app.auth.apple.bundle-id: test-bundle-id
- `.env.example` - Documented APPLE_BUNDLE_ID with iOS bundle ID vs Services ID note
- `src/test/kotlin/kz/innlab/template/AppleAuthIntegrationTest.kt` - 4 integration tests with @MockitoBean

## Decisions Made

- `@MockitoBean` annotation is in `org.springframework.test.context.bean.override.mockito` (Spring 7.x) rather than spring-boot-test. `mockito-kotlin` library not available on classpath; using plain `Mockito.when()`/`Mockito.anyString()` which works cleanly in Kotlin.
- `refreshTokenRepository.deleteAll()` called before `userRepository.deleteAll()` in `@BeforeEach` — discovered FK constraint violation when tests created users with refresh tokens then tried to delete users in cleanup.
- No new Maven dependencies needed — `NimbusJwtDecoder`, `JwtIssuerValidator`, `JwtTimestampValidator`, `JwtClaimValidator`, `DelegatingOAuth2TokenValidator` all already on classpath via `spring-boot-starter-oauth2-authorization-server`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed test cleanup FK constraint violation**
- **Found during:** Task 2 (AppleAuthIntegrationTest execution)
- **Issue:** `userRepository.deleteAll()` in `@BeforeEach` threw `DataIntegrityViolationException` because refresh tokens referencing users still existed from previous test's authentication flow
- **Fix:** Added `refreshTokenRepository.deleteAll()` before `userRepository.deleteAll()` in `@BeforeEach`
- **Files modified:** `src/test/kotlin/kz/innlab/template/AppleAuthIntegrationTest.kt`
- **Verification:** All 9 tests pass with clean state between test methods
- **Committed in:** `2aeee2f` (Task 2 commit)

**2. [Rule 1 - Bug] Fixed @MockitoBean annotation import — mockito-kotlin not available**
- **Found during:** Task 2 (first test compilation attempt)
- **Issue:** Test used `org.mockito.kotlin.whenever` and `org.mockito.kotlin.anyString` from mockito-kotlin library which is not in pom.xml; also `@MockitoBean` from `org.springframework.boot.test.mock.mockito.MockitoBean` doesn't exist in Spring Boot 4.x
- **Fix:** Changed imports to plain `org.mockito.Mockito.when` and `org.mockito.Mockito.anyString`; used correct `@MockitoBean` from `org.springframework.test.context.bean.override.mockito.MockitoBean` (Spring Framework 7.x)
- **Files modified:** `src/test/kotlin/kz/innlab/template/AppleAuthIntegrationTest.kt`
- **Verification:** Compilation succeeds; all 4 Apple auth tests pass
- **Committed in:** `2aeee2f` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs in test infrastructure)
**Impact on plan:** Both auto-fixes necessary for test correctness. No scope creep.

## Issues Encountered

- The plan mentioned `@MockitoBean(name = "appleJwtDecoder")` as the Spring Boot 4.x annotation, but the correct import in Spring Boot 4.0.3 (Spring Framework 7.x) is from `org.springframework.test.context.bean.override.mockito` (not `org.springframework.boot.test.mock.mockito`). The `@MockBean` annotation still exists in the boot package but `@MockitoBean` as a distinct named-bean override annotation lives in the core Spring Framework test package.

## User Setup Required

Environment variables to add (see `.env.example` for guidance):
- `APPLE_BUNDLE_ID`: iOS app bundle ID (e.g., `com.yourcompany.yourapp`) — NOT the Services ID

## Next Phase Readiness

- Apple Sign In fully wired and tested — ready for Phase 5 (final polish/docs)
- Both Google and Apple auth use the same token lifecycle (access + refresh), consistent error handling (401 for invalid tokens)
- No blockers for next phase

---
*Phase: 04-apple-auth*
*Completed: 2026-03-01*
