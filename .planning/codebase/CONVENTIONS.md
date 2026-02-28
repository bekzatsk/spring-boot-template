# Coding Conventions

**Analysis Date:** 2026-03-01

## Naming Patterns

**Files:**
- Kotlin source files use PascalCase: `TemplateApplication.kt`
- Main application class follows pattern: `[ProjectName]Application.kt`
- Test files follow pattern: `[ClassName]Tests.kt`
- Package naming uses reversed domain convention: `kz.innlab.template`

**Functions:**
- Top-level functions use camelCase: `main()`, `runApplication()`
- Kotlin idiomatic style observed in function calls: `runApplication<TemplateApplication>(*args)`

**Classes:**
- PascalCase naming: `TemplateApplication`
- Spring Boot application classes use suffix `Application`: `TemplateApplication`

**Variables:**
- Implicit from limited codebase; Spring convention observed in type inference with Kotlin

**Types:**
- Generic type parameters use PascalCase in angle brackets: `Array<String>`, `SpringBootTest`
- Spring annotations follow standard convention: `@SpringBootApplication`, `@SpringBootTest`

## Code Style

**Formatting:**
- YAML indentation (2 spaces) observed in `application.yaml`
- No explicit formatter configuration detected (likely using IntelliJ IDEA defaults)

**Linting:**
- No linting tool configuration detected
- No `.editorconfig`, `ktlint`, or `detekt` configuration present
- Code follows Kotlin idioms by convention

## Import Organization

**Order:**
1. Package declaration
2. Standard library and framework imports: `import org.springframework.boot...`
3. Kotlin standard library: `import org.jetbrains.kotlin...`
4. JUnit/Testing framework: `import org.junit.jupiter.api...`

**Path Aliases:**
- Not detected; using full qualified imports

## Error Handling

**Patterns:**
- Not extensively demonstrated in minimal codebase
- Test context loads without explicit error handling: `contextLoads()` method is empty
- Spring Boot auto-configuration handles initialization errors

## Logging

**Framework:** Not detected in current codebase

**Patterns:**
- No logging configuration observed
- Relying on Spring Boot's default logging

## Comments

**When to Comment:**
- Minimal commenting observed in current codebase
- Self-documenting code with clear naming preferred

**JavaDoc/KDoc:**
- No KDoc documentation detected
- Not required for simple entry points and test classes

## Function Design

**Size:**
- Functions kept minimal: `main()` is 2 lines, `contextLoads()` is empty
- Top-level functions used when appropriate: `fun main(args: Array<String>)`

**Parameters:**
- Function parameters explicitly typed: `args: Array<String>`
- Varargs unpacking used: `*args`

**Return Values:**
- Type inference leveraged where clear: `fun main()` returns Unit (implicit)
- Explicit generic type parameters when calling generic functions: `runApplication<TemplateApplication>(*args)`

## Module Design

**Exports:**
- Minimal public API; main entry point exposed
- Class marked as open for Spring proxying via `all-open` compiler plugin

**Barrel Files:**
- Not applicable to current project structure

## Configuration

**Build Configuration:**
- Maven used as build tool (`pom.xml`)
- Kotlin Maven plugin configured with Spring compiler plugin
- JAR-based source/test directories configured: `src/main/kotlin` and `src/test/kotlin`

**Spring Configuration:**
- Minimal `application.yaml` with server port (7070) and application name
- Relying on Spring Boot auto-configuration for sensible defaults

---

*Convention analysis: 2026-03-01*
