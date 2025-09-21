# Basic Usage

This comprehensive guide covers all fundamental concepts of using Argos to build command-line interfaces. After reading this, you'll understand how to create options, positional arguments, validation, constraints, and advanced features.

## The Arguments Class

Every Argos application starts with a class that extends `Arguments`:

```kotlin
import onl.ycode.argos.Arguments

class MyApp : Arguments(
    appName = "myapp",
    appDescription = "My awesome CLI application",
    // Many other configuration options available
) {
    // Your options and configuration go here
}
```

### Arguments Constructor Configuration

The `Arguments` class provides extensive configuration options:

```kotlin
class MyApp : Arguments(
    appName = "my-app",                          // Application name (default: class name)
    appDescription = "My awesome CLI tool",      // App description for help
    unknownOptionsAsPositionals = false,         // Treat unknown options as positional args
    defaultLongPrefix = "--",                    // Default prefix for long options
    clusterChar = '-',                           // Character for clustering short options (-abc)
    valueSeparators = setOf('=', ':'),           // Characters for option=value syntax
    didYouMean = true,                           // Enable "did you mean" suggestions
    didYouMeanMax = 2,                           // Max edit distance for suggestions
    aggregateErrors = true,                      // Show multiple errors at once
    maxAggregatedErrors = 20,                    // Max errors to show when aggregating
    terminal = null,                             // Custom terminal (default: auto-detect)
    useANSITerminal = true,                      // Use ANSI colors when supported
    argumentFilePrefix = '@',                    // Prefix for argument files (@file.txt)
    argumentSeparator = "|"                      // Separator in help text (--opt|-o)
) {
    // Options defined here...
}
```

## Options vs Positional Arguments

Argos supports two main types of command-line arguments:

- **Options**: Named parameters like `--verbose` or `-f filename`
- **Positional Arguments**: Unnamed parameters specified by position like `input.txt output.txt`

```bash
# Options are named
myapp --verbose --format json --output result.txt

# Positional arguments are by position
myapp input.txt output.txt
#     ^^^^^^^^^  ^^^^^^^^^^
#     first      second
#     positional positional
```

## Creating Options

Use the `option()` function to create command-line options:

### Basic String Options

```kotlin
class MyApp : Arguments() {
    // Nullable string option (default)
    val name by option("--name", "-n").help("Your name")

    // Non-nullable with default value
    val format by option("--format", "-f").default("json")
        .help("Output format")

    // Multiple switches for the same option
    val help by option("--help", "-h", "-?").bool().default(false)
}
```

### Type Conversions

Argos provides built-in type conversions:

```kotlin
class MyApp : Arguments() {
    // Numeric types
    val count by option("--count").int()           // Int?
    val rate by option("--rate").float()           // Float?
    val size by option("--size").long()            // Long?
    val precision by option("--precision").double() // Double?

    // With defaults (non-nullable)
    val port by option("--port").int().default(8080)      // Int
    val timeout by option("--timeout").long().default(30L) // Long

    // Boolean options
    val verbose by option("--verbose", "-v").bool()       // Boolean?
    val debug by option("--debug").bool().default(false)  // Boolean

    // Enum types
    val level by option("--level").enum<LogLevel>()       // LogLevel?
    val mode by option("--mode").enum<ProcessMode>().default(ProcessMode.AUTO)

    // Restricted string values
    val format by option("--format").oneOf("json", "xml", "yaml")
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
enum class ProcessMode { AUTO, MANUAL, BATCH }
```

### Collection Types

```kotlin
class MyApp : Arguments() {
    // Lists (preserves order, allows duplicates)
    val files by option("--file", "-f").list()           // List<String>
    val ports by option("--port").int().list()           // List<Int>

    // Sets (no duplicates, preserves insertion order)
    val categories by option("--category").set()         // Set<String>
    val levels by option("--level").int().set()          // Set<Int>

    // Count occurrences using boolean lists
    val verbosity by option("-v", "--verbose").bool().list()   // List<Boolean> (check .size)
}
```

### Boolean Options with Negation

```kotlin
class MyApp : Arguments() {
    // Standard boolean
    val cache by option("--cache").bool().default(true)

    // Negatable boolean (auto-generates --no-cache)
    val colors by option("--colors").bool().negatable().default(true)

    // Custom negation prefix
    val compress by option("--compress").bool().negatable("disable-").default(true)
    // Generates: --compress and --disable-compress
}
```

## Positional Arguments

Positional arguments are specified by position rather than by name:

```kotlin
class MyApp : Arguments() {
    // Single positional (nullable)
    val inputFile by positional()                        // String?

    // Required positional (non-nullable)
    val outputFile by positional().required()            // String

    // Positional with default
    val configFile by positional().default("config.json") // String

    // List of positional arguments (collects remaining args - must be last)
    val additionalFiles by positional().list()           // List<String>
}

// Alternative: Set of positional arguments (also must be last)
class MyAppWithSet : Arguments() {
    val inputFile by positional()                        // String?
    val outputFile by positional().required()            // String
    val configFile by positional().default("config.json") // String

    // Set of positional arguments (must be last)
    val sources by positional().set()                    // Set<String>
}
```

## Environment Variables

Options can read from environment variables as fallback:

```kotlin
class MyApp : Arguments() {
    val apiKey by option("--api-key")
        .fromEnv("API_KEY")
        .help("API key for authentication")

    val dbUrl by option("--db-url")
        .fromEnv("DATABASE_URL")
        .default("sqlite:memory:")
}
```

## Validation

Argos provides comprehensive validation capabilities:

### Single Value Validation

```kotlin
class MyApp : Arguments() {
    val email by option("--email")
        .validate("Must be valid email") { it?.contains("@") == true }

    val port by option("--port").int()
        .validate("Port must be in valid range") { it in 1..65535 }

    // Multiple validations
    val username by option("--username")
        .validate(
            "Username must be at least 3 characters" to { it?.length?.let { len -> len >= 3 } == true },
            "Username must contain only alphanumeric chars" to { it?.all { c -> c.isLetterOrDigit() } == true }
        )
}
```

### Collection Validation

```kotlin
class MyApp : Arguments() {
    val ports by option("--port").int().list()
        .validate("Each port must be in valid range") { it in 1..65535 }          // Per-element
        .validateCollection("Must have 1-5 ports") { it.size in 1..5 }           // Entire collection

    val files by option("--file").list()
        .validate("File must exist") { File(it).exists() }                       // Per-element
        .validateCollection("Must specify at least one file") { it.isNotEmpty() } // Collection
}
```

## Constraints and Requirements

### Basic Requirements

```kotlin
class MyApp : Arguments() {
    // Mark scalar option as required
    val input by option("--input").required()

    // Require multiple elements in collections
    val tags by option("--tag").list().atLeast(2)

    // For counting occurrences, require minimum in boolean list
    val verbosity by option("-v").bool().list().atLeast(1)
}
```

### Conditional Requirements

```kotlin
class MyApp : Arguments() {
    val input by option("--input")
    val output by option("--output")
    val config by option("--config")
    val mode by option("--mode").oneOf("fast", "slow")

    // Require backup when output is specified
    val backup by option("--backup").requireIfAnyPresent(::output)

    // Require verify when both input and output are present
    val verify by option("--verify").requireIfAllPresent(::input, ::output)

    // Require threads when mode is "fast"
    val threads by option("--threads").int().requireIfValue(::mode) { it == "fast" }
}
```

### Group Constraints

The `*With` constraint methods (`exactlyOneWith`, `atMostOneWith`, `atLeastOneWith`) automatically include the receiver option in the constraint group. For example, `exactlyOneWith(::input)` creates a group containing both the receiver option and `input`.

```kotlin
class MyApp : Arguments() {
    val input by option("--input")
    val output by option("--output")
    val format by option("--format")
    val raw by option("--raw").bool()
    val json by option("--json").bool()
    val verbose by option("--verbose").bool()
    val quiet by option("--quiet").bool()

    // Exactly one must be provided (group: {source, input})
    val source by option("--source").exactlyOneWith(::input)

    // At most one can be provided (group: {formatOpt, raw, json})
    val formatOpt by option("--format-opt").atMostOneWith(::raw, ::json)

    // At least one must be provided (group: {dest, output})
    val dest by option("--dest").atLeastOneWith(::output)

    // Options that conflict with each other
    val verboseOpt by option("--verbose-opt").conflictsWith(::quiet)
}
```

## Custom Transformations

Use the `map()` function for custom type transformations:

```kotlin
import java.io.File
import java.net.URL

class MyApp : Arguments() {
    // Transform to File
    val configFile by option("--config")
        .map("a valid file path") { path ->
            path?.let { File(it).takeIf { f -> f.exists() } }
        }

    // Transform to URL
    val endpoint by option("--endpoint")
        .map("a valid URL") { url ->
            url?.let {
                try { URL(it) } catch (e: Exception) { null }
            }
        }

    // Custom validation in transformation
    val percentage by option("--percentage")
        .map("a percentage (0-100)") { value ->
            value?.toIntOrNull()?.takeIf { it in 0..100 }
        }
}
```

## Advanced Option Features

### Option Value Requirements

```kotlin
class MyApp : Arguments() {
    // Control whether option requires a value
    val debugLevel by option("--debug-level")
        .int()
        .requiresValue(false)              // Can work as flag or with value

    val output by option("--output")
        .requiresValue(true)               // Always requires value
}
```

### Password Options

```kotlin
class MyApp : Arguments() {
    val password by option("--password")
        .input(
            hidden = true,
            prompt = "Enter password: ",
            confirmPrompt = "Confirm password: ",
            requireConfirmation = true,
            mismatchPrompt = "Passwords don't match. Try again.",
            maxRetries = 3
        )
        .hidden()                          // Don't show in help
}
```

### Hidden Options

```kotlin
class MyApp : Arguments() {
    val secretKey by option("--secret")
        .fromEnv("SECRET_KEY")
        .hidden()                          // Don't show in help output
}
```

### Callbacks

```kotlin
class MyApp : Arguments() {
    val verbose by option("--verbose").bool().default(false)
        .onValue { isVerbose ->
            if (isVerbose) {
                println("Verbose mode enabled")
            }
        }

    val logLevel by option("--log-level").enum<LogLevel>().default(LogLevel.INFO)
        .onValue { level ->
            configureLogging(level)
        }
}
```

## Built-in Helper Methods

### Help and Version

```kotlin
class MyApp : Arguments() {
    // Auto-generates help option with default switches (-h, --help)
    val help by help()

    // Custom help switches
    val helpCustom by help("-?", "--help", "--usage")

    // Version option
    val version by version("1.0.0")

    // Custom version switches
    val versionCustom by version("1.2.3", "--version", "-V")
}
```

## Parsing Methods

### Parse with Callbacks

```kotlin
fun main(args: Array<String>) {
    val app = MyApp()

    app.parse(args) ?: return

    println("Parsing successful!")
    // Process the application logic
    processApp(app)
}
```

### Simple Parse Method

```kotlin
fun main(args: Array<String>) {
    val app = MyApp()

    app.parse(args) ?: return

    println("Arguments parsed successfully")
    processApp(app)
}
```

## Value Source Tracking

Track how each option's value was obtained:

```kotlin
fun main(args: Array<String>) {
    val app = MyApp()
    app.parse(args) ?: return

    // Check value sources
    val apiKeySource = app.valueSourceOf(app::apiKey)
    when (apiKeySource) {
        ValueSource.USER -> println("API key provided by user")
        ValueSource.ENVIRONMENT -> println("API key from environment")
        ValueSource.DEFAULT -> println("API key using default")
        ValueSource.MISSING -> println("API key not provided")
    }
}
```

## Complete Example

Here's a comprehensive example showcasing many features:

```kotlin
import onl.ycode.argos.Arguments
import onl.ycode.argos.parse
import java.io.File

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
enum class OutputFormat { JSON, XML, YAML, CSV }

class FileProcessor : Arguments(
    appName = "fileprocessor",
    appDescription = "Advanced file processing tool with multiple options",
    aggregateErrors = true,
    didYouMean = true
) {
    // Basic options
    val verbose by option("--verbose", "-v").bool().negatable().default(false)
        .help("Enable verbose output (use --no-verbose to disable)")

    val logLevel by option("--log-level", "-l").enum<LogLevel>().default(LogLevel.INFO)
        .help("Set logging level")

    // File operations
    val inputFiles by option("--input", "-i").list()
        .validate("File must exist") { File(it).exists() }
        .validateCollection("Must specify at least one input file") { it.isNotEmpty() }
        .help("Input files to process")

    val outputDir by option("--output-dir", "-o")
        .map("existing directory") { path ->
            path?.let { File(it).takeIf { f -> f.isDirectory } }
        }
        .help("Output directory")

    // Format and processing options
    val format by option("--format", "-f").enum<OutputFormat>().default(OutputFormat.JSON)
        .help("Output format")

    val compress by option("--compress", "-c").bool().default(false)
        .help("Compress output files")

    val threads by option("--threads", "-t").int().default(1)
        .validate("Thread count must be positive") { it > 0 }
        .help("Number of processing threads")

    // Advanced options
    val configFile by option("--config").fromEnv("PROCESSOR_CONFIG")
        .map("readable config file") { path ->
            path?.let { File(it).takeIf { f -> f.canRead() } }
        }
        .help("Configuration file path")

    val apiKey by option("--api-key").fromEnv("API_KEY").hidden()
        .help("API key for external services")

    // Conditional requirements
    val backup by option("--backup").requireIfAnyPresent(::outputDir)
        .help("Create backup before processing")

    val verify by option("--verify").requireIfValue(::format) { it == OutputFormat.XML }
        .help("Verify XML output (required for XML format)")

    // Conflicting options
    val quietMode by option("--quiet", "-q").bool().conflictsWith(::verbose)
        .help("Suppress all output")

    // Built-in options
    val help by help()
    val version by version("2.1.0")
}

fun main(args: Array<String>) {
    val processor = FileProcessor()

    processor.parse(args) ?: return

    println("File processor starting...")
    println("Input files: ${processor.inputFiles}")
    println("Output format: ${processor.format}")
    println("Verbose mode: ${processor.verbose}")

    // Check value sources
    val configSource = processor.valueSourceOf(processor::configFile)
    if (configSource == ValueSource.ENVIRONMENT) {
        println("Using config from environment variable")
    }

    // Process files here...
    processFiles(processor)
}

fun processFiles(processor: FileProcessor) {
    // Implementation here...
    println("Processing ${processor.inputFiles.size} files...")
}
```

This example demonstrates:

- Multiple option types and conversions
- Validation at both element and collection levels
- Conditional requirements and constraints
- Environment variable fallbacks
- Custom transformations with `map()`
- Hidden options for sensitive data
- Value source tracking
- Comprehensive error handling
- Built-in help and version support
