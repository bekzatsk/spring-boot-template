# Changelog

## Unreleased

### Security

- **CSRF was not enforced on cookie-authenticated requests.** In cookie mode the access cookie was
  read by a `BearerTokenResolver` plugged into the resource server, and the resource server exempts
  from CSRF every request its resolver finds a token in. Any state-changing request carrying only
  the access cookie skipped CSRF — a forged write to a consumer's own endpoint answered `204`. The
  cookie is now turned into an `Authorization` header by `AccessTokenCookieFilter` **after**
  `CsrfFilter`, so CSRF judges the request as the browser sent it. Requests with their own
  `Authorization` header still need no CSRF token.
- **The recent-login rule for admin actions is enforced in the services**, not only in the
  starter's controller. A consumer endpoint calling `AdminUserService` (or
  `UserService.createUserByAdmin`) directly — a team screen, an invitation flow — skipped it.
  The check lives in the new public `FreshLoginGuard` bean; call `requireFreshLogin()` from your
  own endpoints that grant access in the same way. A JWT caller must have logged in within
  `app.auth.security.admin-fresh-login-seconds`; an anonymous caller is refused; code running with
  no authentication at all (a scheduled job, a migration) is allowed.
- `ForbiddenOperationException` carries `@ResponseStatus(FORBIDDEN)`, so it answers `403` from
  consumer controllers too (the starter's exception handler only covers its own).

### Changed (breaking)

- `AdminUserService` and `UserService` take a `FreshLoginGuard` constructor argument, and their
  account-handover methods throw `RecentLoginRequiredException` (403) for a stale or anonymous
  caller. Calls with no authentication in the security context are unaffected.
- `CookieBearerTokenResolver` is deprecated and no longer used. Plugged into a resource server, it
  disables CSRF for cookie-authenticated requests.

## 0.1.5 — 2026-10-02

Second security release, from a re-audit of 0.1.4. **0.1.4 and earlier ship
`application-dev.yaml` inside the jar** (fixed `123456` login codes for any application running
with the `dev` profile and no file of its own) — upgrade. Also fixes SMS toll fraud, a rate
limiter that could be filled to lock everyone out, admin endpoints that relied on URL rules alone,
gaps in the email-OTP takeover fix, and a set of smaller issues. Contains **breaking changes** (listed below) and
migration `V16`.

### Security

- **The starter jar no longer ships `application*.yaml`.** They configure this repository's
  runnable app, but packaged they loaded into every consumer without a file of the same name. A
  consumer running with the `dev` profile and no `application-dev.yaml` of its own got every
  `dev-code` override, so anyone could log in with `123456`. **This affects 0.1.4 and earlier:**
  upgrade, or ship your own `application-dev.yaml`. CI now fails if the files reappear.
- **Email code identifiers ignore letter case.** Reset, verify and resend keyed codes on the raw
  input, so every case variant of an address had its own code and guessing budget — unlimited
  guessing, and a way around the email-OTP takeover fix.
- **An unverified account cannot change its email or phone**, and when email-code login claims an
  unverified account it also clears the phone and drops push devices (new
  `DeviceRegistrationRevoker` port). Completing an email change marks the address verified.
- **Admin-only handlers are enforced with `@PreAuthorize`**, not only by URL rules, so they stay
  closed when `app.auth.security.enabled=false` or the consumer defines its own chain.
  `AccessDeniedException` answers `403`. The starter's `jwtDecoder` is `@Primary`, which also fixes
  startup with the starter's chain disabled (two `JwtDecoder` candidates).
- **Account-handover admin actions need a recent login**: password, email, phone and role changes,
  deletion and creation of a user require the token's `auth_time` within
  `app.auth.security.admin-fresh-login-seconds` (default 900). An admin email change ends the
  target's sessions.
- **Last-admin check counts admins in a separate statement after locking them**; on PostgreSQL the
  locking query's own result still counted an admin demoted by a concurrent transaction.
- **Actuator endpoints other than health are admin-only.**
- **Per-client code-send limit buckets IPv6 by /64**, so rotating addresses inside one subscriber's
  prefix no longer escapes it.
- **Input bounds**: push `data` keys/values (64/1024 characters), topic broadcast body and data,
  `fcmToken` (512) and `deviceId` (255) at registration, mail subject (255). Each used to be
  unbounded or wider than its column, answering `500` or storing megabytes per call.
- **Mail**: recipient addresses are masked in every mail log; `ExternalMailService.sendCode` sends
  after commit with connect/read timeouts instead of synchronously inside the caller's transaction;
  inbox HTML also drops `<img>` (tracking pixels).
- **Errors**: malformed path variables and missing parameters answer `400`, concurrent-update and
  unique-constraint conflicts `409` (all were `500`). `IllegalStateException`/`IllegalArgumentException`
  messages reach the client only when the starter threw them, not a library.
- **Key material**: `generate-keystore.sh` writes to `secrets/` with a generated password and refuses
  paths under `src/`; the jar excludes `*.p12`, `*.jks`, `*.pem`, `*.key`. `publish.sh` no longer
  puts the GitHub token on the command line.
- **`V16` gives `refresh_tokens.authenticated_at` a default** (`1970-01-01`, metadata-only), so
  instances still on an older version keep creating refresh tokens during a rolling deploy; those
  tokens count as "logged in at the epoch". `V15` itself is unchanged — it shipped in 0.1.4.
- **The in-memory rate limiter no longer locks newcomers out when full.** At capacity it refused
  every key it was not already tracking, so filling it with fresh emails answered `429` to every
  other user. It now evicts expired entries, then the lowest-count ones; a flood of fresh keys
  mostly evicts itself, and a key with accumulated failures survives it.
- **Code sending is limited per client and per purpose.** The per-identifier cooldown let one
  client send codes to an unbounded list of phone numbers (SMS toll fraud). New limits:
  `app.auth.rate-limit.code-send-per-client` (default 20 per hour per client address) and
  `app.auth.rate-limit.code-send-per-purpose` (default 1,000 per hour, a circuit breaker), both
  answered with `429` and `Retry-After`. **Behind a reverse proxy, set
  `server.forward-headers-strategy`**, or every request shares the proxy's address and the
  per-client limit acts as a global one.
- **The console Telegram bot no longer logs message text**, which carried the login code.
  `ProductionSafetyConfig` refuses to start with Telegram enabled and no bot token.
- **The Telegram webhook secret is compared in constant time.**
- **Admin user list**: sorting is limited to `email`, `name`, `phone`, `createdAt` (sorting by
  `passwordHash` was accepted) and pages are capped at 100 rows (Spring's default ceiling is 2000).
- **Last-admin race**: removing the admin role or deleting an admin locks the admin rows before
  counting, so two admins demoting each other at once can no longer leave none.
- **Admin user creation is audited** (`CREATE_USER`), through the new `AdminUserService.createUser`.
- **Admin profile updates are validated**: `name` up to 255 characters, `picture` an http(s) URL.
- **Shared inbox HTML is sanitized** with jsoup (`Safelist.relaxed()`) before it is returned:
  scripts, event handlers and `javascript:` links from outside senders are stripped. The inbox
  listing fetches only the requested page, rejects a negative offset, and IMAP calls time out
  after 10 seconds.
- **Email lookups ignore letter case everywhere** — login, password reset, verification, email
  changes and admin checks. Mixed-case input used to fail login and answer 500 on duplicates.
- **Push payloads are bounded**: `body` up to 2,000 characters, `data` up to 20 entries.
- **Less personal data in logs**: the admin audit log line no longer carries before/after values
  (they stay in the audit table); the console push and mail fallbacks no longer log device tokens,
  message content or full addresses.
- **`ProductionSafetyConfig` refuses auth cookies without `Secure`.**
- **The unused OAuth2 authorization server is gone**: the starter depends on
  `spring-boot-starter-oauth2-resource-server` instead, so consumers no longer get authorization
  server auto-configuration and its endpoints.
- `docker-compose.yml` binds PostgreSQL to `127.0.0.1`; CI runs with read-only `contents` permission.

### Changed (breaking)

- `spring-boot-starter-oauth2-authorization-server` is no longer a transitive dependency. A consumer
  that references `OAuth2AuthorizationServerAutoConfiguration` (for example to exclude it) must
  drop that reference, or add the dependency itself if it really runs an authorization server.
- The admin user list answers `400` for sort properties outside the allowlist.
- New dependency: `org.jsoup:jsoup`.
- `application*.yaml` are no longer in the jar. Settings you relied on from them (for example
  `app.auth.email-verification.enabled: true`, `spring.threads.virtual.enabled`, springdoc paths)
  now fall back to the code defaults unless your application sets them.
- Accounts with an unverified email get `403` on `change-email` / `change-phone` until they verify.
- Admin password, email, phone, role, delete and create calls answer `403` when the admin's login is
  older than 15 minutes; clients must send the admin through login again.
- Signed-in non-admins get `403` on `/actuator/**` other than health.

## 0.1.4 — 2026-10-02

Security release. It closes an account takeover through email-OTP login, an open mail relay and
several smaller holes found in a security review, and updates Jackson for seven advisories. It
contains **breaking changes** (listed below) and two migrations, `V14` and `V15`.

### Dependencies

- Jackson 3.1.7 and 2.21.7, overriding the 3.1.5 / 2.21.5 that Spring Boot 4.1.1 manages:
  GHSA-7hhh-6rmp-j9qf, GHSA-cxp5-3px4-pw24, GHSA-gx83-3vf8-gh7j, GHSA-p6pp-m3f8-5c89,
  GHSA-q4xh-88c3-wmh7, GHSA-wjgm-6hv5-3cvf, GHSA-wv8q-qhhj-9h54.

### Security

- **Account takeover through email-OTP login.** Anyone could register a victim's address with
  their own password; when the owner later signed in with an email code, that account was adopted
  with the registrant's password and refresh tokens intact. Email-OTP login now clears the password
  and revokes all refresh tokens of an account whose address was never verified.
- **Mail send is admin-only.** `POST /api/v1/mail/send` and `/send/with-attachments` were open to
  every authenticated user — a phishing relay from the application's domain that the per-user
  quota could not stop across accounts. They now require `ROLE_ADMIN`, like the inbox.
  `to` must be a single valid address; subject and bodies are size-limited.

- **Production guards cover more than the literal `prod` profile.** `ProductionSafetyConfig` and the
  JWT keystore check now run under any profile in `app.security.production-profiles`
  (default `prod,production`). A deployment running as `production` used to start with an
  in-memory signing key, accepted dev OTP overrides, and skipped every other check.
- **Swagger UI and the OpenAPI spec are no longer public.** Set
  `app.auth.security.public-api-docs=true` to serve them without authentication (the dev profile does).
- **Admin rules are evaluated before `public-paths`**, so an entry such as `/api/**` can no longer
  open `/api/v1/admin/**`, the shared inbox, topic broadcast or mail send.
- **The starter's exception handler is scoped to its own controllers.** It used to answer for the
  whole application and return the message of any `IllegalStateException`/`IllegalArgumentException`
  the consumer's code threw.
- **An FCM token belongs to one user.** Registering a token another user holds removes their
  registration, so the previous owner of a device can no longer push to whoever signs in on it next.
  Migration `V14` keeps the most recent registration of each duplicated token and adds a unique index.
- **One-time codes have a guessing budget across codes.** Each code allowed 3 attempts, but a new
  code (one a minute) started a fresh count, so an attacker could keep guessing indefinitely —
  about 28 hours on average to hit a 4-digit phone code. Code checks are now limited per identifier
  and purpose across codes: `app.auth.rate-limit.otp-verify` (default 10 per 24 hours), answered
  with `429` and `Retry-After`; a correct code clears the count. Anyone who knows an address can
  spend that budget, which blocks code login for that address for the window — lower the window if
  that trade-off does not suit you.
- **Changing email or phone requires proof of ownership.** A stolen access token was enough to
  move an account to the thief's address and then take it over through password reset or code
  login. `/users/me/change-email/request` and `/change-phone/request` now take `currentPassword`,
  or `reauthVerificationId` + `reauthCode` from the new `POST /users/me/reauth/request` (a code
  sent to the current email, or phone if there is no email). An account with no password, email or
  phone (Telegram-only) can add one only within 5 minutes of logging in. Completing either change
  revokes all refresh tokens.
- **Access tokens carry `auth_time`**, the time of the login they descend from. Refresh keeps it:
  migration `V15` adds `refresh_tokens.authenticated_at`, copied on every rotation.
- **Password reset, verification resend and email verification no longer reveal which addresses
  have accounts.** `forgot-password` and `verify-email/resend` answered `{"verificationId": null}`
  for unknown addresses and a UUID for real ones, took longer for real ones (synchronous mail
  send), and only real ones hit the once-a-minute cooldown. They now return a verificationId either
  way (a random one when nothing is sent), apply the cooldown to every address, hash a throwaway
  code when there is nothing to send, and send mail on the starter's executor. `verify-email` used
  to answer `200` for any already-verified address without checking the code.
- **Changing a user's roles revokes their refresh tokens**, so a demoted admin cannot keep minting
  tokens that carry the old roles.

### Changed (breaking)

- Password registrations start with `emailVerified = false` even when
  `app.auth.email-verification.enabled=false`. The account is not gated, but the profile no
  longer claims an address nobody proved. Users created before this release keep their value.
- `UserService` takes a `RefreshTokenRevoker` constructor argument. Applications that construct
  or subclass it must pass one.
- Login with a password for an account that has none now answers `401` instead of `500`.
- Swagger UI and `/v3/api-docs` require authentication unless `app.auth.security.public-api-docs=true`.
- Exceptions thrown by the consumer's own controllers are no longer turned into the starter's
  `ErrorResponse`; declare your own `@RestControllerAdvice` if you relied on it.
- Migration `V14` deletes all but the most recent registration of each duplicated FCM token.
- `/users/me/change-email/request` and `/change-phone/request` answer `403` without `currentPassword`
  or a re-authentication code. Clients must collect one before starting the change.
- `RefreshTokenService.rotate` returns `RotatedRefreshToken` instead of `Pair<User, String>`. It still
  destructures as `(user, rawToken)`.
- `AccountManagementService.requestEmailChange`/`requestPhoneChange` take a `ReauthProof`.
- Re-authentication codes reach `EmailService.sendCode` with purpose `"REAUTH"`; custom
  implementations that pick a template by purpose need one for it.
- `forgot-password` and `verify-email/resend` always return a `verificationId`; clients can no
  longer use `null` to say "no such account". `AccountManagementService.requestPasswordReset` and
  `resendEmailVerification` return `UUID` instead of `UUID?`.
- `verify-email` is no longer idempotent: repeating it after success answers `401`, as a spent code.
- Reset and resend codes are sent asynchronously on `authStarterTaskExecutor`; a failure to send
  no longer fails the request.

### Fixed

- The "maximum device tokens" error printed the properties object instead of the limit.

## 0.1.2

### Changed

- Upgraded to Spring Boot 4.1.1, Java 25 and Kotlin 2.3.21.
- Phone and email OTP lengths are independently configurable as 4 or 6 digits through
  `app.auth.phone.code-length` and `app.auth.email-otp.code-length`.

### Added

- Passwordless email OTP authentication through `POST /api/v1/auth/email/request` and
  `POST /api/v1/auth/email/verify`.
- Email OTP provider toggle: `app.auth.email-otp.enabled`.

## 0.1.1

Fixes a regression introduced in 0.1.0. No other changes — everything in the 0.1.0 notes below
still applies.

### Fixed

- **Cookie authentication did nothing in 0.1.0.** `AuthCookieWriter` was left out when the
  auto-configuration stopped scanning the starter package, and no `@Bean` method declared it.
  Every consumer of that bean injects it as `ObjectProvider<AuthCookieWriter>` and reads absence
  as "cookie mode off", so nothing failed: the application started clean, logged nothing, answered
  `200` — and never sent `Set-Cookie`. `app.auth.cookie.enabled=true` had no effect through yaml,
  environment variables or `SPRING_APPLICATION_JSON`.
- `FirebaseConfig.firebaseApp` gained `@ConditionalOnMissingBean(FirebaseApp::class)`, so a
  consumer that builds its own `FirebaseApp` no longer collides with the starter's.

### Changed

- **`ProductionSafetyConfig`'s console-fallback checks are waived per channel.** An application
  with no SMS provider could only get past the SMS check with
  `app.security.allow-console-fallbacks=true`, which disarmed the email and outgoing-mail guards
  along with it — the blanket switch bought a start-up at the cost of the guards that were still
  doing their job. Three new switches replace it for that purpose:

  | Property | Waives |
  |---|---|
  | `app.security.console-fallbacks.allow-sms` | phone OTP and `change-phone` codes |
  | `app.security.console-fallbacks.allow-email` | registration, password-reset and email-change codes |
  | `app.security.console-fallbacks.allow-mail` | outgoing mail from `/api/v1/mail` |

  `app.security.allow-console-fallbacks=true` still waives all three and keeps working; nothing
  needs to change on upgrade. The guard's messages now name the affected flows and the exact
  switch to set.

  The checks are not tied to whether a provider is enabled, which would be the obvious move:
  `/users/me/change-phone/request` sends an OTP whether or not `app.auth.phone.enabled` is set,
  and any consumer can call `MailService` directly, so "provider disabled" does not mean "channel
  unused". Declaring the waiver is the operator's call.

### Tests

The starter's own suite could not have caught this, and stayed green (174 tests) against the
broken artifact: `@SpringBootTest` boots `AuthStarterApplication`, whose `@SpringBootApplication`
scan covers `kz.innlab.starter` and creates any bean the auto-configuration forgets. A consumer
application lives in another package and gets no such backfill.

New tests under `kz.innlab.consumer` build the context the way a consumer does — from
`AuthAutoConfiguration` alone, with no scan of the starter package:

- `AutoConfigurationContractTest` — bean names and types, rebuilt on `ApplicationContextRunner`.
- `ConditionalBeanContractTest` — every `@ConditionalOnProperty` bean asserted present with the
  flag on and absent with it off. A missing optional bean does not break startup, so this is the
  only kind of test that catches this class of regression.
- `AutoConfigurationCoverageTest` — audits every stereotype-annotated class in the starter against
  the beans a real consumer context holds. Run against the broken commit it reports exactly one
  missing class, `AuthCookieWriter`, which is also the evidence that nothing else was dropped.
- `ConsumerCookieAuthIntegrationTest` — logs in over MockMvc from a consumer application and
  asserts the `Set-Cookie` headers with `HttpOnly`, `Secure` and `SameSite`.

## 0.1.0

**⛔ Withdrawn — use 0.1.1.** Cookie authentication is dead in this release (see 0.1.1 above).
Maven Central is immutable, so the artifact stays published; do not depend on it.

Security, transaction and architecture hardening. **Upgrading requires action** — see below.

The minor bump (rather than `0.0.14`) signals the size of the change: five breaking changes and
a new auto-configuration contract. Everything in `0.0.x` should be treated as superseded.

### ⚠ Upgrade steps

1. **Set `SPRING_PROFILES_ACTIVE` explicitly.** There is no default profile any more. Previously
   it fell back to `dev`, which silently enabled the fixed verification code `123456` on every
   OTP flow — a deployment that forgot the variable accepted that code in production.
2. **Review your public paths.** The filter chain now ends in `authenticated`, not `permitAll`.
   Anything your application serves outside `/api/**` is protected unless you list it under
   `app.auth.security.public-paths`. `/actuator/health` stays public for container health checks.
3. **Check admin roles.** `POST /api/v1/notifications/send/topic` and `/api/v1/mail/inbox/**` now
   require `ROLE_ADMIN`.
4. **Adjust these signatures** if you call them directly:
   `AdminUserService.list()` returns `Page<UserSummaryResponse>`;
   `TopicService.subscribe/unsubscribe` take a `userId` first argument.
5. **Apply migrations V8, V9 and V10.** V9 merges `sms_verifications` into `verification_codes`
   and drops the old table; V10 adds optimistic-locking columns.

Bean names, configuration property names and response JSON are unchanged.

### Fixed — security

- **Fixed OTP reachable in production.** The active profile defaulted to `dev`, enabling the
  `123456` override on every code flow. No default profile now, plus `ProductionSafetyConfig`
  refuses to start under `prod` with a dev override, console fallbacks, or Telegram enabled
  without a webhook secret.
- **Verification codes written to logs.** The console SMS/email fallbacks logged the raw code and
  are selected automatically whenever a real provider is not configured.
- **Push and shared inbox open to any user.** Any authenticated user could push to any FCM token,
  broadcast to a topic's whole subscriber base, and read the shared organisation mailbox.
- **Brute-force protection never applied.** The attempt counter was incremented inside the
  caller's transaction and the failure path threw, rolling the increment back — every attempt
  started from zero on all channels.
- **Refresh-token reuse detection revoked nothing.** The family delete happened in the same
  transaction as the throw that followed it, so a replayed token left every session usable.
- **No rate limit on login.** The password check answered as fast as the server could. Login and
  change-password are now limited (`app.auth.rate-limit.*`) and answer 429 with `Retry-After`.
- **Telegram**: resend reset the attempt counter with no cooldown, the webhook secret check was
  skipped when the secret was unset, and per-IP limits trusted a client-supplied
  `X-Forwarded-For`.
- **Weak passwords accepted** on change-password and reset-password, bypassing the registration
  policy.
- Apple token failures echoed the underlying parser message to the caller.

### Fixed — correctness

- External calls (SMTP, FCM, Telegram, SMS) no longer run inside open transactions holding a
  pooled DB connection; `@Async` work is scheduled after commit and runs on a bounded executor.
- Missing or malformed request bodies return 400 instead of 500; missing records return 404
  instead of 500.
- Optimistic locking added — concurrent updates to a user silently lost changes.
- N+1 removed from the admin user list.
- Admin search escapes LIKE wildcards; `%` used to match every row.

### Changed — architecture

- Auto-configuration registers beans explicitly with `@ConditionalOnMissingBean` instead of
  scanning its package, so **any** starter bean can now be replaced by declaring your own of the
  same type. Bean names are unchanged and covered by a contract test.
- Phone OTP merged into the shared one-time-code service; the duplicate table and its divergent
  copy of the brute-force checks are gone.
- Settings moved from scattered `@Value` to validated `@ConfigurationProperties`.
- Raw `Map` request/response bodies replaced by typed DTOs, restoring `@Valid` and the OpenAPI
  schema.
- Telegram bot copy moved behind a replaceable `TelegramBotMessages` bean — it previously carried
  a third party's brand.
- Module cycle between `user` and `authentication` broken.

### Added

- CI: build and test on every pull request, plus a job that applies all migrations against
  PostgreSQL 18 (the suite runs on H2 and never executes them).
- Test count went from 106 to 174.

### Known limitations

- Rate limiting is in-memory and therefore per instance. Declare a `RateLimiter` bean backed by a
  shared store for a clustered deployment.
- Registration still reveals whether an email is taken (`409 Email already registered`).
- The OTP request limit answers 409 where the newer limits answer 429.
