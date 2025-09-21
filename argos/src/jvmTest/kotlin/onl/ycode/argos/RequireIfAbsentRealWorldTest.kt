/*
 * SPDX-License-Identifier: Apache-2.0
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

/**
 * Real-world test scenarios for requireIfAnyAbsent and requireIfAllAbsent constraint methods.
 * These tests represent actual use cases where absent constraints make practical sense.
 */
class RequireIfAbsentRealWorldTest {

    // ========== Backup Strategy Patterns ==========

    @Test
    fun backupStrategy_requireBackupWhenPrimaryAbsent() {
        class BackupArgs : Arguments() {
            val primaryDb by option("--primary-db")
            val backupDb by option("--backup-db").requireIfAllAbsent(::primaryDb)
            val readOnlyDb by option("--readonly-db")
        }

        // When primary is provided, backup is optional
        val args1 = BackupArgs()
        args1.parseWithException(arrayOf("--primary-db", "postgresql://primary"))
        assertEquals("postgresql://primary", args1.primaryDb)
        assertNull(args1.backupDb)

        // When primary is missing, backup is required
        assertFailsWith<ParseError> {
            BackupArgs().parseWithException(arrayOf("--readonly-db", "sqlite://readonly.db"))
        }

        // Valid: backup provided when primary absent
        val args2 = BackupArgs()
        args2.parseWithException(arrayOf("--backup-db", "mysql://backup"))
        assertEquals("mysql://backup", args2.backupDb)
        assertNull(args2.primaryDb)
    }

    @Test
    fun redundantSystems_requireFallbackWhenAnySystemAbsent() {
        class RedundantArgs : Arguments() {
            val systemA by option("--system-a")
            val systemB by option("--system-b")
            val fallback by option("--fallback").requireIfAnyAbsent(::systemA, ::systemB)
        }

        // When both systems present, fallback not required
        val args1 = RedundantArgs()
        args1.parseWithException(arrayOf("--system-a", "node1", "--system-b", "node2"))
        assertEquals("node1", args1.systemA)
        assertEquals("node2", args1.systemB)
        assertNull(args1.fallback)

        // When one system absent, fallback required
        assertFailsWith<ParseError> {
            RedundantArgs().parseWithException(arrayOf("--system-a", "node1"))
        }

        // Valid: fallback provided when system absent
        val args2 = RedundantArgs()
        args2.parseWithException(arrayOf("--system-a", "node1", "--fallback", "backup-node"))
        assertEquals("backup-node", args2.fallback)
    }

    // ========== Configuration Patterns ==========

    @Test
    fun configFile_requireIndividualOptionsWhenConfigAbsent() {
        class ConfigArgs : Arguments() {
            val configFile by option("--config")
            val host by option("--host").requireIfAllAbsent(::configFile)
            val port by option("--port").int().requireIfAllAbsent(::configFile)
            val database by option("--database").requireIfAllAbsent(::configFile)
        }

        // When config file provided, individual options not required
        val args1 = ConfigArgs()
        args1.parseWithException(arrayOf("--config", "app.yaml"))
        assertEquals("app.yaml", args1.configFile)
        assertNull(args1.host)
        assertNull(args1.port)
        assertNull(args1.database)

        // When config absent, all individual options required
        assertFailsWith<ParseError> {
            ConfigArgs().parseWithException(arrayOf("--host", "localhost"))
        }

        // Valid: all options provided when config absent
        val args2 = ConfigArgs()
        args2.parseWithException(arrayOf("--host", "localhost", "--port", "5432", "--database", "myapp"))
        assertEquals("localhost", args2.host)
        assertEquals(5432, args2.port)
        assertEquals("myapp", args2.database)
    }

    @Test
    fun authentication_requireCredentialsWhenTokenAbsent() {
        class AuthArgs : Arguments() {
            val token by option("--token")
            val username by option("--username").requireIfAllAbsent(::token)
            val password by option("--password").requireIfAllAbsent(::token)
        }

        // Token authentication - credentials not required
        val args1 = AuthArgs()
        args1.parseWithException(arrayOf("--token", "abc123"))
        assertEquals("abc123", args1.token)
        assertNull(args1.username)
        assertNull(args1.password)

        // No token - credentials required
        assertFailsWith<ParseError> {
            AuthArgs().parseWithException(arrayOf("--username", "admin"))
        }

        // Valid: both credentials when token absent
        val args2 = AuthArgs()
        args2.parseWithException(arrayOf("--username", "admin", "--password", "secret"))
        assertEquals("admin", args2.username)
        assertEquals("secret", args2.password)
    }

    // ========== Development vs Production Patterns ==========

    @Test
    fun environment_requireProductionConfigWhenDevToolsAbsent() {
        class EnvArgs : Arguments() {
            val devMode by option("--dev").bool()
            val hotReload by option("--hot-reload").bool()
            val prodConfig by option("--prod-config").requireIfAllAbsent(::devMode, ::hotReload)
            val sslCert by option("--ssl-cert").requireIfAllAbsent(::devMode, ::hotReload)
        }

        // Development mode - production config not required
        val args1 = EnvArgs()
        args1.parseWithException(arrayOf("--dev", "--hot-reload"))
        assertEquals(true, args1.devMode)
        assertEquals(true, args1.hotReload)
        assertNull(args1.prodConfig)
        assertNull(args1.sslCert)

        // Production mode (no dev tools) - production config required
        assertFailsWith<ParseError> {
            EnvArgs().parseWithException(arrayOf())
        }

        // Valid: production config when no dev tools
        val args2 = EnvArgs()
        args2.parseWithException(arrayOf("--prod-config", "prod.yaml", "--ssl-cert", "cert.pem"))
        assertEquals("prod.yaml", args2.prodConfig)
        assertEquals("cert.pem", args2.sslCert)
    }

    // ========== Build System Patterns ==========

    @Test
    fun buildSystem_requireSourcesWhenPrebuiltAbsent() {
        class BuildArgs : Arguments() {
            val prebuiltBinary by option("--prebuilt")
            val dockerImage by option("--docker-image")
            val sourceDir by option("--source-dir").requireIfAllAbsent(::prebuiltBinary, ::dockerImage)
            val buildTool by option("--build-tool").requireIfAllAbsent(::prebuiltBinary, ::dockerImage)
        }

        // Prebuilt binary - no need to build from source
        val args1 = BuildArgs()
        args1.parseWithException(arrayOf("--prebuilt", "app.jar"))
        assertEquals("app.jar", args1.prebuiltBinary)
        assertNull(args1.sourceDir)
        assertNull(args1.buildTool)

        // Docker image - no need to build from source
        val args2 = BuildArgs()
        args2.parseWithException(arrayOf("--docker-image", "myapp:1.0"))
        assertEquals("myapp:1.0", args2.dockerImage)
        assertNull(args2.sourceDir)
        assertNull(args2.buildTool)

        // No prebuilt options - source build required
        assertFailsWith<ParseError> {
            BuildArgs().parseWithException(arrayOf("--source-dir", "src/"))
        }

        // Valid: all source build options when prebuilt absent
        val args3 = BuildArgs()
        args3.parseWithException(arrayOf("--source-dir", "src/", "--build-tool", "gradle"))
        assertEquals("src/", args3.sourceDir)
        assertEquals("gradle", args3.buildTool)
    }

    // ========== Monitoring and Logging Patterns ==========

    @Test
    fun logging_requireFileOutputWhenRemoteAbsent() {
        class LogArgs : Arguments() {
            val syslogHost by option("--syslog-host")
            val logstashUrl by option("--logstash-url")
            val logFile by option("--log-file").requireIfAllAbsent(::syslogHost, ::logstashUrl)
            val logLevel by option("--log-level").default("info")
        }

        // Remote logging configured - local file not required
        val args1 = LogArgs()
        args1.parseWithException(arrayOf("--syslog-host", "logs.company.com"))
        assertEquals("logs.company.com", args1.syslogHost)
        assertNull(args1.logFile)

        // No remote logging - local file required
        assertFailsWith<ParseError> {
            LogArgs().parseWithException(arrayOf("--log-level", "debug"))
        }

        // Valid: log file when no remote logging
        val args2 = LogArgs()
        args2.parseWithException(arrayOf("--log-file", "/var/log/app.log"))
        assertEquals("/var/log/app.log", args2.logFile)
    }

    // ========== Data Source Patterns ==========

    @Test
    fun dataSource_requireInputWhenCacheAbsent() {
        class DataArgs : Arguments() {
            val cacheFile by option("--cache")
            val seedData by option("--seed-data")
            val inputFile by option("--input").requireIfAllAbsent(::cacheFile, ::seedData)
        }

        // Cache available - input not required
        val args1 = DataArgs()
        args1.parseWithException(arrayOf("--cache", "data.cache"))
        assertEquals("data.cache", args1.cacheFile)
        assertNull(args1.inputFile)

        // Seed data available - input not required
        val args2 = DataArgs()
        args2.parseWithException(arrayOf("--seed-data", "seeds.json"))
        assertEquals("seeds.json", args2.seedData)
        assertNull(args2.inputFile)

        // Both cache and seed data available - input not required
        val args3 = DataArgs()
        args3.parseWithException(arrayOf("--cache", "data.cache", "--seed-data", "seeds.json"))
        assertNull(args3.inputFile)

        // Neither cache nor seed data (both absent) - input required
        assertFailsWith<ParseError> {
            DataArgs().parseWithException(arrayOf())
        }

        // Valid: input provided when both cache and seed absent
        val args4 = DataArgs()
        args4.parseWithException(arrayOf("--input", "raw-data.csv"))
        assertEquals("raw-data.csv", args4.inputFile)
    }

    // ========== Security Patterns ==========

    @Test
    fun security_requireAuthMethodWhenInsecureAbsent() {
        class SecurityArgs : Arguments() {
            val allowInsecure by option("--allow-insecure").bool()
            val clientCert by option("--client-cert")
            val apiKey by option("--api-key")
            val authToken by option("--auth-token").requireIfAllAbsent(::allowInsecure)
        }

        // Insecure mode - no authentication required
        val args1 = SecurityArgs()
        args1.parseWithException(arrayOf("--allow-insecure"))
        assertEquals(true, args1.allowInsecure)
        assertNull(args1.clientCert)
        assertNull(args1.apiKey)
        assertNull(args1.authToken)

        // Secure mode - auth token required
        assertFailsWith<ParseError> {
            SecurityArgs().parseWithException(arrayOf())
        }

        // Valid: auth token for secure mode
        val args2 = SecurityArgs()
        args2.parseWithException(arrayOf("--auth-token", "bearer-123"))
        assertEquals("bearer-123", args2.authToken)

        // Valid: auth token with optional cert and key
        val args3 = SecurityArgs()
        args3.parseWithException(arrayOf("--auth-token", "bearer-456", "--client-cert", "client.pem", "--api-key", "secret123"))
        assertEquals("bearer-456", args3.authToken)
        assertEquals("client.pem", args3.clientCert)
        assertEquals("secret123", args3.apiKey)
    }

    // ========== Networking Patterns ==========

    @Test
    fun networking_requireConnectionDetailsWhenDiscoveryAbsent() {
        class NetworkArgs : Arguments() {
            val serviceDiscovery by option("--service-discovery")
            val consulUrl by option("--consul-url")
            val targetHost by option("--target-host").requireIfAllAbsent(::serviceDiscovery, ::consulUrl)
            val targetPort by option("--target-port").int().requireIfAllAbsent(::serviceDiscovery, ::consulUrl)
        }

        // Service discovery enabled - manual connection details not required
        val args1 = NetworkArgs()
        args1.parseWithException(arrayOf("--service-discovery", "kubernetes"))
        assertEquals("kubernetes", args1.serviceDiscovery)
        assertNull(args1.targetHost)
        assertNull(args1.targetPort)

        // Consul discovery - manual connection details not required
        val args2 = NetworkArgs()
        args2.parseWithException(arrayOf("--consul-url", "http://consul:8500"))
        assertEquals("http://consul:8500", args2.consulUrl)
        assertNull(args2.targetHost)
        assertNull(args2.targetPort)

        // No discovery - manual connection required
        assertFailsWith<ParseError> {
            NetworkArgs().parseWithException(arrayOf("--target-host", "api.example.com"))
        }

        // Valid: complete manual connection when discovery absent
        val args3 = NetworkArgs()
        args3.parseWithException(arrayOf("--target-host", "api.example.com", "--target-port", "443"))
        assertEquals("api.example.com", args3.targetHost)
        assertEquals(443, args3.targetPort)
    }
}