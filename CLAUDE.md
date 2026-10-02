# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 4.1.1 auth template in **Kotlin 2.3.21** (Java 25) with JWT authentication, multi-provider login (Google, Apple, email+password, email OTP, phone OTP), push notifications (Firebase), and email (SMTP/IMAP). Uses PostgreSQL 18 with Flyway migrations and UUID v7 primary keys.

## Build & Run Commands

```bash
# Start PostgreSQL (required for local dev)
docker compose up -d

# Run application (dev profile must be set explicitly — no default profile for safety)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# Run all tests (H2 in-memory, no Docker needed)
./mvnw test

# Run a single test class
./mvnw test -pl . -Dtest=AccountManagementIntegrationTest

# Run a single test method
./mvnw test -pl . -Dtest=AccountManagementIntegrationTest#testChangePasswordSuccess

# Compile only
./mvnw clean compile
```

Server runs on port **7070**. Swagger UI at `/swagger-ui.html`.

## Architecture

The codebase is organized into three domain modules under `src/main/kotlin/kz/innlab/starter/`:

- **authentication/** - All auth flows (local, Google, Apple, phone OTP), token management (JWT + refresh token rotation), account management (password/email/phone change), verification codes
- **notification/** - Firebase Cloud Messaging push notifications and email sending/receiving (SMTP/IMAP)
- **user/** - User entity, profile endpoint, roles, providers

Each module follows the same internal structure: `controller/`, `dto/`, `model/`, `repository/`, `service/`.

Module dependency direction is one-way: `authentication` → `user`. The `user` module must never import
from `authentication` — it talks back through the `RefreshTokenRevoker` port instead.

Wiring:
- **AuthAutoConfiguration.kt** + **autoconfigure/** - `AuthCoreAutoConfiguration`, `AuthProvidersAutoConfiguration`, `NotificationAutoConfiguration`, `UserAutoConfiguration` declare the module beans as `@Bean` methods. A new `@Service`/`@Component` is not registered until a `@Bean` method here declares it.

Cross-cutting concerns:
- **config/** - Security config, RSA key config, CORS, async executor, provider configs (Google, Apple, Firebase, email). All config properties are under the `app.*` namespace in `application.yaml`.
- **shared/** - `BaseEntity` (UUID v7 + `Persistable` for JPA new-entity detection), error DTO/exceptions, `AfterCommitRunner`, phone normalization

### Key Design Decisions

- **Email is the universal identity key** for account linking. One email = one user across all providers. Phone-only users have `email = ""` with a partial unique index.
- **Refresh token rotation** with reuse detection: used tokens within 10s grace window return 409; reuse after grace revokes all user tokens. The revocation runs in its own transaction (`RefreshTokenFamilyRevoker`) — the detection path ends by throwing, and an in-transaction delete would be rolled back with it.
- **Credential-checking endpoints are attempt-limited**: `/auth/local/login` (per email), `/users/me/change-password` (per user) and every one-time-code check in `VerificationCodeService` (per identifier + purpose, across codes — the per-code counter alone resets with each new code) go through the `RateLimiter` bean and answer 429 with `Retry-After` once the limit is hit; a success clears the counter. The default implementation is in-memory, so limits are per instance — declare a `RateLimiter` bean backed by a shared store for a cluster. Tunable via `app.auth.rate-limit.*`.
- **Provider linking lives on `User.linkProvider(provider, providerId)`**, not inline at each call site — twelve places used to add the provider by hand and some forgot the provider id.
- **Bot copy is a replaceable `TelegramBotMessages` bean**, so the starter does not message a consumer's users under someone else's brand.
- **Entities carry `@Version` and inherit identity semantics from `BaseEntity`**: optimistic locking guards the read-modify-write flows that update a user from several places, and `equals`/`hashCode` are id-based in one place rather than copy-pasted into some entities and missing from others.
- **Auto-configuration registers beans explicitly, never by package scan**: `AuthAutoConfiguration` `@Import`s the feature configurations under `autoconfigure/` plus the infrastructure ones under `config/`. Every bean uses `@ConditionalOnMissingBean`, so a consumer application can replace any of them. Bean names equal the decapitalized class name and are load-bearing (`@Qualifier`, `@Async`, `@ConditionalOnMissingBean(name = …)`) — `AutoConfigurationContractTest` pins them.
- **The auto-configuration contract is tested from the consumer's position**, in `src/test/kotlin/kz/innlab/consumer/`: contexts are built from `AuthAutoConfiguration` alone (`consumerContextRunner()`) or from `ConsumerApplication`, never from `AuthStarterApplication` — whose `@SpringBootApplication` scan of `kz.innlab.starter` backfills any bean the auto-configuration forgets and hid a dead cookie mode through a green 174-test suite in 0.1.0. `AutoConfigurationCoverageTest` audits every stereotype-annotated class against a real consumer context; a class that is intentionally not registered goes in its `intentionallyUnregistered` map with a reason. A bean injected as `ObjectProvider`/`Optional` needs an explicit presence test (`ConditionalBeanContractTest`) — its absence is silent, not fatal.
- **`@Service`/`@Component` annotations stay on the classes even though nothing scans for them**: the Kotlin `spring` compiler plugin uses them to make classes open. Removing one from a class whose `@Transactional` sits on its methods would make it final, and proxying would fail at runtime.
- **Settings are typed `@ConfigurationProperties`, not scattered `@Value`**: `AuthTokenProperties`, `VerificationProperties`, `TelegramAuthProperties`, `DeviceTokenProperties` join the existing `MailProperties`/`CorsProperties`/`AuthCookieProperties`/`AuthSecurityProperties`. All are `@Validated`, so a nonsensical value (negative lifetime, zero attempt limit) fails at startup rather than at first use. Nested property objects need `@field:Valid` or their constraints are skipped silently.
- **One-time codes live in one place**: `VerificationCodeService` owns codes for every channel (email, phone OTP, Telegram) in the `verification_codes` table, keyed by identifier + purpose. Attempt counting runs in its own transaction (`VerificationAttemptRecorder`) so the brute-force limit survives the rollback that a failed verification triggers.
- **Token issuance goes through `AuthTokenIssuer`** — every provider (local, Google, Apple, phone, Telegram) delegates there, so JWT contents change in one place.
- **External I/O never runs inside a transaction.** SMTP, FCM, Telegram and SMS calls are deferred with `AfterCommitRunner`; token verification (Google certs, Apple JWKS) happens before the transactional work starts.
- **`@Async` uses the bounded `authStarterTaskExecutor`** (`AsyncConfig`), not Spring's unbounded default.
- **SMS/Email services are interfaces** with console-logging defaults. Real providers plug in via `@Bean` (the default uses `@ConditionalOnMissingBean`). The console fallbacks never log the code itself.
- **Virtual threads** are enabled (`spring.threads.virtual.enabled: true`).
- **Dev profile** uses in-memory RSA keys, `dev-code` OTP overrides, and console logging for SMS/email/push.
- **Kotlin compiler plugins**: `spring` (open classes), `jpa` (no-arg constructors), `all-open` for JPA entities.

### Security Posture

- **No default Spring profile.** `SPRING_PROFILES_ACTIVE` must be set explicitly; a `:dev` fallback would silently enable the fixed `123456` OTP override in production.
- **`ProductionSafetyConfig` refuses to start under a production profile** (`prod`/`production` by default, `app.security.production-profiles` to change; `ProductionProfiles` is the one check, also used by `RsaKeyConfig`) when a `dev-code` override is set, Telegram is enabled without a webhook secret, or console SMS/mail fallbacks are serving real traffic. The fallback checks are waived **per channel** (`app.security.console-fallbacks.allow-sms|allow-email|allow-mail`) because most applications use some channels and not others; `app.security.allow-console-fallbacks=true` waives all three and stays only for compatibility. Which channels an application uses is not derivable from config — `change-phone` sends an OTP whether or not the phone provider is enabled — so the waiver is the operator's statement, not a guess.
- **Authorization is fail-secure**: `anyRequest` is `authenticated`. Consumers open extra paths via `app.auth.security.public-paths`. Only `/actuator/health*` is public by default.
- **Swagger/OpenAPI is not public** unless `app.auth.security.public-api-docs=true` (set in the dev profile). Admin rules are registered before every `permitAll`, so a broad `public-paths` entry cannot open them.
- **`AuthExceptionHandler` is scoped to `kz.innlab.starter`** so it never echoes a consumer's exception messages; the JSON 404 for unmapped paths lives in the separate, lowest-precedence `NotFoundExceptionHandler`.
- **Identity changes need re-authentication**: change-email/phone requests require `currentPassword` or a `REAUTH` code (`/users/me/reauth/request`); accounts with no password, email or phone may add one only within 5 minutes of login, judged by the access token's `auth_time` claim, which `refresh_tokens.authenticated_at` carries across rotations.
- **Admin authorization is double**: URL rules in `SecurityConfig` and `@PreAuthorize("hasRole('ADMIN')")` on the handlers (`MethodSecurityConfig`), so admin endpoints stay closed if the starter's chain is disabled or replaced. Account-handover admin actions also need a recent login (`auth_time`, `app.auth.security.admin-fresh-login-seconds`).
- **`src/main/resources/application*.yaml` are for this repo's runnable app only** and are excluded from the jar (`maven-jar-plugin`); CI fails if they ship. Starter defaults belong in the `@ConfigurationProperties` classes.
- **Admin-only endpoints**: `/api/v1/admin/**`, `POST /api/v1/notifications/send/topic` (broadcast), `/api/v1/mail/inbox/**` (shared org mailbox), `/api/v1/mail/send/**` (outbound mail from the app domain), `/actuator/**` except health.
- **Ownership checks**: push send/multicast and topic subscribe only accept FCM tokens registered to the caller. An FCM token is unique; registering one another user holds takes it over.
- **Rate limits**: Telegram resend has a server-side cooldown plus a per-session cap; mail send has a per-user hourly quota and attachment caps (`app.mail.limits.*`).
- **Code sends are limited per client address and per purpose** (`VerificationCodeService.checkSendAllowed`, against SMS toll fraud). Flows that pretend to send a code to hide whether an account exists must call it too. The client address is `request.remoteAddr`: behind a proxy, `server.forward-headers-strategy` must be set.
- **`InMemoryRateLimiter` evicts lowest-count entries at capacity** rather than refusing new keys, which let a flood of fresh keys lock everyone else out.
- **`X-Forwarded-For` is ignored** unless `app.auth.telegram.trust-forwarded-headers=true` (set only behind a trusted proxy).

## Test Setup

Tests use H2 in-memory DB with Flyway disabled and `create-drop` DDL. External dependencies are mocked with `@MockitoBean` (GoogleIdTokenVerifier, AppleJwtDecoder, SmsService, EmailService). Email integration tests use GreenMail (in-process SMTP/IMAP) and Awaitility for async assertions.

## Database Migrations

Flyway migrations in `src/main/resources/db/migration-auth/` (V1 through V16), applied to the `auth` schema by the starter's own Flyway bean (`AuthFlywayConfig`). Dev profile has `clean-on-validation-error: true`; prod uses strict validation. **Never edit a migration that shipped in a release** — every database that applied it fails checksum validation; add a new version instead.

The test suite never executes migrations (H2 `create-drop`), so a broken migration passes `./mvnw test`. CI (`.github/workflows/ci.yml`) has a separate job that applies every migration to PostgreSQL 18 and fails if any entity table lacks a `version` column — when adding a new entity, add its table to that job's `entity_tables` list.

## Releasing

This is a library (`kz.innlab:auth-spring-boot-starter`) published to **Maven Central**, registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. `AuthStarterApplication` exists only for local running. CI runs `./mvnw verify`. Full steps: `DEPLOY_MVN.md`.

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw clean deploy -P release -DskipTests
```

The `release` profile signs with GPG and uploads to Sonatype with `autoPublish=false`: the deployment stops at VALIDATED and must be published by hand at central.sonatype.com/publishing/deployments. Central is immutable — a published version can never be replaced. Bump the version in `pom.xml`, README, `INSTALL_INSTRUCTION.md`, and give it a `CHANGELOG.md` entry first.

`scripts/publish.sh` targets GitHub Packages, which `pom.xml` has no `distributionManagement` for; it fails at deploy and is not the release path.

## Renaming the Project

```bash
./scripts/rename-project.sh <new_package> <new_project_name>
# e.g., ./scripts/rename-project.sh com.innlab.cakeup cakeup
```
