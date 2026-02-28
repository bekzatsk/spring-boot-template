# Technology Stack

**Analysis Date:** 2026-03-01

## Languages

**Primary:**
- Kotlin 2.2.21 - Server-side application logic and Spring Boot components

**Secondary:**
- Java 24 - JVM bytecode compilation target and runtime

## Runtime

**Environment:**
- Java Virtual Machine (JVM) 24+
- Spring Boot 4.0.3 (latest stable release)

**Build Tool:**
- Maven 3.x - Project build and dependency management
- Maven Wrapper (`mvnw`) - Bundled build tool for cross-platform consistency
- Lockfile: `pom.xml`

## Frameworks

**Core:**
- Spring Boot 4.0.3 - Application framework
- Spring MVC - HTTP request handling via `spring-boot-starter-webmvc`
- Spring Framework (bundled with Spring Boot) - Dependency injection and IoC

**Testing:**
- JUnit 5 - Test framework (bundled with `spring-boot-starter-webmvc-test`)
- Kotlin Test - Kotlin-specific test utilities
- Spring Test - Spring Boot testing context support

**Build/Dev:**
- Kotlin Maven Plugin - Kotlin source compilation
- Spring Boot Maven Plugin - Application packaging and execution
- Jackson - JSON serialization/deserialization

## Key Dependencies

**Critical:**
- `spring-boot-starter-webmvc` - HTTP server and MVC support (REST endpoints)
- `kotlin-stdlib` - Kotlin standard library required for all Kotlin code
- `kotlin-reflect` - Reflection utilities for Spring component scanning
- `jackson-module-kotlin` - Improved Kotlin support for JSON serialization

**Data:**
- `postgresql` (runtime scope) - PostgreSQL JDBC driver for database connectivity

**Testing:**
- `spring-boot-starter-webmvc-test` - Spring MVC testing utilities and MockMvc
- `kotlin-test-junit5` - Kotlin DSL for JUnit 5 tests

## Configuration

**Environment:**
- YAML-based configuration via `src/main/resources/application.yaml`
- Server port: 7070 (defined in application.yaml)
- Application name: "template" (Spring application name)
- No environment-specific profiles defined yet

**Build:**
- Source directory: `src/main/kotlin` (Kotlin sources)
- Test directory: `src/test/kotlin` (Kotlin test sources)
- Compiler configuration: Strict JSR-305 null-safety annotations enabled

## Platform Requirements

**Development:**
- Java 24 or later
- Maven 3.6+ (or use bundled `mvnw`)
- Kotlin compiler support (handled by Maven plugin)

**Production:**
- Java 24 or later JVM
- PostgreSQL database (indicated by postgresql driver in dependencies)
- Minimum 2GB heap memory recommended for Spring Boot application

---

*Stack analysis: 2026-03-01*
