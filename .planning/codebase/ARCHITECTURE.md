# Architecture

**Analysis Date:** 2026-03-01

## Pattern Overview

**Overall:** Layered Spring Boot application using Kotlin, following Spring MVC architecture with a standard web application structure.

**Key Characteristics:**
- Spring Boot auto-configuration driven setup with no explicit bean definitions
- Minimal initial scaffolding designed for expansion
- Single entry point to application lifecycle
- Web tier ready for REST or traditional MVC endpoints
- PostgreSQL database connectivity configured but not yet integrated

## Layers

**Application Bootstrap:**
- Purpose: Initializes and configures the Spring Boot application context
- Location: `src/main/kotlin/kz/innlab/template/TemplateApplication.kt`
- Contains: Main entry point, `@SpringBootApplication` annotation
- Depends on: Spring Boot framework components
- Used by: JVM runtime during application startup

**Web Layer (MVC):**
- Purpose: Handles HTTP requests and responses through Spring MVC
- Location: `src/main/kotlin/kz/innlab/template/` (future controllers will live here)
- Contains: Controllers, request handlers, HTTP endpoint definitions
- Depends on: Spring Web, Spring MVC auto-configuration
- Used by: HTTP clients making requests to the application

**Service Layer:**
- Purpose: Business logic and domain operations (to be implemented)
- Location: `src/main/kotlin/kz/innlab/template/` (expected)
- Contains: Service classes handling business rules
- Depends on: Data layer, repositories
- Used by: Controllers and other services

**Data Layer:**
- Purpose: Database access and persistence (to be implemented)
- Location: `src/main/kotlin/kz/innlab/template/` (expected)
- Contains: JPA entities, repositories, database queries
- Depends on: Spring Data JPA, PostgreSQL driver
- Used by: Service layer for data operations

**Configuration Layer:**
- Purpose: Application settings and environment configuration
- Location: `src/main/resources/application.yaml`
- Contains: Server port, application name, database credentials, logging levels
- Depends on: Spring Boot auto-configuration framework
- Used by: All runtime components

## Data Flow

**HTTP Request Handling:**

1. HTTP request arrives at application (port 7070)
2. Spring DispatcherServlet routes request to appropriate controller
3. Controller processes request and delegates to service layer
4. Service layer performs business logic, may access data layer
5. Data layer queries/updates PostgreSQL database
6. Response flows back through service → controller → HTTP response

**Application Startup:**

1. JVM executes `main()` function in `TemplateApplication.kt`
2. `runApplication<TemplateApplication>()` initializes Spring Boot
3. Auto-configuration scans classpath for dependencies
4. Spring creates bean instances and injects dependencies
5. Application context fully initialized and ready for requests

**State Management:**
- Spring manages application state through singleton beans
- Request-scoped beans created per HTTP request
- Stateless service design recommended for scalability
- Database serves as persistent state store

## Key Abstractions

**TemplateApplication:**
- Purpose: Application bootstrap and container
- Examples: `src/main/kotlin/kz/innlab/template/TemplateApplication.kt`
- Pattern: Singleton Spring Boot application context holder

**Spring Beans (Future):**
- Purpose: Managed components following dependency injection pattern
- Pattern: Constructor injection for required dependencies; field injection avoided
- Strategy: Auto-wired components discovered through class path scanning

**Repositories (Future):**
- Purpose: Abstract data access for domain entities
- Pattern: Spring Data JPA repositories extending `JpaRepository` or custom interfaces
- Responsibility: CRUD operations and custom queries

**Services (Future):**
- Purpose: Encapsulate business logic and domain rules
- Pattern: Class-level `@Service` annotation, public methods for use cases
- Responsibility: Orchestrate repositories, validate input, handle transactions

**Controllers (Future):**
- Purpose: HTTP endpoint handlers
- Pattern: Class-level `@RestController` or `@Controller` annotation with `@RequestMapping`
- Responsibility: Parse HTTP input, delegate to services, format response

## Entry Points

**Application Entry:**
- Location: `src/main/kotlin/kz/innlab/template/TemplateApplication.kt` line 9 (main function)
- Triggers: JVM execution `java -jar`
- Responsibilities: Initialize Spring Boot context, start web server on port 7070, enable dependency injection

**HTTP Entry Points:**
- Location: Controllers in `src/main/kotlin/kz/innlab/template/` (to be created)
- Triggers: HTTP requests to defined endpoints
- Responsibilities: Route requests to appropriate handlers, validate input, return HTTP responses

**Test Entry Point:**
- Location: `src/test/kotlin/kz/innlab/template/TemplateApplicationTests.kt`
- Triggers: Maven test execution or IDE test runner
- Responsibilities: Validate application context loads without errors

## Error Handling

**Strategy:** Spring Boot convention with exception translation to HTTP responses

**Patterns:**
- Uncaught exceptions translated to HTTP 500 by default DispatcherServlet error handling
- Spring Data exceptions converted to Spring DataAccessException hierarchy
- Custom exception handlers can be added via `@ControllerAdvice` for specific error responses
- Validation errors (future) handled via `BindingResult` or `@ControllerAdvice`

## Cross-Cutting Concerns

**Logging:**
- Framework: SLF4J with Logback (included via Spring Boot starter)
- Configuration: Via `application.yaml` under `logging.*` properties
- Pattern: Use injected logger instances in components

**Validation:**
- Framework: Jakarta Validation (future addition needed if not already included)
- Pattern: Bean validation annotations on entity fields and method parameters
- Handler: `@ControllerAdvice` methods catch `MethodArgumentNotValidException`

**Authentication:**
- Current: Not implemented
- Future: Spring Security for role-based access control
- Pattern: Interceptor/filter-based approach via Spring Security auto-configuration

**Database Transactions:**
- Framework: Spring Transaction Management
- Pattern: `@Transactional` annotation on service methods
- Behavior: Automatic transaction demarcation, rollback on unchecked exceptions

---

*Architecture analysis: 2026-03-01*
