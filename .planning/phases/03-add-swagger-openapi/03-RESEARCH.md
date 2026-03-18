# Phase 3: Add Swagger (OpenAPI) - Research

**Researched:** 2026-03-18
**Domain:** springdoc-openapi 3.x — Spring Boot 4 / Kotlin / Spring Security JWT
**Confidence:** HIGH

---

## Summary

This phase adds Swagger/OpenAPI documentation to the existing Spring Boot 4.0.3 + Kotlin + Spring Security (JWT RS256) project. The standard library is `springdoc-openapi-starter-webmvc-ui` version 3.0.2, which is the current release explicitly targeting Spring Boot 4.0.3.

**Critical compatibility issue:** Spring Boot 4 ships Jackson 3 (group `tools.jackson`), but swagger-core (a transitive dependency of springdoc) still depends on Jackson 2. This causes a startup `IllegalArgumentException` about conflicting setter definitions on Jackson's `ObjectNode`. The fix is to add `spring-boot-jackson2` — Spring Boot's official deprecated stop-gap module that allows Jackson 2 to coexist — alongside the springdoc dependency.

**Primary recommendation:** Add `springdoc-openapi-starter-webmvc-ui:3.0.2` + `spring-boot-jackson2`, permit Swagger UI paths in SecurityConfig, create a single `OpenApiConfig` bean with global JWT Bearer security scheme, and add `@Parameter(hidden = true)` to all `@AuthenticationPrincipal Jwt` parameters.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `springdoc-openapi-starter-webmvc-ui` | 3.0.2 | Auto-generates OpenAPI spec + Swagger UI from controllers | Official Spring Boot 4 release; includes swagger-ui webjars; zero-config for basic use |
| `spring-boot-jackson2` | managed by Boot 4 BOM | Jackson 2 compatibility stop-gap for Boot 4 | Required because swagger-core still depends on Jackson 2 while Boot 4 defaults to Jackson 3; without it, startup fails |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `springdoc-openapi-starter-webmvc-ui` | springfox 3.x | springfox is unmaintained (last release 2021); not compatible with Spring Boot 3+/4; do not use |
| `springdoc-openapi-starter-webmvc-ui` | `springdoc-openapi-starter-webmvc-scalar` | Scalar is a modern alternative UI — reasonable choice but less familiar to most API consumers; stick with Swagger UI for template |
| Global `@SecurityRequirement` on OpenAPI bean | Per-controller `@SecurityRequirement` annotation | Global approach is less noisy and correct for this project (almost all endpoints require JWT) |

**Installation (pom.xml additions):**
```xml
<!-- OpenAPI / Swagger UI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.0.2</version>
</dependency>
<!-- Jackson 2 stop-gap: required because swagger-core still depends on jackson 2,
     while Spring Boot 4 defaults to jackson 3. Remove when springdoc upgrades swagger-core. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-jackson2</artifactId>
</dependency>
```

---

## Architecture Patterns

### Recommended Project Structure

```
src/main/kotlin/kz/innlab/template/
└── config/
    └── OpenApiConfig.kt    # New — OpenAPI bean, JWT security scheme, API metadata
```

No new packages needed. One config file is sufficient.

### Pattern 1: Global JWT Bearer Security Scheme

**What:** Define the `bearerAuth` security scheme once in `OpenApiConfig` and apply it globally via `addSecurityItem`. This means all documented endpoints show the lock icon and require a token in Swagger UI.

**When to use:** Any project where almost all endpoints are authenticated (as here — only `/api/v1/auth/**` is public).

**Example:**
```kotlin
// Source: springdoc official FAQ + verified against springdoc-openapi GitHub issues
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Spring Boot Auth Template API")
                .version("1.0.0")
                .description("JWT-authenticated REST API — Google, Apple, local, and phone auth with FCM and email notifications")
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste your JWT access token here (without 'Bearer ' prefix)")
            )
        )
}
```

### Pattern 2: Security Permit List for Swagger UI Paths

**What:** Add Swagger UI and OpenAPI spec paths to the `permitAll` list in `SecurityConfig.securityFilterChain()`.

**When to use:** Mandatory whenever Spring Security is present. Without this, requests to Swagger UI return 403.

**Example addition to the existing `authorizeHttpRequests` block in `SecurityConfig`:**
```kotlin
// Add these BEFORE the existing "/api/v1/auth/**" permit line
authorize("/swagger-ui/**", permitAll)
authorize("/swagger-ui.html", permitAll)
authorize("/v3/api-docs/**", permitAll)
authorize("/v3/api-docs.yaml", permitAll)
```

### Pattern 3: Hide Internal Parameters from Generated Docs

**What:** Annotate `@AuthenticationPrincipal Jwt` parameters with `@Parameter(hidden = true)` so they do not appear in the generated spec as request parameters (which would be confusing — the JWT is passed as Bearer, not as a body/query param).

**When to use:** Every controller method that has `@AuthenticationPrincipal Jwt` in its signature.

**Example (Kotlin annotation target required):**
```kotlin
// Source: springdoc FAQ — "@AuthenticationPrincipal is handled automatically in newer versions,
// but explicit @Parameter(hidden = true) is the safe/defensive approach"
@GetMapping("/tokens")
fun listTokens(
    @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
): ResponseEntity<List<DeviceTokenResponse>> { ... }
```

Springdoc newer versions auto-hide `@AuthenticationPrincipal`, but the explicit annotation is the defensive best practice — it makes intent clear and avoids any edge-case version surprises.

**Kotlin annotation target note:** Use `@Parameter(hidden = true)` directly (not `@param:Parameter` or `@get:Parameter`). The Kotlin compiler with `-Xannotation-default-target=param-property` (already configured in this project's pom.xml) means direct annotation placement is correct.

### Pattern 4: application.yaml springdoc Configuration

**What:** Configure paths and UI customization via `springdoc.*` properties.

```yaml
# Add to application.yaml (top-level, not profile-specific)
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method
    tagsSorter: alpha
    tryItOutEnabled: true
  packages-to-scan: kz.innlab.template
```

The `packages-to-scan` property is optional but recommended to limit scanning scope to the project's own package.

### Anti-Patterns to Avoid

- **Annotating every endpoint with `@Operation`:** Not needed unless you want to override the auto-generated summary. springdoc infers names from method names and DTOs automatically.
- **Adding Swagger paths to CORS `allowedOrigins`:** CORS config is for cross-origin browser requests; Swagger UI is served by the same origin.
- **Using springfox:** Unmaintained since 2021, incompatible with Spring Boot 3+.
- **Hardcoding `@SecurityRequirement` on public auth endpoints:** Public endpoints (`/api/v1/auth/**`) should not show the security requirement since they don't need auth. Use `@Operation(security = [])` on those controllers/methods to override the global default, OR leave the global requirement and document that auth endpoints ignore the token.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OpenAPI spec generation | Manual YAML/JSON spec file | springdoc auto-generation | Stays in sync with code; no drift; free |
| Swagger UI hosting | Custom HTML/JS serving | springdoc webjars inclusion | springdoc-starter-webmvc-ui bundles and serves UI automatically |
| Jackson 2/3 coexistence | Custom ClassLoader tricks | `spring-boot-jackson2` dependency | Spring Boot's official stop-gap; correct lifecycle management |
| JWT auth UI button | Custom Swagger UI config | `SecurityScheme` bean + `addSecurityItem` | Built-in OpenAPI 3.0 standard |

**Key insight:** springdoc derives API documentation from annotations already present in the code (`@RestController`, `@RequestMapping`, `@Valid`, Jakarta validation annotations on DTOs). No manual spec writing required for 90% of endpoints.

---

## Common Pitfalls

### Pitfall 1: Swagger UI Returns 403

**What goes wrong:** `/swagger-ui/index.html` returns 403 Forbidden even after adding `permitAll`.

**Why it happens:** Springdoc performs internal redirects. `/swagger-ui.html` redirects to `/swagger-ui/index.html`, which then fetches `/v3/api-docs`. All three path patterns must be explicitly permitted.

**How to avoid:** Permit `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, and `/v3/api-docs.yaml` in `SecurityConfig`.

**Warning signs:** Browser shows 403 on the UI page while `/v3/api-docs` returns 200.

### Pitfall 2: Application Fails to Start (Jackson 2/3 Conflict)

**What goes wrong:** `IllegalArgumentException: Conflicting setter definitions for property 'all'` or `ClassNotFoundException: com.fasterxml.jackson.databind.node.ObjectNode` at startup.

**Why it happens:** springdoc-openapi 3.0.2 transitively depends on swagger-core, which depends on Jackson 2 (`com.fasterxml.jackson`). Spring Boot 4 uses Jackson 3 (`tools.jackson`). Both are on the classpath and conflict.

**How to avoid:** Add `spring-boot-jackson2` dependency (no version — managed by Spring Boot BOM). This is Spring Boot's official compatibility bridge.

**Warning signs:** Application does not reach the "Started TemplateApplication" log line; stack trace mentions `ObjectNode`, `jackson-databind`, or `conflicting setter definitions`.

### Pitfall 3: `@AuthenticationPrincipal Jwt` Appears as Body Parameter in Docs

**What goes wrong:** Swagger UI shows `jwt` as a required request body or query parameter on authenticated endpoints.

**Why it happens:** springdoc may not reliably suppress Spring Security's principal injection parameter in all versions.

**How to avoid:** Add `@Parameter(hidden = true)` to every `@AuthenticationPrincipal Jwt` parameter in all controllers.

**Warning signs:** Generated spec shows an unexpected `jwt` parameter entry on protected endpoint schemas.

### Pitfall 4: Boolean `isX` Properties Documented as `x`

**What goes wrong:** Kotlin data class properties like `val isEnabled: Boolean` appear as `enabled` in the OpenAPI spec instead of `isEnabled`.

**Why it happens:** swagger-core applies JavaBeans naming conventions (strips `is` prefix for booleans). This is a "wontfix" per springdoc maintainers (closed 2026-01-01, issue #3175).

**How to avoid:** Use `@get:Schema(name = "isEnabled")` to explicitly set the property name in the spec. Alternatively, name the property without `is` prefix if that fits your API design.

**Warning signs:** Swagger UI shows `enabled` where you expected `isEnabled` in response schemas for Kotlin DTOs with boolean properties.

### Pitfall 5: Public Auth Endpoints Show Lock Icon

**What goes wrong:** Swagger UI shows the lock icon on `/api/v1/auth/**` endpoints, implying JWT is required — but they are public.

**Why it happens:** The global `addSecurityItem` applies to all endpoints by default.

**How to avoid:** Add `@Operation(security = [])` on `AuthController` to override the global security for that controller's endpoints. This tells Swagger UI those endpoints have no security requirement.

**Warning signs:** API consumers are confused about which endpoints require tokens.

---

## Code Examples

Verified patterns from official sources:

### OpenApiConfig.kt (complete)
```kotlin
// Source: springdoc official FAQ (springdoc.org/faq.html) + confirmed via GitHub issues #3157 workaround
package kz.innlab.template.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Spring Boot Auth Template API")
                .version("1.0.0")
                .description("Authentication, push notifications, and email service")
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            )
        )
}
```

### SecurityConfig.kt — Swagger path permit additions
```kotlin
// Source: springdoc FAQ — https://springdoc.org/faq.html
// Add inside the authorizeHttpRequests block, BEFORE the /api rules
authorize("/swagger-ui/**", permitAll)
authorize("/swagger-ui.html", permitAll)
authorize("/v3/api-docs/**", permitAll)
authorize("/v3/api-docs.yaml", permitAll)
```

### Override global security for public auth controller
```kotlin
// Source: io.swagger.v3.oas.annotations.Operation docs
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Public auth endpoints — no JWT required")
class AuthController(...) {

    @Operation(
        summary = "Register with email + password",
        security = []   // Override global bearerAuth — this endpoint is public
    )
    @PostMapping("/local/register")
    fun localRegister(...): ResponseEntity<AuthResponse> { ... }
}
```

### Hide @AuthenticationPrincipal from generated spec
```kotlin
// Source: springdoc FAQ — "@AuthenticationPrincipal type is recognized and hidden automatically,
// but explicit @Parameter(hidden = true) is the defensive best practice"
import io.swagger.v3.oas.annotations.Parameter

@GetMapping("/tokens")
fun listTokens(
    @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt
): ResponseEntity<List<DeviceTokenResponse>> { ... }
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| springfox 3.x (`io.springfox`) | springdoc-openapi (`org.springdoc`) | springfox abandoned ~2021 | springfox not compatible with Boot 3+; springdoc is the only active library |
| springdoc 2.x for Boot 3.x | springdoc 3.x for Boot 4.x | Boot 4.0 GA November 2024 | Version 3.x line required; version 2.x will not work with Boot 4 |
| Jackson 2 everywhere | Jackson 3 in Boot 4 (Jackson 2 stop-gap available) | Boot 4.0 (October 2025) | swagger-core still on Jackson 2; `spring-boot-jackson2` required as bridge |

**Deprecated/outdated:**
- `springdoc-openapi-ui` (v1 artifact): Old artifact for Spring Boot 1-2; do not use
- springfox: Unmaintained, not compatible
- `spring-boot-jackson2`: This module itself is deprecated and will be removed in a future Boot release — use it now as a stop-gap and remove when springdoc upgrades swagger-core to Jackson 3

---

## Open Questions

1. **When will springdoc upgrade swagger-core to Jackson 3?**
   - What we know: Issue #3200 is marked "COMPLETED" administratively but no fix is released yet (as of 2026-03-18). The upstream swagger-api/swagger-core#4991 is open.
   - What's unclear: Timeline. Could be weeks or months.
   - Recommendation: Use `spring-boot-jackson2` now. Remove it when springdoc 3.x releases a version that doesn't need Jackson 2.

2. **Will `@AuthenticationPrincipal` be auto-hidden in 3.0.2?**
   - What we know: springdoc has auto-detection for `@AuthenticationPrincipal` in many versions. Issue #198 shows it was fixed historically. However, behavior can vary in Kotlin with nullable types.
   - What's unclear: Exact behavior in springdoc 3.0.2 + Kotlin.
   - Recommendation: Explicitly add `@Parameter(hidden = true)` defensively to all `@AuthenticationPrincipal Jwt` parameters. No harm if already auto-hidden.

---

## Sources

### Primary (HIGH confidence)
- https://github.com/springdoc/springdoc-openapi/releases — Confirmed v3.0.2 targets Spring Boot 4.0.3
- https://springdoc.org/faq.html — Official FAQ: security permitAll paths, `@AuthenticationPrincipal` handling
- https://github.com/springdoc/springdoc-openapi/issues/3157 — Confirmed `spring-boot-jackson2` as the fix for Jackson 2/3 conflict in Boot 4 + springdoc 3
- https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide — Confirmed `spring-boot-jackson2` as deprecated stop-gap module

### Secondary (MEDIUM confidence)
- https://github.com/springdoc/springdoc-openapi/issues/3175 — `@get:Schema` workaround for Kotlin `isX` boolean properties (closed wontfix 2026-01-01)
- https://github.com/springdoc/springdoc-openapi/issues/3095 — Spring Boot 4.0.0-M3 support tracking, Jackson 3 migration status
- https://tomaytotomato.com/spring-docs-configuration-with-spring-security/ — Security filter chain permitAll pattern confirmed by multiple sources

### Tertiary (LOW confidence)
- WebSearch results about `@SecurityRequirement` on class vs method level — patterns are stable but not re-verified against springdoc 3.0.2 specifically

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified via GitHub release notes that 3.0.2 targets Boot 4.0.3; Jackson fix verified via issue #3157
- Architecture: HIGH — patterns verified via official FAQ and multiple cross-referenced sources
- Pitfalls: HIGH — pitfalls sourced from active GitHub issues with confirmed resolutions

**Research date:** 2026-03-18
**Valid until:** 2026-04-18 (springdoc is active; monitor for a version that drops Jackson 2 dependency)
