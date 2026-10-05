# Security

## Supported versions

Use the latest release. Older releases carry known vulnerabilities that cannot be patched in place,
because Maven Central is immutable:

| Version | Status |
|---------|--------|
| **0.1.6** | Supported |
| 0.1.5 | Cookie-authenticated requests skip CSRF; the admin recent-login rule only runs in the starter's own controller |
| 0.1.4 and earlier | Additionally ship `application-dev.yaml` with fixed `123456` login codes inside the jar |
| 0.1.0 | Cookie authentication does not work at all |

Each release's fixes and breaking changes are in [CHANGELOG.md](CHANGELOG.md).

## Reporting a vulnerability

Do not open a public issue. Use GitHub's private vulnerability reporting for this repository
(**Security → Report a vulnerability**), with steps to reproduce and the affected version.

## Known limitations

These are open by design decision or not yet implemented. Each says what an application using the
starter should do about it.

1. **Email-code login carries the account's roles, including ADMIN.** `POST /api/v1/auth/email/verify`
   signs in the account that owns the address, so for an admin the mailbox is the only factor.
   *Until there is a policy for privileged accounts:* disable email OTP
   (`app.auth.email-otp.enabled=false`) if admin mailboxes are not protected as strongly as their
   passwords, or put a second factor in front of admin actions.
2. **Access tokens cannot be revoked before they expire.** Role changes, password resets, email
   changes and deletion revoke refresh tokens at once, but an already issued access token works
   until it expires (`app.auth.access-token.expiry-minutes`, 15 by default). Keep the lifetime
   short; immediate revocation would need a token version or denylist checked per request.
3. **No signing-key rotation.** The JWT decoder knows one key. Replacing it signs every user out.
   There is no overlap window with two keys yet.
4. **Rate limits are per instance.** The default `RateLimiter` is in memory. Behind several
   instances every limit is multiplied by the instance count; declare a `RateLimiter` bean backed
   by a shared, atomic store (Redis or similar) for a cluster.
5. **Users can send push notifications with free-form text** to devices registered to them
   (`/api/v1/notifications/send/token`, `/send/multicast`). A device token belongs to whoever
   registered it last, so the previous owner of a shared device can take it back and push to it.
   If your clients do not need user-initiated pushes, restrict these endpoints.
6. **Not covered by the starter's tests:**
   - concurrent refresh-token rotation on PostgreSQL (the tests run on H2; optimistic locking is
     expected to let exactly one rotation win);
   - cookie mode in a real browser behind a reverse proxy, especially with `SameSite=None`, shared
     subdomains, or mixed bearer and cookie clients; login itself carries no CSRF token;
   - live Telegram, SMTP, IMAP, Firebase and Twilio endpoints.

## What the application is responsible for

The starter refuses to start under a production profile (`prod`, `production`) when the most
dangerous settings are wrong, but it cannot see everything:

- set `SPRING_PROFILES_ACTIVE` from the environment, never as a default inside `application.yml`;
  keep any `dev-code` in `application-dev.yml` only;
- configure a persistent keystore and environment-specific `app.security.jwt.issuer` and
  `audience`; never put the keystore under `src/main/resources`;
- behind a reverse proxy, set `server.forward-headers-strategy`, or per-client limits see one
  address for everyone;
- in cookie mode, send `X-XSRF-TOKEN` on every state-changing request, your own endpoints included;
- call `FreshLoginGuard.requireFreshLogin()` in your own flows that grant access (invitations,
  role changes) — the starter's admin services already do;
- keep actuator endpoints other than health private, and do not open `/actuator/**` in a filter
  chain of your own;
- check for duplicate rows before migrations V11–V13 on an existing database: they add unique
  constraints and stop rather than merge accounts. V14 resolves duplicate FCM tokens itself,
  keeping the most recent registration.

## Audit history

| Date | Scope | Result |
|------|-------|--------|
| 2026-09-28 | 0.1.2 | Account takeover through registration, unverified phone binding, missing JWT audience, email-based account linking, and more. Fixed in 0.1.3. |
| 2026-10-02 | 0.1.3 | Email-OTP takeover, open mail relay, OTP brute force, identity change without re-authentication, account enumeration, Jackson CVEs. Fixed in 0.1.4. |
| 2026-10-02 | 0.1.4 | `application-dev.yaml` in the jar, method-level admin authorization, rate-limiter lockout, SMS toll fraud, and lower-severity issues. Fixed in 0.1.5. |
| 2026-10-05 | 0.1.5, reported by a consumer | CSRF skipped for cookie-authenticated requests; recent-login rule bypassed through the admin services. Fixed in 0.1.6. |

The original reports of the first audit (`SECURITY_REVIEW.md`, `SECURITY_IMPLEMENTATION_PLAN.md`,
`SECURITY_FIX_STATUS.md`) are in the git history up to tag `v0.1.6`.
