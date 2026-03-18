---
phase: 03-add-swagger-openapi
plan: 01
subsystem: api
tags: [springdoc, openapi, swagger, spring-security, jackson2, jwt]

# Dependency graph
requires:
  - phase: 02-email-and-prefs
    provides: "SecurityConfig with authorizeHttpRequests block for permit rules"
provides:
  - "Swagger UI accessible at /swagger-ui/index.html without authentication"
  - "OpenAPI spec at /v3/api-docs with global JWT bearerAuth security scheme"
  - "springdoc-openapi-starter-webmvc-ui 3.0.2 dependency"
  - "spring-boot-jackson2 stop-gap for Jackson 2/3 coexistence"
  - "OpenApiConfig bean with API metadata and bearerAuth security scheme"
  - "springdoc YAML config with method sorting and try-it-out enabled"
affects: [future-api-docs, openapi-consumers]

# Tech tracking
tech-stack:
  added:
    - "springdoc-openapi-starter-webmvc-ui:3.0.2 — auto-generates OpenAPI spec + Swagger UI"
    - "spring-boot-jackson2 (BOM-managed) — Jackson 2/3 coexistence stop-gap for Boot 4"
  patterns:
    - "Global JWT Bearer security scheme via OpenAPI bean addSecurityItem — single definition applies to all endpoints"
    - "Swagger UI paths permitted in SecurityConfig before /api/** rules"
    - "springdoc top-level YAML config (not profile-specific) for UI and scan configuration"

key-files:
  created:
    - src/main/kotlin/kz/innlab/template/config/OpenApiConfig.kt
  modified:
    - pom.xml
    - src/main/kotlin/kz/innlab/template/config/SecurityConfig.kt
    - src/main/resources/application.yaml

key-decisions:
  - "Use springdoc-openapi-starter-webmvc-ui 3.0.2 — only active Spring Boot 4 compatible OpenAPI library"
  - "Add spring-boot-jackson2 stop-gap — required because swagger-core still depends on Jackson 2 while Boot 4 ships Jackson 3; remove when springdoc upgrades"
  - "Global bearerAuth security scheme via addSecurityItem in OpenApiConfig — applies to all endpoints; public auth endpoints will need @Operation(security=[]) override in future plan"
  - "springdoc config placed at top level (not profile-specific) — Swagger should work in all profiles"

patterns-established:
  - "OpenApiConfig.kt: single @Configuration class with @Bean fun openApi() for API metadata + security scheme"
  - "SecurityConfig: Swagger paths permitted before /api/** catch-all rules to ensure unauthenticated access"

requirements-completed: [SWAGGER-01, SWAGGER-02, SWAGGER-03]

# Metrics
duration: 2min
completed: 2026-03-18
---

# Phase 3 Plan 01: Add Swagger/OpenAPI Summary

**springdoc-openapi 3.0.2 with global JWT bearerAuth scheme, Swagger UI at /swagger-ui/index.html permitted without authentication, Jackson 2/3 stop-gap via spring-boot-jackson2**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-18T06:05:36Z
- **Completed:** 2026-03-18T06:07:22Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Added springdoc-openapi-starter-webmvc-ui:3.0.2 and spring-boot-jackson2 to pom.xml
- Created OpenApiConfig.kt with API metadata and global JWT bearerAuth security scheme
- Permitted /swagger-ui/**, /swagger-ui.html, /v3/api-docs/**, /v3/api-docs.yaml in SecurityConfig without authentication
- Added springdoc YAML config block with method sorting, try-it-out enabled, and package scan scope

## Task Commits

Each task was committed atomically:

1. **Task 1: Add springdoc dependencies and OpenAPI config** - `db08b14` (feat)
2. **Task 2: Permit Swagger paths in SecurityConfig and add springdoc properties** - `d545564` (feat)

**Plan metadata:** (docs commit — see final commit hash)

## Files Created/Modified
- `pom.xml` - Added springdoc-openapi-starter-webmvc-ui:3.0.2 and spring-boot-jackson2 dependencies
- `src/main/kotlin/kz/innlab/template/config/OpenApiConfig.kt` - New: OpenAPI bean with JWT bearerAuth security scheme and API metadata
- `src/main/kotlin/kz/innlab/template/config/SecurityConfig.kt` - Added 4 permitAll rules for Swagger UI and OpenAPI spec paths
- `src/main/resources/application.yaml` - Added springdoc top-level configuration block

## Decisions Made
- Use springdoc-openapi-starter-webmvc-ui 3.0.2: only active OpenAPI library compatible with Spring Boot 4
- Add spring-boot-jackson2 as stop-gap: swagger-core still depends on Jackson 2 while Boot 4 ships Jackson 3; without it the application fails to start with `IllegalArgumentException: Conflicting setter definitions`
- Global bearerAuth security scheme via `addSecurityItem` on the OpenAPI bean: applies lock icon to all endpoints; public auth endpoints will need `@Operation(security = [])` override in a future plan if needed
- springdoc config placed at top-level (not profile-specific): Swagger should be accessible in all environments

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Maven compile completed successfully with no errors after both tasks.

## User Setup Required

None - Swagger UI will be accessible at http://localhost:7070/swagger-ui/index.html once application starts with a database connection.

## Next Phase Readiness
- Swagger UI infrastructure complete; ready for plan 03-02 if it exists
- Consider adding `@Operation(security = [])` to public auth endpoints to remove misleading lock icon
- Consider adding `@Parameter(hidden = true)` to `@AuthenticationPrincipal Jwt` parameters in controllers to keep generated spec clean

---
*Phase: 03-add-swagger-openapi*
*Completed: 2026-03-18*
