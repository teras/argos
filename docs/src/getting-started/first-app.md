# Your First Application

In this tutorial, we'll build a complete command-line application from scratch: a **log analyzer** that processes log files and generates reports. This will demonstrate all the key concepts you need to build production-ready CLIs with Argos.

## What We're Building

Our log analyzer will:
- 📁 Process multiple log files
- 🔍 Filter by log level and date range
- 📊 Generate different report formats
- ⚡ Support parallel processing
- 🎯 Validate all inputs thoroughly
- 💡 Provide helpful error messages

## Project Setup

First, create a new Kotlin project and add Argos as described in the [Installation Guide](installation.md).

Create the main file:

=== "src/commonMain/kotlin/LogAnalyzer.kt"

    ```kotlin
    import onl.ycode.argos.*
    import kotlin.system.exitProcess
    ```

## Step 1: Define the Application Structure

Let's start with the basic application class:

```kotlin
enum class LogLevel { DEBUG, INFO, WARN, ERROR }
enum class OutputFormat { SUMMARY, DETAILED, JSON, CSV }

class LogAnalyzer : Arguments() {
    init {
        settings {
            appName = "loganalyzer"
            appDescription = "Analyze log files and generate insightful reports"
        }
    }
}
```

## Step 2: Add Input Options

Now let's add options for specifying input files and basic configuration:

```kotlin
class LogAnalyzer : Arguments() {
    // Input files - required positional arguments
    val logFiles by positional("log-files")
        .list()
        .help("Log files to analyze (supports wildcards)")

    // Output configuration
    val outputFile by option("--output", "-o")
        .help("Output file (default: stdout)")

    val format by option("--format", "-f")
        .enum<OutputFormat>()
        .default(OutputFormat.SUMMARY)
        .help("Report format")

    // Filtering options
    val level by option("--level", "-l")
        .enum<LogLevel>()
        .help("Minimum log level to include")

    val since by option("--since")
        .help("Include logs after this date (YYYY-MM-DD)")

    val until by option("--until")
        .help("Include logs before this date (YYYY-MM-DD)")

    // Processing options
    val threads by option("--threads", "-t")
        .int()
        .default(1)
        .help("Number of processing threads")

    val verbose by option("--verbose", "-v")
        .help("Enable verbose output")

    val quiet by option("--quiet", "-q")
        .help("Suppress all output except results")

    init {
        settings {
            appName = "loganalyzer"
            appDescription = "Analyze log files and generate insightful reports"
        }
    }
}
```

## Step 3: Add Validation Constraints

Let's add comprehensive validation to ensure the user provides valid inputs:

```kotlin
class LogAnalyzer : Arguments() {
    // ... options from above ...

    init {
        settings {
            appName = "loganalyzer"
            appDescription = "Analyze log files and generate insightful reports"
        }

        constraints {
            // Basic validation
            "logFiles" requiredMin 1
            "threads" requiredMin 1
            "threads" requiredMax 32

            // Conflicting options
            atMostOne("--verbose", "--quiet")

            // Conditional requirements
            "until" requiredIf { since != null }
        }
    }
}
```

## Step 4: Add Custom Type Conversions

Let's add support for date parsing and file validation:

```kotlin
import kotlinx.datetime.LocalDate

class LogAnalyzer : Arguments() {
    // ... previous options ...

    // Convert string dates to LocalDate
    val since by option("--since")
        .map(desc = "date in YYYY-MM-DD format") { dateString ->
            dateString?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: Exception) {
                    null // Return null for invalid dates - Argos will show error
                }
            }
        }
        .help("Include logs after this date (YYYY-MM-DD)")

    val until by option("--until")
        .map(desc = "date in YYYY-MM-DD format") { dateString ->
            dateString?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        }
        .help("Include logs before this date (YYYY-MM-DD)")

    // Convert output path to File
    val outputFile by option("--output", "-o")
        .map { it?.let(::java.io.File) }
        .help("Output file (default: stdout)")

    // ... rest of the class ...
}
```

## Step 5: Implement the Main Function

Now let's implement the main logic:

```kotlin
fun main(args: Array<String>) {
    val analyzer = LogAnalyzer()

    when (val result = analyzer.parseOrExit(args)) {
        is Arguments.ParseResult.Success -> {
            try {
                runAnalysis(analyzer)
            } catch (e: Exception) {
                if (!analyzer.quiet) {
                    println("❌ Error: ${e.message}")
                }
                exitProcess(1)
            }
        }
        // parseOrExit handles help and error cases automatically
    }
}

fun runAnalysis(analyzer: LogAnalyzer) {
    if (analyzer.verbose) {
        printAnalysisInfo(analyzer)
    }

    // Validate log files exist
    val validFiles = analyzer.logFiles.mapNotNull { path ->
        val file = java.io.File(path)
        if (file.exists() && file.canRead()) {
            file
        } else {
            if (!analyzer.quiet) {
                println("⚠️  Warning: Cannot read file '$path', skipping...")
            }
            null
        }
    }

    if (validFiles.isEmpty()) {
        throw IllegalArgumentException("No readable log files found")
    }

    // Process files
    val logs = processLogFiles(validFiles, analyzer)

    // Filter logs
    val filteredLogs = filterLogs(logs, analyzer)

    // Generate report
    val report = generateReport(filteredLogs, analyzer)

    // Output results
    outputReport(report, analyzer)
}

fun printAnalysisInfo(analyzer: LogAnalyzer) {
    println("🔍 Log Analysis Configuration:")
    println("   Files: ${analyzer.logFiles.joinToString(", ")}")
    analyzer.level?.let { println("   Min Level: $it") }
    analyzer.since?.let { println("   Since: $it") }
    analyzer.until?.let { println("   Until: $it") }
    println("   Format: ${analyzer.format}")
    println("   Threads: ${analyzer.threads}")
    analyzer.outputFile?.let { println("   Output: ${it.path}") }
    println()
}
```

## Step 6: Implement Core Logic

Let's add the actual log processing logic:

```kotlin
data class LogEntry(
    val timestamp: kotlinx.datetime.LocalDateTime,
    val level: LogLevel,
    val message: String,
    val source: String
)

data class AnalysisReport(
    val totalEntries: Int,
    val entriesByLevel: Map<LogLevel, Int>,
    val timeRange: Pair<kotlinx.datetime.LocalDateTime, kotlinx.datetime.LocalDateTime>?,
    val topErrors: List<String>,
    val filesProcessed: List<String>
)

fun processLogFiles(files: List<java.io.File>, analyzer: LogAnalyzer): List<LogEntry> {
    if (analyzer.verbose) {
        println("📁 Processing ${files.size} log files...")
    }

    // Simulate log parsing (in real app, you'd parse actual log formats)
    val logs = mutableListOf<LogEntry>()

    files.forEach { file ->
        if (analyzer.verbose) {
            println("   📄 Processing ${file.name}")
        }

        // Simulate reading and parsing log entries
        // In a real implementation, you'd parse actual log formats
        repeat(kotlin.random.Random.nextInt(100, 1000)) {
            logs.add(
                LogEntry(
                    timestamp = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()),
                    level = LogLevel.values().random(),
                    message = "Sample log message ${kotlin.random.Random.nextInt()}",
                    source = file.name
                )
            )
        }
    }

    if (analyzer.verbose) {
        println("✅ Processed ${logs.size} log entries")
        println()
    }

    return logs
}

fun filterLogs(logs: List<LogEntry>, analyzer: LogAnalyzer): List<LogEntry> {
    var filtered = logs

    // Filter by level
    analyzer.level?.let { minLevel ->
        filtered = filtered.filter { entry ->
            entry.level.ordinal >= minLevel.ordinal
        }
    }

    // Filter by date range
    analyzer.since?.let { since ->
        filtered = filtered.filter { entry ->
            entry.timestamp.date >= since
        }
    }

    analyzer.until?.let { until ->
        filtered = filtered.filter { entry ->
            entry.timestamp.date <= until
        }
    }

    if (analyzer.verbose && filtered.size != logs.size) {
        println("🔍 Filtered to ${filtered.size} entries (from ${logs.size})")
    }

    return filtered
}

fun generateReport(logs: List<LogEntry>, analyzer: LogAnalyzer): AnalysisReport {
    val entriesByLevel = logs.groupBy { it.level }
        .mapValues { it.value.size }

    val timeRange = if (logs.isNotEmpty()) {
        val sorted = logs.sortedBy { it.timestamp }
        sorted.first().timestamp to sorted.last().timestamp
    } else null

    val topErrors = logs
        .filter { it.level == LogLevel.ERROR }
        .map { it.message }
        .groupBy { it }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .take(5)
        .map { "${it.first} (${it.second} times)" }

    val filesProcessed = logs.map { it.source }.distinct()

    return AnalysisReport(
        totalEntries = logs.size,
        entriesByLevel = entriesByLevel,
        timeRange = timeRange,
        topErrors = topErrors,
        filesProcessed = filesProcessed
    )
}

fun outputReport(report: AnalysisReport, analyzer: LogAnalyzer) {
    val output = when (analyzer.format) {
        OutputFormat.SUMMARY -> formatSummaryReport(report)
        OutputFormat.DETAILED -> formatDetailedReport(report)
        OutputFormat.JSON -> formatJsonReport(report)
        OutputFormat.CSV -> formatCsvReport(report)
    }

    // Output to file or stdout
    analyzer.outputFile?.let { file ->
        file.writeText(output)
        if (!analyzer.quiet) {
            println("📄 Report written to ${file.path}")
        }
    } ?: println(output)
}

fun formatSummaryReport(report: AnalysisReport): String = buildString {
    appendLine("📊 Log Analysis Summary")
    appendLine("=" * 50)
    appendLine()
    appendLine("📈 Total Entries: ${report.totalEntries}")
    appendLine("📁 Files Processed: ${report.filesProcessed.size}")
    appendLine()

    appendLine("📊 Entries by Level:")
    LogLevel.values().forEach { level ->
        val count = report.entriesByLevel[level] ?: 0
        val percentage = if (report.totalEntries > 0) {
            (count.toDouble() / report.totalEntries * 100).toInt()
        } else 0
        appendLine("   ${level.name.padEnd(5)}: ${count.toString().padStart(6)} ($percentage%)")
    }

    appendLine()
    report.timeRange?.let { (start, end) ->
        appendLine("⏱️  Time Range: $start to $end")
    }

    if (report.topErrors.isNotEmpty()) {
        appendLine()
        appendLine("🔥 Top Errors:")
        report.topErrors.forEach { error ->
            appendLine("   • $error")
        }
    }
}

// ... implement other format functions similarly ...
```

## Step 7: Test Your Application

Let's test the application with various inputs:

```bash
# Show help
./loganalyzer --help

# Basic usage
./loganalyzer app.log error.log

# With filtering
./loganalyzer --level ERROR --since 2024-01-01 *.log

# Generate detailed JSON report
./loganalyzer --format JSON --output report.json logs/*.log

# Quiet mode with multiple threads
./loganalyzer --quiet --threads 4 --format CSV --output summary.csv /var/log/*.log
```

## Step 8: Add Advanced Features

### Custom Validation

```kotlin
init {
    constraints {
        // ... existing constraints ...

        // Custom validation: ensure date range makes sense
        custom("date-range") { args ->
            val since = args.since
            val until = args.until

            if (since != null && until != null && since > until) {
                "Since date ($since) must be before until date ($until)"
            } else null
        }
    }
}
```

### Environment Variable Support

```kotlin
val threads by option("--threads", "-t")
    .int()
    .default(1)
    .fromEnv("LOG_ANALYZER_THREADS") // Read from environment variable
    .help("Number of processing threads")
```

### Configuration File Support

```kotlin
val configFile by option("--config", "-c")
    .map { it?.let(::java.io.File) }
    .help("Configuration file path")

// In your main function:
if (analyzer.configFile?.exists() == true) {
    loadConfiguration(analyzer.configFile!!, analyzer)
}
```

## Complete Example

Here's the complete, working application:

<details>
<summary>Click to see the full LogAnalyzer.kt</summary>

```kotlin
import onl.ycode.argos.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.system.exitProcess

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
enum class OutputFormat { SUMMARY, DETAILED, JSON, CSV }

data class LogEntry(
    val timestamp: LocalDateTime,
    val level: LogLevel,
    val message: String,
    val source: String
)

data class AnalysisReport(
    val totalEntries: Int,
    val entriesByLevel: Map<LogLevel, Int>,
    val timeRange: Pair<LocalDateTime, LocalDateTime>?,
    val topErrors: List<String>,
    val filesProcessed: List<String>
)

class LogAnalyzer : Arguments() {
    val logFiles by positional("log-files")
        .list()
        .help("Log files to analyze (supports wildcards)")

    val outputFile by option("--output", "-o")
        .map { it?.let(::java.io.File) }
        .help("Output file (default: stdout)")

    val format by option("--format", "-f")
        .enum<OutputFormat>()
        .default(OutputFormat.SUMMARY)
        .help("Report format")

    val level by option("--level", "-l")
        .enum<LogLevel>()
        .help("Minimum log level to include")

    val since by option("--since")
        .map(desc = "date in YYYY-MM-DD format") { dateString ->
            dateString?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        }
        .help("Include logs after this date (YYYY-MM-DD)")

    val until by option("--until")
        .map(desc = "date in YYYY-MM-DD format") { dateString ->
            dateString?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        }
        .help("Include logs before this date (YYYY-MM-DD)")

    val threads by option("--threads", "-t")
        .int()
        .default(1)
        .help("Number of processing threads")

    val verbose by option("--verbose", "-v")
        .help("Enable verbose output")

    val quiet by option("--quiet", "-q")
        .help("Suppress all output except results")

    init {
        settings {
            appName = "loganalyzer"
            appDescription = "Analyze log files and generate insightful reports"
        }

        constraints {
            "logFiles" requiredMin 1
            "threads" requiredMin 1
            "threads" requiredMax 32
            atMostOne("--verbose", "--quiet")
            "until" requiredIf { since != null }
        }
    }
}

fun main(args: Array<String>) {
    val analyzer = LogAnalyzer()

    when (analyzer.parseOrExit(args)) {
        is Arguments.ParseResult.Success -> {
            try {
                runAnalysis(analyzer)
            } catch (e: Exception) {
                if (!analyzer.quiet) {
                    println("❌ Error: ${e.message}")
                }
                exitProcess(1)
            }
        }
    }
}

// ... implement remaining functions as shown above ...
```

</details>

## What You've Learned

Congratulations! You've built a complete command-line application that demonstrates:

- ✅ **Type Safety**: Converting strings to proper types (dates, files, enums)
- ✅ **Validation**: Multiple constraint types and custom validation
- ✅ **Error Handling**: Graceful error messages and exit codes
- ✅ **Help Generation**: Automatic, professional help text
- ✅ **Flexibility**: Multiple output formats and filtering options
- ✅ **User Experience**: Verbose/quiet modes and progress indicators

## Next Steps

- **[Basic Usage Guide](../guide/basic-usage.md)**: Learn about all available option types
- **[Validation Guide](../features/validation.md)**: Master constraint systems
- **[Subcommands](../guide/subcommands.md)**: Build complex multi-command applications
- **[Examples](../examples/)**: See more real-world applications
- **[API Reference](../api/arguments.md)**: Complete documentation

!!! tip "Best Practices"
    - Always validate user input thoroughly
    - Provide helpful error messages
    - Use type-safe conversions instead of manual parsing
    - Test your CLI with various inputs and edge cases
    - Consider adding shell completions for better user experience