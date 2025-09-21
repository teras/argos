/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BooleanRequiresValueTest {

    @Test
    fun testBooleanWithRequiresValueFalse() {
        // Current behavior - boolean options with optional values
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(false)
            val rest by positional()
        }

        // Should consume valid boolean values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug", "true"))
        assertEquals(true, args1.debug)

        // Should NOT consume invalid values - use flag mode
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--debug", "invalid"))
        assertEquals(true, args2.debug) // Flag mode
        assertEquals("invalid", args2.rest) // Invalid becomes positional

        // Should work in flag mode at end of arguments
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--debug"))
        assertEquals(true, args3.debug) // Flag mode
    }

    @Test
    fun testBooleanWithRequiresValueTrue() {
        // Desired behavior - boolean options that require values
        class TestArgs : Arguments() {
            val debug by option("--debug").bool().requiresValue(true)
        }

        // Should consume valid boolean values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug", "true"))
        assertEquals(true, args1.debug)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--debug", "false"))
        assertEquals(false, args2.debug)

        // Should FAIL if no value provided
        assertFailsWith<ParseError> {
            val args3 = TestArgs()
            args3.parseWithException(arrayOf("--debug"))
        }

        // Should consume ANY value and try to parse it as boolean
        // (This follows the requiresValue=true pattern of other types)
        val args4 = TestArgs()
        args4.parseWithException(arrayOf("--debug", "invalid"))
        assertEquals(true, args4.debug) // parseBooleanLiteral("invalid") == null -> defaults to true
    }

    @Test
    fun testBooleanDefaultBehavior() {
        // Default behavior should remain requiresValue(false) for backward compatibility
        class TestArgs : Arguments() {
            val debug by option("--debug").bool() // No explicit requiresValue call
        }

        // Should work in flag mode (backward compatibility)
        val args1 = TestArgs()
        args1.parseWithException(arrayOf("--debug"))
        assertEquals(true, args1.debug)

        // Should consume valid boolean literals
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--debug", "false"))
        assertEquals(false, args2.debug)
    }
}