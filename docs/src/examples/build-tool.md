# Build Tool Example

This example demonstrates a comprehensive build tool implementation using Argos, showcasing domains, constraints, validation, and real-world CLI patterns.

## Complete Implementation

```kotlin
import onl.ycode.argos.*
import java.io.File

enum class BuildTarget { DEBUG, RELEASE, PROFILE }
enum class Architecture { X86, X64, ARM, ARM64 }
enum class OutputFormat { EXECUTABLE, LIBRARY, STATIC_LIBRARY }
enum class OptimizationLevel { NONE, BASIC, AGGRESSIVE }

class BuildTool : Arguments(
    appName = "buildtool",
    appDescription = "Modern multi-platform build system with advanced optimization",
    aggregateErrors = true,
    didYouMean = true
) {
    // Global configuration
    val configFile by option("--config", "-c")
        .help("Build configuration file (default: buildtool.yml)")
        .fromEnv("BUILD_CONFIG")

    val workingDir by option("--working-dir", "-w")
        .default(".")
        .help("Working directory for build operations")
        .validate("Directory must exist") { File(it).isDirectory }

    val verbosity by option("-v", "--verbose")
        .bool().list()
        .help("Increase verbosity (use multiple times: -v, -vv, -vvv)")

    val quiet by option("--quiet", "-q")
        .bool()
        .help("Suppress all output except errors")
        .conflictsWith(::verbosity)

    // Build domains
    val buildDomain by domain("build")
        .label("Build Project")
        .help("Compile source code and generate build artifacts")
        .aliases("compile", "make")
        .required(::sourceInput)
        .exactlyOne(::target)
        .atMostOne(::debugSymbols, ::optimized)
        .requireIfValue(::target) { it == BuildTarget.RELEASE }
        .conflicts(::cleanBuild, ::incrementalBuild)

    val testDomain by domain("test")
        .label("Run Tests")
        .help("Execute test suites with optional coverage reporting")
        .aliases("check")
        .atLeastOne(::unitTests, ::integrationTests, ::e2eTests)
        .requireIfAnyPresent(::coverage, ::coverageFormat)
        .requireIfValue(::testFramework) { it == "custom" }

    val packageDomain by domain("package")
        .label("Package Artifacts")
        .help("Package built artifacts for distribution")
        .aliases("pack", "bundle")
        .required(::outputFormat)
        .requireIfValue(::outputFormat) { it == OutputFormat.LIBRARY }
        .requireIfAnyPresent(::signPackage, ::signingKey, ::signingCert)

    val cleanDomain by domain("clean")
        .label("Clean Build")
        .help("Remove build artifacts and temporary files")
        .aliases("purge")
        .atMostOne(::cleanAll, ::cleanArtifacts, ::cleanCache)

    val deployDomain by domain("deploy")
        .label("Deploy Artifacts")
        .help("Deploy built artifacts to target environments")
        .required(::deployTarget)
        .requireIfValue(::deployTarget) { it == "production" }
        .conflicts(::dryRun, ::autoApprove)

    // Source configuration
    val sourceInput by option("--source", "-s")
        .help("Source directory or file to build")
        .validate("Source must exist") { File(it).exists() }

    val includeDirectories by option("--include", "-I")
        .list()
        .help("Include directories for compilation")
        .validate("Include directory must exist") { File(it).isDirectory }

    val libraryPaths by option("--library-path", "-L")
        .list()
        .help("Library search paths")
        .validate("Library path must exist") { File(it).isDirectory }

    val defineConstants by option("--define", "-D")
        .list()
        .help("Preprocessor definitions (KEY=VALUE format)")
        .validate("Must be KEY=VALUE format") { it.contains("=") && it.split("=").size == 2 }

    // Build configuration
    val target by option("--target", "-t")
        .enum<BuildTarget>()
        .default(BuildTarget.DEBUG)
        .help("Build target configuration")

    val architecture by option("--arch", "-a")
        .enum<Architecture>()
        .set()
        .default(setOf(Architecture.X64))
        .help("Target architectures for multi-platform builds")

    val outputFormat by option("--output-format")
        .enum<OutputFormat>()
        .default(OutputFormat.EXECUTABLE)
        .help("Output artifact format")

    val outputDirectory by option("--output-dir", "-o")
        .default("build/")
        .help("Output directory for build artifacts")

    val outputName by option("--output-name")
        .help("Custom name for output artifacts")

    // Optimization and debugging
    val optimized by option("--optimize", "-O")
        .bool()
        .help("Enable optimization for release builds")

    val optimizationLevel by option("--optimization-level")
        .enum<OptimizationLevel>()
        .default(OptimizationLevel.BASIC)
        .help("Optimization level for release builds")

    val debugSymbols by option("--debug-symbols")
        .bool()
        .help("Include debug symbols in build artifacts")

    val profileInstrumentation by option("--profile")
        .bool()
        .help("Add profiling instrumentation")

    // Build behavior
    val parallel by option("--parallel", "-j")
        .int()
        .default(Runtime.getRuntime().availableProcessors())
        .validate("Parallel jobs must be positive") { it > 0 }
        .validate("Too many parallel jobs") { it <= 32 }
        .help("Number of parallel build jobs")

    val cleanBuild by option("--clean-build")
        .bool()
        .help("Clean before building (removes all artifacts)")

    val incrementalBuild by option("--incremental")
        .bool()
        .default(true)
        .help("Use incremental compilation")

    val continueOnError by option("--continue-on-error")
        .bool()
        .help("Continue building other targets on individual failures")

    // Testing configuration
    val unitTests by option("--unit-tests")
        .bool()
        .default(true)
        .help("Run unit tests")

    val integrationTests by option("--integration-tests")
        .bool()
        .help("Run integration tests")

    val e2eTests by option("--e2e-tests")
        .bool()
        .help("Run end-to-end tests")

    val testFramework by option("--test-framework")
        .oneOf("junit", "testng", "custom")
        .default("junit")
        .help("Testing framework to use")

    val customTestFramework by option("--custom-test-framework")
        .requireIfValue(::testFramework) { it == "custom" }
        .help("Custom test framework configuration")

    val testPattern by option("--test-pattern")
        .default("**/*Test.*")
        .help("Glob pattern for test file discovery")

    val coverage by option("--coverage")
        .bool()
        .help("Generate code coverage reports")

    val coverageFormat by option("--coverage-format")
        .set()
        .help("Coverage report formats (html, xml, json)")
        .validateCollection("At least one format required") {
            coverage != true || it.isNotEmpty()
        }

    val coverageThreshold by option("--coverage-threshold")
        .double()
        .validate("Coverage threshold must be 0-100") { it in 0.0..100.0 }
        .help("Minimum coverage percentage for build success")

    // Packaging configuration
    val packageVersion by option("--package-version")
        .help("Version string for packaged artifacts")

    val packageMetadata by option("--package-metadata")
        .help("Metadata file for package generation")

    val libraryVersion by option("--library-version")
        .requireIfValue(::outputFormat) { it == OutputFormat.LIBRARY }
        .help("Library version for shared library builds")

    val headerFiles by option("--header-files")
        .list()
        .requireIfValue(::outputFormat) { it == OutputFormat.LIBRARY }
        .help("Header files to include in library package")
        .validate("Header file must exist") { File(it).exists() }

    val signPackage by option("--sign-package")
        .bool()
        .help("Digitally sign the package")

    val signingKey by option("--signing-key")
        .requireIfAnyPresent(::signPackage)
        .help("Private key for package signing")
        .validate("Signing key must exist") { File(it).exists() }

    val signingCert by option("--signing-cert")
        .requireIfAnyPresent(::signPackage)
        .help("Certificate for package signing")
        .validate("Certificate must exist") { File(it).exists() }

    // Cleaning configuration
    val cleanAll by option("--clean-all")
        .bool()
        .help("Remove all build artifacts and caches")

    val cleanArtifacts by option("--clean-artifacts")
        .bool()
        .help("Remove only build artifacts")

    val cleanCache by option("--clean-cache")
        .bool()
        .help("Remove only build caches")

    // Deployment configuration
    val deployTarget by option("--deploy-target")
        .oneOf("development", "staging", "production")
        .help("Deployment target environment")

    val productionApproval by option("--production-approval")
        .requireIfValue(::deployTarget) { it == "production" }
        .help("Approval token for production deployments")
        .hidden()

    val deploymentConfig by option("--deployment-config")
        .help("Deployment configuration file")
        .validate("Config file must exist") { File(it).exists() }

    val dryRun by option("--dry-run")
        .bool()
        .help("Show what would be deployed without making changes")

    val autoApprove by option("--auto-approve")
        .bool()
        .help("Automatically approve deployment steps")

    val rollbackPlan by option("--rollback-plan")
        .requireIfValue(::deployTarget) { it == "production" }
        .help("Rollback plan for production deployments")

    // Built-in options
    val help by help()
    val version by version("2.1.0")
}

fun main(args: Array<String>) {
    val buildTool = BuildTool()

    buildTool.parse(args,
        onError = { error, _ ->
            System.err.println("Error: ${error.message}")
            buildTool.printUsage()
        }
    ) ?: return

    // Configure logging based on verbosity
    configureLogging(buildTool.verbosity, buildTool.quiet)

    // Execute the appropriate domain
    when {
        buildTool.buildDomain -> executeBuild(buildTool)
        buildTool.testDomain -> executeTests(buildTool)
        buildTool.packageDomain -> executePackaging(buildTool)
        buildTool.cleanDomain -> executeClean(buildTool)
        buildTool.deployDomain -> executeDeploy(buildTool)
        else -> {
            println("No command specified. Use --help for available commands.")
        }
    }
}

fun configureLogging(verbosity: Int, quiet: Boolean) {
    val level = when {
        quiet -> LogLevel.ERROR
        verbosity >= 3 -> LogLevel.TRACE
        verbosity >= 2 -> LogLevel.DEBUG
        verbosity >= 1 -> LogLevel.INFO
        else -> LogLevel.WARN
    }

    Logger.setLevel(level)
    Logger.info("Build tool starting with log level: $level")
}

fun executeBuild(config: BuildTool) {
    Logger.info("Starting build process...")

    // Validate build configuration
    validateBuildConfig(config)

    // Setup build environment
    val buildContext = BuildContext(
        sourceInput = config.sourceInput!!,
        target = config.target,
        architectures = config.architecture,
        outputDirectory = config.outputDirectory,
        outputName = config.outputName,
        includeDirectories = config.includeDirectories,
        libraryPaths = config.libraryPaths,
        defines = config.defineConstants.associate {
            val parts = it.split("=", limit = 2)
            parts[0] to parts[1]
        },
        optimized = config.optimized,
        debugSymbols = config.debugSymbols,
        parallel = config.parallel,
        incremental = config.incrementalBuild && !config.cleanBuild
    )

    // Execute build phases
    if (config.cleanBuild) {
        Logger.info("Cleaning build directory...")
        cleanBuildDirectory(config.outputDirectory)
    }

    Logger.info("Compiling source files...")
    val compileResult = compileSource(buildContext)

    if (compileResult.hasErrors && !config.continueOnError) {
        Logger.error("Compilation failed with errors")
        kotlin.system.exitProcess(1)
    }

    Logger.info("Linking artifacts...")
    val linkResult = linkArtifacts(buildContext, compileResult)

    if (linkResult.success) {
        Logger.info("Build completed successfully")
        Logger.info("Output artifacts: ${linkResult.artifacts.joinToString()}")
    } else {
        Logger.error("Build failed during linking")
        kotlin.system.exitProcess(1)
    }
}

fun executeTests(config: BuildTool) {
    Logger.info("Starting test execution...")

    val testSuites = mutableListOf<TestSuite>()

    if (config.unitTests) {
        testSuites.add(TestSuite.UNIT)
    }
    if (config.integrationTests) {
        testSuites.add(TestSuite.INTEGRATION)
    }
    if (config.e2eTests) {
        testSuites.add(TestSuite.E2E)
    }

    val testContext = TestContext(
        suites = testSuites,
        framework = config.testFramework,
        customFramework = config.customTestFramework,
        pattern = config.testPattern,
        coverage = config.coverage,
        coverageFormats = config.coverageFormat,
        coverageThreshold = config.coverageThreshold
    )

    val testResult = runTests(testContext)

    if (testResult.success) {
        Logger.info("All tests passed")
        if (config.coverage) {
            Logger.info("Coverage: ${testResult.coverage}%")
        }
    } else {
        Logger.error("Tests failed: ${testResult.failures} failures, ${testResult.errors} errors")
        kotlin.system.exitProcess(1)
    }
}

fun executePackaging(config: BuildTool) {
    Logger.info("Starting packaging process...")

    val packageContext = PackageContext(
        outputFormat = config.outputFormat,
        version = config.packageVersion,
        metadata = config.packageMetadata,
        libraryVersion = config.libraryVersion,
        headerFiles = config.headerFiles,
        signPackage = config.signPackage,
        signingKey = config.signingKey,
        signingCert = config.signingCert
    )

    val packageResult = createPackage(packageContext)

    if (packageResult.success) {
        Logger.info("Package created successfully: ${packageResult.packageFile}")
    } else {
        Logger.error("Packaging failed: ${packageResult.error}")
        kotlin.system.exitProcess(1)
    }
}

fun executeClean(config: BuildTool) {
    Logger.info("Starting clean process...")

    when {
        config.cleanAll -> cleanAll()
        config.cleanArtifacts -> cleanArtifacts()
        config.cleanCache -> cleanCache()
        else -> {
            // Default: clean artifacts
            cleanArtifacts()
        }
    }

    Logger.info("Clean completed successfully")
}

fun executeDeploy(config: BuildTool) {
    Logger.info("Starting deployment process...")

    val deployContext = DeployContext(
        target = config.deployTarget!!,
        config = config.deploymentConfig,
        dryRun = config.dryRun,
        autoApprove = config.autoApprove,
        rollbackPlan = config.rollbackPlan,
        productionApproval = config.productionApproval
    )

    if (config.dryRun) {
        Logger.info("DRY RUN: Would deploy to ${config.deployTarget}")
        simulateDeployment(deployContext)
    } else {
        val deployResult = performDeployment(deployContext)

        if (deployResult.success) {
            Logger.info("Deployment to ${config.deployTarget} completed successfully")
        } else {
            Logger.error("Deployment failed: ${deployResult.error}")
            kotlin.system.exitProcess(1)
        }
    }
}

// Implementation stubs for the example
data class BuildContext(
    val sourceInput: String,
    val target: BuildTarget,
    val architectures: Set<Architecture>,
    val outputDirectory: String,
    val outputName: String?,
    val includeDirectories: List<String>,
    val libraryPaths: List<String>,
    val defines: Map<String, String>,
    val optimized: Boolean,
    val debugSymbols: Boolean,
    val parallel: Int,
    val incremental: Boolean
)

data class CompileResult(val hasErrors: Boolean, val objects: List<String>)
data class LinkResult(val success: Boolean, val artifacts: List<String>)

enum class TestSuite { UNIT, INTEGRATION, E2E }
data class TestContext(
    val suites: List<TestSuite>,
    val framework: String,
    val customFramework: String?,
    val pattern: String,
    val coverage: Boolean,
    val coverageFormats: Set<String>,
    val coverageThreshold: Double?
)

data class TestResult(
    val success: Boolean,
    val failures: Int,
    val errors: Int,
    val coverage: Double?
)

data class PackageContext(
    val outputFormat: OutputFormat,
    val version: String?,
    val metadata: String?,
    val libraryVersion: String?,
    val headerFiles: List<String>,
    val signPackage: Boolean,
    val signingKey: String?,
    val signingCert: String?
)

data class PackageResult(val success: Boolean, val packageFile: String?, val error: String?)

data class DeployContext(
    val target: String,
    val config: String?,
    val dryRun: Boolean,
    val autoApprove: Boolean,
    val rollbackPlan: String?,
    val productionApproval: String?
)

data class DeployResult(val success: Boolean, val error: String?)

enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

object Logger {
    private var currentLevel = LogLevel.INFO

    fun setLevel(level: LogLevel) { currentLevel = level }
    fun info(message: String) { if (currentLevel <= LogLevel.INFO) println("[INFO] $message") }
    fun error(message: String) { if (currentLevel <= LogLevel.ERROR) System.err.println("[ERROR] $message") }
}

// Stub implementations
fun validateBuildConfig(config: BuildTool) { /* Implementation */ }
fun cleanBuildDirectory(dir: String) { /* Implementation */ }
fun compileSource(context: BuildContext): CompileResult = CompileResult(false, emptyList())
fun linkArtifacts(context: BuildContext, compileResult: CompileResult): LinkResult =
    LinkResult(true, listOf("app.exe"))
fun runTests(context: TestContext): TestResult = TestResult(true, 0, 0, 85.5)
fun createPackage(context: PackageContext): PackageResult =
    PackageResult(true, "app-1.0.0.zip", null)
fun cleanAll() { /* Implementation */ }
fun cleanArtifacts() { /* Implementation */ }
fun cleanCache() { /* Implementation */ }
fun simulateDeployment(context: DeployContext) { /* Implementation */ }
fun performDeployment(context: DeployContext): DeployResult = DeployResult(true, null)
```

## Usage Examples

### Basic Build

```bash
# Simple debug build
buildtool build --source src/

# Release build with optimization
buildtool build --source src/ --target release --optimize
```

### Multi-Platform Build

```bash
# Build for multiple architectures
buildtool build --source src/ --arch x64 --arch arm64 --target release

# Cross-compilation with specific output
buildtool build --source src/ --arch arm --output-name myapp-arm --output-dir dist/
```

### Testing with Coverage

```bash
# Run all tests with coverage
buildtool test --unit-tests --integration-tests --coverage --coverage-format html --coverage-format xml

# Run specific test types
buildtool test --e2e-tests --test-framework testng
```

### Packaging and Signing

```bash
# Create signed package
buildtool package --output-format library --library-version 2.1.0 \
  --sign-package --signing-key private.key --signing-cert cert.pem

# Package with metadata
buildtool package --package-version 1.0.0 --package-metadata metadata.json
```

### Production Deployment

```bash
# Production deployment with approvals
buildtool deploy --deploy-target production \
  --production-approval $APPROVAL_TOKEN \
  --rollback-plan rollback.yml \
  --deployment-config prod-config.yml

# Dry run deployment
buildtool deploy --deploy-target staging --dry-run
```

### Complex Workflow

```bash
# Complete build, test, and package workflow
buildtool build --source src/ --target release --optimize --parallel 8
buildtool test --unit-tests --integration-tests --coverage --coverage-threshold 80
buildtool package --output-format executable --package-version 1.0.0
```

## Key Features Demonstrated

1. **Multi-Domain Architecture**: Build, test, package, clean, and deploy domains
2. **Complex Constraints**: Required fields, group constraints, conditional requirements
3. **Type Safety**: Enums, validation, and type conversions
4. **Environment Integration**: Configuration from files and environment variables
5. **Error Handling**: Aggregated errors with helpful messages
6. **Flexible Configuration**: Multiple ways to specify the same options
7. **Production-Ready Features**: Signing, approvals, rollback plans
8. **User Experience**: Aliases, help text, "did you mean" suggestions

This example shows how Argos can handle real-world complexity while maintaining clean, maintainable code and excellent user experience.