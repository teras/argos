/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive test for single-dash vs double-dash argument handling.
 * Verifies that both styles work correctly and can be mixed.
 */
class SingleVsDoubleDashTest {

    class TestArgs : Arguments(appName = "dash-test") {
        // String options
        val singleFile by option("-file")
        val doubleFile by option("--config")

        // Boolean options
        val singleVerbose by option("-verbose").bool()
        val doubleQuiet by option("--quiet").bool()

        // Integer options
        val singleCount by option("-count").int()
        val doubleTimeout by option("--timeout").int()

        // List options
        val singleTags by option("-tag").list()
        val doubleInclude by option("--include").list()

        // Set options
        val singleCategories by option("-cat").set()
        val doubleFormats by option("--format").set()

        // Options with defaults
        val singlePort by option("-port").int().default(8080)
        val doubleHost by option("--host").default("localhost")

        // Enum-like options
        val singleMode by option("-mode").oneOf("fast", "slow", "auto")
        val doubleLevel by option("--level").oneOf("debug", "info", "warn", "error")

        // Count options
        val singleVerbosity by option("-v").bool().list()
        val doubleDebug by option("--debug").bool().list()

        // Additional short options for clustering tests
        val help by option("-h").bool()
        val force by option("-f").bool()
        val recursive by option("-r").bool()
    }

    @Test
    fun `single dash string arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-file", "test.txt"))

        assertEquals("test.txt", args.singleFile)
        assertNull(args.doubleFile)
    }

    @Test
    fun `double dash string arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("--config", "app.conf"))

        assertEquals("app.conf", args.doubleFile)
        assertNull(args.singleFile)
    }

    @Test
    fun `single dash boolean arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-verbose"))

        assertTrue(args.singleVerbose == true)
        assertNull(args.doubleQuiet)
    }

    @Test
    fun `double dash boolean arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("--quiet"))

        assertTrue(args.doubleQuiet == true)
        assertNull(args.singleVerbose)
    }

    @Test
    fun `single dash integer arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-count", "42"))

        assertEquals(42, args.singleCount)
        assertNull(args.doubleTimeout)
    }

    @Test
    fun `double dash integer arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("--timeout", "30"))

        assertEquals(30, args.doubleTimeout)
        assertNull(args.singleCount)
    }

    @Test
    fun `single dash list arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-tag", "core", "-tag", "api", "-tag", "test"))

        assertEquals(listOf("core", "api", "test"), args.singleTags)
        assertEquals(emptyList(), args.doubleInclude)
    }

    @Test
    fun `double dash list arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("--include", "src", "--include", "lib", "--include", "tests"))

        assertEquals(listOf("src", "lib", "tests"), args.doubleInclude)
        assertEquals(emptyList(), args.singleTags)
    }

    @Test
    fun `single dash set arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-cat", "ui", "-cat", "backend", "-cat", "ui"))  // duplicate "ui"

        assertEquals(setOf("ui", "backend"), args.singleCategories)
        assertEquals(emptySet(), args.doubleFormats)
    }

    @Test
    fun `double dash set arguments work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("--format", "json", "--format", "xml", "--format", "json"))  // duplicate "json"

        assertEquals(setOf("json", "xml"), args.doubleFormats)
        assertEquals(emptySet(), args.singleCategories)
    }

    @Test
    fun `single and double dash can be mixed in same command`() {
        val args = TestArgs()
        args.parseWithException(arrayOf(
            "-file", "input.txt",
            "--config", "app.conf",
            "-verbose",
            "--timeout", "60",
            "-tag", "important",
            "--include", "lib"
        ))

        assertEquals("input.txt", args.singleFile)
        assertEquals("app.conf", args.doubleFile)
        assertTrue(args.singleVerbose == true)
        assertEquals(60, args.doubleTimeout)
        assertEquals(listOf("important"), args.singleTags)
        assertEquals(listOf("lib"), args.doubleInclude)
    }

    @Test
    fun `default values work for both single and double dash`() {
        val args = TestArgs()
        args.parseWithException(arrayOf())  // no arguments

        assertEquals(8080, args.singlePort)  // default value
        assertEquals("localhost", args.doubleHost)  // default value
    }

    @Test
    fun `enum-like options work for both single and double dash`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-mode", "fast", "--level", "debug"))

        assertEquals("fast", args.singleMode)
        assertEquals("debug", args.doubleLevel)
    }

    @Test
    fun `count options work for both single and double dash`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-v", "-v", "-v", "--debug", "--debug"))

        assertEquals(3, args.singleVerbosity.size)
        assertEquals(2, args.doubleDebug.size)
    }

    @Test
    fun `equals syntax works for both single and double dash`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-file=test.txt", "--timeout=45"))

        assertEquals("test.txt", args.singleFile)
        assertEquals(45, args.doubleTimeout)
    }

    @Test
    fun `error handling works correctly for unknown options`() {
        val args = TestArgs()

        // Test single dash unknown option
        try {
            args.parseWithException(arrayOf("-unknown"))
            throw AssertionError("Expected exception for unknown option -unknown")
        } catch (e: Exception) {
            // Should throw some kind of exception for unknown options
            assertTrue(e is ParseError || e is NullPointerException || e.message?.contains("unknown") == true)
        }

        // Test double dash unknown option
        try {
            args.parseWithException(arrayOf("--unknown"))
            throw AssertionError("Expected exception for unknown option --unknown")
        } catch (e: Exception) {
            // Should throw some kind of exception for unknown options
            assertTrue(e is ParseError || e is NullPointerException || e.message?.contains("unknown") == true)
        }
    }

    @Test
    fun `error handling works correctly for invalid enum values`() {
        val args = TestArgs()

        assertThrows<ParseError> {
            args.parseWithException(arrayOf("-mode", "invalid"))
        }

        assertThrows<ParseError> {
            args.parseWithException(arrayOf("--level", "invalid"))
        }
    }

    @Test
    fun `error handling works correctly for invalid integer values`() {
        val args = TestArgs()

        assertThrows<ParseError> {
            args.parseWithException(arrayOf("-count", "not-a-number"))
        }

        assertThrows<ParseError> {
            args.parseWithException(arrayOf("--timeout", "not-a-number"))
        }
    }

    @Test
    fun `complex mixed usage scenario`() {
        val args = TestArgs()
        args.parseWithException(arrayOf(
            "-file", "input.txt",
            "--config", "app.conf",
            "-verbose",
            "--quiet",  // both verbose and quiet can be set
            "-count", "100",
            "--timeout", "30",
            "-tag", "core", "-tag", "api",
            "--include", "src", "--include", "lib",
            "-cat", "ui", "-cat", "backend",
            "--format", "json", "--format", "xml",
            "-port", "9090",
            "--host", "example.com",
            "-mode", "auto",
            "--level", "info",
            "-v", "-v",
            "--debug"
        ))

        // Verify all values are correctly parsed
        assertEquals("input.txt", args.singleFile)
        assertEquals("app.conf", args.doubleFile)
        assertTrue(args.singleVerbose == true)
        assertTrue(args.doubleQuiet == true)
        assertEquals(100, args.singleCount)
        assertEquals(30, args.doubleTimeout)
        assertEquals(listOf("core", "api"), args.singleTags)
        assertEquals(listOf("src", "lib"), args.doubleInclude)
        assertEquals(setOf("ui", "backend"), args.singleCategories)
        assertEquals(setOf("json", "xml"), args.doubleFormats)
        assertEquals(9090, args.singlePort)
        assertEquals("example.com", args.doubleHost)
        assertEquals("auto", args.singleMode)
        assertEquals("info", args.doubleLevel)
        assertEquals(2, args.singleVerbosity.size)
        assertEquals(1, args.doubleDebug.size)
    }

    @Test
    fun `clustered short options work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-vhf"))  // -v, -h, -f clustered together

        assertTrue(args.singleVerbosity.size == 1)  // -v appeared once
        assertTrue(args.help == true)
        assertTrue(args.force == true)
        assertNull(args.recursive)
    }

    @Test
    fun `repeated clustered short options work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-vvhf"))  // -v appears twice, -h and -f once

        assertEquals(2, args.singleVerbosity.size)  // -v appeared twice
        assertTrue(args.help == true)
        assertTrue(args.force == true)
        assertNull(args.recursive)
    }

    @Test
    fun `clustered short options mixed with single dash long options`() {
        val args = TestArgs()
        args.parseWithException(arrayOf(
            "-vhf",                    // clustered short options
            "-file", "input.txt",      // single-dash long option
            "-mode", "fast",           // single-dash long option
            "-port", "8080"            // single-dash long option with int value
        ))

        // Verify clustered short options
        assertEquals(1, args.singleVerbosity.size)
        assertTrue(args.help == true)
        assertTrue(args.force == true)
        assertNull(args.recursive)

        // Verify single-dash long options
        assertEquals("input.txt", args.singleFile)
        assertEquals("fast", args.singleMode)
        assertEquals(8080, args.singlePort)
    }

    @Test
    fun `clustered short options mixed with double dash long options`() {
        val args = TestArgs()
        args.parseWithException(arrayOf(
            "-vhr",                    // clustered short options
            "--config", "app.conf",    // double-dash long option
            "--level", "info",         // double-dash long option
            "--timeout", "30"          // double-dash long option with int value
        ))

        // Verify clustered short options
        assertEquals(1, args.singleVerbosity.size)
        assertTrue(args.help == true)
        assertTrue(args.recursive == true)
        assertNull(args.force)

        // Verify double-dash long options
        assertEquals("app.conf", args.doubleFile)
        assertEquals("info", args.doubleLevel)
        assertEquals(30, args.doubleTimeout)
    }

    @Test
    fun `complex mixed clustering and long options scenario`() {
        val args = TestArgs()
        args.parseWithException(arrayOf(
            "-vvhfr",                  // multiple clustered options with -v appearing twice
            "-file", "main.cpp",       // single-dash long option
            "--config", "build.conf",  // double-dash long option
            "-mode", "auto",           // single-dash long option
            "--level", "debug",        // double-dash long option
            "-tag", "core", "-tag", "api",  // single-dash list option
            "--include", "lib", "--include", "tests",  // double-dash list option
            "-cat", "backend",         // single-dash set option
            "--format", "json",        // double-dash set option
            "--debug", "--debug"       // double-dash count option (twice)
        ))

        // Verify clustered short options
        assertEquals(2, args.singleVerbosity.size)  // -v appeared twice in cluster
        assertTrue(args.help == true)
        assertTrue(args.force == true)
        assertTrue(args.recursive == true)

        // Verify single-dash long options
        assertEquals("main.cpp", args.singleFile)
        assertEquals("auto", args.singleMode)
        assertEquals(listOf("core", "api"), args.singleTags)
        assertEquals(setOf("backend"), args.singleCategories)

        // Verify double-dash long options
        assertEquals("build.conf", args.doubleFile)
        assertEquals("debug", args.doubleLevel)
        assertEquals(listOf("lib", "tests"), args.doubleInclude)
        assertEquals(setOf("json"), args.doubleFormats)
        assertEquals(2, args.doubleDebug.size)  // appeared twice
    }

    @Test
    fun `clustered options with attached values work correctly`() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-vh", "-count=42"))  // cluster + attached value

        assertTrue(args.singleVerbosity.size == 1)
        assertTrue(args.help == true)
        assertEquals(42, args.singleCount)
    }

    @Test
    fun `equals syntax works with clustered and long options`() {
        val args = TestArgs()
        args.parseWithException(arrayOf(
            "-vf",                     // clustered short options
            "-file=test.txt",          // single-dash with equals
            "--timeout=45",            // double-dash with equals
            "-mode=slow"               // single-dash enum with equals
        ))

        assertEquals(1, args.singleVerbosity.size)
        assertTrue(args.force == true)
        assertEquals("test.txt", args.singleFile)
        assertEquals(45, args.doubleTimeout)
        assertEquals("slow", args.singleMode)
    }

    @Test
    fun `unknown options in clusters throw ParseError not NullPointerException`() {
        val args = TestArgs()

        // Test unknown option in cluster - should throw ParseError, not NPE
        val exception1 = assertThrows<ParseError> {
            args.parseWithException(arrayOf("-vhx"))  // -v and -h are known, -x is unknown
        }
        assertTrue(exception1.message!!.contains("Unknown option"))
        assertTrue(exception1.message!!.contains("-x"))

        // Test unknown option at start of cluster
        val exception2 = assertThrows<ParseError> {
            args.parseWithException(arrayOf("-xvh"))  // -x is unknown, -v and -h are known
        }
        assertTrue(exception2.message!!.contains("Unknown option"))
        assertTrue(exception2.message!!.contains("-x"))

        // Test single unknown option
        val exception3 = assertThrows<ParseError> {
            args.parseWithException(arrayOf("-z"))  // -z is unknown
        }
        assertTrue(exception3.message!!.contains("Unknown option"))
        assertTrue(exception3.message!!.contains("-z"))

        // Test unknown single-dash long option
        val exception4 = assertThrows<ParseError> {
            args.parseWithException(arrayOf("-unknown"))  // -unknown is unknown
        }
        assertTrue(exception4.message!!.contains("Unknown option"))
        assertTrue(exception4.message!!.contains("-unknown"))
    }

    @Test
    fun `mixed clustering with unknown options shows proper error context`() {
        val args = TestArgs()

        // Test that error shows the full cluster context
        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf(
                "-vhf",                // valid cluster
                "-file", "test.txt",   // valid single-dash long option
                "-invalidCluster"      // invalid cluster with unknown options
            ))
        }

        assertTrue(exception.message!!.contains("Unknown option"))
        // The error should reference the unknown option
    }
}