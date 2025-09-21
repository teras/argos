# Domain Inheritance

Domain inheritance allows you to create reusable constraint templates that can be shared across multiple domains. This powerful feature enables sophisticated CLI architectures with common patterns while avoiding code duplication.

## Fragment Domains

Fragment domains are constraint templates that cannot be selected from the command line but can be inherited by concrete domains.

### Basic Fragment Creation

```kotlin
class CloudTool : Arguments() {
    // Fragment domain - not selectable, used as template
    val authenticationFragment by domain(fragment = true)
        .exactlyOne(::apiKey, ::serviceAccount, ::userCredentials)
        .requireIfAnyPresent(::apiKey, ::region)
        .required(::projectId)

    // Concrete domains inherit the fragment
    val deployDomain by domain("deploy")
        .inherits(::authenticationFragment)
        .required(::deploymentConfig)

    val monitorDomain by domain("monitor")
        .inherits(::authenticationFragment)
        .atLeast(::metric, 1)

    // Options
    val apiKey by option("--api-key").hidden()
    val serviceAccount by option("--service-account")
    val userCredentials by option("--user-credentials").bool()
    val region by option("--region")
    val projectId by option("--project-id")
    val deploymentConfig by option("--deployment-config")
    val metric by option("--metric").list()
}
```

### Usage Examples

```bash
# Fragment constraints are inherited by concrete domains
cloud-tool deploy --user-credentials --project-id my-project --deployment-config config.yml

# Error: Fragment constraint inherited
cloud-tool deploy --api-key key123 --project-id my-project
# Error: Option --region is required when --api-key is present (inherited from authenticationFragment)

# Cannot select fragment directly
cloud-tool authenticationFragment
# Error: Unknown command 'authenticationFragment'
```

## Multiple Inheritance

Domains can inherit from multiple fragments, combining different constraint patterns:

### Combining Fragment Patterns

```kotlin
class KubernetesTool : Arguments() {
    // Authentication fragment
    val authFragment by domain(fragment = true)
        .exactlyOne(::kubeconfig, ::token, ::certificate)
        .requireIfValue(::certificate, ::certificateKey) { it != null }

    // Resource scoping fragment
    val scopeFragment by domain(fragment = true)
        .atMostOne(::namespace, ::allNamespaces)
        .requireIfAnyPresent(::allNamespaces, ::clusterAdmin)

    // Resource selection fragment
    val selectorFragment by domain(fragment = true)
        .atLeastOne(::labelSelector, ::fieldSelector, ::resourceName)
        .requireIfValue(::labelSelector, ::selectorFile) { it?.contains("complex") == true }

    // Concrete domains inheriting multiple fragments
    val getDomain by domain("get")
        .inherits(::authFragment, ::scopeFragment, ::selectorFragment)
        .atMostOne(::output, ::watch)

    val deleteDomain by domain("delete")
        .inherits(::authFragment, ::scopeFragment, ::selectorFragment)
        .required(::confirmDelete)
        .conflicts(::dryRun, ::force)

    val applyDomain by domain("apply")
        .inherits(::authFragment, ::scopeFragment)      // No selector fragment
        .exactlyOne(::filename, ::directory, ::stdin)

    // Authentication options
    val kubeconfig by option("--kubeconfig")
    val token by option("--token").hidden()
    val certificate by option("--certificate")
    val certificateKey by option("--certificate-key").hidden()

    // Scoping options
    val namespace by option("--namespace", "-n")
    val allNamespaces by option("--all-namespaces").bool()
    val clusterAdmin by option("--cluster-admin").bool()

    // Selection options
    val labelSelector by option("--selector", "-l")
    val fieldSelector by option("--field-selector")
    val resourceName by option("--name")
    val selectorFile by option("--selector-file")

    // Command-specific options
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

### Inheritance Order and Conflicts

When inheriting from multiple fragments, constraints are applied in order:

```kotlin
class DatabaseTool : Arguments() {
    // First fragment
    val connectionFragment by domain(fragment = true)
        .required(::host)
        .atMostOne(::sslMode, ::insecureMode)

    // Second fragment that might conflict
    val securityFragment by domain(fragment = true)
        .required(::sslMode)                    // Conflicts with connectionFragment's atMostOne
        .conflicts(::insecureMode)

    val migrateDomain by domain("migrate")
        .inherits(::connectionFragment, ::securityFragment)
        // Resolution: securityFragment's required(::sslMode) takes precedence
        // Result: sslMode is required, insecureMode conflicts
}
```

## Hierarchical Inheritance

### Multi-Level Inheritance

```kotlin
class ComplexTool : Arguments() {
    // Base fragment - fundamental requirements
    val baseFragment by domain(fragment = true)
        .required(::configuration)
        .atMostOne(::verbose, ::quiet)

    // Network fragment - inherits base and adds network requirements
    val networkFragment by domain(fragment = true)
        .inherits(::baseFragment)
        .required(::endpoint)
        .exactlyOne(::httpProtocol, ::httpsProtocol, ::grpcProtocol)

    // Security fragment - inherits network and adds security requirements
    val securityFragment by domain(fragment = true)
        .inherits(::networkFragment)
        .requireIfValue(::httpsProtocol, ::tlsCertificate) { it == true }
        .requireIfValue(::grpcProtocol, ::grpcCredentials) { it == true }
        .conflicts(::insecureMode)

    // Concrete domain inheriting the full hierarchy
    val deployDomain by domain("deploy")
        .inherits(::securityFragment)           // Gets all constraints from the hierarchy
        .required(::deploymentTarget)

    // Options
    val configuration by option("--configuration")
    val verbose by option("--verbose").bool()
    val quiet by option("--quiet").bool()
    val endpoint by option("--endpoint")
    val httpProtocol by option("--http").bool()
    val httpsProtocol by option("--https").bool()
    val grpcProtocol by option("--grpc").bool()
    val tlsCertificate by option("--tls-cert")
    val grpcCredentials by option("--grpc-creds")
    val insecureMode by option("--insecure").bool()
    val deploymentTarget by option("--deployment-target")
}
```

### Effective Constraint Resolution

```bash
# The deploy domain effectively has all these constraints:
# From baseFragment:
#   - required(::configuration)
#   - atMostOne(::verbose, ::quiet)
# From networkFragment:
#   - required(::endpoint)
#   - exactlyOne(::httpProtocol, ::httpsProtocol, ::grpcProtocol)
# From securityFragment:
#   - requireIfValue(::httpsProtocol, ::tlsCertificate) { it == true }
#   - requireIfValue(::grpcProtocol, ::grpcCredentials) { it == true }
#   - conflicts(::insecureMode)
# From deployDomain:
#   - required(::deploymentTarget)

complex-tool deploy --configuration config.yml --endpoint api.example.com --https --tls-cert cert.pem --deployment-target prod
# Success: All inherited constraints satisfied
```

## Selective Inheritance

### Conditional Inheritance

```kotlin
class EnvironmentTool : Arguments() {
    val environment by option("--environment").oneOf("dev", "staging", "prod")

    // Development-specific constraints
    val devFragment by domain(fragment = true)
        .conflicts(::requireApproval)
        .atMostOne(::fastMode, ::debugMode)

    // Production-specific constraints
    val prodFragment by domain(fragment = true)
        .required(::approvalToken)
        .required(::rollbackPlan)
        .conflicts(::skipValidation)

    // Common operational constraints
    val opsFragment by domain(fragment = true)
        .required(::operatorId)
        .atLeastOne(::backupEnabled, ::monitoringEnabled)

    val deployDomain by domain("deploy")
        .inherits(::opsFragment)                // Always inherit ops requirements
        // Conditional inheritance based on environment would be done in business logic

    val requireApproval by option("--require-approval").bool()
    val fastMode by option("--fast").bool()
    val debugMode by option("--debug").bool()
    val approvalToken by option("--approval-token").hidden()
    val rollbackPlan by option("--rollback-plan")
    val skipValidation by option("--skip-validation").bool()
    val operatorId by option("--operator-id")
    val backupEnabled by option("--backup").bool()
    val monitoringEnabled by option("--monitoring").bool()
}
```

### Override Inheritance

Concrete domains can override inherited constraints:

```kotlin
class FlexibleTool : Arguments() {
    val baseFragment by domain(fragment = true)
        .atMostOne(::option1, ::option2)        // Base constraint

    val standardDomain by domain("standard")
        .inherits(::baseFragment)               // Inherits atMostOne constraint

    val flexibleDomain by domain("flexible")
        .inherits(::baseFragment)
        .atLeastOne(::option1, ::option2)       // Overrides to allow both (more permissive)

    val strictDomain by domain("strict")
        .inherits(::baseFragment)
        .exactlyOne(::option1, ::option2)       // Overrides to be more restrictive

    val option1 by option("--option1").bool()
    val option2 by option("--option2").bool()
}
```

## Complex Inheritance Patterns

### Mixin-Style Fragments

```kotlin
class MicroservicePlatform : Arguments() {
    // Authentication mixin
    val authMixin by domain(fragment = true)
        .exactlyOne(::oauth, ::apiKey, ::mtls)

    // Observability mixin
    val observabilityMixin by domain(fragment = true)
        .atLeastOne(::metrics, ::tracing, ::logging)
        .requireIfAnyPresent(::tracing, ::tracingEndpoint)

    // Resilience mixin
    val resilienceMixin by domain(fragment = true)
        .atMostOne(::retryPolicy, ::circuitBreaker)
        .requireIfAnyPresent(::circuitBreaker, ::circuitBreakerConfig)

    // High-level service operations combining mixins
    val httpServiceDomain by domain("http-service")
        .inherits(::authMixin, ::observabilityMixin, ::resilienceMixin)
        .required(::port)
        .conflicts(::mtls, ::insecureHttp)      // HTTP-specific constraints

    val grpcServiceDomain by domain("grpc-service")
        .inherits(::authMixin, ::observabilityMixin, ::resilienceMixin)
        .required(::grpcPort)
        .requireIfValue(::mtls, ::tlsCertificate) { it == true }

    val batchJobDomain by domain("batch-job")
        .inherits(::authMixin, ::observabilityMixin)  // No resilience for batch jobs
        .required(::jobConfig)
        .conflicts(::realTime)

    // Authentication options
    val oauth by option("--oauth").bool()
    val apiKey by option("--api-key").hidden()
    val mtls by option("--mtls").bool()

    // Observability options
    val metrics by option("--metrics").bool()
    val tracing by option("--tracing").bool()
    val logging by option("--logging").bool()
    val tracingEndpoint by option("--tracing-endpoint")

    // Resilience options
    val retryPolicy by option("--retry-policy")
    val circuitBreaker by option("--circuit-breaker").bool()
    val circuitBreakerConfig by option("--circuit-breaker-config")

    // Service-specific options
    val port by option("--port").int()
    val insecureHttp by option("--insecure-http").bool()
    val grpcPort by option("--grpc-port").int()
    val tlsCertificate by option("--tls-certificate")
    val jobConfig by option("--job-config")
    val realTime by option("--real-time").bool()
}
```

### Cross-Cutting Concerns

```kotlin
class EnterpriseTool : Arguments() {
    // Security cross-cutting concern
    val securityConcern by domain(fragment = true)
        .required(::auditLog)
        .exactlyOne(::authMethod)
        .requireIfValue(::environment, ::complianceMode) { it == "prod" }

    // Performance cross-cutting concern
    val performanceConcern by domain(fragment = true)
        .atMostOne(::caching, ::noCaching)
        .requireIfAnyPresent(::caching, ::cacheConfig)
        .requireIfValue(::workload, ::performanceProfile) { it == "high" }

    // Reliability cross-cutting concern
    val reliabilityConcern by domain(fragment = true)
        .required(::healthCheck)
        .atLeastOne(::backup, ::replication)
        .requireIfAnyPresent(::replication, ::replicationFactor)

    // Business domains combining concerns as needed
    val customerFacingDomain by domain("customer-facing")
        .inherits(::securityConcern, ::performanceConcern, ::reliabilityConcern)
        .required(::customerDatabase)

    val internalToolDomain by domain("internal-tool")
        .inherits(::securityConcern, ::performanceConcern)  // No reliability concern
        .required(::internalDatabase)

    val batchProcessingDomain by domain("batch-processing")
        .inherits(::reliabilityConcern)                     // Only reliability concern
        .required(::batchConfig)
        .conflicts(::realTimeMode)

    // Security options
    val auditLog by option("--audit-log")
    val authMethod by option("--auth-method").oneOf("ldap", "oauth", "saml")
    val environment by option("--environment").oneOf("dev", "staging", "prod")
    val complianceMode by option("--compliance-mode").bool()

    // Performance options
    val caching by option("--caching").bool()
    val noCaching by option("--no-caching").bool()
    val cacheConfig by option("--cache-config")
    val workload by option("--workload").oneOf("low", "medium", "high")
    val performanceProfile by option("--performance-profile")

    // Reliability options
    val healthCheck by option("--health-check")
    val backup by option("--backup").bool()
    val replication by option("--replication").bool()
    val replicationFactor by option("--replication-factor").int()

    // Domain-specific options
    val customerDatabase by option("--customer-database")
    val internalDatabase by option("--internal-database")
    val batchConfig by option("--batch-config")
    val realTimeMode by option("--real-time").bool()
}
```

## Error Messages with Inheritance

### Inherited Constraint Errors

```bash
# Error from inherited fragment
my-tool deploy --api-key key123
# Error: Option --region is required when --api-key is present (inherited from authenticationFragment)

# Multiple inheritance constraint error
kubectl-tool get --all-namespaces
# Error: Option --cluster-admin is required when --all-namespaces is present (inherited from scopeFragment)
```

### Inheritance Chain Errors

```bash
# Error showing inheritance chain
complex-tool deploy --https
# Error: Option --tls-cert is required when --https is true
# Constraint source: securityFragment -> networkFragment -> baseFragment
```

## Best Practices

### 1. Design Cohesive Fragments

```kotlin
// Good: Cohesive authentication fragment
val authFragment by domain(fragment = true)
    .exactlyOne(::password, ::keyFile, ::token)
    .requireIfAnyPresent(::keyFile, ::keyPassword)

// Avoid: Mixing unrelated concerns
val mixedFragment by domain(fragment = true)
    .required(::password)                   // Authentication
    .required(::outputFile)                 // I/O
    .conflicts(::verbose, ::quiet)          // Logging
```

### 2. Use Descriptive Fragment Names

```kotlin
// Good: Clear purpose
val authenticationFragment by domain(fragment = true)
val networkConfigFragment by domain(fragment = true)
val securityPolicyFragment by domain(fragment = true)

// Avoid: Vague names
val fragment1 by domain(fragment = true)
val commonStuff by domain(fragment = true)
```

### 3. Layer Fragments Logically

```kotlin
// Good: Logical layering from general to specific
val baseFragment by domain(fragment = true)           // Most general
val networkFragment by domain(fragment = true)        // Network-specific
    .inherits(::baseFragment)
val securityFragment by domain(fragment = true)       // Security-specific
    .inherits(::networkFragment)

// Avoid: Arbitrary inheritance chains
```

### 4. Document Fragment Purpose

```kotlin
// Good: Clear documentation
val authenticationFragment by domain(fragment = true)
    // Provides common authentication patterns for all cloud operations
    .exactlyOne(::apiKey, ::serviceAccount, ::userAuth)

val resourceManagementFragment by domain(fragment = true)
    // Handles resource scoping and access control
    .required(::resourceGroup)
    .atMostOne(::namespace, ::allNamespaces)
```

Domain inheritance enables sophisticated CLI architectures where common patterns can be defined once and reused across multiple commands, reducing duplication while maintaining clear constraint relationships and error messages.