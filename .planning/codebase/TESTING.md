# Testing Patterns

**Analysis Date:** 2026-03-01

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) - defined via Spring Boot starter parent
- Spring Boot Test framework - for integration testing with Spring context
- Kotlin Test JUnit5 - Kotlin-specific test utilities

**Config:**
- `pom.xml` defines test dependencies:
  - `spring-boot-starter-webmvc-test` (Spring Boot testing)
  - `kotlin-test-junit5` (Kotlin testing)
- Maven `testSourceDirectory` configured: `src/test/kotlin`

**Run Commands:**
```bash
mvn test                    # Run all tests
mvn test -Dtest=ClassName  # Run specific test class
mvn test -DfailIfNoTests=false  # Run with tolerance for empty suites
```

## Test File Organization

**Location:**
- Co-located convention: Tests are in separate `src/test/kotlin` directory matching main source structure
- Package-parallel structure: `src/test/kotlin/kz/innlab/template/` mirrors `src/main/kotlin/kz/innlab/template/`

**Naming:**
- Test classes follow pattern: `[ClassName]Tests.kt`
- Example: `TemplateApplicationTests.kt` tests `TemplateApplication.kt`

**Structure:**
```
src/test/kotlin/
├── kz/
│   └── innlab/
│       └── template/
│           └── TemplateApplicationTests.kt
```

## Test Structure

**Suite Organization:**
```kotlin
@SpringBootTest
class TemplateApplicationTests {

	@Test
	fun contextLoads() {
	}

}
```

**Patterns:**
- Class-level annotation: `@SpringBootTest` - loads full Spring application context for integration testing
- Method-level annotation: `@Test` - JUnit 5 marker for test methods
- Method naming uses camelCase with descriptive action: `contextLoads()`
- Setup/Teardown: Not observed in current tests

## Mocking

**Framework:** Not detected in current codebase

**Patterns:**
- Current test uses Spring Boot context loading without explicit mocking
- Spring Test framework provides automatic mocking capabilities if needed

**What to Mock:**
- External service calls (if present)
- Database dependencies
- HTTP clients

**What NOT to Mock:**
- Spring configuration and auto-wiring
- Spring beans (use `@MockBean` if isolation needed)

## Fixtures and Factories

**Test Data:**
- Not implemented in current codebase

**Location:**
- Would be placed in `src/test/kotlin/kz/innlab/template/` alongside test classes
- Could use separate `fixtures` or `builders` subdirectory for shared test data

## Coverage

**Requirements:** No coverage requirements detected

**View Coverage:**
```bash
mvn test jacoco:report  # Requires JaCoCo plugin in pom.xml
```

## Test Types

**Unit Tests:**
- Not present in current codebase
- Should be added for individual components (services, utilities)
- Location: `src/test/kotlin/kz/innlab/template/[module]/[ClassName]Tests.kt`

**Integration Tests:**
- `@SpringBootTest` annotation indicates integration test approach
- `TemplateApplicationTests.kt` is an integration test verifying Spring context loads correctly
- These tests load the full application context

**E2E Tests:**
- Not detected
- Not implemented in current codebase
- Could use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` for E2E scenarios

## Common Patterns

**Async Testing:**
- Not demonstrated in current tests
- Would use `@Async` and `CompletableFuture` or Kotlin coroutines
- JUnit 5 handles async via lambdas and CompletableFuture

**Error Testing:**
- Not demonstrated in current tests
- Should use `assertThrows()` for expected exceptions:
```kotlin
@Test
fun testException() {
    assertThrows<IllegalArgumentException> {
        // code that throws
    }
}
```

**Assertions:**
- Should use JUnit 5 assertions: `assertEquals()`, `assertTrue()`, `assertNotNull()`
- Can extend with AssertJ for fluent assertions (not currently in pom.xml)

## Testing Best Practices

**Spring Boot Test Configuration:**
- Current approach uses `@SpringBootTest` for context loading
- For faster unit tests, use `@DataJpaTest`, `@WebMvcTest`, or `@JsonTest` for specific slices
- Mock external services with `@MockBean`

**Kotlin-Specific:**
- Leverage Kotlin's null safety in test assertions
- Use Kotlin's readability features for test names with backticks if needed:
```kotlin
@Test
fun `context should load successfully`() {
}
```

---

*Testing analysis: 2026-03-01*
