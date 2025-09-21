# Domain System Overview

The Argos domain system enables sophisticated multi-command CLI applications with subcommands, modes, and constraint inheritance. Domains provide a powerful way to organize options, enforce context-specific requirements, and create complex CLI interfaces that scale with application complexity.

## What Are Domains?

Domains represent different modes or subcommands in your CLI application. Each domain can have:

- **Specific option requirements** that only apply when the domain is active
- **Constraint inheritance** from other domains (fragments)
- **Aliases** for alternative command names
- **Help text** and labels for documentation
- **Option scoping** to limit options to specific domains

## Basic Domain Usage

### Simple Subcommands

```kotlin
class GitTool : Arguments() {
    // Define domains (subcommands)
    val commitDomain by domain("commit")
        .label("Commit changes")
        .help("Create a new commit with staged changes")

    val branchDomain by domain("branch")
        .label("Branch operations")
        .help("Create, list, or delete branches")

    val mergeDomain by domain("merge")
        .label("Merge branches")
        .help("Merge one branch into another")

    // Global options (available in all domains)
    val verbose by option("--verbose", "-v").bool()
        .help("Enable verbose output")

    // Domain-specific options
    val message by option("--message", "-m")
        .onlyInDomains(::commitDomain)
        .help("Commit message")

    val branchName by option("--branch-name")
        .onlyInDomains(::branchDomain, ::mergeDomain)
        .help("Target branch name")

    val help by help()
    val version by version("1.0.0")
}
```

### Usage Examples

```bash
# Commit command with domain-specific option
git-tool commit --message "Fix authentication bug" --verbose

# Branch command
git-tool branch --branch-name feature/new-auth --verbose

# Global help shows all domains
git-tool --help

# Domain-specific help
git-tool commit --help
git-tool branch --help
```

## Domain Types

### 1. Concrete Domains (Selectable)

Standard domains that users can select from the command line:

```kotlin
class BuildTool : Arguments() {
    val buildDomain by domain("build")
        .label("Build the project")
        .help("Compile and package the application")
        .aliases("compile", "make")           // Alternative names

    val testDomain by domain("test")
        .label("Run tests")
        .help("Execute the test suite")
        .aliases("check")

    val deployDomain by domain("deploy")
        .label("Deploy application")
        .help("Deploy to target environment")
}
```

### 2. Fragment Domains (Templates)

Constraint templates that can be inherited by other domains:

```kotlin
class MicroserviceTool : Arguments() {
    // Fragment domain - not selectable, used for inheritance
    val commonAuth by domain(fragment = true)
        .required(::apiKey)
        .atMostOne(::username, ::serviceAccount)

    val deployDomain by domain("deploy")
        .inherits(::commonAuth)              // Inherits auth requirements
        .required(::environment)

    val monitorDomain by domain("monitor")
        .inherits(::commonAuth)              // Inherits same auth requirements
        .atLeast(::metrics, 1)

    // Options
    val apiKey by option("--api-key").hidden()
    val username by option("--username")
    val serviceAccount by option("--service-account")
    val environment by option("--environment").oneOf("dev", "staging", "prod")
    val metrics by option("--metric").list()
}
```

## Domain-Scoped Constraints

Domains can enforce constraints that only apply when the domain is active:

### Basic Requirements

```kotlin
class DatabaseTool : Arguments() {
    val migrateDomain by domain("migrate")
        .required(::connectionString)        // Required only in migrate domain
        .required(::migrationDir)

    val backupDomain by domain("backup")
        .required(::connectionString)        // Required only in backup domain
        .required(::outputFile)

    val connectionString by option("--connection-string")
    val migrationDir by option("--migration-dir")
    val outputFile by option("--output-file")
}
```

### Conditional Requirements

```kotlin
class DeployTool : Arguments() {
    val prodDomain by domain("prod")
        .requireIfValue(::environment) { it == "production" }
        .required(::approvalToken)           // Only required in prod domain
        .conflicts(::skipTests)              // Can't skip tests in prod

    val devDomain by domain("dev")
        .atMostOne(::debugMode, ::profileMode)

    val environment by option("--environment").oneOf("dev", "staging", "production")
    val approvalToken by option("--approval-token").hidden()
    val skipTests by option("--skip-tests").bool()
    val debugMode by option("--debug").bool()
    val profileMode by option("--profile").bool()
}
```

### Group Constraints

```kotlin
class TestRunner : Arguments() {
    val unitDomain by domain("unit")
        .exactlyOne(::testDir, ::testFile)   // Exactly one source in unit domain
        .atMostOne(::parallel, ::sequential)

    val integrationDomain by domain("integration")
        .atLeastOne(::database, ::webService, ::messageQueue)  // Need at least one service
        .requireIfAnyPresent(::database, ::dbConfig)

    val testDir by option("--test-dir")
    val testFile by option("--test-file")
    val parallel by option("--parallel").bool()
    val sequential by option("--sequential").bool()
    val database by option("--database").bool()
    val webService by option("--web-service").bool()
    val messageQueue by option("--message-queue").bool()
    val dbConfig by option("--db-config")
}
```

## Domain Inheritance

### Fragment-Based Inheritance

```kotlin
class CloudTool : Arguments() {
    // Base authentication fragment
    val cloudAuth by domain(fragment = true)
        .exactlyOne(::apiKey, ::serviceAccount, ::userCredentials)
        .requireIfAnyPresent(::apiKey, ::region)

    // Base resource management fragment
    val resourceManagement by domain(fragment = true)
        .required(::resourceGroup)
        .atMostOne(::createIfMissing, ::failIfMissing)

    // Concrete domains inheriting from fragments
    val deployDomain by domain("deploy")
        .inherits(::cloudAuth, ::resourceManagement)
        .required(::deploymentConfig)
        .conflicts(::dryRun, ::autoApprove)

    val destroyDomain by domain("destroy")
        .inherits(::cloudAuth, ::resourceManagement)
        .required(::confirmDestroy)
        .requireIfValue(::environment) { it == "production" }

    val monitorDomain by domain("monitor")
        .inherits(::cloudAuth)               // Only auth, not resource management
        .atLeast(::metric, 1)

    // Options
    val apiKey by option("--api-key").hidden()
    val serviceAccount by option("--service-account")
    val userCredentials by option("--user-credentials").bool()
    val region by option("--region").default("us-east-1")
    val resourceGroup by option("--resource-group")
    val createIfMissing by option("--create-if-missing").bool()
    val failIfMissing by option("--fail-if-missing").bool()
    val deploymentConfig by option("--deployment-config")
    val dryRun by option("--dry-run").bool()
    val autoApprove by option("--auto-approve").bool()
    val confirmDestroy by option("--confirm-destroy").bool()
    val environment by option("--environment").oneOf("dev", "staging", "production")
    val metric by option("--metric").list()
}
```

### Multiple Inheritance

```kotlin
class KubernetesTool : Arguments() {
    // Multiple constraint fragments
    val clusterAuth by domain(fragment = true)
        .exactlyOne(::kubeconfig, ::token, ::certificate)

    val namespaceScope by domain(fragment = true)
        .atMostOne(::namespace, ::allNamespaces)
        .requireIfAnyPresent(::allNamespaces, ::clusterAdmin)

    val resourceSelector by domain(fragment = true)
        .atLeastOne(::labelSelector, ::fieldSelector, ::resourceName)

    // Domains inheriting multiple fragments
    val getDomain by domain("get")
        .inherits(::clusterAuth, ::namespaceScope, ::resourceSelector)
        .atMostOne(::output, ::watch)

    val deleteDomain by domain("delete")
        .inherits(::clusterAuth, ::namespaceScope, ::resourceSelector)
        .required(::confirmDelete)
        .conflicts(::dryRun, ::force)

    val applyDomain by domain("apply")
        .inherits(::clusterAuth, ::namespaceScope)
        .exactlyOne(::filename, ::directory, ::stdin)

    // Options
    val kubeconfig by option("--kubeconfig")
    val token by option("--token").hidden()
    val certificate by option("--certificate")
    val namespace by option("--namespace", "-n")
    val allNamespaces by option("--all-namespaces").bool()
    val clusterAdmin by option("--cluster-admin").bool()
    val labelSelector by option("--selector", "-l")
    val fieldSelector by option("--field-selector")
    val resourceName by option("--name")
    val output by option("--output", "-o").oneOf("json", "yaml", "wide")
    val watch by option("--watch").bool()
    val confirmDelete by option("--confirm-delete").bool()
    val dryRun by option("--dry-run").bool()
    val force by option("--force").bool()
    val filename by option("--filename", "-f")
    val directory by option("--directory")
    val stdin by option("--stdin").bool()
}
```

## Advanced Domain Patterns

### Hierarchical Commands

```kotlin
class DockerTool : Arguments() {
    // Main command domains
    val containerDomain by domain("container")
        .label("Container operations")
        .help("Manage Docker containers")

    val imageDomain by domain("image")
        .label("Image operations")
        .help("Manage Docker images")

    val networkDomain by domain("network")
        .label("Network operations")
        .help("Manage Docker networks")

    // Sub-operation domains (require parent domain)
    val containerRunDomain by domain("run")
        .requireIfAnyPresent(::containerDomain)
        .required(::image)
        .atMostOne(::detached, ::interactive)

    val containerStopDomain by domain("stop")
        .requireIfAnyPresent(::containerDomain)
        .atLeastOne(::containerName, ::containerId)

    val imageBuilddomain by domain("build")
        .requireIfAnyPresent(::imageDomain)
        .required(::dockerfile)
        .required(::tag)

    // Options
    val image by option("--image")
    val detached by option("--detached", "-d").bool()
    val interactive by option("--interactive", "-i").bool()
    val containerName by option("--name")
    val containerId by option("--id")
    val dockerfile by option("--dockerfile", "-f").default("Dockerfile")
    val tag by option("--tag", "-t")
}
```

### Conditional Domain Activation

```kotlin
class CIPipeline : Arguments() {
    val environment by option("--environment").oneOf("dev", "staging", "prod")

    // Domains that activate based on environment
    val devDomain by domain("dev-deploy")
        .requireIfValue(::environment) { it == "dev" }
        .conflicts(::requireApproval)

    val stagingDomain by domain("staging-deploy")
        .requireIfValue(::environment) { it == "staging" }
        .required(::testResults)
        .atMostOne(::autoPromote, ::manualPromote)

    val prodDomain by domain("prod-deploy")
        .requireIfValue(::environment) { it == "prod" }
        .required(::approvalToken)
        .required(::rollbackPlan)
        .conflicts(::skipTests)

    val requireApproval by option("--require-approval").bool()
    val testResults by option("--test-results")
    val autoPromote by option("--auto-promote").bool()
    val manualPromote by option("--manual-promote").bool()
    val approvalToken by option("--approval-token").hidden()
    val rollbackPlan by option("--rollback-plan")
    val skipTests by option("--skip-tests").bool()
}
```

## Error Messages and Help

### Domain-Specific Error Messages

When domain constraints are violated, Argos provides clear, context-aware error messages:

```bash
# Missing required option in domain
my-tool deploy
# Error: Option --environment is required when domain 'deploy' is active

# Conflicting options in domain
my-tool test --parallel --sequential
# Error: Options --parallel and --sequential conflict in domain 'test'

# Inheritance constraint violation
my-tool deploy --api-key key123
# Error: Option --region is required when --api-key is present (inherited from cloudAuth)
```

### Domain Help Integration

```bash
# Global help shows available domains
my-tool --help
```

Output:
```
my-tool - Cloud deployment and monitoring tool

Usage: my-tool [COMMAND] [OPTIONS]

Commands:
  deploy     Deploy application to cloud environment
  destroy    Destroy cloud resources
  monitor    Monitor application metrics

Options:
  --verbose          Enable verbose logging
  -h, --help         Show this help message and exit
  -V, --version      Show version information
```

```bash
# Domain-specific help shows relevant options and constraints
my-tool deploy --help
```

Output:
```
my-tool deploy - Deploy application to cloud environment

Usage: my-tool deploy [OPTIONS]

Required Options:
  --environment TEXT         Target environment [choices: dev, staging, prod]
  --deployment-config FILE   Deployment configuration file

Authentication (exactly one required):
  --api-key TEXT            API key for authentication
  --service-account FILE    Service account credentials
  --user-credentials        Use interactive user authentication

Resource Management:
  --resource-group TEXT     Target resource group [required]
  --create-if-missing       Create resource group if it doesn't exist
  --fail-if-missing         Fail if resource group doesn't exist

Options:
  --region TEXT             Cloud region [default: us-east-1]
  --dry-run                 Show what would be deployed without making changes
  --verbose                 Enable verbose logging
  -h, --help                Show this help message and exit

Note: --dry-run and --auto-approve cannot be used together
```

## Best Practices

### 1. Use Descriptive Domain Names

```kotlin
// Good: Clear, action-oriented names
val deployDomain by domain("deploy")
val monitorDomain by domain("monitor")
val rollbackDomain by domain("rollback")

// Avoid: Vague or technical names
val modeDomain by domain("mode")
val optionDomain by domain("opts")
```

### 2. Leverage Fragment Inheritance

```kotlin
// Good: Common constraints in fragments
val commonSecurity by domain(fragment = true)
    .required(::credentials)
    .hidden(::apiKey)

val deployDomain by domain("deploy")
    .inherits(::commonSecurity)

val monitorDomain by domain("monitor")
    .inherits(::commonSecurity)

// Avoid: Duplicating constraints across domains
```

### 3. Provide Clear Labels and Help

```kotlin
// Good: Descriptive labels and help text
val deployDomain by domain("deploy")
    .label("Deploy Application")
    .help("Deploy the application to the specified environment with proper validation")

// Avoid: Missing or unhelpful documentation
val deployDomain by domain("deploy")              // No context for users
```

### 4. Design Logical Constraint Groups

```kotlin
// Good: Logical groupings that make sense to users
val authDomain by domain(fragment = true)
    .exactlyOne(::password, ::keyFile, ::token)   // One auth method

val prodDomain by domain("production")
    .inherits(::authDomain)
    .required(::approvalToken)                    // Extra security for prod
    .conflicts(::skipValidation)                  // No shortcuts in prod

// Avoid: Arbitrary or confusing constraint combinations
```

The domain system enables sophisticated CLI applications that can scale from simple tools to complex multi-command interfaces while maintaining clear organization and user-friendly error messages.