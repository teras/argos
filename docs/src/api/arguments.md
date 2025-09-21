# Arguments API Reference

The `Arguments` class is the foundation of Argos. It provides the DSL for defining command-line interfaces and handles parsing, validation, and help generation.

## Class Overview

```kotlin
abstract class Arguments {
    // DSL methods for defining options
    fun option(vararg switches: String): OptionBuilder<String?>
    fun positional(name: String): PositionalBuilder<String?>
    fun domain(id: String?): DomainBuilder

    // Parsing methods
    fun parse(args: Array<String>): ParseResult
    fun parseOrExit(args: Array<String>): ParseResult.Success

    // Information methods
    fun selectedDomain(): String?
    fun terminal(): Terminal

    // Configuration
    fun settings(block: SettingsBuilder.() -> Unit)
    fun constraints(block: ConstraintsBuilder.() -> Unit)
}
```

## Creating an Arguments Class

### Basic Structure

```kotlin
class MyApp : Arguments() {
    // Define your options here
    val verbose by option("--verbose", "-v")
    val inputFile by positional("input")

    init {
        settings {
            appName = "myapp"
            appDescription = "My awesome CLI application"
        }
    }
}
```

### Property Delegation

All options use Kotlin's property delegation via the `by` keyword:

```kotlin
// The property name becomes the internal identifier
val verbose by option("--verbose", "-v")
//  ^^^^^^^
//  This name is used for validation rules and error messages

// You can access the parsed value after calling parse()
if (verbose) {
    println("Verbose mode enabled")
}
```

## Parsing Methods

### `parse(args: Array<String>): ParseResult`

Parses command-line arguments and returns a result that you must handle:

```kotlin
fun main(args: Array<String>) {
    val app = MyApp()

    when (val result = app.parse(args)) {
        is Arguments.ParseResult.Success -> {
            // Arguments parsed successfully
            println("Input: ${app.inputFile}")
        }
        is Arguments.ParseResult.Help -> {
            // User requested help (--help)
            // Help has already been printed
        }
        is Arguments.ParseResult.Error -> {
            // Parsing failed
            // Error message has already been printed
            kotlin.system.exitProcess(1)
        }
    }
}
```

### `parseOrExit(args: Array<String>): ParseResult.Success`

Simplified parsing that automatically handles help and errors:

```kotlin
fun main(args: Array<String>) {
    val app = MyApp()

    // This will automatically:
    // - Print help and exit(0) if --help is used
    // - Print error and exit(1) if parsing fails
    // - Return only on successful parsing
    when (val result = app.parseOrExit(args)) {
        is Arguments.ParseResult.Success -> {
            // Always successful - no other cases to handle
            runApplication(app)
        }
    }
}
```

## ParseResult Types

```kotlin
sealed class ParseResult {
    object Success : ParseResult()
    object Help : ParseResult()
    object Error : ParseResult()
}
```

### Success
- Parsing completed successfully
- All options are available with their parsed values
- No validation errors occurred

### Help
- User requested help with `--help` or `-h`
- Help text has been printed to stdout
- Application should exit with code 0

### Error
- Parsing failed due to invalid arguments
- Error message has been printed to stderr
- Application should exit with code 1

## Settings Configuration

Use the `settings` block to configure your application:

```kotlin
init {
    settings {
        appName = "myapp"                    // Used in help text and error messages
        appDescription = "Description here"  // Shown in help header
        defaultLongPrefix = "--"             // Default prefix for long options
        defaultShortPrefix = "-"             // Default prefix for short options
        allowUnknownOptions = false          // Whether to allow undefined options
        stopAtFirstPositional = false       // Stop parsing at first positional arg
        terminal = ANSITerminal()            // Terminal implementation to use
    }
}
```

### Settings Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `appName` | `String?` | `null` | Application name for help text |
| `appDescription` | `String?` | `null` | Application description for help header |
| `defaultLongPrefix` | `String` | `"--"` | Default prefix for long options |
| `defaultShortPrefix` | `String` | `"-"` | Default prefix for short options |
| `allowUnknownOptions` | `Boolean` | `false` | Allow undefined options to be ignored |
| `stopAtFirstPositional` | `Boolean` | `false` | Stop parsing at first positional argument |
| `terminal` | `Terminal` | `ANSITerminal()` | Terminal implementation for output |

## Information Methods

### `selectedDomain(): String?`

Returns the currently selected domain (subcommand) after parsing:

```kotlin
class GitApp : Arguments() {
    val commit by domain("commit")
    val push by domain("push")

    init {
        settings { appName = "git" }
    }
}

fun main(args: Array<String>) {
    val app = GitApp()
    app.parseOrExit(args)

    when (app.selectedDomain()) {
        "commit" -> handleCommit()
        "push" -> handlePush()
        null -> println("No subcommand selected")
    }
}
```

### `terminal(): Terminal`

Returns the terminal instance used for formatting output:

```kotlin
val app = MyApp()
val terminal = app.terminal()

// Use for custom output
terminal.emitStrong("Important: ")
terminal.emitPlain("This is a message")
terminal.emitNewLine()
```

## Error Handling

### Validation Errors

When validation fails, Argos provides detailed error messages:

```kotlin
// If user provides: --count invalid
// Error output:
❌ Error: Invalid value 'invalid' for --count (expected an integer)

// If required option is missing:
❌ Error: Missing required option --input-file

// If constraint violation:
❌ Error: Option --count must be at least 1 (got 0)
```

### Custom Error Messages

You can provide custom error messages in constraints:

```kotlin
constraints {
    "port" requiredMin 1024 message "Port must be >= 1024 (ports below 1024 require root privileges)"
    "threads" requiredMax 16 message "Maximum 16 threads supported on this system"
}
```

## Advanced Usage

### Custom Validation

```kotlin
init {
    constraints {
        custom("file-exists") { args ->
            val file = java.io.File(args.inputFile)
            if (!file.exists()) {
                "Input file '${args.inputFile}' does not exist"
            } else null
        }
    }
}
```

### Environment Variables

```kotlin
val port by option("--port")
    .int()
    .fromEnv("SERVER_PORT")  // Read from environment variable if not provided
    .default(8080)
```

### Configuration from Files

```kotlin
val configFile by option("--config")
    .map { it?.let(::java.io.File) }

init {
    // Load configuration after parsing
    configFile?.let { file ->
        if (file.exists()) {
            loadConfiguration(file)
        }
    }
}
```

## Best Practices

### 1. Use Descriptive Names

```kotlin
// Good: Clear what this option does
val maxRetries by option("--max-retries").int().default(3)

// Avoid: Unclear abbreviations
val mr by option("--mr").int().default(3)
```

### 2. Provide Help Text

```kotlin
val timeout by option("--timeout")
    .int()
    .default(30)
    .help("Connection timeout in seconds")
```

### 3. Use Appropriate Defaults

```kotlin
val threads by option("--threads")
    .int()
    .default(Runtime.getRuntime().availableProcessors())
    .help("Number of worker threads")
```

### 4. Group Related Options

```kotlin
// Group networking options together
val host by option("--host").default("localhost")
val port by option("--port").int().default(8080)
val timeout by option("--timeout").int().default(30)

// Group output options together
val output by option("--output")
val format by option("--format").oneOf("json", "xml", "csv")
val pretty by option("--pretty")
```

### 5. Use Type-Safe Conversions

```kotlin
// Good: Type-safe enum
enum class LogLevel { DEBUG, INFO, WARN, ERROR }
val level by option("--level").enum<LogLevel>()

// Avoid: String validation
val level by option("--level").oneOf("debug", "info", "warn", "error")
```

## Migration from Other Libraries

### From kotlinx.cli

```kotlin
// kotlinx.cli
val input by argument(ArgType.String, description = "Input file")
val verbose by option(ArgType.Boolean, shortName = "v", description = "Verbose")

// Argos
val input by positional("input").help("Input file")
val verbose by option("--verbose", "-v").help("Verbose")
```

### From JCommander

```kotlin
// JCommander
@Parameter(names = ["--input", "-i"], description = "Input file", required = true)
var input: String = ""

// Argos
val input by option("--input", "-i").required().help("Input file")
```

### From Clikt

```kotlin
// Clikt
val input by argument(help = "Input file")
val verbose by option("--verbose", "-v").flag()

// Argos
val input by positional("input").help("Input file")
val verbose by option("--verbose", "-v").bool().help("Verbose mode")
```

## Thread Safety

The `Arguments` class is **not thread-safe**. Each instance should be used by only one thread. If you need to parse arguments in multiple threads, create separate instances:

```kotlin
// Don't do this
val sharedApp = MyApp()
// Multiple threads calling sharedApp.parse() - NOT SAFE

// Do this instead
fun parseInThread(args: Array<String>) {
    val app = MyApp() // New instance per thread
    app.parse(args)
}
```

## Memory Usage

Arguments instances are lightweight and designed for single-use:

- Create instance
- Configure options (in init block)
- Parse arguments
- Use parsed values
- Discard instance

For long-running applications that parse arguments multiple times, consider creating a factory function:

```kotlin
fun createApp(): MyApp = MyApp()

// Later...
val app1 = createApp()
app1.parse(firstArgs)

val app2 = createApp()
app2.parse(secondArgs)
```