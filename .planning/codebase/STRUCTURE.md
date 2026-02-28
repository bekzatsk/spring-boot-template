# Codebase Structure

**Analysis Date:** 2026-03-01

## Directory Layout

```
spring-boot-template/
├── pom.xml                           # Maven project configuration
├── .gitignore                        # Git ignore rules
├── .env.template                     # Template for environment variables
├── HELP.md                           # Spring Boot generated help
├── mvnw                              # Maven wrapper script (Unix)
├── mvnw.cmd                          # Maven wrapper script (Windows)
├── .idea/                            # IntelliJ IDEA project files
├── .git/                             # Git repository
├── .planning/                        # GSD planning documents
├── .claude/                          # Claude Code workspace
└── src/
    ├── main/
    │   ├── kotlin/                   # Kotlin source code
    │   │   └── kz/innlab/template/   # Application package root
    │   │       └── TemplateApplication.kt
    │   └── resources/
    │       ├── application.yaml      # Spring Boot configuration
    │       ├── static/               # Static assets (CSS, JS, images)
    │       └── templates/            # Thymeleaf templates (if used)
    └── test/
        └── kotlin/                   # Kotlin test source code
            └── kz/innlab/template/
                └── TemplateApplicationTests.kt
```

## Directory Purposes

**Root Directory:**
- Purpose: Project metadata and build configuration
- Contains: Maven POM, wrapper scripts, documentation
- Key files: `pom.xml`

**src/main/kotlin/:**
- Purpose: Compiled Kotlin source code
- Contains: All application source files, organized by package
- Key files: All classes, controllers, services, repositories (when added)

**src/main/kotlin/kz/innlab/template/:**
- Purpose: Application package root; all business logic starts here
- Contains: TemplateApplication.kt plus future controllers, services, repositories
- Key files: `TemplateApplication.kt`

**src/main/resources/:**
- Purpose: Runtime configuration and assets served to clients
- Contains: Application configuration, static files, templates
- Key files: `application.yaml`

**src/main/resources/static/:**
- Purpose: Serves static web assets (CSS, JavaScript, images)
- Contains: Frontend files directly accessible via HTTP
- Key files: None currently; ready for CSS/JS/images

**src/main/resources/templates/:**
- Purpose: Server-side template files (Thymeleaf, Freemarker, etc.)
- Contains: HTML templates for view rendering
- Key files: None currently; ready for template files

**src/test/kotlin/:**
- Purpose: Test source code
- Contains: Unit tests, integration tests using JUnit 5
- Key files: Test classes mirroring production package structure

**src/test/kotlin/kz/innlab/template/:**
- Purpose: Tests for the application package
- Contains: Test classes for TemplateApplication and future classes
- Key files: `TemplateApplicationTests.kt`

## Key File Locations

**Entry Points:**
- `src/main/kotlin/kz/innlab/template/TemplateApplication.kt`: Main application entry point with `main()` function

**Configuration:**
- `src/main/resources/application.yaml`: Spring Boot server and application configuration
- `pom.xml`: Maven build and dependency configuration

**Core Logic:**
- `src/main/kotlin/kz/innlab/template/`: Application package root (future location for controllers, services, repositories)

**Testing:**
- `src/test/kotlin/kz/innlab/template/TemplateApplicationTests.kt`: Basic Spring Boot test template

## Naming Conventions

**Files:**
- `*.kt`: Kotlin source files (follows Java convention)
- `*Application.kt`: Main application class (Spring Boot convention)
- `*Controller.kt`: REST controller classes
- `*Service.kt`: Business logic service classes
- `*Repository.kt`: Data access repository interfaces
- `*Tests.kt` or `*Test.kt`: Test classes

**Directories:**
- `controller/`: Contains HTTP request handlers
- `service/`: Contains business logic
- `repository/`: Contains data access objects
- `model/`: Contains domain entities and DTOs
- `dto/`: Contains data transfer objects
- `util/`: Contains utility/helper classes
- `config/`: Contains Spring configuration classes

**Packages:**
- `kz.innlab.template`: Root package
- `kz.innlab.template.controller`: HTTP controllers
- `kz.innlab.template.service`: Business services
- `kz.innlab.template.repository`: Data repositories
- `kz.innlab.template.model`: Domain models
- `kz.innlab.template.config`: Configuration classes

## Where to Add New Code

**New REST Endpoint:**
1. Create `@RestController` class in `src/main/kotlin/kz/innlab/template/controller/`
2. Define `@GetMapping`, `@PostMapping`, etc. methods
3. Inject `@Service` dependencies
4. Create corresponding test in `src/test/kotlin/kz/innlab/template/` with matching class name

**New Business Service:**
1. Create class with `@Service` annotation in `src/main/kotlin/kz/innlab/template/service/`
2. Implement business logic
3. Inject `@Repository` dependencies as needed
4. Write unit tests in `src/test/kotlin/kz/innlab/template/` directory

**New Data Repository:**
1. Create interface extending Spring Data `Repository` in `src/main/kotlin/kz/innlab/template/repository/`
2. Define query methods
3. Spring Data generates implementation automatically
4. Write tests if custom query logic needed

**New Domain Model/Entity:**
1. Create Kotlin data class in `src/main/kotlin/kz/innlab/template/model/`
2. Add JPA/Hibernate annotations for persistence
3. Define properties and relationships

**Utility/Helper Classes:**
- Location: `src/main/kotlin/kz/innlab/template/util/`
- Scope: Shared utilities across services and controllers

**Configuration Classes:**
- Location: `src/main/kotlin/kz/innlab/template/config/`
- Purpose: Spring `@Configuration` classes for custom beans

## Special Directories

**target/:**
- Purpose: Maven build output directory
- Generated: Yes (automatic during build)
- Committed: No (in .gitignore)
- Contents: Compiled classes, JARs, artifacts

**.idea/:**
- Purpose: IntelliJ IDEA project metadata
- Generated: Yes (IntelliJ automatic)
- Committed: No (in .gitignore)
- Contents: IDE settings, workspace state

**.mvn/wrapper/:**
- Purpose: Maven wrapper configuration
- Generated: No (checked in for reproducible builds)
- Committed: Conditionally (wrapper JARs excluded in .gitignore)
- Contents: Maven distribution bootstrap

**src/main/resources/static/:**
- Purpose: Static web assets
- Generated: No
- Committed: Yes
- Contents: CSS, JavaScript, images, fonts

**src/main/resources/templates/:**
- Purpose: Server-rendered HTML templates
- Generated: No
- Committed: Yes
- Contents: Thymeleaf/Freemarker template files

---

*Structure analysis: 2026-03-01*
