---
phase: 05-hardening
plan: 01
subsystem: infra
tags: [maven-wrapper, rate-limiting, hibernate, spring-boot, kotlin]

# Dependency graph
requires:
  - phase: 04-apple-auth
    provides: AppleAuthService, /auth/apple endpoint — all auth endpoints now present for rate limiting markers
provides:
  - Maven Wrapper properties file (.mvn/wrapper/maven-wrapper.properties) with Maven 3.9.9 URL
  - Rate limiting TODO markers at all 4 AuthController auth endpoint methods
  - Rate limiting TODO marker at oauth2ResourceServer filter chain insertion point in SecurityConfig
  - Clean test YAML without deprecated H2Dialect setting and with open-in-view disabled
affects: [future-rate-limiting-implementation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Rate limiting TODO markers at auth entry points document the BearerTokenAuthenticationFilter insertion pattern"
    - "Maven Wrapper 3.3+ does not require maven-wrapper.jar — only maven-wrapper.properties needed"
    - "Removing explicit database-platform from test YAML lets Hibernate auto-detect H2Dialect without HHH90000025"

key-files:
  created:
    - .mvn/wrapper/maven-wrapper.properties
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/AuthController.kt
    - src/main/kotlin/kz/innlab/template/config/SecurityConfig.kt
    - src/test/resources/application.yaml

key-decisions:
  - "Maven 3.9.9 chosen — latest 3.9.x patch release; Spring Boot 4.x requires Maven 3.9+"
  - "Rate limiting markers use // TODO comments (not annotations) — zero dependency, zero behavior change, clear insertion points"
  - "H2Dialect removal: let Hibernate auto-detect avoids HHH90000025 deprecation warning in test output"

patterns-established:
  - "Rate limiting insertion point: http.addFilterBefore(myRateLimitFilter, BearerTokenAuthenticationFilter::class.java)"

requirements-completed: [INFR-07, INFR-08]

# Metrics
duration: 2min
completed: 2026-03-01
---

# Phase 5 Plan 01: Hardening Summary

**Maven Wrapper restored (3.9.9), rate limiting TODO markers at all 5 auth entry points (4 in AuthController + 1 in SecurityConfig), and test YAML cleaned of H2Dialect deprecation warning**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-01T06:05:01Z
- **Completed:** 2026-03-01T06:07:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Created `.mvn/wrapper/maven-wrapper.properties` with Maven 3.9.9 so `./mvnw` works on a clean checkout
- Added rate limiting TODO comments to all four AuthController endpoint methods (googleLogin, appleLogin, refresh, revoke)
- Added rate limiting TODO comment in SecurityConfig at the `oauth2ResourceServer` filter chain insertion point documenting the `BearerTokenAuthenticationFilter` pattern
- Removed `database-platform: org.hibernate.dialect.H2Dialect` from test YAML — eliminates HHH90000025 deprecation warning
- Added `open-in-view: false` to test YAML JPA block — suppresses open-in-view WARN when test YAML overrides main YAML
- Verified full build: all 9 tests pass, BUILD SUCCESS, zero Hibernate or open-in-view warnings

## Task Commits

Each task was committed atomically:

1. **Task 1: Add rate limiting TODO markers and fix test YAML warnings** - `d04a40f` (feat)
2. **Task 2: Create Maven Wrapper properties and verify full build** - `43b3806` (feat)

**Plan metadata:** (committed with final docs commit)

## Files Created/Modified
- `.mvn/wrapper/maven-wrapper.properties` - Maven 3.9.9 distributionUrl enabling `./mvnw` on clean checkout
- `src/main/kotlin/kz/innlab/template/authentication/AuthController.kt` - Rate limiting TODO comments in googleLogin, appleLogin, refresh, revoke methods
- `src/main/kotlin/kz/innlab/template/config/SecurityConfig.kt` - Rate limiting TODO comment inside oauth2ResourceServer block before jwt{} with BearerTokenAuthenticationFilter insertion guidance
- `src/test/resources/application.yaml` - Removed database-platform H2Dialect, added open-in-view: false

## Decisions Made
- Maven 3.9.9 selected as the latest 3.9.x release compatible with Spring Boot 4.x
- Rate limiting markers implemented as code comments (not annotations or stubs) — zero runtime behavior change, discoverable via `grep "TODO: rate limiting"`
- H2Dialect auto-detection preferred over explicit setting — Hibernate 7.x auto-detects correctly without the deprecated property

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. JVM-level `WARNING:` lines from Maven's Guice dependency (sun.misc.Unsafe, byte-buddy-agent dynamic loading) appeared in build output as expected — these are Maven-internal and not project warnings.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 5 (Hardening) plan 01 is the only plan in this phase. All requirements are satisfied:
- INFR-07: Rate limiting markers present at all 4 auth endpoint methods and the oauth2ResourceServer filter chain entry point
- INFR-08: `./mvnw clean package` succeeds with all 9 tests passing from the project root

The template is now complete. A developer can clone the repo, run `docker-compose up -d && ./mvnw spring-boot:run`, and immediately have a working auth backend with Google and Apple Sign In, JWT access/refresh tokens, and clear TODO markers showing where to add rate limiting.

---
*Phase: 05-hardening*
*Completed: 2026-03-01*
