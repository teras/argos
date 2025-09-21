# Constraints

Constraints define relationships and requirements between options in your CLI application. They enable sophisticated validation logic that goes beyond individual option validation to enforce business rules and user experience requirements.

## Basic Requirements

### Required Options

Mark options as required using `.required()` for scalars or `.atLeast()` for collections:

```kotlin
class MyApp : Arguments() {
    // Basic required option (scalar)
    val inputFile by option("--input").required()

    // Require multiple elements (collections)
    val servers by option("--server").list().atLeast(2)

    // Require minimum occurrences (using boolean list)
    val verbosity by option("-v").bool().list().atLeast(1)
}
```

### Usage Examples

```bash
# Valid: provides required option
myapp --input file.txt

# Invalid: missing required option
myapp --output results/
# Error: Option --input is required

# Invalid: not enough servers
myapp --server srv1
# Error: Option --server requires at least 2 values
```

## Conditional Requirements

### Require If Any Present

Require an option when any of the specified options are present:

```kotlin
class MyApp : Arguments() {
    val input by option("--input")
    val output by option("--output")
    val config by option("--config")

    // Require backup when any output-related option is specified
    val backup by option("--backup").requireIfAnyPresent(::output, ::config)
}
```

```bash
# Valid: no output options, no backup needed
myapp --input file.txt

# Valid: output specified with backup
myapp --input file.txt --output results/ --backup

# Invalid: output without backup
myapp --input file.txt --output results/
# Error: Option --backup is required when any of --output, --config are present
```

### Require If All Present

Require an option when all specified options are present:

```kotlin
class MyApp : Arguments() {
    val database by option("--database")
    val username by option("--username")
    val ssl by option("--ssl").bool()

    // Only require password when both database and username are specified
    val password by option("--password").requireIfAllPresent(::database, ::username)
}
```

```bash
# Valid: partial database config, no password needed
myapp --database mydb

# Valid: complete database config with password
myapp --database mydb --username user --password secret

# Invalid: database and username without password
myapp --database mydb --username user
# Error: Option --password is required when all of --database, --username are present
```

### Require If Value

Require an option based on another option's value:

```kotlin
class MyApp : Arguments() {
    val mode by option("--mode").oneOf("fast", "slow", "balanced")
    val optimization by option("--optimization").bool()

    // Require optimization when mode is "fast"
    val threads by option("--threads").int()
        .requireIfValue(::mode) { it == "fast" }

    // Require specific config for SSL mode
    val protocol by option("--protocol").oneOf("http", "https")
    val certificate by option("--cert")
        .requireIfValue(::protocol) { it == "https" }
}
```

```bash
# Valid: slow mode, no threads needed
myapp --mode slow

# Valid: fast mode with threads
myapp --mode fast --threads 8

# Invalid: fast mode without threads
myapp --mode fast
# Error: Option --threads is required when --mode is 'fast'
```

### Require If Any Absent

Require an option when any of the specified options are absent (missing):

```kotlin
class MyApp : Arguments() {
    val host by option("--host")
    val port by option("--port").int()
    val config by option("--config")

    // Require config when either host OR port is missing
    val fallback by option("--fallback")
        .requireIfAnyAbsent(::host, ::port)

    // Require primary when either backup1 OR backup2 is missing
    val backup1 by option("--backup1")
    val backup2 by option("--backup2")
    val primary by option("--primary")
        .requireIfAnyAbsent(::backup1, ::backup2)
}
```

```bash
# Valid: both host and port provided, no fallback needed
myapp --host localhost --port 8080

# Valid: host missing, fallback provided
myapp --port 8080 --fallback default-config.yml

# Invalid: port missing, no fallback
myapp --host localhost
# Error: Option --fallback is required when any of --host, --port are absent
```

### Require If All Absent

Require an option when all of the specified options are absent (missing):

```kotlin
class MyApp : Arguments() {
    val host by option("--host")
    val port by option("--port").int()
    val config by option("--config")

    // Require config when BOTH host AND port are missing
    val fallback by option("--fallback")
        .requireIfAllAbsent(::host, ::port)

    // Require default when ALL custom options are missing
    val custom1 by option("--custom1")
    val custom2 by option("--custom2")
    val custom3 by option("--custom3")
    val default by option("--default")
        .requireIfAllAbsent(::custom1, ::custom2, ::custom3)
}
```

```bash
# Valid: both host and port provided
myapp --host localhost --port 8080

# Valid: only host provided (not all absent)
myapp --host localhost

# Valid: neither provided, but fallback given
myapp --fallback default-config.yml

# Invalid: both host and port missing, no fallback
myapp
# Error: Option --fallback is required when all of --host, --port are absent
```

## Group Constraints

### Exactly One

Require exactly one option from a group:

Note that when using `exactlyOneWith()`, the option that the method is called on (the receiver) is automatically included in the constraint group. For example, `val source by option("--source").exactlyOneWith(::input, ::config)` creates a constraint group containing `{source, input, config}`.

```kotlin
class MyApp : Arguments() {
    val input by option("--input")
    val configFile by option("--config")
    val template by option("--template")

    // Exactly one source of configuration
    val source by option("--source").exactlyOneWith(::input, ::configFile)

    // Exactly one output format
    val jsonOutput by option("--json").bool()
    val xmlOutput by option("--xml").bool()
    val yamlOutput by option("--yaml").bool()

    val format by option("--format").exactlyOneWith(::jsonOutput, ::xmlOutput, ::yamlOutput)
}
```

```bash
# Valid: exactly one source
myapp --source data.txt

# Valid: exactly one format
myapp --input file.txt --json

# Invalid: multiple sources
myapp --source data.txt --input file.txt
# Error: Exactly one of --source, --input, --config must be specified

# Invalid: no format specified
myapp --input file.txt
# Error: Exactly one of --format, --json, --xml, --yaml must be specified
```

### At Most One

Allow at most one option from a group:

Note that when using `atMostOneWith()`, the option that the method is called on (the receiver) is automatically included in the constraint group. For example, `val verbosity by option("--verbosity").atMostOneWith(::verbose, ::quiet)` creates a constraint group containing `{verbosity, verbose, quiet}`.

```kotlin
class MyApp : Arguments() {
    val verbose by option("--verbose").bool()
    val quiet by option("--quiet").bool()
    val silent by option("--silent").bool()

    // Can't be verbose and quiet simultaneously
    val verbosity by option("--verbosity").atMostOneWith(::verbose, ::quiet, ::silent)

    val cache by option("--cache").bool()
    val noCache by option("--no-cache").bool()

    // Cache options are mutually exclusive
    val cacheMode by option("--cache-mode").atMostOneWith(::cache, ::noCache)
}
```

```bash
# Valid: one verbosity option
myapp --verbose

# Valid: no verbosity options (all optional)
myapp --input file.txt

# Invalid: multiple verbosity options
myapp --verbose --quiet
# Error: At most one of --verbosity, --verbose, --quiet, --silent may be specified
```

### At Least One

Require at least one option from a group:

Note that when using `atLeastOneWith()`, the option that the method is called on (the receiver) is automatically included in the constraint group. For example, `val inputSource by option("--input").atLeastOneWith(::inputFile, ::inputDir)` creates a constraint group containing `{inputSource, inputFile, inputDir}`.

```kotlin
class MyApp : Arguments() {
    val inputFile by option("--input-file")
    val inputDir by option("--input-dir")
    val stdin by option("--stdin").bool()

    // Must specify at least one input source
    val inputSource by option("--input").atLeastOneWith(::inputFile, ::inputDir, ::stdin)

    val email by option("--email")
    val webhook by option("--webhook")
    val logFile by option("--log-file")

    // Must specify at least one notification method
    val notification by option("--notify").atLeastOneWith(::email, ::webhook, ::logFile)
}
```

```bash
# Valid: one input source
myapp --input-file data.txt

# Valid: multiple input sources
myapp --input-file data.txt --input-dir /data --stdin

# Invalid: no input sources
myapp --output results/
# Error: At least one of --input, --input-file, --input-dir, --stdin must be specified
```

## Conflict Constraints

### Basic Conflicts

Prevent options from being used together:

```kotlin
class MyApp : Arguments() {
    val verbose by option("--verbose").bool()
    val quiet by option("--quiet").bool()

    // Verbose and quiet conflict
    val debug by option("--debug").bool().conflictsWith(::quiet)

    val cache by option("--cache").bool()
    val force by option("--force").bool()

    // Cache and force are incompatible
    val rebuild by option("--rebuild").bool().conflictsWith(::cache, ::force)
}
```

```bash
# Valid: compatible options
myapp --verbose --debug

# Invalid: conflicting options
myapp --quiet --debug
# Error: Option --debug conflicts with --quiet

myapp --cache --force --rebuild
# Error: Option --rebuild conflicts with --cache, --force
```

## Complex Constraint Examples

### Build System Constraints

```kotlin
enum class BuildTarget { DEBUG, RELEASE, PROFILE }
enum class Architecture { X86, X64, ARM }

class BuildTool : Arguments() {
    val target by option("--target").enum<BuildTarget>().default(BuildTarget.DEBUG)
    val architecture by option("--arch").enum<Architecture>().default(Architecture.X64)

    // Optimization constraints
    val optimize by option("--optimize").bool()
        .requireIfValue(::target) { it == BuildTarget.RELEASE }  // Release builds must be optimized

    val debugInfo by option("--debug-info").bool()
        .conflictsWith(::optimize)  // Can't optimize and include debug info

    // Source constraints
    val sourceDir by option("--source-dir")
    val sourceFiles by option("--source-file").list()
    val projectFile by option("--project-file")

    val source by option("--source").exactlyOneWith(::sourceDir, ::sourceFiles, ::projectFile)

    // Output constraints
    val outputDir by option("--output-dir")
    val outputFile by option("--output-file")

    val output by option("--output").atMostOneWith(::outputDir, ::outputFile)

    // Platform-specific constraints
    val windowsSubsystem by option("--windows-subsystem")
        .requireIfValue(::architecture) { it == Architecture.X86 || it == Architecture.X64 }

    val armOptimizations by option("--arm-opts").bool()
        .requireIfValue(::architecture) { it == Architecture.ARM }

    // Clean vs incremental
    val clean by option("--clean").bool()
    val incremental by option("--incremental").bool()

    val buildMode by option("--build-mode").conflictsWith(::clean, ::incremental)
}
```

### Database Migration Tool

```kotlin
enum class MigrationDirection { UP, DOWN }
enum class Environment { DEV, STAGING, PROD }

class MigrationTool : Arguments() {
    val environment by option("--env").enum<Environment>().required()
    val direction by option("--direction").enum<MigrationDirection>().default(MigrationDirection.UP)

    // Safety constraints for production
    val force by option("--force").bool()
        .requireIfValue(::environment) { it == Environment.PROD }  // Prod requires explicit force

    val dryRun by option("--dry-run").bool()
        .conflictsWith(::force)  // Can't force and dry-run

    // Migration specification (exactly one method)
    val migrationFile by option("--file")
    val migrationId by option("--id")
    val toVersion by option("--to-version")
    val stepCount by option("--steps").int()

    val migration by option("--migration")
        .exactlyOneWith(::migrationFile, ::migrationId, ::toVersion, ::stepCount)

    // Backup requirements
    val backup by option("--backup").bool()
        .requireIfValue(::environment) { it == Environment.PROD }  // Always backup prod
        .requireIfValue(::direction) { it == MigrationDirection.DOWN }  // Always backup rollbacks

    val backupLocation by option("--backup-location")
        .requireIfAnyPresent(::backup)

    // Verification options
    val verify by option("--verify").bool()
    val rollbackOnError by option("--rollback-on-error").bool()
        .requireIfAnyPresent(::verify)
        .conflictsWith(::dryRun)
}
```

### Deployment Pipeline

```kotlin
enum class DeploymentStage { BUILD, TEST, DEPLOY, VERIFY }

class DeploymentPipeline : Arguments() {
    val stages by option("--stage").enum<DeploymentStage>().set()
        .validateCollection("At least one stage required") { it.isNotEmpty() }

    // Build stage requirements
    val buildConfig by option("--build-config")
        .requireIfValue(::stages) { DeploymentStage.BUILD in it }

    val buildTarget by option("--build-target")
        .requireIfValue(::stages) { DeploymentStage.BUILD in it }

    // Test stage requirements
    val testSuite by option("--test-suite").list()
        .requireIfValue(::stages) { DeploymentStage.TEST in it }
        .validateCollection("Test suite required for testing") {
            stages?.let { DeploymentStage.TEST !in it } ?: true || it.isNotEmpty()
        }

    // Deploy stage requirements
    val environment by option("--environment").oneOf("dev", "staging", "prod")
        .requireIfValue(::stages) { DeploymentStage.DEPLOY in it }

    val deployKey by option("--deploy-key")
        .requireIfValue(::stages) { DeploymentStage.DEPLOY in it }

    // Verification requirements
    val healthChecks by option("--health-check").list()
        .requireIfValue(::stages) { DeploymentStage.VERIFY in it }

    // Safety constraints
    val approvalRequired by option("--require-approval").bool()
        .requireIfValue(::environment) { it == "prod" }

    val rollbackPlan by option("--rollback-plan")
        .requireIfValue(::environment) { it == "prod" }
        .requireIfValue(::stages) { DeploymentStage.DEPLOY in it }

    // Conflicting options
    val skipTests by option("--skip-tests").bool()
        .conflictsWith(::testSuite)

    val forceDeploy by option("--force-deploy").bool()
        .conflictsWith(::approvalRequired)
}
```

## Domain-Scoped Constraints

Constraints can be applied only when specific domains are active. See the [Domain Constraints](../domains/domain-constraints.md) documentation for details on domain-scoped constraint syntax.

## Best Practices

### 1. Design Intuitive Relationships

```kotlin
// Good: Logical relationships users expect
val ssl by option("--ssl").bool()
val certificate by option("--cert").requireIfAnyPresent(::ssl)

val verbose by option("--verbose").bool()
val quiet by option("--quiet").bool().conflictsWith(::verbose)

// Avoid: Surprising or arbitrary constraints
val input by option("--input").requireIfAnyPresent(::verbose)  // Why?
```

### 2. Provide Clear Error Messages

```kotlin
// Good: The constraint methods generate clear messages
val backup by option("--backup").requireIfAnyPresent(::output)
// Error: Option --backup is required when any of --output are present

// Consider: Custom validation for complex business rules
fun validateBusinessRules(args: MyArgs): List<String> {
    val errors = mutableListOf<String>()
    if (args.environment == "prod" && !args.approvalRequired) {
        errors.add("Production deployments require explicit approval (--require-approval)")
    }
    return errors
}
```

### 3. Group Related Options

```kotlin
// Good: Logical groupings
class DatabaseApp : Arguments() {
    // Connection group
    val host by option("--host")
    val port by option("--port").int()
    val database by option("--database").required()

    // Authentication group (exactly one method)
    val password by option("--password").exactlyOneWith(::keyFile, ::token)
    val keyFile by option("--key-file")
    val token by option("--token")

    // SSL group (all or nothing)
    val ssl by option("--ssl").bool()
    val sslCert by option("--ssl-cert").requireIfAnyPresent(::ssl)
    val sslKey by option("--ssl-key").requireIfAnyPresent(::ssl)
}
```

### 4. Consider User Workflows

```kotlin
// Good: Supports common user workflows
class BackupTool : Arguments() {
    // Basic usage: just specify source
    val source by option("--source").required()

    // Advanced usage: multiple options with constraints
    val destination by option("--destination")
    val compress by option("--compress").bool()
    val encrypt by option("--encrypt").bool()

    // Encryption requires password
    val password by option("--password").requireIfAnyPresent(::encrypt)

    // Large backups should be compressed
    val sizeThreshold by option("--auto-compress-above").long()
        .requireIfAnyPresent(::compress)
}
```

### 5. Document Complex Constraints

```kotlin
class ComplexApp : Arguments() {
    // Document non-obvious relationships in help text
    val serverMode by option("--server-mode").bool()
        .help("Enable server mode (requires --port and --bind-address)")

    val port by option("--port").int()
        .requireIfAnyPresent(::serverMode)
        .help("Server port (required in server mode)")

    val bindAddress by option("--bind-address")
        .requireIfAnyPresent(::serverMode)
        .help("Server bind address (required in server mode)")
}
```

Constraints enable sophisticated CLI interfaces that guide users toward correct usage patterns while preventing invalid combinations. Design constraints that match user expectations and provide clear feedback when requirements aren't met.