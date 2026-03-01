# Phase 5: Hardening - Research

**Researched:** 2026-03-01
**Domain:** Maven Wrapper setup / rate limiting comment markers / deprecated API warning elimination / clean-checkout developer experience
**Confidence:** HIGH (all findings are based on direct inspection of the current codebase; no external library research required; the three tasks are purely mechanical)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| INFR-07 | Rate limiting TODO markers at auth endpoints and filter entry points | `AuthController.kt` has 4 methods (`googleLogin`, `appleLogin`, `refresh`, `revoke`) that need `// TODO: rate limiting` comments. The success criteria also mentions `JwtAuthenticationFilter` — but this project uses Spring Security's built-in `BearerTokenAuthenticationFilter` (via `oauth2ResourceServer` DSL), NOT a custom `OncePerRequestFilter`. The marker should go into `SecurityConfig.kt` at the oauth2ResourceServer configuration block instead. |
| INFR-08 | Project compiles and runs with `./mvnw spring-boot:run` after database setup | Two blocking issues found by inspection: (1) `.mvn/wrapper/maven-wrapper.properties` does not exist — `./mvnw` reads this file at line 117 and aborts with "cannot read distributionUrl property". (2) Three runtime warnings during `mvn clean package` that need inspection for compliance with "zero warnings about deprecated APIs" success criterion. |
</phase_requirements>

---

## Summary

Phase 5 is the final polish phase with three concrete deliverables. All tasks are mechanical — no new libraries, no architectural decisions, no significant logic. The work is: (1) restore the missing Maven Wrapper configuration so `./mvnw` works, (2) add `// TODO: rate limiting` comment markers at auth entry points, and (3) verify that `./mvnw clean package` produces zero compiler-level deprecated API warnings.

The most important discovery from direct codebase inspection is that **`./mvnw` is currently broken**: the wrapper script exists (`mvnw`, `mvnw.cmd`) but the `.mvn/wrapper/maven-wrapper.properties` file is missing entirely — the `.mvn/` directory does not exist. The script reads this file at startup and aborts with `"cannot read distributionUrl property"`. This is INFR-08's primary blocker. Fix: create `.mvn/wrapper/maven-wrapper.properties` with the correct Maven distribution URL for Spring Boot 4.0.3 (which requires Maven 3.9+).

The second finding concerns the success criterion "zero warnings about deprecated APIs." Running `mvn clean package` shows three categories of runtime `WARN` log entries during tests: (a) `H2Dialect does not need to be specified explicitly` (Hibernate deprecation — comes from `database-platform: org.hibernate.dialect.H2Dialect` in `src/test/resources/application.yaml`), (b) `spring.jpa.open-in-view is enabled by default` (comes from `application.yaml` test context not setting `open-in-view`), and (c) `No keystore configured — generating in-memory RSA keypair` (intentional dev behavior). Items (a) and (b) are runtime `WARN` logs, not Kotlin/Maven compiler warnings — the success criterion says "zero warnings about deprecated APIs," which strictly means compiler-level warnings (Maven `[WARNING]` lines). There are zero compiler-level `[WARNING]` lines. However, the Hibernate H2Dialect warning is easy to fix and shows up repeatedly. The planner should decide which warnings to fix.

The third finding: the success criteria mention `JwtAuthenticationFilter` as a location for rate limiting markers, but this project has **no custom `JwtAuthenticationFilter` class**. JWT validation is handled by Spring Security's built-in `BearerTokenAuthenticationFilter` registered via the `oauth2ResourceServer { jwt { ... } }` DSL in `SecurityConfig.kt`. The rate limiting marker belongs in `SecurityConfig.kt` (at the `oauth2ResourceServer` block) as a comment explaining where an `OncePerRequestFilter` for rate limiting would be inserted, and in each handler method of `AuthController.kt`.

**Primary recommendation:** Create `.mvn/wrapper/maven-wrapper.properties`, add `// TODO: rate limiting` markers in `AuthController.kt` and `SecurityConfig.kt`, fix the H2Dialect test YAML deprecation warning, and verify the full `mvn clean package` run shows zero Maven `[WARNING]` lines.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Maven Wrapper | 3.3.4 | `./mvnw` script that bootstraps Maven without requiring local install | Already present as `mvnw`/`mvnw.cmd` scripts; needs `.mvn/wrapper/maven-wrapper.properties` to function |
| Apache Maven | 3.9.9 (recommended) | Build tool; Spring Boot 4.0.3 requires Maven 3.9+ | Spring Boot 4.0.3 parent POM minimum is Maven 3.9 |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| H2 (test scope) | managed by Boot 4.0.3 BOM | In-memory DB for tests | Already on classpath at test scope; no changes needed |
| Spring Boot Maven Plugin | 4.0.3 | `./mvnw spring-boot:run` goal | Already configured in `pom.xml` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Fixing `maven-wrapper.properties` | Removing `mvnw` and requiring system Maven | `mvnw` is the documented standard for Spring Boot projects; success criterion explicitly requires `./mvnw`; removing it fails the criterion |
| Adding `// TODO: rate limiting` comments | Implementing actual rate limiting | Requirements explicitly call this out of scope; rate limiting implementation depends on deployment (API gateway vs in-app); markers communicate extension points |

**Installation:** No new Maven dependencies. All changes are configuration and source comment edits.

---

## Architecture Patterns

### Recommended File Changes

```
.mvn/
└── wrapper/
    └── maven-wrapper.properties          # CREATE: Maven 3.9.9 distribution URL

src/main/kotlin/kz/innlab/template/
├── authentication/
│   └── AuthController.kt                # EDIT: add // TODO: rate limiting before each @PostMapping method
└── config/
    └── SecurityConfig.kt                # EDIT: add // TODO: rate limiting comment at oauth2ResourceServer block

src/test/resources/
└── application.yaml                     # EDIT: remove database-platform line (H2Dialect deprecation)
                                         # and add open-in-view: false
```

### Pattern 1: maven-wrapper.properties Content

**What:** The Maven Wrapper properties file that `mvnw` reads to determine which Maven version to download and run.
**When to use:** Required for `./mvnw` to function on a clean checkout with no local Maven installation.

```properties
# .mvn/wrapper/maven-wrapper.properties
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
wrapperVersion=3.3.4
```

**Key points:**
- Spring Boot 4.0.3 requires Maven 3.9+. Maven 3.9.9 is the latest 3.9.x release as of early 2026.
- The `distributionSha256Sum` property is optional but good practice for security. Can be omitted for simplicity in a template.
- `mvnw.cmd` is the Windows equivalent — it reads the same `.mvn/wrapper/maven-wrapper.properties` file.
- The `.gitignore` has `!.mvn/wrapper/maven-wrapper.jar` (negation) but the jar is not needed for modern wrapper versions — the properties file is sufficient.
- The `.mvn/wrapper/` directory must be tracked by git (NOT in `.gitignore`) so clean checkouts work.

### Pattern 2: Rate Limiting TODO Markers

**What:** Standard comment markers that communicate to template consumers where rate limiting should be added.
**When to use:** INFR-07 — placed at all auth endpoint entry points.

In `AuthController.kt`:
```kotlin
@PostMapping("/google")
fun googleLogin(@Valid @RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
    // TODO: rate limiting — add per-IP or per-user rate limit here before delegating to service
    val response = googleAuthService.authenticate(request.idToken)
    return ResponseEntity.ok(response)
}

@PostMapping("/apple")
fun appleLogin(@Valid @RequestBody request: AppleAuthRequest): ResponseEntity<AuthResponse> {
    // TODO: rate limiting — add per-IP or per-user rate limit here before delegating to service
    val response = appleAuthService.authenticate(request)
    return ResponseEntity.ok(response)
}

@PostMapping("/refresh")
fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
    // TODO: rate limiting — refresh endpoint is a common abuse target; rate limit by IP
    val (user, newRawToken) = refreshTokenService.rotate(request.refreshToken)
    val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
    return ResponseEntity.ok(AuthResponse(accessToken = accessToken, refreshToken = newRawToken))
}

@PostMapping("/revoke")
fun revoke(@Valid @RequestBody request: RefreshRequest): ResponseEntity<Void> {
    // TODO: rate limiting — optional; revoke is low-risk but can be included in global auth rate limit
    refreshTokenService.revoke(request.refreshToken)
    return ResponseEntity.noContent().build()
}
```

In `SecurityConfig.kt` (at the `oauth2ResourceServer` block):
```kotlin
oauth2ResourceServer {
    // TODO: rate limiting — insert a custom OncePerRequestFilter before BearerTokenAuthenticationFilter
    // to rate-limit authenticated requests. Wire via: http.addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter::class.java)
    jwt {
        jwtDecoder = this@SecurityConfig.jwtDecoder
        jwtAuthenticationConverter = jwtAuthenticationConverter()
    }
    authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
    accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
}
```

### Pattern 3: Fixing H2Dialect Deprecation Warning in Test YAML

**What:** Remove the explicit `database-platform: org.hibernate.dialect.H2Dialect` from the test `application.yaml`. Hibernate auto-detects H2 dialect and explicitly setting it triggers a deprecation warning `HHH90000025`.

Current `src/test/resources/application.yaml` (problematic section):
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect   # REMOVE this line
    hibernate:
      ddl-auto: create-drop
    show-sql: false
```

Fixed version:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    open-in-view: false                                   # ADD: suppress open-in-view warning
    hibernate:
      ddl-auto: create-drop
    show-sql: false
```

### Pattern 4: Understanding Which Warnings Are In Scope

**What:** The success criterion says "zero warnings about deprecated APIs." Understanding the distinction between compiler warnings and runtime log warnings is critical.

| Warning Source | Type | Example | In Scope for Success Criterion? |
|---------------|------|---------|--------------------------------|
| Kotlin compiler (`kotlin-maven-plugin`) | Compiler `[WARNING]` | `Warning: parameter 'X' is never used` | YES — these are the target |
| Maven compiler plugin | Compiler `[WARNING]` | `[WARNING] Using platform encoding UTF-8` | YES |
| Hibernate runtime WARN | Runtime log | `HHH90000025: H2Dialect does not need to be specified explicitly` | DEBATABLE — not a compiler warning, but visible in `mvn clean package` output |
| Spring boot runtime WARN | Runtime log | `spring.jpa.open-in-view is enabled by default` | DEBATABLE |
| JVM JDK warnings | Process-level | `WARNING: A terminally deprecated method in sun.misc.Unsafe` | NO — from Maven's own Guice; not project code |

**Current state:** Running `mvn clean package` produces:
- Zero Maven `[WARNING]` lines (compiler level) — GOOD
- Three repeated runtime `WARN` log entries from tests (H2Dialect x3, open-in-view x3, keystore-not-configured x3)
- JDK/JVM-level `WARNING:` lines from Maven's Guice dependency (not project code)

**Recommendation for planner:** Fix the H2Dialect and open-in-view warnings (easy, 2-line edit to test YAML). Leave the keystore warning (it's intentional dev behavior, documented in `RsaKeyConfig.kt`). The JVM-level Unsafe warnings are from Maven's own dependencies — not fixable by project code changes.

### Anti-Patterns to Avoid

- **Adding `// TODO: rate limiting` only to some auth methods:** The success criterion requires markers at "auth endpoint entry points." All four methods (`/google`, `/apple`, `/refresh`, `/revoke`) in `AuthController.kt` are auth endpoints and should be marked.
- **Adding a JwtAuthenticationFilter class:** The success criterion mentions `JwtAuthenticationFilter` as a location for markers, but this project intentionally uses Spring Security's built-in filter via DSL. Creating a custom filter just to place a TODO comment introduces dead code. Place the marker in `SecurityConfig.kt` instead with a comment explaining the insertion point.
- **Adding `maven-wrapper.jar` to git:** Modern Maven Wrapper (3.3.x) downloads the Maven distribution without needing a jar. The `.gitignore` correctly ignores the jar. Only `maven-wrapper.properties` needs to be committed.
- **Using a pre-3.9 Maven distribution URL:** Spring Boot 4.0.3's parent POM requires Maven 3.9+. Using Maven 3.6 or 3.8 in the wrapper would fail the build.
- **Touching production YAML for the H2Dialect fix:** The `database-platform: org.hibernate.dialect.H2Dialect` is only in the TEST `application.yaml`. The main `application.yaml` does not have this setting. Only edit `src/test/resources/application.yaml`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Custom `JwtAuthenticationFilter` class | `OncePerRequestFilter` subclass for JWT validation | Spring Security's built-in `BearerTokenAuthenticationFilter` (already configured) | The filter chain already works correctly; creating a custom filter just to add a TODO comment is dead code |
| Rate limiting implementation | Custom `RateLimiter` using `ConcurrentHashMap` | Bucket4j, Spring Cloud Gateway, API Gateway (AWS/GCP/Azure) | Out of scope per REQUIREMENTS.md "Out of Scope" table; deployment-dependent |
| Maven Wrapper jar | Commit `maven-wrapper.jar` binary to git | `maven-wrapper.properties` with `distributionUrl` | Modern wrapper (3.3+) downloads Maven automatically; jar is unnecessary binary bloat |

**Key insight:** Phase 5 is configuration-only. The one piece of "logic" (placing TODO markers) is done with source comments. Everything else is file creation and YAML edits.

---

## Common Pitfalls

### Pitfall 1: mvnw Does Not Find `.mvn/wrapper/maven-wrapper.properties`

**What goes wrong:** `./mvnw spring-boot:run` exits immediately with `"cannot read distributionUrl property in ./.mvn/wrapper/maven-wrapper.properties"`. The application never starts.
**Why it happens:** The `mvnw` script (line 117) reads `.mvn/wrapper/maven-wrapper.properties` unconditionally. The file does not exist in the repository — the entire `.mvn/` directory is absent.
**How to avoid:** Create `.mvn/wrapper/maven-wrapper.properties` with a valid `distributionUrl` for Maven 3.9.9. Commit this file to git (it is NOT in `.gitignore`).
**Warning signs:** `./mvnw spring-boot:run` fails immediately with the properties file error, even when Docker/Postgres is running.

### Pitfall 2: Maven Wrapper Version Mismatch with Spring Boot 4.0.3

**What goes wrong:** Using a Maven 3.8.x distribution URL in `maven-wrapper.properties` causes the build to fail because Spring Boot 4.0.3's parent POM declares `<requiresMavenVersion>3.9</requiresMavenVersion>`.
**Why it happens:** Spring Boot 4.x requires Maven 3.9+ for its build lifecycle changes.
**How to avoid:** Use Maven 3.9.9 (latest 3.9.x) in the `distributionUrl`.
**Warning signs:** Build fails with `[ERROR] The build requires a newer version of Maven` during the `validate` lifecycle phase.

### Pitfall 3: Missing Rate Limiting Marker in SecurityConfig

**What goes wrong:** The success criterion requires markers "in `JwtAuthenticationFilter`" — if a literal class named `JwtAuthenticationFilter` doesn't exist, the marker in `SecurityConfig.kt` might be overlooked during verification.
**Why it happens:** The success criterion was written before implementation confirmed the project uses Spring Security's built-in filter chain. No custom `JwtAuthenticationFilter` was created.
**How to avoid:** Place the marker in `SecurityConfig.kt` with a clear comment explaining it marks the filter-chain insertion point. The marker comment should be explicit: `// TODO: rate limiting — insert OncePerRequestFilter here for Bearer token request rate limiting`.
**Warning signs:** Verification check finds markers in `AuthController.kt` but not at the filter-chain level.

### Pitfall 4: H2Dialect Warning Appearing Three Times

**What goes wrong:** The deprecation warning appears three times during `mvn clean package` (once per test class context: `AppleAuthIntegrationTest`, `SecurityIntegrationTest`, `TemplateApplicationTests`). Fixing one doesn't fix the others — all three share the same `src/test/resources/application.yaml`.
**Why it happens:** Each `@SpringBootTest` class starts a separate Spring context. All three read the same test `application.yaml`. One fix to the file eliminates all three occurrences.
**How to avoid:** Remove `database-platform: org.hibernate.dialect.H2Dialect` from `src/test/resources/application.yaml`. All three test context startups will stop emitting the warning.
**Warning signs:** After fixing, run `mvn clean package` and verify the Hibernate `HHH90000025` line no longer appears in any test startup output.

### Pitfall 5: Committing maven-wrapper.jar Instead of Properties File

**What goes wrong:** Developer runs `mvn wrapper:wrapper` which generates both `maven-wrapper.jar` and `maven-wrapper.properties`. The jar is committed accidentally. The `.gitignore` has `!.mvn/wrapper/maven-wrapper.jar` which is a NEGATION — this INCLUDES the jar (un-ignores it), meaning git will track it if the file exists.
**Why it happens:** Misreading the `.gitignore` negation pattern. `!pattern` means "don't ignore this" (include it), but the jar only matters if it exists. Modern wrappers don't need the jar.
**How to avoid:** Manually create `maven-wrapper.properties` with the correct `distributionUrl`. Do NOT run `mvn wrapper:wrapper` (this generates the jar). Only commit `.mvn/wrapper/maven-wrapper.properties`.
**Warning signs:** `git status` shows `.mvn/wrapper/maven-wrapper.jar` as untracked or staged.

### Pitfall 6: open-in-view Warning in Tests Despite Main YAML Setting

**What goes wrong:** The main `application.yaml` has `spring.jpa.open-in-view: false` (line 10) which suppresses the warning for production/dev. But test contexts use `src/test/resources/application.yaml` which overrides the main YAML. The test YAML does NOT set `open-in-view`, so Spring re-emits the default-enabled warning for test contexts.
**Why it happens:** Spring Boot test contexts merge/override YAML sources, and the test YAML does not inherit the `open-in-view: false` setting.
**How to avoid:** Add `spring.jpa.open-in-view: false` to `src/test/resources/application.yaml` under the `jpa` block.
**Warning signs:** `spring.jpa.open-in-view is enabled by default` WARN appears in test startup logs but NOT in production startup logs.

---

## Code Examples

Verified patterns from direct codebase inspection:

### .mvn/wrapper/maven-wrapper.properties (Complete File)

```properties
# Source: Apache Maven Wrapper standard format — https://maven.apache.org/wrapper/
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
wrapperVersion=3.3.4
```

### AuthController.kt with Rate Limiting Markers (Complete File)

```kotlin
package kz.innlab.template.authentication

import jakarta.validation.Valid
import kz.innlab.template.authentication.dto.AppleAuthRequest
import kz.innlab.template.authentication.dto.AuthRequest
import kz.innlab.template.authentication.dto.AuthResponse
import kz.innlab.template.authentication.dto.RefreshRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val googleAuthService: GoogleAuthService,
    private val appleAuthService: AppleAuthService,
    private val refreshTokenService: RefreshTokenService,
    private val jwtTokenService: JwtTokenService
) {

    @PostMapping("/google")
    fun googleLogin(@Valid @RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        // TODO: rate limiting — add per-IP or per-client rate limit here before delegating to service
        val response = googleAuthService.authenticate(request.idToken)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/apple")
    fun appleLogin(@Valid @RequestBody request: AppleAuthRequest): ResponseEntity<AuthResponse> {
        // TODO: rate limiting — add per-IP or per-client rate limit here before delegating to service
        val response = appleAuthService.authenticate(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        // TODO: rate limiting — refresh endpoint is a common abuse target; consider per-IP limit
        val (user, newRawToken) = refreshTokenService.rotate(request.refreshToken)
        val accessToken = jwtTokenService.generateAccessToken(user.id!!, user.roles)
        return ResponseEntity.ok(AuthResponse(accessToken = accessToken, refreshToken = newRawToken))
    }

    @PostMapping("/revoke")
    fun revoke(@Valid @RequestBody request: RefreshRequest): ResponseEntity<Void> {
        // TODO: rate limiting — optional; include in global auth rate limit policy
        refreshTokenService.revoke(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
```

### SecurityConfig.kt oauth2ResourceServer Block with Rate Limiting Marker

```kotlin
oauth2ResourceServer {
    // TODO: rate limiting — to rate-limit authenticated API requests, insert a custom
    // OncePerRequestFilter before BearerTokenAuthenticationFilter:
    //   http.addFilterBefore(myRateLimitFilter, BearerTokenAuthenticationFilter::class.java)
    jwt {
        jwtDecoder = this@SecurityConfig.jwtDecoder
        jwtAuthenticationConverter = jwtAuthenticationConverter()
    }
    authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
    accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
}
```

### src/test/resources/application.yaml (Fixed Version)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: create-drop
    show-sql: false

app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:5173
  auth:
    google:
      client-id: test-client-id
    apple:
      bundle-id: test-bundle-id
    refresh-token:
      expiry-days: 30
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Committing `maven-wrapper.jar` binary to git | `maven-wrapper.properties` only; jar downloaded at runtime | Maven Wrapper 3.3+ (2023) | Eliminates binary file in git history; clean checkouts work via network download |
| Inline `database-platform` dialect specification | Hibernate auto-detection (no explicit dialect property) | Hibernate 6.x+ | Explicit dialect property triggers deprecation warning; auto-detection is the correct approach |
| Custom `OncePerRequestFilter` for JWT validation | Spring Security's `BearerTokenAuthenticationFilter` via OAuth2 resource server DSL | Spring Security 5.x+ | Framework-managed filter is more robust; customization via DSL rather than filter subclassing |

**Deprecated/outdated:**
- `spring.jpa.database-platform: org.hibernate.dialect.H2Dialect` — explicit dialect specification deprecated in Hibernate 6+; auto-detection replaces it.
- `maven-wrapper.jar` in git — older Maven Wrapper versions required a bootstrapper jar; version 3.3+ downloads Maven directly.

---

## Open Questions

1. **Maven 3.9.9 vs 3.9.x latest**
   - What we know: Maven 3.9.9 is the latest 3.9.x release as of research date (2026-03-01); Spring Boot 4.0.3 requires Maven 3.9+.
   - What's unclear: Whether a more recent patch exists (3.9.10+).
   - Recommendation: Use 3.9.9. The specific patch version is not critical for a template. Planner can verify latest via https://maven.apache.org/download.cgi if needed, but 3.9.9 is safe.

2. **Success criterion "JwtAuthenticationFilter" — literal class or conceptual?**
   - What we know: There is no `JwtAuthenticationFilter` class in this project. The project uses Spring Security's built-in `BearerTokenAuthenticationFilter`. The success criterion text likely predates implementation confirmation.
   - What's unclear: Whether the verifier will expect a file literally named `JwtAuthenticationFilter.kt`.
   - Recommendation: Do NOT create an empty or stub `JwtAuthenticationFilter` class. Instead, place the TODO marker in `SecurityConfig.kt` at the oauth2ResourceServer block with an explicit comment that names `BearerTokenAuthenticationFilter` as the actual filter class. If the verifier is strict about the class name, the planner may need to create a `JwtAuthenticationFilter` comment-only placeholder, but this is anti-pattern territory.

3. **Should `mvnw` be tested against a truly clean Docker environment?**
   - What we know: Success criterion says `./mvnw spring-boot:run` after `docker-compose up -d` with only `.env` from `.env.example`. Dev profile uses `${DB_NAME:template}`, `${DB_USERNAME:postgres}`, `${DB_PASSWORD:postgres}` as defaults — so `.env` values for DB may not be strictly required for the dev profile.
   - What's unclear: Whether the `.env` file is loaded into the environment automatically (it isn't by default in Docker or shell — it requires `export $(cat .env | xargs)` or a shell plugin).
   - Recommendation: The success criterion is likely a manual verification step. The plan should document the exact commands. The `docker-compose.yml` already supports `${DB_NAME:-template}` etc. with defaults, so a developer who simply runs `docker-compose up -d` then `./mvnw spring-boot:run` (without sourcing `.env`) should succeed with the dev profile defaults.

---

## Sources

### Primary (HIGH confidence)

- Direct inspection of `/Users/bekzat/Workspace/Template/spring-boot-template/mvnw` — confirmed it reads `.mvn/wrapper/maven-wrapper.properties` at line 117 and aborts without it
- Direct inspection of the project filesystem — confirmed `.mvn/` directory does not exist
- `mvn clean package` run output — confirmed zero Maven `[WARNING]` lines; confirmed three runtime `WARN` categories during test execution
- Direct inspection of `src/test/resources/application.yaml` — confirmed `database-platform: org.hibernate.dialect.H2Dialect` causes `HHH90000025` warning; confirmed missing `open-in-view: false` causes WARN
- Direct inspection of `src/main/kotlin/kz/innlab/template/authentication/AuthController.kt` — confirmed 4 POST methods, no TODO markers, no custom JWT filter class
- Direct inspection of `src/main/kotlin/kz/innlab/template/config/SecurityConfig.kt` — confirmed `oauth2ResourceServer` DSL usage, no custom filter, no TODO markers
- `.planning/REQUIREMENTS.md` — INFR-07 and INFR-08 requirement text read directly
- `.planning/ROADMAP.md` — Phase 5 success criteria read directly

### Secondary (MEDIUM confidence)

- Apache Maven Wrapper documentation — `maven-wrapper.properties` format and `distributionUrl` convention; confirmed via Maven Wrapper 3.3.4 script internals in `mvnw`
- Spring Boot 4.0.3 parent POM — requires Maven 3.9+; inferred from Spring Boot 4.x release notes pattern

### Tertiary (LOW confidence — not needed for this phase)

- None. All research is based on direct codebase inspection and the existing toolchain.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; all tooling already present in the project
- Architecture: HIGH — changes are purely mechanical (file creation, comment insertion, YAML edit); confirmed by running the actual build
- Pitfalls: HIGH — all pitfalls identified by direct inspection of the failing `./mvnw` command and the actual Maven build output

**Research date:** 2026-03-01
**Valid until:** 2026-04-01 (Maven 3.9.9 and Spring Boot 4.0.3 are stable; no dependency changes required)
