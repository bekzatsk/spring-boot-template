# Phase 4: Replace UUID v4 with UUID v7 — Research

**Researched:** 2026-03-02
**Domain:** UUID v7 generation in Kotlin/JPA/Hibernate + Spring Boot 4.x
**Confidence:** HIGH

---

## Summary

The project currently uses `@GeneratedValue(strategy = GenerationType.UUID)` on all three JPA entities (`User`, `RefreshToken`, `SmsVerification`), which instructs Hibernate to generate UUID v4 (random) identifiers. UUID v4 causes scattered B-tree index insertions in PostgreSQL — benchmarks show 500x more page splits vs sequential IDs, significantly degrading INSERT performance at scale.

UUID v7 is time-ordered (Unix Epoch milliseconds in the high bits), so new records always append to the end of the B-tree index. This makes INSERT throughput dramatically better and enables cursor-based pagination by ID alone (no need to index `created_at`). The library `com.github.f4b6a3:uuid-creator` 6.1.1 provides `UuidCreator.getTimeOrderedEpoch()` — a thread-safe, RFC 9562-compliant UUID v7 generator.

**The primary migration strategy** is Option A (from the phase spec): replace `@GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null` with `val id: UUID = UuidCreator.getTimeOrderedEpoch()` on each entity. This is application-assigned ID generation. However, this creates a critical Hibernate/Spring Data JPA behavioral issue that MUST be handled: when `id` is never null, `SimpleJpaRepository.save()` always calls `merge()` instead of `persist()`, causing an extra SELECT before every INSERT. The fix is to implement `Persistable<UUID>` on each entity (or a shared `@MappedSuperclass` base). No Flyway migration is needed — the `UUID` column type accepts any UUID version.

**Primary recommendation:** Use `com.github.f4b6a3:uuid-creator:6.1.1` with `UuidCreator.getTimeOrderedEpoch()` as the property initializer, and implement `Persistable<UUID>` with a `@Transient isNew` flag to prevent merge-on-save.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| com.github.f4b6a3:uuid-creator | 6.1.1 | UUID v7 (time-ordered) generation | Actively maintained, RFC 9562 compliant, thread-safe, zero transitive deps |
| Spring Data JPA Persistable | (bundled) | Entity state detection for pre-assigned IDs | Prevents merge-on-save when ID is never null |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Hibernate JPA (bundled with Spring Boot 4.x) | 7.x | ORM layer | Already present — no change needed |
| Kotlin all-open plugin | (already configured) | Makes entity classes open for Hibernate proxies | Already configured in pom.xml |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| uuid-creator 6.1.1 | Java 26 built-in `UUID.ofEpochMillis()` | Java 26 not yet released (scheduled ~2026-09); project is on Java 24 |
| uuid-creator 6.1.1 | java-uuid-generator (JUG) by cowtowncoder | Both are valid; uuid-creator is simpler API, JUG is more widely cited in Spring Boot ecosystem |
| Persistable<UUID> | @Version field for isNew detection | @Version adds optimistic locking semantics; overkill if not needed; Persistable is direct |
| Persistable<UUID> | Custom Hibernate IdentifierGenerator (Option B) | Option B keeps @GeneratedValue but adds a class per entity; more boilerplate, no advantage |

**Installation:**
```xml
<!-- pom.xml — add to <dependencies> -->
<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>uuid-creator</artifactId>
    <version>6.1.1</version>
</dependency>
```

No BOM entry for uuid-creator in Spring Boot 4.x — explicit version required.

---

## Architecture Patterns

### Scope of Changes

All three JPA entities use `@GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null`. These must all change:

| File | Current | Change Required |
|------|---------|-----------------|
| `user/model/User.kt` | `@GeneratedValue(UUID) val id: UUID? = null` | UUID v7 initializer + Persistable |
| `authentication/model/RefreshToken.kt` | `@GeneratedValue(UUID) val id: UUID? = null` | UUID v7 initializer + Persistable |
| `authentication/model/SmsVerification.kt` | `@GeneratedValue(UUID) val id: UUID? = null` | UUID v7 initializer + Persistable |

Non-entity `UUID.randomUUID()` usages (in `RsaKeyConfig.kt`, test files) are NOT part of this phase — JWK key IDs and test data identifiers do not need time-ordering.

### Pattern 1: Shared `@MappedSuperclass` Base Entity

**What:** Abstract base class that all entities extend. Provides the UUID v7 id field + `Persistable<UUID>` implementation. Centralizes the pattern instead of duplicating it in each entity.

**When to use:** Any time multiple entities need application-assigned UUIDs (which is always the case here).

```kotlin
// Source: Spring Data JPA Persistable docs + uuid-creator README
// File: shared/model/BaseEntity.kt (NEW)
package kz.innlab.template.shared.model

import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity : Persistable<UUID> {

    @Id
    val id: UUID = UuidCreator.getTimeOrderedEpoch()

    @Transient
    private var _new: Boolean = true

    override fun getId(): UUID = id

    override fun isNew(): Boolean = _new

    @PostPersist
    @PostLoad
    fun markNotNew() {
        _new = false
    }
}
```

**Each entity then becomes:**
```kotlin
// User.kt — extends BaseEntity, removes @Id + @GeneratedValue
@Entity
@Table(name = "users")
class User(
    @Column(nullable = false)
    var email: String,
) : BaseEntity() {
    // @Id and id field REMOVED — inherited from BaseEntity
    // rest of fields unchanged ...
}
```

### Pattern 2: Inline Per-Entity (No Base Class)

**What:** Each entity gets its own `@Id val id: UUID` + `Persistable<UUID>` implementation directly.

**When to use:** If a shared base class is undesirable (e.g., entity already extends another class). For this project, the base class approach is cleaner.

```kotlin
// Per-entity pattern (reference — prefer base class)
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    // ... constructor params ...
) : Persistable<UUID> {

    @Id
    val id: UUID = UuidCreator.getTimeOrderedEpoch()

    @Transient
    private var _new: Boolean = true

    override fun getId(): UUID = id
    override fun isNew(): Boolean = _new

    @PostPersist @PostLoad
    fun markNotNew() { _new = false }

    // ... rest of fields ...
}
```

### Pattern 3: equals/hashCode Adjustment

**What:** Current entities use `id?.hashCode() ?: 0` (id is nullable). After UUID v7, id is never null — simplify accordingly.

```kotlin
// BEFORE (nullable id):
override fun hashCode(): Int = id?.hashCode() ?: 0

// AFTER (non-null id):
override fun hashCode(): Int = id.hashCode()

// equals() also simplifies:
override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is User) return false
    return id == other.id   // no null check needed
}
```

### Recommended Project Structure (new file only)
```
src/main/kotlin/kz/innlab/template/
└── shared/
    ├── error/
    │   └── ErrorResponse.kt          (existing)
    └── model/
        └── BaseEntity.kt             (NEW — @MappedSuperclass)
```

### Anti-Patterns to Avoid

- **Keep `@GeneratedValue(strategy = GenerationType.UUID)` with a non-null initializer:** Hibernate sees the id is non-null and may conflict — remove `@GeneratedValue` entirely when using application-assigned IDs.
- **Use `UUID.randomUUID()` for entity IDs:** UUID v4 is the entire problem being solved; never use it for primary keys.
- **Skipping `Persistable<UUID>`:** Without it, `SimpleJpaRepository.save()` calls `merge()` for every new entity (requires a SELECT first), negating performance gains.
- **Modifying Flyway migrations to change column defaults:** The `UUID` column type in PostgreSQL accepts any UUID version. `gen_random_uuid()` defaults in migrations are irrelevant since Hibernate now sets the ID client-side. No migration needed.
- **Changing `UUID.randomUUID()` in `RsaKeyConfig.kt` or test files:** Those UUIDs are not primary keys — leave them as-is. Scope is limited to JPA entity `@Id` fields only.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| UUID v7 generation | Custom bit-manipulation of timestamp + random | `UuidCreator.getTimeOrderedEpoch()` | RFC 9562 compliance, thread safety, monotonic counter within same millisecond — all complex to implement correctly |
| isNew detection | Track persistence state manually per-service | `Persistable<UUID>` + `@PostPersist`/`@PostLoad` | JPA lifecycle hooks ensure correct state across load/persist/merge paths |
| B-tree ordering | Custom `created_at` index for pagination cursor | UUID v7 id as cursor | UUID v7 contains timestamp — the id itself is the sort key |

**Key insight:** UUID v7 generation has a subtle monotonic counter requirement (multiple UUIDs within the same millisecond must still sort correctly). `UuidCreator` handles this internally with a `ReentrantLock`-protected counter. Any hand-rolled solution must replicate this or risk duplicates.

---

## Common Pitfalls

### Pitfall 1: Hibernate merge() instead of persist() for new entities
**What goes wrong:** After removing `@GeneratedValue` and initializing `id = UuidCreator.getTimeOrderedEpoch()`, the id is never null. Spring Data's `SimpleJpaRepository.save()` checks `entityInformation.isNew(entity)` which defaults to `id == null`. Since id is always non-null, every `save()` call on a new entity triggers `merge()` → an extra SELECT → then INSERT. Under load this doubles DB round-trips.
**Why it happens:** Spring Data JPA's default `isNew` implementation relies on id nullability as the proxy for "never persisted". Application-assigned IDs break this assumption.
**How to avoid:** Implement `Persistable<UUID>` with `@Transient private var _new = true` + `@PostPersist`/`@PostLoad` callbacks that flip `_new = false`.
**Warning signs:** Hibernate logs showing `SELECT` before every `INSERT` for new entities (with `show-sql: true`).

### Pitfall 2: Retaining @GeneratedValue alongside application-assigned id
**What goes wrong:** If `@GeneratedValue(strategy = GenerationType.UUID)` is kept while the field has a non-null initializer, Hibernate may either overwrite the initializer value with its own generated UUID or throw a conflict error depending on provider version.
**Why it happens:** `@GeneratedValue` tells the persistence provider to generate the value — it doesn't know to skip generation when the value is already set.
**How to avoid:** Remove `@GeneratedValue` entirely when switching to application-assigned UUIDs.
**Warning signs:** IDs in the DB differ from IDs available during entity construction, or Hibernate throws at startup about conflicting generation strategies.

### Pitfall 3: Null safety — changing `UUID?` to `UUID` breaks existing null checks
**What goes wrong:** Current entities declare `val id: UUID? = null`. Code that does `id != null && id == other.id` in `equals()` will still compile but the null check is now always true (unnecessary). More critically, anywhere the code does `saved.id!!` (force-unwrap) will need cleanup.
**Why it happens:** Changing from nullable to non-null changes the type contract.
**How to avoid:** Search for all `!!` operator usage on entity id fields and remove the force-unwrap. Update equals/hashCode to remove null guards.
**Warning signs:** Compiler warnings about unnecessary null checks; `SmsVerificationService.sendCode()` has `return saved.id!!` which becomes `return saved.id`.

### Pitfall 4: Flyway migration needed (false alarm)
**What goes wrong:** Developer adds a Flyway migration to change column defaults to a UUID v7 generator function, wasting time.
**Why it happens:** Confusion between DB-side and app-side UUID generation.
**How to avoid:** No Flyway migration needed. PostgreSQL `UUID` columns accept any UUID version. The `DEFAULT gen_random_uuid()` column defaults in V1/V3 migrations are only used when INSERT omits the id column — Hibernate always supplies the id explicitly. No schema change required.
**Warning signs:** Temptation to write `V4__update_uuid_defaults.sql`.

### Pitfall 5: `@Transient _new` flag lost after deserialization / session reload
**What goes wrong:** After `@PostLoad`, `_new` is set to `false`. But if an entity is detached and re-attached in a new session, `@PostLoad` fires again and keeps `_new = false` — correct. However, if a developer bypasses the repository and directly constructs entities from persisted data without going through JPA, `_new` would be `true` incorrectly.
**Why it happens:** `@Transient` fields are not persisted. The JPA lifecycle callbacks (`@PostPersist`, `@PostLoad`) are the source of truth.
**How to avoid:** Always use repositories for CRUD. Never construct entities from DB data outside JPA session.
**Warning signs:** Duplicate key errors when saving entities that were manually reconstructed.

### Pitfall 6: Test code creates entities directly without saving — `_new = true` is fine
**What goes wrong:** Tests that compare entity ids or check persistence state may behave differently.
**Why it happens:** Test creates `User(email = "test@example.com")` — the id is immediately a real UUID v7, not null.
**How to avoid:** Tests already use `userRepository.save(...)` and then access `.id`. Since id is no longer nullable, remove `!!` operators. The id is available before `save()` now (useful for assertion setup).
**Warning signs:** None — this is actually a simplification. Tests no longer need `!!` to access id.

---

## Code Examples

### Adding the dependency (pom.xml)
```xml
<!-- Source: https://github.com/f4b6a3/uuid-creator — version 6.1.1, April 2025 -->
<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>uuid-creator</artifactId>
    <version>6.1.1</version>
</dependency>
```

### BaseEntity (new file — shared/model/BaseEntity.kt)
```kotlin
// Source: Spring Data Persistable<T> + uuid-creator README + Coding Forest blog pattern
package kz.innlab.template.shared.model

import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity : Persistable<UUID> {

    @Id
    val id: UUID = UuidCreator.getTimeOrderedEpoch()

    @Transient
    private var _new: Boolean = true

    override fun getId(): UUID = id

    override fun isNew(): Boolean = _new

    @PostPersist
    @PostLoad
    fun markNotNew() {
        _new = false
    }
}
```

### Updated User entity (after migration)
```kotlin
// Source: adapted from current User.kt
@Entity
@Table(name = "users")
class User(
    @Column(nullable = false)
    var email: String,
) : BaseEntity() {
    // @Id field REMOVED — inherited from BaseEntity as non-null UUID v7

    var name: String? = null
    var picture: String? = null

    @Column(name = "password_hash")
    var passwordHash: String? = null

    @Column(name = "phone", unique = true)
    var phone: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "user_providers",
        joinColumns = [JoinColumn(name = "user_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "provider"])]
    )
    @Column(name = "provider")
    var providers: MutableSet<AuthProvider> = mutableSetOf()

    // ... other fields unchanged ...

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id  // non-null — no ?: 0 guard needed
    }

    override fun hashCode(): Int = id.hashCode()
}
```

### Updated SmsVerificationService (remove !! operator)
```kotlin
// sendCode — BEFORE:
return saved.id!!

// sendCode — AFTER:
return saved.id   // UUID is now non-null
```

### UuidCreator.getTimeOrderedEpoch() — thread safety note
```kotlin
// Source: uuid-creator source — uses ReentrantLock-guarded factory
// Safe to call concurrently from Spring Boot virtual threads (enabled: true)
val id: UUID = UuidCreator.getTimeOrderedEpoch()
// Returns RFC 9562 UUIDv7 — format: [48-bit ms timestamp][12-bit counter][62-bit random]
// Monotonically increases within same millisecond via counter increment
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `UUID.randomUUID()` (v4) | `UuidCreator.getTimeOrderedEpoch()` (v7) | Phase 4 | Sequential B-tree inserts, cursor pagination by id |
| `@GeneratedValue(UUID)` — Hibernate generates | Application-assigned id + `Persistable<UUID>` | Phase 4 | Id available before persist(); no extra SELECT |
| `val id: UUID? = null` (nullable) | `val id: UUID = UuidCreator.getTimeOrderedEpoch()` (non-null) | Phase 4 | Removes null-check boilerplate, simplifies equals/hashCode |
| `saved.id!!` (force-unwrap) | `saved.id` (direct access) | Phase 4 | Cleaner Kotlin code |

**Deprecated/outdated after this phase:**
- `GenerationType.UUID` strategy on entity `@Id` fields — replaced by application-assigned UUID v7
- Null-safe `id?.hashCode() ?: 0` in `hashCode()` — replaced by `id.hashCode()`
- `@GeneratedValue` annotation on entity `@Id` fields — removed

---

## Open Questions

1. **Whether `@MappedSuperclass` interacts with `all-open` Kotlin plugin**
   - What we know: The `all-open` plugin is configured for `Entity`, `MappedSuperclass`, `Embeddable`. `@MappedSuperclass` is explicitly listed.
   - What's clear: `BaseEntity` with `@MappedSuperclass` will be made open automatically — no manual `open` keyword needed.
   - Recommendation: No action needed; existing plugin config covers this.

2. **Whether H2 (test database) handles UUID v7 correctly**
   - What we know: H2 stores UUID columns as `java.util.UUID` internally. UUID v7 is structurally identical to UUID v4 — same 128-bit format, just different bit layout.
   - What's clear: H2 does not care about UUID version; all 128-bit UUIDs work.
   - Recommendation: No test infrastructure change needed.

3. **Whether Flyway migrations need updating**
   - What we know: `gen_random_uuid()` defaults are DB-side fallbacks; Hibernate always provides the id value explicitly on INSERT when using application-assigned IDs.
   - What's clear: No migration needed. The `DEFAULT gen_random_uuid()` clause will never fire since Hibernate always supplies an explicit id.
   - Recommendation: Leave all existing migrations unchanged.

4. **RsaKeyConfig.kt uses `UUID.randomUUID().toString()` for JWK key ID**
   - What we know: This is not a primary key — it's an in-memory RSA key identifier for JWT signing.
   - What's clear: Time-ordering provides no benefit here; randomness is fine.
   - Recommendation: Leave `UUID.randomUUID()` in `RsaKeyConfig.kt` unchanged.

5. **Test files use `UUID.randomUUID()` for Apple sub IDs and test request bodies**
   - What we know: These are test data values, not entity primary keys.
   - What's clear: Out of scope for this phase.
   - Recommendation: Leave test `UUID.randomUUID()` calls unchanged.

---

## Sources

### Primary (HIGH confidence)
- https://github.com/f4b6a3/uuid-creator — Latest version 6.1.1, API for `getTimeOrderedEpoch()`, thread safety via ReentrantLock, RFC 9562 compliance
- https://central.sonatype.com/artifact/com.github.f4b6a3/uuid-creator/5.3.4 — Maven coordinates verified
- https://bugs.openjdk.org/browse/JDK-8357251 — Java 26 built-in UUID v7 (`UUID.ofEpochMillis()`) — confirms NOT available in Java 24; fix version is 26
- https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/Persistable.html — `Persistable<ID>` interface for isNew detection

### Secondary (MEDIUM confidence)
- https://jivimberg.io/blog/2018/11/05/using-uuid-on-spring-data-jpa-entities/ — `Persistable<UUID>` + `@PostPersist`/`@PostLoad` pattern; well-known Spring Data UUID blog post; pattern confirmed by Spring Data docs
- https://dev.to/umangsinha12/postgresql-uuid-performance-benchmarking-random-v4-and-time-based-v7-uuids-n9b — UUID v4 vs v7 B-tree performance benchmark; 500x more page splits with v4
- https://jpa-buddy.com/blog/the-ultimate-guide-on-client/ — Client-generated IDs guide; confirms `@GeneratedValue` should be removed for application-assigned IDs

### Tertiary (LOW confidence)
- Multiple Medium articles on UUID v7 Spring Boot patterns — general consensus matches HIGH-confidence sources; not independently verified

---

## Metadata

**Confidence breakdown:**
- Standard stack (uuid-creator 6.1.1): HIGH — latest version confirmed on Maven Central, API verified from GitHub source
- Architecture (BaseEntity + Persistable pattern): HIGH — confirmed against Spring Data Persistable docs and established blog pattern
- Pitfalls (merge vs persist, @GeneratedValue removal): HIGH — verified against Spring Data source behavior and JPA spec
- Java 26 built-in UUID v7: HIGH — JDK issue confirmed as Fix Version 26, not available in Java 24
- Flyway no-migration needed: HIGH — UUID column type is version-agnostic, Hibernate always provides explicit id on INSERT

**Research date:** 2026-03-02
**Valid until:** 2026-06-01 (uuid-creator is stable; Spring Data Persistable API is stable)
