/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CustomNegationPrefixTest {

    class DefaultNegationArgs : Arguments() {
        val colors by option("--colors").bool().negatable()
        val verbose by option("--verbose").bool().negatable()
    }

    class CustomNegationArgs : Arguments() {
        val cache by option("--cache").bool().negatable("disable-")
        val debug by option("--debug").bool().negatable("skip-")
        val logging by option("--logging").bool().negatable("turn-off-")
    }

    class MixedNegationArgs : Arguments() {
        val colors by option("--colors").bool().negatable()  // default "no-"
        val cache by option("--cache").bool().negatable("disable-")  // custom prefix
        val debug by option("--debug").bool().negatable("skip-")     // custom prefix
        val backwardCompat by option("--backward").bool().negatable()  // now using new API
    }

    class NonNullableBooleanArgs : Arguments() {
        val feature by option("--feature").bool().default(false).negatable("turn-off-")  // non-nullable Boolean
        val mode by option("--mode").bool().default(true).negatable("disable-")  // non-nullable Boolean with true default
    }

    @Test
    fun testDefaultNegationPrefix() {
        val args = DefaultNegationArgs()

        // Test positive case
        args.parseWithException(arrayOf("--colors"))
        assertEquals(true, args.colors)

        // Test negative case with default "no-" prefix
        val args2 = DefaultNegationArgs()
        args2.parseWithException(arrayOf("--no-colors"))
        assertEquals(false, args2.colors)

        // Test both options
        val args3 = DefaultNegationArgs()
        args3.parseWithException(arrayOf("--colors", "--no-verbose"))
        assertEquals(true, args3.colors)
        assertEquals(false, args3.verbose)
    }

    @Test
    fun testCustomNegationPrefix() {
        val args = CustomNegationArgs()

        // Test positive case
        args.parseWithException(arrayOf("--cache"))
        assertEquals(true, args.cache)

        // Test custom negation prefix
        val args2 = CustomNegationArgs()
        args2.parseWithException(arrayOf("--disable-cache"))
        assertEquals(false, args2.cache)

        // Test different custom prefix
        val args3 = CustomNegationArgs()
        args3.parseWithException(arrayOf("--skip-debug"))
        assertEquals(false, args3.debug)

        // Test longer custom prefix
        val args4 = CustomNegationArgs()
        args4.parseWithException(arrayOf("--turn-off-logging"))
        assertEquals(false, args4.logging)
    }

    @Test
    fun testMixedNegationPrefixes() {
        val args = MixedNegationArgs()

        // Test mixing default and custom prefixes
        args.parseWithException(arrayOf("--no-colors", "--disable-cache", "--skip-debug", "--no-backward"))

        assertEquals(false, args.colors)
        assertEquals(false, args.cache)
        assertEquals(false, args.debug)
        assertEquals(false, args.backwardCompat)
    }

    @Test
    fun testInvalidNegationAttempts() {
        val args = CustomNegationArgs()

        // Should fail when using wrong negation prefix
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--no-cache"))  // should be --disable-cache
        }

        val args2 = CustomNegationArgs()
        assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--disable-debug"))  // should be --skip-debug
        }
    }

    @Test
    fun testNegationWithoutPositiveOption() {
        val args = CustomNegationArgs()

        // Test that only the negation works
        args.parseWithException(arrayOf("--disable-cache"))

        assertEquals(false, args.cache)
        assertNull(args.debug)  // not provided
        assertNull(args.logging)  // not provided
    }

    @Test
    fun testHelpOutputShowsCustomPrefixes() {
        val args = CustomNegationArgs()

        // Get help snapshot to verify custom prefixes are shown
        val snapshot = args.snapshot()
        val options = snapshot.options

        // Find the cache option and verify it shows custom prefix
        val cacheOption = options.find { it.switches.contains("--cache") }
        checkNotNull(cacheOption) { "Cache option should exist" }
        assertEquals(true, cacheOption.negationPrefix != null)
        assertEquals("disable-", cacheOption.negationPrefix)

        // Find the debug option
        val debugOption = options.find { it.switches.contains("--debug") }
        checkNotNull(debugOption) { "Debug option should exist" }
        assertEquals(true, debugOption.negationPrefix != null)
        assertEquals("skip-", debugOption.negationPrefix)
    }

    @Test
    fun testNonNullableBooleanNegatable() {
        val args = NonNullableBooleanArgs()

        // Test default values
        args.parseWithException(arrayOf())
        assertEquals(false, args.feature)  // default false
        assertEquals(true, args.mode)      // default true

        // Test positive cases
        val args2 = NonNullableBooleanArgs()
        args2.parseWithException(arrayOf("--feature", "--mode"))
        assertEquals(true, args2.feature)
        assertEquals(true, args2.mode)

        // Test custom negation prefixes
        val args3 = NonNullableBooleanArgs()
        args3.parseWithException(arrayOf("--turn-off-feature", "--disable-mode"))
        assertEquals(false, args3.feature)
        assertEquals(false, args3.mode)
    }

    @Test
    fun testEmptyPrefixBehavior() {
        // Empty prefix now throws ConfigError due to validation
        // This is the correct behavior to prevent confusing edge cases
        assertFailsWith<ConfigException> {
            class EmptyPrefixArgs : Arguments() {
                val test by option("--test").bool().negatable("")
            }
            EmptyPrefixArgs() // This will throw ConfigError
        }
    }

    @Test
    fun testInvalidPrefixValidation() {
        // Test that empty prefix throws ConfigError
        assertFailsWith<ConfigException> {
            class EmptyPrefixArgs : Arguments() {
                val test by option("--test").bool().negatable("")
            }
            EmptyPrefixArgs() // Instantiate to trigger validation
        }

        // Test that whitespace-only prefix throws ConfigError
        assertFailsWith<ConfigException> {
            class WhitespaceOnlyPrefixArgs : Arguments() {
                val test by option("--test").bool().negatable("   ")
            }
            WhitespaceOnlyPrefixArgs() // Instantiate to trigger validation
        }

        // Test that null prefix is allowed (uses default)
        class NullPrefixArgs : Arguments() {
            val test by option("--test").bool().negatable(null)
        }
        val args = NullPrefixArgs()
        args.parseWithException(arrayOf("--no-test"))  // Should use default "no-" prefix
        assertEquals(false, args.test)

        // Test that valid prefix works
        class ValidPrefixArgs : Arguments() {
            val test by option("--test").bool().negatable("disable-")
        }
        val validArgs = ValidPrefixArgs()
        validArgs.parseWithException(arrayOf("--disable-test"))
        assertEquals(false, validArgs.test)
    }

    @Test
    fun testNonNullableInvalidPrefixValidation() {
        // Test that empty prefix throws ConfigError for non-nullable boolean
        assertFailsWith<ConfigException> {
            class EmptyPrefixArgs : Arguments() {
                val test by option("--test").bool().default(false).negatable("")
            }
            EmptyPrefixArgs() // Instantiate to trigger validation
        }

        // Test that whitespace-only prefix throws ConfigError for non-nullable boolean
        assertFailsWith<ConfigException> {
            class WhitespaceOnlyPrefixArgs : Arguments() {
                val test by option("--test").bool().default(false).negatable("   ")
            }
            WhitespaceOnlyPrefixArgs() // Instantiate to trigger validation
        }
    }
}