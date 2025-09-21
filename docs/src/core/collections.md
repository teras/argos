# Collection Types

Argos supports collection types for options and positional arguments that can accept multiple values. This enables powerful CLI interfaces that can handle lists, sets, and counted occurrences.

## List Options

Lists preserve order and allow duplicate values:

```kotlin
class MyApp : Arguments() {
    // Basic string list
    val files by option("--file", "-f").list()           // List<String>

    // Typed lists
    val ports by option("--port").int().list()           // List<Int>
    val percentages by option("--load").double().list()  // List<Double>

    // List with default values
    val sources by option("--source").list().default(listOf("src/", "lib/"))
}
```

### Usage Examples

```bash
# Multiple occurrences of the same option
myapp --file input1.txt --file input2.txt --file input3.txt

# Short form
myapp -f input1.txt -f input2.txt -f input3.txt

# Mixed with other options
myapp --port 8080 --port 8081 --port 8082 --verbose
```

### List Requirements

```kotlin
class MyApp : Arguments() {
    // Require at least one item
    val inputFiles by option("--input").list().required()  // equivalent to atLeast(1)

    // Require specific minimum count
    val servers by option("--server").list().atLeast(2)

    // With validation
    val ports by option("--port").int().list()
        .atLeast(1)
        .validate("Port must be valid") { it in 1..65535 }  // Per-element validation
}
```

## Set Options

Sets automatically remove duplicates and preserve insertion order:

```kotlin
class MyApp : Arguments() {
    // Basic string set
    val categories by option("--category").set()         // Set<String>

    // Typed sets
    val levels by option("--level").int().set()          // Set<Int>
    val formats by option("--format").enum<Format>().set() // Set<Format>

    // Set with defaults
    val features by option("--feature").set().default(setOf("basic"))
}
```

### Usage Examples

```bash
# Duplicates automatically removed
myapp --category web --category api --category web --category mobile
# Result: Set("web", "api", "mobile")  # "web" appears only once

# Order preserved for first occurrence
myapp --level 1 --level 3 --level 2 --level 1
# Result: Set(1, 3, 2)  # Insertion order preserved, duplicate 1 removed
```

## Counting Option Occurrences

Track how many times an option appears using boolean lists:

```kotlin
class MyApp : Arguments() {
    // Count occurrences using boolean list
    val verbosity by option("-v", "--verbose").bool().list()   // List<Boolean>

    // Multiple count options
    val debugLevel by option("-d", "--debug").bool().list()     // List<Boolean>
}
```

### Usage Examples

```bash
# Different verbosity levels
myapp -v           # verbosity.size = 1
myapp -vv          # verbosity.size = 2
myapp -vvv         # verbosity.size = 3
myapp -v -v -v     # verbosity.size = 3

# Using in code
when (verbosity.size) {
    0 -> setLogLevel(LogLevel.ERROR)
    1 -> setLogLevel(LogLevel.WARN)
    2 -> setLogLevel(LogLevel.INFO)
    else -> setLogLevel(LogLevel.DEBUG)
}
```

## Collection Validation

### Single Value Validation

Validate individual (non-collection) options:

```kotlin
class MyApp : Arguments() {
    val port by option("--port").int()
        .validate("Port must be in valid range: {value}") { it in 1..65535 }

    val email by option("--email")
        .validate("Invalid email format: {value}") { it.contains("@") }

    val configFile by option("--config")
        .validate("Config file not found: {value}") { File(it).exists() }
        .validate("Config must be readable: {value}") { File(it).canRead() }
}
```

**Available template variables for single values:**
- `{value}` - The actual value provided by the user
- `{option}` - The option name (e.g., "--port")

### Per-Element Validation

Validate each element in collections:

```kotlin
class MyApp : Arguments() {
    val ports by option("--port").int().list()
        .validate("Port must be in valid range: {value}") { it in 1..65535 }

    val emails by option("--email").list()
        .validate("Invalid email format: {value}") { it.contains("@") }

    val files by option("--file").list()
        .validate("File not found: {value}") { File(it).exists() }
        .validate("File not readable: {value}") { File(it).canRead() }
}
```

**Note:** Per-element validation uses the same template variables as single value validation, where `{value}` represents each individual element.

### Collection-Wide Validation

Validate the entire collection:

```kotlin
class MyApp : Arguments() {
    val ports by option("--port").int().list()
        .validate("Port must be valid") { it in 1..65535 }           // Per-element
        .validateCollection("Must have 1-5 ports") { it.size in 1..5 } // Collection

    val servers by option("--server").list()
        .validateCollection("Must have at least one server") { it.isNotEmpty() }
        .validateCollection("Cannot have more than 10 servers") { it.size <= 10 }

    val categories by option("--category").set()
        .validateCollection("Must include 'core' category") { "core" in it }
}
```

#### Template Variables in Collection Validation

Collection validation messages support template variables that are replaced automatically:

```kotlin
class MyApp : Arguments() {
    val files by option("--file").list()
        .validateCollection("Must specify at least 2 files, got {count}") { it.size >= 2 }

    val tags by option("--tag").set()
        .validateCollection("Must have exactly 3 unique tags, got {value}") { it.size == 3 }

    val verbosity by option("-v").bool().list()
        .validateCollection("Verbosity level too high: {count} (max 3)") { it.size <= 3 }
}
```

**Available template variables:**
- `{count}` - The size of the collection
- `{value}` - The size of the collection (same as {count})
- `{option}` - The option name (e.g., "--file")

**Example error messages:**
```
Must specify at least 2 files, got 1
Must have exactly 3 unique tags, got 5
Verbosity level too high: 4 (max 3)
```

## Positional Collections

Collections work with positional arguments too:

**⚠️ Important:** Positional lists and sets must always be the last positional argument since they consume all remaining arguments.

```kotlin
class MyApp : Arguments() {
    // List of positional arguments (must be last)
    val inputFiles by positional().list()                // List<String>

    // Set of positional arguments (must be last)
    val uniqueTargets by positional().set()              // Set<String>

    // With validation (must be last)
    val sourceFiles by positional().list()
        .validate("File must exist") { File(it).exists() }
        .validateCollection("At least one file required") { it.isNotEmpty() }
}
```

### Usage Examples

```bash
# List of files
myapp file1.txt file2.txt file3.txt dir/file4.txt

# Mixed positional and options
myapp --verbose file1.txt file2.txt --output results/
```

## Advanced Collection Features

### Custom Collection Transformations

```kotlin
import java.io.File

class MyApp : Arguments() {
    // Transform to File objects
    val configFiles by option("--config").list()
        .map("readable config files") { paths ->
            paths?.mapNotNull { path ->
                File(path).takeIf { it.exists() && it.canRead() }
            }
        }

    // Transform and filter
    val validPorts by option("--port").list()
        .map("valid port numbers") { strings ->
            strings?.mapNotNull { it.toIntOrNull() }
                   ?.filter { it in 1..65535 }
        }
}
```

### Environment Variable Integration

```kotlin
class MyApp : Arguments() {
    // Read from environment variable (comma-separated)
    val searchPaths by option("--search-path").list()
        .fromEnv("SEARCH_PATHS")  // Reads from env var, splits on whitespace
        .default(listOf("/usr/local/bin", "/usr/bin"))

    val categories by option("--category").set()
        .fromEnv("DEFAULT_CATEGORIES")
}
```

```bash
# Environment variable usage
export SEARCH_PATHS="/opt/tools /home/user/bin /usr/local/bin"
export DEFAULT_CATEGORIES="web api mobile"

myapp  # Uses environment variables
myapp --search-path /custom/path  # Overrides environment
```

## Complex Collection Examples

### Build System

```kotlin
enum class Target { DEBUG, RELEASE, PROFILE }
enum class Architecture { X86, X64, ARM, ARM64 }

class BuildTool : Arguments() {
    val sourceFiles by option("--source", "-s").list()
        .validate("Source file must exist") { File(it).exists() }
        .validateCollection("At least one source file required") { it.isNotEmpty() }
        .help("Source files to compile")

    val targets by option("--target", "-t").enum<Target>().set()
        .default(setOf(Target.DEBUG))
        .help("Build targets (debug, release, profile)")

    val architectures by option("--arch", "-a").enum<Architecture>().set()
        .default(setOf(Architecture.X64))
        .help("Target architectures")

    val includePaths by option("--include", "-I").list()
        .validate("Include path must exist") { File(it).isDirectory }
        .help("Include directories for compilation")

    val defines by option("--define", "-D").list()
        .validate("Must be KEY=VALUE format") { it.contains("=") }
        .help("Preprocessor definitions")

    val verbosity by option("-v", "--verbose").bool().list()
        .help("Increase verbosity (use multiple times)")
}
```

```bash
# Build example
buildtool --source main.cpp --source utils.cpp \
         --target debug --target release \
         --arch x64 --arch arm64 \
         --include /usr/include --include ./include \
         --define VERSION=1.0 --define DEBUG=1 \
         -vv
```

### Test Runner

```kotlin
enum class TestFramework { JUNIT, TESTNG, SPEK }

class TestRunner : Arguments() {
    val testFiles by positional().list()
        .validate("Test file must exist") { File(it).exists() }
        .validateCollection("At least one test file required") { it.isNotEmpty() }
        .help("Test files to execute (must be last)")

    val frameworks by option("--framework").enum<TestFramework>().set()
        .default(setOf(TestFramework.JUNIT))
        .help("Testing frameworks to use")

    val testGroups by option("--group", "-g").set()
        .help("Test groups to include")

    val excludeGroups by option("--exclude-group").set()
        .help("Test groups to exclude")

    val systemProperties by option("--system-prop", "-D").list()
        .validate("Must be key=value format") { it.contains("=") }
        .help("System properties for test execution")

    val parallelism by option("--parallel", "-j").int().default(1)
        .validate("Parallelism must be positive") { it > 0 }
        .help("Number of parallel test threads")

    val retries by option("--retry").int().default(0)
        .validate("Retries must be non-negative") { it >= 0 }
        .help("Number of times to retry failed tests")
}
```

```bash
# Test execution
testrunner tests/unit/*.kt tests/integration/*.kt \
          --framework junit --framework testng \
          --group unit --group integration \
          --exclude-group slow \
          --system-prop test.timeout=30000 \
          --system-prop test.parallel=true \
          --parallel 4 --retry 2
```

### Deployment Tool

```kotlin
enum class Environment { DEV, STAGING, PROD }

class DeployTool : Arguments() {
    val environments by option("--env", "-e").enum<Environment>().set()
        .validateCollection("At least one environment required") { it.isNotEmpty() }
        .help("Target environments for deployment")

    val services by option("--service", "-s").set()
        .validateCollection("At least one service required") { it.isNotEmpty() }
        .help("Services to deploy")

    val configFiles by option("--config").list()
        .validate("Config file must exist") { File(it).exists() }
        .validate("Config file must be readable") { File(it).canRead() }
        .help("Configuration files for deployment")

    val tags by option("--tag").set()
        .help("Tags to apply to deployed resources")

    val regions by option("--region").set()
        .default(setOf("us-east-1"))
        .help("AWS regions for deployment")

    val rollbackSteps by option("--rollback-step").int().list()
        .validate("Step must be positive") { it > 0 }
        .help("Rollback steps to prepare")
}
```

```bash
# Deployment example
deploytool --env staging --env prod \
          --service api --service web --service worker \
          --config config/staging.yml --config config/prod.yml \
          --tag version:2.1.0 --tag team:backend \
          --region us-east-1 --region eu-west-1 \
          --rollback-step 1 --rollback-step 2 --rollback-step 3
```

## Best Practices

### 1. Choose the Right Collection Type

```kotlin
// Use List for ordered, potentially duplicate data
val inputFiles by option("--file").list()        // Order matters, duplicates OK

// Use Set for unique values
val categories by option("--category").set()     // No duplicates needed

// Use boolean list for counting occurrences
val verbosity by option("-v").bool().list()      // -v, -vv, -vvv (check .size)
```

### 2. Validate Appropriately

```kotlin
// Good: Validate both elements and collection
val ports by option("--port").int().list()
    .validate("Port must be valid") { it in 1..65535 }           // Each port
    .validateCollection("Too many ports") { it.size <= 10 }      // Total count

// Consider: What validation makes sense for your use case?
val files by option("--file").list()
    .validate("File must exist") { File(it).exists() }          // Each file
    .validateCollection("Need input files") { it.isNotEmpty() }  // At least one
```

### 3. Provide Clear Help

```kotlin
// Good: Explain how multiple values work
val includes by option("--include", "-I").list()
    .help("Include directories (can be specified multiple times)")

val verbosity by option("-v", "--verbose").bool().list()
    .help("Verbosity level (use -v, -vv, -vvv for increasing verbosity)")
```

### 4. Set Reasonable Defaults

```kotlin
// Good: Sensible defaults for common cases
val searchPaths by option("--search").list()
    .default(listOf(".", "src/", "lib/"))

val logLevels by option("--log-level").enum<LogLevel>().set()
    .default(setOf(LogLevel.INFO))
```

## Related Features

For structured multi-value parsing where each option invocation consumes exactly N values (like coordinate pairs or geometric points), see [Arity Options](options.md#arity-options) which provide `--coords 10 20` style functionality.

## KeyValue Collections

KeyValue types provide structured key-value parsing with configurable separators:

```kotlin
class MyApp : Arguments() {
    // Scalar KeyValue (nullable and required)
    val config by option("--config").keyvalue()             // KeyValue?
    val database by option("--db").keyvalue().required()    // KeyValue

    // Collections of KeyValue pairs
    val properties by option("--prop").keyvalue().list()    // List<KeyValue>
    val settings by option("--setting").keyvalue().set()    // Set<KeyValue>

    // Custom separators
    val colonSeparated by option("--colon").keyvalue(":")   // KeyValue? (expects key:value)
    val arrowSeparated by option("--arrow").keyvalue("->")  // KeyValue? (expects key->value)
}
```

### Usage Examples

```bash
# Default separator (=)
myapp --config debug=true --db url=postgresql://localhost:5432/mydb

# Multiple properties with lists (preserves order and duplicates)
myapp --prop host=localhost --prop port=5432 --prop host=backup.example.com

# Sets with key deduplication (first value wins for duplicate keys)
myapp --setting cache=true --setting debug=false --setting cache=false
# Result: Set{cache=true, debug=false}

# Custom separators
myapp --colon database:postgresql --arrow input->output.txt
```

### Easy Map Conversion

```kotlin
// Convert to Map when needed
val configMap: Map<String, String> = properties.associate { it.key to it.value }
val settingsMap: Map<String, String> = settings.associate { it.key to it.value }
```

### Validation

```kotlin
class MyApp : Arguments() {
    // Element validation
    val validated by option("--validated").keyvalue()
        .validate("Key must start with 'app'") { it?.key?.startsWith("app") ?: true }

    // Collection validation
    val validatedList by option("--vlist").keyvalue().list()
        .validate("Each key must be alphanumeric") { it.key.all { c -> c.isLetterOrDigit() } }
        .validateCollection("Must have 1-3 entries") { it.size in 1..3 }
}
```

Collection types enable powerful CLI interfaces that can handle complex, multi-value inputs while maintaining type safety and providing clear validation feedback.