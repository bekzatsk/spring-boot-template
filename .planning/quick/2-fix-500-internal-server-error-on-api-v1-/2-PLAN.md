---
phase: quick
plan: 2
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt
  - src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt
autonomous: true
requirements: []
must_haves:
  truths:
    - "POST /api/v1/auth/refresh returns 200 with new tokens when given a valid refresh token"
    - "The User entity is fully loaded (not a Hibernate proxy) when accessed after rotate()"
    - "handleGeneral() logs the actual exception class and message so 500s are diagnosable"
  artifacts:
    - path: "src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt"
      provides: "JOIN FETCH query for findByTokenHash"
      contains: "JOIN FETCH rt.user"
    - path: "src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt"
      provides: "Exception logging in catch-all handler"
      contains: "logger"
  key_links:
    - from: "RefreshTokenRepository.findByTokenHash"
      to: "RefreshToken.user (User entity)"
      via: "JOIN FETCH in JPQL query"
      pattern: "JOIN FETCH rt\\.user"
---

<objective>
Fix 500 Internal Server Error on POST /api/v1/auth/refresh endpoint.

Purpose: The refresh endpoint crashes with LazyInitializationException because `RefreshToken.user` is lazily loaded, `open-in-view` is false, and the User proxy is accessed outside the transaction boundary in AuthController. The catch-all exception handler silently swallows it with no logging, making diagnosis impossible.

Output: Working refresh endpoint, diagnosable 500 errors going forward.
</objective>

<execution_context>
@./.claude/get-shit-done/workflows/execute-plan.md
@./.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt
@src/main/kotlin/kz/innlab/template/authentication/RefreshTokenService.kt
@src/main/kotlin/kz/innlab/template/authentication/RefreshToken.kt
@src/main/kotlin/kz/innlab/template/authentication/AuthController.kt
@src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt

<interfaces>
<!-- Root cause chain -->
<!-- 1. RefreshTokenRepository.findByTokenHash returns RefreshToken with LAZY user proxy -->
<!-- 2. RefreshTokenService.rotate() returns Pair(stored.user, newRawToken) — never touches user properties -->
<!-- 3. AuthController.refresh() calls user.roles after @Transactional scope ends -->
<!-- 4. LazyInitializationException thrown, caught by handleGeneral(), returns generic 500 -->

From RefreshToken.kt:
```kotlin
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
val user: User
```

From RefreshTokenService.kt line 86:
```kotlin
return Pair(stored.user, newRawToken)
```

From AuthController.kt line 41:
```kotlin
val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add JOIN FETCH to findByTokenHash and add exception logging</name>
  <files>
    src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt
    src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt
  </files>
  <action>
**RefreshTokenRepository.kt** — Replace the plain `findByTokenHash` method with a `@Query`-annotated version that eagerly loads the User:

```kotlin
@Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :tokenHash")
fun findByTokenHash(@Param("tokenHash") tokenHash: String): RefreshToken?
```

This ensures that when `RefreshTokenService.rotate()` returns `stored.user`, the User entity is fully initialized (not a Hibernate proxy), so `AuthController.refresh()` can safely access `user.roles` outside the transaction.

Keep the existing `@Query` import and `@Param` import (already present for `deleteAllByUser`). No other changes to the file.

**GlobalExceptionHandler.kt** — Add a SLF4J logger and log the exception in `handleGeneral()`:

1. Add import: `import org.slf4j.LoggerFactory`
2. Add companion object with logger at the top of the class:
   ```kotlin
   companion object {
       private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
   }
   ```
3. In `handleGeneral()`, add logging before the return:
   ```kotlin
   logger.error("Unhandled exception: {}", ex.message, ex)
   ```

This ensures future 500 errors show the real exception class, message, and stack trace in server logs instead of being silently swallowed.
  </action>
  <verify>
    <automated>cd /Users/bekzat/Workspace/Template/spring-boot-template && ./mvnw test -pl . -q 2>&1 | tail -20</automated>
  </verify>
  <done>
    - `findByTokenHash` uses `JOIN FETCH rt.user` so User is eagerly loaded with the RefreshToken
    - `handleGeneral()` logs the exception with `logger.error()` before returning 500
    - All existing tests pass (SecurityIntegrationTest, AppleAuthIntegrationTest)
  </done>
</task>

</tasks>

<verification>
1. All existing tests pass: `./mvnw test` exits 0
2. Grep confirms JOIN FETCH: `grep "JOIN FETCH rt.user" src/main/kotlin/kz/innlab/template/authentication/RefreshTokenRepository.kt`
3. Grep confirms logging: `grep "logger.error" src/main/kotlin/kz/innlab/template/shared/error/GlobalExceptionHandler.kt`
</verification>

<success_criteria>
- POST /api/v1/auth/refresh no longer throws LazyInitializationException (User eagerly loaded via JOIN FETCH)
- All 9 existing tests pass without modification
- Future unhandled exceptions are logged with full stack trace before returning 500
</success_criteria>

<output>
After completion, create `.planning/quick/2-fix-500-internal-server-error-on-api-v1-/2-SUMMARY.md`
</output>
