# Stack Research

**Domain:** Spring Boot 4 JWT Auth — stateless API backend with Google/Apple ID token verification
**Researched:** 2026-03-01
**Confidence:** MEDIUM-HIGH (core Spring ecosystem verified via official docs; Google/Apple library versions verified via Maven Central)

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Spring Boot | 4.0.3 (existing) | Framework parent, dependency management | Already in project; manages all Spring versions automatically |
| Spring Security | 7.0.x (managed by Boot 4) | HTTP security, filter chain, stateless JWT validation | Native Spring Security 7 ships with Boot 4 — no separate version pin needed |
| Spring Data JPA | managed by Boot 4 | ORM layer, repositories, UUID primary keys | Boot 4 starter pulls Hibernate 7.2.4.Final + HikariCP 7.x; no version to manage |
| Hibernate | 7.2.4.Final (managed) | JPA provider | Managed transitively via spring-boot-starter-data-jpa; supports Jakarta EE 11 |
| Spring Authorization Server | managed via Boot 4 starter | JWT signing infrastructure (JWKSource, NimbusJwtEncoder) — NOT a full OAuth2 server | Absorbed into Spring Security 7 project; provides battle-tested RS256 JWT signing without running a full authorization server |
| Java | 24 (existing, target 25) | JVM runtime | Project currently targets Java 24; Virtual Threads available via `spring.threads.virtual.enabled=true` — safe to upgrade to Java 25 at any point |

### New Dependencies to Add

| Library | Version | Maven Artifact | Purpose | Why |
|---------|---------|---------------|---------|-----|
| spring-boot-starter-security | managed (7.0.x) | `org.springframework.boot:spring-boot-starter-security` | Spring Security 7 for filter chain, stateless session, CORS, CSRF disable | The foundational dependency for all auth/security work; version managed by Boot 4 |
| spring-boot-starter-security-oauth2-authorization-server | managed (Boot 4 starter) | `org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server` | JWKSource + NimbusJwtEncoder for RS256 JWT signing | **NOTE:** This is the NEW artifact name in Spring Boot 4 — the old `spring-boot-starter-oauth2-authorization-server` is deprecated. Spring Authorization Server is now part of Spring Security 7 |
| spring-boot-starter-data-jpa | managed | `org.springframework.boot:spring-boot-starter-data-jpa` | JPA repositories, entity management, Hibernate 7, HikariCP | Pulls Hibernate 7.2.4.Final + HikariCP — no separate version declarations needed |
| spring-boot-starter-validation | managed | `org.springframework.boot:spring-boot-starter-validation` | Jakarta Bean Validation 3.1.1 for DTO input validation | Not included in starter-webmvc — must be added explicitly (changed in Boot 2.3+) |
| google-api-client | 2.9.0 | `com.google.api-client:google-api-client:2.9.0` | `GoogleIdTokenVerifier` for verifying Google ID tokens server-side | Official Google library; handles Google public key caching and RS256/ES256 signature verification |
| nimbus-jose-jwt | 10.8 (transitive from auth server starter) | `com.nimbusds:nimbus-jose-jwt` | JWT parsing, RSA key management, JWKSet construction | Pulled transitively by the authorization server starter — used directly for Apple JWKS verification and RSA keystore loading |
| postgresql | 42.7.10 (managed) | `org.postgresql:postgresql` (existing, runtime scope) | JDBC driver | Already in project |

### Infrastructure

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| PostgreSQL | 18 (`postgres:18`) | Primary database for users + refresh tokens | Project.md specifies PostgreSQL 18; official Docker image tag `postgres:18` confirmed available |
| Docker + docker-compose | Compose v2 syntax | Local dev environment | Standard for Spring Boot projects; multi-stage build reduces image size ~50% |
| eclipse-temurin | `25-jre` or `25.0.2_10-jre-noble` | Docker runtime base image | Official Adoptium JRE image; use JRE (not JDK) in runtime stage to reduce image size |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlin-maven-allopen | 2.2.21 (existing) | Opens Kotlin classes for Spring AOP/proxying | Already configured in pom.xml; required for Spring component proxying with Kotlin |
| HikariCP | 7.x (managed by Boot 4 via data-jpa) | JDBC connection pooling | Included automatically with spring-boot-starter-data-jpa; configure pool size in application.yml |
| Spring Boot DevTools | managed | Hot reload during development | Add as optional/test scope only; never ship in production jar |

---

## Maven Dependency Additions

Add to `pom.xml` `<dependencies>` block. All Spring starters use Boot 4's managed versions — no `<version>` tag needed.

```xml
<!-- ===== SECURITY ===== -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Spring Authorization Server (JWT signing only — JWKSource, NimbusJwtEncoder) -->
<!-- NOTE: This is the Spring Boot 4 artifact name. Old name "spring-boot-starter-oauth2-authorization-server" is deprecated. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-authorization-server</artifactId>
</dependency>

<!-- ===== PERSISTENCE ===== -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- ===== VALIDATION ===== -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- ===== GOOGLE ID TOKEN VERIFICATION ===== -->
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.9.0</version>
</dependency>
```

Note: `nimbus-jose-jwt` (10.8) is pulled **transitively** by the authorization server starter — no explicit declaration needed for RSA key operations and Apple JWKS parsing.

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Spring Authorization Server (JWT signing only) | JJWT (io.jsonwebtoken) | Use JJWT if you don't need the full JWKSource/JWK endpoint infrastructure and want a lighter dependency. JJWT requires manual RSA key loading and lacks built-in JWK Set endpoint support. |
| google-api-client 2.9.0 | Manual JWT parsing with nimbus-jose-jwt | Only if you want zero Google-specific dependencies. Manual approach: fetch `https://www.googleapis.com/oauth2/v3/certs`, parse JWKS, verify RS256 signature. More work but removes Google library dependency. |
| spring-boot-starter-data-jpa | Spring Data JDBC | Use Spring Data JDBC if you want explicit SQL control and no ORM magic. For this project, JPA is correct because the User entity has complex lifecycle needs (timestamps, UUID generation, provider tracking). |
| PostgreSQL 18 | PostgreSQL 17 | Use PostgreSQL 17 if you need a more battle-hardened image. PostgreSQL 18 is newly released; 17 is a safer choice for production if you're deploying soon. For local dev, 18 is fine. |
| eclipse-temurin:25-jre | amazoncorretto:25 or azul/zulu | Use corretto if deploying on AWS (AWS-optimized). Use eclipse-temurin for neutral, official Adoptium builds. |
| Virtual Threads (`spring.threads.virtual.enabled=true`) | Tomcat thread pool tuning | Use traditional thread pool only if running Java < 21. With Java 24/25, Virtual Threads give Tomcat-equivalent concurrency without configuration; set it and forget it. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `spring-boot-starter-oauth2-authorization-server` (old name) | Deprecated in Spring Boot 4 — will cause confusion or break in future releases. The Boot 4 Migration Guide explicitly renames this starter. | `spring-boot-starter-security-oauth2-authorization-server` |
| `com.fasterxml.jackson.*` imports (Jackson 2) | Spring Boot 4 migrated to Jackson 3 with `tools.jackson` group IDs. The `com.fasterxml.jackson` groupId is legacy; `jackson-module-kotlin` in pom.xml already correctly uses `tools.jackson.module`. Do NOT add new Jackson 2 dependencies. | `tools.jackson.*` (Jackson 3 — already used in this project's pom.xml) |
| Full Spring Authorization Server flows (Authorization Code, client registration, token endpoint) | The project explicitly scopes Authorization Server to JWT signing only. Standing up a full auth server adds ~10 beans, token storage, client registration, and discovery endpoint that are not needed. | Use only `JWKSource`, `NimbusJwtEncoder`, and `JwtDecoder` from the authorization server starter |
| Session-based Spring Security (`SessionCreationPolicy.IF_REQUIRED`) | Anti-pattern for stateless JWT API. Sessions cause horizontal scaling issues and defeat the purpose of JWT. | `SessionCreationPolicy.STATELESS` in `SecurityFilterChain` |
| `@Autowired` field injection in Kotlin | Anti-pattern in Kotlin. Field injection breaks null-safety guarantees and makes testing harder. The project.md explicitly forbids it. | Constructor injection (primary constructor parameters in `@Service`/`@Component` classes) |
| `!!` operator for null assertion in Kotlin | Can throw NullPointerException at runtime; defeats Kotlin null-safety. Project.md forbids it. | Safe calls (`?.`), `?: throw` expressions, or `requireNotNull()` |
| JJWT `0.9.x` (legacy) | Very old API, `io.jsonwebtoken` 0.9.x has breaking changes vs 0.11+ and uses deprecated classes. Often appears in old tutorials. | Spring's `NimbusJwtDecoder` / `NimbusJwtEncoder` (from nimbus-jose-jwt via authorization server starter) |
| Storing access tokens in the database | Defeats the stateless design — access tokens are self-contained RS256-signed JWTs. Only refresh tokens need DB storage. | Store only opaque refresh tokens in DB; validate access tokens by RS256 signature verification only |

---

## Stack Patterns by Variant

**For Google ID token verification:**
- Use `GoogleIdTokenVerifier` from `google-api-client:2.9.0`
- Configure with `GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory()).setAudience(listOf(clientId)).build()`
- The library handles Google public key fetching and caching automatically
- Confidence: HIGH (official Google library, documented in Google Cloud Java docs)

**For Apple ID token verification:**
- Do NOT use a dedicated Apple library — use `NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys")`
- Apple exposes a standard JWKS endpoint; Spring Security's NimbusJwtDecoder handles JWKS fetching/caching natively
- Validate claims manually: `iss == "https://appleid.apple.com"`, `aud == your-bundle-id`, `exp` not expired
- nimbus-jose-jwt is already transitive from the authorization server starter — no new dependency needed
- Confidence: MEDIUM (pattern verified from Spring Security docs and Apple Developer docs; no single authoritative Java/Kotlin example)

**For JWT signing (issuing access tokens):**
- Use `NimbusJwtEncoder` + `JWKSource<SecurityContext>` from authorization server starter
- Load RSA key from `.jks` keystore file at startup; fall back to runtime-generated key in dev if keystore absent
- RS256, 15-minute expiry as specified in PROJECT.md
- Confidence: HIGH (Spring Authorization Server Getting Started docs)

**For Virtual Threads with Tomcat:**
- Add `spring.threads.virtual.enabled=true` to `application.yml`
- Requires Java 21+ (project uses Java 24 — already satisfied)
- No other configuration needed; Tomcat, `@Async`, and `@Scheduled` all switch automatically
- Confidence: HIGH (Spring Boot docs + Feb 2026 benchmark article confirming Spring Boot 4.0.2 behavior)

---

## Version Compatibility

| Component | Version | Compatible With | Notes |
|-----------|---------|-----------------|-------|
| Spring Boot | 4.0.3 | Spring Security 7.0.x, Hibernate 7.2.4.Final, Spring Data 2025.1.x | Boot 4 manages all Spring versions — do not pin them manually |
| Spring Security | 7.0.x (managed) | Spring Authorization Server 7.0.x (same version series) | Authorization Server merged into Spring Security 7 project — versions are now synchronized |
| Hibernate | 7.2.4.Final (managed) | Jakarta EE 11, Java 17+ | Spring Boot 4 switched from Hibernate 6 (Boot 3) to Hibernate 7; Jakarta namespace (not javax) |
| google-api-client | 2.9.0 | Java 8+ | Published ~Feb 2026 per Maven Central; uses OkHttp transport internally |
| nimbus-jose-jwt | 10.8 (transitive) | Java 11+ | Transitive via authorization server starter; do not add explicit version to avoid conflicts |
| PostgreSQL JDBC | 42.7.10 (managed) | PostgreSQL 15-18 | Already in project as runtime dependency; Boot 4 manages version |
| Jackson | 3.x (tools.jackson, managed) | Spring MVC, Spring Boot 4 | **Breaking change from Boot 3**: group ID changed from `com.fasterxml.jackson` to `tools.jackson`. jackson-module-kotlin already uses `tools.jackson.module` in pom.xml — this is correct. |
| Jakarta Validation | 3.1.1 (managed) | Hibernate Validator 8.x | spring-boot-starter-validation pulls this; not bundled with starter-webmvc |
| Java | 24 (current), 25 (target) | Virtual Threads stable in 21+ | Java 25 is an LTS release candidate; safe to target |

---

## Sources

- Spring Boot 4.0 Migration Guide (GitHub Wiki) — artifact renames (`spring-boot-starter-oauth2-*` → `spring-boot-starter-security-oauth2-*`), Jackson 3 migration — MEDIUM confidence (page structure; content confirmed via WebFetch)
- [Spring Authorization Server Getting Started](https://docs.spring.io/spring-authorization-server/reference/getting-started.html) — Maven coordinates, JWKSource/JwtEncoder pattern — HIGH confidence
- [Spring Security — Getting Spring Security](https://docs.spring.io/spring-security/reference/getting-spring-security.html) — Spring Security 7.0.3 current version, Spring Boot starter coordinates — HIGH confidence
- [Spring Security — OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — NimbusJwtDecoder with JWK Set URI (Apple pattern), NimbusJwtDecoder.withJwkSetUri() API — HIGH confidence
- [Spring Boot Dependency Versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html) — Hibernate 7.2.4.Final, PostgreSQL 42.7.10, Jakarta Validation 3.1.1 — HIGH confidence
- [Maven Central: google-api-client](https://central.sonatype.com/artifact/com.google.api-client/google-api-client) — Latest version 2.9.0 confirmed — HIGH confidence
- [Maven Central: nimbus-jose-jwt](https://central.sonatype.com/artifact/com.nimbusds/nimbus-jose-jwt) — Latest version 10.8 confirmed — HIGH confidence
- [Apple Developer Docs — Fetch Apple's public key](https://developer.apple.com/documentation/signinwithapplerestapi/fetch-apple's-public-key-for-verifying-token-signature) — JWKS endpoint URL `https://appleid.apple.com/auth/keys` — HIGH confidence
- [spring.io blog — Spring Authorization Server moving to Spring Security 7.0](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/) — Merger announcement, artifact ID preserved with new version — MEDIUM confidence (page rendered as CSS only; content confirmed via secondary sources)
- [Docker Hub: eclipse-temurin](https://hub.docker.com/_/eclipse-temurin/tags) — `eclipse-temurin:25-jre` tag confirmed — HIGH confidence
- [Docker Hub: postgres](https://hub.docker.com/_/postgres/) — `postgres:18` tag confirmed with PGDATA path change note — HIGH confidence
- WebSearch: Spring Boot 4 Virtual Threads, Feb 2026 benchmark article (ITNEXT) — MEDIUM confidence (corroborates official docs)

---

*Stack research for: Spring Boot 4 JWT Auth Template — Google/Apple ID token verification, Spring Security 7, Spring Data JPA, Docker*
*Researched: 2026-03-01*
