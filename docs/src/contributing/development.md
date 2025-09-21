# Development Setup

This guide will help you set up a development environment for contributing to Argos.

## Prerequisites

- **JDK 11+**: For JVM development and testing
- **Git**: For version control
- **IDE**: IntelliJ IDEA (recommended) or any Kotlin-compatible IDE

### Platform-Specific Tools

For native targets, you'll need:

- **Linux**: GCC toolchain, development headers
- **Windows**: MinGW-w64 or Visual Studio Build Tools
- **macOS**: Xcode Command Line Tools (if targeting macOS in the future)

## Repository Setup

1. **Fork the repository** on GitHub
2. **Clone your fork**:
   ```bash
   git clone https://github.com/teras/argos.git
   cd argos
   ```

3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/teras/argos.git
   ```

4. **Install Git hooks** (optional but recommended):
   ```bash
   cp scripts/pre-commit.sh .git/hooks/pre-commit
   chmod +x .git/hooks/pre-commit
   ```

## Build System

Argos uses Gradle with Kotlin Multiplatform. The build is configured for:

- **JVM**: Java 11+ compatible bytecode
- **Native Linux**: x64 and ARM64 targets
- **Windows**: MinGW-w64 target
- **Common**: Shared multiplatform code

### Key Build Commands

```bash
# Build all targets
./gradlew build

# Run tests
./gradlew test

# Build specific targets
./gradlew jvmTest              # JVM tests only
./gradlew linuxX64Test         # Linux x64 native tests
./gradlew compileKotlinJvm     # JVM compilation only

# Format code
./gradlew ktlintFormat

# Check code style
./gradlew ktlintCheck

# Generate documentation
./gradlew dokkaHtml
```

## Project Structure

```
argos/
├── build.gradle.kts           # Main build configuration
├── settings.gradle.kts        # Gradle settings
├── src/
│   ├── commonMain/kotlin/     # Shared multiplatform code
│   │   └── onl/ycode/argos/   # Main library code
│   ├── commonTest/kotlin/     # Shared tests
│   ├── jvmMain/kotlin/        # JVM-specific code
│   ├── jvmTest/kotlin/        # JVM-specific tests
│   ├── posixMain/kotlin/      # POSIX-specific code (Linux)
│   ├── mingwX64Main/kotlin/   # Windows-specific code
│   └── nativeTest/kotlin/     # Native-specific tests
├── docs/                      # Documentation (MkDocs)
├── scripts/                   # Build and utility scripts
└── examples/                  # Example applications
```

### Source Set Hierarchy

```
commonMain
├── jvmMain
├── nativeMain
│   ├── posixMain
│   │   └── linuxMain
│   │       ├── linuxX64Main
│   │       └── linuxArm64Main
│   └── mingwX64Main
└── commonTest
    ├── jvmTest
    └── nativeTest
```

## IDE Setup

### IntelliJ IDEA

1. **Open the project**: Use "Open" and select the `build.gradle.kts` file
2. **Import Gradle project**: Let IDEA import and sync the project
3. **Configure Kotlin plugin**: Should be enabled by default
4. **Set up code style**:
   - Go to Settings > Editor > Code Style > Kotlin
   - Import the provided code style from `scripts/kotlin-style.xml`

### Useful IDEA Plugins

- **Kotlin Multiplatform Mobile**: For multiplatform development
- **Gradle**: For build system integration
- **GitToolBox**: Enhanced Git integration
- **Rainbow Brackets**: Makes nested structures easier to read

## Code Organization

### Core Components

- **Arguments.kt**: Main DSL class and parsing engine
- **Builders**: Option, positional, and domain builders
- **Help.kt**: Help text generation and formatting
- **Completions.kt**: Shell completion script generation
- **Terminal/**: Output formatting system
- **OsBound.kt**: Platform abstraction layer

### Key Design Patterns

1. **Property Delegation**: Options use Kotlin property delegates
2. **Builder Pattern**: Fluent configuration with method chaining
3. **Type Safety**: Strong typing with compile-time validation
4. **Multiplatform**: Platform-specific code isolated in expect/actual

### Coding Standards

#### Kotlin Style

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// Good: Standard Kotlin naming
class ArgumentParser {
    fun parseArguments(args: Array<String>): ParseResult {
        // Implementation
    }
}

// Property naming
val inputFile by option("--input-file")
val maxRetries by option("--max-retries")

// Function naming
fun validateOptions(): ValidationResult
fun formatErrorMessage(error: ParseError): String
```

#### Documentation

All public APIs must have KDoc documentation:

```kotlin
/**
 * Parses command-line arguments and returns the result.
 *
 * This method processes the provided arguments according to the configured
 * options and validation rules. It returns a [ParseResult] indicating
 * success, help request, or error.
 *
 * @param args Command-line arguments to parse
 * @return Parse result with success, help, or error status
 * @throws IllegalStateException if the parser is not properly configured
 */
fun parse(args: Array<String>): ParseResult
```

#### Error Handling

Use structured error handling with meaningful messages:

```kotlin
// Good: Structured error with context
sealed class ParseError(val message: String) {
    class InvalidValue(
        val option: String,
        val value: String,
        val expected: String
    ) : ParseError("Invalid value '$value' for $option (expected $expected)")
}

// Avoid: Generic exceptions
throw Exception("Something went wrong")
```

## Testing

### Test Structure

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test complete parsing scenarios
- **Multiplatform Tests**: Ensure behavior is consistent across platforms

### Writing Tests

Use Kotlin Test for multiplatform testing:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgumentsTest {
    @Test
    fun testBasicOptionParsing() {
        class TestApp : Arguments() {
            val verbose by option("--verbose", "-v")
            val name by option("--name").required()
        }

        val app = TestApp()
        val result = app.parse(arrayOf("--verbose", "--name", "test"))

        assertEquals(Arguments.ParseResult.Success, result)
        assertEquals(true, app.verbose)
        assertEquals("test", app.name)
    }

    @Test
    fun testMissingRequiredOption() {
        class TestApp : Arguments() {
            val name by option("--name").required()
        }

        val app = TestApp()
        val result = app.parse(arrayOf())

        assertEquals(Arguments.ParseResult.Error, result)
    }
}
```

### Running Tests

```bash
# All tests
./gradlew test

# JVM tests only
./gradlew jvmTest

# Native tests
./gradlew linuxX64Test
./gradlew mingwX64Test

# Specific test class
./gradlew test --tests ArgumentsTest

# With verbose output
./gradlew test --info
```

## Platform-Specific Development

### JVM Development

JVM code is in `src/jvmMain/kotlin/`:

```kotlin
// src/jvmMain/kotlin/onl/ycode/argos/OsBound.jvm.kt
actual object OsBound {
    actual fun getenv(name: String): String? = System.getenv(name)
    actual fun termWidth(): Int = // JVM-specific terminal width detection
}
```

### Native Development

Native code is in platform-specific directories:

```kotlin
// src/posixMain/kotlin/onl/ycode/argos/OsBound.posix.kt
actual object OsBound {
    actual fun getenv(name: String): String? = getenv(name)
    // Uses POSIX C functions
}
```

### Windows Development

Windows-specific code uses MinGW:

```kotlin
// src/mingwX64Main/kotlin/onl/ycode/argos/OsBound.mingwX64.kt
actual object OsBound {
    actual fun getenv(name: String): String? = _wgetenv(name)
    // Uses Windows API functions
}
```

## Debugging

### Debug Builds

Enable debug information:

```bash
# Debug build
./gradlew build -Pdebug=true

# Debug tests with verbose output
./gradlew test --debug-jvm
```

### Logging

Use structured logging for debugging:

```kotlin
// Debug output in tests
@Test
fun testComplexScenario() {
    val app = TestApp()
    println("Testing with args: ${args.joinToString()}")

    val result = app.parse(args)
    println("Parse result: $result")

    // Assertions...
}
```

## Performance

### Benchmarking

Basic performance testing:

```kotlin
@Test
fun testParsingPerformance() {
    val app = ComplexApp()
    val args = generateLargeArgSet()

    val startTime = System.nanoTime()
    repeat(1000) {
        app.parse(args)
    }
    val endTime = System.nanoTime()

    val avgTime = (endTime - startTime) / 1000000.0 / 1000.0
    println("Average parsing time: ${avgTime}ms")

    // Assert reasonable performance
    assertTrue(avgTime < 1.0, "Parsing should take less than 1ms on average")
}
```

### Memory Usage

Monitor memory usage in tests:

```kotlin
@Test
fun testMemoryUsage() {
    val runtime = Runtime.getRuntime()

    runtime.gc()
    val beforeMemory = runtime.totalMemory() - runtime.freeMemory()

    // Create many parser instances
    repeat(1000) {
        val app = TestApp()
        app.parse(testArgs)
    }

    runtime.gc()
    val afterMemory = runtime.totalMemory() - runtime.freeMemory()

    val memoryUsed = afterMemory - beforeMemory
    println("Memory used: ${memoryUsed / 1024}KB")
}
```

## Contributing Workflow

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/my-new-feature
   ```

2. **Make your changes** following the coding standards

3. **Add tests** for new functionality

4. **Run the full test suite**:
   ```bash
   ./gradlew test
   ./gradlew ktlintCheck
   ```

5. **Update documentation** if needed

6. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Add: Description of your changes"
   ```

7. **Push and create a Pull Request**:
   ```bash
   git push origin feature/my-new-feature
   ```

## Common Development Tasks

### Adding a New Option Type

1. **Add conversion function**:
   ```kotlin
   fun OptionBuilder<String?>.uuid() = map(desc = "UUID") { s ->
       s?.let {
           try { UUID.fromString(it) }
           catch (e: IllegalArgumentException) { null }
       }
   }
   ```

2. **Add tests**:
   ```kotlin
   @Test
   fun testUuidOption() {
       class TestApp : Arguments() {
           val id by option("--id").uuid()
       }

       val app = TestApp()
       app.parse(arrayOf("--id", "123e4567-e89b-12d3-a456-426614174000"))

       assertNotNull(app.id)
       assertEquals("123e4567-e89b-12d3-a456-426614174000", app.id.toString())
   }
   ```

3. **Update documentation**

### Adding Platform-Specific Functionality

1. **Add to common interface**:
   ```kotlin
   // src/commonMain/kotlin/onl/ycode/argos/OsBound.kt
   expect object OsBound {
       fun newFunction(): String
   }
   ```

2. **Implement for each platform**:
   ```kotlin
   // src/jvmMain/kotlin/onl/ycode/argos/OsBound.jvm.kt
   actual object OsBound {
       actual fun newFunction(): String = "JVM implementation"
   }
   ```

3. **Add tests for all platforms**

## Getting Help

- **GitHub Issues**: For bug reports and feature requests
- **GitHub Discussions**: For questions and design discussions
- **Code Review**: All PRs receive thorough code review
- **Documentation**: Keep docs updated with your changes

The Argos development process emphasizes code quality, comprehensive testing, and maintaining multiplatform compatibility. Take time to understand the existing patterns before making changes.
