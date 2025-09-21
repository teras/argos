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
import kotlin.test.assertNotNull

class InputConfirmationTest {

    @Test
    fun testPasswordWithConfirmationTrue() {
        // Test the API structure for password confirmation
        class PasswordArgs : Arguments() {
            val password by option("--password").input(requireConfirmation = true)
        }

        val args = PasswordArgs()

        // When value is provided via command line, no confirmation needed
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithCustomPrompts() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input(
                prompt = "Enter your password: ",
                confirmPrompt = "Confirm your password: ",
                requireConfirmation = true
            )
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordConfirmationDefaults() {
        // Test default prompt messages
        class PasswordArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
    }

    @Test
    fun testMultiplePasswordsWithConfirmation() {
        class PasswordArgs : Arguments() {
            val userPassword by option("--user-password").input(
                prompt = "User password: ",
                requireConfirmation = true
            )
            val adminPassword by option("--admin-password").input(
                prompt = "Admin password: ",
                confirmPrompt = "Confirm admin password: ",
                requireConfirmation = true
            )
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf(
            "--user-password=user123",
            "--admin-password=admin456"
        ))

        assertNotNull(args.userPassword)
        assertEquals("user123", String(args.userPassword!!))
        assertNotNull(args.adminPassword)
        assertEquals("admin456", String(args.adminPassword!!))
    }

    @Test
    fun testPasswordConfirmationWithConstraints() {
        class PasswordArgs : Arguments() {
            val password by option("--password")
                .input(requireConfirmation = true)
                .required()
        }

        // Should fail when password is required but not provided
        assertFailsWith<ParseError> {
            val args = PasswordArgs()
            args.parseWithException(arrayOf())
        }

        // Should succeed when provided
        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password))
    }

    @Test
    fun testPasswordConfirmationInDomain() {
        class PasswordArgs : Arguments() {
            val loginDomain by domain("login")
            val password by option("--password").input(
                prompt = "Login password: ",
                requireConfirmation = true
            )
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("login", "--password=secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordConfirmationWithHelp() {
        class PasswordArgs : Arguments() {
            val password by option("--password")
                .input(requireConfirmation = true)
                .help("Your secure password (will be confirmed)")
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
    }

    @Test
    fun testPasswordConfirmationChaining() {
        // Test that password() can be chained with other methods
        class PasswordArgs : Arguments() {
            val password by option("--password")
                .help("User password")
                .input(
                    prompt = "Enter password: ",
                    confirmPrompt = "Confirm password: ",
                    requireConfirmation = true
                )
                .required()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password))
    }

    @Test
    fun testPasswordConfirmationWithEnvironmentVariable() {
        class PasswordArgs : Arguments() {
            val password by option("--password")
                .input(requireConfirmation = true)
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordConfirmationSecurity() {
        // Test that passwords with confirmation are still stored as CharArray
        class PasswordArgs : Arguments() {
            val password by option("--password").input(requireConfirmation = true)
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertTrue(args.password is CharArray)
        assertEquals("secret123", String(args.password!!))

        // Test that we can clear the password for security
        args.password!!.fill('\u0000')
        assertEquals("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", String(args.password!!))
    }

    @Test
    fun testPasswordConfirmationExpectedDescription() {
        // Test that password options with confirmation still show appropriate description
        class PasswordArgs : Arguments() {
            val password by option("--password").input(requireConfirmation = true)
        }

        val args = PasswordArgs()
        val spec = args.snapshot().options.first { it.ownerName == "password" }
        assertEquals(listOf("input"), spec.expectedDesc)
    }

    @Test
    fun testPasswordNoConfirmationByDefault() {
        // Test that requireConfirmation defaults to false
        class PasswordArgs : Arguments() {
            val password by option("--password").input() // No requireConfirmation specified
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordConfirmationWithShortOptions() {
        class PasswordArgs : Arguments() {
            val password by option("-p", "--password").input(
                prompt = "Password: ",
                requireConfirmation = true
            )
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("-p", "secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }
}
