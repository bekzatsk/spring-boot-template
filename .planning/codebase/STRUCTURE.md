# Codebase Structure

**Analysis Date:** 2026-03-01

## Directory Layout

```
spring-boot-template/
├── src/
│   ├── main/
│   │   ├── kotlin/kz/innlab/template/         # Application source code
│   │   └── resources/
│   │       ├── application.yaml               # Configuration file
│   │       ├── static/                        # Static assets (CSS, JS, images)
│   │       └── templates/                     # HTML templates (Thymeleaf)
│   └── test/
│       └── kotlin/kz/innlab/template/         # Unit and integration tests
├── pom.xml                                    # Maven project configuration
├── mvnw / mvnw.cmd                            # Maven wrapper scripts
├── .env.template                              # Environment configuration template
├── .gitignore                                 # Git ignore rules
└── HELP.md                                    # Project documentation
```

## Directory Purposes

**src/main/kotlin/kz/innlab/template/:**
- Purpose: All application source code organized by package structure
- Contains: Application bootstrap class, future controllers, services, entities, repositories
- Key files: `TemplateApplication.kt`
- Package convention: `kz.innlab.template` as root, extend with subdomain packages

**src/main/resources/:**
- Purpose: Non-code resources needed at runtime
- Contains: Configuration files, static web content, templates
- Key files: `application.yaml`

**src/main/resources/static/:**
- Purpose: Static web assets served directly by Spring
- Contains: CSS files, JavaScript files, images, fonts
- Served from: Root path or `/static/` context depending on configuration

**src/main/resources/templates/:**
- Purpose: Server-side rendered templates (if using traditional MVC)
- Contains: Thymeleaf templates (.html files)
- Pattern: One template per endpoint or logical view
- Note: Only used for traditional MVC; REST APIs return JSON directly

**src/test/kotlin/kz/innlab/template/:**
- Purpose: Test code mirroring source code structure
- Contains: Unit tests, integration tests, test fixtures
- Pattern: One test class per source class, named `{SourceClass}Tests.kt` or `{SourceClass}Test.kt`
- Key files: `TemplateApplicationTests.kt`

**pom.xml:**
- Purpose: Maven project configuration and dependency management
- Contains: Project metadata, dependency declarations, plugin configurations
- Package manager: Maven (via `mvnw` wrapper)

**.env.template:**
- Purpose: Template showing required environment variables
- Contains: Example environment variable names and values
- Usage: Copy to `.env` and fill in actual values (never committed)

## Key File Locations

**Entry Points:**
- `src/main/kotlin/kz/innlab/template/TemplateApplication.kt`: Application bootstrap, main function

**Configuration:**
- `src/main/resources/application.yaml`: Spring Boot configuration (server port, application name, database)
- `pom.xml`: Maven dependencies and build configuration

**Core Logic:**
- `src/main/kotlin/kz/innlab/template/`: All application code (empty initially, ready for expansion)

**Testing:**
- `src/test/kotlin/kz/innlab/template/`: Test classes and test utilities
- `TemplateApplicationTests.kt`: Context load test

## Naming Conventions

**Files:**
- Kotlin files: PascalCase (ClassName.kt) - e.g., `UserController.kt`, `UserService.kt`
- Configuration: lowercase with hyphens (application-profile.yaml) - e.g., `application-dev.yaml`, `application-prod.yaml`
- Test files: {SourceClass}Tests.kt or {SourceClass}Test.kt - e.g., `UserServiceTests.kt`, `UserControllerTest.kt`

**Directories:**
- Package directories: lowercase, reverse domain notation - e.g., `kz/innlab/template/`
- Subdomain packages: lowercase, descriptive - e.g., `controller/`, `service/`, `repository/`, `entity/`, `dto/`
- Test directories: Mirror source structure exactly

**Kotlin Classes:**
- Class names: PascalCase - e.g., `UserController`, `UserService`, `User`
- Interface names: PascalCase, often with I prefix or -able suffix optional - e.g., `UserRepository` (preferred)
- Companion objects: Contain constants and factory methods
- Data classes: For immutable value objects - e.g., `UserDto`, `CreateUserRequest`

**Functions/Methods:**
- Method names: camelCase - e.g., `getUserById()`, `createUser()`, `deleteUserByEmail()`
- Suspend functions: Async operations, name doesn't differ from sync counterpart
- Lambda parameters: Single letter conventional for simple operations - e.g., `users.map { it.name }`

## Where to Add New Code

**New Controller Endpoint:**
- Primary code: `src/main/kotlin/kz/innlab/template/controller/YourController.kt`
- Class pattern: `@RestController` or `@Controller` with `@RequestMapping` on class
- Tests: `src/test/kotlin/kz/innlab/template/controller/YourControllerTests.kt`

**New Service/Business Logic:**
- Implementation: `src/main/kotlin/kz/innlab/template/service/YourService.kt`
- Interface: `src/main/kotlin/kz/innlab/template/service/YourService.kt` (interface inside, implementation can follow)
- Tests: `src/test/kotlin/kz/innlab/template/service/YourServiceTests.kt`

**New Data Entity:**
- JPA Entity: `src/main/kotlin/kz/innlab/template/entity/YourEntity.kt`
- Data Transfer Object: `src/main/kotlin/kz/innlab/template/dto/YourDto.kt`
- Repository: `src/main/kotlin/kz/innlab/template/repository/YourRepository.kt`

**New Configuration Class:**
- Location: `src/main/kotlin/kz/innlab/template/config/YourConfiguration.kt`
- Pattern: `@Configuration` class with `@Bean` methods

**Utilities/Helpers:**
- Location: `src/main/kotlin/kz/innlab/template/util/YourUtil.kt`
- Pattern: Object (Kotlin singleton) with extension functions and helper methods

**Exception Classes:**
- Location: `src/main/kotlin/kz/innlab/template/exception/YourException.kt`
- Pattern: Extend `RuntimeException` for unchecked exceptions

## Special Directories

**src/main/resources/application.yaml:**
- Purpose: Spring Boot configuration
- Generated: No
- Committed: Yes
- Usage: Define server port, application name, database connection, logging levels

**src/main/resources/static/:**
- Purpose: Static web resources
- Generated: No
- Committed: Yes (typically)
- Usage: CSS, JavaScript, images served directly

**src/main/resources/templates/:**
- Purpose: Server-side templates
- Generated: No
- Committed: Yes
- Usage: Thymeleaf templates for traditional MVC views

**target/:**
- Purpose: Maven build output (compiled classes, JARs, etc.)
- Generated: Yes
- Committed: No (in .gitignore)
- Usage: Never edit; automatically created by Maven

**.idea/:**
- Purpose: IntelliJ IDEA IDE configuration
- Generated: Yes
- Committed: No (in .gitignore)
- Usage: IDE-specific settings, never manually edit

**.mvn/wrapper/:**
- Purpose: Maven wrapper scripts and dependencies
- Generated: Yes (once)
- Committed: Yes (recommended)
- Usage: Allows building without installing Maven globally

---

*Structure analysis: 2026-03-01*
