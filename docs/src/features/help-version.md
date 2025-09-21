# Help and Version Options

Argos provides built-in support for help and version options that automatically generate formatted output and handle standard CLI conventions. These options integrate seamlessly with the terminal system and provide professional, consistent interfaces.

## Basic Help and Version

Use the built-in `help()` and `version()` methods to create standard options:

```kotlin
class MyApp : Arguments() {
    val input by option("--input")
        .required()
        .help("Input file to process")

    val output by option("--output")
        .default("output.txt")
        .help("Output file location")

    val verbose by option("--verbose").bool()
        .help("Enable verbose logging")

    val help by help()                          // Creates --help option
    val version by version("1.0.0")             // Creates --version option
}
```

### Usage Examples

```bash
# Display help
myapp --help
myapp -h                    # Short form automatically available

# Display version
myapp --version
myapp -V                    # Short form automatically available

# Help takes precedence over other validation
myapp --help                # Shows help even without required --input
```

## Custom Help Configuration

### Help with Custom Options

```kotlin
class MyApp : Arguments() {
    val input by option("--input").required()
    val output by option("--output").default("output.txt")

    // Custom help option names
    val help by help("--help", "-h", "--usage")
        .help("Show this help message and exit")

    // Custom version with multiple formats
    val version by version("2.1.0", "--version", "-V", "--build-info")
        .help("Show version information and exit")
}
```

### Help Text Customization

```kotlin
class MyApp : Arguments(
    appName = "my-tool",
    appDescription = "A powerful CLI tool for data processing"
) {
    val input by option("--input", "-i")
        .required()
        .help("Input file to process (supports .json, .xml, .csv)")

    val format by option("--format", "-f")
        .oneOf("json", "xml", "csv")
        .default("json")
        .help("Output format (default: json)")

    val verbose by option("--verbose", "-v").bool()
        .help("Enable verbose output with detailed progress")

    val quiet by option("--quiet", "-q").bool()
        .help("Suppress all output except errors")
        .conflictsWith(::verbose)

    val help by help()
    val version by version("1.0.0")
}
```

### Generated Help Output

```bash
myapp --help
```

Output:
```
my-tool - A powerful CLI tool for data processing

Usage: my-tool [OPTIONS]

Options:
  -i, --input TEXT       Input file to process (supports .json, .xml, .csv) [required]
  -f, --format TEXT      Output format (default: json) [choices: json, xml, csv]
  -v, --verbose          Enable verbose output with detailed progress
  -q, --quiet            Suppress all output except errors
  -h, --help             Show this help message and exit
  -V, --version          Show version information and exit
```

## Advanced Help Features

### Section Organization

```kotlin
class BuildTool : Arguments(
    appName = "buildtool",
    appDescription = "Modern build system for multi-language projects"
) {
    // Input/Output section
    val sourceDir by option("--source-dir", "-s")
        .default("src/")
        .help("Source directory containing project files")

    val outputDir by option("--output-dir", "-o")
        .default("build/")
        .help("Output directory for build artifacts")

    val configFile by option("--config", "-c")
        .help("Build configuration file (default: buildtool.yml)")

    // Build options section
    val target by option("--target", "-t")
        .oneOf("debug", "release", "profile")
        .default("debug")
        .help("Build target with optimization settings")

    val parallel by option("--parallel", "-j").int()
        .default(4)
        .validate("Must be positive") { it > 0 }
        .help("Number of parallel build jobs")

    val clean by option("--clean")
        .bool()
        .help("Clean build directory before building")

    // Verbosity section
    val verbose by option("--verbose", "-v")
        .bool().list()
        .help("Increase verbosity (use multiple times: -v, -vv, -vvv)")

    val quiet by option("--quiet", "-q")
        .bool()
        .help("Suppress all output except errors")
        .conflictsWith(::verbose)

    val help by help()
    val version by version("2.1.0")
}
```

### Help with Domains

```kotlin
class GitTool : Arguments(
    appName = "git-tool",
    appDescription = "Enhanced Git workflow management"
) {
    val globalVerbose by option("--verbose", "-v").bool()
        .help("Enable verbose output for all commands")

    val commitDomain by domain("commit")
        .label("Commit changes")
        .help("Create a new commit with staged changes")

    val branchDomain by domain("branch")
        .label("Branch operations")
        .help("Create, list, or delete branches")

    val mergeDomain by domain("merge")
        .label("Merge branches")
        .help("Merge one branch into another")

    // Domain-specific options
    val message by option("--message", "-m")
        .onlyInDomains(::commitDomain)
        .help("Commit message")

    val branchName by option("--name")
        .onlyInDomains(::branchDomain)
        .help("Branch name for operations")

    val help by help()
    val version by version("1.5.0")
}
```

Domain help output:
```bash
git-tool commit --help
```

Output:
```
git-tool commit - Create a new commit with staged changes

Usage: git-tool commit [OPTIONS]

Options:
  -m, --message TEXT     Commit message
  -v, --verbose          Enable verbose output for all commands
  -h, --help             Show this help message and exit
```

## Version Information

### Basic Version

```kotlin
class MyApp : Arguments() {
    val version by version("1.0.0")
}
```

Output:
```bash
myapp --version
# Output: myapp 1.0.0
```

### Detailed Version Information

```kotlin
class MyApp : Arguments(
    appName = "my-tool"
) {
    val version by version(
        version = "2.1.0",
        buildInfo = mapOf(
            "Build Date" to "2023-12-15",
            "Git Commit" to "a1b2c3d",
            "Kotlin Version" to "1.9.20",
            "Platform" to "JVM"
        )
    )
}
```

Output:
```bash
my-tool --version
```

Output:
```
my-tool 2.1.0
Build Date: 2023-12-15
Git Commit: a1b2c3d
Kotlin Version: 1.9.20
Platform: JVM
```

### Version with Custom Format

```kotlin
class MyApp : Arguments() {
    val version by version("1.0.0") { appName, version, buildInfo ->
        buildString {
            appendLine("$appName version $version")
            if (buildInfo.isNotEmpty()) {
                appendLine("Build Information:")
                buildInfo.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
            }
            appendLine("Copyright (c) 2023 MyCompany")
        }
    }
}
```

## Help Text Styling

### Terminal-Aware Formatting

Argos automatically adapts help output to the terminal capabilities:

```kotlin
class MyApp : Arguments(
    useANSITerminal = true                      // Enable colors when supported
) {
    val input by option("--input")
        .required()
        .help("Input file to process")

    val output by option("--output")
        .help("Output file location")

    val help by help()
}
```

When ANSI is supported:
- Option names are **bold**
- Required options show **[required]** in red
- Default values show in *italics*
- Choices show in blue

When ANSI is not supported:
- Plain text formatting
- Clear visual hierarchy maintained
- All information preserved

### Custom Terminal

```kotlin
import onl.ycode.argos.terminal.MarkdownTerminal

class MyApp : Arguments(
    terminal = MarkdownTerminal()               // Generate Markdown help
) {
    val help by help()
}
```

Markdown output:
```markdown
## my-app

Usage: `my-app [OPTIONS]`

### Options

- `--input TEXT` - Input file to process **[required]**
- `--output TEXT` - Output file location
- `--help` - Show this help message and exit
```

## Eager Evaluation

Help and version options use eager evaluation to show output immediately:

```kotlin
class MyApp : Arguments() {
    val requiredInput by option("--input")
        .required()                             // This would normally fail validation

    val help by help()                          // Eager: shows help before validation
    val version by version("1.0.0")             // Eager: shows version before validation
}
```

```bash
# These work even though --input is missing
myapp --help                                    # Shows help, exits successfully
myapp --version                                 # Shows version, exits successfully

# This fails validation
myapp                                           # Error: --input is required
```

## Integration Patterns

### Application Metadata

```kotlin
class MyApp : Arguments(
    appName = "data-processor",
    appDescription = "High-performance data transformation pipeline"
) {
    val help by help()
    val version by version(
        version = BuildConfig.VERSION,          // From build system
        buildInfo = mapOf(
            "Build Time" to BuildConfig.BUILD_TIME,
            "Git SHA" to BuildConfig.GIT_SHA,
            "Branch" to BuildConfig.GIT_BRANCH
        )
    )
}
```

### Multi-Command Tools

```kotlin
class DockerTool : Arguments(
    appName = "docker-tool",
    appDescription = "Docker container management utility"
) {
    val runDomain by domain("run")
        .help("Run a new container from an image")

    val buildDomain by domain("build")
        .help("Build an image from a Dockerfile")

    val psDomain by domain("ps")
        .help("List running containers")

    val help by help()
    val version by version("3.2.1")
}
```

Global help shows all domains:
```bash
docker-tool --help
```

Output:
```
docker-tool - Docker container management utility

Usage: docker-tool [COMMAND] [OPTIONS]

Commands:
  run        Run a new container from an image
  build      Build an image from a Dockerfile
  ps         List running containers

Options:
  -h, --help     Show this help message and exit
  -V, --version  Show version information and exit

Use 'docker-tool COMMAND --help' for command-specific help.
```

## Error Handling with Help

### Validation Errors with Help Suggestions

```kotlin
class MyApp : Arguments() {
    val input by option("--input").required()
    val help by help()
}
```

When validation fails:
```bash
myapp --invalid-option
```

Output:
```
Error: No such option: --invalid-option

Did you mean --input?

Use 'myapp --help' for available options.
```

### Context-Sensitive Help

```kotlin
class BuildTool : Arguments() {
    val buildDomain by domain("build")

    val testDomain by domain("test")
        .required(::testDir)

    val testDir by option("--test-dir")

    val help by help()
}
```

```bash
buildtool test
```

Output:
```
Error: Option --test-dir is required when domain 'test' is active

Use 'buildtool test --help' for available options in the test domain.
```

## Best Practices

### 1. Always Include Help and Version

```kotlin
// Good: Standard help and version
class MyApp : Arguments() {
    // ... other options ...
    val help by help()
    val version by version("1.0.0")
}

// Avoid: Missing standard options
class MyApp : Arguments() {
    // ... options without help/version
}
```

### 2. Provide Descriptive Help Text

```kotlin
// Good: Clear, actionable help text
val timeout by option("--timeout").int()
    .default(30)
    .help("Connection timeout in seconds (default: 30)")

val format by option("--format")
    .oneOf("json", "xml", "csv")
    .help("Output format: json (compact), xml (structured), csv (tabular)")

// Avoid: Vague or redundant help
val timeout by option("--timeout").int()
    .help("Timeout value")                      // What kind of timeout?
```

### 3. Use Semantic Versioning

```kotlin
// Good: Semantic versioning
val version by version("2.1.0")                // major.minor.patch

// Consider: Build metadata for detailed information
val version by version(
    version = "2.1.0",
    buildInfo = mapOf(
        "Build" to BuildConfig.BUILD_NUMBER,
        "Date" to BuildConfig.BUILD_DATE
    )
)
```

### 4. Handle Multi-Command Scenarios

```kotlin
// Good: Clear command structure with global help
class MultiTool : Arguments() {
    val verboseDomain by domain("verbose")
    val quietDomain by domain("quiet")
        .conflictsWith(::verboseDomain)

    val help by help()
    val version by version("1.0.0")
}
```

### 5. Leverage Terminal Capabilities

```kotlin
// Good: Let Argos detect and use terminal capabilities
class MyApp : Arguments(
    useANSITerminal = true                      // Use colors when available
) {
    val help by help()
}

// Good: Override for specific output needs
class DocumentationApp : Arguments(
    terminal = MarkdownTerminal()               // Generate documentation
) {
    val help by help()
}
```

Help and version options provide essential user experience features that make CLI applications professional and user-friendly. They integrate seamlessly with the rest of the Argos system while providing consistent, well-formatted output across all platforms.