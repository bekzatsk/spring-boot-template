---
phase: 06-restructure-project-into-layered-packages-config-user-authentication-with-model-repository-service-controller-dto-exception-subpackages
plan: "01"
subsystem: project-structure
tags: [refactor, packaging, user-domain, authentication, layered-architecture]
dependency_graph:
  requires: []
  provides: [user.model, user.repository, user.service, user.controller, user.dto, authentication.model, authentication.repository, authentication.exception, authentication.filter]
  affects: [user, authentication, config, shared]
tech_stack:
  added: []
  patterns: [layered-package-structure, domain-subpackages]
key_files:
  created:
    - src/main/kotlin/kz/innlab/template/user/model/User.kt
    - src/main/kotlin/kz/innlab/template/user/model/AuthProvider.kt
    - src/main/kotlin/kz/innlab/template/user/model/Role.kt
    - src/main/kotlin/kz/innlab/template/user/repository/UserRepository.kt
    - src/main/kotlin/kz/innlab/template/user/dto/UserProfileResponse.kt
    - src/main/kotlin/kz/innlab/template/user/service/UserService.kt
    - src/main/kotlin/kz/innlab/template/user/controller/UserController.kt
    - src/main/kotlin/kz/innlab/template/authentication/model/RefreshToken.kt
    - src/main/kotlin/kz/innlab/template/authentication/repository/RefreshTokenRepository.kt
    - src/main/kotlin/kz/innlab/template/authentication/exception/TokenGracePeriodException.kt
    - src/main/kotlin/kz/innlab/template/authentication/exception/AuthExceptionHandler.kt
    - src/main/kotlin/kz/innlab/template/authentication/filter/ApiAuthenticationEntryPoint.kt
    - src/main/kotlin/kz/innlab/template/authentication/filter/ApiAccessDeniedHandler.kt
  modified:
    - src/main/kotlin/kz/innlab/template/authentication/RefreshTokenService.kt
    - src/main/kotlin/kz/innlab/template/authentication/JwtTokenService.kt
    - src/main/kotlin/kz/innlab/template/authentication/GoogleAuthService.kt
    - src/main/kotlin/kz/innlab/template/authentication/AppleAuthService.kt
    - src/main/kotlin/kz/innlab/template/config/SecurityConfig.kt
  deleted:
    - src/main/kotlin/kz/innlab/template/user/User.kt
    - src/main/kotlin/kz/innlab/template/user/AuthProvider.kt
    - src/main/kotlin/kz/innlab/template/user/Role.kt
    - src/main/kotlin/kz/innlab/template/user/UserRepository.kt
    - src/main/kotlin/kz/innlab/template/user/UserProfileResponse.kt
    - src/main/kotlin/kz/innlab/template/user/UserService.kt
    - src/main/kotlin/kz/innlab/template/user/UserController.kt
    - src/main/kotlin/kz/innlab/template/authentication/RefreshToken.kt
    - src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt
    - src/main/kotlin/kz/innlab/template/authentication/error/ApiAuthenticationEntryPoint.kt
    - src/main/kotlin/kz/innlab/template/authentication/error/ApiAccessDeniedHandler.kt
    - src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt
decisions:
  - "GlobalExceptionHandler renamed to AuthExceptionHandler and placed in authentication/exception — semantically belongs with auth domain, not shared error utilities"
  - "TokenGracePeriodException extracted from RefreshTokenService into its own file — single-responsibility for exception definitions"
  - "ErrorResponse kept in shared/error (not moved) — generic DTO used by both authentication and user domains"
  - "authentication/error/ package replaced by authentication/filter/ — filter handlers belong with filter layer, not generic error handling"
metrics:
  duration: "3 minutes"
  completed_date: "2026-03-01"
  tasks_completed: 2
  files_created: 13
  files_modified: 5
  files_deleted: 12
---

# Phase 06 Plan 01: User Domain and Authentication Foundation Restructure Summary

User and authentication domains reorganized into layered sub-packages (model/repository/service/controller/dto/exception/filter) with all 13 files in new locations, 12 old files deleted, and production compilation verified passing.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Move user domain into layered sub-packages | e62e18d | user/model/User.kt, user/repository/UserRepository.kt, user/service/UserService.kt, user/controller/UserController.kt, user/dto/UserProfileResponse.kt |
| 2 | Move authentication models, repository, exceptions, and filter handlers | a0c88c9 | authentication/model/RefreshToken.kt, authentication/repository/RefreshTokenRepository.kt, authentication/exception/AuthExceptionHandler.kt, authentication/exception/TokenGracePeriodException.kt, authentication/filter/ApiAuthenticationEntryPoint.kt, authentication/filter/ApiAccessDeniedHandler.kt |

## What Was Built

### User Domain Restructure (Task 1)

Moved 7 files from flat `user/` root into layered sub-packages:

- `user/model/` — User entity, AuthProvider enum, Role enum (package declaration updated from `kz.innlab.template.user` to `kz.innlab.template.user.model`; AuthProvider and Role remain same-package relative to User, so no new cross-imports needed within model/)
- `user/repository/` — UserRepository (added explicit imports for User and AuthProvider from user.model)
- `user/dto/` — UserProfileResponse (added explicit import for User from user.model)
- `user/service/` — UserService (added explicit imports for AuthProvider, User from user.model; UserRepository from user.repository)
- `user/controller/` — UserController (added explicit imports for UserProfileResponse from user.dto; UserService from user.service)

### Authentication Foundation Restructure (Task 2)

Moved 6 files and updated 5 existing files:

- `authentication/model/` — RefreshToken entity (updated import from `user.User` to `user.model.User`)
- `authentication/repository/` — RefreshTokenRepository (updated User import; added RefreshToken import from authentication.model)
- `authentication/exception/` — TokenGracePeriodException (extracted from RefreshTokenService into own file); AuthExceptionHandler (renamed from GlobalExceptionHandler, moved from shared/error to authentication/exception, TokenGracePeriodException now same-package)
- `authentication/filter/` — ApiAuthenticationEntryPoint and ApiAccessDeniedHandler (moved from authentication/error; package only changed, content identical)
- `authentication/error/` directory deleted (now empty)

**Files patched in place (remain at authentication/ root for Plan 02 to move):**
- `RefreshTokenService.kt` — inline TokenGracePeriodException class removed; imports added for exception, model, repository sub-packages
- `JwtTokenService.kt` — Role import updated to user.model.Role
- `GoogleAuthService.kt` — UserService import updated to user.service.UserService
- `AppleAuthService.kt` — UserService import updated to user.service.UserService
- `SecurityConfig.kt` — ApiAuthenticationEntryPoint and ApiAccessDeniedHandler imports updated from authentication.error to authentication.filter

## Verification Results

- `./mvnw compile` — PASSED with zero errors
- No `.kt` files remain directly in `user/` root (only sub-directories: model/, repository/, service/, controller/, dto/)
- `authentication/error/` directory deleted
- `shared/error/ErrorResponse.kt` still exists (intentionally not moved)
- `grep "^package kz.innlab.template.user$"` returns zero matches

## Deviations from Plan

None — plan executed exactly as written. All steps followed the prescribed order. AuthController was verified to have no user imports to fix (only imports from authentication.dto which did not move).

## Self-Check: PASSED

Files created:
- FOUND: src/main/kotlin/kz/innlab/template/user/model/User.kt
- FOUND: src/main/kotlin/kz/innlab/template/user/model/AuthProvider.kt
- FOUND: src/main/kotlin/kz/innlab/template/user/model/Role.kt
- FOUND: src/main/kotlin/kz/innlab/template/user/repository/UserRepository.kt
- FOUND: src/main/kotlin/kz/innlab/template/user/dto/UserProfileResponse.kt
- FOUND: src/main/kotlin/kz/innlab/template/user/service/UserService.kt
- FOUND: src/main/kotlin/kz/innlab/template/user/controller/UserController.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/model/RefreshToken.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/repository/RefreshTokenRepository.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/exception/TokenGracePeriodException.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/exception/AuthExceptionHandler.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/filter/ApiAuthenticationEntryPoint.kt
- FOUND: src/main/kotlin/kz/innlab/template/authentication/filter/ApiAccessDeniedHandler.kt

Commits:
- FOUND: e62e18d (Task 1 — user domain)
- FOUND: a0c88c9 (Task 2 — authentication foundation)

Compile: PASSED
