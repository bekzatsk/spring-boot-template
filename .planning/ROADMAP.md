# Roadmap: Spring Boot Auth Template

## Milestones

- ✅ **v1.0 MVP** — Phases 1-6 (shipped 2026-03-01)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-6) — SHIPPED 2026-03-01</summary>

- [x] Phase 1: Foundation (2/2 plans) — completed 2026-03-01
- [x] Phase 2: Security Wiring (2/2 plans) — completed 2026-03-01
- [x] Phase 3: Google Auth and Token Management (2/2 plans) — completed 2026-03-01
- [x] Phase 4: Apple Auth (1/1 plan) — completed 2026-03-01
- [x] Phase 5: Hardening (1/1 plan) — completed 2026-03-01
- [x] Phase 6: Restructure (2/2 plans) — completed 2026-03-01

</details>

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation | v1.0 | 2/2 | Complete | 2026-03-01 |
| 2. Security Wiring | v1.0 | 2/2 | Complete | 2026-03-01 |
| 3. Google Auth and Token Management | v1.0 | 2/2 | Complete | 2026-03-01 |
| 4. Apple Auth | v1.0 | 1/1 | Complete | 2026-03-01 |
| 5. Hardening | v1.0 | 1/1 | Complete | 2026-03-01 |
| 6. Restructure | v1.0 | 2/2 | Complete | 2026-03-01 |
| 1. Local Auth (v2) | v2.0 | 2/3 | In Progress | — |

### Phase 1: Add LOCAL authentication — email+password and phone+SMS code login

**Goal:** Users can register and login with email+password or phone+SMS OTP, with Flyway-managed schema migrations, extending the existing JWT token infrastructure
**Depends on:** v1.0 MVP (Phases 1-6)
**Plans:** 3 plans

Plans:
- [x] 01-01-PLAN.md — Foundation: Maven dependencies (Flyway, Twilio, libphonenumber), AuthProvider.LOCAL, User entity columns, Flyway migrations — completed 2026-03-02
- [x] 01-02-PLAN.md — Email+password auth: LocalUserDetailsService, LocalAuthService, DaoAuthenticationProvider, register/login endpoints, integration tests — completed 2026-03-02
- [ ] 01-03-PLAN.md — Phone+SMS OTP auth: TwilioConfig, PhoneOtpService, E.164 normalization, request-otp/verify-otp endpoints, integration tests
