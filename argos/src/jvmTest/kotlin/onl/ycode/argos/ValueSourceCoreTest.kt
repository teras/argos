/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test the core valueSource functionality without using the extension property
 * that requires kotlin-reflect. This verifies that our tracking works correctly.
 */
class ValueSourceCoreTest {

    @Test
    fun testCoreValueSourceTracking() {
        class TestArgs : Arguments(appName = "test") {
            val port by option("--port").int().default(8080)
            val host by option("--host").default("localhost")
            val debug by option("--debug").bool()
            val tags by option("--tag").list()
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--port", "9000", "--debug", "--tag", "test"))

        // Test via the new property-based API
        assertEquals(ValueSource.USER, args.valueSourceOf(args::port))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::debug))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::tags))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::host))

        // Verify actual values
        assertEquals(9000, args.port)
        assertEquals("localhost", args.host)
        assertEquals(true, args.debug)
        assertEquals(listOf("test"), args.tags)
    }

    @Test
    fun testDefaultValues() {
        class TestArgs : Arguments(appName = "test") {
            val port by option("--port").int().default(8080)
            val host by option("--host").default("localhost")
            val enabled by option("--enabled").bool().default(false)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf()) // No arguments provided

        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::port))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::host))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::enabled))

        assertEquals(8080, args.port)
        assertEquals("localhost", args.host)
        assertEquals(false, args.enabled)
    }

    @Test
    fun testMissingValues() {
        class TestArgs : Arguments(appName = "test") {
            val port by option("--port").int() // No default
            val host by option("--host")       // No default
            val debug by option("--debug").bool()  // No default
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())

        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::port))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::host))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::debug))

        assertNull(args.port)
        assertNull(args.host)
        assertNull(args.debug)
    }

    @Test
    fun testCollectionValueSources() {
        class TestArgs : Arguments(appName = "test") {
            val userTags by option("--tag").list() // USER when provided
            val missingColors by option("--color").set() // MISSING
            val verboseCount by option("-v").bool().list() // USER when provided
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--tag", "test1", "--tag", "test2", "-v", "-v"))

        assertEquals(ValueSource.USER, args.valueSourceOf(args::userTags))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::verboseCount))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::missingColors))

        assertEquals(listOf("test1", "test2"), args.userTags)
        assertEquals(2, args.verboseCount.size)
        assertEquals(emptySet<String>(), args.missingColors)
    }

    @Test
    fun testDomainValueSources() {
        class TestArgs : Arguments(appName = "test") {
            val alpha by domain("alpha")
            val beta by domain("beta")
            val port by option("--port").int().default(8080)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("alpha", "--port", "9000"))

        // Domain tracking is now implemented
        assertEquals(ValueSource.USER, args.valueSourceOf(args::port))

        assertEquals(true, args.alpha)
        assertEquals(false, args.beta)
        assertEquals(9000, args.port)
    }

    @Test
    fun testComplexScenario() {
        class TestArgs : Arguments(appName = "test") {
            val alpha by domain("alpha")
            val config by option("--config").default("/etc/app.conf")
            val port by option("--port").int().default(8080)
            val verbose by option("-v", "--verbose").bool().list()
            val files by option("--file").list()
            val categories by option("--category").set()
            val optional by option("--optional").bool()
        }

        val args = TestArgs()
        args.parseWithException(arrayOf(
            "alpha",                    // domain selection
            "--port", "3000",           // user-provided int
            "-vv",                      // user-provided count (2)
            "--file", "a.txt",          // user-provided list
            "--file", "b.txt",
            "--category", "web",        // user-provided set
            "--category", "api"
            // config uses default, optional is missing
        ))

        // Domain tracking now implemented
        assertEquals(ValueSource.USER, args.valueSourceOf(args::alpha))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::config))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::port))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::verbose))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::files))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::categories))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::optional))

        assertEquals(true, args.alpha)
        assertEquals("/etc/app.conf", args.config)
        assertEquals(3000, args.port)
        assertEquals(2, args.verbose.size)
        assertEquals(listOf("a.txt", "b.txt"), args.files)
        assertEquals(setOf("web", "api"), args.categories)
        assertNull(args.optional)
    }
}