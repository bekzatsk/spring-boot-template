---
phase: 06-restructure-project-into-layered-packages-config-user-authentication-with-model-repository-service-controller-dto-exception-subpackages
plan: "02"
subsystem: project-structure
tags: [refactor, packaging, authentication, layered-architecture, rename]
dependency_graph:
  requires: [06-01]
  provides: [authentication.service, authentication.controller]
  affects: [authentication, tests]
tech_stack:
  added: []
  patterns: [layered-package-structure, domain-subpackages]
key_files:
  created:
    - src/main/kotlin/kz/innlab/template/authentication/service/TokenService.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/GoogleOAuth2Service.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/AppleOAuth2Service.kt
    - src/main/kotlin/kz/innlab/template/authentication/service/RefreshTokenService.kt
    - src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt
  modified:
    - src/test/kotlin/kz/innlab/template/SecurityIntegrationTest.kt
    - src/test/kotlin/kz/innlab/template/AppleAuthIntegrationTest.kt
  deleted:
    - src/main/kotlin/kz/innlab/template/authentication/JwtTokenService.kt
    - src/main/kotlin/kz/innlab/template/authentication/GoogleAuthService.kt
    - src/main/kotlin/kz/innlab/template/authentication/AppleAuthService.kt
    - src/main/kotlin/kz/innlab/template/authentication/RefreshTokenService.kt
    - src/main/kotlin/kz/innlab/template/authentication/AuthController.kt
decisions:
  - "JwtTokenService renamed to TokenService — clearer name without JWT implementation detail leaking into service name"
  - "GoogleAuthService renamed to GoogleOAuth2Service — explicit OAuth2 protocol reference matches AppleOAuth2Service naming symmetry"
  - "AppleAuthService renamed to AppleOAuth2Service — symmetric naming with GoogleOAuth2Service"
  - "RefreshTokenService and AuthController moved to sub-packages without rename — names were already correct"
  - "Clean Maven build (mvnw clean test) required after moving compiled classes — incremental compile left stale .class files causing ConflictingBeanDefinitionException"
metrics:
  duration: "4 minutes"
  completed_date: "2026-03-01"
  tasks_completed: 2
  files_created: 5
  files_modified: 2
  files_deleted: 5
---

# Phase 06 Plan 02: Authentication Services and Controller Restructure Summary

Authentication services moved to authentication/service/ (3 renamed: JwtTokenService → TokenService, GoogleAuthService → GoogleOAuth2Service, AppleAuthService → AppleOAuth2Service), AuthController moved to authentication/controller/, 5 old root-level files deleted, 2 test files updated, all 9 tests pass with clean build.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Move and rename authentication services and controller | 275799b | authentication/service/TokenService.kt, authentication/service/GoogleOAuth2Service.kt, authentication/service/AppleOAuth2Service.kt, authentication/service/RefreshTokenService.kt, authentication/controller/AuthController.kt |
| 2 | Update test file imports and run full test suite | a38d96b | SecurityIntegrationTest.kt, AppleAuthIntegrationTest.kt |

## What Was Built

### Authentication Services Restructure (Task 1)

Moved 5 files from flat `authentication/` root into layered sub-packages with 3 class renames:

- `authentication/service/TokenService.kt` — renamed from JwtTokenService; package changed to `authentication.service`; class name simplified from JwtTokenService to TokenService
- `authentication/service/GoogleOAuth2Service.kt` — renamed from GoogleAuthService; package changed to `authentication.service`; `jwtTokenService: JwtTokenService` parameter renamed to `tokenService: TokenService` with usage updated; RefreshTokenService stays same-package (no import needed)
- `authentication/service/AppleOAuth2Service.kt` — renamed from AppleAuthService; package changed to `authentication.service`; same changes as GoogleOAuth2Service; `@Qualifier("appleJwtDecoder")` preserved unchanged (bean name reference, not class reference)
- `authentication/service/RefreshTokenService.kt` — moved only; package changed to `authentication.service`; all sub-package imports already set by Plan 01 (exception, model, repository)
- `authentication/controller/AuthController.kt` — moved only; package changed to `authentication.controller`; 4 explicit imports added for services (all were same-package before move); constructor parameters renamed: `googleAuthService → googleOAuth2Service`, `appleAuthService → appleOAuth2Service`, `jwtTokenService → tokenService`; all method body usages updated accordingly

Deleted 5 old root-level files: JwtTokenService.kt, GoogleAuthService.kt, AppleAuthService.kt, RefreshTokenService.kt, AuthController.kt

### Test File Import Updates (Task 2)

Updated both integration test files with new package paths and renamed class:

**SecurityIntegrationTest.kt:**
- `authentication.JwtTokenService` → `authentication.service.TokenService`
- `user.AuthProvider` → `user.model.AuthProvider`
- `user.Role` → `user.model.Role`
- `user.User` → `user.model.User`
- `user.UserRepository` → `user.repository.UserRepository`
- `jwtTokenService: JwtTokenService` field → `tokenService: TokenService`
- 2 usages of `jwtTokenService.generateAccessToken(...)` → `tokenService.generateAccessToken(...)`

**AppleAuthIntegrationTest.kt:**
- `authentication.JwtTokenService` → `authentication.service.TokenService`
- `authentication.RefreshTokenRepository` → `authentication.repository.RefreshTokenRepository`
- `user.AuthProvider` → `user.model.AuthProvider`
- `user.User` → `user.model.User`
- `user.UserRepository` → `user.repository.UserRepository`
- `jwtTokenService: JwtTokenService` field → `tokenService: TokenService`

## Verification Results

- `./mvnw clean test` — PASSED with 9/9 tests green, zero compilation errors, zero test failures
- No `.kt` files remain directly in `authentication/` root (only sub-directories: controller/, dto/, exception/, filter/, model/, repository/, service/)
- `grep "^package kz.innlab.template.authentication\b"` returns zero matches (all files are in sub-packages)
- `grep "^package kz.innlab.template.user\b"` returns zero matches
- `grep "JwtTokenService\|GoogleAuthService\|AppleAuthService\|GlobalExceptionHandler" src/` returns zero matches

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Stale compiled .class files caused ConflictingBeanDefinitionException**
- **Found during:** Task 2 (first test run)
- **Issue:** Maven incremental compilation left `target/classes/kz/innlab/template/authentication/AuthController.class` in place after the source file was deleted and recreated at a new package path. Spring component scan detected both `authentication.AuthController` and `authentication.controller.AuthController` as candidates for the `authController` bean name, causing `ConflictingBeanDefinitionException` at context load time.
- **Fix:** Ran `./mvnw clean test` instead of `./mvnw test` to fully clear the `target/classes/` directory before recompiling.
- **Files modified:** None (build output only)
- **Commit:** a38d96b (tests passed after clean build)

## Self-Check: PASSED

Files created:
- FOUND: src/main/kotlin/kz/innlab/template/authentication/service/TokenService.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/service/GoogleOAuth2Service.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/service/AppleOAuth2Service.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/service/RefreshTokenService.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/controller/AuthController.kt

Commits:
- FOUND: 275799b (Task 1 — move and rename services/controller)
- FOUND: a38d96b (Task 2 — test import updates)

Tests: 9/9 PASSED
