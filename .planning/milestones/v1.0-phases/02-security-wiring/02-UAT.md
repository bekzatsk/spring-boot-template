---
status: testing
phase: 02-security-wiring
source: [02-01-SUMMARY.md, 02-02-SUMMARY.md]
started: 2026-03-01T01:20:00+05:00
updated: 2026-03-01T01:20:00+05:00
---

## Current Test

number: 1
name: Application Startup
expected: |
  1. Run `docker-compose up -d` — PostgreSQL 18 container starts and is healthy
  2. Run `./mvnw spring-boot:run` — application starts without errors
  3. Hibernate creates `users`, `user_roles`, and `refresh_tokens` tables
  4. Console shows RSA keypair generation log (in-memory dev fallback)
awaiting: user response

## Tests

### 1. Application Startup
expected: docker-compose up -d starts PostgreSQL, ./mvnw spring-boot:run starts without errors, Hibernate creates tables, RSA keypair generated in dev mode
result: [pending]

### 2. Unauthenticated Request Returns 401 JSON
expected: GET /api/v1/users/me without a Bearer token returns HTTP 401 with JSON body { error, message, status }
result: [pending]

### 3. Authenticated Request Returns 200 with Claims
expected: GET /api/v1/users/me with a valid RS256 Bearer token returns HTTP 200 with JSON containing sub and roles from the JWT — no database hit occurs
result: [pending]

### 4. CORS Preflight Passes
expected: OPTIONS request to /api/v1/users/me with Origin header returns HTTP 200 with Access-Control-Allow-Origin and other CORS headers — not blocked by JWT filter
result: [pending]

### 5. Integration Tests Pass
expected: ./mvnw test runs all tests green (5 tests: 4 SecurityIntegrationTest + 1 contextLoads)
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0

## Gaps

[none yet]
