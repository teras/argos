# Password Input

Argos provides secure password input capabilities that prompt users for sensitive information without echoing to the terminal. The password system includes confirmation prompts, retry logic, and seamless integration with environment variables.

## Basic Password Input

Use `.password()` to create password options with secure input:

```kotlin
class MyApp : Arguments() {
    val dbPassword by option("--db-password")
        .password()
        .help("Database password (will prompt if not provided)")

    val apiKey by option("--api-key")
        .password(prompt = "Enter API key: ")
        .required()
}
```

### Usage Examples

```bash
# Password prompted when not provided via environment or command line
myapp --db-user admin
# Prompts: Enter password for --db-password:
# User types password (not echoed to terminal)

# Can still be provided via environment variable
DB_PASSWORD=secret myapp --db-user admin
# No prompt - uses environment variable

# Or via command line (not recommended for security)
myapp --db-password secret --db-user admin
```

## Password Confirmation

Enable confirmation prompts for critical passwords:

```kotlin
class MyApp : Arguments() {
    val newPassword by option("--new-password")
        .password(
            prompt = "Enter new password: ",
            confirmPrompt = "Confirm new password: ",
            requireConfirmation = true
        )
        .required()

    val masterKey by option("--master-key")
        .password(
            prompt = "Enter master key: ",
            confirmPrompt = "Confirm master key: ",
            requireConfirmation = true,
            mismatchPrompt = "Keys don't match. Try again.",
            maxRetries = 3
        )
        .required()
}
```

### Confirmation Flow

```bash
myapp --new-password
# Prompts: Enter new password:
# User types: ********
# Prompts: Confirm new password:
# User types: ********
# Success: Passwords match

myapp --master-key
# Prompts: Enter master key:
# User types: ********
# Prompts: Confirm master key:
# User types: *******  (typo)
# Output: Keys don't match. Try again.
# Prompts: Enter master key:
# (Process repeats up to maxRetries times)
```

## Configuration Options

### Full Password Configuration

```kotlin
class SecureApp : Arguments() {
    val adminPassword by option("--admin-password")
        .password(
            prompt = "Enter admin password: ",           // Custom prompt message
            confirmPrompt = "Confirm admin password: ",  // Confirmation prompt
            requireConfirmation = true,                  // Enable confirmation
            mismatchPrompt = "Passwords don't match. Please try again.", // Mismatch message
            maxRetries = 3,                             // Maximum retry attempts
            hideInput = true                            // Hide input (default: true)
        )
        .required()
        .hidden()                                       // Hide from help output

    val encryptionKey by option("--encryption-key")
        .password(
            prompt = "Encryption key: ",
            requireConfirmation = false                 // No confirmation needed
        )
        .validate("Key must be at least 32 characters") { it.length >= 32 }
}
```

### Parameter Details

- **prompt**: Custom prompt message (default: "Enter password for {option}: ")
- **confirmPrompt**: Confirmation prompt message (default: "Confirm password: ")
- **requireConfirmation**: Whether to prompt for confirmation (default: false)
- **mismatchPrompt**: Message when passwords don't match (default: "Passwords don't match. Try again.")
- **maxRetries**: Maximum retry attempts for confirmation (default: 3)
- **hideInput**: Whether to hide typed characters (default: true)

## Environment Variable Integration

Password options work seamlessly with environment variables:

```kotlin
class DatabaseApp : Arguments() {
    val dbPassword by option("--db-password")
        .password()
        .fromEnv("DB_PASSWORD")
        .help("Database password (env: DB_PASSWORD)")

    val apiToken by option("--api-token")
        .password(prompt = "Enter API token: ")
        .fromEnv("API_TOKEN")
        .required()
        .hidden()

    val sslKeyPassword by option("--ssl-key-password")
        .password(
            prompt = "SSL key password: ",
            requireConfirmation = false
        )
        .fromEnv("SSL_KEY_PASSWORD")
}
```

### Priority Order

1. **Command line argument** (not recommended for passwords)
2. **Environment variable** (preferred for automation)
3. **Interactive prompt** (preferred for manual use)

```bash
# Method 1: Environment variable (recommended for scripts)
export DB_PASSWORD=secret
export API_TOKEN=abc123
myapp

# Method 2: Interactive prompts (recommended for manual use)
myapp
# Prompts for both passwords interactively

# Method 3: Mixed approach
export DB_PASSWORD=secret
myapp
# Uses env var for DB_PASSWORD, prompts for API_TOKEN
```

## Security Features

### Hidden Options

Hide password options from help output:

```kotlin
class SecureApp : Arguments() {
    // Visible in help
    val username by option("--username")
        .required()
        .help("Username for authentication")

    val host by option("--host")
        .default("localhost")
        .help("Database host")

    // Hidden from help
    val password by option("--password")
        .password()
        .required()
        .hidden()                               // Not shown in --help

    val apiKey by option("--api-key")
        .password(prompt = "API key: ")
        .fromEnv("API_KEY")
        .hidden()
}
```

### Validation with Passwords

```kotlin
class UserAccount : Arguments() {
    val password by option("--password")
        .password(
            prompt = "Enter password: ",
            confirmPrompt = "Confirm password: ",
            requireConfirmation = true
        )
        .validate("Password must be at least 8 characters") { it.length >= 8 }
        .validate("Password must contain uppercase letter") { it.any { c -> c.isUpperCase() } }
        .validate("Password must contain lowercase letter") { it.any { c -> c.isLowerCase() } }
        .validate("Password must contain digit") { it.any { c -> c.isDigit() } }
        .validate("Password must contain special character") { it.any { c -> !c.isLetterOrDigit() } }

    val confirmEmail by option("--email")
        .validate("Must be valid email") { it.contains("@") }
        .required()
}
```

## Advanced Password Patterns

### Multi-Step Authentication

```kotlin
class AuthApp : Arguments() {
    val username by option("--username")
        .required()
        .help("Username for authentication")

    val password by option("--password")
        .password(prompt = "Password: ")
        .required()
        .hidden()

    val mfaToken by option("--mfa-token")
        .password(
            prompt = "Enter MFA token: ",
            requireConfirmation = false,
            hideInput = false                   // Show MFA tokens (they're temporary)
        )
        .validate("MFA token must be 6 digits") { it.matches(Regex("\\d{6}")) }
        .hidden()

    val backupCode by option("--backup-code")
        .password(
            prompt = "Enter backup code: ",
            requireConfirmation = false
        )
        .validate("Backup code format invalid") { it.matches(Regex("\\d{4}-\\d{4}-\\d{4}")) }
        .hidden()
}
```

### Certificate and Key Management

```kotlin
class CertificateApp : Arguments() {
    val certificateFile by option("--cert-file")
        .validate("Certificate file must exist") { File(it).exists() }
        .required()

    val keyFile by option("--key-file")
        .validate("Key file must exist") { File(it).exists() }
        .required()

    val keyPassword by option("--key-password")
        .password(
            prompt = "Enter private key password: ",
            requireConfirmation = false
        )
        .fromEnv("KEY_PASSWORD")
        .hidden()

    val keystorePassword by option("--keystore-password")
        .password(
            prompt = "Enter keystore password: ",
            confirmPrompt = "Confirm keystore password: ",
            requireConfirmation = true
        )
        .fromEnv("KEYSTORE_PASSWORD")
        .validate("Keystore password must be at least 6 characters") { it.length >= 6 }
        .hidden()
}
```

### Database Connection Patterns

```kotlin
class DatabaseTool : Arguments() {
    val host by option("--host")
        .default("localhost")
        .help("Database host")

    val port by option("--port").int()
        .default(5432)
        .help("Database port")

    val database by option("--database")
        .required()
        .help("Database name")

    val username by option("--username")
        .required()
        .help("Database username")

    val password by option("--password")
        .password()
        .fromEnv("DB_PASSWORD")
        .hidden()

    val sslMode by option("--ssl-mode")
        .oneOf("disable", "require", "verify-ca", "verify-full")
        .default("require")
        .help("SSL connection mode")

    val sslCertPassword by option("--ssl-cert-password")
        .password(
            prompt = "SSL certificate password: ",
            requireConfirmation = false
        )
        .fromEnv("SSL_CERT_PASSWORD")
        .requireIfValue(::sslMode) { it in listOf("verify-ca", "verify-full") }
        .hidden()
}
```

## Error Handling and Edge Cases

### Retry Logic

```kotlin
class RetryApp : Arguments() {
    val sensitiveData by option("--sensitive")
        .password(
            prompt = "Enter sensitive data: ",
            confirmPrompt = "Confirm sensitive data: ",
            requireConfirmation = true,
            mismatchPrompt = "Data doesn't match. Please try again.",
            maxRetries = 5                      // Allow more retries for complex data
        )
        .validate("Data must be at least 20 characters") { it.length >= 20 }
        .validate("Data must contain special characters") { it.any { c -> !c.isLetterOrDigit() } }
}
```

### Graceful Fallbacks

```kotlin
class FlexibleAuth : Arguments() {
    val authMethod by option("--auth-method")
        .oneOf("password", "token", "key")
        .default("password")

    val password by option("--password")
        .password()
        .requireIfValue(::authMethod) { it == "password" }
        .hidden()

    val token by option("--token")
        .password(prompt = "Enter auth token: ", requireConfirmation = false)
        .requireIfValue(::authMethod) { it == "token" }
        .hidden()

    val keyFile by option("--key-file")
        .validate("Key file must exist") { File(it).exists() }
        .requireIfValue(::authMethod) { it == "key" }

    val keyPassword by option("--key-password")
        .password(prompt = "Key password: ", requireConfirmation = false)
        .requireIfValue(::authMethod) { it == "key" }
        .hidden()
}
```

## Platform Considerations

### Cross-Platform Compatibility

```kotlin
class CrossPlatformApp : Arguments() {
    val password by option("--password")
        .password(
            prompt = "Password: ",
            hideInput = true                    // Works on all platforms
        )
        .fromEnv("APP_PASSWORD")               // Consistent across platforms
        .hidden()
}
```

The password input system automatically handles platform differences:
- **JVM**: Uses `System.console()` when available, falls back to standard input
- **Native Linux/ARM**: Uses POSIX terminal control for hiding input
- **Windows**: Uses Windows console API for secure input
- **Fallback**: Graceful degradation when secure input isn't available

## Best Practices

### 1. Always Use Environment Variables for Automation

```kotlin
// Good: Support both interactive and automated use
val password by option("--password")
    .password()
    .fromEnv("DB_PASSWORD")
    .hidden()

// Avoid: Only interactive prompts (breaks automation)
val password by option("--password")
    .password()
    .required()
```

### 2. Hide Sensitive Options from Help

```kotlin
// Good: Keep secrets out of help output
val apiKey by option("--api-key")
    .password()
    .fromEnv("API_KEY")
    .hidden()                               // Critical for security

// Avoid: Exposing sensitive options in help
val apiKey by option("--api-key")
    .password()
    .help("API key for authentication")     // Visible in --help
```

### 3. Use Confirmation for Critical Operations

```kotlin
// Good: Confirm destructive or critical operations
val masterPassword by option("--master-password")
    .password(
        requireConfirmation = true,
        prompt = "Enter master password: ",
        confirmPrompt = "Confirm master password: "
    )

// Good: Skip confirmation for read-only operations
val readPassword by option("--read-password")
    .password(requireConfirmation = false)
```

### 4. Validate Password Requirements

```kotlin
// Good: Enforce security requirements
val userPassword by option("--user-password")
    .password(requireConfirmation = true)
    .validate("Password must be at least 12 characters") { it.length >= 12 }
    .validate("Password must contain mixed case") {
        it.any { c -> c.isUpperCase() } && it.any { c -> c.isLowerCase() }
    }
    .validate("Password must contain numbers") { it.any { c -> c.isDigit() } }
```

### 5. Provide Clear Context

```kotlin
// Good: Clear, descriptive prompts
val dbPassword by option("--db-password")
    .password(prompt = "Enter database password for connection: ")

val certPassword by option("--cert-password")
    .password(prompt = "Enter SSL certificate password: ")

// Avoid: Generic prompts
val password by option("--password")
    .password(prompt = "Password: ")        // Which password?
```

The password input system provides secure, user-friendly password handling that works consistently across all supported platforms while maintaining security best practices.