# Database Migration Tool Example

This example demonstrates a comprehensive database migration tool using Argos with sophisticated validation, environment-specific constraints, and safety features for production environments.

## Complete Implementation

```kotlin
import onl.ycode.argos.*
import java.io.File
import java.time.LocalDateTime

enum class DatabaseType { POSTGRESQL, MYSQL, SQLITE, ORACLE, SQL_SERVER }
enum class MigrationDirection { UP, DOWN }
enum class Environment { DEVELOPMENT, TESTING, STAGING, PRODUCTION }
enum class BackupStrategy { NONE, SCHEMA_ONLY, FULL, INCREMENTAL }

class DatabaseMigrationTool : Arguments(
    appName = "db-migrate",
    appDescription = "Enterprise database migration tool with safety features",
    aggregateErrors = true,
    didYouMean = true,
    maxAggregatedErrors = 10
) {
    // Global configuration
    val configFile by option("--config", "-c")
        .help("Migration configuration file")
        .fromEnv("DB_MIGRATION_CONFIG")
        .validate("Config file must exist") { File(it).exists() }
        .validate("Must be JSON or YAML") { it.endsWith(".json") || it.endsWith(".yml") || it.endsWith(".yaml") }

    val verbosity by option("--verbose", "-v")
        .bool().list()
        .help("Increase verbosity (use multiple times: -v, -vv, -vvv)")

    val quiet by option("--quiet", "-q")
        .bool()
        .help("Suppress all output except errors")
        .conflictsWith(::verbosity)

    val dryRun by option("--dry-run")
        .bool()
        .help("Show what migrations would be applied without executing them")

    // Database connection fragment - shared across all operations
    val connectionFragment by domain(fragment = true)
        .required(::databaseType)
        .required(::host)
        .required(::database)
        .exactlyOne(::passwordAuth, ::keyAuth, ::trustedAuth)
        .requireIfValue(::passwordAuth, ::password) { it == true }
        .requireIfValue(::keyAuth, ::keyFile) { it == true }
        .requireIfValue(::sslMode) { it in listOf("require", "verify-ca", "verify-full") }

    // Environment safety fragment - production protection
    val environmentSafetyFragment by domain(fragment = true)
        .required(::environment)
        .requireIfValue(::environment, ::productionApproval) { it == Environment.PRODUCTION }
        .requireIfValue(::environment, ::stagingValidation) { it == Environment.STAGING }
        .requireIfValue(::environment, ::backupStrategy) { it in listOf(Environment.STAGING, Environment.PRODUCTION) }
        .conflicts(::skipValidation, ::forceExecution)

    // Backup management fragment
    val backupFragment by domain(fragment = true)
        .requireIfValue(::backupStrategy) { it != BackupStrategy.NONE }
        .requireIfAnyPresent(::backupLocation, ::backupStrategy)
        .requireIfValue(::backupStrategy, ::compressionLevel) { it == BackupStrategy.FULL }

    // Migration execution domain
    val migrateDomain by domain("migrate")
        .label("Execute Migrations")
        .help("Execute database migrations up or down")
        .aliases("run", "execute")
        .inherits(::connectionFragment, ::environmentSafetyFragment, ::backupFragment)
        .required(::direction)
        .exactlyOne(::migrationFile, ::migrationDirectory, ::migrationVersion, ::stepCount)
        .requireIfValue(::direction, ::confirmDowngrade) { it == MigrationDirection.DOWN }
        .requireIfValue(::direction, ::rollbackSafety) { it == MigrationDirection.DOWN && environment == Environment.PRODUCTION }
        .conflicts(::dryRun, ::autoConfirm)

    // Status and history domain
    val statusDomain by domain("status")
        .label("Migration Status")
        .help("Show current migration status and history")
        .aliases("info", "history")
        .inherits(::connectionFragment)
        .atMostOne(::showPending, ::showApplied, ::showAll)

    // Schema validation domain
    val validateDomain by domain("validate")
        .label("Validate Schema")
        .help("Validate database schema against expected state")
        .aliases("check")
        .inherits(::connectionFragment, ::environmentSafetyFragment)
        .atLeastOne(::validateSchema, ::validateData, ::validateConstraints)
        .requireIfAnyPresent(::validateData, ::dataValidationRules)

    // Backup management domain
    val backupDomain by domain("backup")
        .label("Create Backup")
        .help("Create database backup before migrations")
        .inherits(::connectionFragment, ::backupFragment)
        .required(::backupStrategy)
        .requireIfValue(::backupStrategy, ::backupSchedule) { it == BackupStrategy.INCREMENTAL }

    // Rollback domain
    val rollbackDomain by domain("rollback")
        .label("Rollback Migrations")
        .help("Rollback to a specific migration version")
        .inherits(::connectionFragment, ::environmentSafetyFragment, ::backupFragment)
        .required(::rollbackTarget)
        .required(::rollbackConfirmation)
        .requireIfValue(::environment, ::emergencyApproval) { it == Environment.PRODUCTION }
        .conflicts(::dryRun, ::skipRollbackValidation)

    // Database connection options
    val databaseType by option("--database-type", "--type")
        .enum<DatabaseType>()
        .fromEnv("DB_TYPE")
        .help("Database management system type")

    val host by option("--host", "-h")
        .fromEnv("DB_HOST")
        .default("localhost")
        .help("Database host address")

    val port by option("--port", "-p")
        .int()
        .fromEnv("DB_PORT")
        .validate("Port must be valid") { it in 1..65535 }
        .help("Database port number")

    val database by option("--database", "--db")
        .fromEnv("DB_NAME")
        .help("Database name")

    val username by option("--username", "--user", "-u")
        .fromEnv("DB_USER")
        .help("Database username")

    // Authentication options
    val passwordAuth by option("--password-auth")
        .bool()
        .help("Use password authentication")

    val password by option("--password")
        .input(hidden = true, prompt = "Enter database password: ")
        .fromEnv("DB_PASSWORD")
        .hidden()
        .help("Database password")

    val keyAuth by option("--key-auth")
        .bool()
        .help("Use key-based authentication")

    val keyFile by option("--key-file")
        .fromEnv("DB_KEY_FILE")
        .validate("Key file must exist") { File(it).exists() }
        .help("Private key file for authentication")

    val trustedAuth by option("--trusted-auth")
        .bool()
        .help("Use trusted/integrated authentication")

    // SSL/TLS configuration
    val sslMode by option("--ssl-mode")
        .oneOf("disable", "prefer", "require", "verify-ca", "verify-full")
        .default("prefer")
        .fromEnv("DB_SSL_MODE")
        .help("SSL connection mode")

    val sslCertificate by option("--ssl-cert")
        .requireIfValue(::sslMode) { it in listOf("verify-ca", "verify-full") }
        .validate("SSL certificate must exist") { File(it).exists() }
        .fromEnv("DB_SSL_CERT")
        .help("SSL certificate file")

    val sslKey by option("--ssl-key")
        .requireIfAnyPresent(::sslCertificate)
        .validate("SSL key must exist") { File(it).exists() }
        .fromEnv("DB_SSL_KEY")
        .help("SSL private key file")

    // Connection pooling and performance
    val connectionTimeout by option("--connection-timeout")
        .int()
        .default(30)
        .validate("Timeout must be positive") { it > 0 }
        .help("Connection timeout in seconds")

    val commandTimeout by option("--command-timeout")
        .int()
        .default(300)
        .validate("Command timeout must be positive") { it > 0 }
        .help("Command execution timeout in seconds")

    val maxConnections by option("--max-connections")
        .int()
        .default(5)
        .validate("Max connections must be positive") { it > 0 }
        .validate("Too many connections") { it <= 100 }
        .help("Maximum number of database connections")

    // Environment and safety
    val environment by option("--environment", "--env")
        .enum<Environment>()
        .fromEnv("MIGRATION_ENVIRONMENT")
        .help("Target environment for migrations")

    val productionApproval by option("--production-approval")
        .fromEnv("PRODUCTION_MIGRATION_TOKEN")
        .input(hidden = true, prompt = "Enter production approval token: ")
        .hidden()
        .help("Approval token required for production migrations")

    val stagingValidation by option("--staging-validation")
        .bool()
        .default(true)
        .help("Require validation checks for staging migrations")

    val emergencyApproval by option("--emergency-approval")
        .fromEnv("EMERGENCY_ROLLBACK_TOKEN")
        .input(hidden = true, prompt = "Enter emergency approval token: ")
        .hidden()
        .help("Emergency approval for production rollbacks")

    val skipValidation by option("--skip-validation")
        .bool()
        .help("Skip pre-migration validation (dangerous)")

    val forceExecution by option("--force")
        .bool()
        .help("Force migration execution despite warnings")

    // Migration specification
    val direction by option("--direction")
        .enum<MigrationDirection>()
        .default(MigrationDirection.UP)
        .help("Migration direction: up (apply) or down (rollback)")

    val migrationFile by option("--migration-file", "--file")
        .validate("Migration file must exist") { File(it).exists() }
        .validate("Must be SQL file") { it.endsWith(".sql") }
        .help("Specific migration file to execute")

    val migrationDirectory by option("--migration-directory", "--dir")
        .default("migrations/")
        .validate("Migration directory must exist") { File(it).isDirectory }
        .help("Directory containing migration files")

    val migrationVersion by option("--to-version", "--version")
        .help("Target migration version")

    val stepCount by option("--steps")
        .int()
        .validate("Steps must be positive") { it > 0 }
        .validate("Too many steps") { it <= 1000 }
        .help("Number of migration steps to execute")

    val confirmDowngrade by option("--confirm-downgrade")
        .help("Confirmation phrase for downgrade migrations")
        .validate("Must type exact confirmation") { it == "CONFIRM_DOWNGRADE" }

    val rollbackSafety by option("--rollback-safety")
        .bool()
        .default(true)
        .help("Enable rollback safety checks for production")

    val autoConfirm by option("--auto-confirm")
        .bool()
        .help("Automatically confirm migration steps")

    // Backup configuration
    val backupStrategy by option("--backup-strategy")
        .enum<BackupStrategy>()
        .default(BackupStrategy.SCHEMA_ONLY)
        .fromEnv("BACKUP_STRATEGY")
        .help("Backup strategy before migrations")

    val backupLocation by option("--backup-location")
        .fromEnv("BACKUP_LOCATION")
        .help("Location to store database backups")

    val compressionLevel by option("--compression-level")
        .int()
        .default(6)
        .validate("Compression level must be 0-9") { it in 0..9 }
        .help("Backup compression level")

    val backupSchedule by option("--backup-schedule")
        .help("Schedule for incremental backups (cron format)")

    val retentionPeriod by option("--retention-period")
        .int()
        .default(30)
        .validate("Retention period must be positive") { it > 0 }
        .help("Backup retention period in days")

    // Status and reporting options
    val showPending by option("--show-pending")
        .bool()
        .help("Show pending migrations only")

    val showApplied by option("--show-applied")
        .bool()
        .help("Show applied migrations only")

    val showAll by option("--show-all")
        .bool()
        .default(true)
        .help("Show all migration information")

    val outputFormat by option("--output-format")
        .oneOf("table", "json", "csv", "xml")
        .default("table")
        .help("Output format for status information")

    // Validation options
    val validateSchema by option("--validate-schema")
        .bool()
        .help("Validate database schema structure")

    val validateData by option("--validate-data")
        .bool()
        .help("Validate data integrity and constraints")

    val validateConstraints by option("--validate-constraints")
        .bool()
        .help("Validate foreign key and check constraints")

    val dataValidationRules by option("--data-validation-rules")
        .validate("Validation rules file must exist") { File(it).exists() }
        .help("Data validation rules configuration file")

    val schemaSnapshot by option("--schema-snapshot")
        .help("Reference schema snapshot for validation")

    // Rollback options
    val rollbackTarget by option("--rollback-target")
        .help("Target version for rollback operation")

    val rollbackConfirmation by option("--rollback-confirmation")
        .help("Confirmation phrase for rollback")
        .validate("Must confirm rollback") { it == "ROLLBACK_DATABASE" }

    val skipRollbackValidation by option("--skip-rollback-validation")
        .bool()
        .help("Skip validation during rollback")

    val createRestorePoint by option("--create-restore-point")
        .bool()
        .default(true)
        .help("Create restore point before rollback")

    // Advanced options
    val transactionMode by option("--transaction-mode")
        .oneOf("per-migration", "all-migrations", "none")
        .default("per-migration")
        .help("Transaction handling mode")

    val lockTimeout by option("--lock-timeout")
        .int()
        .default(60)
        .validate("Lock timeout must be positive") { it > 0 }
        .help("Lock timeout in seconds")

    val migrationTable by option("--migration-table")
        .default("schema_migrations")
        .help("Table name for migration tracking")

    val charset by option("--charset")
        .default("utf8mb4")
        .help("Character set for database operations")

    val logFile by option("--log-file")
        .fromEnv("MIGRATION_LOG_FILE")
        .help("Log file for migration operations")

    // Built-in options
    val help by help()
    val version by version("2.3.1")
}

fun main(args: Array<String>) {
    val migrationTool = DatabaseMigrationTool()
    migrationTool.parse(args,
        onError = { error, _ ->
            System.err.println("Error: ${error.message}")
            migrationTool.printUsage()
        }
    ) ?: return

    // Configure logging
    configureLogging(migrationTool.verbosity, migrationTool.quiet, migrationTool.logFile)

    // Validate database configuration
    validateDatabaseConfig(migrationTool)

    // Execute the appropriate domain
    when {
        migrationTool.migrateDomain -> executeMigration(migrationTool)
        migrationTool.statusDomain -> showMigrationStatus(migrationTool)
        migrationTool.validateDomain -> validateDatabase(migrationTool)
        migrationTool.backupDomain -> createBackup(migrationTool)
        migrationTool.rollbackDomain -> executeRollback(migrationTool)
        else -> {
            println("No command specified. Use --help for available commands.")
            showQuickStart()
        }
    }
}

fun configureLogging(verbosity: Int, quiet: Boolean, logFile: String?) {
    val level = when {
        quiet -> LogLevel.ERROR
        verbosity >= 3 -> LogLevel.TRACE
        verbosity >= 2 -> LogLevel.DEBUG
        verbosity >= 1 -> LogLevel.INFO
        else -> LogLevel.WARN
    }

    Logger.configure(level, logFile)
    Logger.info("Database migration tool starting...")
}

fun validateDatabaseConfig(config: DatabaseMigrationTool) {
    // Validate database type specific requirements
    when (config.databaseType) {
        DatabaseType.POSTGRESQL -> {
            if (config.port == null) {
                config.port = 5432  // Default PostgreSQL port
            }
        }
        DatabaseType.MYSQL -> {
            if (config.port == null) {
                config.port = 3306  // Default MySQL port
            }
            if (config.charset == "utf8mb4" && config.environment == Environment.PRODUCTION) {
                Logger.info("Using utf8mb4 charset for MySQL production environment")
            }
        }
        DatabaseType.SQLITE -> {
            if (config.host != "localhost") {
                Logger.warning("SQLite ignores host parameter")
            }
        }
        DatabaseType.ORACLE -> {
            if (config.port == null) {
                config.port = 1521  // Default Oracle port
            }
        }
        DatabaseType.SQL_SERVER -> {
            if (config.port == null) {
                config.port = 1433  // Default SQL Server port
            }
        }
    }

    Logger.debug("Database configuration validated for ${config.databaseType}")
}

fun executeMigration(config: DatabaseMigrationTool) {
    Logger.info("Starting migration process...")

    val migrationContext = MigrationContext(
        databaseType = config.databaseType,
        host = config.host,
        port = config.port ?: getDefaultPort(config.databaseType),
        database = config.database!!,
        username = config.username,
        direction = config.direction,
        environment = config.environment!!,
        dryRun = config.dryRun,
        transactionMode = config.transactionMode,
        lockTimeout = config.lockTimeout
    )

    // Pre-migration validation
    if (!config.skipValidation) {
        Logger.info("Running pre-migration validation...")
        val validationResult = validateMigrationPreconditions(migrationContext)
        if (!validationResult.isValid) {
            Logger.error("Pre-migration validation failed:")
            validationResult.errors.forEach { Logger.error("  - $it") }
            if (!config.forceExecution) {
                kotlin.system.exitProcess(1)
            }
        }
    }

    // Create backup if required
    if (config.backupStrategy != BackupStrategy.NONE) {
        Logger.info("Creating pre-migration backup...")
        val backupResult = createPreMigrationBackup(config)
        if (!backupResult.success) {
            Logger.error("Backup failed: ${backupResult.error}")
            if (config.environment == Environment.PRODUCTION) {
                kotlin.system.exitProcess(1)
            }
        } else {
            Logger.info("Backup created: ${backupResult.backupFile}")
        }
    }

    // Determine migrations to execute
    val migrations = when {
        config.migrationFile != null -> listOf(loadMigration(config.migrationFile!!))
        config.migrationVersion != null -> getMigrationsToVersion(config.migrationVersion!!, config.direction)
        config.stepCount != null -> getMigrationsBySteps(config.stepCount!!, config.direction)
        else -> getPendingMigrations(config.migrationDirectory)
    }

    Logger.info("Found ${migrations.size} migrations to execute")

    // Execute migrations
    if (config.dryRun) {
        Logger.info("DRY RUN: Would execute the following migrations:")
        migrations.forEach { migration ->
            Logger.info("  ${config.direction}: ${migration.version} - ${migration.description}")
        }
    } else {
        Logger.info("Executing migrations...")
        val migrationResult = executeMigrations(migrationContext, migrations)

        if (migrationResult.success) {
            Logger.info("Migration completed successfully!")
            Logger.info("Applied ${migrationResult.appliedCount} migrations")
            if (migrationResult.warnings.isNotEmpty()) {
                Logger.warning("Warnings:")
                migrationResult.warnings.forEach { Logger.warning("  - $it") }
            }
        } else {
            Logger.error("Migration failed: ${migrationResult.error}")

            // Automatic rollback on failure for production
            if (config.environment == Environment.PRODUCTION && config.rollbackSafety) {
                Logger.info("Initiating automatic rollback...")
                val rollbackResult = performAutomaticRollback(migrationContext, migrationResult.lastSuccessfulMigration)
                if (rollbackResult.success) {
                    Logger.info("Automatic rollback completed")
                } else {
                    Logger.error("Automatic rollback failed: ${rollbackResult.error}")
                }
            }

            kotlin.system.exitProcess(1)
        }
    }
}

fun showMigrationStatus(config: DatabaseMigrationTool) {
    Logger.info("Retrieving migration status...")

    val statusContext = StatusContext(
        databaseType = config.databaseType,
        host = config.host,
        port = config.port ?: getDefaultPort(config.databaseType),
        database = config.database!!,
        username = config.username,
        outputFormat = config.outputFormat
    )

    val statusResult = getMigrationStatus(statusContext)

    when (config.outputFormat) {
        "table" -> displayStatusTable(statusResult, config.showPending, config.showApplied, config.showAll)
        "json" -> displayStatusJson(statusResult)
        "csv" -> displayStatusCsv(statusResult)
        "xml" -> displayStatusXml(statusResult)
    }

    Logger.info("Migration status retrieved successfully")
}

fun validateDatabase(config: DatabaseMigrationTool) {
    Logger.info("Starting database validation...")

    val validationContext = ValidationContext(
        databaseType = config.databaseType,
        host = config.host,
        port = config.port ?: getDefaultPort(config.databaseType),
        database = config.database!!,
        username = config.username,
        validateSchema = config.validateSchema,
        validateData = config.validateData,
        validateConstraints = config.validateConstraints,
        validationRules = config.dataValidationRules,
        schemaSnapshot = config.schemaSnapshot
    )

    val validationResult = performDatabaseValidation(validationContext)

    if (validationResult.isValid) {
        Logger.info("Database validation passed")
        Logger.info("Schema validation: ${if (config.validateSchema) "PASSED" else "SKIPPED"}")
        Logger.info("Data validation: ${if (config.validateData) "PASSED" else "SKIPPED"}")
        Logger.info("Constraint validation: ${if (config.validateConstraints) "PASSED" else "SKIPPED"}")
    } else {
        Logger.error("Database validation failed:")
        validationResult.errors.forEach { Logger.error("  - $it") }
        kotlin.system.exitProcess(1)
    }
}

fun createBackup(config: DatabaseMigrationTool) {
    Logger.info("Creating database backup...")

    val backupContext = BackupContext(
        databaseType = config.databaseType,
        host = config.host,
        port = config.port ?: getDefaultPort(config.databaseType),
        database = config.database!!,
        username = config.username,
        strategy = config.backupStrategy,
        location = config.backupLocation!!,
        compressionLevel = config.compressionLevel,
        retentionPeriod = config.retentionPeriod
    )

    if (config.dryRun) {
        Logger.info("DRY RUN: Would create backup with strategy ${config.backupStrategy}")
        simulateBackup(backupContext)
    } else {
        val backupResult = performBackup(backupContext)

        if (backupResult.success) {
            Logger.info("Backup created successfully!")
            Logger.info("Backup file: ${backupResult.backupFile}")
            Logger.info("Backup size: ${backupResult.backupSize}")
        } else {
            Logger.error("Backup failed: ${backupResult.error}")
            kotlin.system.exitProcess(1)
        }
    }
}

fun executeRollback(config: DatabaseMigrationTool) {
    Logger.info("Starting rollback process...")

    val rollbackContext = RollbackContext(
        databaseType = config.databaseType,
        host = config.host,
        port = config.port ?: getDefaultPort(config.databaseType),
        database = config.database!!,
        username = config.username,
        target = config.rollbackTarget!!,
        environment = config.environment!!,
        createRestorePoint = config.createRestorePoint,
        skipValidation = config.skipRollbackValidation
    )

    // Create restore point before rollback
    if (config.createRestorePoint) {
        Logger.info("Creating restore point before rollback...")
        val restorePointResult = createRestorePoint(rollbackContext)
        if (!restorePointResult.success) {
            Logger.error("Failed to create restore point: ${restorePointResult.error}")
            if (config.environment == Environment.PRODUCTION) {
                kotlin.system.exitProcess(1)
            }
        }
    }

    // Validate rollback target
    if (!config.skipRollbackValidation) {
        Logger.info("Validating rollback target...")
        val validationResult = validateRollbackTarget(rollbackContext)
        if (!validationResult.isValid) {
            Logger.error("Rollback validation failed: ${validationResult.error}")
            kotlin.system.exitProcess(1)
        }
    }

    // Execute rollback
    if (config.dryRun) {
        Logger.info("DRY RUN: Would rollback to ${config.rollbackTarget}")
        simulateRollback(rollbackContext)
    } else {
        Logger.info("Executing rollback...")
        val rollbackResult = performRollback(rollbackContext)

        if (rollbackResult.success) {
            Logger.info("Rollback completed successfully!")
            Logger.info("Rolled back to version: ${rollbackResult.targetVersion}")
        } else {
            Logger.error("Rollback failed: ${rollbackResult.error}")
            kotlin.system.exitProcess(1)
        }
    }
}


fun showQuickStart() {
    println("""
Quick Start Examples:
  Run pending migrations:
    db-migrate migrate --database-type postgresql --host localhost --database myapp --username admin

  Check migration status:
    db-migrate status --show-pending

  Validate database:
    db-migrate validate --validate-schema --validate-data

  Create backup:
    db-migrate backup --backup-strategy full --backup-location /backups

  Rollback to version:
    db-migrate rollback --rollback-target v1.2.3 --rollback-confirmation ROLLBACK_DATABASE

For detailed documentation, visit: https://docs.example.com/db-migrate
    """.trimIndent())
}

// Data classes and implementations
data class MigrationContext(
    val databaseType: DatabaseType,
    val host: String,
    val port: Int,
    val database: String,
    val username: String?,
    val direction: MigrationDirection,
    val environment: Environment,
    val dryRun: Boolean,
    val transactionMode: String,
    val lockTimeout: Int
)

data class Migration(
    val version: String,
    val description: String,
    val filename: String,
    val sql: String,
    val appliedAt: LocalDateTime?
)

data class ValidationResult(val isValid: Boolean, val errors: List<String> = emptyList())
data class BackupResult(val success: Boolean, val error: String? = null, val backupFile: String? = null, val backupSize: String? = null)
data class MigrationResult(
    val success: Boolean,
    val error: String? = null,
    val appliedCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val lastSuccessfulMigration: String? = null
)

data class StatusContext(
    val databaseType: DatabaseType,
    val host: String,
    val port: Int,
    val database: String,
    val username: String?,
    val outputFormat: String
)

data class StatusResult(
    val appliedMigrations: List<Migration>,
    val pendingMigrations: List<Migration>,
    val currentVersion: String?
)

data class ValidationContext(
    val databaseType: DatabaseType,
    val host: String,
    val port: Int,
    val database: String,
    val username: String?,
    val validateSchema: Boolean,
    val validateData: Boolean,
    val validateConstraints: Boolean,
    val validationRules: String?,
    val schemaSnapshot: String?
)

data class BackupContext(
    val databaseType: DatabaseType,
    val host: String,
    val port: Int,
    val database: String,
    val username: String?,
    val strategy: BackupStrategy,
    val location: String,
    val compressionLevel: Int,
    val retentionPeriod: Int
)

data class RollbackContext(
    val databaseType: DatabaseType,
    val host: String,
    val port: Int,
    val database: String,
    val username: String?,
    val target: String,
    val environment: Environment,
    val createRestorePoint: Boolean,
    val skipValidation: Boolean
)

data class RestorePointResult(val success: Boolean, val error: String? = null)
data class RollbackResult(val success: Boolean, val error: String? = null, val targetVersion: String? = null)

enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

object Logger {
    private var level = LogLevel.INFO
    private var logFile: String? = null

    fun configure(newLevel: LogLevel, file: String?) {
        level = newLevel
        logFile = file
    }

    fun info(message: String) { if (level <= LogLevel.INFO) log("INFO", message) }
    fun debug(message: String) { if (level <= LogLevel.DEBUG) log("DEBUG", message) }
    fun warning(message: String) { if (level <= LogLevel.WARN) log("WARN", message) }
    fun error(message: String) { if (level <= LogLevel.ERROR) log("ERROR", message) }

    private fun log(level: String, message: String) {
        val formatted = "[${LocalDateTime.now()}] [$level] $message"
        println(formatted)
        logFile?.let { File(it).appendText("$formatted\n") }
    }
}

// Stub implementations
fun getDefaultPort(dbType: DatabaseType): Int = when (dbType) {
    DatabaseType.POSTGRESQL -> 5432
    DatabaseType.MYSQL -> 3306
    DatabaseType.SQLITE -> 0
    DatabaseType.ORACLE -> 1521
    DatabaseType.SQL_SERVER -> 1433
}

fun validateMigrationPreconditions(context: MigrationContext): ValidationResult = ValidationResult(true)
fun createPreMigrationBackup(config: DatabaseMigrationTool): BackupResult = BackupResult(true, backupFile = "backup-${LocalDateTime.now()}.sql")
fun loadMigration(file: String): Migration = Migration("001", "Initial migration", file, "CREATE TABLE...", null)
fun getMigrationsToVersion(version: String, direction: MigrationDirection): List<Migration> = emptyList()
fun getMigrationsBySteps(steps: Int, direction: MigrationDirection): List<Migration> = emptyList()
fun getPendingMigrations(directory: String): List<Migration> = emptyList()
fun executeMigrations(context: MigrationContext, migrations: List<Migration>): MigrationResult = MigrationResult(true, appliedCount = migrations.size)
fun performAutomaticRollback(context: MigrationContext, lastSuccessful: String?): RollbackResult = RollbackResult(true)
fun getMigrationStatus(context: StatusContext): StatusResult = StatusResult(emptyList(), emptyList(), "v1.0.0")
fun displayStatusTable(result: StatusResult, showPending: Boolean, showApplied: Boolean, showAll: Boolean) { /* Implementation */ }
fun displayStatusJson(result: StatusResult) { /* Implementation */ }
fun displayStatusCsv(result: StatusResult) { /* Implementation */ }
fun displayStatusXml(result: StatusResult) { /* Implementation */ }
fun performDatabaseValidation(context: ValidationContext): ValidationResult = ValidationResult(true)
fun simulateBackup(context: BackupContext) { /* Implementation */ }
fun performBackup(context: BackupContext): BackupResult = BackupResult(true, backupFile = "full-backup.sql", backupSize = "125MB")
fun createRestorePoint(context: RollbackContext): RestorePointResult = RestorePointResult(true)
fun validateRollbackTarget(context: RollbackContext): ValidationResult = ValidationResult(true)
fun simulateRollback(context: RollbackContext) { /* Implementation */ }
fun performRollback(context: RollbackContext): RollbackResult = RollbackResult(true, targetVersion = "v1.2.3")
```

## Usage Examples

### Basic Migration

```bash
# Run pending migrations
db-migrate migrate --database-type postgresql --host localhost --database myapp \
  --username admin --password-auth --environment development

# Migrate to specific version
db-migrate migrate --to-version v2.1.0 --direction up --environment staging \
  --staging-validation
```

### Production Migrations

```bash
# Production migration with all safety features
db-migrate migrate --database-type mysql --host prod-db.example.com --database myapp \
  --username admin --environment production \
  --production-approval $PROD_TOKEN \
  --backup-strategy full --backup-location /backups \
  --ssl-mode verify-full --ssl-cert prod.crt

# Rollback migration with emergency approval
db-migrate rollback --rollback-target v1.9.0 \
  --rollback-confirmation ROLLBACK_DATABASE \
  --environment production --emergency-approval $EMERGENCY_TOKEN
```

### Backup and Validation

```bash
# Create full backup
db-migrate backup --backup-strategy full --backup-location /backups \
  --compression-level 9 --retention-period 90

# Comprehensive database validation
db-migrate validate --validate-schema --validate-data --validate-constraints \
  --data-validation-rules rules.json --schema-snapshot baseline.sql
```

### Status and Monitoring

```bash
# Check migration status
db-migrate status --show-pending --output-format json

# Dry run migration
db-migrate migrate --dry-run --direction up --steps 5
```

## Key Features Demonstrated

1. **Environment-Specific Safety**: Different constraints for dev/staging/production
2. **Comprehensive Authentication**: Password, key-based, and trusted authentication
3. **Advanced Backup Strategies**: Schema-only, full, incremental backups
4. **Production Safety Features**: Approval tokens, confirmation phrases, rollback safety
5. **Multi-Database Support**: PostgreSQL, MySQL, SQLite, Oracle, SQL Server
6. **Sophisticated Validation**: Schema, data, and constraint validation
7. **Fragment Inheritance**: Reusable patterns for connection, environment, backup
8. **Rich Status Reporting**: Multiple output formats and filtering options
9. **Transaction Management**: Flexible transaction handling modes
10. **Comprehensive Logging**: Configurable logging with file output

This example demonstrates how Argos enables building enterprise-grade database tools with complex business logic, safety features, and excellent user experience.
