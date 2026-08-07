# Changelog

## 0.1.0

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
