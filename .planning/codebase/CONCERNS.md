# Codebase Concerns

**Analysis Date:** 2026-03-01

## Tech Debt

**Minimal Implementation:**
- Issue: Project contains only starter classes with no business logic, controllers, services, or repositories
- Files: `src/main/kotlin/kz/innlab/template/TemplateApplication.kt`
- Impact: No actual application functionality exists; application requires significant development before production readiness
- Fix approach: Implement domain models, service layer, REST controllers, and database layer as features are added

**Incomplete Test Coverage:**
- Issue: Single test file with empty test case that only checks if application context loads
- Files: `src/test/kotlin/kz/innlab/template/TemplateApplicationTests.kt`
- Impact: No unit tests for business logic; no integration test patterns established; future developers lack testing guidance
- Fix approach: Establish unit test patterns in `src/test/kotlin/` and create test fixtures for new features

**Missing Configuration Documentation:**
- Issue: POM metadata contains empty or placeholder values; `.env.template` file is empty
- Files: `pom.xml` (lines 16-28 contain empty elements), `.env.template`
- Impact: Unclear what environment variables are required or expected; incomplete project metadata makes multi-team development harder
- Fix approach: Populate `<license>`, `<developers>`, `<scm>` in pom.xml; document all required environment variables in `.env.template`

## Known Bugs

**Java Version Compatibility Issue:**
- Symptoms: HELP.md reports JVM was downgraded from Java 25 to 24 because Kotlin doesn't support Java 25 yet
- Files: `HELP.md` (line 4), `pom.xml` (line 30)
- Trigger: Building with latest Java 25 JDK installed
- Workaround: Continue using Java 24 until Kotlin releases compatible version

## Security Considerations

**No Authentication/Authorization Framework:**
- Risk: Application has no security configuration (no Spring Security, no JWT implementation, no role-based access control)
- Files: `src/main/kotlin/kz/innlab/template/TemplateApplication.kt` - no security dependencies
- Current mitigation: None
- Recommendations: Before adding any API endpoints, implement Spring Security with appropriate authentication mechanism (e.g., JWT, OAuth2, or API keys)

**PostgreSQL Dependency Not Integrated:**
- Risk: PostgreSQL driver is declared in pom.xml but not configured; database connection details not specified; credentials would be hardcoded or missing
- Files: `pom.xml` (lines 52-55), no configuration in `application.yaml`
- Current mitigation: Dependency is runtime scoped
- Recommendations: Add Spring Data JPA starter; configure database properties in `application.yaml` using environment variables; never hardcode credentials

**Environment Variables Not Enforced:**
- Risk: `.env.template` is empty; no validation that required environment variables are present at startup
- Files: `.env.template`, `.gitignore` (line 34 ignores .env)
- Current mitigation: Good: .env files are in .gitignore
- Recommendations: Create startup health checks that validate all required env vars are set; document in `.env.template` with examples like `DATABASE_URL=postgresql://user:pass@localhost/dbname`

**No Input Validation:**
- Risk: When REST controllers are added, no framework established for request validation
- Files: Not yet applicable, but will affect future `src/main/kotlin/kz/innlab/template/controller/` files
- Current mitigation: None
- Recommendations: Use `@Valid` with Jakarta Bean Validation; create centralized exception handler for validation errors

## Performance Bottlenecks

**No Caching Layer Configured:**
- Problem: No caching mechanism established (Redis, Caffeine, or Spring Cache)
- Files: `pom.xml` - no caching dependency; `application.yaml` - no cache configuration
- Cause: Template intentionally minimal, but N+1 query problems will emerge when data layer is added
- Improvement path: Add Spring Cache starter and configure appropriate caching strategy before deploying with data access

**No Database Connection Pooling Configuration:**
- Problem: PostgreSQL driver present but HikariCP (or other pooling) not explicitly configured
- Files: `pom.xml` (lines 52-55), `application.yaml` - missing `spring.datasource.*` properties
- Cause: Spring Boot defaults work but not optimized for production loads
- Improvement path: Configure `spring.datasource.hikari.*` properties (maxPoolSize, minimumIdle, idleTimeout) in `application.yaml` once database integration is complete

## Fragile Areas

**Empty POM Metadata Blocks:**
- Files: `pom.xml` (lines 16-28)
- Why fragile: Build tools, documentation generators, and package repositories may fail or behave unexpectedly with empty license/developer/scm elements
- Safe modification: Fill in `<license>`, `<developer>` (at minimum with empty sub-elements or valid values), and `<scm>` sections; never leave empty tags
- Test coverage: Automated: run `mvn project-help:describe` to validate POM structure

**Missing Spring Profiles Configuration:**
- Files: `application.yaml` - no profile-specific files exist
- Why fragile: Same configuration will be applied to dev, test, and production; port hardcoded to 7070; no environment-specific overrides
- Safe modification: Create `application-dev.yaml`, `application-test.yaml`, `application-prod.yaml` with environment-specific settings; use `spring.profiles.active` environment variable
- Test coverage: Manual: verify application starts correctly with each profile: `./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"`

**No Graceful Shutdown Configuration:**
- Files: `application.yaml` - missing shutdown configuration
- Why fragile: Abrupt termination during deployment can leave requests in-flight or database connections hanging
- Safe modification: Add to `application.yaml`:
  ```yaml
  server:
    shutdown: graceful
  spring:
    lifecycle:
      timeout-per-shutdown-phase: 30s
  ```
- Test coverage: Manual: send SIGTERM during active request and verify proper cleanup

**Kotlin Compiler Arguments Hard to Discover:**
- Files: `pom.xml` (lines 80-82) - kotlin-maven-plugin args are non-standard
- Why fragile: `-Xjsr305=strict` enables strict null checks; if removed accidentally, null-safety assumptions break
- Safe modification: Document in README why these compiler args exist; add to comments in pom.xml; communicate to team that removing these requires thorough testing
- Test coverage: Manual: run compilation with `./mvnw clean compile` and verify no warnings about null-safety

## Scaling Limits

**Single Module Architecture:**
- Current capacity: Suitable for teams of 1-2 developers, simple single-bounded-context applications
- Limit: Once business logic exceeds ~1000 lines of code or team exceeds 3 developers, monolith becomes difficult to modify safely
- Scaling path: Break into modules (e.g., `app-api`, `app-domain`, `app-infrastructure`) using Maven multi-module structure; or extract to separate services if domain complexity warrants it

**Embedded Tomcat Server:**
- Current capacity: ~100-200 concurrent requests with default configuration
- Limit: Single application instance has no horizontal scaling; default thread pool (200 threads) reached before load balancer can help
- Scaling path: Configure `server.tomcat.threads.max` based on load testing; add load balancer and deploy multiple instances; consider async/reactive stack (Spring WebFlux) if I/O bound

## Dependencies at Risk

**Kotlin 2.2.21 Against Java 24:**
- Risk: Beta/release cycle mismatch - Kotlin released 2.2.21 but full Java 24 support may be incomplete
- Impact: Potential compiler issues, runtime edge cases, or deprecation warnings in future releases
- Migration plan: Monitor Kotlin releases monthly; upgrade to next Kotlin 2.3.x when Java 25 is fully supported; test thoroughly in dev environment before rolling to production

**Spring Boot 4.0.3:**
- Risk: Not latest; future security patches will require active version bumps
- Impact: Security vulnerabilities discovered after release date will not be backported
- Migration plan: Establish quarterly dependency update cycle; test Spring Boot 4.1.x when released; keep Spring Security and other security libraries up-to-date independent of Boot version

**Jackson Module for Kotlin (tools.jackson.module:jackson-module-kotlin):**
- Risk: Third-party Maven group ID (`tools.jackson.module` instead of standard `com.fasterxml.jackson`); potential supply chain risk
- Impact: If package is compromised or removed, build fails
- Migration plan: Verify this is correct upstream; consider switching to standard `com.fasterxml.jackson.module:jackson-module-kotlin` if this is unintended; audit dependency origin quarterly

## Missing Critical Features

**No HTTP Error Handling Framework:**
- Problem: No `@ControllerAdvice` or error handler configured; when controllers are added, errors will return raw exception stack traces
- Blocks: Production-ready API error responses; standardized error format for client applications
- Priority: High - implement before first controller is added

**No Logging Configuration:**
- Problem: No SLF4J or Logback configuration; Spring Boot defaults to INFO level with basic formatting
- Blocks: Production debugging, audit trails, monitoring
- Priority: High - create `src/main/resources/logback-spring.xml` with appropriate levels per package and structured logging format

**No API Documentation:**
- Problem: No SpringDoc OpenAPI (Swagger) integration
- Blocks: Client developers cannot discover API contracts; no generated API documentation
- Priority: Medium - add springdoc-openapi-starter-webmvc-ui dependency and configure before deploying with multiple endpoints

**No Actuator/Health Checks:**
- Problem: No Spring Boot Actuator; health endpoint unavailable; deployment orchestrators cannot determine application health
- Blocks: Kubernetes readiness/liveness probes; monitoring dashboards
- Priority: High - add `spring-boot-starter-actuator` before containerization

## Test Coverage Gaps

**No Unit Test Infrastructure:**
- What's not tested: No test utilities, no test data builders, no assertions framework preference documented
- Files: `src/test/kotlin/` - only has placeholder test
- Risk: Future developers will create ad-hoc tests with inconsistent patterns; test maintenance becomes expensive
- Priority: High - create test utilities in `src/test/kotlin/kz/innlab/template/util/` with builders and assertion helpers; document test patterns in TESTING.md

**No Integration Test Configuration:**
- What's not tested: Database interactions, REST endpoint behavior, Spring context integration
- Files: No `@SpringBootTest` tests beyond context load check; no TestContainers setup
- Risk: Business logic shipped without verifying Spring component wiring or database queries
- Priority: High - establish TestContainers pattern for database tests; create integration test template

**No Contract/API Tests:**
- What's not tested: If this becomes a multi-service system, no consumer-driven contract tests exist
- Files: Not applicable yet
- Risk: Service integrations will break in production due to incompatible changes
- Priority: Medium (initially) - establish pattern when first external integration is added

---

*Concerns audit: 2026-03-01*
