# Phase 2: Account Linking Logic - Research

**Researched:** 2026-03-02
**Domain:** JPA @ElementCollection (Set + Map with Enum keys), Spring Data JPA repository methods, Hibernate schema migration, Spring Security UserDetailsService adaptation
**Confidence:** HIGH

## Summary

Phase 2 replaces the current single-provider model (`provider` + `providerId` fields on User) with a multi-provider model (`providers: Set<AuthProvider>` + `providerIds: Map<AuthProvider, String>`). Email becomes the global unique key — one User row per email, regardless of how many social providers the user has linked. The core change is in the User entity, UserRepository, UserService, LocalAuthService, and LocalUserDetailsService. No new external dependencies are required.

The critical insight is that the existing codebase already has `email` on every user row (non-null) and has `passwordHash` and `phone` as nullable fields, making the data model straightforward to extend. The schema migration (V2) drops the old `UNIQUE(provider, provider_id)` constraint, adds a `UNIQUE(email)` constraint on the users table (non-phone users), creates two new collection tables (`user_providers`, `user_provider_ids`), and removes the `provider` and `provider_id` columns from `users`.

Phone users are a special case: they have no email (stored as `""` today), so the email uniqueness constraint must NOT apply to phone-only users. Recommended approach: keep the current empty-string convention for phone users and enforce email uniqueness at the application layer (not DB unique constraint on users.email), or use a partial index. Application-layer enforcement is simpler and already follows the project's existing guard patterns.

**Primary recommendation:** Migrate User entity from single `(provider, providerId)` to `providers: MutableSet<AuthProvider>` + `providerIds: MutableMap<AuthProvider, String>`, change UserRepository to `findByEmail()`, and update all three service layers (UserService, LocalAuthService, LocalUserDetailsService) to implement the linking rules. One Flyway V2 migration handles the schema transformation.

## Standard Stack

### Core (all already on classpath — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | BOM-managed (Boot 4.0.3) | `@ElementCollection`, `@CollectionTable`, `@MapKeyEnumerated` | Already in project |
| Hibernate ORM | BOM-managed | Maps `Set<Enum>` and `Map<Enum, String>` to collection tables | Already in project |
| Flyway | BOM-managed | V2 migration to restructure users table | Already in project |
| H2 (test) | BOM-managed | Test profile DDL create-drop — collection tables auto-created | Already in project |

No new Maven dependencies needed.

### Entity Model Target State

```
users table:
  id, email (UNIQUE — with partial index or app-layer guard for phone users),
  name, picture, password_hash, phone (UNIQUE), roles (user_roles table),
  created_at, updated_at
  — REMOVED: provider, provider_id columns
  — REMOVED: UNIQUE(provider, provider_id) constraint

user_providers table:
  user_id FK -> users.id
  provider VARCHAR(50) NOT NULL
  UNIQUE(user_id, provider)

user_provider_ids table:
  user_id FK -> users.id
  provider VARCHAR(50) NOT NULL  [map key]
  provider_id VARCHAR(255) NOT NULL  [map value]
  UNIQUE(user_id, provider)
```

## Architecture Patterns

### Recommended Project Structure

No new files/directories needed. Changes are within existing files:

```
src/main/kotlin/kz/innlab/template/
├── user/
│   ├── model/
│   │   └── User.kt                    # Replace provider/providerId with providers/providerIds
│   ├── repository/
│   │   └── UserRepository.kt          # Replace findByProviderAndProviderId → findByEmail
│   └── service/
│       └── UserService.kt             # Rewrite findOrCreateGoogleUser, findOrCreateAppleUser,
│                                      # findOrCreatePhoneUser to use linking logic
├── authentication/
│   └── service/
│       ├── LocalAuthService.kt        # Rewrite register() and login() for linking rules
│       └── LocalUserDetailsService.kt # Change lookup from (LOCAL, email) to findByEmail
│                                      # + check LOCAL in providers + check passwordHash != null
└── resources/
    └── db/migration/
        └── V2__add_account_linking.sql  # Drop old constraint, add providers/providerIds tables
```

### Pattern 1: @ElementCollection Set<AuthProvider> for providers

**What:** Maps a Kotlin `MutableSet<AuthProvider>` to a separate collection table `user_providers`.
**When to use:** When a user can have multiple enum values (e.g., [LOCAL, GOOGLE]).

```kotlin
// Source: verified via JPA spec + softwarecave.org/@ElementCollection docs
@ElementCollection(fetch = FetchType.EAGER)
@Enumerated(EnumType.STRING)
@CollectionTable(
    name = "user_providers",
    joinColumns = [JoinColumn(name = "user_id")],
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "provider"])]
)
@Column(name = "provider")
var providers: MutableSet<AuthProvider> = mutableSetOf()
```

### Pattern 2: @ElementCollection Map<AuthProvider, String> for providerIds

**What:** Maps a Kotlin `MutableMap<AuthProvider, String>` to a separate collection table `user_provider_ids`. Key = enum (stored as STRING), value = external provider's user ID.
**When to use:** When each enum key maps to a string value (e.g., GOOGLE -> "google-sub-123").

```kotlin
// Source: verified via softwarecave.org + thorben-janssen.com ElementCollection docs
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(
    name = "user_provider_ids",
    joinColumns = [JoinColumn(name = "user_id")],
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "provider"])]
)
@MapKeyEnumerated(EnumType.STRING)
@MapKeyColumn(name = "provider")
@Column(name = "provider_id")
var providerIds: MutableMap<AuthProvider, String> = mutableMapOf()
```

**Note:** LOCAL provider has no external ID — it does NOT get an entry in `providerIds`. Only GOOGLE and APPLE entries are stored here.

### Pattern 3: Repository lookup by email (replaces provider+providerId lookup)

**What:** Simple Spring Data JPA derived query on the `email` column.

```kotlin
// Source: Spring Data JPA docs — derived query method
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    // findByProviderAndProviderId REMOVED — no longer needed
}
```

**Note:** LocalUserDetailsService and all three findOrCreate* methods in UserService switch to `findByEmail`.

### Pattern 4: Account linking in UserService.findOrCreateGoogleUser

**What:** After email lookup, either link GOOGLE to existing account or create new.

```kotlin
@Transactional
fun findOrCreateGoogleUser(providerId: String, email: String, name: String?, picture: String?): User {
    val existing = userRepository.findByEmail(email)
    if (existing != null) {
        // Link GOOGLE to existing account (idempotent if already linked)
        existing.providers.add(AuthProvider.GOOGLE)
        existing.providerIds[AuthProvider.GOOGLE] = providerId
        // Update profile fields if provided
        if (name != null && existing.name == null) existing.name = name
        if (picture != null && existing.picture == null) existing.picture = picture
        return userRepository.save(existing)
    }
    // New user
    return userRepository.save(
        User(email = email).also {
            it.providers.add(AuthProvider.GOOGLE)
            it.providerIds[AuthProvider.GOOGLE] = providerId
            it.name = name
            it.picture = picture
        }
    )
}
```

### Pattern 5: Account linking in LocalAuthService.register

**What:** Email lookup first; linking rules applied before creating new user.

```kotlin
@Transactional
fun register(email: String, rawPassword: String, name: String?): AuthResponse {
    val existing = userRepository.findByEmail(email)
    if (existing != null) {
        if (existing.passwordHash != null) {
            // Password already set — account exists, redirect to login
            throw IllegalStateException("Account already exists. Use login instead.")
        }
        // Account exists (e.g., Google-only user) — add LOCAL provider + set password
        existing.providers.add(AuthProvider.LOCAL)
        existing.passwordHash = passwordEncoder.encode(rawPassword)
        if (name != null && existing.name == null) existing.name = name
        val savedUser = userRepository.save(existing)
        val accessToken = tokenService.generateAccessToken(savedUser.id!!, savedUser.roles)
        val refreshToken = refreshTokenService.createToken(savedUser)
        return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
    }
    // Truly new user
    val user = User(email = email).also {
        it.providers.add(AuthProvider.LOCAL)
        it.passwordHash = passwordEncoder.encode(rawPassword)
        it.name = name
    }
    val savedUser = userRepository.save(user)
    val accessToken = tokenService.generateAccessToken(savedUser.id!!, savedUser.roles)
    val refreshToken = refreshTokenService.createToken(savedUser)
    return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
}
```

### Pattern 6: LocalUserDetailsService — check LOCAL in providers

**What:** After finding user by email, verify LOCAL is in the `providers` set.

```kotlin
override fun loadUserByUsername(email: String): UserDetails {
    val user = userRepository.findByEmail(email)
        ?: throw UsernameNotFoundException("No account for email: $email")

    if (AuthProvider.LOCAL !in user.providers) {
        // User exists but registered via Google/Apple only — no local credentials
        throw UsernameNotFoundException("No local account for email: $email")
    }
    if (user.passwordHash == null) {
        throw BadCredentialsException("No password set")
    }

    return org.springframework.security.core.userdetails.User
        .withUsername(email)
        .password(user.passwordHash!!)
        .roles(*user.roles.map { it.name }.toTypedArray())
        .build()
}
```

### Pattern 7: User entity constructor change

The existing `User` constructor takes `(email, provider, providerId)`. After this phase, `provider` and `providerId` are removed from the entity. The new constructor takes only `email`:

```kotlin
@Entity
@Table(
    name = "users",
    uniqueConstraints = [] // Remove old UNIQUE(provider, provider_id)
)
class User(
    @Column(nullable = false)
    var email: String,
) {
    // ... providers, providerIds as ElementCollections
}
```

**CRITICAL:** All existing test code that creates `User(email, provider, providerId)` must be updated to use the new constructor + set providers/providerIds explicitly.

### Pattern 8: Flyway V2 migration strategy

**What:** V2 drops old columns and constraints, adds new collection tables.

```sql
-- V2__add_account_linking.sql
-- Source: Flyway standard DDL migration pattern

-- Step 1: Create new collection tables BEFORE dropping old columns
CREATE TABLE user_providers (
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    CONSTRAINT uq_user_providers UNIQUE (user_id, provider)
);

CREATE TABLE user_provider_ids (
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_user_provider_ids UNIQUE (user_id, provider)
);

-- Step 2: Migrate existing data from old columns to new tables
INSERT INTO user_providers (user_id, provider)
SELECT id, provider FROM users;

INSERT INTO user_provider_ids (user_id, provider, provider_id)
SELECT id, provider, provider_id FROM users
WHERE provider != 'LOCAL';  -- LOCAL email users have no external provider_id

-- Step 3: Add email uniqueness (partial — excludes phone-only users with empty email)
-- Using application-layer enforcement instead of DB constraint (see Architecture Patterns note)

-- Step 4: Drop old columns and constraint
ALTER TABLE users DROP CONSTRAINT uq_provider_provider_id;
ALTER TABLE users DROP COLUMN provider;
ALTER TABLE users DROP COLUMN provider_id;
```

**Note on email uniqueness:** A PostgreSQL partial unique index `CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email != ''` ensures uniqueness for real email addresses while allowing multiple phone-only users with `email = ''`. This is cleaner than application-layer enforcement alone.

### Anti-Patterns to Avoid

- **Keeping findByProviderAndProviderId:** Remove it entirely — it's the root of the single-provider model. Any leftover call will fail to compile.
- **Storing LOCAL in providerIds:** LOCAL users have no external ID — don't add a `providerIds[LOCAL]` entry. Only GOOGLE and APPLE get entries.
- **Overwriting existing name/picture on re-link:** When linking a new provider to an existing account, only set name/picture if currently null (don't overwrite user's data).
- **Creating new user on duplicate Google sign-in:** After switching to email-based lookup, the old `findByProviderAndProviderId(GOOGLE, sub)` check is removed. Use `findByEmail` — if a user switches Google accounts but uses the same email, they'll link correctly.
- **Forgetting to update test User constructors:** Every `User(email, provider, providerId)` call in test code will be a compile error after the constructor change. Update all test setup code.
- **LAZY fetch on providers Set:** The `providers` set is checked in business logic (LocalUserDetailsService, LocalAuthService) after the transaction is gone in some paths. Keep `FetchType.EAGER` on `providers` to avoid `LazyInitializationException`. The set is small (max 3 elements: LOCAL, GOOGLE, APPLE), so EAGER is safe here.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Storing enum set in DB | Custom join table logic with manual SQL | `@ElementCollection` + `@CollectionTable` | JPA handles all CRUD, H2 in tests auto-creates tables |
| Storing enum->string map in DB | Custom map serialization | `@ElementCollection` + `@MapKeyEnumerated` | JPA handles map CRUD, type-safe enum key |
| Email-based lookup | Custom query with JPQL | Spring Data derived query `findByEmail()` | Zero boilerplate, Spring Data generates SQL |
| MEMBER OF query on providers | Custom SQL EXISTS subquery | JPQL `MEMBER OF` or JOIN — but not needed here since we use `findByEmail()` then check `providers` in Kotlin | Simpler; lookup-then-check pattern is already used throughout the codebase |

**Key insight:** The entire account linking logic is in the application layer (UserService, LocalAuthService). There's no need for complex DB queries — find by email, then inspect/mutate the providers set and providerIds map in Kotlin. JPA @ElementCollection handles persistence automatically.

## Common Pitfalls

### Pitfall 1: LazyInitializationException on providers Set
**What goes wrong:** `LocalUserDetailsService.loadUserByUsername()` checks `providers` after the Hibernate session closes (open-in-view=false). If `providers` is LAZY, you get `LazyInitializationException`.
**Why it happens:** `open-in-view: false` is already set in this project (correct production setting). LAZY collections are only accessible within a transaction.
**How to avoid:** Set `FetchType.EAGER` on both `providers` and `providerIds` collections. They are tiny sets (max 3 items) so N+1 concerns don't apply.
**Warning signs:** `org.hibernate.LazyInitializationException: failed to lazily initialize a collection` in the logs.

### Pitfall 2: Old User constructor used in tests
**What goes wrong:** After removing `provider` and `providerId` from the User constructor, all test code that calls `User(email, provider, providerId)` fails to compile.
**Why it happens:** The constructor signature changes.
**How to avoid:** Search for all `User(` calls in test files and update them. The new pattern is:
```kotlin
User(email = "test@example.com").also {
    it.providers.add(AuthProvider.GOOGLE)
    it.providerIds[AuthProvider.GOOGLE] = "google-sub-123"
}
```
**Warning signs:** Compile errors: `None of the following candidates is applicable`.

### Pitfall 3: findByProviderAndProviderId references left in non-test code
**What goes wrong:** LocalAuthService.login() and LocalUserDetailsService still reference `findByProviderAndProviderId` after it's removed from the repository.
**Why it happens:** Multiple services use the repository method — easy to miss one.
**How to avoid:** Remove `findByProviderAndProviderId` from UserRepository first (compile-driven refactoring). Fix all compile errors in sequence.
**Warning signs:** Compile errors in LocalAuthService.login() — `findByProviderAndProviderId` unresolved.

### Pitfall 4: Phone users uniqueness conflict with email partial index
**What goes wrong:** Multiple phone users all have `email = ""`. A naive `UNIQUE(email)` constraint on the users table would prevent the second phone user from being created.
**Why it happens:** Phone users use empty string for the NOT NULL email column.
**How to avoid:** Use PostgreSQL partial unique index: `CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email != ''`. Or (simpler for this template) enforce uniqueness at application layer only — `findByEmail("")` would return any phone user, but LocalAuthService and LocalUserDetailsService never call `findByEmail("")`.
**Warning signs:** `duplicate key value violates unique constraint "users_email_key"` when creating second phone user.

### Pitfall 5: H2 compatibility with partial unique index
**What goes wrong:** The Flyway V2 migration contains a PostgreSQL-specific `WHERE email != ''` partial index that H2 does not support.
**Why it happens:** Tests use H2 with `ddl-auto: create-drop` and `flyway.enabled: false` — but if someone enables Flyway in tests, H2 will fail on the partial index syntax.
**How to avoid:** `spring.flyway.enabled: false` is already set in test profile (Phase 01-01 v2 decision). Keep it disabled. The email uniqueness is enforced at application layer for tests. Alternatively, skip the partial index entirely and rely on application-layer enforcement only (simpler).

### Pitfall 6: Apple auth — email absent on subsequent logins
**What goes wrong:** Apple only sends email on first sign-in. On subsequent sign-ins, `jwt.getClaimAsString("email")` returns null. After this phase, `findOrCreateAppleUser` uses `findByEmail` — if email is null, we can't look up the user.
**Why it happens:** Apple's documented behavior — email is a one-time disclosure.
**How to avoid:** For Apple subsequent sign-ins, fall back to looking up by `providerIds` map entry (query by APPLE provider_id). This requires a new repository query OR storing the APPLE sub in the `providerIds` collection and querying it.

**Concrete solution:** Add a second repository method specifically for Apple re-login:
```kotlin
// Spring Data JPA JPQL — find user where APPLE maps to this sub
@Query("SELECT u FROM User u JOIN u.providerIds pid WHERE KEY(pid) = 'APPLE' AND VALUE(pid) = :sub")
fun findByAppleProviderId(sub: String): User?
```
Or use a custom native query. This is the one complexity that doesn't simplify under email-only lookup.

**Alternatively:** Restructure `findOrCreateAppleUser` to always try `providerIds` lookup first (by APPLE sub), then email lookup — covering both first-login and subsequent-login cases.

### Pitfall 7: @ElementCollection with collection mutation outside transaction
**What goes wrong:** Adding to `user.providers` or `user.providerIds` outside a `@Transactional` method does not persist — the change is lost.
**Why it happens:** JPA dirty-checking only works within a transaction.
**How to avoid:** All methods that mutate `providers` or `providerIds` must be within `@Transactional`. Existing `@Transactional` annotations on UserService methods cover this. LocalAuthService methods also have `@Transactional` on `register()`. Confirm `login()` does NOT need mutation (it doesn't — login is read-only).

## Code Examples

### Full User entity after change

```kotlin
// User.kt — after Phase 2 changes
@Entity
@Table(name = "users")
class User(
    @Column(nullable = false)
    var email: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_provider_ids",
        joinColumns = [JoinColumn(name = "user_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "provider"])]
    )
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "provider")
    @Column(name = "provider_id")
    var providerIds: MutableMap<AuthProvider, String> = mutableMapOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role")
    var roles: MutableSet<Role> = mutableSetOf(Role.USER)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
```

### UserRepository after change

```kotlin
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    // For Apple re-login (email absent on subsequent sign-ins)
    @Query("SELECT u FROM User u JOIN u.providerIds pid WHERE KEY(pid) = 'APPLE' AND VALUE(pid) = :sub")
    fun findByAppleProviderId(@Param("sub") sub: String): User?
}
```

### AppleOAuth2Service updated for email-absent case

```kotlin
@Transactional
fun authenticate(request: AppleAuthRequest): AuthResponse {
    val jwt = try { appleJwtDecoder.decode(request.idToken) }
    catch (ex: Exception) { throw BadCredentialsException("Invalid Apple ID token: ${ex.message}", ex) }

    val sub: String = jwt.subject
        ?: throw BadCredentialsException("Apple identity token missing sub claim")
    val email: String? = jwt.getClaimAsString("email")
    val fullName: String? = /* ... same as before ... */

    val user = userService.findOrCreateAppleUser(providerId = sub, email = email, name = fullName)

    val accessToken = tokenService.generateAccessToken(user.id!!, user.roles)
    val refreshToken = refreshTokenService.createToken(user)
    return AuthResponse(accessToken = accessToken, refreshToken = refreshToken)
}
```

### UserService.findOrCreateAppleUser updated

```kotlin
@Transactional
fun findOrCreateAppleUser(providerId: String, email: String?, name: String?): User {
    // Step 1: Try to find by Apple sub (covers returning users where email is absent)
    val byAppleSub = userRepository.findByAppleProviderId(providerId)
    if (byAppleSub != null) {
        return byAppleSub  // Returning Apple user — no changes needed
    }

    // Step 2: Email must be present for new users (first sign-in)
    val resolvedEmail = email
        ?: throw BadCredentialsException(
            "Email not present in Apple identity token on first sign-in"
        )

    // Step 3: Link or create
    val existing = userRepository.findByEmail(resolvedEmail)
    if (existing != null) {
        existing.providers.add(AuthProvider.APPLE)
        existing.providerIds[AuthProvider.APPLE] = providerId
        if (name != null && existing.name == null) existing.name = name
        return userRepository.save(existing)
    }

    // New user
    return userRepository.save(
        User(email = resolvedEmail).also {
            it.providers.add(AuthProvider.APPLE)
            it.providerIds[AuthProvider.APPLE] = providerId
            it.name = name
        }
    )
}
```

### V2 Flyway migration

```sql
-- V2__add_account_linking.sql

-- Create collection tables for multi-provider support
CREATE TABLE user_providers (
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    CONSTRAINT uq_user_providers UNIQUE (user_id, provider)
);

CREATE TABLE user_provider_ids (
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_user_provider_ids UNIQUE (user_id, provider)
);

-- Migrate existing data (needed for any existing deployments)
INSERT INTO user_providers (user_id, provider)
SELECT id, provider FROM users;

-- Migrate providerIds — LOCAL email users have no external ID (skip them)
-- Phone users (LOCAL with empty email) also skipped — no external ID
INSERT INTO user_provider_ids (user_id, provider, provider_id)
SELECT id, provider, provider_id FROM users
WHERE provider IN ('GOOGLE', 'APPLE');

-- Add partial unique index for email uniqueness (excludes phone users with empty email)
CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email != '';

-- Drop old single-provider columns and constraint
ALTER TABLE users DROP CONSTRAINT uq_provider_provider_id;
ALTER TABLE users DROP COLUMN provider;
ALTER TABLE users DROP COLUMN provider_id;
```

### Test: updated User construction pattern

```kotlin
// OLD (compile error after change):
// User(email = "test@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-123")

// NEW — required after Phase 2:
User(email = "test@example.com").also {
    it.providers.add(AuthProvider.GOOGLE)
    it.providerIds[AuthProvider.GOOGLE] = "sub-123"
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single `(provider, providerId)` key per user row | `providers: Set<AuthProvider>` + `providerIds: Map<AuthProvider, String>` via @ElementCollection | Phase 2 | One user = one email = N providers |
| `findByProviderAndProviderId(provider, id)` | `findByEmail(email)` + secondary `findByAppleProviderId(sub)` | Phase 2 | Email is the universal identity key |
| UNIQUE(provider, provider_id) DB constraint | Partial unique index on email + collection table unique constraints | Phase 2 | Allows multi-provider per user |

**Deprecated/outdated after this phase:**
- `UserRepository.findByProviderAndProviderId()` — remove entirely
- `User.provider` field — remove
- `User.providerId` field — remove
- `UNIQUE(provider, provider_id)` constraint on `users` table — replaced by collection table uniqueness

## Open Questions

1. **Email uniqueness for phone users**
   - What we know: Phone users currently use `email = ""` (empty string, NOT NULL constraint). A `UNIQUE(email)` DB constraint would break multi-phone-user scenarios.
   - What's unclear: Should email be made nullable for phone users? The existing TODO in UserService says "consider making email nullable for phone-only users"
   - Recommendation: Use PostgreSQL partial unique index `WHERE email != ''` in V2 migration. This cleanly enforces uniqueness for real emails while allowing multiple phone users with empty-string email. Keep email NOT NULL for now (phone users already have empty string).

2. **Apple sub lookup query syntax**
   - What we know: JPQL `KEY()` and `VALUE()` functions work with `@ElementCollection` Maps in Hibernate (JPQL spec).
   - What's unclear: Exact JPQL syntax for `KEY(pid) = 'APPLE'` — the KEY() function compares against a string when the key is an enum stored as STRING.
   - Recommendation: Write and test the query in isolation during implementation. If JPQL Map query proves difficult, an alternative is a native PostgreSQL query on `user_provider_ids` table directly.

3. **Google sub lookup**
   - What we know: Google tokens always include email. So `findByEmail()` is sufficient for both new and returning Google users — the providerId (sub) is an add-on stored in `providerIds`.
   - What's unclear: What if Google changes the email associated with a sub? (Extremely rare, Google doesn't do this.)
   - Recommendation: Keep current approach — Google always provides email, so `findByEmail()` is canonical. Update `providerIds[GOOGLE] = sub` on every login (idempotent).

## Validation Architecture

> `workflow.nyquist_validation` is not present in `.planning/config.json` — skipping this section per instructions.

## Sources

### Primary (HIGH confidence)
- JPA spec + Hibernate ORM docs — `@ElementCollection`, `@CollectionTable`, `@MapKeyEnumerated` annotation behavior
- softwarecave.org — verified `@MapKeyEnumerated(STRING)` + `@MapKeyColumn` + `@Column` pattern for `Map<Enum, String>`
- thorben-janssen.com — verified JPQL `MEMBER OF` and `JOIN` patterns for @ElementCollection queries; also `KEY()` / `VALUE()` for Map collections
- Project codebase (direct inspection) — current User entity, UserRepository, UserService, LocalAuthService, LocalUserDetailsService, AuthController, existing test patterns

### Secondary (MEDIUM confidence)
- WebSearch: JPA `@MapKeyEnumerated` Hibernate pattern — multiple sources confirm `@MapKeyEnumerated(EnumType.STRING)` + `@MapKeyColumn(name = "...")` + `@Column(name = "...")` is the standard pattern for `Map<Enum, String>`
- WebSearch: JPQL `KEY()` / `VALUE()` functions for ElementCollection Map queries — supported in Hibernate JPQL spec

### Tertiary (LOW confidence)
- JPQL syntax `KEY(pid) = 'APPLE'` for enum key comparison — not directly verified against a running instance; alternative native query exists as fallback

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all JPA/Hibernate patterns are standard and well-documented
- Architecture: HIGH — direct codebase inspection; all affected files identified; linking rules transcribed from phase description
- JPA ElementCollection patterns: HIGH — verified via multiple official/semi-official sources
- Apple sub JPQL query: MEDIUM — pattern is known but exact syntax needs validation during implementation
- Pitfalls: HIGH — LazyInitializationException pattern, constructor breaks, and phone-user email uniqueness all observed from the existing codebase decisions log

**Research date:** 2026-03-02
**Valid until:** 2026-04-02 (stable JPA spec — 30 days)
