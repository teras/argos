/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

enum class TestMode { DEBUG, RELEASE, TEST }

class TypeAwareRequiresValueTest {

    @Test
    fun testBooleanOptionTypeAware() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val rest by positional()
        }

        // Should consume valid boolean values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug", "true"))
        assertEquals(true, args1.debug)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--debug", "false"))
        assertEquals(false, args2.debug)

        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--debug", "1"))
        assertEquals(true, args3.debug)

        val args4 = TestArgs()
        args4.parseWithException(arrayOf("--debug", "0"))
        assertEquals(false, args4.debug)

        // Should NOT consume invalid boolean values - treat as flag mode
        val args5 = TestArgs()
        args5.parseWithException(arrayOf("--debug", "invalidtext"))
        assertEquals(true, args5.debug) // Flag mode - empty string converted to true
        assertEquals("invalidtext", args5.rest) // Invalid boolean becomes positional
    }

    @Test
    fun testIntOptionTypeAware() {
        class TestArgs : Arguments() {
            val count by option("--count").int().requiresValue(false)
            val rest by positional()
        }

        // Should consume valid integer values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--count", "42"))
        assertEquals(42, args1.count)
        assertNull(args1.rest)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--count", "-5"))
        assertEquals(-5, args2.count)

        // Should NOT consume invalid integer values - treat as flag mode
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--count", "notanumber"))
        assertNull(args3.count) // Flag mode - empty string becomes null for int
        assertEquals("notanumber", args3.rest) // Invalid int becomes positional
    }

    @Test
    fun testDoubleOptionTypeAware() {
        class TestArgs : Arguments() {
            val rate by option("--rate").double().requiresValue(false)
            val rest by positional()
        }

        // Should consume valid double values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--rate", "3.14"))
        assertEquals(3.14, args1.rate)
        assertNull(args1.rest)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--rate", "42"))
        assertEquals(42.0, args2.rate)

        // Should NOT consume invalid double values - treat as flag mode
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--rate", "notadouble"))
        assertNull(args3.rate) // Flag mode - empty string becomes null for double
        assertEquals("notadouble", args3.rest) // Invalid double becomes positional
    }

    @Test
    fun testEnumOptionTypeAware() {
        class TestArgs : Arguments() {
            val mode by option("--mode").enum<TestMode>().requiresValue(false)
            val rest by positional()
        }

        // Should consume valid enum values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--mode", "DEBUG"))
        assertEquals(TestMode.DEBUG, args1.mode)
        assertNull(args1.rest)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--mode", "RELEASE"))
        assertEquals(TestMode.RELEASE, args2.mode)

        // Should NOT consume invalid enum values - treat as flag mode
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--mode", "INVALID"))
        assertNull(args3.mode) // Flag mode - empty string becomes null for enum
        assertEquals("INVALID", args3.rest) // Invalid enum becomes positional
    }

    @Test
    fun testOneOfOptionTypeAware() {
        class TestArgs : Arguments() {
            val format by option("--format").oneOf("json", "xml", "yaml").requiresValue(false)
            val rest by positional()
        }

        // Should consume valid oneOf values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--format", "json"))
        assertEquals("json", args1.format)
        assertNull(args1.rest)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--format", "xml"))
        assertEquals("xml", args2.format)

        // Should NOT consume invalid oneOf values - treat as flag mode
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--format", "invalid"))
        assertNull(args3.format) // Flag mode - empty string becomes null for oneOf
        assertEquals("invalid", args3.rest) // Invalid oneOf becomes positional
    }

    @Test
    fun testStringOptionTypeAware() {
        class TestArgs : Arguments() {
            val name by option("--name").requiresValue(false)
            val rest by positional()
        }

        // String options always consume non-option arguments
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--name", "john"))
        assertEquals("john", args1.name)
        assertNull(args1.rest)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--name", "123"))
        assertEquals("123", args2.name)

        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--name", "any-text"))
        assertEquals("any-text", args3.name)
    }

    @Test
    fun testWithKnownOptions() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val count by option("--count").int().requiresValue(false)
            val verbose by option("-v", "--verbose").bool()
        }

        // Should NOT consume next argument if it's a known option
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug", "--verbose"))
        assertEquals(true, args1.debug) // Flag mode since next is an option
        assertEquals(true, args1.verbose) // Boolean option consumed

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--count", "-v"))
        assertNull(args2.count) // Flag mode since next is an option
        assertEquals(true, args2.verbose)
    }

    @Test
    fun testWithAttachedValues() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val count by option("-c").int()
            val rest by positional()
        }

        // Should NOT consume attached values as regular values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug", "-c42", "file"))
        assertEquals(true, args1.debug) // Flag mode since next is option with attached value
        assertEquals(42, args1.count) // Attached value parsed correctly
        assertEquals("file", args1.rest)
    }

    @Test
    fun testWithDoubleDash() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val rest by positional()
        }

        // Should NOT consume "--"
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug", "--", "file"))
        assertEquals(true, args1.debug) // Flag mode since next is "--"
        assertEquals("file", args1.rest) // Everything after "--" is positional
    }

    @Test
    fun testChainedWithOtherMethods() {
        class TestArgs : Arguments() {
            val port by option("--port").int().requiresValue(false).default(8080)
            val level by option("--level").int().requiresValue(false).help("Logging level")
            val rest by positional()
        }

        // Type-aware behavior works with chained methods
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--port", "3000"))
        assertEquals(3000, args1.port)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--port", "invalid"))
        assertEquals(8080, args2.port) // Default used when flag mode + no override
        assertEquals("invalid", args2.rest) // Invalid int becomes positional

        val args3 = TestArgs()
        args3.parseWithException(arrayOf())
        assertEquals(8080, args3.port) // Default when not provided
    }

    @Test
    fun testNumericSwitchEdgeCase() {
        // Test the edge case where a numeric value could also be a switch
        class TestArgs : Arguments() {
            val count by option("--count").int().requiresValue(false)
            val three by option("-3").bool() // Numeric switch
            val rest by positional()
        }

        // If "-3" is a valid int AND a defined switch, --count should consume it as an int
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--count", "-3"))
        assertEquals(-3, args1.count) // Consumed as integer value
        assertEquals(null, args1.three) // Switch not triggered
        assertNull(args1.rest)
    }

    @Test
    fun testTypeFirstPriorityOverSwitchDetection() {
        // Verify that type compatibility takes priority over switch detection
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val level by option("--level").int().requiresValue(false)
            val enable by option("--enable").bool() // Another boolean switch
            val rest by positional()
        }

        // Boolean should consume "true" even if it looks like it could be something else
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug", "true"))
        assertEquals(true, args1.debug)
        assertNull(args1.rest)

        // Int should consume "123" even if there was a "-123" switch defined
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--level", "123"))
        assertEquals(123, args2.level)
        assertNull(args2.rest)

        // But string options should still avoid consuming known switches
        class StringTestArgs : Arguments() {
            val name by option("--name").requiresValue(false)
            val verbose by option("--verbose").bool()
            val rest by positional()
        }

        val args3 = StringTestArgs()
        args3.parseWithException(arrayOf("--name", "--verbose"))
        assertEquals("", args3.name) // Flag mode since next is a known switch
        assertEquals(true, args3.verbose) // Switch processed normally
        assertNull(args3.rest)
    }
}