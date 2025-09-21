/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

enum class TestCaseMode { DEBUG, RELEASE, TEST }

class TypeAwareRequiresValueEdgeCasesTest {

    @Test
    fun testEqualsSyntaxWithBooleanOptions() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val verbose by option("-v", "--verbose").bool().requiresValue(false)
            val rest by positional()
        }

        // Valid boolean values with equals syntax
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug=true"))
        assertEquals(true, args1.debug)
        assertNull(args1.rest)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--debug=false"))
        assertEquals(false, args2.debug)
        assertNull(args2.rest)

        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--debug=1"))
        assertEquals(true, args3.debug)
        assertNull(args3.rest)

        val args4 = TestArgs()
        args4.parseWithException(arrayOf("--debug=0"))
        assertEquals(false, args4.debug)
        assertNull(args4.rest)

        // Invalid boolean values with equals syntax should parse as invalid but attached
        // Note: equals syntax always consumes the value, unlike space-separated
        val args5 = TestArgs()
        args5.parseWithException(arrayOf("--debug=invalid", "file.txt"))
        assertEquals(true, args5.debug) // parseBooleanLiteral("invalid") -> null -> defaults to true
        assertEquals("file.txt", args5.rest)

        // Short option with equals
        val args6 = TestArgs()
        args6.parseWithException(arrayOf("-v=true"))
        assertEquals(true, args6.verbose)
        assertNull(args6.rest)
    }

    @Test
    fun testEqualsSyntaxWithNumericOptions() {
        class TestArgs : Arguments() {
            val count by option("--count").int().requiresValue(false)
            val rate by option("--rate").double().requiresValue(false)
            val rest by positional()
        }

        // Valid numeric values with equals syntax
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--count=42"))
        assertEquals(42, args1.count)
        assertNull(args1.rest)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--rate=3.14"))
        assertEquals(3.14, args2.rate)
        assertNull(args2.rest)

        // Invalid numeric values with equals syntax
        // Note: equals syntax always consumes, so invalid values cause parse errors
        assertFailsWith<ParseError> {
            val args3 = TestArgs()
            args3.parseWithException(arrayOf("--count=invalid"))
        }

        assertFailsWith<ParseError> {
            val args4 = TestArgs()
            args4.parseWithException(arrayOf("--rate=notanumber"))
        }
    }

    @Test
    fun testEqualsSyntaxWithEnumAndOneOf() {
        class TestArgs : Arguments() {
            val mode by option("--mode").enum<TestCaseMode>().requiresValue(false)
            val format by option("--format").oneOf("json", "xml", "yaml").requiresValue(false)
            val rest by positional()
        }

        // Valid enum/oneOf with equals syntax
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--mode=DEBUG"))
        assertEquals(TestCaseMode.DEBUG, args1.mode)
        assertNull(args1.rest)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--format=json"))
        assertEquals("json", args2.format)
        assertNull(args2.rest)

        // Invalid enum/oneOf with equals syntax should cause parse errors
        assertFailsWith<ParseError> {
            val args3 = TestArgs()
            args3.parseWithException(arrayOf("--mode=INVALID"))
        }

        assertFailsWith<ParseError> {
            val args4 = TestArgs()
            args4.parseWithException(arrayOf("--format=invalid"))
        }
    }

    @Test
    fun testShortOptionClustering() {
        class TestArgs : Arguments() {
            val debug by option("-d").bool().requiresValue(false)
            val verbose by option("-v").bool()
            val count by option("-c").int().requiresValue(false)
            val help by option("-h").bool()
            val rest by positional()
        }

        // Clustering with boolean requiresValue(false) at end
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("-vd"))
        assertEquals(true, args1.verbose)
        assertEquals(true, args1.debug) // Flag mode since it's clustered
        assertNull(args1.rest)

        // Clustering with boolean requiresValue(false) in middle
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("-dvh"))
        assertEquals(true, args2.debug) // Flag mode since it's clustered
        assertEquals(true, args2.verbose)
        assertEquals(true, args2.help)
        assertNull(args2.rest)

        // Clustering with int requiresValue(false) - should be treated as flag mode
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("-cv", "file.txt"))
        assertNull(args3.count) // Flag mode - no value consumed
        assertEquals(true, args3.verbose)
        assertEquals("file.txt", args3.rest)

        // Attached value in clustering with equals sign
        val args4 = TestArgs()
        args4.parseWithException(arrayOf("-c=123", "file.txt"))
        assertEquals(123, args4.count) // Attached value consumed
        assertEquals("file.txt", args4.rest)
    }

    @Test
    fun testCaseSensitivityForBooleans() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val rest by positional()
        }

        // Various case combinations that should be recognized as boolean
        val testCases = listOf(
            "true" to true,
            "TRUE" to true,
            "True" to true,
            "tRuE" to true,
            "false" to false,
            "FALSE" to false,
            "False" to false,
            "fAlSe" to false,
            "1" to true,
            "0" to false
        )

        testCases.forEach { (input, expected) ->
            val args = TestArgs()
            args.parseWithException(arrayOf("--debug", input))
            assertEquals(expected, args.debug, "Failed for input: '$input'")
            assertNull(args.rest)
        }

        // Test additional valid boolean values that should be consumed
        val additionalValidCases = listOf(
            "yes" to true,
            "no" to false,
            "on" to true,
            "off" to false
        )

        additionalValidCases.forEach { (input, expected) ->
            val args = TestArgs()
            args.parseWithException(arrayOf("--debug", input))
            assertEquals(expected, args.debug, "Failed for valid boolean input: '$input'")
            assertNull(args.rest)
        }

        // Invalid boolean values should fall back to flag mode
        val invalidCases = listOf("y", "n", "2", "-1", "truE_typo", "maybe", "invalid")

        invalidCases.forEach { input ->
            val args = TestArgs()
            args.parseWithException(arrayOf("--debug", input))
            assertEquals(true, args.debug, "Should be flag mode for invalid input: '$input'")
            assertEquals(input, args.rest, "Invalid value should become positional for: '$input'")
        }
    }

    @Test
    fun testEnvironmentVariableInteraction() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false).fromEnv("DEBUG_VAR")
            val count by option("--count").int().requiresValue(false).fromEnv("COUNT_VAR")
            val rest by positional()
        }

        // Note: Environment variable testing would require mocking environment
        // For now, test the behavior when env vars are not set

        // When no env var and no CLI value - should be null/default
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("file.txt"))
        assertNull(args1.debug)
        assertNull(args1.count)
        assertEquals("file.txt", args1.rest)

        // CLI value should work normally even with env() defined
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--debug", "false", "file.txt"))
        assertEquals(false, args2.debug)
        assertEquals("file.txt", args2.rest)

        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--count", "42", "file.txt"))
        assertEquals(42, args3.count)
        assertEquals("file.txt", args3.rest)

        // Flag mode should still work
        val args4 = TestArgs()
        args4.parseWithException(arrayOf("--debug", "file.txt"))
        assertEquals(true, args4.debug) // Flag mode
        assertEquals("file.txt", args4.rest)
    }

    @Test
    fun testNumericEdgeCases() {
        class TestArgs : Arguments() {
            val count by option("--count").int().requiresValue(false)
            val rate by option("--rate").double().requiresValue(false)
            val rest by positional().list()
        }

        // Plus sign prefix
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--count", "+42"))
        assertEquals(42, args1.count)
        assertEquals(emptyList<String>(), args1.rest)

        // Scientific notation for double
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--rate", "1.23e-4"))
        assertEquals(1.23e-4, args2.rate)
        assertEquals(emptyList<String>(), args2.rest)

        // Hex numbers should be invalid for decimal parsing (fall back to flag mode)
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--count", "0x42", "file.txt"))
        assertNull(args3.count) // Flag mode - hex not valid for Int.parseFromString
        assertEquals(listOf("0x42", "file.txt"), args3.rest)

        // Floating point for int option (should be invalid)
        val args4 = TestArgs()
        args4.parseWithException(arrayOf("--count", "1.5", "file.txt"))
        assertNull(args4.count) // Flag mode - 1.5 not valid int
        assertEquals(listOf("1.5", "file.txt"), args4.rest)

        // Very large numbers
        val args5 = TestArgs()
        args5.parseWithException(arrayOf("--count", "999999999999999999999", "file.txt"))
        assertNull(args5.count) // Flag mode - too large for Int
        assertEquals(listOf("999999999999999999999", "file.txt"), args5.rest)
    }

    @Test
    fun testWhitespaceAndSpecialCharacters() {
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val name by option("--name").requiresValue(false)
            val rest by positional().list()
        }

        // Empty string as value
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug", "", "file.txt"))
        assertEquals(true, args1.debug) // parseBooleanLiteral("") -> null -> true for flag mode
        assertEquals(listOf("", "file.txt"), args1.rest)

        // Whitespace-only values
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--debug", "   ", "file.txt"))
        assertEquals(true, args2.debug) // Whitespace not valid boolean -> flag mode
        assertEquals(listOf("   ", "file.txt"), args2.rest)

        // String option always consumes (even whitespace)
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--name", "   ", "file.txt"))
        assertEquals("   ", args3.name) // String options consume any non-option value
        assertEquals(listOf("file.txt"), args3.rest)

        // Unicode characters
        val args4 = TestArgs()
        args4.parseWithException(arrayOf("--name", "françois", "file.txt"))
        assertEquals("françois", args4.name)
        assertEquals(listOf("file.txt"), args4.rest)
    }

    @Test
    fun testMultipleOccurrencesWithMixedValidity() {
        class TestArgs : Arguments() {
            val modes by option("--mode").enum<TestCaseMode>().requiresValue(false).list()
            val formats by option("--format").oneOf("json", "xml", "yaml").requiresValue(false).list()
            val rest by positional().list()
        }

        // Mix of valid and invalid values for list options
        val args1 = TestArgs()
        args1.parseWithException(arrayOf(
            "--mode", "DEBUG",      // Valid - consumed
            "--mode", "INVALID",    // Invalid - flag mode, becomes positional
            "--format", "json",     // Valid - consumed
            "--format", "badformat", // Invalid - flag mode, becomes positional
            "file.txt"
        ))

        assertEquals(listOf(TestCaseMode.DEBUG, null), args1.modes) // Second is null from flag mode
        assertEquals(listOf("json", null), args1.formats) // Second is null from flag mode
        assertEquals(listOf("INVALID", "badformat", "file.txt"), args1.rest) // Invalid values become positional
    }

    @Test
    fun testComplexScenarioWithMultipleFeatures() {
        class TestArgs : Arguments() {
            val debug by option("-d", "--debug").bool().requiresValue(false).default(false)
            val count by option("-c", "--count").int().requiresValue(false).help("Count value")
            val mode by option("--mode").enum<TestCaseMode>().requiresValue(false)
            val format by option("-f").oneOf("json", "xml").requiresValue(false).default("json")
            val files by positional().list()
        }

        // Complex scenario: mix of valid values, invalid values, flags, and defaults
        val args = TestArgs()
        args.parseWithException(arrayOf(
            "--debug", "true",          // Valid boolean - consumed
            "-c", "invalid",            // Invalid int - flag mode, becomes positional
            "--mode", "DEBUG",          // Valid enum - consumed
            "-f", "badformat",          // Invalid oneOf - flag mode, becomes positional
            "file1.txt",                // Positional
            "file2.txt"                 // Positional
        ))

        assertEquals(true, args.debug)              // Consumed true
        assertNull(args.count)                      // Flag mode from invalid value
        assertEquals(TestCaseMode.DEBUG, args.mode) // Consumed DEBUG
        assertEquals("json", args.format)           // Default value (flag mode)
        assertEquals(listOf("invalid", "badformat", "file1.txt", "file2.txt"), args.files) // All invalid values + files
    }
}