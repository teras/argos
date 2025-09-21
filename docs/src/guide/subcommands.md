# Subcommands (Domains)

Argos provides a powerful domain system for creating subcommands in your CLI applications. Domains allow you to create sophisticated command-line interfaces with git-style subcommands, each with their own options and constraints.

## What are Domains?

Domains in Argos are subcommands that group related functionality. They're similar to subcommands in tools like `git`, `docker`, or `kubectl`:

```bash
# Git-style subcommands
git commit --message "Fix bug" --author "John"
git push --force origin main
git checkout -b feature/new-thing

# Your CLI with domains
myapp build --target release --optimize
myapp test --parallel --coverage
myapp deploy --environment production
```

## Basic Domain Usage

Create domains using the `domain()` function:

```kotlin
import onl.ycode.argos.Arguments

class MyTool : Arguments(
    appName = "mytool",
    appDescription = "A CLI tool with multiple commands"
) {
    // Define domains (subcommands)
    val buildDomain by domain("build")
    val testDomain by domain("test")
    val deployDomain by domain("deploy")

    // Global options (available in all domains)
    val verbose by option("--verbose", "-v").bool().default(false)
        .help("Enable verbose output")

    // Domain-specific options
    val target by option("--target").oneOf("debug", "release")
        .help("Build target")

    val parallel by option("--parallel").bool().default(false)
        .help("Run tests in parallel")

    val environment by option("--environment", "-e")
        .oneOf("dev", "staging", "production")
        .help("Deployment environment")

    val help by help()
}

fun main(args: Array<String>) {
    val tool = MyTool()

    tool.parse(args,
        onSuccess = {
            when {
                tool.buildDomain -> {
                    println("Building project...")
                    println("Target: ${tool.target}")
                    // Build logic here
                }
                tool.testDomain -> {
                    println("Running tests...")
                    println("Parallel: ${tool.parallel}")
                    // Test logic here
                }
                tool.deployDomain -> {
                    println("Deploying application...")
                    println("Environment: ${tool.environment}")
                    // Deploy logic here
                }
                else -> {
                    println("No command specified. Use --help for usage.")
                }
            }
        },
        onError = { error, _ ->
            println("Error: ${error.message}")
            kotlin.system.exitProcess(1)
        }
    )
}
```

## Domain Configuration

Domains can be extensively configured with labels, descriptions, and aliases:

```kotlin
class MyTool : Arguments() {
    val buildDomain by domain("build")
        .label("Build Project")
        .help("Compile and package the project")
        .aliases("compile", "make")

    val testDomain by domain("test")
        .label("Run Tests")
        .help("Execute the test suite with various options")
        .aliases("t", "check")

    val deployDomain by domain("deploy")
        .label("Deploy Application")
        .help("Deploy the application to specified environment")
        .aliases("d", "ship")

    // Options here...
}
```

With this configuration, users can invoke commands using aliases:

```bash
# All of these are equivalent
mytool build --target release
mytool compile --target release
mytool make --target release
```

## Domain-Scoped Constraints

One of the most powerful features of domains is the ability to define constraints that only apply when a specific domain is active:

### Basic Domain Requirements

```kotlin
class MyTool : Arguments() {
    val buildDomain by domain("build")
        .required(::target)                    // target is required in build domain
        .atLeast(::sources, 1)                 // at least one source required

    val deployDomain by domain("deploy")
        .required(::environment)               // environment required for deploy
        .required(::version)                   // version required for deploy

    val target by option("--target").oneOf("debug", "release")
    val sources by option("--source").list()
    val environment by option("--environment").oneOf("dev", "staging", "prod")
    val version by option("--version")
}
```

### Conditional Domain Requirements

```kotlin
class MyTool : Arguments() {
    val buildDomain by domain("build")
        .requireIfValue(::target, ::optimize) { it == "release" }  // optimize required for release builds
        .requireIfAnyPresent(::output, ::verbose, ::logLevel)      // output required if verbose or logLevel set

    val testDomain by domain("test")
        .requireIfAllPresent(::coverage, ::parallel, ::outputDir)  // coverage requires both parallel and outputDir

    val target by option("--target").oneOf("debug", "release")
    val optimize by option("--optimize").bool()
    val output by option("--output")
    val verbose by option("--verbose").bool()
    val logLevel by option("--log-level")
    val coverage by option("--coverage").bool()
    val parallel by option("--parallel").bool()
    val outputDir by option("--output-dir")
}
```

### Domain Group Constraints

```kotlin
class MyTool : Arguments() {
    val buildDomain by domain("build")
        .exactlyOne(::input, ::configFile)     // exactly one source of input
        .atMostOne(::verbose, ::quiet)         // can't be both verbose and quiet
        .atLeastOne(::target, ::defaultBuild)  // must specify target or use default

    val deployDomain by domain("deploy")
        .conflicts(::dryRun, ::force)          // can't dry-run and force at same time

    val input by option("--input")
    val configFile by option("--config")
    val verbose by option("--verbose").bool()
    val quiet by option("--quiet").bool()
    val target by option("--target")
    val defaultBuild by option("--default").bool()
    val dryRun by option("--dry-run").bool()
    val force by option("--force").bool()
}
```

## Domain Fragments and Inheritance

Domain fragments are reusable constraint templates that can be inherited by multiple domains:

```kotlin
class MyTool : Arguments() {
    // Fragment: common logging setup (not selectable from command line)
    val commonLogging by domain(fragment = true)
        .required(::logLevel)
        .atMostOne(::verbose, ::quiet)
        .conflicts(::silent, ::debug)

    // Fragment: common file operations
    val commonFileOps by domain(fragment = true)
        .required(::workingDir)
        .requireIfAnyPresent(::backup, ::input, ::output)

    // Concrete domains that inherit from fragments
    val buildDomain by domain("build")
        .inherits(::commonLogging, ::commonFileOps)
        .required(::target)
        .conflicts(::clean, ::incremental)

    val testDomain by domain("test")
        .inherits(::commonLogging)              // Only inherits logging constraints
        .required(::testDir)
        .atMostOne(::parallel, ::sequential)

    val deployDomain by domain("deploy")
        .inherits(::commonLogging, ::commonFileOps)  // Inherits both fragments
        .required(::environment)

    // Options used by fragments
    val logLevel by option("--log-level").enum<LogLevel>()
    val verbose by option("--verbose").bool()
    val quiet by option("--quiet").bool()
    val silent by option("--silent").bool()
    val debug by option("--debug").bool()
    val workingDir by option("--working-dir").default(".")
    val backup by option("--backup").bool()
    val input by option("--input")
    val output by option("--output")

    // Domain-specific options
    val target by option("--target").oneOf("debug", "release")
    val clean by option("--clean").bool()
    val incremental by option("--incremental").bool()
    val testDir by option("--test-dir").default("tests/")
    val parallel by option("--parallel").bool()
    val sequential by option("--sequential").bool()
    val environment by option("--environment").oneOf("dev", "staging", "prod")
}
```

## Restricting Options to Specific Domains

You can restrict options to only be available in specific domains:

```kotlin
class MyTool : Arguments() {
    val buildDomain by domain("build")
    val testDomain by domain("test")
    val deployDomain by domain("deploy")

    // Available in all domains
    val verbose by option("--verbose").bool().help("Enable verbose output")

    // Only available in build and deploy domains
    val target by option("--target").onlyInDomains(::buildDomain, ::deployDomain)
        .oneOf("debug", "release")

    // Only available in test domain
    val coverage by option("--coverage").onlyInDomains(::testDomain)
        .bool().help("Generate coverage report")

    // Only available in deploy domain
    val rollback by option("--rollback").onlyInDomains(::deployDomain)
        .bool().help("Rollback on failure")
}
```

## Complete Domain Example

Here's a comprehensive example showing advanced domain usage:

```kotlin
import onl.ycode.argos.Arguments
import onl.ycode.argos.parse

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
enum class BuildTarget { DEBUG, RELEASE, PROFILE }
enum class TestFramework { JUNIT, TESTNG, SPOCK }

class DevTool : Arguments(
    appName = "devtool",
    appDescription = "Development tool with build, test, and deploy commands",
    aggregateErrors = true,
    didYouMean = true
) {
    // ===== FRAGMENTS =====

    // Common logging configuration
    val commonLogging by domain(fragment = true)
        .required(::logLevel)
        .atMostOne(::verbose, ::quiet)
        .conflicts(::silent, ::debug)

    // Common file operations
    val commonFileOps by domain(fragment = true)
        .required(::workingDir)
        .requireIfAnyPresent(::configFile, ::outputDir)

    // Common CI/CD options
    val commonCiCd by domain(fragment = true)
        .requireIfValue(::ciMode, ::buildNumber) { it == true }
        .conflicts(::dryRun, ::force)

    // ===== CONCRETE DOMAINS =====

    val buildDomain by domain("build")
        .label("Build Project")
        .help("Compile and package the project with various options")
        .aliases("compile", "make", "b")
        .inherits(::commonLogging, ::commonFileOps, ::commonCiCd)
        .required(::target)
        .exactlyOne(::sourceDir, ::projectFile)
        .requireIfValue(::target, ::optimize) { it == BuildTarget.RELEASE }
        .conflicts(::clean, ::incremental)

    val testDomain by domain("test")
        .label("Run Tests")
        .help("Execute test suite with coverage and reporting")
        .aliases("t", "check")
        .inherits(::commonLogging)
        .required(::testDir)
        .atMostOne(::parallel, ::sequential)
        .requireIfAnyPresent(::coverage, ::reportFormat, ::outputDir)
        .requireIfValue(::framework, ::parallel) { it == TestFramework.JUNIT }

    val deployDomain by domain("deploy")
        .label("Deploy Application")
        .help("Deploy to various environments with safety checks")
        .aliases("d", "ship")
        .inherits(::commonLogging, ::commonFileOps, ::commonCiCd)
        .required(::environment)
        .required(::version)
        .exactlyOne(::artifact, ::buildFromSource)
        .requireIfValue(::environment, ::approvalRequired) { it == "production" }
        .conflicts(::skipTests, ::runAllTests)

    // ===== GLOBAL OPTIONS =====

    // Logging options (used by commonLogging fragment)
    val logLevel by option("--log-level", "-l").enum<LogLevel>().default(LogLevel.INFO)
        .help("Set logging verbosity")
    val verbose by option("--verbose", "-v").bool().default(false)
        .help("Enable verbose output")
    val quiet by option("--quiet", "-q").bool().default(false)
        .help("Suppress non-essential output")
    val silent by option("--silent").bool().default(false)
        .help("Suppress all output except errors")
    val debug by option("--debug").bool().default(false)
        .help("Enable debug mode with detailed output")

    // File operation options (used by commonFileOps fragment)
    val workingDir by option("--working-dir", "-w").default(".")
        .help("Working directory for operations")
    val configFile by option("--config", "-c")
        .help("Configuration file path")
    val outputDir by option("--output-dir", "-o")
        .help("Output directory for generated files")

    // CI/CD options (used by commonCiCd fragment)
    val ciMode by option("--ci").bool().default(false)
        .help("Enable CI mode optimizations")
    val buildNumber by option("--build-number")
        .help("Build number for CI environments")
    val dryRun by option("--dry-run").bool().default(false)
        .help("Show what would be done without executing")
    val force by option("--force", "-f").bool().default(false)
        .help("Force operation, bypass safety checks")

    // ===== BUILD-SPECIFIC OPTIONS =====

    val target by option("--target", "-t").enum<BuildTarget>()
        .onlyInDomains(::buildDomain)
        .help("Build target type")

    val sourceDir by option("--source-dir", "-s")
        .onlyInDomains(::buildDomain)
        .help("Source code directory")

    val projectFile by option("--project-file", "-p")
        .onlyInDomains(::buildDomain)
        .help("Project file to build")

    val optimize by option("--optimize", "-O").bool().default(false)
        .onlyInDomains(::buildDomain)
        .help("Enable optimization (required for release builds)")

    val clean by option("--clean").bool().default(false)
        .onlyInDomains(::buildDomain)
        .help("Clean before building")

    val incremental by option("--incremental", "-i").bool().default(false)
        .onlyInDomains(::buildDomain)
        .help("Enable incremental compilation")

    // ===== TEST-SPECIFIC OPTIONS =====

    val testDir by option("--test-dir").default("tests/")
        .onlyInDomains(::testDomain)
        .help("Directory containing tests")

    val framework by option("--framework").enum<TestFramework>().default(TestFramework.JUNIT)
        .onlyInDomains(::testDomain)
        .help("Testing framework to use")

    val parallel by option("--parallel", "-j").bool().default(false)
        .onlyInDomains(::testDomain)
        .help("Run tests in parallel")

    val sequential by option("--sequential").bool().default(false)
        .onlyInDomains(::testDomain)
        .help("Force sequential test execution")

    val coverage by option("--coverage").bool().default(false)
        .onlyInDomains(::testDomain)
        .help("Generate code coverage report")

    val reportFormat by option("--report-format").oneOf("html", "xml", "json")
        .onlyInDomains(::testDomain)
        .help("Test report format")

    // ===== DEPLOY-SPECIFIC OPTIONS =====

    val environment by option("--environment", "-e")
        .oneOf("dev", "staging", "production")
        .onlyInDomains(::deployDomain)
        .help("Target deployment environment")

    val version by option("--version", "-V")
        .onlyInDomains(::deployDomain)
        .help("Version to deploy")

    val artifact by option("--artifact", "-a")
        .onlyInDomains(::deployDomain)
        .help("Pre-built artifact to deploy")

    val buildFromSource by option("--build-from-source").bool().default(false)
        .onlyInDomains(::deployDomain)
        .help("Build from source before deploying")

    val approvalRequired by option("--require-approval").bool().default(false)
        .onlyInDomains(::deployDomain)
        .help("Require manual approval before deployment")

    val skipTests by option("--skip-tests").bool().default(false)
        .onlyInDomains(::deployDomain)
        .help("Skip test execution before deployment")

    val runAllTests by option("--run-all-tests").bool().default(false)
        .onlyInDomains(::deployDomain)
        .help("Run full test suite before deployment")

    // ===== BUILT-IN OPTIONS =====

    val help by help()
    val versionInfo by version("2.1.0")
}

fun main(args: Array<String>) {
    val tool = DevTool()

    tool.parse(args,
        onSuccess = {
            // Check which domain was selected
            when {
                tool.buildDomain -> handleBuild(tool)
                tool.testDomain -> handleTest(tool)
                tool.deployDomain -> handleDeploy(tool)
                else -> {
                    println("No command specified. Available commands:")
                    println("  build    - Build the project")
                    println("  test     - Run tests")
                    println("  deploy   - Deploy application")
                    println()
                    println("Use --help for detailed usage information.")
                }
            }
        },
        onError = { error, _ ->
            println("Error: ${error.message}")
            kotlin.system.exitProcess(1)
        }
    )
}

fun handleBuild(tool: DevTool) {
    println("🔨 Building project...")
    println("Target: ${tool.target}")
    println("Working directory: ${tool.workingDir}")

    if (tool.clean) {
        println("🧹 Cleaning previous build...")
    }

    if (tool.optimize) {
        println("⚡ Optimization enabled")
    }

    // Build implementation here...
}

fun handleTest(tool: DevTool) {
    println("🧪 Running tests...")
    println("Framework: ${tool.framework}")
    println("Test directory: ${tool.testDir}")

    if (tool.parallel) {
        println("🚀 Running tests in parallel")
    }

    if (tool.coverage) {
        println("📊 Generating coverage report")
        tool.reportFormat?.let { format ->
            println("Report format: $format")
        }
    }

    // Test implementation here...
}

fun handleDeploy(tool: DevTool) {
    println("🚀 Deploying application...")
    println("Environment: ${tool.environment}")
    println("Version: ${tool.version}")

    if (tool.environment == "production" && tool.approvalRequired) {
        println("⚠️  Production deployment requires approval")
        // Approval logic here...
    }

    if (tool.dryRun) {
        println("🔍 Dry run mode - no actual deployment")
    }

    // Deployment implementation here...
}
```

This comprehensive example demonstrates:

- **Fragment inheritance** for sharing common constraint logic
- **Domain-scoped constraints** that only apply when specific domains are active
- **Option restrictions** to limit availability to certain domains
- **Complex conditional requirements** based on option values
- **Domain aliases** for user convenience
- **Comprehensive help and documentation**
- **Real-world command structure** similar to popular CLI tools

## Usage Examples

With the above configuration, users can run commands like:

```bash
# Build commands
devtool build --target release --optimize --source-dir src/
devtool compile --target debug --clean --verbose  # using alias

# Test commands
devtool test --parallel --coverage --report-format html
devtool t --framework junit --verbose  # using alias

# Deploy commands
devtool deploy --environment production --version 1.2.3 --require-approval
devtool ship --environment dev --build-from-source --force  # using alias

# Help for specific domains
devtool build --help
devtool test --help
devtool deploy --help
```

The domain system in Argos provides the flexibility to create sophisticated CLI tools with complex constraint systems while maintaining clean, readable code and excellent user experience.