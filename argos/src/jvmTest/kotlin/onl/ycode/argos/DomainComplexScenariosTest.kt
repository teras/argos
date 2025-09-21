/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class DomainComplexScenariosTest {

    @Test
    fun domainConstraints_withEnvironmentVariables() {
        class EnvConstraintsArgs : Arguments(
            appName = "env-constraints-test",
            useANSITerminal = false
        ) {
            val serverDomain by domain("server")
                .required(::port)
                .requireIfValue(::ssl, ::port) { it != null && it == 443 }
            val clientDomain by domain("client")

            val port by option("--port").int().fromEnv("SERVER_PORT")
            val ssl by option("--ssl")
            val host by option("--host").fromEnv("SERVER_HOST")
            val file by positional()
        }

        // Test valid: explicit port satisfies domain requirement
        val a1 = EnvConstraintsArgs()
        a1.parseWithException(arrayOf("server", "--port", "8080", "file"))
        assertEquals(8080, a1.port)
        assertNull(a1.ssl)

        // Test valid: HTTPS port with SSL in server domain
        val a2 = EnvConstraintsArgs()
        a2.parseWithException(arrayOf("server", "--port", "443", "--ssl", "cert.pem", "file"))
        assertEquals(443, a2.port)
        assertEquals("cert.pem", a2.ssl)

        // Test valid: no port requirement in client domain
        val a3 = EnvConstraintsArgs()
        a3.parseWithException(arrayOf("client", "--host", "localhost", "file"))
        assertNull(a3.port)
        assertEquals("localhost", a3.host)

        // Test failure: no port in server domain (required)
        val a4 = EnvConstraintsArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("server", "--host", "example.com", "file"))
        }

        // Test failure: HTTPS port but SSL missing in server domain
        val a5 = EnvConstraintsArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("server", "--port", "443", "file"))
        }
    }

    @Test
    fun domainConstraints_withDomainAliases() {
        class DomainAliasesArgs : Arguments(
            appName = "domain-aliases-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build").aliases("b", "compile")
                .required(::source)
                .exactlyOne(::target, ::output)
            val deployDomain by domain("deploy").aliases("d")
                .atLeastOne(::staging, ::production)

            val source by option("--source")
            val target by option("--target").oneOf("debug", "release")
            val output by option("--output")
            val staging by option("--staging").bool()
            val production by option("--production").bool()
            val file by positional()
        }

        // Test using main domain name
        val a1 = DomainAliasesArgs()
        a1.parseWithException(arrayOf("build", "--source", "main.kt", "--target", "release", "file"))
        assertEquals("main.kt", a1.source)
        assertEquals("release", a1.target)

        // Test using first alias
        val a2 = DomainAliasesArgs()
        a2.parseWithException(arrayOf("b", "--source", "app.kt", "--output", "dist/", "file"))
        assertEquals("app.kt", a2.source)
        assertEquals("dist/", a2.output)

        // Test using second alias
        val a3 = DomainAliasesArgs()
        a3.parseWithException(arrayOf("compile", "--source", "lib.kt", "--target", "debug", "file"))
        assertEquals("lib.kt", a3.source)
        assertEquals("debug", a3.target)

        // Test deploy domain with alias
        val a4 = DomainAliasesArgs()
        a4.parseWithException(arrayOf("d", "--staging", "--production", "file"))
        assertEquals(true, a4.staging)
        assertEquals(true, a4.production)

        // Test failure with alias: missing source in build domain
        val a5 = DomainAliasesArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("b", "--target", "release", "file"))
        }

        // Test failure with alias: neither staging nor production in deploy domain
        val a6 = DomainAliasesArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("d", "file"))
        }
    }

    @Test
    fun domainConstraints_withDomainLabelsAndDescriptions() {
        class DomainMetadataArgs : Arguments(
            appName = "domain-metadata-test",
            useANSITerminal = false
        ) {
            val devDomain by domain("dev")
                .label("Development")
                .help("Development environment with hot reloading")
                .required(::source)
                .atMostOne(::watch, ::polling)
            val prodDomain by domain("prod")
                .label("Production")
                .help("Production environment with optimizations")
                .required(::replicas)
                .conflicts(::debug, ::replicas)

            val source by option("--source")
            val watch by option("--watch").bool()
            val polling by option("--polling").bool()
            val replicas by option("--replicas").int()
            val debug by option("--debug").bool()
            val file by positional()
        }

        // Test dev domain constraints work correctly
        val a1 = DomainMetadataArgs()
        a1.parseWithException(arrayOf("dev", "--source", "main.kt", "--watch", "file"))
        assertEquals("main.kt", a1.source)
        assertEquals(true, a1.watch)
        assertNull(a1.polling)

        // Test prod domain constraints work correctly
        val a2 = DomainMetadataArgs()
        a2.parseWithException(arrayOf("prod", "--replicas", "5", "file"))
        assertEquals(5, a2.replicas)
        assertNull(a2.debug)

        // Test failure in dev domain
        val a3 = DomainMetadataArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("dev", "--watch", "file")) // missing required source
        }

        // Test failure in prod domain
        val a4 = DomainMetadataArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("prod", "file")) // missing required replicas
        }

        val a5 = DomainMetadataArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("prod", "--replicas", "3", "--debug", "file")) // conflicts
        }
    }

    @Test
    fun domainConstraints_multipleDomainsWithSharedOptions() {
        class SharedOptionsArgs : Arguments(
            appName = "shared-options-test",
            useANSITerminal = false
        ) {
            val webDomain by domain("web")
                .required(::port)
                .requireIfValue(::ssl, ::port) { it != null && it == 443 }
                .conflicts(::development, ::ssl)
            val apiDomain by domain("api")
                .required(::port)
                .atLeast(::endpoints, 1)
                .exactlyOne(::auth, ::noAuth)
            val workerDomain by domain("worker")
                .atMostOne(::threads, ::async)
                .requireIfAllPresent(::monitoring, ::threads, ::queue)

            val port by option("--port").int()
            val ssl by option("--ssl")
            val development by option("--development").bool()
            val endpoints by option("--endpoint").list()
            val auth by option("--auth")
            val noAuth by option("--no-auth").bool()
            val threads by option("--threads").int()
            val async by option("--async").bool()
            val monitoring by option("--monitoring")
            val queue by option("--queue")
            val file by positional()
        }

        // Test web domain with development mode
        val a1 = SharedOptionsArgs()
        a1.parseWithException(arrayOf("web", "--port", "8080", "--development", "file"))
        assertEquals(8080, a1.port)
        assertNull(a1.ssl)
        assertEquals(true, a1.development)

        // Test web domain with SSL
        val a2 = SharedOptionsArgs()
        a2.parseWithException(arrayOf("web", "--port", "443", "--ssl", "cert.pem", "file"))
        assertEquals(443, a2.port)
        assertEquals("cert.pem", a2.ssl)
        assertNull(a2.development)

        // Test api domain with auth
        val a3 = SharedOptionsArgs()
        a3.parseWithException(arrayOf("api", "--port", "9000", "--endpoint", "/users", "--endpoint", "/orders", "--auth", "jwt", "file"))
        assertEquals(9000, a3.port)
        assertEquals(listOf("/users", "/orders"), a3.endpoints)
        assertEquals("jwt", a3.auth)
        assertNull(a3.noAuth)

        // Test api domain with no auth
        val a4 = SharedOptionsArgs()
        a4.parseWithException(arrayOf("api", "--port", "9001", "--endpoint", "/health", "--no-auth", "file"))
        assertEquals(9001, a4.port)
        assertEquals(listOf("/health"), a4.endpoints)
        assertNull(a4.auth)
        assertEquals(true, a4.noAuth)

        // Test worker domain with threads and monitoring
        val a5 = SharedOptionsArgs()
        a5.parseWithException(arrayOf("worker", "--threads", "8", "--queue", "redis", "--monitoring", "prometheus", "file"))
        assertEquals(8, a5.threads)
        assertNull(a5.async)
        assertEquals("redis", a5.queue)
        assertEquals("prometheus", a5.monitoring)

        // Test worker domain with async
        val a6 = SharedOptionsArgs()
        a6.parseWithException(arrayOf("worker", "--async", "file"))
        assertNull(a6.threads)
        assertEquals(true, a6.async)

        // Test failures across domains
        val a7 = SharedOptionsArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("web", "--port", "443", "--ssl", "cert.pem", "--development", "file")) // conflicts
        }

        val a8 = SharedOptionsArgs()
        assertFailsWith<ParseError> {
            a8.parseWithException(arrayOf("api", "--port", "9000", "--auth", "jwt", "--no-auth", "file")) // exactlyOne violation
        }

        val a9 = SharedOptionsArgs()
        assertFailsWith<ParseError> {
            a9.parseWithException(arrayOf("worker", "--threads", "4", "--async", "file")) // atMostOne violation
        }

        val a10 = SharedOptionsArgs()
        assertFailsWith<ParseError> {
            a10.parseWithException(arrayOf("worker", "--threads", "4", "--queue", "kafka", "file")) // missing monitoring for requireIfAllPresent
        }
    }

    @Test
    fun domainConstraints_nestedComplexScenarios() {
        class NestedComplexArgs : Arguments(
            appName = "nested-complex-test",
            useANSITerminal = false
        ) {
            val orchestratorDomain by domain("orchestrator")
                .exactlyOne(::dockerMode, ::kubernetesMode)
                .requireIfValue(::namespace, ::kubernetesMode) { it == true }
                .requireIfValue(::registry, ::dockerMode) { it == true }
                .atMostOne(::services, ::workloads)
                .requireIfAnyPresent(::monitoring, ::services, ::workloads)
                .conflicts(::debug, ::production)

            val dockerMode by option("--docker").bool()
            val kubernetesMode by option("--kubernetes").bool()
            val namespace by option("--namespace")
            val registry by option("--registry")
            val services by option("--service").list()
            val workloads by option("--workload").set()
            val monitoring by option("--monitoring")
            val debug by option("--debug").bool()
            val production by option("--production").bool()
            val file by positional()
        }

        // Test valid: Docker mode with services and monitoring
        val a1 = NestedComplexArgs()
        a1.parseWithException(arrayOf("orchestrator", "--docker", "--registry", "hub.docker.com", "--service", "web", "--service", "api", "--monitoring", "prometheus", "file"))
        assertEquals(true, a1.dockerMode)
        assertNull(a1.kubernetesMode)
        assertEquals("hub.docker.com", a1.registry)
        assertEquals(listOf("web", "api"), a1.services)
        assertEquals(emptySet<String>(), a1.workloads)
        assertEquals("prometheus", a1.monitoring)

        // Test valid: Kubernetes mode with workloads and monitoring
        val a2 = NestedComplexArgs()
        a2.parseWithException(arrayOf("orchestrator", "--kubernetes", "--namespace", "prod", "--workload", "deployment", "--workload", "service", "--monitoring", "grafana", "file"))
        assertNull(a2.dockerMode)
        assertEquals(true, a2.kubernetesMode)
        assertEquals("prod", a2.namespace)
        assertEquals(emptyList<String>(), a2.services)
        assertEquals(setOf("deployment", "service"), a2.workloads)
        assertEquals("grafana", a2.monitoring)

        // Test valid: Docker mode with debug
        val a3 = NestedComplexArgs()
        a3.parseWithException(arrayOf("orchestrator", "--docker", "--registry", "localhost:5000", "--service", "test", "--monitoring", "local", "--debug", "file"))
        assertEquals(true, a3.dockerMode)
        assertEquals("localhost:5000", a3.registry)
        assertEquals(true, a3.debug)
        assertNull(a3.production)

        // Test valid: Kubernetes mode with production
        val a4 = NestedComplexArgs()
        a4.parseWithException(arrayOf("orchestrator", "--kubernetes", "--namespace", "production", "--workload", "statefulset", "--monitoring", "datadog", "--production", "file"))
        assertEquals(true, a4.kubernetesMode)
        assertEquals("production", a4.namespace)
        assertEquals(true, a4.production)
        assertNull(a4.debug)

        // Test failure: neither docker nor kubernetes mode
        val a5 = NestedComplexArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("orchestrator", "--service", "web", "--monitoring", "prometheus", "file"))
        }

        // Test failure: both docker and kubernetes mode
        val a6 = NestedComplexArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("orchestrator", "--docker", "--kubernetes", "--namespace", "test", "file"))
        }

        // Test failure: kubernetes mode but namespace missing
        val a7 = NestedComplexArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("orchestrator", "--kubernetes", "--workload", "pod", "--monitoring", "prometheus", "file"))
        }

        // Test failure: docker mode but registry missing
        val a8 = NestedComplexArgs()
        assertFailsWith<ParseError> {
            a8.parseWithException(arrayOf("orchestrator", "--docker", "--service", "web", "--monitoring", "prometheus", "file"))
        }

        // Test failure: both services and workloads
        val a9 = NestedComplexArgs()
        assertFailsWith<ParseError> {
            a9.parseWithException(arrayOf("orchestrator", "--docker", "--registry", "hub.docker.com", "--service", "web", "--workload", "deployment", "--monitoring", "prometheus", "file"))
        }

        // Test failure: services provided but monitoring missing
        val a10 = NestedComplexArgs()
        assertFailsWith<ParseError> {
            a10.parseWithException(arrayOf("orchestrator", "--docker", "--registry", "hub.docker.com", "--service", "web", "file"))
        }

        // Test failure: both debug and production
        val a11 = NestedComplexArgs()
        assertFailsWith<ParseError> {
            a11.parseWithException(arrayOf("orchestrator", "--docker", "--registry", "hub.docker.com", "--service", "web", "--monitoring", "prometheus", "--debug", "--production", "file"))
        }
    }

    @Test
    fun domainConstraints_errorMessageAccuracy() {
        class ErrorMessageArgs : Arguments(
            appName = "error-message-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build")
                .required(::source)
                .exactlyOne(::target, ::config)
                .requireIfValue(::optimization, ::target) { it == "release" }

            val source by option("--source")
            val target by option("--target").oneOf("debug", "release")
            val config by option("--config")
            val optimization by option("--optimization")
            val file by positional()
        }

        // Test specific error messages for domain constraints
        val a1 = ErrorMessageArgs()
        val error1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("build", "--target", "debug", "file"))
        }
        assertTrue(error1.message!!.contains("source") && error1.message!!.contains("required"))

        val a2 = ErrorMessageArgs()
        val error2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("build", "--source", "main.kt", "file"))
        }
        assertTrue(error2.message!!.contains("Exactly one"))

        val a3 = ErrorMessageArgs()
        val error3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("build", "--source", "main.kt", "--target", "debug", "--config", "build.conf", "file"))
        }
        assertTrue(error3.message!!.contains("Exactly one"))

        val a4 = ErrorMessageArgs()
        val error4 = assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--source", "main.kt", "--target", "release", "file"))
        }
        assertTrue(error4.message!!.contains("optimization"))
    }
}
