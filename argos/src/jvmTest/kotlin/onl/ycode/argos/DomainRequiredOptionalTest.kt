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

class DomainRequiredOptionalTest {

    @Test
    fun parameterOptionalInOneDomainRequiredInAnother() {
        class RequiredOptionalArgs : Arguments(
            appName = "required-optional-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build")
                .required(::config)
                .required(::output)
            val testDomain by domain("test")
                .required(::config)
                // output is optional in test domain
            val runDomain by domain("run")
                // both config and output are optional in run domain

            val config by option("--config")
            val output by option("--output")
            val verbose by option("--verbose").bool()
            val file by positional()
        }

        // Test build domain: both config and output are required
        val a1 = RequiredOptionalArgs()
        a1.parseWithException(arrayOf("build", "--config", "build.conf", "--output", "dist/", "main.kt"))
        assertEquals("build.conf", a1.config)
        assertEquals("dist/", a1.output)

        // Test build domain failure: config missing (required)
        val a2 = RequiredOptionalArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("build", "--output", "dist/", "main.kt"))
        }

        // Test build domain failure: output missing (required)
        val a3 = RequiredOptionalArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("build", "--config", "build.conf", "main.kt"))
        }

        // Test build domain failure: both missing (both required)
        val a4 = RequiredOptionalArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--verbose", "main.kt"))
        }

        // Test test domain: config required, output optional
        val a5 = RequiredOptionalArgs()
        a5.parseWithException(arrayOf("test", "--config", "test.conf", "test.kt"))
        assertEquals("test.conf", a5.config)
        assertNull(a5.output)

        // Test test domain with output: config required, output provided but optional
        val a6 = RequiredOptionalArgs()
        a6.parseWithException(arrayOf("test", "--config", "test.conf", "--output", "results/", "test.kt"))
        assertEquals("test.conf", a6.config)
        assertEquals("results/", a6.output)

        // Test test domain failure: config missing (required in test domain)
        val a7 = RequiredOptionalArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("test", "--output", "results/", "test.kt"))
        }

        // Test run domain: both config and output are optional
        val a8 = RequiredOptionalArgs()
        a8.parseWithException(arrayOf("run", "--verbose", "app.kt"))
        assertNull(a8.config)
        assertNull(a8.output)
        assertEquals(true, a8.verbose)

        // Test run domain with config only
        val a9 = RequiredOptionalArgs()
        a9.parseWithException(arrayOf("run", "--config", "run.conf", "app.kt"))
        assertEquals("run.conf", a9.config)
        assertNull(a9.output)

        // Test run domain with output only
        val a10 = RequiredOptionalArgs()
        a10.parseWithException(arrayOf("run", "--output", "logs/", "app.kt"))
        assertNull(a10.config)
        assertEquals("logs/", a10.output)

        // Test run domain with both
        val a11 = RequiredOptionalArgs()
        a11.parseWithException(arrayOf("run", "--config", "run.conf", "--output", "logs/", "app.kt"))
        assertEquals("run.conf", a11.config)
        assertEquals("logs/", a11.output)
    }

    @Test
    fun parameterRequiredGloballyButOptionalInSpecificDomain() {
        class GlobalRequiredArgs : Arguments(
            appName = "global-required-test",
            useANSITerminal = false
        ) {
            val helpDomain by domain("help")
                // In help domain, normally required parameters become optional
                // since help doesn't need them
            val processDomain by domain("process")
                // In process domain, the global requirement still applies

            // This parameter is required globally
            val input by option("--input").required()
            val format by option("--format").oneOf("json", "xml", "yaml")
            val file by positional()
        }

        // Test process domain: input is required (global requirement applies)
        val a1 = GlobalRequiredArgs()
        a1.parseWithException(arrayOf("process", "--input", "data.txt", "output.txt"))
        assertEquals("data.txt", a1.input)

        // Test process domain failure: input missing (globally required)
        val a2 = GlobalRequiredArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("process", "--format", "json", "output.txt"))
        }

        // Test help domain: input still required globally even in help
        // (global constraints override domain behavior)
        val a3 = GlobalRequiredArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("help", "topic"))
        }

        // Test help domain with required input
        val a4 = GlobalRequiredArgs()
        a4.parseWithException(arrayOf("help", "--input", "required.txt", "topic"))
        assertEquals("required.txt", a4.input)
    }

    @Test
    fun multipleParametersWithDifferentDomainRequirements() {
        class MixedRequirementsArgs : Arguments(
            appName = "mixed-requirements-test",
            useANSITerminal = false
        ) {
            val compileDomain by domain("compile")
                .required(::source)
                .required(::target)
                // output is optional
            val packageDomain by domain("package")
                .required(::source)
                .required(::output)
                // target is optional
            val deployDomain by domain("deploy")
                .required(::target)
                .required(::output)
                // source is optional
            val cleanDomain by domain("clean")
                // all parameters are optional

            val source by option("--source")
            val target by option("--target")
            val output by option("--output")
            val verbose by option("--verbose").bool()
            val file by positional()
        }

        // Test compile domain: source and target required, output optional
        val a1 = MixedRequirementsArgs()
        a1.parseWithException(arrayOf("compile", "--source", "src/", "--target", "jvm", "build.kt"))
        assertEquals("src/", a1.source)
        assertEquals("jvm", a1.target)
        assertNull(a1.output)

        // Test compile domain with optional output
        val a2 = MixedRequirementsArgs()
        a2.parseWithException(arrayOf("compile", "--source", "src/", "--target", "jvm", "--output", "dist/", "build.kt"))
        assertEquals("src/", a2.source)
        assertEquals("jvm", a2.target)
        assertEquals("dist/", a2.output)

        // Test compile domain failure: source missing
        val a3 = MixedRequirementsArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("compile", "--target", "jvm", "build.kt"))
        }

        // Test compile domain failure: target missing
        val a4 = MixedRequirementsArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("compile", "--source", "src/", "build.kt"))
        }

        // Test package domain: source and output required, target optional
        val a5 = MixedRequirementsArgs()
        a5.parseWithException(arrayOf("package", "--source", "dist/", "--output", "packages/app.jar", "pack.kt"))
        assertEquals("dist/", a5.source)
        assertEquals("packages/app.jar", a5.output)
        assertNull(a5.target)

        // Test package domain failure: output missing
        val a6 = MixedRequirementsArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("package", "--source", "dist/", "pack.kt"))
        }

        // Test deploy domain: target and output required, source optional
        val a7 = MixedRequirementsArgs()
        a7.parseWithException(arrayOf("deploy", "--target", "production", "--output", "deploy.log", "deploy.kt"))
        assertEquals("production", a7.target)
        assertEquals("deploy.log", a7.output)
        assertNull(a7.source)

        // Test deploy domain failure: target missing
        val a8 = MixedRequirementsArgs()
        assertFailsWith<ParseError> {
            a8.parseWithException(arrayOf("deploy", "--output", "deploy.log", "deploy.kt"))
        }

        // Test clean domain: all optional
        val a9 = MixedRequirementsArgs()
        a9.parseWithException(arrayOf("clean", "--verbose", "clean.kt"))
        assertNull(a9.source)
        assertNull(a9.target)
        assertNull(a9.output)
        assertEquals(true, a9.verbose)

        // Test clean domain with some parameters
        val a10 = MixedRequirementsArgs()
        a10.parseWithException(arrayOf("clean", "--source", "temp/", "--verbose", "clean.kt"))
        assertEquals("temp/", a10.source)
        assertNull(a10.target)
        assertNull(a10.output)
        assertEquals(true, a10.verbose)
    }

    @Test
    fun domainRequiredWithListOptions() {
        class ListDomainArgs : Arguments(
            appName = "list-domain-test",
            useANSITerminal = false
        ) {
            val serverDomain by domain("server")
                .required(::inputs)
                .required(::port)
            val clientDomain by domain("client")
                // inputs and port are optional

            val inputs by option("--input").list()
            val port by option("--port").int()
            val timeout by option("--timeout").int()
            val file by positional()
        }

        // Test server domain: port required, inputs required (as list)
        val a1 = ListDomainArgs()
        a1.parseWithException(arrayOf("server", "--input", "file1.txt", "--input", "file2.txt", "--port", "8080", "server.kt"))
        assertEquals(listOf("file1.txt", "file2.txt"), a1.inputs)
        assertEquals(8080, a1.port)

        // Test server domain failure: inputs missing (required)
        val a2 = ListDomainArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("server", "--port", "8080", "server.kt"))
        }

        // Test server domain failure: port missing (required)
        val a3 = ListDomainArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("server", "--input", "file1.txt", "server.kt"))
        }

        // Test client domain: both inputs and port are optional
        val a4 = ListDomainArgs()
        a4.parseWithException(arrayOf("client", "--timeout", "30", "client.kt"))
        assertEquals(emptyList(), a4.inputs)
        assertNull(a4.port)
        assertEquals(30, a4.timeout)

        // Test client domain with inputs provided (allowed in client domain)
        val a5 = ListDomainArgs()
        a5.parseWithException(arrayOf("client", "--input", "optional.txt", "client.kt"))
        assertEquals(listOf("optional.txt"), a5.inputs)
        assertNull(a5.port)
    }
}
