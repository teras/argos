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

class DomainGroupConstraintsTest {

    @Test
    fun domainExactlyOne_basic() {
        class ExactlyOneArgs : Arguments(
            appName = "exactly-one-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build").exactlyOne(::input, ::config)
            val testDomain by domain("test")

            val input by option("--input")
            val config by option("--config")
            val output by option("--output")
            val file by positional()
        }

        // Test valid: input provided in build domain
        val a1 = ExactlyOneArgs()
        a1.parseWithException(arrayOf("build", "--input", "src.txt", "file"))
        assertEquals("src.txt", a1.input)
        assertNull(a1.config)

        // Test valid: config provided in build domain
        val a2 = ExactlyOneArgs()
        a2.parseWithException(arrayOf("build", "--config", "config.json", "file"))
        assertNull(a2.input)
        assertEquals("config.json", a2.config)

        // Test valid: neither provided in test domain (constraint doesn't apply)
        val a3 = ExactlyOneArgs()
        a3.parseWithException(arrayOf("test", "--output", "out.txt", "file"))
        assertNull(a3.input)
        assertNull(a3.config)
        assertEquals("out.txt", a3.output)

        // Test failure: neither provided in build domain
        val a4 = ExactlyOneArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--output", "out.txt", "file"))
        }

        // Test failure: both provided in build domain
        val a5 = ExactlyOneArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("build", "--input", "src.txt", "--config", "config.json", "file"))
        }
    }

    @Test
    fun domainAtMostOne_basic() {
        class AtMostOneArgs : Arguments(
            appName = "at-most-one-test",
            useANSITerminal = false
        ) {
            val deployDomain by domain("deploy").atMostOne(::staging, ::production)
            val devDomain by domain("dev")

            val staging by option("--staging").bool()
            val production by option("--production").bool()
            val debug by option("--debug").bool()
            val file by positional()
        }

        // Test valid: staging in deploy domain
        val a1 = AtMostOneArgs()
        a1.parseWithException(arrayOf("deploy", "--staging", "file"))
        assertEquals(true, a1.staging)
        assertNull(a1.production)

        // Test valid: production in deploy domain
        val a2 = AtMostOneArgs()
        a2.parseWithException(arrayOf("deploy", "--production", "file"))
        assertNull(a2.staging)
        assertEquals(true, a2.production)

        // Test valid: neither in deploy domain
        val a3 = AtMostOneArgs()
        a3.parseWithException(arrayOf("deploy", "--debug", "file"))
        assertNull(a3.staging)
        assertNull(a3.production)
        assertEquals(true, a3.debug)

        // Test valid: both in dev domain (constraint doesn't apply)
        val a4 = AtMostOneArgs()
        a4.parseWithException(arrayOf("dev", "--staging", "--production", "file"))
        assertEquals(true, a4.staging)
        assertEquals(true, a4.production)

        // Test failure: both in deploy domain
        val a5 = AtMostOneArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("deploy", "--staging", "--production", "file"))
        }
    }

    @Test
    fun domainAtLeastOne_basic() {
        class AtLeastOneArgs : Arguments(
            appName = "at-least-one-test",
            useANSITerminal = false
        ) {
            val serverDomain by domain("server").atLeastOne(::port, ::socket)
            val clientDomain by domain("client")

            val port by option("--port").int()
            val socket by option("--socket")
            val host by option("--host")
            val file by positional()
        }

        // Test valid: port provided in server domain
        val a1 = AtLeastOneArgs()
        a1.parseWithException(arrayOf("server", "--port", "8080", "file"))
        assertEquals(8080, a1.port)
        assertNull(a1.socket)

        // Test valid: socket provided in server domain
        val a2 = AtLeastOneArgs()
        a2.parseWithException(arrayOf("server", "--socket", "/tmp/server.sock", "file"))
        assertNull(a2.port)
        assertEquals("/tmp/server.sock", a2.socket)

        // Test valid: both provided in server domain
        val a3 = AtLeastOneArgs()
        a3.parseWithException(arrayOf("server", "--port", "9000", "--socket", "/tmp/alt.sock", "file"))
        assertEquals(9000, a3.port)
        assertEquals("/tmp/alt.sock", a3.socket)

        // Test valid: neither provided in client domain (constraint doesn't apply)
        val a4 = AtLeastOneArgs()
        a4.parseWithException(arrayOf("client", "--host", "localhost", "file"))
        assertNull(a4.port)
        assertNull(a4.socket)
        assertEquals("localhost", a4.host)

        // Test failure: neither provided in server domain
        val a5 = AtLeastOneArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("server", "--host", "localhost", "file"))
        }
    }

    @Test
    fun domainConflicts_basic() {
        class ConflictsArgs : Arguments(
            appName = "conflicts-test",
            useANSITerminal = false
        ) {
            val processDomain by domain("process").conflicts(::verbose, ::quiet)
            val analyzeDomain by domain("analyze")

            val verbose by option("--verbose").bool()
            val quiet by option("--quiet").bool()
            val debug by option("--debug").bool()
            val file by positional()
        }

        // Test valid: verbose in process domain
        val a1 = ConflictsArgs()
        a1.parseWithException(arrayOf("process", "--verbose", "file"))
        assertEquals(true, a1.verbose)
        assertNull(a1.quiet)

        // Test valid: quiet in process domain
        val a2 = ConflictsArgs()
        a2.parseWithException(arrayOf("process", "--quiet", "file"))
        assertNull(a2.verbose)
        assertEquals(true, a2.quiet)

        // Test valid: neither in process domain
        val a3 = ConflictsArgs()
        a3.parseWithException(arrayOf("process", "--debug", "file"))
        assertNull(a3.verbose)
        assertNull(a3.quiet)
        assertEquals(true, a3.debug)

        // Test valid: both in analyze domain (constraint doesn't apply)
        val a4 = ConflictsArgs()
        a4.parseWithException(arrayOf("analyze", "--verbose", "--quiet", "file"))
        assertEquals(true, a4.verbose)
        assertEquals(true, a4.quiet)

        // Test failure: both in process domain
        val a5 = ConflictsArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("process", "--verbose", "--quiet", "file"))
        }
    }

    @Test
    fun domainMultipleGroupConstraints() {
        class MultiGroupArgs : Arguments(
            appName = "multi-group-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build")
                .exactlyOne(::input, ::template)
                .atMostOne(::optimize, ::debug)
                .conflicts(::fast, ::safe)

            val input by option("--input")
            val template by option("--template")
            val optimize by option("--optimize").bool()
            val debug by option("--debug").bool()
            val fast by option("--fast").bool()
            val safe by option("--safe").bool()
            val file by positional()
        }

        // Test valid: all constraints satisfied
        val a1 = MultiGroupArgs()
        a1.parseWithException(arrayOf("build", "--input", "main.kt", "--optimize", "file"))
        assertEquals("main.kt", a1.input)
        assertNull(a1.template)
        assertEquals(true, a1.optimize)
        assertNull(a1.debug)

        // Test valid: template with debug (not fast or safe)
        val a2 = MultiGroupArgs()
        a2.parseWithException(arrayOf("build", "--template", "basic.kt", "--debug", "file"))
        assertNull(a2.input)
        assertEquals("basic.kt", a2.template)
        assertNull(a2.optimize)
        assertEquals(true, a2.debug)

        // Test failure: both input and template (exactlyOne violation)
        val a3 = MultiGroupArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("build", "--input", "main.kt", "--template", "basic.kt", "file"))
        }

        // Test failure: both optimize and debug (atMostOne violation)
        val a4 = MultiGroupArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--input", "main.kt", "--optimize", "--debug", "file"))
        }

        // Test failure: both fast and safe (conflicts violation)
        val a5 = MultiGroupArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("build", "--input", "main.kt", "--fast", "--safe", "file"))
        }
    }

    @Test
    fun domainGroupConstraints_multipleDomains() {
        class MultiDomainArgs : Arguments(
            appName = "multi-domain-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build").exactlyOne(::source, ::binary)
            val testDomain by domain("test").atMostOne(::unit, ::integration)
            val deployDomain by domain("deploy")
                .atLeastOne(::staging, ::production)
                .conflicts(::rollback, ::fresh)

            val source by option("--source")
            val binary by option("--binary")
            val unit by option("--unit").bool()
            val integration by option("--integration").bool()
            val staging by option("--staging").bool()
            val production by option("--production").bool()
            val rollback by option("--rollback").bool()
            val fresh by option("--fresh").bool()
            val file by positional()
        }

        // Test build domain constraints
        val a1 = MultiDomainArgs()
        a1.parseWithException(arrayOf("build", "--source", "main.kt", "file"))
        assertEquals("main.kt", a1.source)
        assertNull(a1.binary)

        // Test test domain constraints
        val a2 = MultiDomainArgs()
        a2.parseWithException(arrayOf("test", "--unit", "file"))
        assertEquals(true, a2.unit)
        assertNull(a2.integration)

        // Test deploy domain constraints
        val a3 = MultiDomainArgs()
        a3.parseWithException(arrayOf("deploy", "--staging", "--production", "file"))
        assertEquals(true, a3.staging)
        assertEquals(true, a3.production)
        assertNull(a3.rollback)
        assertNull(a3.fresh)

        // Test failure in build domain
        val a4 = MultiDomainArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "file")) // missing exactlyOne
        }

        // Test failure in test domain
        val a5 = MultiDomainArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("test", "--unit", "--integration", "file")) // atMostOne violation
        }

        // Test failure in deploy domain
        val a6 = MultiDomainArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("deploy", "file")) // missing atLeastOne
        }

        val a7 = MultiDomainArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("deploy", "--staging", "--rollback", "--fresh", "file")) // conflicts violation
        }
    }
}
