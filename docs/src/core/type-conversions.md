# Type Conversions

Argos provides automatic type conversion from command-line strings to Kotlin types. This enables type-safe CLI interfaces with compile-time guarantees.

## Numeric Types

Convert string arguments to numeric types:

```kotlin
class MyApp : Arguments() {
    // Integer conversion
    val count by option("--count").int()                 // Int?
    val port by option("--port").int().default(8080)    // Int

    // Long conversion
    val size by option("--size").long()                  // Long?
    val maxMemory by option("--memory").long().default(1024L) // Long

    // Floating point conversion
    val rate by option("--rate").float()                 // Float?
    val precision by option("--precision").double()      // Double?
}
```

### Usage Examples

```bash
myapp --count 42 --rate 3.14 --size 1048576
```

### Error Handling

Invalid numeric inputs generate clear error messages:

```bash
myapp --count abc
# Error: Invalid value for --count: 'abc' is not a valid integer
```

## Boolean Types

Convert to boolean values with intelligent parsing:

```kotlin
class MyApp : Arguments() {
    // Basic boolean
    val verbose by option("--verbose").bool()            // Boolean?
    val debug by option("--debug").bool().default(false) // Boolean
}
```

### Boolean Value Recognition

Argos recognizes multiple boolean representations:

| True Values | False Values |
|-------------|--------------|
| `true` | `false` |
| `yes` | `no` |
| `on` | `off` |
| `1` | `0` |

```bash
# All equivalent to --verbose=true
myapp --verbose true
myapp --verbose yes
myapp --verbose on
myapp --verbose 1

# Flag form (no value needed)
myapp --verbose
```

### Negatable Booleans

Create options that auto-generate negation switches:

```kotlin
class MyApp : Arguments() {
    val cache by option("--cache").bool().negatable().default(true)
    val colors by option("--colors").bool().negatable("disable-").default(true)
}
```

```bash
# User can specify
myapp --cache          # Sets cache = true
myapp --no-cache       # Sets cache = false (auto-generated)

myapp --colors         # Sets colors = true
myapp --disable-colors # Sets colors = false (custom prefix)
```

## Enum Types

Convert strings to enum values with case-insensitive matching:

```kotlin
enum class LogLevel { DEBUG, INFO, WARN, ERROR }
enum class Format { JSON, XML, YAML, CSV }

class MyApp : Arguments() {
    val logLevel by option("--log-level").enum<LogLevel>()
    val format by option("--format").enum<Format>().default(Format.JSON)
}
```

### Usage Examples

```bash
# Case-insensitive enum matching
myapp --log-level debug    # LogLevel.DEBUG
myapp --log-level INFO     # LogLevel.INFO
myapp --format json        # Format.JSON
myapp --format XML         # Format.XML
```

### Custom Enum Aliases

Provide custom aliases for enum values:

```kotlin
enum class BuildMode { DEBUG, RELEASE, PROFILE }

class MyApp : Arguments() {
    val mode by option("--mode").enum<BuildMode>(
        aliases = mapOf(
            "dev" to BuildMode.DEBUG,
            "prod" to BuildMode.RELEASE,
            "perf" to BuildMode.PROFILE
        )
    )
}
```

```bash
# Using aliases
myapp --mode dev      # BuildMode.DEBUG
myapp --mode prod     # BuildMode.RELEASE
myapp --mode release  # BuildMode.RELEASE (original name still works)
```

## Restricted String Values

Limit string options to specific allowed values:

```kotlin
class MyApp : Arguments() {
    val protocol by option("--protocol").oneOf("http", "https", "ftp")
    val encoding by option("--encoding").oneOf("utf-8", "ascii", "latin1")

    // Case-sensitive restriction
    val httpMethod by option("--method").oneOf("GET", "POST", "PUT", "DELETE", ignoreCase = false)
}
```

### Usage Examples

```bash
myapp --protocol https --encoding utf-8 --method POST
```

## KeyValue Types

Parse structured key-value pairs with configurable separators:

```kotlin
class MyApp : Arguments() {
    // Basic KeyValue parsing (expects key=value)
    val config by option("--config").keyvalue()             // KeyValue?
    val database by option("--db").keyvalue().required()    // KeyValue

    // Custom separators
    val colonSeparated by option("--colon").keyvalue(":")   // KeyValue? (expects key:value)
    val arrowSeparated by option("--arrow").keyvalue("->")  // KeyValue? (expects key->value)

    // Collections
    val properties by option("--prop").keyvalue().list()    // List<KeyValue>
    val settings by option("--setting").keyvalue().set()    // Set<KeyValue>
}
```

### Usage Examples

```bash
# Default separator (=)
myapp --config debug=true --db url=postgresql://localhost:5432/mydb

# Custom separators
myapp --colon database:postgresql --arrow input->output.txt

# Multiple values
myapp --prop host=localhost --prop port=5432 --setting cache=true
```

### Map Conversion

```kotlin
// Easy conversion to Map when needed
val configMap: Map<String, String> = properties.associate { it.key to it.value }
```

### KeyValue Class

```kotlin
class KeyValue(val key: String, val value: String) {
    // Equality based on key only (for Set behavior)
    override fun equals(other: Any?): Boolean = other is KeyValue && key == other.key
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = "$key=$value"
}
```

## Custom Type Transformations

Use the `map()` function for custom type conversions:

```kotlin
import java.io.File
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MyApp : Arguments() {
    // File path conversion
    val configFile by option("--config")
        .map("readable file") { path ->
            path?.let { File(it).takeIf { f -> f.exists() && f.canRead() } }
        }

    // URL conversion
    val endpoint by option("--endpoint")
        .map("valid URL") { url ->
            url?.let {
                try { URL(it) } catch (e: Exception) { null }
            }
        }

    // Date conversion
    val startDate by option("--start-date")
        .map("date in YYYY-MM-DD format") { dateStr ->
            dateStr?.let {
                try {
                    LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: Exception) { null }
                }
            }
        }

    // Range conversion
    val range by option("--range")
        .map("number range (e.g., 1-10)") { rangeStr ->
            rangeStr?.let { str ->
                val parts = str.split("-")
                if (parts.size == 2) {
                    try {
                        val start = parts[0].toInt()
                        val end = parts[1].toInt()
                        if (start <= end) start..end else null
                    } catch (e: Exception) { null }
                } else null
            }
        }
}
```

### Usage Examples

```bash
myapp --config /path/to/config.json \
      --endpoint https://api.example.com \
      --start-date 2024-01-15 \
      --range 1-100
```

## Type Conversion with Validation

Combine type conversion with validation:

```kotlin
class ServerApp : Arguments() {
    val port by option("--port").int()
        .validate("Port must be in valid range") { it in 1..65535 }

    val percentage by option("--load").double()
        .validate("Percentage must be 0-100") { it in 0.0..100.0 }

    val email by option("--email")
        .validate("Must be valid email format") { it?.contains("@") == true }
        .map("email address") { email ->
            email?.lowercase()?.trim()
        }
}
```

## Complex Type Examples

### Duration Parsing

```kotlin
import java.time.Duration
import java.time.format.DateTimeParseException

class MyApp : Arguments() {
    val timeout by option("--timeout")
        .map("duration (e.g., PT30S, 5m, 2h)") { durationStr ->
            durationStr?.let { str ->
                try {
                    // Try ISO 8601 duration format first
                    Duration.parse(str)
                } catch (e: DateTimeParseException) {
                    // Try simple formats
                    when {
                        str.endsWith("s") -> Duration.ofSeconds(str.dropLast(1).toLong())
                        str.endsWith("m") -> Duration.ofMinutes(str.dropLast(1).toLong())
                        str.endsWith("h") -> Duration.ofHours(str.dropLast(1).toLong())
                        else -> null
                    }
                }
            }
        }
}
```

```bash
myapp --timeout PT30S    # 30 seconds (ISO 8601)
myapp --timeout 5m       # 5 minutes (simple format)
myapp --timeout 2h       # 2 hours (simple format)
```

### Size Parsing

```kotlin
class MyApp : Arguments() {
    val maxSize by option("--max-size")
        .map("size with unit (e.g., 100MB, 2GB)") { sizeStr ->
            sizeStr?.let { str ->
                val regex = """(\d+(?:\.\d+)?)\s*(B|KB|MB|GB|TB)?""".toRegex(RegexOption.IGNORE_CASE)
                val match = regex.matchEntire(str.trim())
                if (match != null) {
                    val number = match.groupValues[1].toDouble()
                    val unit = match.groupValues[2].uppercase()
                    val multiplier = when (unit) {
                        "", "B" -> 1L
                        "KB" -> 1024L
                        "MB" -> 1024L * 1024L
                        "GB" -> 1024L * 1024L * 1024L
                        "TB" -> 1024L * 1024L * 1024L * 1024L
                        else -> 1L
                    }
                    (number * multiplier).toLong()
                } else null
            }
        }
}
```

```bash
myapp --max-size 100MB   # 104857600 bytes
myapp --max-size 2GB     # 2147483648 bytes
myapp --max-size 1024    # 1024 bytes
```

### JSON Configuration

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class MyApp : Arguments() {
    val config by option("--config-json")
        .map("JSON configuration") { jsonStr ->
            jsonStr?.let { str ->
                try {
                    Json.parseToJsonElement(str) as? JsonObject
                } catch (e: Exception) { null }
                }
            }
        }
}
```

```bash
myapp --config-json '{"host": "localhost", "port": 8080}'
```

## Error Messages

Type conversion failures produce clear error messages:

```kotlin
class MyApp : Arguments() {
    val port by option("--port").int()
        .map("valid port number") { it }  // Custom description
}
```

```bash
myapp --port abc
# Error: Invalid value for --port: 'abc' is not a valid port number

myapp --port 99999
# Error: Invalid value for --port: '99999' is not a valid port number (if validated)
```

## Non-Nullable vs Nullable Types

Understanding the type system:

```kotlin
class MyApp : Arguments() {
    // Nullable types (no default/required)
    val optionalPort by option("--port").int()          // Int?
    val optionalFile by option("--file")                // String?
    val optionalFlag by option("--flag").bool()         // Boolean?

    // Non-nullable types (with default or required)
    val requiredPort by option("--port").int().required()     // Int
    val defaultPort by option("--port").int().default(8080)   // Int
    val defaultFlag by option("--flag").bool().default(false) // Boolean
}
```

## Best Practices

### 1. Choose Appropriate Types

```kotlin
// Good: Use specific types for specific data
val port by option("--port").int()              // Network port
val percentage by option("--cpu").float()       // Percentage value
val enabled by option("--enabled").bool()       // Feature flag

// Avoid: Using strings for everything
val port by option("--port")                    // String that should be Int
```

### 2. Provide Clear Error Descriptions

```kotlin
// Good: Descriptive transformation descriptions
val timeout by option("--timeout")
    .map("duration in seconds (e.g., 30, 60, 120)") { it?.toIntOrNull() }

// Avoid: Generic or missing descriptions
val timeout by option("--timeout").map { it?.toIntOrNull() }
```

### 3. Validate After Conversion

```kotlin
// Good: Type conversion + validation
val port by option("--port").int()
    .validate("Port must be 1-65535") { it in 1..65535 }

val percentage by option("--load").double()
    .validate("Percentage must be 0-100") { it in 0.0..100.0 }
```

### 4. Handle Edge Cases

```kotlin
// Good: Handle null, empty, and invalid inputs
val configFile by option("--config")
    .map("readable config file") { path ->
        when {
            path.isNullOrBlank() -> null
            !File(path).exists() -> null
            !File(path).canRead() -> null
            else -> File(path)
        }
    }
```

Type conversions enable type-safe CLIs while maintaining the string-based nature of command-line interfaces. Choose types that match your data and provide clear error messages for invalid inputs.