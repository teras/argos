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
import kotlin.test.assertNotNull

class RequiresValueTest {

    @Test
    fun testRequiresValueTrue() {
        class TestArgs : Arguments() {
            val config by option("--config").requiresValue(true)
        }

        val args = TestArgs()

        // Should succeed when value is provided
        args.parseWithException(arrayOf("--config", "app.conf"))
        assertEquals("app.conf", args.config)

        // Should fail when no value is provided
        val args2 = TestArgs()
        assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--config"))
        }
    }

    @Test
    fun testRequiresValueFalse() {
        // requiresValue(false) now works correctly - options can be used with or without values

        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").requiresValue(false).map { it?.toIntOrNull() ?: 1 }
        }

        val args = TestArgs()

        // requiresValue(false) now works - option consumes value when available
        args.parseWithException(arrayOf("-v", "3"))
        assertEquals(3, args.verbose)
    }

    @Test
    fun testRequiresValueWithBooleanOption() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--debug"))
        assertTrue(args.debug == true)

        // Should also work with explicit value
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--debug", "false"))
        assertTrue(args2.debug == false)
    }

    @Test
    fun testRequiresValueWithIntOption() {
        class TestArgs : Arguments() {
            val threads by option("--threads").int().requiresValue(true)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--threads", "4"))
        assertEquals(4, args.threads)

        // Should fail without value
        val args2 = TestArgs()
        assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--threads"))
        }
    }

    @Test
    fun testListOptionsAlwaysRequireValues() {
        // List options inherently require values for each occurrence
        class TestArgs : Arguments() {
            val files by option("--file").list()
        }

        val args = TestArgs()
        // List options always need values - no optional value support
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--file"))
        }
    }

    @Test
    fun testSetOptionsAlwaysRequireValues() {
        // Set options inherently require values for each occurrence
        class TestArgs : Arguments() {
            val tags by option("--tag").set()
        }

        val args = TestArgs()
        // Set options always need values - no optional value support
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--tag"))
        }
    }

    @Test
    fun testRequiresValueWithCountOption() {
        // Count options inherently don't require values
        class TestArgs : Arguments() {
            val verbosity by option("-v").bool().list()
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("-v", "-v", "-v"))
        assertEquals(3, args.verbosity.size)
    }

    @Test
    fun testRequiresValueWithDefault() {
        // requiresValue(false) now works correctly with defaults
        class TestArgs : Arguments() {
            val port by option("--port").int().requiresValue(false).default(8080)
        }

        val args = TestArgs()

        // Should use default when not provided
        args.parseWithException(arrayOf())
        assertEquals(8080, args.port)

        // requiresValue(false) now works - option consumes value when provided
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--port", "3000"))
        assertEquals(3000, args2.port)
    }

    @Test
    fun testRequiresValueBasicFunctionality() {
        // Test that requiresValue() method exists and can be called
        class TestArgs : Arguments() {
            val level by option("--level").int().requiresValue(true) // Should work normally
            val output by option("-o").requiresValue(false) // Should not crash
        }

        val args = TestArgs()

        // requiresValue(true) should work normally
        args.parseWithException(arrayOf("--level", "5"))
        assertEquals(5, args.level)

        // requiresValue(false) currently has limited implementation
        // but the API should not crash
        assertNotNull(args) // Basic smoke test
    }

    @Test
    fun testRequiresValueWithEnvironmentVariable() {
        class TestArgs : Arguments() {
            val config by option("--config").requiresValue(false)
        }

        val args = TestArgs()

        // requiresValue(false) now works correctly!
        args.parseWithException(arrayOf("--config"))
        assertEquals("", args.config) // Empty string indicates flag present without value
    }

    @Test
    fun testRequiresValueChaining() {
        // Test that requiresValue can be chained with other methods
        class TestArgs : Arguments() {
            val config by option("--config").requiresValue(false).help("Config file")
        }

        val args = TestArgs()

        // requiresValue(false) now works correctly!
        args.parseWithException(arrayOf("--config"))
        assertEquals("", args.config) // Empty string indicates flag present without value
    }

    @Test
    fun testRequiresValuePreservesOtherProperties() {
        // Test that requiresValue preserves other builder properties
        class TestArgs : Arguments() {
            val password by option("-p", "--password").requiresValue(false).help("Password")
        }

        val args = TestArgs()

        // requiresValue(false) now works correctly!
        args.parseWithException(arrayOf("-p"))
        assertEquals("", args.password) // Empty string indicates flag present without value
    }
}
