# Phase 1: Foundation - Research

**Researched:** 2026-03-01
**Domain:** Spring Boot 4 / Kotlin / JPA / RSA JWT Infrastructure
**Confidence:** MEDIUM-HIGH (Spring Boot 4 is recent; most findings verified via official docs and migration guide)

---

## Summary

Phase 1 establishes the non-auth skeleton of the application: a working database connection, JPA entities (User and RefreshToken), RSA key infrastructure, and environment-based configuration. No auth logic is wired in this phase.

The project already uses Spring Boot **4.0.3** with Kotlin **2.2.21** and Java **24**. This is a significant version: Spring Boot 4 ships with Hibernate 7.1, Jakarta Persistence 3.2, Spring Security 7, and Jackson 3. Several dependency coordinates changed from the 3.x ecosystem (Jackson group IDs, starter renames). The pom.xml already has the correct `tools.jackson.module:jackson-module-kotlin` Jackson 3 coordinate, but it is missing `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-authorization-server` (or its replacement), `spring-boot-starter-validation`, and the Kotlin JPA/no-arg compiler plugins.

The key technical decisions for this phase are: (1) Kotlin JPA entities require the `kotlin-maven-noarg` (`jpa` preset) and `kotlin-maven-allopen` compiler plugins — without them, entities are final and have no no-arg constructor, breaking Hibernate; (2) UUID primary keys should use `@GeneratedValue(strategy = GenerationType.UUID)` from JPA 3.1; (3) RSA keystore should be a `.jks` file loaded from classpath, with a programmatic `KeyPairGenerator` fallback in the dev profile when no keystore file is present; (4) the `application.yml` multi-document approach using `---` separators with `spring.config.activate.on-profile` should be used for dev/prod profile separation.

**Primary recommendation:** Wire all pom.xml dependencies and Kotlin compiler plugins first, then create JPA entities, then configure RSA infrastructure — in that order, verifying the application starts at each step.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| INFR-01 | Virtual Threads enabled via `spring.threads.virtual.enabled=true` | Confirmed: single property in application.yml; auto-configures Tomcat, @Async, scheduler. Java 21+ required (project uses Java 24 — satisfied). |
| INFR-02 | Spring Data JPA + Hibernate for User and RefreshToken persistence | Requires `spring-boot-starter-data-jpa` dependency + Kotlin `jpa` + `all-open` compiler plugins. Hibernate 7.1 included via Spring Boot 4 BOM. |
| INFR-03 | docker-compose.yml with PostgreSQL 18 | PostgreSQL 18 official Docker image (`postgres:18`) is available on Docker Hub. PGDATA path changed in PG18: now `/var/lib/postgresql`. |
| INFR-04 | application.yml with dev and prod profiles | Multi-document YAML with `---` separator and `spring.config.activate.on-profile` per section. Sensitive values as `${ENV_VAR}` placeholders. |
| INFR-05 | .env.example documenting all required environment variables | Plain file with comments — no library needed. Must cover DB, keystore, and any future auth vars. |
| INFR-06 | Domain-based package layout: config/, user/, authentication/ | Kotlin package layout under `kz.innlab.template`: `config/`, `user/`, `authentication/`. No framework constraint — pure convention. |
| USER-01 | User entity with UUID id, email, name, picture, provider (enum), providerId, roles, createdAt, updatedAt | Kotlin regular class (not data class) with `@Entity`. UUID via `GenerationType.UUID`. Enum via `@Enumerated(EnumType.STRING)`. Roles as `@ElementCollection` with `@Enumerated(EnumType.STRING)`. |
| USER-02 | Users identified by (provider, providerId) composite key — never by email | `@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])])`. No `@EmbeddedId` needed — composite uniqueness via constraint. |
| SECU-06 | RSA keystore (.jks) for JWT signing with helper generation script + runtime fallback for dev | `keytool -genkeypair -alias jwt -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore jwt.p12`. Dev profile: programmatic `KeyPairGenerator` fallback when no file configured. Exposes `JWKSource`, `JwtEncoder`, `JwtDecoder` beans. |
| SECU-09 | No hardcoded secrets — all sensitive values via environment variables / `${ENV_VAR}` in application.yml | Spring Boot `${ENV_VAR:default}` syntax. Keystore password, DB password, DB URL all externalized. |
</phase_requirements>

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-data-jpa` | managed by Boot 4.0.3 BOM | Spring Data repositories + Hibernate 7.1 ORM | Official starter; includes HikariCP, Hibernate, Spring Data |
| `spring-boot-starter-security` | managed by Boot 4.0.3 BOM | Spring Security 7 base (needed for oauth2 JWT beans) | Required transitive for JWT encoder/decoder |
| `spring-boot-starter-oauth2-authorization-server` | managed by Boot 4.0.3 BOM (deprecated) OR `spring-boot-starter-security-oauth2-authorization-server` | Brings `NimbusJwtEncoder`, `JWKSource`, `OAuth2AuthorizationServerConfiguration` | Official Spring way to use Authorization Server JWT infrastructure without a full auth server |
| `spring-boot-starter-validation` | managed by Boot 4.0.3 BOM | Jakarta Validation 3.1 for DTO validation | Official starter; Hibernate Validator bundled |
| `postgresql` | managed by Boot 4.0.3 BOM | PostgreSQL JDBC driver | Only JDBC driver for PostgreSQL |
| `kotlin-maven-noarg` + `jpa` plugin | `${kotlin.version}` = 2.2.21 | Generates synthetic no-arg constructors for `@Entity`, `@Embeddable`, `@MappedSuperclass` | JPA spec requires public no-arg constructor; Kotlin classes don't have them by default |
| `kotlin-maven-allopen` + `all-open` annotation config | `${kotlin.version}` = 2.2.21 | Makes `@Entity` classes and their members open (non-final) | Hibernate requires non-final classes to create lazy-loading proxies |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `tools.jackson.module:jackson-module-kotlin` | already in pom.xml | Kotlin-specific Jackson 3 deserialization | Already present — no action needed |
| `com.nimbus-jose-jwt` | transitive via auth-server starter | RSA key operations, JWK creation | Pulled in transitively — no explicit declaration needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| JKS/PKCS12 keystore file | Raw PEM private key file (`@Value("${jwt.private.key}") RSAPrivateKey`) | PEM approach is simpler for dev/CI but less standard for production key management |
| `@ElementCollection` for roles | Separate `Role` entity with `@ManyToMany` | `@ElementCollection` is simpler for a template — roles are a simple enum set; `@ManyToMany` adds a join table entity and more complexity |
| `GenerationType.UUID` | Application-generated UUID (set in init block) | Application-generated gives more control over UUID version; `GenerationType.UUID` is cleaner and JPA-standard |

---

## Architecture Patterns

### Recommended Project Structure
```
src/main/kotlin/kz/innlab/template/
├── config/          # Spring configuration classes (JPA, Security, RSA, CORS)
│   └── RsaKeyConfig.kt      # JWKSource, JwtEncoder, JwtDecoder beans
├── user/            # User domain: entity, repository, service
│   ├── User.kt              # @Entity
│   ├── UserRepository.kt    # JpaRepository<User, UUID>
│   └── AuthProvider.kt      # enum: GOOGLE, APPLE
├── authentication/  # Auth domain (Phase 2+): tokens, filters, controllers
│   └── RefreshToken.kt      # @Entity (Phase 1: entity only, no service)
└── TemplateApplication.kt
```

### Pattern 1: Kotlin JPA Entity (Non-Data-Class)
**What:** Regular Kotlin class annotated with `@Entity`. No `data class` — avoids problematic auto-generated `equals`/`hashCode`/`toString` on lazy associations.
**When to use:** All JPA-managed entities.
**Example:**
```kotlin
// Source: Kotlin docs + jpa-buddy.com best practices
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])]
)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    var email: String,

    var name: String? = null,
    var picture: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provider: AuthProvider,

    @Column(name = "provider_id", nullable = false)
    var providerId: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role")
    var roles: MutableSet<Role> = mutableSetOf(Role.USER),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
) {
    // Manual equals/hashCode using only id — avoids lazy-load triggers
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id != null && id == other.id
    }
    override fun hashCode(): Int = id?.hashCode() ?: 0
}
```

### Pattern 2: RSA Key Infrastructure with Dev Fallback
**What:** Production loads keystore from file; dev profile falls back to programmatically generated in-memory RSA keypair.
**When to use:** SECU-06 — dev must start without a keystore file; prod requires a real one.
**Example:**
```kotlin
// Source: Spring Security reference docs + spring-security-samples RestConfig.java
@Configuration
class RsaKeyConfig(
    @Value("\${app.security.jwt.keystore-location:#{null}}")
    private val keystoreLocation: String?,
    @Value("\${app.security.jwt.keystore-password:#{null}}")
    private val keystorePassword: String?,
    @Value("\${app.security.jwt.key-alias:jwt}")
    private val keyAlias: String
) {

    @Bean
    fun rsaKeyPair(): KeyPair {
        if (keystoreLocation != null && keystorePassword != null) {
            // Production: load from .jks or .p12 file
            val resource = ClassPathResource(keystoreLocation)
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(resource.inputStream, keystorePassword.toCharArray())
            val privateKey = keyStore.getKey(keyAlias, keystorePassword.toCharArray()) as PrivateKey
            val publicKey = keyStore.getCertificate(keyAlias).publicKey
            return KeyPair(publicKey, privateKey)
        }
        // Dev fallback: generate in-memory (NOT safe for production — keys rotate on restart)
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }

    @Bean
    fun jwkSource(keyPair: KeyPair): JWKSource<SecurityContext> {
        val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private)
            .keyID(UUID.randomUUID().toString())
            .build()
        return ImmutableJWKSet(JWKSet(rsaKey))
    }

    @Bean
    fun jwtEncoder(jwkSource: JWKSource<SecurityContext>): JwtEncoder =
        NimbusJwtEncoder(jwkSource)

    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)
}
```

### Pattern 3: Multi-Document application.yml
**What:** Single file with `---` separators for profile-specific sections; sensitive values always as `${ENV_VAR}` placeholders.
**When to use:** INFR-04, SECU-09.
**Example:**
```yaml
# Default / common config
spring:
  application:
    name: template
  threads:
    virtual:
      enabled: true
  jpa:
    open-in-view: false

server:
  port: 7070

---
# Dev profile
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:postgresql://localhost:5432/${DB_NAME:template}
    username: ${DB_USERNAME:template}
    password: ${DB_PASSWORD:template}
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

---
# Prod profile
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate

app:
  security:
    jwt:
      keystore-location: ${JWT_KEYSTORE_LOCATION}
      keystore-password: ${JWT_KEYSTORE_PASSWORD}
      key-alias: ${JWT_KEY_ALIAS:jwt}
```

### Pattern 4: RefreshToken Entity (stub for Phase 1)
**What:** JPA entity for opaque refresh tokens stored as SHA-256 hashes. Only the entity and repository; no service logic in Phase 1.
**When to use:** Phase 1 creates the table; Phase 3 wires the rotation logic.
**Example:**
```kotlin
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    // Stored as SHA-256 hash of the raw token — never store plaintext
    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RefreshToken) return false
        return id != null && id == other.id
    }
    override fun hashCode(): Int = id?.hashCode() ?: 0
}
```

### Anti-Patterns to Avoid
- **Using `data class` for JPA entities:** Auto-generated `equals`/`hashCode` includes all fields including lazy associations, causing `LazyInitializationException` or N+1 queries when collections are included in the check. Use a regular class with manual `equals`/`hashCode` based only on `id`.
- **Omitting kotlin-maven-noarg/allopen plugins:** Kotlin classes are `final` by default. Without `allopen`, Hibernate cannot create subclass-based proxies for lazy loading. Without `noarg`, JPA cannot instantiate entities (requires a public no-arg constructor at bytecode level).
- **Using `@Enumerated(EnumType.ORDINAL)` for roles/provider:** Ordinal values break when enum constants are reordered. Always use `EnumType.STRING`.
- **Hardcoding dev credentials as defaults in prod profile:** Dev defaults (`:template`) must only exist in the dev profile section, never in the prod section.
- **Committing the keystore file:** The `.jks`/`.p12` file must be in `.gitignore`. The repo provides only a generation script.
- **Setting `ddl-auto: create-drop` in prod:** Destroys schema on shutdown. Production must use `validate` (or `none` with Flyway — deferred to v2).
- **Spring Authorization Server auto-configuration conflict:** Adding the `spring-boot-starter-oauth2-authorization-server` starter without disabling its auto-configuration for the full authorization server will trigger an `AuthorizationServerSecurityFilterChain` bean that conflicts with Phase 2's custom security chain. The config class must either exclude the auto-configuration or explicitly define the `SecurityFilterChain` order.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| No-arg constructors for Kotlin entities | Manual no-arg secondary constructor on every entity | `kotlin-maven-noarg` + `jpa` plugin preset | Plugin generates it at bytecode level — no source clutter; handles `@Embeddable` and `@MappedSuperclass` too |
| Making Kotlin classes open for Hibernate | Manually mark every entity and all its properties as `open` | `kotlin-maven-allopen` with `@Entity`, `@MappedSuperclass`, `@Embeddable` annotations configured | Plugin is comprehensive and future-proof; manual `open` is easy to forget on new properties |
| RSA key wrapping for JWK | Custom JWK JSON builder | Nimbus `RSAKey.Builder` + `ImmutableJWKSet` | Nimbus handles key ID, algorithm specification, serialization correctly |
| JwtDecoder from JWKSource | Custom token parser | `OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)` | Single line; handles key rotation, algorithm selection, claims extraction |
| UUID generation | Custom UUID utility | `@GeneratedValue(strategy = GenerationType.UUID)` | JPA 3.1 standard; Hibernate generates RFC 4122 v4 UUID before insert |

**Key insight:** Every item in this list seems trivially easy to hand-roll but carries subtle edge cases — lazy proxy compatibility (open classes), key ID consistency (JWK), UUID mutation hazards (GenerationType). Use the provided infrastructure.

---

## Common Pitfalls

### Pitfall 1: Missing Kotlin Compiler Plugins (High Impact)
**What goes wrong:** Application compiles but fails at runtime: `InstantiationException: User has no default constructor` from JPA, or `Cannot subclass final class User` from CGLIB proxy creation.
**Why it happens:** Kotlin classes are `final` and have no no-arg constructor by default. The plugins fix this at compile time; without them, Hibernate cannot work at all.
**How to avoid:** Add BOTH `jpa` and `all-open` to `compilerPlugins`, add BOTH `kotlin-maven-noarg` and `kotlin-maven-allopen` to plugin dependencies. The `spring` plugin (already in pom.xml) handles `@Component`/`@Service` etc., but does NOT cover `@Entity`.
**Warning signs:** `InstantiationException`, `LazyInitializationException` during startup, or "cannot subclass final class" in stack trace.

### Pitfall 2: Spring Authorization Server Auto-Configuration Conflict
**What goes wrong:** Adding the auth-server starter triggers a default `SecurityFilterChain` bean for an authorization server. This conflicts with the custom `SecurityFilterChain` added in Phase 2, causing ambiguous bean errors or incorrect security behavior.
**Why it happens:** The starter enables Spring Authorization Server auto-configuration, which registers a full OAuth 2.1 authorization server configuration by default.
**How to avoid:** In Phase 1, either (a) do not add the auth-server starter yet (add it in Phase 2 when `SecurityFilterChain` is defined), or (b) add `@SpringBootApplication(exclude = [AuthorizationServerAutoConfiguration::class])` to prevent auto-configuration. The requirements say we use it only for `JWKSource`/`JwtEncoder`, not the full server.
**Warning signs:** Multiple `SecurityFilterChain` beans in context; application starts but all endpoints return 401 or redirect to OAuth login.

### Pitfall 3: PostgreSQL 18 PGDATA Path Change
**What goes wrong:** Docker container fails to start or data doesn't persist in the expected volume mount location.
**Why it happens:** PostgreSQL 18 changed the default `PGDATA` path from `/var/lib/postgresql/data` to `/var/lib/postgresql` (without `/data`).
**How to avoid:** In `docker-compose.yml`, explicitly set `PGDATA` environment variable OR ensure the volume mount targets the correct path for PG18. Verify with the official Docker Hub `postgres:18` documentation.
**Warning signs:** `data directory has wrong ownership` or empty data directory on container restart.

### Pitfall 4: Jackson 3 Group ID Mismatch
**What goes wrong:** Jackson-related beans fail to configure; `ClassNotFoundException` for `com.fasterxml.jackson.module.kotlin.KotlinModule`.
**Why it happens:** Jackson 3 (used by Spring Boot 4) moved all modules from `com.fasterxml.jackson` group to `tools.jackson`. The pom.xml already has `tools.jackson.module:jackson-module-kotlin` — this is correct. If any dependency pulls in Jackson 2 transitively, there will be a conflict.
**How to avoid:** Use only `tools.jackson.*` coordinates. The project pom.xml already has the correct coordinate — verify no other dependency introduces Jackson 2 transitively.
**Warning signs:** `NoSuchMethodError`, `ClassNotFoundException` referencing `com.fasterxml.jackson`.

### Pitfall 5: `@ElementCollection` roles stored as ORDINAL
**What goes wrong:** Adding a new role at a non-end position in the enum breaks all existing user role data silently — ordinal 0 maps to the wrong role.
**Why it happens:** `@ElementCollection` defaults to `@Enumerated(EnumType.ORDINAL)` unless overridden.
**How to avoid:** Always explicitly add `@Enumerated(EnumType.STRING)` on the `@ElementCollection` column.
**Warning signs:** `ClassCastException` or unexpected roles returned for users after adding an enum constant.

### Pitfall 6: `open-in-view: false` Missing
**What goes wrong:** Hibernate session held open for entire HTTP request lifecycle; N+1 queries; false sense of lazy loading working outside transaction boundaries.
**Why it happens:** Spring Boot defaults `open-in-view` to `true` for Spring MVC, keeping the persistence context open during view rendering.
**How to avoid:** Set `spring.jpa.open-in-view: false` in the base application.yml section. This is required for stateless, token-based APIs.
**Warning signs:** Startup warning log: `spring.jpa.open-in-view is enabled by default`.

---

## Code Examples

Verified patterns from official sources:

### pom.xml: Kotlin Compiler Plugins for JPA
```xml
<!-- Source: kotlinlang.org/docs/no-arg-plugin.html + kotlinlang.org/docs/all-open-plugin.html -->
<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <configuration>
        <args>
            <arg>-Xjsr305=strict</arg>
            <arg>-Xannotation-default-target=param-property</arg>
        </args>
        <compilerPlugins>
            <plugin>spring</plugin>  <!-- already present -->
            <plugin>jpa</plugin>     <!-- ADD: no-arg for @Entity -->
            <plugin>all-open</plugin> <!-- ADD: open for @Entity proxy support -->
        </compilerPlugins>
        <pluginOptions>
            <!-- all-open: make @Entity, @MappedSuperclass, @Embeddable classes open -->
            <option>all-open:annotation=jakarta.persistence.Entity</option>
            <option>all-open:annotation=jakarta.persistence.MappedSuperclass</option>
            <option>all-open:annotation=jakarta.persistence.Embeddable</option>
        </pluginOptions>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-allopen</artifactId>
            <version>${kotlin.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-noarg</artifactId>
            <version>${kotlin.version}</version>
        </dependency>
    </dependencies>
</plugin>
```

### pom.xml: New Dependencies to Add
```xml
<!-- Source: Spring Boot 4 BOM — no version needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<!-- For NimbusJwtEncoder, JWKSource, OAuth2AuthorizationServerConfiguration.jwtDecoder() -->
<!-- NOTE: deprecated in favor of spring-boot-starter-security-oauth2-authorization-server
     in Spring Boot 4.1+. At 4.0.3, use the name below. Verify exact artifact at boot startup. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### Keystore Generation Script
```bash
#!/bin/bash
# Source: Spring Boot wiki + keytool docs
# Generates a PKCS12 keystore for JWT RSA signing
# Usage: ./scripts/generate-keystore.sh

KEYSTORE_PATH="src/main/resources/jwt-keystore.p12"
ALIAS="jwt"
STOREPASS="changeit"
VALIDITY=3650
KEYSIZE=2048

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize "$KEYSIZE" \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$STOREPASS" \
  -validity "$VALIDITY" \
  -dname "CN=localhost, OU=Dev, O=Template, L=, ST=, C=US" \
  -noprompt

echo "Keystore generated at $KEYSTORE_PATH"
echo "IMPORTANT: Add $KEYSTORE_PATH to .gitignore"
echo "Copy it out of src/main/resources and configure via environment variable in production"
```

### docker-compose.yml for PostgreSQL 18
```yaml
# Source: PostgreSQL 18 Docker Hub docs (postgres:18 tag)
version: '3.9'
services:
  postgres:
    image: postgres:18
    container_name: template-postgres
    environment:
      POSTGRES_DB: ${DB_NAME:-template}
      POSTGRES_USER: ${DB_USERNAME:-template}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-template}
      # PG18 changed default PGDATA — explicitly set for clarity
      PGDATA: /var/lib/postgresql/data
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME:-template}"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

### .env.example
```bash
# Database
DB_NAME=template
DB_USERNAME=template
DB_PASSWORD=changeme

# JWT Keystore (production only — dev uses auto-generated in-memory keypair)
JWT_KEYSTORE_LOCATION=jwt-keystore.p12
JWT_KEYSTORE_PASSWORD=changeit
JWT_KEY_ALIAS=jwt

# Spring profile
SPRING_PROFILES_ACTIVE=dev
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `com.fasterxml.jackson.module:jackson-module-kotlin` | `tools.jackson.module:jackson-module-kotlin` | Jackson 3 / Spring Boot 4.0 | pom.xml already correct; nothing to do |
| `javax.persistence.*` annotations | `jakarta.persistence.*` | Spring Boot 3.0 (Jakarta EE) | Fully migrated; project uses Spring Boot 4 which requires Jakarta |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | Spring Boot 4.0 | pom.xml already uses `spring-boot-starter-webmvc` — correct |
| `@JsonComponent` / `@JsonMixin` | `@JacksonComponent` / `@JacksonMixin` | Spring Boot 4.0 | Not used in Phase 1; awareness for later |
| Hibernate `@Type(type="uuid-char")` | `@GeneratedValue(strategy = GenerationType.UUID)` | JPA 3.1 / Hibernate 6.2+ | Cleaner standard approach; no custom types needed |
| `spring-authorization-server.version` property override | `spring-security.version` property override | Spring Boot 4.0 | Spring Authorization Server merged into Spring Security 7 |

**Deprecated/outdated:**
- `spring-boot-starter-oauth2-authorization-server`: deprecated in Spring Boot 4.1+ in favor of `spring-boot-starter-security-oauth2-authorization-server`. At current project version (4.0.3), use the original name — it is still valid and not yet renamed.
- `@MockBean` / `@SpyBean`: deprecated in Spring Boot 4.0 in favor of `@MockitoBean` / `@MockitoSpyBean` — not relevant for Phase 1.
- `EnumType.ORDINAL`: technically still works but considered unsafe for enums that may gain new constants. Use `STRING`.

---

## Open Questions

1. **Exact artifact name for OAuth2 authorization server starter at Boot 4.0.3**
   - What we know: `spring-boot-starter-oauth2-authorization-server` is valid at 4.0.3; it is deprecated starting 4.1.0-M2 in favor of `spring-boot-starter-security-oauth2-authorization-server`
   - What's unclear: Whether the auto-configuration triggers conflict needs a test build to confirm; the research shows it can conflict if `SecurityFilterChain` is not properly ordered
   - Recommendation: Add the starter in Phase 1 to get the JWT infrastructure beans, AND add `@SpringBootApplication(exclude = [OAuth2AuthorizationServerAutoConfiguration::class])` to prevent the full authorization server configuration from activating. Verify in Phase 2 when security is wired.

2. **PostgreSQL 18 PGDATA path in docker-compose**
   - What we know: PG18 changed the PGDATA default; `postgres:18` image is available on Docker Hub
   - What's unclear: The exact volume mount path (`/var/lib/postgresql/data` vs `/var/lib/postgresql`) for the PG18 official Docker image needs validation by running `docker pull postgres:18` and checking the image documentation
   - Recommendation: Use explicit `PGDATA: /var/lib/postgresql/data` in docker-compose to be safe

3. **`@CreationTimestamp` / `@UpdateTimestamp` in Hibernate 7**
   - What we know: These annotations exist since Hibernate 5 and were not mentioned as changed in the migration guide
   - What's unclear: Whether Hibernate 7.1 changed the import path (was `org.hibernate.annotations`, not Jakarta)
   - Recommendation: Use `@CreationTimestamp` and `@UpdateTimestamp` from `org.hibernate.annotations` — verify import resolves at compile time

---

## Sources

### Primary (HIGH confidence)
- [Spring Boot 4.0 Release Notes (GitHub Wiki)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) — Hibernate 7.1, Jakarta EE 11, virtual threads, Kotlin 2.2 baseline
- [Spring Boot 4.0 Migration Guide (GitHub Wiki)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) — Jackson group IDs, starter renames, persistence module, hibernate-processor
- [Kotlin No-Arg Plugin docs](https://kotlinlang.org/docs/no-arg-plugin.html) — `jpa` preset, Maven pom.xml configuration
- [Kotlin All-Open Plugin docs](https://kotlinlang.org/docs/all-open-plugin.html) — annotation-based open configuration for Maven
- [Spring Security Resource Server JWT docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — `NimbusJwtDecoder`, `NimbusJwtEncoder`, `JWKSource` configuration
- [Spring Security Kotlin configuration](https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html) — Kotlin DSL for security config
- [spring-boot-starter-oauth2-authorization-server on Maven Central](https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-oauth2-authorization-server) — version availability and deprecation status
- [spring-boot-starter-security-oauth2-authorization-server on Maven Central](https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-security-oauth2-authorization-server) — 4.0.3 is latest stable

### Secondary (MEDIUM confidence)
- [JPA Buddy: Best Practices and Pitfalls with Kotlin](https://jpa-buddy.com/blog/best-practices-and-common-pitfalls/) — regular class vs data class for entities, lazy loading pitfalls
- [dimitri.codes: @EnumeratedValue in Spring Boot 4](https://dimitri.codes/spring-boot-enumeratedvalue/) — new JPA 3.2 enum mapping annotation
- [Spring Security samples: RestConfig.java](https://github.com/spring-projects/spring-security-samples/blob/main/servlet/spring-boot/java/jwt/login/src/main/java/example/RestConfig.java) — canonical JwtEncoder/JwtDecoder RSA configuration
- [Spring Boot wiki: Generating SSL KeyStores](https://github.com/spring-projects/spring-boot/wiki/Generating-SSL-KeyStores) — keytool commands
- [PostgreSQL Docker Hub image](https://hub.docker.com/_/postgres/) — `postgres:18` availability and PGDATA change
- [Spring.io blog: Introducing Jackson 3 support](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/) — `tools.jackson` group IDs

### Tertiary (LOW confidence — verify before use)
- WebSearch result: "spring-boot-starter-oauth2-authorization-server replaced by spring-boot-starter-security-oauth2-authorization-server" — partially confirmed by Maven Central; exact rename schedule needs verification against Boot 4.0.3 changelog
- WebSearch result: PostgreSQL 18 PGDATA path is `/var/lib/postgresql` — conflicting with other sources saying `/var/lib/postgresql/data`; needs empirical verification

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all core libraries are from official Spring Boot 4 BOM; Kotlin plugin configuration verified from official Kotlin docs
- Architecture: MEDIUM-HIGH — entity patterns verified from JPA spec + jpa-buddy; RSA config verified from Spring Security samples; profile YAML pattern is unchanged from Boot 2.4+
- Pitfalls: HIGH — compiler plugin omission is a well-documented and frequently reported issue; PG18 PGDATA path change is documented; Jackson group ID confirmed in Spring migration guide

**Research date:** 2026-03-01
**Valid until:** 2026-04-01 (Spring Boot 4 is stable; Kotlin/JPA patterns are stable; 30-day window is safe)
