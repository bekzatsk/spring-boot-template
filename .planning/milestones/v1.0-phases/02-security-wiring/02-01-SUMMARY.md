---
phase: 02-security-wiring
plan: 01
subsystem: security
tags: [security, jwt, cors, error-handling, spring-security, oauth2-resource-server]
dependency_graph:
  requires: [01-02]
  provides: [SecurityFilterChain, ErrorResponse, GlobalExceptionHandler, ApiAuthenticationEntryPoint, ApiAccessDeniedHandler, CorsProperties]
  affects: [03-google-auth, 04-apple-auth, 05-token-management]
tech_stack:
  added: []
  patterns:
    - stateless OAuth2 resource server with JwtDecoder from Phase 1
    - ConfigurationProperties for externalized CORS origins
    - dual registration of AuthenticationEntryPoint in both exceptionHandling and oauth2ResourceServer DSL
    - Jackson 3.x uses tools.jackson.databind package namespace (not com.fasterxml.jackson)
key_files:
  created:
    - src/main/kotlin/kz/innlab/template/config/SecurityConfig.kt
    - src/main/kotlin/kz/innlab/template/config/CorsProperties.kt
    - src/main/kotlin/kz/innlab/template/authentication/error/ApiAuthenticationEntryPoint.kt
    - src/main/kotlin/kz/innlab/template/authentication/error/ApiAccessDeniedHandler.kt
    - src/main/kotlin/kz/innlab/template/shared/error/ErrorResponse.kt
    - src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt
  modified:
    - src/main/resources/application.yaml
    - .env.example
decisions:
  - Jackson 3.x (tools.jackson.* namespace) used for ObjectMapper in error handlers — not com.fasterxml.jackson
  - AuthenticationEntryPoint registered in both exceptionHandling and oauth2ResourceServer DSL per research Pitfall 4
metrics:
  duration: 8 min
  completed: 2026-03-01
  tasks_completed: 2
  files_changed: 8
---

# Phase 2 Plan 1: Security Wiring — SecurityFilterChain and Error Handling Summary

**One-liner:** Stateless OAuth2 resource server with RS256 JWT, CORS from CorsProperties, and consistent {error, message, status} JSON for 401/403/400/500.

## What Was Built

Spring Security 7 configured as a stateless OAuth2 resource server that:
- Validates Bearer tokens via the `JwtDecoder` bean from Phase 1 (RsaKeyConfig) — no custom filter needed
- Routes all `/api/**` endpoints through authentication (except `/api/v1/auth/**` which is `permitAll`)
- Passes `OPTIONS` preflight requests before JWT validation via CORS filter ordering
- Returns JSON `{ error, message, status }` for every error status (401, 403, 400, 500)
- Externalizes CORS allowed origins via `@ConfigurationProperties` (not hardcoded)

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | SecurityFilterChain with CORS, error handlers, and app config | a2dd845 | SecurityConfig.kt, CorsProperties.kt, ApiAuthenticationEntryPoint.kt, ApiAccessDeniedHandler.kt, ErrorResponse.kt, application.yaml, .env.example |
| 2 | GlobalExceptionHandler for controller-layer error handling | 0848e5d | GlobalExceptionHandler.kt |

## Decisions Made

1. **Jackson 3.x package:** The project uses `tools.jackson.module:jackson-module-kotlin` (Jackson 3.x), which ships under the `tools.jackson.*` namespace. `ObjectMapper` must be imported as `tools.jackson.databind.ObjectMapper`, not `com.fasterxml.jackson.databind.ObjectMapper`. Applied as Rule 1 auto-fix during Task 1.

2. **Dual AuthenticationEntryPoint registration:** The custom entry point is registered in both `exceptionHandling { }` and `oauth2ResourceServer { }` DSL blocks. This ensures 401 JSON is returned regardless of whether the exception originates from a missing token (filter-level) or an invalid/expired token (JWT decoder-level). Per research Pitfall 4.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Jackson 3.x package namespace mismatch**
- **Found during:** Task 1 — first mvn compile
- **Issue:** The plan instructed importing `com.fasterxml.jackson.databind.ObjectMapper`. However, the project's `pom.xml` uses `tools.jackson.module:jackson-module-kotlin` (Jackson 3.x), which ships under the `tools.jackson.*` namespace instead of `com.fasterxml.jackson.*`. This caused `Unresolved reference 'databind'` compilation errors in both `ApiAuthenticationEntryPoint.kt` and `ApiAccessDeniedHandler.kt`.
- **Fix:** Changed import to `tools.jackson.databind.ObjectMapper` in both files.
- **Files modified:** `ApiAuthenticationEntryPoint.kt`, `ApiAccessDeniedHandler.kt`
- **Commit:** a2dd845 (included in Task 1 commit)

## Self-Check: PASSED

All 6 created Kotlin files exist. SUMMARY.md exists. Both task commits (a2dd845, 0848e5d) verified in git log. Project compiles with zero errors.
