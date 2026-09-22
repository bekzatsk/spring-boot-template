# Changelog

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
