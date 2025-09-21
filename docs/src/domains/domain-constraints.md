# Domain-Scoped Constraints

Domain-scoped constraints allow you to define requirements and relationships that only apply when specific domains are active. This enables sophisticated CLI interfaces where different commands have different validation rules and option requirements.

## Constraint Application Scope

### Global vs Domain-Scoped Constraints

```kotlin
class MyTool : Arguments() {
    // Global constraint - applies always
    val verbose by option("--verbose").bool()
        .conflictsWith(::quiet)                 // Always conflicts

    // Domain-scoped constraint - applies only in build domain
    val buildDomain by domain("build")
        .required(::sourceDir)                  // Only required in build domain
        .conflicts(::cleanFirst, ::incrementalBuild) // Only conflicts in build domain

    val quiet by option("--quiet").bool()
    val sourceDir by option("--source-dir")
    val cleanFirst by option("--clean-first").bool()
    val incrementalBuild by option("--incremental").bool()
}
```

```bash
# Global constraint applies everywhere
my-tool build --verbose --quiet
# Error: Options --verbose and --quiet conflict

# Domain constraint only applies in build domain
my-tool build --clean-first --incremental
# Error: Options --clean-first and --incremental conflict in domain 'build'

my-tool test --clean-first --incremental
# OK: No conflict outside build domain
```

## Basic Domain Requirements

### Required Options

Make options required only when specific domains are active:

```kotlin
class DatabaseTool : Arguments() {
    val migrateDomain by domain("migrate")
        .required(::connectionString)
        .required(::migrationPath)

    val backupDomain by domain("backup")
        .required(::connectionString)
        .required(::outputFile)

    val testDomain by domain("test")
        .required(::testDatabase)

    val connectionString by option("--connection-string")
    val migrationPath by option("--migration-path")
    val outputFile by option("--output-file")
    val testDatabase by option("--test-database")
}
```

### Minimum Occurrence Requirements

```kotlin
class TestRunner : Arguments() {
    val unitDomain by domain("unit")
        .atLeast(::testFile, 1)                 // At least one test file

    val integrationDomain by domain("integration")
        .atLeast(::service, 2)                  // At least two services
        .atLeast(::testSuite, 1)

    val testFile by option("--test-file").list()
    val service by option("--service").list()
    val testSuite by option("--test-suite").list()
}
```

## Conditional Requirements

### Require If Any Present

Require an option when any of the specified trigger options are present in the domain:

```kotlin
class DeployTool : Arguments() {
    val prodDomain by domain("production")
        .requireIfAnyPresent(::backupLocation, ::database, ::fileStorage)
        .requireIfAnyPresent(::approvalToken, ::autoApprove, ::forceDeployment)

    val devDomain by domain("development")
        .requireIfAnyPresent(::debugPort, ::verbose, ::profileMode)

    val backupLocation by option("--backup-location")
    val database by option("--database").bool()
    val fileStorage by option("--file-storage").bool()
    val approvalToken by option("--approval-token").hidden()
    val autoApprove by option("--auto-approve").bool()
    val forceDeployment by option("--force").bool()
    val debugPort by option("--debug-port").int()
    val verbose by option("--verbose").bool()
    val profileMode by option("--profile").bool()
}
```

### Require If All Present

Require an option when all specified trigger options are present:

```kotlin
class SecurityTool : Arguments() {
    val encryptDomain by domain("encrypt")
        .requireIfAllPresent(::certificate, ::sslMode, ::keyFile)  // Need cert password when all SSL components present

    val signDomain by domain("sign")
        .requireIfAllPresent(::privateKey, ::certificate, ::timestampServer)  // Need key password for complete signing

    val certificate by option("--certificate")
    val sslMode by option("--ssl-mode").oneOf("require", "verify-ca", "verify-full")
    val keyFile by option("--key-file")
    val certPassword by option("--cert-password").input(hidden = true).hidden()
    val privateKey by option("--private-key")
    val timestampServer by option("--timestamp-server")
    val keyPassword by option("--key-password").input(hidden = true).hidden()
}
```

### Require If Value

Require options based on the value of another option within the domain:

```kotlin
class CIDomain : Arguments() {
    val deployDomain by domain("deploy")
        .requireIfValue(::environment, ::approvalToken) { it == "production" }
        .requireIfValue(::environment, ::rollbackPlan) { it in listOf("staging", "production") }
        .requireIfValue(::deployMode, ::blueGreenConfig) { it == "blue-green" }
        .requireIfValue(::deployMode, ::canaryConfig) { it == "canary" }

    val testDomain by domain("test")
        .requireIfValue(::testType, ::performanceConfig) { it == "performance" }
        .requireIfValue(::testType, ::loadTestConfig) { it == "load" }

    val environment by option("--environment").oneOf("dev", "staging", "production")
    val approvalToken by option("--approval-token").hidden()
    val rollbackPlan by option("--rollback-plan")
    val deployMode by option("--deploy-mode").oneOf("rolling", "blue-green", "canary")
    val blueGreenConfig by option("--blue-green-config")
    val canaryConfig by option("--canary-config")
    val testType by option("--test-type").oneOf("unit", "integration", "performance", "load")
    val performanceConfig by option("--performance-config")
    val loadTestConfig by option("--load-test-config")
}
```

### Require If Any Absent

Require an option when any of the specified options are absent within the domain:

```kotlin
class BackupTool : Arguments() {
    val restoreDomain by domain("restore")
        // Require default-source when either primary OR secondary is missing
        .requireIfAnyAbsent(::defaultSource, ::primaryBackup, ::secondaryBackup)
        // Require fallback-location when either location1 OR location2 is missing
        .requireIfAnyAbsent(::fallbackLocation, ::location1, ::location2)

    val verifyDomain by domain("verify")
        // Require full-check when either quick-scan OR hash-check is missing
        .requireIfAnyAbsent(::fullCheck, ::quickScan, ::hashCheck)

    val defaultSource by option("--default-source")
    val primaryBackup by option("--primary-backup")
    val secondaryBackup by option("--secondary-backup")
    val fallbackLocation by option("--fallback-location")
    val location1 by option("--location1")
    val location2 by option("--location2")
    val fullCheck by option("--full-check").bool()
    val quickScan by option("--quick-scan").bool()
    val hashCheck by option("--hash-check").bool()
}
```

### Require If All Absent

Require an option when all of the specified options are absent within the domain:

```kotlin
class NetworkTool : Arguments() {
    val connectDomain by domain("connect")
        // Require default-config when BOTH host AND port are missing
        .requireIfAllAbsent(::defaultConfig, ::host, ::port)
        // Require auto-discover when ALL connection methods are missing
        .requireIfAllAbsent(::autoDiscover, ::directConnect, ::proxyConnect, ::tunnelConnect)

    val monitorDomain by domain("monitor")
        // Require default-metrics when ALL custom metrics are missing
        .requireIfAllAbsent(::defaultMetrics, ::cpuMetric, ::memoryMetric, ::networkMetric)

    val defaultConfig by option("--default-config")
    val host by option("--host")
    val port by option("--port").int()
    val autoDiscover by option("--auto-discover").bool()
    val directConnect by option("--direct-connect").bool()
    val proxyConnect by option("--proxy-connect").bool()
    val tunnelConnect by option("--tunnel-connect").bool()
    val defaultMetrics by option("--default-metrics").bool()
    val cpuMetric by option("--cpu-metric").bool()
    val memoryMetric by option("--memory-metric").bool()
    val networkMetric by option("--network-metric").bool()
}
```

## Group Constraints

### Exactly One

Require exactly one option from a group within the domain:

```kotlin
class BuildTool : Arguments() {
    val buildDomain by domain("build")
        .exactlyOne(::sourceDir, ::projectFile, ::buildScript)    // Exactly one build source
        .exactlyOne(::debugBuild, ::releaseBuild, ::profileBuild) // Exactly one build type

    val packageDomain by domain("package")
        .exactlyOne(::jarPackage, ::nativePackage, ::dockerPackage) // Exactly one package type

    val sourceDir by option("--source-dir")
    val projectFile by option("--project-file")
    val buildScript by option("--build-script")
    val debugBuild by option("--debug").bool()
    val releaseBuild by option("--release").bool()
    val profileBuild by option("--profile").bool()
    val jarPackage by option("--jar").bool()
    val nativePackage by option("--native").bool()
    val dockerPackage by option("--docker").bool()
}
```

### At Most One

Allow at most one option from a group within the domain:

```kotlin
class LoggingTool : Arguments() {
    val runDomain by domain("run")
        .atMostOne(::verbose, ::quiet, ::silent)                 // Only one verbosity level
        .atMostOne(::logFile, ::syslog, ::journald)             // At most one log destination

    val analysisDomain by domain("analyze")
        .atMostOne(::realTime, ::batch)                         // Processing mode
        .atMostOne(::csvOutput, ::jsonOutput, ::xmlOutput)      // Output format

    val verbose by option("--verbose").bool()
    val quiet by option("--quiet").bool()
    val silent by option("--silent").bool()
    val logFile by option("--log-file")
    val syslog by option("--syslog").bool()
    val journald by option("--journald").bool()
    val realTime by option("--real-time").bool()
    val batch by option("--batch").bool()
    val csvOutput by option("--csv").bool()
    val jsonOutput by option("--json").bool()
    val xmlOutput by option("--xml").bool()
}
```

### At Least One

Require at least one option from a group within the domain:

```kotlin
class MonitoringTool : Arguments() {
    val monitorDomain by domain("monitor")
        .atLeastOne(::cpuMetrics, ::memoryMetrics, ::diskMetrics, ::networkMetrics) // Need some metrics
        .atLeastOne(::alertEmail, ::alertWebhook, ::alertSms)                       // Need some alerting

    val backupDomain by domain("backup")
        .atLeastOne(::localBackup, ::s3Backup, ::gcsBackup, ::azureBackup)         // Need backup destination

    val cpuMetrics by option("--cpu").bool()
    val memoryMetrics by option("--memory").bool()
    val diskMetrics by option("--disk").bool()
    val networkMetrics by option("--network").bool()
    val alertEmail by option("--alert-email")
    val alertWebhook by option("--alert-webhook")
    val alertSms by option("--alert-sms")
    val localBackup by option("--local-backup").bool()
    val s3Backup by option("--s3-backup").bool()
    val gcsBackup by option("--gcs-backup").bool()
    val azureBackup by option("--azure-backup").bool()
}
```

## Conflict Constraints

### Basic Conflicts

Prevent options from being used together within a domain:

```kotlin
class ServerTool : Arguments() {
    val startDomain by domain("start")
        .conflicts(::developmentMode, ::productionMode)          // Can't be both dev and prod
        .conflicts(::debugEnabled, ::optimizedBuild)            // Debug conflicts with optimization

    val testDomain by domain("test")
        .conflicts(::fastTests, ::comprehensiveTests)           // Different test modes
        .conflicts(::mockDatabase, ::realDatabase)             // Different database modes

    val developmentMode by option("--development").bool()
    val productionMode by option("--production").bool()
    val debugEnabled by option("--debug").bool()
    val optimizedBuild by option("--optimized").bool()
    val fastTests by option("--fast").bool()
    val comprehensiveTests by option("--comprehensive").bool()
    val mockDatabase by option("--mock-db").bool()
    val realDatabase by option("--real-db").bool()
}
```

### Multi-Option Conflicts

```kotlin
class DeploymentTool : Arguments() {
    val deployDomain by domain("deploy")
        .conflicts(::dryRun, ::autoApprove, ::forceDeployment)   // Can't combine these safety options
        .conflicts(::rollback, ::blueGreen, ::canaryDeployment)  // Different deployment strategies

    val maintenanceDomain by domain("maintenance")
        .conflicts(::startMaintenance, ::endMaintenance, ::extendMaintenance) // Only one maintenance action

    val dryRun by option("--dry-run").bool()
    val autoApprove by option("--auto-approve").bool()
    val forceDeployment by option("--force").bool()
    val rollback by option("--rollback").bool()
    val blueGreen by option("--blue-green").bool()
    val canaryDeployment by option("--canary").bool()
    val startMaintenance by option("--start-maintenance").bool()
    val endMaintenance by option("--end-maintenance").bool()
    val extendMaintenance by option("--extend-maintenance").bool()
}
```

## Complex Constraint Examples

### Multi-Environment Pipeline

```kotlin
class PipelineTool : Arguments() {
    val devDomain by domain("dev")
        .atMostOne(::fastBuild, ::debugBuild)
        .conflicts(::requireApproval)
        .requireIfValue(::testLevel) { it == "integration" }

    val stagingDomain by domain("staging")
        .required(::testResults)
        .exactlyOne(::promoteToProduction, ::holdForApproval)
        .requireIfAnyPresent(::promoteToProduction, ::approvalToken)
        .conflicts(::skipTests)

    val productionDomain by domain("production")
        .required(::approvalToken)
        .required(::rollbackPlan)
        .exactlyOne(::blueGreenDeploy, ::canaryDeploy, ::rollingDeploy)
        .requireIfValue(::blueGreenDeploy, ::loadBalancerConfig) { it == true }
        .requireIfValue(::canaryDeploy, ::canaryConfig) { it == true }
        .conflicts(::skipValidation, ::skipBackup)

    val fastBuild by option("--fast-build").bool()
    val debugBuild by option("--debug-build").bool()
    val requireApproval by option("--require-approval").bool()
    val testLevel by option("--test-level").oneOf("unit", "integration", "e2e")
    val testResults by option("--test-results")
    val promoteToProduction by option("--promote").bool()
    val holdForApproval by option("--hold").bool()
    val approvalToken by option("--approval-token").hidden()
    val skipTests by option("--skip-tests").bool()
    val rollbackPlan by option("--rollback-plan")
    val blueGreenDeploy by option("--blue-green").bool()
    val canaryDeploy by option("--canary").bool()
    val rollingDeploy by option("--rolling").bool()
    val loadBalancerConfig by option("--load-balancer-config")
    val canaryConfig by option("--canary-config")
    val skipValidation by option("--skip-validation").bool()
    val skipBackup by option("--skip-backup").bool()
}
```

### Microservice Management

```kotlin
class MicroserviceTool : Arguments() {
    // Authentication fragment - inherited by multiple domains
    val authFragment by domain(fragment = true)
        .exactlyOne(::apiKey, ::serviceAccount, ::userAuth)
        .requireIfValue(::apiKey, ::region) { it != null }

    // Resource management fragment
    val resourceFragment by domain(fragment = true)
        .required(::cluster)
        .atMostOne(::namespace, ::allNamespaces)
        .requireIfAnyPresent(::allNamespaces, ::clusterAdmin)

    val deployDomain by domain("deploy")
        .inherits(::authFragment, ::resourceFragment)
        .required(::serviceConfig)
        .exactlyOne(::deploymentMode)
        .requireIfValue(::deploymentMode, ::canarySettings) { it == "canary" }
        .requireIfValue(::deploymentMode, ::blueGreenSettings) { it == "blue-green" }
        .conflicts(::dryRun, ::autoConfirm)

    val scaleDomain by domain("scale")
        .inherits(::authFragment, ::resourceFragment)
        .required(::serviceName)
        .exactlyOne(::targetReplicas, ::autoscaleConfig)
        .requireIfAnyPresent(::autoscaleConfig, ::minReplicas, ::maxReplicas)

    val monitorDomain by domain("monitor")
        .inherits(::authFragment, ::resourceFragment)
        .atLeastOne(::metrics, ::logs, ::events)
        .requireIfAnyPresent(::metrics, ::metricsConfig)
        .atMostOne(::realTime, ::historical)

    // Authentication options
    val apiKey by option("--api-key").hidden()
    val serviceAccount by option("--service-account")
    val userAuth by option("--user-auth").bool()
    val region by option("--region")

    // Resource options
    val cluster by option("--cluster")
    val namespace by option("--namespace")
    val allNamespaces by option("--all-namespaces").bool()
    val clusterAdmin by option("--cluster-admin").bool()

    // Deployment options
    val serviceConfig by option("--service-config")
    val deploymentMode by option("--deployment-mode").oneOf("rolling", "canary", "blue-green")
    val canarySettings by option("--canary-settings")
    val blueGreenSettings by option("--blue-green-settings")
    val dryRun by option("--dry-run").bool()
    val autoConfirm by option("--auto-confirm").bool()

    // Scaling options
    val serviceName by option("--service-name")
    val targetReplicas by option("--target-replicas").int()
    val autoscaleConfig by option("--autoscale-config")
    val minReplicas by option("--min-replicas").int()
    val maxReplicas by option("--max-replicas").int()

    // Monitoring options
    val metrics by option("--metrics").bool()
    val logs by option("--logs").bool()
    val events by option("--events").bool()
    val metricsConfig by option("--metrics-config")
    val realTime by option("--real-time").bool()
    val historical by option("--historical").bool()
}
```

## Error Messages

Domain-scoped constraints provide clear, context-aware error messages:

### Requirement Errors

```bash
my-tool deploy
# Error: Option --service-config is required when domain 'deploy' is active

my-tool scale --autoscale-config config.yml
# Error: Options --min-replicas and --max-replicas are required when --autoscale-config is present in domain 'scale'
```

### Group Constraint Errors

```bash
my-tool deploy --canary --blue-green
# Error: Exactly one of --deployment-mode must be specified in domain 'deploy'

my-tool monitor
# Error: At least one of --metrics, --logs, --events must be specified in domain 'monitor'
```

### Conflict Errors

```bash
my-tool deploy --dry-run --auto-confirm
# Error: Options --dry-run and --auto-confirm conflict in domain 'deploy'

my-tool production --skip-validation --skip-backup
# Error: Options --skip-validation and --skip-backup conflict in domain 'production'
```

### Inheritance Errors

```bash
my-tool deploy --api-key key123
# Error: Option --region is required when --api-key is present (inherited from authFragment)

my-tool scale --all-namespaces
# Error: Option --cluster-admin is required when --all-namespaces is present (inherited from resourceFragment)
```

## Best Practices

### 1. Design Logical Constraint Groups

```kotlin
// Good: Logical groupings that make business sense
val sslDomain by domain("ssl")
    .exactlyOne(::certificate, ::letsEncrypt, ::selfSigned)  // One cert source
    .requireIfAnyPresent(::certificate, ::privateKey)       // Cert needs key

// Avoid: Arbitrary constraint combinations
val confusingDomain by domain("confusing")
    .exactlyOne(::verbose, ::inputFile)                     // Unrelated options
```

### 2. Use Descriptive Constraint Names

```kotlin
// Good: Clear constraint purpose
val deployDomain by domain("deploy")
    .required(::environment)                                // Clear requirement
    .conflicts(::dryRun, ::autoApprove)                    // Clear conflict

// Avoid: Unclear constraint relationships
val deployDomain by domain("deploy")
    .required(::option1)                                    // What's option1?
    .conflicts(::flag1, ::flag2)                           // Why do they conflict?
```

### 3. Leverage Fragment Inheritance

```kotlin
// Good: Reusable constraint patterns
val authFragment by domain(fragment = true)
    .exactlyOne(::apiKey, ::userAuth, ::serviceAccount)

val deployDomain by domain("deploy").inherits(::authFragment)
val monitorDomain by domain("monitor").inherits(::authFragment)

// Avoid: Duplicating constraints across domains
```

### 4. Provide Clear Error Context

Domain-scoped constraints automatically provide context about which domain is active and which constraints are violated, making errors easy to understand and fix.

Domain-scoped constraints enable sophisticated CLI applications that can enforce different rules for different commands while maintaining clear, understandable error messages and help text.
