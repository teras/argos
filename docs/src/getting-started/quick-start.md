# Quick Start

Get up and running with Argos in just 5 minutes! This guide will walk you through creating your first command-line application.

## 5-Minute CLI App

Let's build a simple file processing tool that demonstrates Argos's core features.

### Step 1: Create the Application Class

```kotlin
import onl.ycode.argos.*

class FileProcessor : Arguments() {
    // Basic flag option
    val verbose by option("--verbose", "-v")
        .help("Enable verbose output")

    // Option with type conversion and default value
    val threads by option("--threads", "-t")
        .int()
        .default(1)
        .help("Number of processing threads")

    // Enum option with validation
    val format by option("--format", "-f")
        .oneOf("json", "xml", "csv")
        .default("json")
        .help("Output format")

    // Positional arguments
    val inputFiles by positional("input-files")
        .list()
        .help("Files to process")

    val outputDir by positional("output-dir")
        .help("Output directory")

    // Application configuration
    init {
        settings {
            appName = "fileproc"
            appDescription = "A simple file processing tool"
        }

        // Add validation constraints
        constraints {
            "threads" requiredMin 1
            "threads" requiredMax 16
            "inputFiles" requiredMin 1
        }
    }
}
```

### Step 2: Add the Main Function

```kotlin
fun main(args: Array<String>) {
    val app = FileProcessor()

    when (val result = app.parseOrExit(args)) {
        is Arguments.ParseResult.Success -> {
            processFiles(app)
        }
        // parseOrExit automatically handles help and errors
    }
}

fun processFiles(app: FileProcessor) {
    if (app.verbose) {
        println("🔧 Starting file processor...")
        println("📁 Input files: ${app.inputFiles}")
        println("📂 Output directory: ${app.outputDir}")
        println("🧵 Threads: ${app.threads}")
        println("📋 Format: ${app.format}")
    }

    // Simulate file processing
    app.inputFiles.forEachIndexed { index, file ->
        if (app.verbose) {
            println("⚡ Processing $file [${index + 1}/${app.inputFiles.size}]")
        }

        // Your file processing logic here
        Thread.sleep(100) // Simulate work
    }

    println("✅ Processed ${app.inputFiles.size} files successfully!")
}
```

### Step 3: Test Your Application

Let's see what we've built:

```bash
# Show help
./fileproc --help
```

Output:
```
fileproc — A simple file processing tool

Usage:
  fileproc [--verbose] [--threads <int>] [--format <string>] <input-files>... <output-dir>

Arguments:
  <input-files>    Files to process
  <output-dir>     Output directory

Options:
  -v, --verbose          Enable verbose output
  -t, --threads <int>    Number of processing threads — type int [1]
  -f, --format <string>  Output format [json|xml|csv] [json]
```

```bash
# Basic usage
./fileproc file1.txt file2.txt output/

# With options
./fileproc --verbose --threads 4 --format xml *.txt /tmp/output/

# Short options
./fileproc -v -t 8 -f csv data/*.csv results/
```

## Key Features Demonstrated

### 1. Type Safety
```kotlin
val threads by option("--threads").int().default(1)
// ✅ threads is Int, not String
// ✅ Compile-time type checking
```

### 2. Validation
```kotlin
constraints {
    "threads" requiredMin 1      // Must be at least 1
    "threads" requiredMax 16     // Must be at most 16
    "inputFiles" requiredMin 1   // At least one input file
}
```

### 3. Help Generation
Automatic, beautiful help text with no extra code needed.

### 4. Error Handling
```kotlin
when (app.parseOrExit(args)) {
    is Arguments.ParseResult.Success -> { /* handle success */ }
    // Errors and help are automatically handled
}
```

## Common Patterns

### Optional vs Required

```kotlin
// Optional with default
val port by option("--port").int().default(8080)

// Required option
val host by option("--host").required()

// Optional positional
val configFile by positional("config").optional()

// Required positional
val inputFile by positional("input") // Required by default
```

### Lists and Multiple Values

```kotlin
// List of values
val files by option("--file").list()
// Usage: --file a.txt --file b.txt

// Multiple positional arguments
val sources by positional("sources").list()
// Usage: cmd file1.txt file2.txt file3.txt

// Set (unique values only)
val tags by option("--tag").set()
```

### Boolean Flags

```kotlin
// Simple flag
val verbose by option("--verbose", "-v")

// Negatable flag
val colors by option("--colors").negatable().default(true)
// Usage: --colors (true) or --no-colors (false)
```

### Enums and Choices

```kotlin
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

// Type-safe enum
val level by option("--level").enum<LogLevel>().default(LogLevel.INFO)

// String choices
val format by option("--format").oneOf("json", "xml", "yaml")
```

## Next Steps

Now that you've seen the basics, explore more advanced features:

### Subcommands
```kotlin
class GitLike : Arguments() {
    val commit by domain("commit").help("Record changes")
    val push by domain("push").help("Upload changes")
    val pull by domain("pull").help("Download changes")

    // Domain-specific options
    val message by option("--message", "-m")
        .onlyInDomains(::commit)
        .help("Commit message")
}
```

### Custom Types
```kotlin
val outputFile by option("--output")
    .map { it?.let(::File) }
    .help("Output file path")

val timeout by option("--timeout")
    .map(desc = "duration in seconds") { it?.toLongOrNull()?.let { Duration.ofSeconds(it) } }
    .default(Duration.ofMinutes(5))
```

### Advanced Validation
```kotlin
constraints {
    "output" requiredIf { input != null }
    exactlyOne("--json", "--xml", "--csv")
    atMostOne("--quiet", "--verbose")
}
```

## What's Next?

- **[First App Tutorial](first-app.md)**: Build a complete application step-by-step
- **[Basic Usage Guide](../guide/basic-usage.md)**: Learn all the core concepts
- **[Examples](../examples/basic-cli.md)**: See real-world applications
- **[API Reference](../api/arguments.md)**: Complete documentation

!!! tip "Pro Tips"
    - Use `parseOrExit()` for simple applications - it handles help and errors automatically
    - Add constraints early to catch user errors with helpful messages
    - Use descriptive help text - it becomes your application's documentation
    - Test with `--help` frequently to see how your CLI looks to users