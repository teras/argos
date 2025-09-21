# Builder APIs Reference

Argos uses builder classes to configure different types of command-line arguments. Each builder provides a fluent API for setting up options, validation, and behavior.

## OptionBuilder

The `OptionBuilder` is used for single-value options that can be specified once (or multiple times with `.list()` or `.set()`).

### Creation

```kotlin
val name by option("--name", "-n")
//         ^^^^^^^^^^^^^^^^^^^^^^^^
//         Returns OptionBuilder<String?>

val port by option("--port").int()
//         ^^^^^^^^^^^^^^^^^^
//         Returns OptionBuilder<Int?>
```

### Type Conversion Methods

#### Basic Types

```kotlin
// String (default)
val name by option("--name")  // OptionBuilder<String?>

// Numeric types
val port by option("--port").int()        // OptionBuilder<Int?>
val size by option("--size").long()       // OptionBuilder<Long?>
val rate by option("--rate").float()      // OptionBuilder<Float?>
val factor by option("--factor").double() // OptionBuilder<Double?>

// Boolean flags
val verbose by option("--verbose").bool() // OptionBuilder<Boolean?>
```

#### Advanced Types

```kotlin
// Enums
enum class LogLevel { DEBUG, INFO, WARN, ERROR }
val level by option("--level").enum<LogLevel>() // OptionBuilder<LogLevel?>

// Restricted choices
val format by option("--format").oneOf("json", "xml", "csv") // OptionBuilder<String?>

// KeyValue types
val config by option("--config").keyvalue() // OptionBuilder<KeyValue?>
val database by option("--db").keyvalue(":") // OptionBuilder<KeyValue?> (expects key:value)

// Custom mapping
val file by option("--input").map { it?.let(::File) } // OptionBuilder<File?>
val timeout by option("--timeout").map(desc = "duration in seconds") { s ->
    s?.toLongOrNull()?.let { Duration.ofSeconds(it) }
} // OptionBuilder<Duration?>
```

### Configuration Methods

#### Help and Documentation

```kotlin
val port by option("--port")
    .int()
    .help("Server port number")
    .default(8080)
```

#### Default Values

```kotlin
// Simple default
val threads by option("--threads").int().default(1)

// Computed default
val output by option("--output").default { "output-${System.currentTimeMillis()}.txt" }

// Environment variable fallback
val apiKey by option("--api-key").fromEnv("API_KEY").required()
```

#### Required Options

```kotlin
// Required option
val input by option("--input").required() // OptionBuilder<String>

// Required with custom message
val database by option("--database").required("Database URL must be specified")
```

#### Domain Restrictions

```kotlin
class GitApp : Arguments() {
    val commit by domain("commit")
    val push by domain("push")

    // Option only available in commit domain
    val message by option("--message", "-m")
        .onlyInDomains(::commit)
        .help("Commit message")
}
```

### Collection Methods

#### Lists

```kotlin
// Multiple values: --file a.txt --file b.txt
val files by option("--file").list() // OptionBuilder<List<String>>

// With type conversion
val ports by option("--port").int().list() // OptionBuilder<List<Int>>

// KeyValue lists
val properties by option("--prop").keyvalue().list() // OptionBuilder<List<KeyValue>>

// Positional list
val inputFiles by positional("files").list() // OptionListBuilder<String>
```

#### Sets

```kotlin
// Unique values only
val tags by option("--tag").set() // OptionSetBuilder<String>

// With type conversion
val levels by option("--level").enum<LogLevel>().set() // OptionSetBuilder<LogLevel>

// KeyValue sets (key-only equality, first value wins for duplicate keys)
val settings by option("--setting").keyvalue().set() // OptionSetBuilder<KeyValue>
```

#### Count

```kotlin
// Count occurrences using boolean list: -v -v -v = List<Boolean> with size 3
val verbosity by option("--verbose", "-v").bool().list() // List<Boolean> (check .size)
```

### Arity

Arity options consume exactly N values per invocation, creating structured multi-value options:

```kotlin
// Single coordinate pair: --coords 10 20
val coords by option("--coords").int().arity(2) // ArityOptionBuilder<Int> (returns List<Int>?)

// Multiple line segments: --line 0 0 10 10 --line 5 5 15 15
val lines by option("--line").int().list().arity(4) // ArityListOptionBuilder<Int> (returns List<List<Int>>?)

// Unique triangles: --triangle 0 0 1 --triangle 1 1 2
val triangles by option("--triangle").float().set().arity(3) // AritySetOptionBuilder<Float> (returns Set<List<Float>>?)

// Required arity option
val center by option("--center").int().arity(2).required() // NonNullableArityOptionBuilder<Int> (returns List<Int>)
```

### Validation

```kotlin
val port by option("--port")
    .int()
    .validate { value ->
        when {
            value == null -> null // Allow null for optional options
            value < 1024 -> "Port must be >= 1024"
            value > 65535 -> "Port must be <= 65535"
            else -> null // Valid
        }
    }
```

### Boolean Flags

```kotlin
// Simple flag
val verbose by option("--verbose", "-v").bool() // Boolean? (true if present)

// Negatable flag
val colors by option("--colors")
    .negatable()  // Enables --no-colors
    .default(true)

// Explicit boolean values
val debug by option("--debug")
    .bool()  // Accepts --debug=true, --debug=false, etc.
```

### Environment Variables

```kotlin
// Read from environment if not provided on command line
val apiKey by option("--api-key").fromEnv("API_KEY")

// With fallback chain
val host by option("--host")
    .fromEnv("SERVER_HOST")
    .default("localhost")
```

## PositionalBuilder

Positional arguments are specified by position rather than name.

### Basic Usage

```kotlin
// Single positional argument
val input by positional("input") // Required by default

// Optional positional
val output by positional("output").optional() // PositionalBuilder<String?>

// With default
val config by positional("config").default("config.yml")
```

### Type Conversion

```kotlin
// Same conversion methods as options
val count by positional("count").int()
val file by positional("file").map { File(it) }
val level by positional("level").enum<LogLevel>()
```

### Collections

```kotlin
// List of positional arguments (rest of command line)
val files by positional("files").list() // List<String>

// At least one required
val sources by positional("sources").list().requiredMin(1)
```

### Validation

```kotlin
val inputFile by positional("input")
    .map { File(it) }
    .validate { file ->
        when {
            !file.exists() -> "File does not exist: ${file.path}"
            !file.canRead() -> "Cannot read file: ${file.path}"
            else -> null
        }
    }
```

## DomainBuilder

Domains represent subcommands (like `git commit`, `docker run`).

### Basic Usage

```kotlin
class GitApp : Arguments() {
    val commit by domain("commit").help("Record changes to the repository")
    val push by domain("push").help("Update remote refs")
    val pull by domain("pull").help("Fetch and integrate with another repository")

    // Check which domain was selected
    fun handleCommand() {
        when (selectedDomain()) {
            "commit" -> handleCommit()
            "push" -> handlePush()
            "pull" -> handlePull()
            null -> printHelp()
        }
    }
}
```

### Domain Configuration

```kotlin
val commit by domain("commit")
    .help("Record changes to the repository")
    .aliases("ci")  // Allow 'ci' as shorthand for 'commit'

val status by domain()  // Uses property name as domain ID
    .help("Show working tree status")
```

### Domain-Specific Options

```kotlin
class DockerApp : Arguments() {
    val run by domain("run")
    val build by domain("build")

    // Only available in 'run' domain
    val interactive by option("--interactive", "-i")
        .onlyInDomains(::run)
        .help("Keep STDIN open")

    // Only available in 'build' domain
    val tag by option("--tag", "-t")
        .onlyInDomains(::build)
        .help("Name and optionally tag the image")

    // Available in multiple domains
    val verbose by option("--verbose", "-v")
        .onlyInDomains(::run, ::build)
        .help("Verbose output")
}
```

## ConstraintsBuilder

Define validation rules and relationships between options.

### Basic Constraints

```kotlin
constraints {
    // Minimum/maximum values
    "port" requiredMin 1024
    "port" requiredMax 65535

    // Minimum/maximum occurrences
    "files" requiredMin 1  // At least one file
    "threads" requiredMax 16  // At most 16 threads

    // Required options
    "input" requiredAlways  // Always required
    "output" requiredIf { input != null }  // Required if input is provided
}
```

### Option Groups

```kotlin
constraints {
    // Exactly one of these options must be provided
    exactlyOne("--json", "--xml", "--csv")

    // At most one of these options
    atMostOne("--quiet", "--verbose")

    // At least one of these options
    atLeastOne("--input-file", "--input-url", "--stdin")
}
```

### Conditional Requirements

```kotlin
constraints {
    // Require option if another is present
    "password" requiredIf { username != null }

    // Require option if any of several are present
    require("output") ifAnyPresent("--process", "--transform", "--convert")

    // Require option if all of several are present
    require("format") ifAllPresent("--output", "--save")

    // Require based on value
    require("ssl-cert") ifValue(::useSSL) { it == true }
}
```

### Custom Validation

```kotlin
constraints {
    custom("file-size") { args ->
        val file = File(args.input)
        if (file.length() > 100_000_000) {
            "Input file is too large (max 100MB)"
        } else null
    }

    custom("date-range") { args ->
        if (args.startDate != null && args.endDate != null && args.startDate > args.endDate) {
            "Start date must be before end date"
        } else null
    }
}
```

### Conflict Resolution

```kotlin
constraints {
    // These options cannot be used together
    conflicts("--input-file", "--input-url")

    // More complex conflicts
    conflicts {
        group("--batch", "--interactive")
        group("--force", "--confirm")
    }
}
```

## SettingsBuilder

Configure global application behavior.

### Basic Settings

```kotlin
settings {
    appName = "myapp"
    appDescription = "My awesome command-line tool"
    version = "1.0.0"
}
```

### Option Behavior

```kotlin
settings {
    // Change default prefixes
    defaultLongPrefix = "--"
    defaultShortPrefix = "-"

    // Allow unknown options to be ignored
    allowUnknownOptions = true

    // Stop parsing at first positional argument
    stopAtFirstPositional = false

    // Case sensitivity for options
    caseSensitive = true
}
```

### Terminal Configuration

```kotlin
settings {
    // Use different terminal implementation
    terminal = PlainTerminal()  // No colors
    terminal = MarkdownTerminal()  // Markdown output
    terminal = ANSITerminal(useStdErr = true)  // Colors to stderr

    // Customize help formatting
    helpFormatter = CustomHelpFormatter()
}
```

### Advanced Settings

```kotlin
settings {
    // Custom error handling
    onError = { error ->
        logger.error("Parsing failed: $error")
        exitProcess(1)
    }

    // Custom help handling
    onHelp = { helpText ->
        logger.info("Help requested")
        println(helpText)
        exitProcess(0)
    }

    // Locale for error messages
    locale = Locale.getDefault()
}
```

## Method Chaining

All builders support fluent method chaining:

```kotlin
val input by option("--input", "-i")
    .help("Input file path")
    .map { File(it) }
    .validate { file ->
        when {
            !file.exists() -> "File does not exist"
            !file.canRead() -> "Cannot read file"
            else -> null
        }
    }
    .required()
    .fromEnv("INPUT_FILE")

val verbosity by option("--verbose", "-v")
    .bool().list()
    .help("Increase verbosity (use multiple times)")
    .onlyInDomains(::process, ::analyze)
```

## Type Safety

Builders maintain type safety throughout the chain:

```kotlin
// Type progression:
val port by option("--port")    // OptionBuilder<String?>
    .int()                      // OptionBuilder<Int?>
    .default(8080)              // OptionBuilder<Int>
    .validate { port ->         // port is Int (not nullable)
        if (port < 1024) "Port must be >= 1024" else null
    }
// Final type: Int
```

## Common Patterns

### File Input/Output

```kotlin
val input by option("--input", "-i")
    .map { File(it) }
    .validate { file ->
        when {
            !file.exists() -> "Input file does not exist"
            !file.canRead() -> "Cannot read input file"
            else -> null
        }
    }
    .required()

val output by option("--output", "-o")
    .map { File(it) }
    .validate { file ->
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            "Output directory does not exist: ${parent.path}"
        } else null
    }
    .default { File("output.txt") }
```

### Network Configuration

```kotlin
val host by option("--host")
    .default("localhost")
    .validate { host ->
        if (!host.matches(Regex("""^[\w.-]+$"""))) {
            "Invalid hostname format"
        } else null
    }

val port by option("--port")
    .int()
    .default(8080)
    .validate { port ->
        when {
            port < 1 -> "Port must be positive"
            port > 65535 -> "Port must be <= 65535"
            port < 1024 && !isRoot() -> "Ports < 1024 require root privileges"
            else -> null
        }
    }
```

### Date/Time Options

```kotlin
val startDate by option("--start")
    .map(desc = "date in YYYY-MM-DD format") {
        it?.let { LocalDate.parse(it) }
    }

val endDate by option("--end")
    .map(desc = "date in YYYY-MM-DD format") {
        it?.let { LocalDate.parse(it) }
    }

// In constraints block:
constraints {
    custom("date-range") { args ->
        val start = args.startDate
        val end = args.endDate
        if (start != null && end != null && start.isAfter(end)) {
            "Start date must be before or equal to end date"
        } else null
    }
}
```