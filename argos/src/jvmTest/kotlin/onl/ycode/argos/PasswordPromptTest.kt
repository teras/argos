/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Tests for password prompting functionality.
 * These tests verify that password options work correctly in their current implementation.
 *
 * Current implementation status:
 * - ✅ Password options with explicit values work (--password=value, --password value)
 * - ✅ Required password prompting works when password is missing entirely
 * - ❌ Standalone --password without value (followed by other options) not yet supported
 */
class PasswordPromptTest {

    @Test
    fun testPasswordWithDirectValue() {
        // Test --password value syntax
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password", "direct_value"))

        assertNotNull(args.password)
        assertEquals("direct_value", String(args.password!!))
    }

    @Test
    fun testPasswordWithEqualsValue() {
        // Test --password=value syntax
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=direct_value"))

        assertNotNull(args.password)
        assertEquals("direct_value", String(args.password!!))
    }

    @Test
    fun testPasswordInDomainWithValue() {
        class PasswordArgs : Arguments() {
            val loginDomain by domain("login")
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("login", "--password", "secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
        assertEquals("login", args.selectedDomain())
    }

    @Test
    fun testPasswordWithNonOptionValue() {
        // Test that password options consume the next argument as their value
        // when it's not recognized as an option
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        // --password consumes the next non-option argument
        args.parseWithException(arrayOf("--password", "non-option-value"))

        assertNotNull(args.password)
        assertEquals("non-option-value", String(args.password!!))
    }

    @Test
    fun testPasswordSecurity() {
        // Test that passwords are stored as CharArray for security
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertTrue(args.password is CharArray)

        // Test that we can clear the password for security
        args.password!!.fill('\u0000')
        assertEquals("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", String(args.password!!))
    }
}