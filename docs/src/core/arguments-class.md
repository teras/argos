# Arguments Class

The `Arguments` class is the foundation of every Argos CLI application. It provides the configuration and structure for your command-line interface.

## Basic Structure

Every Argos application starts with a class that extends `Arguments`:

```kotlin
import onl.ycode.argos.Arguments

class MyApp : Arguments() {
    // Your options and configuration go here
}
```

## Constructor Configuration

The `Arguments` class provides extensive configuration options through its constructor:

```kotlin
class MyApp : Arguments(
    appName = "my-app",
    appDescription = "My awesome CLI tool",
    // ... other configuration options
) {
    // Options defined here...
}
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `appName` | `String` | Class name | Application name shown in help |
| `appDescription` | `String?` | `null` | App description for help text |
| `unknownOptionsAsPositionals` | `Boolean` | `false` | Treat unknown options as positional args |
| `defaultLongPrefix` | `String` | `"--"` | Default prefix for long options |
| `clusterChar` | `Char?` | `'-'` | Character for clustering short options (-abc) |
| `valueSeparators` | `Set<Char>` | `setOf('=', ':')` | Characters for option=value syntax |
| `didYouMean` | `Boolean` | `true` | Enable "did you mean" suggestions |
| `didYouMeanMax` | `Int` | `2` | Max edit distance for suggestions |
| `aggregateErrors` | `Boolean` | `true` | Show multiple errors at once |
| `maxAggregatedErrors` | `Int` | `20` | Max errors to show when aggregating |
| `terminal` | `Terminal?` | `null` | Custom terminal (default: auto-detect) |
| `useANSITerminal` | `Boolean` | `true` | Use ANSI colors when supported |
| `argumentFilePrefix` | `Char?` | `'@'` | Prefix for argument files (@file.txt) |
| `argumentSeparator` | `String` | `"|"` | Separator in help text (--opt\|-o) |

### Complete Configuration Example

```kotlin
class MyApp : Arguments(
    appName = "my-app",
    appDescription = "My awesome CLI tool with advanced features",
    unknownOptionsAsPositionals = false,
    defaultLongPrefix = "--",
    clusterChar = '-',
    valueSeparators = setOf('=', ':'),
    didYouMean = true,
    didYouMeanMax = 2,
    aggregateErrors = true,
    maxAggregatedErrors = 20,
    terminal = null,
    useANSITerminal = true,
    argumentFilePrefix = '@',
    argumentSeparator = "|"
) {
    val verbose by option("--verbose", "-v").bool().default(false)
    val inputFile by positional().required()
    val help by help()
}
```

## Key Configuration Features

### Application Identity

```kotlin
class MyApp : Arguments(
    appName = "file-processor",
    appDescription = "Process files with various transformations and filters"
) {
    // Options...
}
```

This sets the identity of your CLI tool, shown in help text and error messages.

### Error Handling Behavior

```kotlin
class MyApp : Arguments(
    aggregateErrors = true,           // Show multiple errors at once
    maxAggregatedErrors = 10,         // Limit to 10 errors
    didYouMean = true,                // Enable suggestions
    didYouMeanMax = 2                 // Max edit distance for "did you mean"
) {
    // Options...
}
```

Controls how parsing errors are reported to users.

### Option Parsing Behavior

```kotlin
class MyApp : Arguments(
    unknownOptionsAsPositionals = true,    // Treat unknown --opts as positional
    defaultLongPrefix = "--",              // Prefix for long options
    clusterChar = '-',                     // Allow -abc clustering
    valueSeparators = setOf('=', ':')      // Allow --opt=value and --opt:value
) {
    // Options...
}
```

Customizes how command-line arguments are parsed.

### Terminal Output

```kotlin
class MyApp : Arguments(
    useANSITerminal = true,               // Use colors when supported
    argumentSeparator = " | "             // Custom separator in help
) {
    // Options...
}
```

Controls terminal output formatting and appearance.

### Argument Files

```kotlin
class MyApp : Arguments(
    argumentFilePrefix = '@'              // Enable @file.txt syntax
) {
    // Options...
}
```

When enabled, users can specify `@filename.txt` to read arguments from a file:

```bash
# Instead of typing all arguments
myapp --input file1.txt --output dir/ --verbose --format json

# Users can create a file with arguments
echo "--input file1.txt --output dir/ --verbose --format json" > args.txt
myapp @args.txt
```

## Built-in Factory Methods

The `Arguments` class provides factory methods for common options:

### Help Option

```kotlin
class MyApp : Arguments() {
    // Default help option (-h, --help)
    val help by help()

    // Custom help option
    val customHelp by help("-?", "--help", "--usage")
}
```

### Version Option

```kotlin
class MyApp : Arguments() {
    // Default version option (--version)
    val version by version("1.0.0")

    // Custom version option
    val customVersion by version("1.2.3", "--version", "-V")
}
```

## Example Applications

### Simple CLI Tool

```kotlin
class SimpleApp : Arguments(
    appName = "simple",
    appDescription = "A simple command-line tool"
) {
    val verbose by option("--verbose", "-v").bool().default(false)
    val inputFile by positional().required()
    val help by help()
}
```

### Production CLI Tool

```kotlin
class ProductionApp : Arguments(
    appName = "prod-tool",
    appDescription = "Production-ready CLI tool with comprehensive error handling",
    aggregateErrors = true,
    maxAggregatedErrors = 15,
    didYouMean = true,
    didYouMeanMax = 2,
    useANSITerminal = true,
    argumentFilePrefix = '@'
) {
    val configFile by option("--config", "-c")
        .fromEnv("TOOL_CONFIG")
        .help("Configuration file path")

    val logLevel by option("--log-level", "-l")
        .enum<LogLevel>()
        .default(LogLevel.INFO)
        .help("Set logging level")

    val verbose by option("--verbose", "-v").bool().default(false)
        .help("Enable verbose output")

    val inputFiles by positional().list()
        .help("Input files to process (must be last)")

    val help by help()
    val version by version("2.1.0")
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
```

## Best Practices

### 1. Choose Meaningful Names

```kotlin
// Good: Clear, descriptive names
class FileProcessor : Arguments(
    appName = "file-processor",
    appDescription = "Process and transform files with various operations"
)

// Avoid: Generic or unclear names
class Tool : Arguments(appName = "tool")
```

### 2. Enable Error Aggregation

```kotlin
class MyApp : Arguments(
    aggregateErrors = true,        // Show multiple errors
    maxAggregatedErrors = 10       // Reasonable limit
) {
    // This helps users fix multiple issues at once
}
```

### 3. Use Argument Files for Complex Tools

```kotlin
class ComplexApp : Arguments(
    argumentFilePrefix = '@'       // Enable @file.txt syntax
) {
    // Useful for tools with many options
}
```

### 4. Provide Good Help Text

```kotlin
class MyApp : Arguments(
    appName = "my-tool",
    appDescription = "Clear, concise description of what the tool does"
) {
    // This appears in --help output
}
```

The Arguments class configuration sets the foundation for your entire CLI experience. Choose settings that match your tool's complexity and user expectations.