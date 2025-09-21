# Parsing Methods

Argos provides flexible parsing methods that allow you to handle different scenarios from simple command-line tools to complex applications with sophisticated error handling and user feedback.

## Basic Parsing Methods

### parse()

The most straightforward parsing method that returns null on errors:

```kotlin
class MyApp : Arguments() {
    val input by option("--input").required()
    val output by option("--output").default("output.txt")
    val verbose by option("--verbose").bool()

    val help by help()
    val version by version("1.0.0")
}

fun main(args: Array<String>) {
    val app = MyApp()

    app.parse(args) ?: return

    println("Input: ${app.input}")
    println("Output: ${app.output}")
    println("Verbose: ${app.verbose}")
}
```

### parse() with Callbacks

More flexible parsing with success and error callbacks:

```kotlin
fun main(args: Array<String>) {
    val app = MyApp()

    val result = app.parse(args,
        onSuccess = { parsedArgs ->
            println("Successfully parsed arguments!")
            println("Input: ${parsedArgs.input}")
            println("Output: ${parsedArgs.output}")
        },
        onError = { error, args ->
            println("Parse failed: ${error.message}")
            println("Use --help for usage information")
        }
    )

    if (result == null) {
        // Parsing failed, error callback was called
        return
    }

    // Continue with application logic
    processFiles(result)
}
```

## Advanced Parsing Patterns

### Handling Different Error Types

```kotlin
import onl.ycode.argos.ParseError
import onl.ycode.argos.MissingOptionError
import onl.ycode.argos.InvalidValueError
import onl.ycode.argos.ConstraintViolationError

fun main(args: Array<String>) {
    val app = MyApp()

    app.parse(args,
        onSuccess = {
            // Continue with application logic
        },
        onError = { error, _ ->
            when (error) {
                is MissingOptionError -> {
                    println("Missing required option: ${error.option}")
                    println("Use --help to see all required options")
                }
                is InvalidValueError -> {
                    println("Invalid value '${error.value}' for option ${error.option}")
                    println("Expected: ${error.expectedType}")
                }
                is ConstraintViolationError -> {
                    println("Constraint violation: ${error.message}")
                    if (error.suggestions.isNotEmpty()) {
                        println("Suggestions:")
                        error.suggestions.forEach { println("  - $it") }
                    }
                }
                else -> {
                    println("Parse error: ${error.message}")
                }
            }
        }
    ) ?: return
}
```

### Conditional Parsing

```kotlin
class ConfigurableApp : Arguments() {
    val configFile by option("--config")
    val input by option("--input")
    val output by option("--output")
}

fun main(args: Array<String>) {
    val app = ConfigurableApp()

    // First pass: check for config file
    val firstResult = app.parse(args,
        onError = { _, _ -> /* Ignore first pass errors */ }
    )

    if (firstResult == null && app.configFile != null) {
        // Load additional arguments from config file
        val configArgs = loadArgsFromConfig(app.configFile!!)
        val combinedArgs = configArgs + args

        app.parse(combinedArgs) ?: return
    } else if (firstResult == null) {
        println("Error: Failed to parse arguments")
        return
    }

    // Proceed with parsed arguments
}

fun loadArgsFromConfig(configFile: String): Array<String> {
    // Load configuration and convert to command line arguments
    // Implementation depends on config format (JSON, YAML, properties, etc.)
    return emptyArray()
}
```

## Error Aggregation

### Multiple Error Reporting

```kotlin
class MyApp : Arguments(
    aggregateErrors = true,                 // Show multiple errors at once
    maxAggregatedErrors = 10               // Limit number of errors shown
) {
    val email by option("--email")
        .validate("Must contain @") { it.contains("@") }
        .required()

    val port by option("--port").int()
        .validate("Must be valid port") { it in 1..65535 }
        .required()

    val count by option("--count").int()
        .validate("Must be positive") { it > 0 }
        .default(1)
}

fun main(args: Array<String>) {
    val app = MyApp()

    app.parse(arrayOf("--email", "invalid", "--port", "99999"),
        onError = { error, _ ->
            // With aggregateErrors = true, this will show:
            // Error: Multiple validation errors:
            // - Invalid value for --email: 'invalid' - Must contain @
            // - Invalid value for --port: '99999' - Must be valid port
            println(error.message)
        }
    ) ?: return
}
```

### Error Prioritization

```kotlin
fun main(args: Array<String>) {
    val app = MyApp()

    val result = app.parse(args,
        onSuccess = { parsedArgs ->
            // Validate business logic after parsing
            val businessErrors = validateBusinessLogic(parsedArgs)
            if (businessErrors.isNotEmpty()) {
                businessErrors.forEach { println("Business rule violation: $it") }
                return@parse null  // Return null to indicate failure
            }

            // All validation passed
            runApplication(parsedArgs)
            parsedArgs  // Return parsed args
        },
        onError = { error, args ->
            when (error) {
                is MissingOptionError -> {
                    println("Missing required option: ${error.option}")
                    app.printUsage()
                }
                is InvalidValueError -> {
                    println("Invalid value: ${error.message}")
                    app.printUsage()
                }
                else -> {
                    println("Parse error: ${error.message}")
                    println("Use --help for detailed usage")
                }
            }
        }
    ) ?: return
}

fun validateBusinessLogic(args: MyApp): List<String> {
    val errors = mutableListOf<String>()

    // Custom business validation that can't be expressed as constraints
    if (args.input != null && args.output != null) {
        if (File(args.input!!).absolutePath == File(args.output!!).absolutePath) {
            errors.add("Input and output files cannot be the same")
        }
    }

    return errors
}
```

## Domain-Aware Parsing

### Multi-Command Parsing

```kotlin
class GitTool : Arguments() {
    val commitDomain by domain("commit")
        .required(::message)

    val branchDomain by domain("branch")
        .exactlyOne(::create, ::delete, ::list)

    val mergeDomain by domain("merge")
        .required(::sourceBranch)
        .conflicts(::fastForward, ::noFastForward)

    val message by option("--message", "-m")
        .onlyInDomains(::commitDomain)

    val create by option("--create").bool()
        .onlyInDomains(::branchDomain)

    val delete by option("--delete").bool()
        .onlyInDomains(::branchDomain)

    val list by option("--list").bool()
        .onlyInDomains(::branchDomain)

    val sourceBranch by option("--source-branch")
        .onlyInDomains(::mergeDomain)

    val fastForward by option("--ff").bool()
        .onlyInDomains(::mergeDomain)

    val noFastForward by option("--no-ff").bool()
        .onlyInDomains(::mergeDomain)

    val help by help()
}

fun main(args: Array<String>) {
    val app = GitTool()

    app.parse(args,
        onError = { error, _ ->
            println("Error: ${error.message}")

            // Provide domain-specific help
            when {
                args.any { it == "commit" } -> println("Use 'git-tool commit --help' for commit options")
                args.any { it == "branch" } -> println("Use 'git-tool branch --help' for branch options")
                args.any { it == "merge" } -> println("Use 'git-tool merge --help' for merge options")
                else -> println("Use 'git-tool --help' for available commands")
            }
        }
    ) ?: return

    when {
        app.commitDomain -> {
            println("Creating commit with message: ${app.message}")
            performCommit(app.message!!)
        }
        app.branchDomain -> {
            when {
                app.create -> createBranch()
                app.delete -> deleteBranch()
                app.list -> listBranches()
            }
        }
        app.mergeDomain -> {
            val strategy = when {
                app.fastForward -> MergeStrategy.FAST_FORWARD
                app.noFastForward -> MergeStrategy.NO_FAST_FORWARD
                else -> MergeStrategy.AUTO
            }
            performMerge(app.sourceBranch!!, strategy)
        }
        else -> {
            println("No command specified. Use --help for available commands.")
        }
    }
}
```

## Interactive Parsing

### Progressive Option Collection

```kotlin
class InteractiveApp : Arguments() {
    val batchMode by option("--batch").bool()
    val configFile by option("--config")
    val inputFiles by option("--input").list()
    val outputDir by option("--output-dir")
}

fun main(args: Array<String>) {
    val app = InteractiveApp()

    app.parse(args,
        onError = { error, _ ->
            if (app.batchMode) {
                // In batch mode, fail immediately
                println("Batch mode error: ${error.message}")
            } else {
                // In interactive mode, collect missing information
                println("Some options are missing. Let's collect them interactively:")
                collectMissingOptions(app, error)
            }
        }
    ) ?: return

    runApplication(app)
}

fun collectMissingOptions(app: InteractiveApp, error: ParseError) {
    println("Error: ${error.message}")

    // Collect missing required options interactively
    if (app.inputFiles.isEmpty()) {
        print("Enter input files (space-separated): ")
        val files = readLine()?.split(" ") ?: emptyList()
        // Would need to re-parse with the additional arguments
    }

    if (app.outputDir == null) {
        print("Enter output directory: ")
        val outputDir = readLine()
        // Would need to re-parse with the additional arguments
    }
}
```

### Validation Feedback Loop

```kotlin
fun main(args: Array<String>) {
    val app = MyApp()
    var attempts = 0
    val maxAttempts = 3

    while (attempts < maxAttempts) {
        val result = app.parse(args,
            onError = { error, _ ->
                attempts++

                if (attempts >= maxAttempts) {
                    println("Too many parse failures. Exiting.")
                    return@parse null
                }

                println("Parse attempt $attempts failed: ${error.message}")

                // Provide contextual help and potentially modify args
                when (error) {
                    is MissingOptionError -> {
                        println("Would you like to provide ${error.option} interactively? (y/n)")
                        val response = readLine()
                        if (response?.lowercase() == "y") {
                            print("Enter value for ${error.option}: ")
                            val value = readLine()
                            if (value != null) {
                                args = args + arrayOf(error.option, value)
                            }
                        }
                    }
                    else -> {
                        println("Use --help for usage information")
                        return@parse null
                    }
                }
            }
        )

        if (result != null) break  // Success
        if (attempts >= maxAttempts) return
    }
}
```

## Integration Patterns

### Framework Integration

```kotlin
// Spring Boot integration example
@Component
class ArgosConfigurer {

    @EventListener
    fun configureFromArgs(event: ApplicationReadyEvent) {
        val args = event.applicationContext.environment
            .getProperty("spring.application.args", Array<String>::class.java)
            ?: emptyArray()

        val cliArgs = MyApp()
        cliArgs.parse(args,
            onSuccess = { configureApplication(it) },
            onError = { error, _ ->
                logger.error("CLI argument parse error: ${error.message}")
                // Decide whether to fail startup or use defaults
            }
        )
    }
}

// Testing integration
class MyAppTest {
    @Test
    fun testValidArguments() {
        val app = MyApp()
        val args = arrayOf("--input", "test.txt", "--output", "result.txt")

        val result = app.parse(args)
        assertNotNull(result)
        assertEquals("test.txt", app.input)
        assertEquals("result.txt", app.output)
    }

    @Test
    fun testInvalidArguments() {
        val app = MyApp()
        val args = arrayOf("--invalid-option", "value")

        val result = app.parse(args)
        assertNull(result)

        assertTrue(exception.message.contains("invalid-option"))
    }
}
```

### Logging Integration

```kotlin
import java.util.logging.Logger
import java.util.logging.Level

class LoggingApp : Arguments() {
    val logLevel by option("--log-level")
        .enum<Level>()
        .default(Level.INFO)

    val logFile by option("--log-file")

    val input by option("--input").required()
}

fun main(args: Array<String>) {
    val app = LoggingApp()

    app.parse(args) ?: return

    // Configure logging based on parsed arguments
    val logger = Logger.getLogger("MyApp")
    logger.level = app.logLevel

    if (app.logFile != null) {
        val handler = java.util.logging.FileHandler(app.logFile)
        logger.addHandler(handler)
    }

    logger.info("Application starting with input: ${app.input}")

    // Continue with application logic
}
```

## Best Practices

### 1. Choose the Right Parsing Method

```kotlin
// Simple applications: parse
fun simpleMain(args: Array<String>) {
    val app = SimpleApp()
    app.parse(args) ?: return
    runApp(app)
}

// Complex applications: parse with callbacks
fun complexMain(args: Array<String>) {
    val app = ComplexApp()
    app.parse(args,
        onSuccess = { runComplexApp(it) },
        onError = { error, _ ->
            println("Error: ${error.message}")
            app.printUsage()
        }
    ) ?: return
```

### 2. Provide Helpful Error Messages

```kotlin
// Good: Contextual error handling
catch (e: ParseError) {
    when (e) {
        is MissingOptionError -> {
            println("Missing required option: ${e.option}")
            println("Example: myapp ${e.option} <value>")
        }
        is InvalidValueError -> {
            println("Invalid value '${e.value}' for ${e.option}")
            println("Expected: ${e.expectedType}")
            if (e.suggestions.isNotEmpty()) {
                println("Did you mean: ${e.suggestions.joinToString()}")
            }
        }
        else -> println("Error: ${e.message}")
    }
    println("\nUse --help for detailed usage information")
}
```

### 3. Handle Edge Cases

```kotlin
// Good: Handle empty arguments
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("No arguments provided. Use --help for usage information.")
        kotlin.system.exitProcess(1)
    }

    val app = MyApp()
    // Continue with parsing...
}

// Good: Handle help/version early
fun main(args: Array<String>) {
    if (args.any { it in listOf("--help", "-h", "--version", "-V") }) {
        val app = MyApp()
        app.parse(args)  // Let help/version options handle themselves
        return
    }

    // Continue with normal parsing...
}
```

### 4. Structure Complex Applications

```kotlin
// Good: Separate parsing from business logic
fun main(args: Array<String>) {
    val config = parseArgumentsToConfig(args)
    val application = createApplication(config)
    application.run()
}

fun parseArgumentsToConfig(args: Array<String>): ApplicationConfig? {
    val cliArgs = MyApp()
    return cliArgs.parse(args,
        onError = { error, _ -> handleParseError(error) }
    )?.let { ApplicationConfig.fromCliArgs(it) }
}
```

Argos parsing methods provide flexibility for different application architectures while maintaining consistent error handling and user experience across simple scripts and complex multi-command applications.