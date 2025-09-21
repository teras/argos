# Validation

Argos provides comprehensive validation capabilities to ensure command-line arguments meet your application's requirements. Validation happens after type conversion and provides clear error messages to users.

## Basic Validation

Validate single values using predicates:

```kotlin
class MyApp : Arguments() {
    val email by option("--email")
        .validate("Must be a valid email address") { it?.contains("@") == true }

    val port by option("--port").int()
        .validate("Port must be in valid range") { it in 1..65535 }

    val percentage by option("--load").double()
        .validate("Percentage must be between 0 and 100") { it in 0.0..100.0 }
}
```

### Usage Examples

```bash
# Valid inputs
myapp --email user@example.com --port 8080 --load 75.5

# Invalid inputs generate clear errors
myapp --email invalid-email
# Error: Invalid value for --email: 'invalid-email' - Must be a valid email address

myapp --port 99999
# Error: Invalid value for --port: '99999' - Port must be in valid range
```

## Multiple Validations

Apply multiple validation rules to the same option:

```kotlin
class MyApp : Arguments() {
    // Method 1: Multiple validate() calls
    val username by option("--username")
        .validate("Username must be at least 3 characters") { it?.length?.let { len -> len >= 3 } == true }
        .validate("Username must contain only alphanumeric characters") {
            it?.all { c -> c.isLetterOrDigit() } == true
        }

    // Method 2: Multiple validations at once
    val password by option("--password")
        .validate(
            "Password must be at least 8 characters" to { it?.length?.let { len -> len >= 8 } == true },
            "Password must contain uppercase letter" to { it?.any { c -> c.isUpperCase() } == true },
            "Password must contain lowercase letter" to { it?.any { c -> c.isLowerCase() } == true },
            "Password must contain digit" to { it?.any { c -> c.isDigit() } == true }
        )
}
```

### Error Messages

When validation fails, users see specific error messages:

```bash
myapp --username ab
# Error: Invalid value for --username: 'ab' - Username must be at least 3 characters

myapp --password simple
# Error: Invalid value for --password: 'simple' - Password must contain uppercase letter
# Error: Invalid value for --password: 'simple' - Password must contain digit
```

## Collection Validation

### Per-Element Validation

Validate each element in a collection:

```kotlin
class MyApp : Arguments() {
    val ports by option("--port").int().list()
        .validate("Each port must be in valid range") { it in 1..65535 }

    val emails by option("--email").list()
        .validate("Each email must be valid") { it.contains("@") }

    val files by option("--file").list()
        .validate("File must exist") { File(it).exists() }
        .validate("File must be readable") { File(it).canRead() }
}
```

### Collection-Wide Validation

Validate the entire collection:

```kotlin
class MyApp : Arguments() {
    val servers by option("--server").list()
        .validate("Server name must be valid") { it.isNotBlank() }           // Per-element
        .validateCollection("Must have 1-5 servers") { it.size in 1..5 }     // Collection-wide

    val categories by option("--category").set()
        .validateCollection("Must include 'core' category") { "core" in it }
        .validateCollection("Cannot exceed 10 categories") { it.size <= 10 }

    val priorities by option("--priority").int().list()
        .validate("Priority must be 1-10") { it in 1..10 }                   // Per-element
        .validateCollection("Priorities must be unique") { it.toSet().size == it.size }  // No duplicates
}
```

## Advanced Validation Patterns

### File System Validation

```kotlin
import java.io.File

class MyApp : Arguments() {
    val inputFile by option("--input")
        .validate("Input file must exist") { it?.let { File(it).exists() } == true }
        .validate("Input file must be readable") { it?.let { File(it).canRead() } == true }
        .validate("Input file must not be empty") { it?.let { File(it).length() > 0 } == true }

    val outputDir by option("--output-dir")
        .validate("Output directory must exist") { it?.let { File(it).isDirectory } == true }
        .validate("Output directory must be writable") { it?.let { File(it).canWrite() } == true }

    val configFile by option("--config")
        .validate("Config file must be JSON or YAML") { path ->
            path?.let {
                it.endsWith(".json") || it.endsWith(".yml") || it.endsWith(".yaml")
            } == true
        }
}
```

### Network Validation

```kotlin
import java.net.URL

class MyApp : Arguments() {
    val endpoint by option("--endpoint")
        .validate("Must be a valid URL") { url ->
            url?.let {
                try { URL(it); true } catch (e: Exception) { false }
            } == true
        }
        .validate("Must use HTTPS") { it?.startsWith("https://") == true }

    val hostPort by option("--host-port")
        .validate("Must be in host:port format") { it?.contains(":") == true }
        .validate("Port must be valid") { hostPort ->
            hostPort?.let {
                val parts = it.split(":")
                if (parts.size == 2) {
                    val port = parts[1].toIntOrNull()
                    port != null && port in 1..65535
                } else false
            } == true
        }
}
```

### Business Logic Validation

```kotlin
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MyApp : Arguments() {
    val startDate by option("--start-date")
        .map("date in YYYY-MM-DD format") {
            it?.let {
                try { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
                catch (e: Exception) { null }
            }
        }
        .validate("Start date cannot be in the past") {
            it?.isAfter(LocalDate.now().minusDays(1)) == true
        }

    val endDate by option("--end-date")
        .map("date in YYYY-MM-DD format") {
            it?.let {
                try { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
                catch (e: Exception) { null }
            }
        }

    val budget by option("--budget").double()
        .validate("Budget must be positive") { it > 0 }
        .validate("Budget cannot exceed $1,000,000") { it <= 1_000_000.0 }

    val teamSize by option("--team-size").int()
        .validate("Team size must be reasonable") { it in 1..50 }
}
```

### Cross-Field Validation

For validation that depends on multiple fields, use custom validation in your application logic:

```kotlin
class ProjectApp : Arguments() {
    val startDate by option("--start-date")
        .map("date in YYYY-MM-DD format") {
            it?.let { LocalDate.parse(it) }
        }

    val endDate by option("--end-date")
        .map("date in YYYY-MM-DD format") {
            it?.let { LocalDate.parse(it) }
        }

    val budget by option("--budget").double()
    val teamSize by option("--team-size").int()

    // Custom validation method
    fun validateCrossFields(): List<String> {
        val errors = mutableListOf<String>()

        // Date validation
        if (startDate != null && endDate != null && startDate!! >= endDate!!) {
            errors.add("End date must be after start date")
        }

        // Budget per person validation
        if (budget != null && teamSize != null && teamSize!! > 0) {
            val budgetPerPerson = budget!! / teamSize!!
            if (budgetPerPerson < 1000) {
                errors.add("Budget per team member is too low (minimum $1,000)")
            }
        }

        return errors
    }
}

fun main(args: Array<String>) {
    val app = ProjectApp().parse(args) ?: return

    // Perform cross-field validation
    val crossFieldErrors = app.validateCrossFields()
    if (crossFieldErrors.isNotEmpty()) {
        crossFieldErrors.forEach { println("Error: $it") }
        return
    }

    // Continue with application logic...
}
```

## Validation Error Messages

### Template Variables

Validation error messages support template variables:

```kotlin
class MyApp : Arguments() {
    val port by option("--port", "-p").int()
        .validate("Invalid value for {name}: {value} - Port must be 1-65535") {
            it in 1..65535
        }

    val config by option("--config").list()
        .validate("{switches} option: '{value}' is not a valid config file") {
            it.endsWith(".json") || it.endsWith(".yaml")
        }
}
```

Available template variables:
- `{name}`: Property name
- `{value}`: The invalid value (formatted appropriately)
 - `{switches}`: Option switches (e.g., "--port|-p")

### Custom Error Messages

```kotlin
class MyApp : Arguments() {
    val apiKey by option("--api-key")
        .validate("API key must be exactly 32 characters long") {
            it?.length == 32
        }
        .validate("API key must contain only hexadecimal characters") {
            it?.all { c -> c in "0123456789abcdefABCDEF" } == true
        }

    val retryCount by option("--retries").int()
        .validate("Retry count must be reasonable (0-10)") {
            it in 0..10
        }
}
```

## Complex Validation Examples

### Build Configuration Validator

```kotlin
enum class BuildType { DEBUG, RELEASE, PROFILE }

class BuildConfig : Arguments() {
    val buildType by option("--type").enum<BuildType>().default(BuildType.DEBUG)

    val optimizationLevel by option("--optimization", "-O").int()
        .validate("Optimization level must be 0-3") { it in 0..3 }

    val sourceFiles by option("--source").list()
        .validate("Source file must exist") { File(it).exists() }
        .validate("Source file must be .cpp or .c file") {
            it.endsWith(".cpp") || it.endsWith(".c") || it.endsWith(".cc")
        }
        .validateCollection("At least one source file required") { it.isNotEmpty() }

    val includePaths by option("--include", "-I").list()
        .validate("Include path must exist") { File(it).isDirectory }
        .validate("Include path must be readable") { File(it).canRead() }

    val defines by option("--define", "-D").list()
        .validate("Define must be in KEY=VALUE format") {
            it.contains("=") && it.split("=").size == 2
        }
        .validate("Define key must be valid C identifier") {
            val key = it.split("=")[0]
            key.isNotEmpty() && key[0].isLetter() && key.all { c -> c.isLetterOrDigit() || c == '_' }
        }

    val warningLevel by option("--warnings").int().default(2)
        .validate("Warning level must be 0-4") { it in 0..4 }

    val maxErrors by option("--max-errors").int().default(50)
        .validate("Max errors must be positive") { it > 0 }
        .validate("Max errors should be reasonable") { it <= 1000 }
}
```

### Database Connection Validator

```kotlin
class DatabaseConfig : Arguments() {
    val host by option("--host").default("localhost")
        .validate("Host must not be empty") { it.isNotBlank() }

    val port by option("--port").int().default(5432)
        .validate("Port must be valid") { it in 1..65535 }

    val database by option("--database").required()
        .validate("Database name must be valid") {
            it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c in "_-" }
        }

    val username by option("--username").required()
        .validate("Username must not be empty") { it.isNotBlank() }

    val password by option("--password").input(hidden = true)
        .validate("Password must be at least 8 characters") { it.length >= 8 }

    val connectionTimeout by option("--timeout").int().default(30)
        .validate("Timeout must be positive") { it > 0 }
        .validate("Timeout should be reasonable") { it <= 300 }

    val sslMode by option("--ssl-mode").oneOf("disable", "require", "verify-ca", "verify-full")
        .default("require")

    val poolSize by option("--pool-size").int().default(10)
        .validate("Pool size must be positive") { it > 0 }
        .validate("Pool size should be reasonable") { it <= 100 }
}
```

## Best Practices

### 1. Provide Clear, Actionable Messages

```kotlin
// Good: Specific, actionable error messages
val port by option("--port").int()
    .validate("Port must be between 1 and 65535") { it in 1..65535 }

val email by option("--email")
    .validate("Email must contain @ symbol") { it?.contains("@") == true }

// Avoid: Vague or confusing messages
val port by option("--port").int()
    .validate("Invalid port") { it in 1..65535 }
```

### 2. Validate Early and Often

```kotlin
// Good: Validate inputs as soon as they're parsed
val configFile by option("--config")
    .validate("Config file must exist") { File(it ?: "").exists() }
    .validate("Config file must be readable") { File(it ?: "").canRead() }

// Better: Let the application fail later with unclear errors
```

### 3. Use Appropriate Validation Level

```kotlin
// Good: Essential validations in CLI parsing
val port by option("--port").int()
    .validate("Port must be valid") { it in 1..65535 }

// Good: Business logic validation in application
fun validateProjectConfiguration(config: ProjectConfig) {
    // Complex business rules here
}

// Avoid: All validation in CLI layer (makes it complex)
// Avoid: No validation in CLI layer (poor user experience)
```

### 4. Consider User Experience

```kotlin
// Good: Help users understand what's expected
val retries by option("--retries").int()
    .validate("Retries must be 0-10 (0 = no retries, 10 = maximum)") {
        it in 0..10
    }

// Good: Accept common variations
val boolValue by option("--enabled")
    .validate("Must be true/false, yes/no, on/off, or 1/0") { value ->
        value?.lowercase() in listOf("true", "false", "yes", "no", "on", "off", "1", "0")
    }
```

Validation ensures your CLI application receives valid inputs and provides clear feedback when users make mistakes. Design validation rules that match your application's requirements while providing helpful guidance to users.
