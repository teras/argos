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

class DomainConditionalConstraintsTest {

    @Test
    fun domainRequireIfAnyPresent_basic() {
        class RequireIfAnyArgs : Arguments(
            appName = "require-if-any-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build").requireIfAnyPresent(::output, ::source, ::template)
            val testDomain by domain("test")

            val source by option("--source")
            val template by option("--template")
            val output by option("--output")
            val debug by option("--debug").bool()
            val file by positional()
        }

        // Test valid: source triggers output requirement in build domain
        val a1 = RequireIfAnyArgs()
        a1.parseWithException(arrayOf("build", "--source", "main.kt", "--output", "build/", "file"))
        assertEquals("main.kt", a1.source)
        assertEquals("build/", a1.output)

        // Test valid: template triggers output requirement in build domain
        val a2 = RequireIfAnyArgs()
        a2.parseWithException(arrayOf("build", "--template", "basic.kt", "--output", "gen/", "file"))
        assertEquals("basic.kt", a2.template)
        assertEquals("gen/", a2.output)

        // Test valid: both source and template trigger output requirement
        val a3 = RequireIfAnyArgs()
        a3.parseWithException(arrayOf("build", "--source", "main.kt", "--template", "basic.kt", "--output", "mixed/", "file"))
        assertEquals("main.kt", a3.source)
        assertEquals("basic.kt", a3.template)
        assertEquals("mixed/", a3.output)

        // Test valid: no triggers in build domain, no output needed
        val a4 = RequireIfAnyArgs()
        a4.parseWithException(arrayOf("build", "--debug", "file"))
        assertNull(a4.source)
        assertNull(a4.template)
        assertNull(a4.output)
        assertEquals(true, a4.debug)

        // Test valid: source provided in test domain (constraint doesn't apply)
        val a5 = RequireIfAnyArgs()
        a5.parseWithException(arrayOf("test", "--source", "test.kt", "file"))
        assertEquals("test.kt", a5.source)
        assertNull(a5.output)

        // Test failure: source provided but output missing in build domain
        val a6 = RequireIfAnyArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("build", "--source", "main.kt", "file"))
        }

        // Test failure: template provided but output missing in build domain
        val a7 = RequireIfAnyArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("build", "--template", "basic.kt", "file"))
        }
    }

    @Test
    fun domainRequireIfAllPresent_basic() {
        class RequireIfAllArgs : Arguments(
            appName = "require-if-all-test",
            useANSITerminal = false
        ) {
            val compileDomain by domain("compile").requireIfAllPresent(::target, ::optimize, ::profile)
            val runDomain by domain("run")

            val optimize by option("--optimize").bool()
            val profile by option("--profile").bool()
            val target by option("--target")
            val verbose by option("--verbose").bool()
            val file by positional()
        }

        // Test valid: all triggers present, target provided in compile domain
        val a1 = RequireIfAllArgs()
        a1.parseWithException(arrayOf("compile", "--optimize", "--profile", "--target", "release", "file"))
        assertEquals(true, a1.optimize)
        assertEquals(true, a1.profile)
        assertEquals("release", a1.target)

        // Test valid: only optimize present, target not required in compile domain
        val a2 = RequireIfAllArgs()
        a2.parseWithException(arrayOf("compile", "--optimize", "file"))
        assertEquals(true, a2.optimize)
        assertNull(a2.profile)
        assertNull(a2.target)

        // Test valid: only profile present, target not required in compile domain
        val a3 = RequireIfAllArgs()
        a3.parseWithException(arrayOf("compile", "--profile", "file"))
        assertNull(a3.optimize)
        assertEquals(true, a3.profile)
        assertNull(a3.target)

        // Test valid: no triggers in compile domain
        val a4 = RequireIfAllArgs()
        a4.parseWithException(arrayOf("compile", "--verbose", "file"))
        assertNull(a4.optimize)
        assertNull(a4.profile)
        assertNull(a4.target)
        assertEquals(true, a4.verbose)

        // Test valid: all triggers in run domain (constraint doesn't apply)
        val a5 = RequireIfAllArgs()
        a5.parseWithException(arrayOf("run", "--optimize", "--profile", "file"))
        assertEquals(true, a5.optimize)
        assertEquals(true, a5.profile)
        assertNull(a5.target)

        // Test failure: both triggers present but target missing in compile domain
        val a6 = RequireIfAllArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("compile", "--optimize", "--profile", "file"))
        }
    }

    @Test
    fun domainRequireIfValue_stringPredicate() {
        class RequireIfValueStringArgs : Arguments(
            appName = "require-if-value-string-test",
            useANSITerminal = false
        ) {
            val serverDomain by domain("server").requireIfValue(::config, ::mode) { it == "production" }
            val clientDomain by domain("client")

            val mode by option("--mode").oneOf("dev", "staging", "production")
            val config by option("--config")
            val port by option("--port").int()
            val file by positional()
        }

        // Test valid: mode is production, config provided in server domain
        val a1 = RequireIfValueStringArgs()
        a1.parseWithException(arrayOf("server", "--mode", "production", "--config", "prod.conf", "file"))
        assertEquals("production", a1.mode)
        assertEquals("prod.conf", a1.config)

        // Test valid: mode is dev, config not required in server domain
        val a2 = RequireIfValueStringArgs()
        a2.parseWithException(arrayOf("server", "--mode", "dev", "file"))
        assertEquals("dev", a2.mode)
        assertNull(a2.config)

        // Test valid: mode is staging, config not required in server domain
        val a3 = RequireIfValueStringArgs()
        a3.parseWithException(arrayOf("server", "--mode", "staging", "file"))
        assertEquals("staging", a3.mode)
        assertNull(a3.config)

        // Test valid: no mode provided, config not required in server domain
        val a4 = RequireIfValueStringArgs()
        a4.parseWithException(arrayOf("server", "--port", "8080", "file"))
        assertNull(a4.mode)
        assertNull(a4.config)
        assertEquals(8080, a4.port)

        // Test valid: mode is production in client domain (constraint doesn't apply)
        val a5 = RequireIfValueStringArgs()
        a5.parseWithException(arrayOf("client", "--mode", "production", "file"))
        assertEquals("production", a5.mode)
        assertNull(a5.config)

        // Test failure: mode is production but config missing in server domain
        val a6 = RequireIfValueStringArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("server", "--mode", "production", "file"))
        }
    }

    @Test
    fun domainRequireIfValue_intPredicate() {
        class RequireIfValueIntArgs : Arguments(
            appName = "require-if-value-int-test",
            useANSITerminal = false
        ) {
            val processDomain by domain("process").requireIfValue(::backup, ::threads) { it != null && it > 4 }
            val analyzeDomain by domain("analyze")

            val threads by option("--threads").int()
            val backup by option("--backup")
            val memory by option("--memory").int()
            val file by positional()
        }

        // Test valid: threads > 4, backup provided in process domain
        val a1 = RequireIfValueIntArgs()
        a1.parseWithException(arrayOf("process", "--threads", "8", "--backup", "/tmp/backup", "file"))
        assertEquals(8, a1.threads)
        assertEquals("/tmp/backup", a1.backup)

        // Test valid: threads <= 4, backup not required in process domain
        val a2 = RequireIfValueIntArgs()
        a2.parseWithException(arrayOf("process", "--threads", "2", "file"))
        assertEquals(2, a2.threads)
        assertNull(a2.backup)

        // Test valid: threads = 4 (boundary), backup not required in process domain
        val a3 = RequireIfValueIntArgs()
        a3.parseWithException(arrayOf("process", "--threads", "4", "file"))
        assertEquals(4, a3.threads)
        assertNull(a3.backup)

        // Test valid: no threads provided, backup not required in process domain
        val a4 = RequireIfValueIntArgs()
        a4.parseWithException(arrayOf("process", "--memory", "2048", "file"))
        assertNull(a4.threads)
        assertNull(a4.backup)
        assertEquals(2048, a4.memory)

        // Test valid: threads > 4 in analyze domain (constraint doesn't apply)
        val a5 = RequireIfValueIntArgs()
        a5.parseWithException(arrayOf("analyze", "--threads", "16", "file"))
        assertEquals(16, a5.threads)
        assertNull(a5.backup)

        // Test failure: threads > 4 but backup missing in process domain
        val a6 = RequireIfValueIntArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("process", "--threads", "12", "file"))
        }
    }

    @Test
    fun domainRequireIfValue_booleanPredicate() {
        class RequireIfValueBoolArgs : Arguments(
            appName = "require-if-value-bool-test",
            useANSITerminal = false
        ) {
            val securityDomain by domain("security").requireIfValue(::keyfile, ::encrypt) { it == true }
            val basicDomain by domain("basic")

            val encrypt by option("--encrypt").bool()
            val keyfile by option("--keyfile")
            val algorithm by option("--algorithm").oneOf("aes", "rsa")
            val file by positional()
        }

        // Test valid: encrypt is true, keyfile provided in security domain
        val a1 = RequireIfValueBoolArgs()
        a1.parseWithException(arrayOf("security", "--encrypt", "--keyfile", "secret.key", "file"))
        assertEquals(true, a1.encrypt)
        assertEquals("secret.key", a1.keyfile)

        // Test valid: encrypt is false, keyfile not required in security domain
        val a2 = RequireIfValueBoolArgs()
        a2.parseWithException(arrayOf("security", "--encrypt", "false", "file"))
        assertEquals(false, a2.encrypt)
        assertNull(a2.keyfile)

        // Test valid: encrypt not provided, keyfile not required in security domain
        val a3 = RequireIfValueBoolArgs()
        a3.parseWithException(arrayOf("security", "--algorithm", "aes", "file"))
        assertNull(a3.encrypt)
        assertNull(a3.keyfile)
        assertEquals("aes", a3.algorithm)

        // Test valid: encrypt is true in basic domain (constraint doesn't apply)
        val a4 = RequireIfValueBoolArgs()
        a4.parseWithException(arrayOf("basic", "--encrypt", "file"))
        assertEquals(true, a4.encrypt)
        assertNull(a4.keyfile)

        // Test failure: encrypt is true but keyfile missing in security domain
        val a5 = RequireIfValueBoolArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("security", "--encrypt", "file"))
        }
    }

    @Test
    fun domainMultipleConditionalConstraints() {
        class MultiConditionalArgs : Arguments(
            appName = "multi-conditional-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build")
                .requireIfAnyPresent(::output, ::source, ::lib)
                .requireIfAllPresent(::manifest, ::optimize, ::target)
                .requireIfValue(::backup, ::mode) { it == "release" }

            val source by option("--source")
            val lib by option("--lib")
            val output by option("--output")
            val optimize by option("--optimize").bool()
            val target by option("--target")
            val manifest by option("--manifest")
            val mode by option("--mode").oneOf("debug", "release")
            val backup by option("--backup")
            val file by positional()
        }

        // Test valid: all constraints satisfied
        val a1 = MultiConditionalArgs()
        a1.parseWithException(arrayOf("build",
            "--source", "main.kt", "--output", "build/",
            "--optimize", "--target", "jvm", "--manifest", "MANIFEST.MF",
            "--mode", "release", "--backup", "/tmp/backup",
            "file"))
        assertEquals("main.kt", a1.source)
        assertEquals("build/", a1.output)
        assertEquals(true, a1.optimize)
        assertEquals("jvm", a1.target)
        assertEquals("MANIFEST.MF", a1.manifest)
        assertEquals("release", a1.mode)
        assertEquals("/tmp/backup", a1.backup)

        // Test valid: only first constraint triggered
        val a2 = MultiConditionalArgs()
        a2.parseWithException(arrayOf("build", "--lib", "mylib.jar", "--output", "dist/", "file"))
        assertEquals("mylib.jar", a2.lib)
        assertEquals("dist/", a2.output)
        assertNull(a2.optimize)
        assertNull(a2.target)
        assertNull(a2.manifest)

        // Test valid: mode is debug, backup not required
        val a3 = MultiConditionalArgs()
        a3.parseWithException(arrayOf("build", "--source", "test.kt", "--output", "out/", "--mode", "debug", "file"))
        assertEquals("test.kt", a3.source)
        assertEquals("out/", a3.output)
        assertEquals("debug", a3.mode)
        assertNull(a3.backup)

        // Test failure: source provided but output missing (requireIfAnyPresent)
        val a4 = MultiConditionalArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--source", "main.kt", "file"))
        }

        // Test failure: optimize and target provided but manifest missing (requireIfAllPresent)
        val a5 = MultiConditionalArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("build", "--source", "main.kt", "--output", "build/", "--optimize", "--target", "jvm", "file"))
        }

        // Test failure: mode is release but backup missing (requireIfValue)
        val a6 = MultiConditionalArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("build", "--source", "main.kt", "--output", "build/", "--mode", "release", "file"))
        }
    }

    @Test
    fun domainConditionalConstraints_multipleDomains() {
        class MultiDomainConditionalArgs : Arguments(
            appName = "multi-domain-conditional-test",
            useANSITerminal = false
        ) {
            val devDomain by domain("dev").requireIfAnyPresent(::debugConfig, ::verbose, ::trace)
            val prodDomain by domain("prod")
                .requireIfAllPresent(::ssl, ::monitoring, ::logging)
                .requireIfValue(::backup, ::replicas) { it != null && it > 1 }

            val verbose by option("--verbose").bool()
            val trace by option("--trace").bool()
            val debugConfig by option("--debug-config")
            val monitoring by option("--monitoring").bool()
            val logging by option("--logging").bool()
            val ssl by option("--ssl")
            val replicas by option("--replicas").int()
            val backup by option("--backup")
            val file by positional()
        }

        // Test dev domain constraints
        val a1 = MultiDomainConditionalArgs()
        a1.parseWithException(arrayOf("dev", "--verbose", "--debug-config", "debug.conf", "file"))
        assertEquals(true, a1.verbose)
        assertEquals("debug.conf", a1.debugConfig)

        // Test prod domain constraints - all present
        val a2 = MultiDomainConditionalArgs()
        a2.parseWithException(arrayOf("prod", "--monitoring", "--logging", "--ssl", "cert.pem", "--replicas", "3", "--backup", "/backup", "file"))
        assertEquals(true, a2.monitoring)
        assertEquals(true, a2.logging)
        assertEquals("cert.pem", a2.ssl)
        assertEquals(3, a2.replicas)
        assertEquals("/backup", a2.backup)

        // Test prod domain - replicas <= 1, backup not required
        val a3 = MultiDomainConditionalArgs()
        a3.parseWithException(arrayOf("prod", "--replicas", "1", "file"))
        assertEquals(1, a3.replicas)
        assertNull(a3.backup)

        // Test failure in dev domain
        val a4 = MultiDomainConditionalArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("dev", "--verbose", "file")) // missing debugConfig
        }

        // Test failure in prod domain - requireIfAllPresent
        val a5 = MultiDomainConditionalArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("prod", "--monitoring", "--logging", "file")) // missing ssl
        }

        // Test failure in prod domain - requireIfValue
        val a6 = MultiDomainConditionalArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("prod", "--replicas", "5", "file")) // missing backup
        }
    }
}
