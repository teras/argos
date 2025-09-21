# Options

Options are named command-line parameters that users specify with flags like `--verbose` or `-f filename`. They form the backbone of most CLI interfaces.

## Basic Option Creation

Use the `option()` function to create command-line options:

```kotlin
class MyApp : Arguments() {
    // Basic string option (nullable)
    val name by option("--name", "-n")

    // Multiple switches for the same option
    val help by option("--help", "-h", "-?")
}
```

## Option Types

### String Options

```kotlin
class MyApp : Arguments() {
    // Nullable string (default)
    val name by option("--name", "-n")                    // String?

    // Non-nullable with default
    val format by option("--format", "-f").default("json")  // String

    // Required string
    val input by option("--input").required()             // String
}
```

### Numeric Options

```kotlin
class MyApp : Arguments() {
    // Integer options
    val count by option("--count").int()                  // Int?
    val port by option("--port").int().default(8080)     // Int

    // Other numeric types
    val rate by option("--rate").float()                 // Float?
    val size by option("--size").long()                  // Long?
    val precision by option("--precision").double()      // Double?
}
```

### Boolean Options

```kotlin
class MyApp : Arguments() {
    // Basic boolean flag
    val verbose by option("--verbose", "-v").bool()      // Boolean?

    // Boolean with default
    val debug by option("--debug").bool().default(false) // Boolean

    // Negatable boolean (generates --no-cache)
    val cache by option("--cache").bool().negatable().default(true)
}
```

### Enum Options

```kotlin
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

class MyApp : Arguments() {
    // Enum with case-insensitive matching
    val level by option("--level").enum<LogLevel>()      // LogLevel?

    // Enum with default
    val mode by option("--mode").enum<ProcessMode>().default(ProcessMode.AUTO)
}
```

### Restricted String Values

```kotlin
class MyApp : Arguments() {
    // Restrict to specific values
    val format by option("--format").oneOf("json", "xml", "yaml")

    // Case-sensitive restriction
    val protocol by option("--protocol").oneOf("HTTP", "HTTPS", ignoreCase = false)
}
```

## Option Configuration

### Help Text

```kotlin
class MyApp : Arguments() {
    val verbose by option("--verbose", "-v").bool().default(false)
        .help("Enable verbose output with detailed information")

    val port by option("--port").int().default(8080)
        .help("Port number for the server (1-65535)")
}
```

### Hidden Options

```kotlin
class MyApp : Arguments() {
    // Hidden from help output (useful for debug options)
    val debugMode by option("--debug-mode").bool().hidden()

    // Secret configuration
    val apiKey by option("--api-key").fromEnv("API_KEY").hidden()
}
```

### Value Requirements

```kotlin
class MyApp : Arguments() {
    // Option can work as flag or accept value
    val debugLevel by option("--debug-level")
        .int()
        .requiresValue(false)    // --debug-level or --debug-level 3

    // Always requires a value
    val output by option("--output")
        .requiresValue(true)     // Must be --output filename
}
```

## Option Behavior

### Short Option Clustering

When `clusterChar` is enabled (default), users can combine short options:

```bash
# These are equivalent
myapp -v -f json -o output.txt
myapp -vfo output.txt json     # Clustered short options
```

### Value Separators

Options support multiple ways to specify values:

```bash
# All equivalent (with default separators = and :)
myapp --format json
myapp --format=json
myapp --format:json
```

### Boolean Option Variations

```kotlin
class MyApp : Arguments() {
    val cache by option("--cache").bool().negatable()
}
```

```bash
# User can specify
myapp --cache         # Sets cache = true
myapp --no-cache      # Sets cache = false (auto-generated)
```

### Custom Negation Prefix

```kotlin
class MyApp : Arguments() {
    val compress by option("--compress").bool()
        .negatable("disable-")    // Generates --disable-compress
}
```

## Advanced Option Features

### Callbacks

```kotlin
class MyApp : Arguments() {
    val verbose by option("--verbose").bool().default(false)
        .onValue { isVerbose ->
            if (isVerbose) {
                println("Verbose mode enabled")
                configureLogging(LogLevel.DEBUG)
            }
        }
}
```

### Domain Restrictions

```kotlin
class MyApp : Arguments() {
    val buildDomain by domain("build")
    val testDomain by domain("test")

    // Only available in build domain
    val optimize by option("--optimize").bool()
        .onlyInDomains(::buildDomain)

    // Available in multiple domains
    val target by option("--target")
        .onlyInDomains(::buildDomain, ::testDomain)
}
```

## Complete Option Examples

### File Processing Tool

```kotlin
class FileProcessor : Arguments() {
    // Input/Output options
    val inputFile by option("--input", "-i").required()
        .help("Input file to process")

    val outputDir by option("--output-dir", "-o").default("./output")
        .help("Output directory for processed files")

    // Processing options
    val format by option("--format", "-f")
        .oneOf("json", "xml", "yaml", "csv")
        .default("json")
        .help("Output format")

    val compress by option("--compress", "-c").bool().default(false)
        .help("Compress output files")

    // Behavior options
    val verbose by option("--verbose", "-v").bool().default(false)
        .help("Enable verbose output")

    val maxThreads by option("--threads", "-t").int().default(1)
        .help("Maximum number of processing threads")

    // Advanced options
    val configFile by option("--config").fromEnv("PROCESSOR_CONFIG")
        .help("Configuration file path")

    val dryRun by option("--dry-run").bool().default(false)
        .help("Show what would be done without executing")

    val help by help()
    val version by version("1.0.0")
}
```

### Development Tool

```kotlin
enum class BuildTarget { DEBUG, RELEASE, PROFILE }
enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

class DevTool : Arguments() {
    // Core options
    val target by option("--target", "-t").enum<BuildTarget>().default(BuildTarget.DEBUG)
        .help("Build target (debug, release, profile)")

    val logLevel by option("--log-level", "-l").enum<LogLevel>().default(LogLevel.INFO)
        .help("Set logging verbosity")

    // Feature flags
    val optimize by option("--optimize", "-O").bool().default(false)
        .help("Enable optimization (implied for release builds)")

    val parallel by option("--parallel", "-j").bool().default(false)
        .help("Enable parallel compilation")

    val incremental by option("--incremental").bool().negatable().default(true)
        .help("Enable incremental compilation (use --no-incremental to disable)")

    // Output options
    val quiet by option("--quiet", "-q").bool().default(false)
        .help("Suppress non-essential output")

    val outputDir by option("--output-dir").default("build/")
        .help("Output directory for build artifacts")

    // Advanced options
    val memoryLimit by option("--memory").int()
        .help("Memory limit in MB for build process")

    val configOverride by option("--config-override").list()
        .help("Override configuration values (key=value)")

    val help by help()
    val version by version("2.1.0")
}
```

## Arity Options

Arity options consume exactly N values per invocation, enabling structured multi-value parsing. This is useful for options that need to accept multiple related values as a group.

### Basic Arity

```kotlin
class MyApp : Arguments() {
    // Coordinate pair: --coords 10 20
    val coords by option("--coords").int().arity(2)          // List<Int>?

    // 3D point: --point 1.0 2.5 3.1
    val point by option("--point").float().arity(3)          // List<Float>?

    // Required arity option
    val center by option("--center").int().arity(2).required()  // List<Int>
}
```

### Usage Examples

```bash
# Single coordinate pair
myapp --coords 10 20
# Result: coords = [10, 20]

# Multiple 3D points
myapp --point 0 0 0 --point 1 1 1 --point 2 2 2
# Error: arity(3) with single invocation expects exactly 3 values
# Correct: --point 0 0 0  (this creates one 3D point)
```

### Arity with Collections

Combine arity with collections for powerful multi-value parsing:

```kotlin
class GeometryApp : Arguments() {
    // Multiple line segments: each --line takes 4 values (x1, y1, x2, y2)
    val lines by option("--line").int().list().arity(4)      // List<List<Int>>?

    // Unique triangles: each --triangle takes 3 points (x, y, z)
    val triangles by option("--triangle").float().set().arity(3)  // Set<List<Float>>?
}
```

### Arity Collection Usage

```bash
# Multiple line segments
myapp --line 0 0 10 10 --line 5 5 15 15 --line 20 20 30 30
# Result: lines = [[0, 0, 10, 10], [5, 5, 15, 15], [20, 20, 30, 30]]

# Unique triangles (duplicates removed)
myapp --triangle 0 0 1 --triangle 1 1 2 --triangle 0 0 1
# Result: triangles = {[0.0, 0.0, 1.0], [1.0, 1.0, 2.0]}  # duplicate removed
```

### Arity Validation

```kotlin
class MathApp : Arguments() {
    // Validate each coordinate pair
    val coords by option("--coords").int().list().arity(2)
        .validate("coordinates must be positive") { pair ->
            pair.all { it > 0 }
        }

    // Validate the entire collection of ranges
    val ranges by option("--range").int().list().arity(2)
        .validate("range must be valid (start < end)") { range ->
            range[0] < range[1]
        }
        .validateCollection("ranges must not overlap") { ranges ->
            ranges.sortedBy { it[0] }.zipWithNext().all { (a, b) -> a[1] <= b[0] }
        }
}
```

### Arity Constraints

```kotlin
class AdvancedApp : Arguments() {
    // At least 2 rectangles required
    val rectangles by option("--rect").int().list().arity(4).atLeast(2)  // List<List<Int>>

    // Default coordinate
    val origin by option("--origin").int().arity(2).default(listOf(0, 0))  // List<Int>
}
```

### Arity Limitations

Arity options have some constraints:

```kotlin
// These combinations are NOT allowed and will throw ConfigError:
// val invalid1 by option("--test").requiresValue(false).arity(2)  // arity requires values
// val invalid2 by option("--test").fromEnv("VAR").arity(2)        // arity incompatible with env
```

## Best Practices

### 1. Provide Both Long and Short Forms

```kotlin
// Good: Provides both convenience and clarity
val verbose by option("--verbose", "-v")
val output by option("--output", "-o")

// Acceptable for less common options
val configFile by option("--config")
```

### 2. Use Meaningful Default Values

```kotlin
// Good: Sensible defaults
val port by option("--port").int().default(8080)
val format by option("--format").default("json")

// Consider: Are null defaults appropriate?
val optionalFile by option("--optional-file")  // String? = null
```

### 3. Write Clear Help Text

```kotlin
// Good: Clear, actionable help
val threads by option("--threads", "-t").int().default(4)
    .help("Number of worker threads (1-32, default: 4)")

// Avoid: Vague or unhelpful text
val threads by option("--threads").int().help("Thread setting")
```

### 4. Group Related Options

```kotlin
class MyApp : Arguments() {
    // Input/Output group
    val inputFile by option("--input", "-i")
    val outputDir by option("--output", "-o")
    val format by option("--format", "-f")

    // Behavior group
    val verbose by option("--verbose", "-v")
    val quiet by option("--quiet", "-q")
    val dryRun by option("--dry-run")

    // Performance group
    val threads by option("--threads", "-t")
    val memoryLimit by option("--memory")
}
```

Options are the primary way users interact with your CLI tool. Design them to be intuitive, well-documented, and consistent with user expectations.