# Codebase Concerns

**Analysis Date:** 2026-03-01

## Tech Debt

**Incomplete POM metadata:**
- Issue: Maven pom.xml contains empty placeholder elements for license, developers, SCM, and project URL
- Files: `pom.xml` (lines 17-28)
- Impact: Missing project metadata makes builds less professional, complicates deployment pipelines, and obscures project ownership/documentation
- Fix approach: Populate all metadata sections in pom.xml with actual project details

**Missing Jackson groupId correction:**
- Issue: pom.xml uses `tools.jackson.module` groupId instead of standard `com.fasterxml.jackson.module`
- Files: `pom.xml` (line 47)
- Impact: Dependency resolution may fail or use incorrect versions; Jackson module may not load properly
- Fix approach: Update to `com.fasterxml.jackson.module` with standard artifact `jackson-module-kotlin`

**Java version incompatibility noted:**
- Issue: HELP.md documents that JVM level was downgraded from 25 to 24 due to Kotlin version incompatibility
- Files: `HELP.md`, `pom.xml` (line 30)
- Impact: Project cannot use Java 25 features; Kotlin compiler (v2.2.21) has language support ceiling
- Fix approach: Monitor Kotlin release cycles; consider upgrading Kotlin when Java 25 support is released

## Configuration Gaps

**Minimal test coverage:**
- Issue: Only a single placeholder test exists with empty test body
- Files: `src/test/kotlin/kz/innlab/template/TemplateApplicationTests.kt` (lines 9-11)
- Impact: No automated validation of application behavior; regressions will not be caught; Spring context loading is verified but nothing else
- Fix approach: Add meaningful integration tests and unit tests for features as they are implemented

**No logging configuration:**
- Issue: application.yaml contains only Spring app name and server port; no logging levels, formats, or output configuration
- Files: `src/main/resources/application.yaml`
- Impact: Default logging will use Spring Boot's minimal configuration; debugging production issues becomes difficult; no structured logging
- Fix approach: Add logging configuration sections (logback-spring.xml or application.yaml logging properties)

**Missing database configuration:**
- Issue: PostgreSQL driver is declared as runtime dependency but no connection properties, initialization, or data source configuration exists
- Files: `pom.xml` (lines 52-55)
- Impact: PostgreSQL dependency is unused; application cannot connect to database without manual configuration; suggests incomplete migration
- Fix approach: Either remove PostgreSQL dependency or configure spring.datasource properties in application.yaml

**Empty environment template:**
- Issue: `.env.template` file exists but contains no content
- Files: `.env.template`
- Impact: Developers have no guidance on required environment variables; deployment process unclear
- Fix approach: Document all environment variables needed for development, testing, and production

## Application Scope Issues

**Minimal application bootstrap:**
- Issue: TemplateApplication.kt is a bare Spring Boot application with no business logic, controllers, services, or configuration
- Files: `src/main/kotlin/kz/innlab/template/TemplateApplication.kt` (lines 1-11)
- Impact: Application starts but provides no functionality; unclear what this template is meant to do
- Fix approach: Define and implement actual application capabilities or clarify template purpose

**No health or readiness endpoints:**
- Issue: No Spring Actuator configuration or custom health endpoints exist
- Files: Application configuration
- Impact: Kubernetes/container orchestration cannot determine if application is ready or healthy; no way to verify database connectivity
- Fix approach: Add spring-boot-starter-actuator dependency and configure health endpoints

## Dependency Management Concerns

**Test dependency name mismatch:**
- Issue: pom.xml declares `spring-boot-starter-webmvc-test` which doesn't appear to be a standard Spring Boot artifact
- Files: `pom.xml` (line 58)
- Impact: Dependency resolution may fail or use wrong artifact; testing infrastructure may be incomplete
- Fix approach: Replace with standard `spring-boot-starter-test` which includes all standard test dependencies

**Incomplete test framework:**
- Issue: kotlin-test-junit5 is declared but no test runners or assertions library explicitly configured
- Files: `pom.xml` (lines 62-65)
- Impact: Tests exist but lack standard assertion helpers; custom test infrastructure required
- Fix approach: Add junit-jupiter-api and assertion library (e.g., AssertJ) dependencies

**Missing common Spring starters:**
- Issue: No starters for Spring Data, Spring JPA, or other standard Spring modules despite PostgreSQL dependency
- Files: `pom.xml`
- Impact: Database access layer must be built manually; ORM overhead deferred
- Fix approach: Add spring-boot-starter-data-jpa or spring-boot-starter-data-r2dbc based on requirements

## Security Considerations

**No Spring Security configuration:**
- Issue: No authentication or authorization framework configured
- Files: Application codebase
- Impact: If endpoints are added, they will be publicly accessible with no access control
- Fix approach: Add spring-boot-starter-security dependency and implement authentication strategy

**Default server port exposure:**
- Issue: Server configured to listen on port 7070 with no HTTPS or security headers configured
- Files: `src/main/resources/application.yaml` (line 6)
- Impact: Default configuration is not production-ready; no TLS encryption; vulnerable to MITM attacks
- Fix approach: Configure server.ssl properties and security headers; document port selection rationale

**No input validation framework:**
- Issue: No Spring Validation or Bean Validation (JSR-380) dependencies present
- Files: `pom.xml`
- Impact: When endpoints are added, data validation must be implemented manually or omitted
- Fix approach: Add spring-boot-starter-validation dependency

**Missing CORS configuration:**
- Issue: No CORS policy configuration or security headers configured
- Files: Application configuration
- Impact: If frontend application needs to consume APIs, cross-origin requests will be blocked or unrestricted
- Fix approach: Configure spring.web.cors properties or implement WebMvcConfigurer

## Observability Gaps

**No structured logging:**
- Issue: No SLF4J configuration or structured logging framework configured
- Files: Application
- Impact: Log parsing and analysis difficult; no JSON structured logs for centralized logging systems
- Fix approach: Add logback-spring.xml with JSON layout or configure Logstash encoder

**No metrics collection:**
- Issue: No Micrometer or Prometheus metrics configured
- Files: Application
- Impact: Cannot monitor application performance, request rates, or system health
- Fix approach: Add spring-boot-starter-actuator and configure appropriate meters

**No distributed tracing:**
- Issue: No Spring Cloud Sleuth or distributed tracing framework configured
- Files: Application
- Impact: Cannot trace requests across microservices or log correlation IDs
- Fix approach: Consider adding Spring Cloud Sleuth if microservices architecture is planned

## Build and Deployment Concerns

**No build profiles:**
- Issue: Maven pom.xml has no profiles for dev, test, prod environments
- Files: `pom.xml`
- Impact: Same build artifact used everywhere; no environment-specific configuration possible at build time
- Fix approach: Add Maven profiles for different deployment environments

**No Docker support:**
- Issue: No Dockerfile or container configuration present
- Files: Project root
- Impact: Application cannot be containerized without manual Dockerfile creation; Spring Boot's OCI image build support unused
- Fix approach: Create Dockerfile or use Maven spring-boot:build-image plugin configuration

**No runtime dependencies documentation:**
- Issue: PostgreSQL dependency exists but no documentation on required database version, initialization scripts, or connection pooling
- Files: `pom.xml`
- Impact: Deployment process unclear; database setup prerequisites unknown
- Fix approach: Add DEPLOYMENT.md or README with infrastructure requirements

## Kotlin-Specific Issues

**Minimal use of Kotlin features:**
- Issue: TemplateApplication.kt uses only basic Kotlin syntax; no idiomatic patterns
- Files: `src/main/kotlin/kz/innlab/template/TemplateApplication.kt`
- Impact: Limited as a teaching example for Kotlin best practices
- Fix approach: Add comments explaining Kotlin-specific idioms once application logic is implemented

**JSR-305 annotations enabled:**
- Issue: kotlin-maven-plugin explicitly enables `-Xjsr305=strict` and `-Xannotation-default-target=param-property`
- Files: `pom.xml` (lines 81-82)
- Impact: Compiler treats JSR-305 null safety annotations as platform types; may cause unexpected nullable behavior
- Fix approach: Document nullability assumptions or consider using Kotlin's native @Nullable/@NotNull

---

*Concerns audit: 2026-03-01*
