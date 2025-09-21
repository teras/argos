# Custom Transformations

Argos provides powerful transformation capabilities through the `map()` function, allowing you to convert command-line string arguments into any custom type your application needs. This enables type-safe CLIs with rich domain objects.

## Basic Transformations

Use `map()` to transform string arguments into custom types:

```kotlin
import java.io.File
import java.net.URL

class MyApp : Arguments() {
    // Transform to File objects
    val configFile by option("--config")
        .map("readable config file") { path ->
            path?.let { File(it).takeIf { f -> f.exists() && f.canRead() } }
        }

    // Transform to URL objects
    val endpoint by option("--endpoint")
        .map("valid URL") { url ->
            url?.let {
                try { URL(it) } catch (e: Exception) { null }
            }
        }

    // Transform to custom data classes
    val coordinates by option("--coords")
        .map("coordinates in lat,lng format") { coordStr ->
            coordStr?.let {
                val parts = it.split(",")
                if (parts.size == 2) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        Coordinates(lat, lng)
                    } else null
                } else null
            }
        }
}

data class Coordinates(val latitude: Double, val longitude: Double)
```

### Usage Examples

```bash
myapp --config /path/to/config.json \
      --endpoint https://api.example.com \
      --coords 37.7749,-122.4194
```

## Error Handling in Transformations

Transformations should return `null` for invalid inputs to trigger clear error messages:

```kotlin
class MyApp : Arguments() {
    val port by option("--port")
        .map("port number (1-65535)") { portStr ->
            portStr?.toIntOrNull()?.takeIf { it in 1..65535 }
        }

    val percentage by option("--load")
        .map("percentage (0-100)") { percentStr ->
            percentStr?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
        }

    val duration by option("--timeout")
        .map("duration in seconds") { durationStr ->
            durationStr?.toLongOrNull()?.takeIf { it > 0 }
        }
}
```

### Error Messages

When transformations return `null`, users see clear error messages:

```bash
myapp --port 99999
# Error: Invalid value for --port: '99999' is not a port number (1-65535)

myapp --load 150
# Error: Invalid value for --load: '150' is not a percentage (0-100)

myapp --timeout -5
# Error: Invalid value for --timeout: '-5' is not a duration in seconds
```

## Advanced Transformations

### Date and Time Parsing

```kotlin
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class TimeApp : Arguments() {
    val startDate by option("--start-date")
        .map("date in YYYY-MM-DD format") { dateStr ->
            dateStr?.let {
                try {
                    LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e: DateTimeParseException) { null }
            }
        }

    val timestamp by option("--timestamp")
        .map("timestamp in ISO format") { timestampStr ->
            timestampStr?.let {
                try {
                    LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                } catch (e: DateTimeParseException) {
                    // Try alternative formats
                    try {
                        LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    } catch (e2: DateTimeParseException) { null }
                }
            }
        }

    val scheduledTime by option("--time")
        .map("time in HH:MM format") { timeStr ->
            timeStr?.let {
                try {
                    LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm"))
                } catch (e: DateTimeParseException) { null }
                }
            }
        }
}
```

### Size and Duration Parsing

```kotlin
import java.time.Duration
import java.time.format.DateTimeParseException

class ResourceApp : Arguments() {
    val maxSize by option("--max-size")
        .map("size with unit (e.g., 100MB, 2GB)") { sizeStr ->
            sizeStr?.let { parseSize(it) }
        }

    val timeout by option("--timeout")
        .map("duration (e.g., 30s, 5m, 2h)") { durationStr ->
            durationStr?.let { parseDuration(it) }
        }

    val memoryLimit by option("--memory")
        .map("memory size (e.g., 512M, 2G)") { memStr ->
            memStr?.let { parseMemorySize(it) }
        }
}

fun parseSize(sizeStr: String): Long? {
    val regex = """(\d+(?:\.\d+)?)\s*(B|KB|MB|GB|TB)?""".toRegex(RegexOption.IGNORE_CASE)
    val match = regex.matchEntire(sizeStr.trim()) ?: return null

    val number = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].uppercase().ifEmpty { "B" }

    val multiplier = when (unit) {
        "B" -> 1L
        "KB" -> 1024L
        "MB" -> 1024L * 1024L
        "GB" -> 1024L * 1024L * 1024L
        "TB" -> 1024L * 1024L * 1024L * 1024L
        else -> return null
    }

    return (number * multiplier).toLong()
}

fun parseDuration(durationStr: String): Duration? {
    return try {
        // Try ISO 8601 duration format first
        Duration.parse(durationStr)
    } catch (e: DateTimeParseException) {
        // Try simple formats
        val regex = """(\d+)\s*([smhd])""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(durationStr.trim()) ?: return null

        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()

        when (unit) {
            "s" -> Duration.ofSeconds(amount)
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            "d" -> Duration.ofDays(amount)
            else -> null
        }
    }
}

fun parseMemorySize(memStr: String): Long? {
    val regex = """(\d+(?:\.\d+)?)\s*([KMGT])?B?""".toRegex(RegexOption.IGNORE_CASE)
    val match = regex.matchEntire(memStr.trim()) ?: return null

    val number = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].uppercase()

    val multiplier = when (unit) {
        "", "B" -> 1L
        "K" -> 1024L
        "M" -> 1024L * 1024L
        "G" -> 1024L * 1024L * 1024L
        "T" -> 1024L * 1024L * 1024L * 1024L
        else -> return null
    }

    return (number * multiplier).toLong()
}
```

### Network Address Parsing

```kotlin
import java.net.InetAddress
import java.net.InetSocketAddress

class NetworkApp : Arguments() {
    val bindAddress by option("--bind")
        .map("IP address") { addressStr ->
            addressStr?.let {
                try {
                    InetAddress.getByName(it)
                } catch (e: Exception) { null }
            }
        }

    val serverEndpoint by option("--server")
        .map("host:port address") { endpointStr ->
            endpointStr?.let { parseHostPort(it) }
        }

    val proxies by option("--proxy").list()
        .map("proxy addresses") { proxyList ->
            proxyList?.mapNotNull { parseHostPort(it) }
        }
}

fun parseHostPort(hostPort: String): InetSocketAddress? {
    val parts = hostPort.split(":")
    if (parts.size != 2) return null

    val host = parts[0].trim()
    val port = parts[1].trim().toIntOrNull()

    return if (port != null && port in 1..65535) {
        try {
            InetSocketAddress(host, port)
        } catch (e: Exception) { null }
        } else null
}
```

## Configuration Objects

Transform complex configuration strings into structured objects:

```kotlin
data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String? = null,
    val password: String? = null,
    val ssl: Boolean = false
)

data class RedisConfig(
    val host: String,
    val port: Int = 6379,
    val database: Int = 0,
    val password: String? = null
)

class DatabaseApp : Arguments() {
    val database by option("--database")
        .map("database URL") { url ->
            url?.let { parseDatabaseUrl(it) }
        }

    val redis by option("--redis")
        .map("Redis connection string") { redisStr ->
            redisStr?.let { parseRedisConfig(it) }
        }

    val servers by option("--server").list()
        .map("server configurations") { serverList ->
            serverList?.mapNotNull { parseServerConfig(it) }
        }
}

fun parseDatabaseUrl(url: String): DatabaseConfig? {
    // Parse URLs like: postgresql://user:pass@host:5432/dbname?ssl=true
    val regex = """(\w+)://(?:([^:]+)(?::([^@]+))?@)?([^:/]+)(?::(\d+))?/([^?]+)(?:\?(.+))?""".toRegex()
    val match = regex.matchEntire(url) ?: return null

    val scheme = match.groupValues[1]
    val username = match.groupValues[2].takeIf { it.isNotEmpty() }
    val password = match.groupValues[3].takeIf { it.isNotEmpty() }
    val host = match.groupValues[4]
    val port = match.groupValues[5].toIntOrNull() ?: when (scheme) {
        "postgresql" -> 5432
        "mysql" -> 3306
        else -> return null
    }
    val database = match.groupValues[6]
    val queryString = match.groupValues[7]

    val ssl = queryString?.contains("ssl=true") == true

    return DatabaseConfig(host, port, database, username, password, ssl)
}

fun parseRedisConfig(redisStr: String): RedisConfig? {
    // Parse formats like: localhost:6379/0, redis://password@host:port/db
    return when {
        redisStr.startsWith("redis://") -> {
            val regex = """redis://(?:([^@]+)@)?([^:/]+)(?::(\d+))?(?:/(\d+))?""".toRegex()
            val match = regex.matchEntire(redisStr) ?: return null

            val password = match.groupValues[1].takeIf { it.isNotEmpty() }
            val host = match.groupValues[2]
            val port = match.groupValues[3].toIntOrNull() ?: 6379
            val database = match.groupValues[4].toIntOrNull() ?: 0

            RedisConfig(host, port, database, password)
        }
        else -> {
            // Simple host:port/db format
            val parts = redisStr.split("/")
            val hostPort = parts[0]
            val database = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val hostPortParts = hostPort.split(":")
            val host = hostPortParts[0]
            val port = hostPortParts.getOrNull(1)?.toIntOrNull() ?: 6379

            RedisConfig(host, port, database)
        }
    }
}

data class ServerConfig(val name: String, val host: String, val port: Int, val weight: Int = 1)

fun parseServerConfig(serverStr: String): ServerConfig? {
    // Parse formats like: name=host:port:weight or host:port
    val parts = serverStr.split("=")
    return when (parts.size) {
        1 -> {
            // Simple host:port format
            val hostPortWeight = parts[0].split(":")
            if (hostPortWeight.size >= 2) {
                val host = hostPortWeight[0]
                val port = hostPortWeight[1].toIntOrNull() ?: return null
                val weight = hostPortWeight.getOrNull(2)?.toIntOrNull() ?: 1
                ServerConfig(host, host, port, weight)
            } else null
        }
        2 -> {
            // name=host:port:weight format
            val name = parts[0]
            val hostPortWeight = parts[1].split(":")
            if (hostPortWeight.size >= 2) {
                val host = hostPortWeight[0]
                val port = hostPortWeight[1].toIntOrNull() ?: return null
                val weight = hostPortWeight.getOrNull(2)?.toIntOrNull() ?: 1
                ServerConfig(name, host, port, weight)
            } else null
        }
        else -> null
    }
}
```

## Collection Transformations

Transform collections while preserving the collection type:

```kotlin
class CollectionApp : Arguments() {
    // Transform list elements
    val configFiles by option("--config").list()
        .map("readable config files") { paths ->
            paths?.mapNotNull { path ->
                File(path).takeIf { it.exists() && it.canRead() }
            }
        }

    // Transform and validate ports
    val serverPorts by option("--port").list()
        .map("valid port numbers") { ports ->
            ports?.mapNotNull { portStr ->
                portStr.toIntOrNull()?.takeIf { it in 1..65535 }
            }
        }

    // Transform set elements
    val allowedHosts by option("--allow-host").set()
        .map("valid hostnames") { hosts ->
            hosts?.mapNotNull { host ->
                if (isValidHostname(host)) host.lowercase() else null
            }?.toSet()
        }
}

fun isValidHostname(hostname: String): Boolean {
    if (hostname.isEmpty() || hostname.length > 253) return false

    val labels = hostname.split(".")
    return labels.all { label ->
        label.isNotEmpty() &&
        label.length <= 63 &&
        label.all { it.isLetterOrDigit() || it == '-' } &&
        !label.startsWith("-") &&
        !label.endsWith("-")
    }
}
```

## Chaining Transformations

You can chain transformations with validation:

```kotlin
class ChainedApp : Arguments() {
    val configFile by option("--config")
        .map("existing file") { path ->
            path?.let { File(it).takeIf { f -> f.exists() } }
        }
        .validate("Config file must be readable") { it?.canRead() == true }
        .map("JSON config file") { file ->
            file?.let {
                if (it.extension == "json") it else null
            }
        }

    val apiEndpoint by option("--api-endpoint")
        .map("valid URL") { urlStr ->
            urlStr?.let {
                try { URL(it) } catch (e: Exception) { null }
            }
        }
        .validate("Must use HTTPS") { it?.protocol == "https" }
        .map("API endpoint") { url ->
            url?.let {
                // Ensure it ends with /api
                if (it.path.endsWith("/api")) it
                else URL("${it.protocol}://${it.host}:${it.port}${it.path}/api")
            }
        }
}
```

## Non-Nullable Transformations

For non-nullable options, transformations work with NonNullableOptionBuilder:

```kotlin
class NonNullApp : Arguments() {
    // Transform required option
    val configFile by option("--config").required()
        .map("config file") { path -> File(path) }

    // Transform option with default
    val serverPort by option("--port").default("8080")
        .map("port number") { portStr -> portStr.toInt() }

    // Chain transformation after default
    val logLevel by option("--log-level").enum<LogLevel>().default(LogLevel.INFO)
        .map("log configuration") { level ->
            LogConfig(level, level == LogLevel.DEBUG)
        }
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
data class LogConfig(val level: LogLevel, val verbose: Boolean)
```

## Complete Examples

### Configuration File Parser

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.Properties

class ConfigApp : Arguments() {
    val jsonConfig by option("--json-config")
        .map("JSON configuration") { jsonStr ->
            jsonStr?.let {
                try {
                    Json.parseToJsonElement(it) as? JsonObject
                } catch (e: Exception) { null }
                }
            }
        }

    val propertiesFile by option("--properties")
        .map("properties file") { path ->
            path?.let {
                val file = File(it)
                if (file.exists() && file.canRead()) {
                    val props = Properties()
                    file.inputStream().use { props.load(it) }
                    props
                } else null
            }
        }

    val keyValuePairs by option("--set").list()
        .map("key=value pairs") { pairs ->
            pairs?.mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }?.toMap()
        }
}
```

### Resource Specification Parser

```kotlin
data class ResourceSpec(
    val cpu: String,
    val memory: Long,
    val storage: Long? = null
)

data class ServiceSpec(
    val name: String,
    val image: String,
    val tag: String = "latest",
    val resources: ResourceSpec
)

class KubernetesApp : Arguments() {
    val resourceLimits by option("--resources")
        .map("resource specification (cpu=1,memory=512M,storage=10G)") { resourceStr ->
            resourceStr?.let { parseResourceSpec(it) }
        }

    val services by option("--service").list()
        .map("service specifications") { serviceList ->
            serviceList?.mapNotNull { parseServiceSpec(it) }
        }

    val nodeSelector by option("--node-selector")
        .map("node selector labels") { selectorStr ->
            selectorStr?.let { parseLabels(it) }
        }
}

fun parseResourceSpec(resourceStr: String): ResourceSpec? {
    val parts = resourceStr.split(",").associate { part ->
        val keyValue = part.split("=", limit = 2)
        if (keyValue.size == 2) keyValue[0].trim() to keyValue[1].trim() else return null
    }

    val cpu = parts["cpu"] ?: return null
    val memory = parts["memory"]?.let { parseMemorySize(it) } ?: return null
    val storage = parts["storage"]?.let { parseSize(it) }

    return ResourceSpec(cpu, memory, storage)
}

fun parseServiceSpec(serviceStr: String): ServiceSpec? {
    // Format: name:image:tag,cpu=1,memory=512M
    val mainParts = serviceStr.split(",", limit = 2)
    if (mainParts.isEmpty()) return null

    val imageParts = mainParts[0].split(":")
    if (imageParts.size < 2) return null

    val name = imageParts[0]
    val image = imageParts[1]
    val tag = imageParts.getOrNull(2) ?: "latest"

    val resourceStr = mainParts.getOrNull(1) ?: return null
    val resources = parseResourceSpec(resourceStr) ?: return null

    return ServiceSpec(name, image, tag, resources)
}

fun parseLabels(labelStr: String): Map<String, String>? {
    return labelStr.split(",").associate { part ->
        val keyValue = part.split("=", limit = 2)
        if (keyValue.size == 2) {
            keyValue[0].trim() to keyValue[1].trim()
        } else return null
    }
}
```

## Best Practices

### 1. Provide Clear Transformation Descriptions

```kotlin
// Good: Clear, specific descriptions
val timeout by option("--timeout")
    .map("duration in seconds (e.g., 30, 60, 120)") { it?.toIntOrNull() }

val endpoint by option("--endpoint")
    .map("valid HTTPS URL") { url ->
        url?.let { try { URL(it) } catch (e: Exception) { null } }
    }

// Avoid: Vague or missing descriptions
val data by option("--data").map { it?.toIntOrNull() }
```

### 2. Handle All Edge Cases

```kotlin
// Good: Comprehensive error handling
val percentage by option("--load")
    .map("percentage (0-100)") { percentStr ->
        when {
            percentStr.isNullOrBlank() -> null
            percentStr.endsWith("%") -> {
                percentStr.dropLast(1).toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
            }
            else -> percentStr.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
        }
    }

// Avoid: Minimal error handling
val percentage by option("--load").map { it?.toDouble() }
```

### 3. Keep Transformations Pure

```kotlin
// Good: Pure transformation functions
val configFile by option("--config")
    .map("readable config file") { path ->
        path?.let { File(it).takeIf { f -> f.exists() && f.canRead() } }
    }

// Avoid: Side effects in transformations
val configFile by option("--config")
    .map("config file") { path ->
        path?.let {
            println("Loading config from $it")  // Don't do this
            File(it)
        }
    }
```

### 4. Validate After Transformation

```kotlin
// Good: Transform then validate
val serverConfig by option("--server")
    .map("server configuration") { parseServerConfig(it) }
    .validate("Server must be reachable") { config ->
        // Validate the transformed object
        config?.let { isServerReachable(it.host, it.port) } != false
    }

// Consider: Some validation might be better in the transformation
val port by option("--port")
    .map("valid port (1-65535)") { portStr ->
        portStr?.toIntOrNull()?.takeIf { it in 1..65535 }
    }
```

Custom transformations enable rich, type-safe CLI interfaces that work with your application's domain objects while providing clear error messages for invalid inputs.