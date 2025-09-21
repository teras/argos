# Environment Variables

Argos provides seamless integration with environment variables, allowing options to read values from the environment as fallbacks. This enables flexible configuration management for different deployment environments.

## Basic Environment Integration

Use `.fromEnv()` to read from environment variables:

```kotlin
class MyApp : Arguments() {
    // Read from environment variable as fallback
    val apiKey by option("--api-key")
        .fromEnv("API_KEY")
        .help("API key for authentication")

    // Environment with default fallback
    val host by option("--host")
        .fromEnv("SERVER_HOST")
        .default("localhost")
        .help("Server hostname")

    // Required option with environment fallback
    val database by option("--database")
        .fromEnv("DATABASE_URL")
        .required()
        .help("Database connection URL")
}
```

### Usage Examples

```bash
# Set environment variables
export API_KEY="abc123"
export SERVER_HOST="api.example.com"
export DATABASE_URL="postgresql://localhost/mydb"

# Use environment values
myapp
# Uses: API_KEY=abc123, SERVER_HOST=api.example.com, DATABASE_URL=postgresql://localhost/mydb

# Override specific environment values
myapp --host staging.example.com
# Uses: API_KEY=abc123, SERVER_HOST=staging.example.com, DATABASE_URL=postgresql://localhost/mydb

# Command line takes precedence over environment
myapp --api-key xyz789 --host localhost
# Uses: API_KEY=xyz789, SERVER_HOST=localhost, DATABASE_URL=postgresql://localhost/mydb
```

## Priority Order

Argos follows a clear priority order for option values:

1. **Command line arguments** (highest priority)
2. **Environment variables**
3. **Default values**
4. **Null/missing** (lowest priority)

```kotlin
class MyApp : Arguments() {
    val port by option("--port").int()
        .fromEnv("PORT")
        .default(8080)
}
```

```bash
# Priority demonstration
export PORT=3000

myapp                    # port = 3000 (from environment)
myapp --port 9000        # port = 9000 (command line overrides environment)

unset PORT
myapp                    # port = 8080 (default when no environment)
myapp --port 9000        # port = 9000 (command line overrides default)
```

## Collection Environment Variables

Environment variables work with collection types:

```kotlin
class MyApp : Arguments() {
    // List from environment (space-separated)
    val searchPaths by option("--search-path").list()
        .fromEnv("SEARCH_PATHS")
        .default(listOf("/usr/bin", "/usr/local/bin"))

    // Set from environment
    val features by option("--feature").set()
        .fromEnv("ENABLED_FEATURES")

    // Multiple environment sources
    val includePaths by option("--include", "-I").list()
        .fromEnv("INCLUDE_PATHS")
}
```

### Environment Collection Parsing

```bash
# Space-separated values (default behavior)
export SEARCH_PATHS="/opt/tools /home/user/bin /usr/local/bin"
export ENABLED_FEATURES="auth logging metrics"

myapp
# searchPaths = ["/opt/tools", "/home/user/bin", "/usr/local/bin"]
# features = {"auth", "logging", "metrics"}

# Override with command line
myapp --search-path /custom/path --feature auth --feature admin
# searchPaths = ["/custom/path"]
# features = {"auth", "admin"}

# Append to environment (if using multiple --option calls)
myapp --search-path /custom/path --search-path /another/path
# searchPaths = ["/custom/path", "/another/path"]
```

## Configuration Patterns

### Development vs Production

```kotlin
class WebApp : Arguments() {
    // Database configuration
    val dbHost by option("--db-host")
        .fromEnv("DB_HOST")
        .default("localhost")

    val dbPort by option("--db-port").int()
        .fromEnv("DB_PORT")
        .default(5432)

    val dbName by option("--db-name")
        .fromEnv("DB_NAME")
        .default("myapp_dev")

    val dbUser by option("--db-user")
        .fromEnv("DB_USER")
        .default("postgres")

    val dbPassword by option("--db-password")
        .fromEnv("DB_PASSWORD")
        .help("Database password")

    // Application configuration
    val serverPort by option("--port").int()
        .fromEnv("PORT")
        .default(8080)

    val logLevel by option("--log-level")
        .fromEnv("LOG_LEVEL")
        .enum<LogLevel>()
        .default(LogLevel.INFO)

    // Security configuration
    val jwtSecret by option("--jwt-secret")
        .fromEnv("JWT_SECRET")
        .required()
        .hidden()

    val corsOrigins by option("--cors-origin").list()
        .fromEnv("CORS_ORIGINS")
        .default(listOf("http://localhost:3000"))
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
```

### Environment Files

```bash
# .env.development
DB_HOST=localhost
DB_PORT=5432
DB_NAME=myapp_dev
DB_USER=dev_user
DB_PASSWORD=dev_password
PORT=3000
LOG_LEVEL=DEBUG
JWT_SECRET=dev-secret-key
CORS_ORIGINS="http://localhost:3000 http://localhost:3001"

# .env.production
DB_HOST=prod-db.example.com
DB_PORT=5432
DB_NAME=myapp_prod
DB_USER=prod_user
DB_PASSWORD=super-secure-password
PORT=80
LOG_LEVEL=WARN
JWT_SECRET=production-secret-key-very-long-and-secure
CORS_ORIGINS="https://myapp.com https://www.myapp.com"

# .env.testing
DB_HOST=test-db.example.com
DB_NAME=myapp_test
LOG_LEVEL=ERROR
JWT_SECRET=test-secret
```

## Typed Environment Variables

Environment variables work with all type conversions:

```kotlin
class MyApp : Arguments() {
    // Numeric types
    val maxConnections by option("--max-connections").int()
        .fromEnv("MAX_CONNECTIONS")
        .default(100)

    val timeout by option("--timeout").double()
        .fromEnv("TIMEOUT_SECONDS")
        .default(30.0)

    // Boolean types
    val enableMetrics by option("--enable-metrics").bool()
        .fromEnv("ENABLE_METRICS")
        .default(false)

    val debugMode by option("--debug").bool()
        .fromEnv("DEBUG")
        .default(false)

    // Enum types
    val environment by option("--env").enum<Environment>()
        .fromEnv("ENVIRONMENT")
        .default(Environment.DEVELOPMENT)

    // Custom transformations
    val configFile by option("--config")
        .fromEnv("CONFIG_FILE")
        .map("readable config file") { path ->
            path?.let { File(it).takeIf { f -> f.exists() && f.canRead() } }
        }
}

enum class Environment { DEVELOPMENT, STAGING, PRODUCTION }
```

### Environment Type Examples

```bash
# Numeric environment variables
export MAX_CONNECTIONS=500
export TIMEOUT_SECONDS=45.5

# Boolean environment variables (multiple formats supported)
export ENABLE_METRICS=true
export DEBUG=1                    # Also: yes, on

# Enum environment variables
export ENVIRONMENT=PRODUCTION     # Case-insensitive

# File path environment variables
export CONFIG_FILE=/etc/myapp/config.json

myapp
# maxConnections = 500, timeout = 45.5, enableMetrics = true,
# debugMode = true, environment = PRODUCTION
```

## Advanced Environment Patterns

### Hierarchical Configuration

```kotlin
class MicroserviceApp : Arguments() {
    // Service identity
    val serviceName by option("--service-name")
        .fromEnv("SERVICE_NAME")
        .required()

    val serviceVersion by option("--service-version")
        .fromEnv("SERVICE_VERSION")
        .default("unknown")

    // Infrastructure configuration
    val kubernetesNamespace by option("--k8s-namespace")
        .fromEnv("KUBERNETES_NAMESPACE")
        .default("default")

    val clusterName by option("--cluster-name")
        .fromEnv("CLUSTER_NAME")
        .default("local")

    // Service discovery
    val consulHost by option("--consul-host")
        .fromEnv("CONSUL_HOST")
        .default("localhost")

    val consulToken by option("--consul-token")
        .fromEnv("CONSUL_TOKEN")
        .hidden()

    // Observability
    val metricsEndpoint by option("--metrics-endpoint")
        .fromEnv("METRICS_ENDPOINT")
        .default("/metrics")

    val tracingEndpoint by option("--tracing-endpoint")
        .fromEnv("JAEGER_ENDPOINT")

    val logFormat by option("--log-format")
        .fromEnv("LOG_FORMAT")
        .oneOf("json", "text")
        .default("json")
}
```

### Configuration with Validation

```kotlin
class DatabaseApp : Arguments() {
    val connectionString by option("--connection-string")
        .fromEnv("DATABASE_URL")
        .validate("Must be a valid database URL") {
            it?.startsWith("postgresql://") == true ||
            it?.startsWith("mysql://") == true ||
            it?.startsWith("sqlite://") == true
        }

    val maxPoolSize by option("--max-pool-size").int()
        .fromEnv("DB_MAX_POOL_SIZE")
        .default(20)
        .validate("Pool size must be reasonable") { it in 1..100 }

    val connectionTimeout by option("--connection-timeout").int()
        .fromEnv("DB_CONNECTION_TIMEOUT")
        .default(30)
        .validate("Timeout must be positive") { it > 0 }

    val sslMode by option("--ssl-mode")
        .fromEnv("DB_SSL_MODE")
        .oneOf("disable", "require", "verify-ca", "verify-full")
        .default("require")

    val tlsCertPath by option("--tls-cert")
        .fromEnv("DB_TLS_CERT_PATH")
        .requireIfValue(::sslMode) { it in listOf("verify-ca", "verify-full") }
        .validate("Certificate file must exist") {
            it?.let { File(it).exists() } != false
        }
}
```

## Secret Management

### Hidden Environment Options

```kotlin
class SecureApp : Arguments() {
    // Visible configuration
    val serverHost by option("--host")
        .fromEnv("SERVER_HOST")
        .default("localhost")

    val serverPort by option("--port").int()
        .fromEnv("SERVER_PORT")
        .default(8080)

    // Hidden secrets (don't show in help)
    val apiKey by option("--api-key")
        .fromEnv("API_KEY")
        .required()
        .hidden()

    val dbPassword by option("--db-password")
        .fromEnv("DB_PASSWORD")
        .input(hidden = true)  // Prompts if not in environment
        .hidden()

    val jwtSigningKey by option("--jwt-key")
        .fromEnv("JWT_SIGNING_KEY")
        .required()
        .hidden()
        .validate("JWT key must be at least 32 characters") {
            it.length >= 32
        }
}
```

### Environment Security Practices

```bash
# Good: Use secure environment variable management
export API_KEY="$(cat /etc/secrets/api-key)"
export DB_PASSWORD="$(kubectl get secret db-creds -o jsonpath='{.data.password}' | base64 -d)"

# Good: Different secrets per environment
if [ "$ENVIRONMENT" = "production" ]; then
    export JWT_SIGNING_KEY="$(cat /prod/secrets/jwt-key)"
else
    export JWT_SIGNING_KEY="dev-jwt-key-not-secure"
fi

# Avoid: Hardcoded secrets in scripts
export API_KEY="hardcoded-secret"  # Don't do this
```

## Value Source Tracking

Track where option values came from:

```kotlin
fun main(args: Array<String>) {
    val app = MyApp().parse(args) ?: return

    // Check value sources
    val apiKeySource = app.valueSourceOf(app::apiKey)
    when (apiKeySource) {
        ValueSource.USER -> println("API key from command line")
        ValueSource.ENVIRONMENT -> println("API key from environment")
        ValueSource.DEFAULT -> println("API key using default")
        ValueSource.MISSING -> println("API key not provided")
    }

    val hostSource = app.valueSourceOf(app::host)
    if (hostSource == ValueSource.ENVIRONMENT) {
        println("Using host from environment: ${app.host}")
    }
}
```

## Complete Examples

### Cloud Application

```kotlin
enum class CloudProvider { AWS, GCP, AZURE }
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

class CloudApp : Arguments(
    appName = "cloud-deploy",
    appDescription = "Cloud deployment tool with environment configuration"
) {
    // Cloud provider configuration
    val provider by option("--provider").enum<CloudProvider>()
        .fromEnv("CLOUD_PROVIDER")
        .default(CloudProvider.AWS)

    val region by option("--region")
        .fromEnv("CLOUD_REGION")
        .default("us-east-1")

    // AWS-specific configuration
    val awsAccessKey by option("--aws-access-key")
        .fromEnv("AWS_ACCESS_KEY_ID")
        .hidden()

    val awsSecretKey by option("--aws-secret-key")
        .fromEnv("AWS_SECRET_ACCESS_KEY")
        .hidden()

    val awsSessionToken by option("--aws-session-token")
        .fromEnv("AWS_SESSION_TOKEN")
        .hidden()

    // Application configuration
    val appName by option("--app-name")
        .fromEnv("APP_NAME")
        .required()

    val appVersion by option("--app-version")
        .fromEnv("APP_VERSION")
        .default("latest")

    val environment by option("--environment")
        .fromEnv("DEPLOY_ENVIRONMENT")
        .oneOf("dev", "staging", "prod")
        .default("dev")

    // Deployment configuration
    val instances by option("--instances").int()
        .fromEnv("INSTANCE_COUNT")
        .default(1)
        .validate("Instance count must be reasonable") { it in 1..100 }

    val enableAutoScaling by option("--auto-scaling").bool()
        .fromEnv("ENABLE_AUTO_SCALING")
        .default(false)

    val healthCheckPath by option("--health-check")
        .fromEnv("HEALTH_CHECK_PATH")
        .default("/health")

    // Logging and monitoring
    val logLevel by option("--log-level").enum<LogLevel>()
        .fromEnv("LOG_LEVEL")
        .default(LogLevel.INFO)

    val enableMetrics by option("--enable-metrics").bool()
        .fromEnv("ENABLE_METRICS")
        .default(true)

    val metricsPort by option("--metrics-port").int()
        .fromEnv("METRICS_PORT")
        .default(9090)

    val help by help()
    val version by version("1.0.0")
}
```

### Container Application

```kotlin
class ContainerApp : Arguments() {
    // Container runtime
    val runtime by option("--runtime")
        .fromEnv("CONTAINER_RUNTIME")
        .oneOf("docker", "containerd", "cri-o")
        .default("docker")

    // Registry configuration
    val registryUrl by option("--registry")
        .fromEnv("CONTAINER_REGISTRY")
        .default("docker.io")

    val registryUser by option("--registry-user")
        .fromEnv("REGISTRY_USERNAME")

    val registryPassword by option("--registry-password")
        .fromEnv("REGISTRY_PASSWORD")
        .input(hidden = true)
        .hidden()

    // Image configuration
    val imageName by option("--image")
        .fromEnv("IMAGE_NAME")
        .required()

    val imageTag by option("--tag")
        .fromEnv("IMAGE_TAG")
        .default("latest")

    val buildArgs by option("--build-arg").list()
        .fromEnv("BUILD_ARGS")

    // Runtime configuration
    val cpuLimit by option("--cpu-limit")
        .fromEnv("CPU_LIMIT")
        .default("1.0")

    val memoryLimit by option("--memory-limit")
        .fromEnv("MEMORY_LIMIT")
        .default("512M")

    val environmentVars by option("--env", "-e").list()
        .fromEnv("CONTAINER_ENV_VARS")

    val volumes by option("--volume", "-v").list()
        .fromEnv("CONTAINER_VOLUMES")

    val ports by option("--port", "-p").list()
        .fromEnv("CONTAINER_PORTS")
        .validate("Port mapping must be in format host:container") {
            it.contains(":")
        }
}
```

## Best Practices

### 1. Use Descriptive Environment Variable Names

```kotlin
// Good: Clear, descriptive names
val apiKey by option("--api-key").fromEnv("API_KEY")
val dbHost by option("--db-host").fromEnv("DATABASE_HOST")
val maxRetries by option("--max-retries").fromEnv("MAX_RETRY_COUNT")

// Avoid: Ambiguous or short names
val key by option("--api-key").fromEnv("KEY")
val host by option("--db-host").fromEnv("H")
```

### 2. Provide Sensible Defaults

```kotlin
// Good: Reasonable defaults for local development
val host by option("--host").fromEnv("HOST").default("localhost")
val port by option("--port").int().fromEnv("PORT").default(8080)
val logLevel by option("--log-level").fromEnv("LOG_LEVEL").default("INFO")

// Consider: When should options be required vs have defaults?
val apiKey by option("--api-key").fromEnv("API_KEY").required()  // Secrets often required
```

### 3. Hide Sensitive Information

```kotlin
// Good: Hide secrets from help output
val password by option("--password").fromEnv("PASSWORD").input(hidden = true).hidden()
val apiKey by option("--api-key").fromEnv("API_KEY").hidden()
val token by option("--token").fromEnv("ACCESS_TOKEN").hidden()

// Consider: Use input(hidden = true) for interactive input when env var missing
val dbPassword by option("--db-password").fromEnv("DB_PASSWORD").input(hidden = true)
```

### 4. Document Environment Variables

```kotlin
// Good: Document environment variables in help text
val apiKey by option("--api-key").fromEnv("API_KEY")
    .help("API key for authentication (env: API_KEY)")

val host by option("--host").fromEnv("SERVER_HOST").default("localhost")
    .help("Server hostname (env: SERVER_HOST, default: localhost)")
```

Environment variables provide flexible configuration management that adapts to different deployment environments while maintaining security and ease of use.
