# Coding Conventions

**Analysis Date:** 2026-03-01

## Language & Build

**Primary Language:** Kotlin 2.2.21
**Target JVM:** Java 24
**Build Tool:** Maven 3.x (via mvnw)
**Build Source Directories:**
- Main: `src/main/kotlin`
- Test: `src/test/kotlin`

## Naming Patterns

**Package Names:**
- Reverse domain convention: `kz.innlab.template`
- All lowercase with no underscores
- Example: `kz.innlab.template`

**Files:**
- Match Kotlin class names (PascalCase)
- One public class per file when possible
- Application entry point: `TemplateApplication.kt`
- Test files mirror source structure with `Tests` suffix: `TemplateApplicationTests.kt`

**Classes:**
- PascalCase (UpperCamelCase)
- Example: `TemplateApplication`, `TemplateApplicationTests`

**Functions:**
- camelCase (lowerCamelCase) in Kotlin style
- Main entry function: `main(args: Array<String>)`

**Variables:**
- camelCase (lowerCamelCase)
- Example: `args`

## Kotlin Conventions

**Spring Integration:**
- Use Spring Boot starter conventions with Kotlin
- Kotlin `all-open` compiler plugin enabled for Spring proxying (configured in `pom.xml`)
- JSR-305 strict null-checking enabled via Kotlin compiler args: `-Xjsr305=strict`
- Annotation default target set to param-property: `-Xannotation-default-target=param-property`

**Class & Function Style:**
- Use Kotlin's concise syntax where appropriate
- Spring Boot Kotlin conventions: make Spring-annotated classes open (handled by all-open plugin)
- Single-expression functions preferred when reasonable

**null Safety:**
- Strict JSR-305 enforcement applied
- Use Kotlin's nullable types (`Type?`) explicitly
- Non-null types are default

## Code Organization

**Imports:**
1. Package declaration at top
2. Spring Boot imports
3. Kotlin standard library imports
4. Other imports

**Example from `TemplateApplication.kt`:**
```kotlin
package kz.innlab.template

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TemplateApplication

fun main(args: Array<String>) {
	runApplication<TemplateApplication>(*args)
}
```

## Spring Boot Patterns

**Application Class:**
- Single `@SpringBootApplication` annotated class: `src/main/kotlin/kz/innlab/template/TemplateApplication.kt`
- Standard `main()` function using `runApplication<T>()` DSL

**Configuration Files:**
- YAML format for application properties: `src/main/resources/application.yaml`
- Server configuration: port specified (7070)
- Application name specified in config

**Resource Directories:**
- Static files: `src/main/resources/static/`
- Templates: `src/main/resources/templates/`
- Properties/YAML: `src/main/resources/`

## Code Style Practices

**Indentation:**
- Tab characters (configured in `.gitattributes`)
- Maven wrapper scripts: LF line endings (`mvnw`)
- Windows batch: CRLF line endings (`mvnw.cmd`)

**Formatting:**
- No explicit code formatter configured (relies on IDE defaults or manual formatting)
- IDE: IntelliJ IDEA configuration present (`.idea/` directory)
- Kotlin Maven plugin handles compilation

**Comments:**
- Minimal comments observed in current code
- Self-documenting code preferred
- Consider KDoc for public APIs when expanded

## Error Handling

**Spring Boot Default:**
- No explicit error handling visible in current minimal code
- Spring Boot provides default exception handling
- Consider adding custom `@ControllerAdvice` for consistent error responses as application grows

**Null Safety:**
- Leverage Kotlin's type system and JSR-305 strict checking
- Avoid null in function signatures where possible
- Use optional types explicitly

## Dependency Management

**Maven Configuration:**
- Parent: `spring-boot-starter-parent:4.0.3`
- Transitive dependency management handled by parent
- Jackson Kotlin module configured for serialization
- PostgreSQL driver included (runtime scope)

## IDE Configuration

**IntelliJ IDEA:**
- Project uses IntelliJ IDEA configuration (`.idea/` directory)
- Kotlin compiler settings: `kotlinc.xml` configured for Maven
- Compiler options: `-parameters` for Spring parameter introspection

---

*Convention analysis: 2026-03-01*
