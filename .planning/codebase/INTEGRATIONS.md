# External Integrations

**Analysis Date:** 2026-03-01

## APIs & External Services

**Not Detected:**
- No external API integrations currently configured
- No third-party SDK dependencies present

## Data Storage

**Databases:**
- PostgreSQL (declared in pom.xml)
  - Connection: Configurable via Spring Boot properties (currently in `src/main/resources/application.yaml`)
  - Client: PostgreSQL JDBC Driver
  - ORM: Not yet integrated (JPA/Hibernate not included)
  - Status: Driver available but no active database configuration

**File Storage:**
- Local filesystem only
- Static resources directory: `src/main/resources/static`
- Templates directory: `src/main/resources/templates`

**Caching:**
- None currently integrated

## Authentication & Identity

**Auth Provider:**
- Custom implementation or none currently configured
- No OAuth2, SAML, or third-party auth providers configured
- Spring Security not included in dependencies

## Monitoring & Observability

**Error Tracking:**
- None configured

**Logs:**
- Spring Boot default logging (SLF4J + Logback implied via Spring Boot starter)
- Default configuration: console output
- Configuration: Can be customized via `application.yaml` or `application.properties`

## CI/CD & Deployment

**Hosting:**
- Not yet configured
- Target: Any platform supporting Java 24 runtime (Docker, Kubernetes, cloud platforms)

**CI Pipeline:**
- Not detected
- No GitHub Actions, GitLab CI, or Jenkins configuration present
- Maven build: Available via `./mvnw clean package`

## Environment Configuration

**Required env vars:**
- No required environment variables currently enforced
- Spring Boot standard properties can be externalized via:
  - Environment variables (e.g., `SERVER_PORT=8080`)
  - `application.yaml` or `application.properties`
  - System properties
  - `.env` file (via external library if added)

**Secrets location:**
- `.env.template` file present but empty - placeholder for future secrets
- `.env` listed in `.gitignore` - secrets should not be committed
- Configuration pattern ready for environment-based secrets (Spring Boot externalized config)

## Webhooks & Callbacks

**Incoming:**
- None currently implemented

**Outgoing:**
- None currently implemented

## Database Schema

**Current State:**
- No JPA entities or database schema defined
- PostgreSQL driver present but no ORM framework to leverage it
- Connection configuration not yet specified in `application.yaml`

**Future Setup:**
- Requires: Adding spring-boot-starter-data-jpa or spring-boot-starter-jpa
- Requires: Entity definitions and Flyway/Liquibase for migrations (if desired)

---

*Integration audit: 2026-03-01*
