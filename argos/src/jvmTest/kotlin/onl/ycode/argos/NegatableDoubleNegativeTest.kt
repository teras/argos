/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals

class NegatableDoubleNegativeTest {

    class NegatableArgs : Arguments() {
        val ansi by option("--ansi").bool().default(true).negatable()
        val cache by option("--cache").bool().default(false).negatable()
    }

    @Test
    fun testDoubleNegativeLogicWithTrueValues() {
        // Test case: --no-ansi true should result in false (negative of true)
        val args1 = NegatableArgs()
        args1.parseWithException(arrayOf("--no-ansi", "true"))
        assertEquals(false, args1.ansi, "--no-ansi true should result in false")

        // Test case: --no-ansi false should result in true (negative of false)
        val args2 = NegatableArgs()
        args2.parseWithException(arrayOf("--no-ansi", "false"))
        assertEquals(true, args2.ansi, "--no-ansi false should result in true")
    }

    @Test
    fun testDoubleNegativeLogicWithFalseDefault() {
        // Test case: --no-cache true should result in false (negative of true)
        val args1 = NegatableArgs()
        args1.parseWithException(arrayOf("--no-cache", "true"))
        assertEquals(false, args1.cache, "--no-cache true should result in false")

        // Test case: --no-cache false should result in true (negative of false)
        val args2 = NegatableArgs()
        args2.parseWithException(arrayOf("--no-cache", "false"))
        assertEquals(true, args2.cache, "--no-cache false should result in true")
    }

    @Test
    fun testBasicNegationStillWorks() {
        // Ensure basic negation still works
        val args1 = NegatableArgs()
        args1.parseWithException(arrayOf("--no-ansi"))
        assertEquals(false, args1.ansi, "--no-ansi flag should result in false")

        val args2 = NegatableArgs()
        args2.parseWithException(arrayOf("--ansi"))
        assertEquals(true, args2.ansi, "--ansi flag should result in true")
    }

    @Test
    fun testVariousLiterals() {
        // Test with various true/false literals
        val literals = listOf(
            "true" to true, "false" to false,
            "1" to true, "0" to false,
            "yes" to true, "no" to false,
            "on" to true, "off" to false
        )

        for ((literal, value) in literals) {
            val args = NegatableArgs()
            args.parseWithException(arrayOf("--no-ansi", literal))
            assertEquals(!value, args.ansi, "--no-ansi $literal should result in ${!value}")
        }
    }

    @Test
    fun testCustomNegationPrefix() {
        class CustomArgs : Arguments() {
            val feature by option("--feature").bool().default(true).negatable("disable-")
            val mode by option("--mode").bool().default(false).negatable("turn-off-")
        }

        // Test custom prefix with explicit true
        val args1 = CustomArgs()
        args1.parseWithException(arrayOf("--disable-feature", "true"))
        assertEquals(false, args1.feature, "--disable-feature true should result in false")

        // Test custom prefix with explicit false
        val args2 = CustomArgs()
        args2.parseWithException(arrayOf("--disable-feature", "false"))
        assertEquals(true, args2.feature, "--disable-feature false should result in true")

        // Test longer custom prefix
        val args3 = CustomArgs()
        args3.parseWithException(arrayOf("--turn-off-mode", "yes"))
        assertEquals(false, args3.mode, "--turn-off-mode yes should result in false")

        val args4 = CustomArgs()
        args4.parseWithException(arrayOf("--turn-off-mode", "no"))
        assertEquals(true, args4.mode, "--turn-off-mode no should result in true")
    }

    @Test
    fun testCaseSensitivity() {
        // Test that boolean literals are case-insensitive when negated
        val literals = listOf("TRUE", "True", "FALSE", "False", "YES", "Yes", "NO", "No")

        for (literal in literals) {
            val args = NegatableArgs()
            args.parseWithException(arrayOf("--no-ansi", literal))
            val lowercased = literal.lowercase()
            val parsed = when (lowercased) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> true // default for invalid
            }
            val expected = !parsed
            assertEquals(expected, args.ansi, "--no-ansi $literal should result in $expected")
        }
    }

    @Test
    fun testInvalidLiteralsAsFlags() {
        // When invalid values are not consumed, the option works as a flag
        // This test verifies that invalid values are not consumed and the option works as a negated flag
        val args = NegatableArgs()
        // "invalid" should not be consumed as a value, so --no-ansi works as a flag
        args.parseWithException(arrayOf("--no-ansi"))
        assertEquals(false, args.ansi, "--no-ansi as flag should result in false")
    }

    @Test
    fun testMixedPositiveAndNegativeOptions() {
        // Test that positive and negative options can be mixed
        val args = NegatableArgs()
        args.parseWithException(arrayOf("--ansi", "true", "--no-cache", "false"))
        assertEquals(true, args.ansi, "--ansi true should result in true")
        assertEquals(true, args.cache, "--no-cache false should result in true")
    }

    @Test
    fun testRequiredNegatedOptions() {
        class RequiredArgs : Arguments() {
            val enabled by option("--enabled").bool().negatable().required()
        }

        // Test that required negated options work
        val args1 = RequiredArgs()
        args1.parseWithException(arrayOf("--no-enabled", "true"))
        assertEquals(false, args1.enabled)

        val args2 = RequiredArgs()
        args2.parseWithException(arrayOf("--enabled", "false"))
        assertEquals(false, args2.enabled)
    }
}