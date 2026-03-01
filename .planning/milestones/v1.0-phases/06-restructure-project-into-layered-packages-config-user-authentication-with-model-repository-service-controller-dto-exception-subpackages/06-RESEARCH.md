# Phase 6: Restructure Project into Layered Packages - Research

**Researched:** 2026-03-01
**Domain:** Kotlin/Spring Boot package restructuring — file moves, renames, import updates
**Confidence:** HIGH

## Summary

Phase 6 is a pure structural refactoring. No logic changes, no new features. The project
currently uses a partially-layered package layout where most types live at the top of their
domain package (e.g. `authentication.RefreshToken`, `user.User`). The target moves each type
into an explicit sub-package (`model/`, `repository/`, `service/`, `controller/`, `dto/`,
`filter/`, `exception/`) and renames several classes to clarify their responsibilities.

The Spring Boot component-scan and JPA entity-scan implications are minimal because
`@SpringBootApplication` on `TemplateApplication` (in `kz.innlab.template`) already scans
all sub-packages recursively. Moving files deeper does not require any scan configuration
change — JPA finds all `@Entity` classes anywhere under the root package automatically.

The highest-risk items in this phase are: (1) the `GlobalExceptionHandler` in
`kz.innlab.template.shared.error` imports `TokenGracePeriodException` from
`kz.innlab.template.authentication` — this import must be updated once the exception moves;
(2) the three test files have direct imports of authentication and user types that must be
updated to new package paths; (3) `SecurityConfig` imports
`authentication.error.ApiAccessDeniedHandler` and `authentication.error.ApiAuthenticationEntryPoint`
— those two files are not listed in the target structure, so their destination must be decided.

**Primary recommendation:** Move files one domain at a time (user first, then authentication,
then config cleanup), update all imports immediately after each move, run tests after each
domain. Do not rename and move in the same step — move first, then rename.

---

## Current File Layout vs. Target Layout

### Complete File-by-File Mapping

| Current Path | Target Path | Change Type |
|---|---|---|
| `kz.innlab.template.TemplateApplication` | `kz.innlab.template.TemplateApplication` | no change |
| `kz.innlab.template.config.SecurityConfig` | `kz.innlab.template.config.SecurityConfig` | no change |
| `kz.innlab.template.config.AppleAuthConfig` | `kz.innlab.template.config.AppleAuthConfig` | no change |
| `kz.innlab.template.config.GoogleAuthConfig` | `kz.innlab.template.config.GoogleAuthConfig` | no change |
| `kz.innlab.template.config.RsaKeyConfig` | `kz.innlab.template.config.RsaKeyConfig` | no change |
| `kz.innlab.template.config.CorsProperties` | `kz.innlab.template.config.CorsProperties` | no change |
| `kz.innlab.template.user.User` | `kz.innlab.template.user.model.User` | move to subpackage |
| `kz.innlab.template.user.AuthProvider` | `kz.innlab.template.user.model.AuthProvider` | move to subpackage |
| `kz.innlab.template.user.Role` | `kz.innlab.template.user.model.Role` | move to subpackage |
| `kz.innlab.template.user.UserRepository` | `kz.innlab.template.user.repository.UserRepository` | move to subpackage |
| `kz.innlab.template.user.UserService` | `kz.innlab.template.user.service.UserService` | move to subpackage |
| `kz.innlab.template.user.UserController` | `kz.innlab.template.user.controller.UserController` | move to subpackage |
| `kz.innlab.template.user.UserProfileResponse` | `kz.innlab.template.user.dto.UserProfileResponse` | move to subpackage |
| `kz.innlab.template.authentication.RefreshToken` | `kz.innlab.template.authentication.model.RefreshToken` | move to subpackage |
| `kz.innlab.template.authentication.RefreshTokenRepository` | `kz.innlab.template.authentication.repository.RefreshTokenRepository` | move to subpackage |
| `kz.innlab.template.authentication.RefreshTokenService` | `kz.innlab.template.authentication.service.RefreshTokenService` | move to subpackage |
| `kz.innlab.template.authentication.JwtTokenService` | `kz.innlab.template.authentication.service.TokenService` | move + rename |
| `kz.innlab.template.authentication.GoogleAuthService` | `kz.innlab.template.authentication.service.GoogleOAuth2Service` | move + rename |
| `kz.innlab.template.authentication.AppleAuthService` | `kz.innlab.template.authentication.service.AppleOAuth2Service` | move + rename |
| `kz.innlab.template.authentication.AuthController` | `kz.innlab.template.authentication.controller.AuthController` | move to subpackage |
| `kz.innlab.template.authentication.dto.AuthRequest` | `kz.innlab.template.authentication.dto.AuthRequest` | no change (already in dto) |
| `kz.innlab.template.authentication.dto.AppleAuthRequest` | `kz.innlab.template.authentication.dto.AppleAuthRequest` | no change (already in dto) |
| `kz.innlab.template.authentication.dto.AuthResponse` | `kz.innlab.template.authentication.dto.AuthResponse` | no change (already in dto) |
| `kz.innlab.template.authentication.dto.RefreshRequest` | `kz.innlab.template.authentication.dto.RefreshRequest` | no change (already in dto) |
| `kz.innlab.template.authentication.error.ApiAuthenticationEntryPoint` | `kz.innlab.template.authentication.filter.ApiAuthenticationEntryPoint` OR `kz.innlab.template.config.ApiAuthenticationEntryPoint` | destination unclear — see open questions |
| `kz.innlab.template.authentication.error.ApiAccessDeniedHandler` | `kz.innlab.template.authentication.filter.ApiAccessDeniedHandler` OR `kz.innlab.template.config.ApiAccessDeniedHandler` | destination unclear — see open questions |
| `kz.innlab.template.shared.error.ErrorResponse` | `kz.innlab.template.authentication.exception.ErrorResponse` OR keep in `shared` | destination unclear — see open questions |
| `kz.innlab.template.shared.error.GlobalExceptionHandler` | `kz.innlab.template.authentication.exception.AuthExceptionHandler` | move + rename |
| `kz.innlab.template.authentication.TokenGracePeriodException` (in RefreshTokenService.kt) | `kz.innlab.template.authentication.exception.TokenGracePeriodException` | extract to own file + move |

### Files Not in Target Structure (Decisions Needed)

The target structure provided does not list these existing files:

| File | Status | Recommended Decision |
|---|---|---|
| `authentication.error.ApiAuthenticationEntryPoint` | Not in target | Move to `authentication.filter` alongside `JwtAuthenticationFilter` (fits — it is a security filter-layer component) |
| `authentication.error.ApiAccessDeniedHandler` | Not in target | Move to `authentication.filter` (same reasoning) |
| `user.dto.UserProfileResponse` | Target shows `user/` has no `dto/` but response type exists | Create `user/dto/UserProfileResponse.kt` — the response is user-domain data |
| `shared.error.ErrorResponse` | Not in target | Move to `authentication.exception.ErrorResponse` if making it auth-domain specific, OR keep as `shared.error.ErrorResponse` (used by both ApiAuthenticationEntryPoint AND GlobalExceptionHandler) |
| `user.Role` | Target does not list it explicitly | Move to `user.model.Role` with User and AuthProvider |

---

## Architecture Patterns

### Spring Boot Component Scan — No Action Required

`@SpringBootApplication` on `TemplateApplication` at `kz.innlab.template` triggers recursive
scanning of ALL sub-packages. This is standard Spring Boot behavior. Moving `User.kt` to
`kz.innlab.template.user.model` does NOT require adding any `@ComponentScan`, `@EntityScan`,
or `@EnableJpaRepositories` annotation.

**Verified:** Spring Boot's `@SpringBootApplication` includes `@EnableAutoConfiguration` which
sets up entity scanning from the root package downwards automatically (HIGH confidence — this
is foundational Spring Boot behavior unchanged across versions).

### JPA Entity Scan — No Action Required

Hibernate + Spring Data JPA discovers `@Entity` classes via the JPA persistence provider's
classpath scan, which scans from the `@SpringBootApplication` package root. Moving
`User.kt` from `user.User` to `user.model.User` and `RefreshToken.kt` from
`authentication.RefreshToken` to `authentication.model.RefreshToken` requires NO `@EntityScan`
annotation.

**Only exception:** If `@EntityScan` were already explicitly set to a specific package,
it would need updating. It is NOT currently set in this project — confirmed by reading
`TemplateApplication.kt`.

### Bean Names Survive Renames — Verify Qualifier

`AppleAuthService` is injected by type in `AuthController`. After rename to `AppleOAuth2Service`
and `GoogleOAuth2Service`, Spring still injects by type — no issues.

The `appleJwtDecoder` bean is named explicitly via `@Bean(name = ["appleJwtDecoder"])` in
`AppleAuthConfig`. The `AppleAuthService`/`AppleOAuth2Service` injects it with
`@Qualifier("appleJwtDecoder")`. These are by-name references and will continue to work after
the service class rename since the qualifier is on the bean definition in `AppleAuthConfig`,
not on the service class.

`JwtTokenService` renamed to `TokenService` — used in `AuthController` and test files. After
rename, the field type changes, which means:
- `AuthController` constructor param: `jwtTokenService: JwtTokenService` → `tokenService: TokenService`
- `SecurityIntegrationTest`: `@Autowired lateinit var jwtTokenService: JwtTokenService` → new class name
- `AppleAuthIntegrationTest`: `@Autowired lateinit var jwtTokenService: JwtTokenService` → new class name

### Package Declaration and Import Updates — Mechanical Pattern

Each moved file needs:
1. `package` declaration updated to new path
2. All imports of old package paths updated in every file that references the moved type

This is a pure find-and-replace operation with no logic changes.

---

## Complete Import Dependency Graph

Understanding which file imports which class is critical for planning the order of operations.

### Imports That Will Break and Must Be Fixed

| File Being Changed | Imports That Break After Moves |
|---|---|
| `authentication.service.RefreshTokenService` (moved) | imports `kz.innlab.template.user.User` → `kz.innlab.template.user.model.User` |
| `authentication.service.TokenService` (moved+renamed) | imports `kz.innlab.template.user.Role` → `kz.innlab.template.user.model.Role` |
| `authentication.service.GoogleOAuth2Service` (moved+renamed) | imports `kz.innlab.template.user.UserService` → `kz.innlab.template.user.service.UserService` |
| `authentication.service.AppleOAuth2Service` (moved+renamed) | imports `kz.innlab.template.user.UserService` → `kz.innlab.template.user.service.UserService` |
| `authentication.model.RefreshToken` (moved) | imports `kz.innlab.template.user.User` → `kz.innlab.template.user.model.User` |
| `authentication.repository.RefreshTokenRepository` (moved) | imports `kz.innlab.template.user.User` → `kz.innlab.template.user.model.User`; imports `kz.innlab.template.authentication.RefreshToken` → `...authentication.model.RefreshToken` |
| `authentication.controller.AuthController` (moved) | imports old `GoogleAuthService`, `AppleAuthService`, `RefreshTokenService`, `JwtTokenService` → all new names and packages |
| `authentication.exception.AuthExceptionHandler` (moved+renamed) | imports `kz.innlab.template.authentication.TokenGracePeriodException` → new exception package |
| `authentication.filter.ApiAuthenticationEntryPoint` (moved) | imports `kz.innlab.template.shared.error.ErrorResponse` — decision needed on ErrorResponse destination |
| `authentication.filter.ApiAccessDeniedHandler` (moved) | imports `kz.innlab.template.shared.error.ErrorResponse` — decision needed on ErrorResponse destination |
| `config.SecurityConfig` (stays) | imports `kz.innlab.template.authentication.error.ApiAccessDeniedHandler` → new package; `kz.innlab.template.authentication.error.ApiAuthenticationEntryPoint` → new package |
| `user.service.UserService` (moved) | imports `kz.innlab.template.user.UserRepository` → `...user.repository.UserRepository`; imports `kz.innlab.template.user.User` → `...user.model.User`; imports `kz.innlab.template.user.AuthProvider` → `...user.model.AuthProvider` |
| `user.controller.UserController` (moved) | imports `kz.innlab.template.user.UserService` → `...user.service.UserService`; `kz.innlab.template.user.UserProfileResponse` → `...user.dto.UserProfileResponse` |
| `user.dto.UserProfileResponse` (moved) | imports `kz.innlab.template.user.User` → `...user.model.User` |
| `user.repository.UserRepository` (moved) | imports `kz.innlab.template.user.User` → `...user.model.User`; `kz.innlab.template.user.AuthProvider` → `...user.model.AuthProvider` |

### Test Files — Imports That Must Update

| Test File | Imports to Update |
|---|---|
| `SecurityIntegrationTest.kt` | `kz.innlab.template.authentication.JwtTokenService` → `...authentication.service.TokenService`; `kz.innlab.template.authentication.dto.AuthRequest` (already correct); `kz.innlab.template.user.AuthProvider` → `...user.model.AuthProvider`; `kz.innlab.template.user.Role` → `...user.model.Role`; `kz.innlab.template.user.User` → `...user.model.User`; `kz.innlab.template.user.UserRepository` → `...user.repository.UserRepository` |
| `AppleAuthIntegrationTest.kt` | `kz.innlab.template.authentication.JwtTokenService` → `...authentication.service.TokenService`; `kz.innlab.template.authentication.RefreshTokenRepository` → `...authentication.repository.RefreshTokenRepository`; `kz.innlab.template.user.AuthProvider` → `...user.model.AuthProvider`; `kz.innlab.template.user.User` → `...user.model.User`; `kz.innlab.template.user.UserRepository` → `...user.repository.UserRepository` |
| `TemplateApplicationTests.kt` | No authentication/user imports — no change needed |

---

## Rename Decision: JwtTokenService → TokenService

The target structure names it `TokenService` (not `JwtTokenService`). The current class has
one method: `generateAccessToken`. The target shows `JwtTokenService + RefreshTokenService`
either "merged or renamed."

**Research finding:** The two services have different concerns:
- `JwtTokenService` — JWT encoding (stateless, no DB)
- `RefreshTokenService` — opaque token lifecycle (stateful, DB-backed)

Merging them would create a class with mixed concerns and dependencies (both `JwtEncoder`
AND `RefreshTokenRepository`). This is an anti-pattern for a template codebase.

**Recommendation:** Rename only. `JwtTokenService` → `TokenService`. Keep
`RefreshTokenService` as a separate class, also in `authentication.service`. The target
structure listing shows both `TokenService` and implicitly `RefreshTokenService` under
`authentication/service/` (the `TokenService` note says "JwtTokenService + RefreshTokenService
merged or renamed" but the structure only shows one entry, which is ambiguous).

**Decision to make explicit:** Keep them separate, rename `JwtTokenService` to `TokenService`.
This is the simplest change and preserves single-responsibility.

---

## Rename Decision: GoogleAuthService → GoogleOAuth2Service, AppleAuthService → AppleOAuth2Service

The target renames these to clarify they are OAuth2 protocol handlers, not generic auth
services. This is purely cosmetic — no logic changes. The new names align with the Spring
Security convention (e.g. `OAuth2UserService`, `OAuth2AuthorizationCodeAuthenticationProvider`).

---

## Rename Decision: GlobalExceptionHandler → AuthExceptionHandler

The `GlobalExceptionHandler` in `shared.error` handles:
1. `MethodArgumentNotValidException` — validation errors
2. `HandlerMethodValidationException` — validation errors
3. `BadCredentialsException` — auth errors
4. `TokenGracePeriodException` — auth-specific error
5. `Exception` — catch-all 500

Moving it to `authentication.exception.AuthExceptionHandler` is semantically reasonable since
all current exception handlers are auth-related. `ErrorResponse` is used by both the handler
and the filter-layer components (`ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler`).

**Implication:** `ErrorResponse` should either stay in `shared.error` (currently its location)
or move to `authentication.exception`. If moved to `authentication.exception`, the filter
components must also import from there.

---

## TokenGracePeriodException — Extract to Own File

`TokenGracePeriodException` is currently defined at the top of `RefreshTokenService.kt` (line 14):

```kotlin
class TokenGracePeriodException(message: String) : RuntimeException(message)
```

It must be extracted to its own file `authentication/exception/TokenGracePeriodException.kt`
as part of this restructure. The `GlobalExceptionHandler` (`AuthExceptionHandler` after rename)
imports it — this import must be updated.

---

## Architecture Patterns

### Recommended Restructure Order (Minimizes Breaking Imports)

**Wave 1 — User domain (lowest dependency count, touched by auth domain):**
1. Create `user/model/` — move `User.kt`, `AuthProvider.kt`, `Role.kt`
2. Create `user/repository/` — move `UserRepository.kt`, update its imports
3. Create `user/dto/` — move `UserProfileResponse.kt`, update its imports
4. Create `user/service/` — move `UserService.kt`, update its imports
5. Create `user/controller/` — move `UserController.kt`, update its imports

**Wave 2 — Authentication models and repositories:**
6. Create `authentication/model/` — move `RefreshToken.kt`, update its user imports
7. Create `authentication/repository/` — move `RefreshTokenRepository.kt`, update its imports

**Wave 3 — Authentication exceptions:**
8. Create `authentication/exception/` — extract `TokenGracePeriodException` to own file
9. Move `GlobalExceptionHandler` → `AuthExceptionHandler`, update its imports
10. Decide on `ErrorResponse` location (see open questions)

**Wave 4 — Authentication services (renames happen here):**
11. Move + rename `JwtTokenService` → `authentication/service/TokenService.kt`
12. Move + rename `GoogleAuthService` → `authentication/service/GoogleOAuth2Service.kt`
13. Move + rename `AppleAuthService` → `authentication/service/AppleOAuth2Service.kt`
14. Move `RefreshTokenService` → `authentication/service/RefreshTokenService.kt`

**Wave 5 — Authentication controller and filters:**
15. Move `AuthController` → `authentication/controller/AuthController.kt`, update imports
16. Move `authentication.error.*` → `authentication/filter/`, update imports
17. Update `SecurityConfig` to reference new filter package

**Wave 6 — Test import updates:**
18. Update all test file imports

### Anti-Patterns to Avoid

- **Rename and move simultaneously across many files:** Increases error surface. Move first,
  verify compile, then rename.
- **Deleting old files before updating all callers:** Will break compilation. Update every
  import before deleting the old file.
- **Forgetting test files:** Tests import `JwtTokenService`, `RefreshTokenRepository`,
  `User`, `AuthProvider`, `Role`, `UserRepository` directly. All must be updated.
- **Leaving `TokenGracePeriodException` embedded in `RefreshTokenService.kt`:** The
  `GlobalExceptionHandler`/`AuthExceptionHandler` imports it; keeping it co-located in another
  service file would be confusing in the new structure.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Updating all import paths | Manual text editing of every file | IntelliJ IDEA "Move File/Class" refactoring (updates imports automatically) | Catches all references including those in test files; handles Kotlin wildcards |
| Verifying nothing broke | Manual test of each endpoint | `./mvnw test` after each wave | Tests cover SecurityIntegrationTest + AppleAuthIntegrationTest + context load |

**Key insight:** The Maven build (`./mvnw compile`) catches import errors at compile time.
Running `./mvnw test` after each wave is the verification gate — the existing 9 tests
(all passing) will catch regressions.

---

## Common Pitfalls

### Pitfall 1: Forgetting to Update the `package` Declaration

**What goes wrong:** File is moved to new directory but `package kz.innlab.template.authentication`
declaration at line 1 is not updated. Code compiles if nothing external references the class by
package, but IntelliJ shows the class is in the wrong package, and Spring scans may load it
from two locations in edge cases.

**How to avoid:** Always update the `package` line first when editing a moved file.

### Pitfall 2: `SecurityConfig` Import of `authentication.error.*`

**What goes wrong:** `SecurityConfig` imports `ApiAuthenticationEntryPoint` and
`ApiAccessDeniedHandler` from `kz.innlab.template.authentication.error`. After move to
`kz.innlab.template.authentication.filter`, the import breaks and the app fails to start with
a `NoSuchBeanDefinitionException` or compile error.

**How to avoid:** Update `SecurityConfig` imports in the same step as moving the filter classes.

### Pitfall 3: `TokenGracePeriodException` Import in `GlobalExceptionHandler`

**What goes wrong:** `GlobalExceptionHandler` imports `kz.innlab.template.authentication.TokenGracePeriodException`
(currently defined inside `RefreshTokenService.kt`). After extraction to
`authentication.exception.TokenGracePeriodException`, the old import breaks. After rename of
`GlobalExceptionHandler` to `AuthExceptionHandler` AND move to `authentication.exception`, this
import becomes a same-package reference and the import line can be removed entirely.

**How to avoid:** Extract `TokenGracePeriodException` first, then update `AuthExceptionHandler`.

### Pitfall 4: Test `@Autowired` Fields Reference Old Class Names

**What goes wrong:** `SecurityIntegrationTest` has:
```kotlin
@Autowired
private lateinit var jwtTokenService: JwtTokenService
```
After renaming `JwtTokenService` to `TokenService`, this field type becomes unresolvable.
Spring Test will fail to inject it.

**How to avoid:** Update test files in Wave 6 as a dedicated step. After updating, run
`./mvnw test` to confirm all tests pass.

### Pitfall 5: `AppleAuthIntegrationTest` Uses `@MockitoBean(name = "appleJwtDecoder")`

This mock references the bean NAME `"appleJwtDecoder"` defined in `AppleAuthConfig`. The
`AppleAuthConfig` is in `kz.innlab.template.config` and is NOT moved. The bean name definition
stays intact. After renaming `AppleAuthService` to `AppleOAuth2Service`, the qualifier
`@Qualifier("appleJwtDecoder")` inside `AppleOAuth2Service` still works — the qualifier
references the bean name, not the service class name.

**No action needed** for this pitfall — just verify it remains correct after rename.

### Pitfall 6: `ErrorResponse` Is Used by Both `shared.error.*` and `authentication.error.*`

`ApiAuthenticationEntryPoint` and `ApiAccessDeniedHandler` both import
`kz.innlab.template.shared.error.ErrorResponse`. If `ErrorResponse` is moved to
`authentication.exception`, these filter-layer components must update their import.
If `ErrorResponse` stays in `shared.error`, the `AuthExceptionHandler` must import it from
there (cross-package import within the project, which is fine).

**How to avoid:** Make a clear decision early: keep `ErrorResponse` in `shared.error` OR
move it to `authentication.exception`. Recommend keeping in `shared.error` since it is used
by multiple packages (see Open Questions).

---

## Code Examples

### Correct `package` Declaration Pattern After Move

```kotlin
// BEFORE: src/main/kotlin/kz/innlab/template/user/User.kt
package kz.innlab.template.user

// AFTER: src/main/kotlin/kz/innlab/template/user/model/User.kt
package kz.innlab.template.user.model
```

### TokenGracePeriodException Extraction

```kotlin
// NEW FILE: authentication/exception/TokenGracePeriodException.kt
package kz.innlab.template.authentication.exception

class TokenGracePeriodException(message: String) : RuntimeException(message)
```

Remove the class definition from `RefreshTokenService.kt` (currently lines 14-15).

### AuthExceptionHandler After Move (Same-Package Import Removed)

```kotlin
// BEFORE (shared.error.GlobalExceptionHandler):
package kz.innlab.template.shared.error

import kz.innlab.template.authentication.TokenGracePeriodException  // cross-package

// AFTER (authentication.exception.AuthExceptionHandler):
package kz.innlab.template.authentication.exception

// TokenGracePeriodException is in same package — no import needed
```

### SecurityConfig Import Update Pattern

```kotlin
// BEFORE:
import kz.innlab.template.authentication.error.ApiAccessDeniedHandler
import kz.innlab.template.authentication.error.ApiAuthenticationEntryPoint

// AFTER (if moved to authentication.filter):
import kz.innlab.template.authentication.filter.ApiAccessDeniedHandler
import kz.innlab.template.authentication.filter.ApiAuthenticationEntryPoint
```

### Test Import Update Pattern

```kotlin
// BEFORE (SecurityIntegrationTest):
import kz.innlab.template.authentication.JwtTokenService
import kz.innlab.template.user.AuthProvider
import kz.innlab.template.user.Role
import kz.innlab.template.user.User
import kz.innlab.template.user.UserRepository

// AFTER:
import kz.innlab.template.authentication.service.TokenService
import kz.innlab.template.user.model.AuthProvider
import kz.innlab.template.user.model.Role
import kz.innlab.template.user.model.User
import kz.innlab.template.user.repository.UserRepository
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| Flat package per domain (everything at domain root) | Sub-packages by layer (model/repo/service/controller/dto) | This phase | Clearer navigation, explicit layer boundaries |
| `TokenGracePeriodException` co-located in service file | Dedicated exception file in exception sub-package | This phase | Follows single-file-per-class Kotlin convention |

---

## Open Questions

### 1. Destination of `ApiAuthenticationEntryPoint` and `ApiAccessDeniedHandler`

**What we know:** Target structure shows `authentication/filter/JwtAuthenticationFilter.kt`.
The two handler classes are in `authentication.error` currently but are not listed in the
target structure. They are Spring Security filter-layer components.

**What's unclear:** Should they go into `authentication/filter/` or stay in a `config/` sub-area?

**Recommendation:** Move to `authentication/filter/` alongside the (future) `JwtAuthenticationFilter`.
These are request-lifecycle components that participate in the security filter chain.

### 2. Destination and Ownership of `ErrorResponse`

**What we know:** `ErrorResponse` is currently in `shared.error` and is used by:
- `ApiAuthenticationEntryPoint` (authentication.error → authentication.filter)
- `ApiAccessDeniedHandler` (authentication.error → authentication.filter)
- `GlobalExceptionHandler` → `AuthExceptionHandler` (authentication.exception)

**What's unclear:** The target structure does not list `ErrorResponse` or a `shared/` package.

**Recommendation:** Keep `ErrorResponse` in `kz.innlab.template.shared.error.ErrorResponse`
(do NOT move it). It is a cross-cutting concern used by multiple packages. Moving it into
`authentication.exception` would introduce an unusual upward dependency (filter layer
importing from exception layer). The `shared` package is a valid permanent home.

**Alternative:** Move `ErrorResponse` to `authentication.exception` and accept the cross-package
import from filter classes. Only viable if the planner decides no other domain will ever need
its own error response type.

### 3. `JwtAuthenticationFilter` Listed in Target But Doesn't Exist

**What we know:** The target structure includes `authentication/filter/JwtAuthenticationFilter.kt`
but this file does NOT exist in the current codebase. The project uses Spring Security's built-in
`BearerTokenAuthenticationFilter` (configured via `oauth2ResourceServer { jwt { ... } }` in
`SecurityConfig`), not a custom JWT filter.

**What's unclear:** Was this a mistake in the target structure, or is a custom filter
to be added as part of this phase?

**Recommendation:** This phase is structural only. Do NOT create a new `JwtAuthenticationFilter`
unless explicitly requested. Create the `authentication/filter/` directory with only the moved
`ApiAuthenticationEntryPoint` and `ApiAccessDeniedHandler` files.

### 4. Merge vs. Rename: `JwtTokenService` → `TokenService`

**Recommendation (decided above):** Rename only. Keep `RefreshTokenService` separate.
The planner should confirm this before generating tasks.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| INFR-06 | Domain-based package layout: config/, user/, authentication/ | This phase implements the sub-package layer within each domain: model/, repository/, service/, controller/, dto/, exception/. The file mapping table above provides the complete move list. |
</phase_requirements>

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection of all 28 source files in the project — complete import graph built from source
- `@SpringBootApplication` component scan behavior — foundational Spring Boot behavior, no external source needed
- JPA entity scan behavior — same; `@SpringBootApplication` enables auto-configuration including entity scanning from root package

### Secondary (MEDIUM confidence)
- Spring Boot 4.x / Spring 7.x behavior with `@SpringBootApplication` package scanning — consistent with Spring Framework documentation principles

### Tertiary (LOW confidence)
- None — all findings are derived directly from the project source files

---

## Metadata

**Confidence breakdown:**
- File layout / move map: HIGH — built directly from reading all source files
- Spring scan implications: HIGH — standard Spring Boot auto-configuration behavior
- Import dependency graph: HIGH — traced from actual source code imports
- Class rename decisions: MEDIUM — based on target structure intent interpretation; planner should confirm merge-vs-rename decision

**Research date:** 2026-03-01
**Valid until:** Stable — pure structural change, no external dependency research needed
