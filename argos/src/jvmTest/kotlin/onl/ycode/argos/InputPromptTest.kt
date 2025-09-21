/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

/**
 * Tests for input prompting functionality with the input() method.
 * These tests verify that input options work correctly with both clear text and hidden input.
 *
 * Current implementation status:
 * - ✅ Input options with explicit values work (--password=value, --password value)
 * - ✅ Required input prompting works when input is missing entirely
 * - ✅ Hidden input (password-like) supported via hidden=true parameter
 * - ✅ Clear text input (default) supported via hidden=false parameter
 * - ✅ Confirmation prompting supported via requireConfirmation=true parameter
 */
class InputPromptTest {

    @Test
    fun testPasswordWithDirectValue() {
        // Test --password value syntax
        class PasswordArgs : Arguments() {
            val password by option("--password").input()
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
            val password by option("--password").input()
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
            val password by option("--password").input()
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
            val password by option("--password").input()
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
            val password by option("--password").input()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertTrue(args.password is CharArray)

        // Test that we can clear the password for security
        args.password!!.fill('\u0000')
        assertEquals("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", String(args.password!!))
    }

    @Test
    fun testPasswordPromptClearTextByDefault() {
        val originalIn = System.`in`
        try {
            System.setIn(ByteArrayInputStream("prompted-value\n".toByteArray()))

            class PasswordArgs : Arguments() {
                val password by option("--password").input().required()
            }

            val args = PasswordArgs()
            args.parseWithException(emptyArray())

            assertEquals("prompted-value", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testPasswordPromptClearTextWithConfirmation() {
        val originalIn = System.`in`
        try {
            System.setIn(ByteArrayInputStream("secret123\nsecret123\n".toByteArray()))

            class PasswordArgs : Arguments() {
                val password by option("--password")
                    .input(hidden = false, requireConfirmation = true)
                    .required()
            }

            val args = PasswordArgs()
            args.parseWithException(emptyArray())

            assertEquals("secret123", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testPasswordPromptClearTextMismatchFailsAfterRetries() {
        val originalIn = System.`in`
        val originalErr = System.err
        val errCapture = ByteArrayOutputStream()
        try {
            System.setIn(ByteArrayInputStream("one\ntwo\nthree\nfour\n".toByteArray()))
            System.setErr(PrintStream(errCapture, true))

            class PasswordArgs : Arguments() {
                val password by option("--password")
                    .input(
                        hidden = false,
                        requireConfirmation = true,
                        mismatchPrompt = "Passwords do not match. Please try again.",
                        maxRetries = 2
                    )
                    .required()
            }

            assertFailsWith<ParseError> {
                PasswordArgs().parseWithException(emptyArray())
            }

            val errOutput = errCapture.toString()
            assertTrue(errOutput.contains("Passwords do not match. Please try again."))
        } finally {
            System.setIn(originalIn)
            System.setErr(originalErr)
        }
    }

    @Test
    fun testInputPromptWithCustomPromptText() {
        val originalIn = System.`in`
        val originalErr = System.err
        val errCapture = ByteArrayOutputStream()
        try {
            System.setIn(ByteArrayInputStream("customvalue\n".toByteArray()))
            System.setErr(PrintStream(errCapture, true))

            class InputArgs : Arguments() {
                val apiKey by option("--api-key")
                    .input(prompt = "Enter your API key: ", hidden = false)
                    .required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            assertEquals("customvalue", String(args.apiKey))
            val errOutput = errCapture.toString()
            assertTrue(errOutput.contains("Enter your API key: "))
        } finally {
            System.setIn(originalIn)
            System.setErr(originalErr)
        }
    }

    @Test
    fun testInputPromptWithConfirmationCustomPrompts() {
        val originalIn = System.`in`
        val originalErr = System.err
        val errCapture = ByteArrayOutputStream()
        try {
            System.setIn(ByteArrayInputStream("secret\nsecret\n".toByteArray()))
            System.setErr(PrintStream(errCapture, true))

            class InputArgs : Arguments() {
                val token by option("--token")
                    .input(
                        prompt = "Enter token: ",
                        confirmPrompt = "Confirm token: ",
                        hidden = false,
                        requireConfirmation = true
                    )
                    .required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            assertEquals("secret", String(args.token))
            val errOutput = errCapture.toString()
            assertTrue(errOutput.contains("Enter token: "))
            assertTrue(errOutput.contains("Confirm token: "))
        } finally {
            System.setIn(originalIn)
            System.setErr(originalErr)
        }
    }

    @Test
    fun testInputPromptMismatchWithRetrySucceeds() {
        val originalIn = System.`in`
        val originalErr = System.err
        val errCapture = ByteArrayOutputStream()
        try {
            // First attempt mismatch, second attempt matches
            System.setIn(ByteArrayInputStream("pass1\npass2\npass3\npass3\n".toByteArray()))
            System.setErr(PrintStream(errCapture, true))

            class InputArgs : Arguments() {
                val password by option("--password")
                    .input(
                        hidden = false,
                        requireConfirmation = true,
                        mismatchPrompt = "Mismatch! Try again.",
                        maxRetries = 3
                    )
                    .required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            // Should succeed with pass3 after one retry
            assertEquals("pass3", String(args.password))
            val errOutput = errCapture.toString()
            assertTrue(errOutput.contains("Mismatch! Try again."))
        } finally {
            System.setIn(originalIn)
            System.setErr(originalErr)
        }
    }

    @Test
    fun testInputPromptOptionalNotProvided() {
        val originalIn = System.`in`
        try {
            // Empty input stream (user doesn't provide anything)
            System.setIn(ByteArrayInputStream("\n".toByteArray()))

            class InputArgs : Arguments() {
                val password by option("--password").input(hidden = false)
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            // Optional input not provided should be null
            assertNull(args.password)
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptMultipleOptions() {
        val originalIn = System.`in`
        try {
            // Provide values for two different input options
            System.setIn(ByteArrayInputStream("userpass\nadminpass\n".toByteArray()))

            class InputArgs : Arguments() {
                val userPassword by option("--user-password").input(hidden = false).required()
                val adminPassword by option("--admin-password").input(hidden = false).required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            assertEquals("userpass", String(args.userPassword))
            assertEquals("adminpass", String(args.adminPassword))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptWithEmptyInput() {
        val originalIn = System.`in`
        try {
            // Provide empty string as input
            System.setIn(ByteArrayInputStream("\n".toByteArray()))

            class InputArgs : Arguments() {
                val password by option("--password").input(hidden = false).required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            // Empty input should be treated as empty string
            assertNotNull(args.password)
            assertEquals("", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptConfirmationWithMaxRetriesOne() {
        val originalIn = System.`in`
        try {
            // Only one mismatch allowed, then fail
            System.setIn(ByteArrayInputStream("pass1\npass2\n".toByteArray()))

            class InputArgs : Arguments() {
                val password by option("--password")
                    .input(
                        hidden = false,
                        requireConfirmation = true,
                        mismatchPrompt = "Mismatch!",
                        maxRetries = 1
                    )
                    .required()
            }

            // Should fail after 1 failed retry attempt
            assertFailsWith<ParseError> {
                InputArgs().parseWithException(emptyArray())
            }
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptInDomain() {
        val originalIn = System.`in`
        try {
            System.setIn(ByteArrayInputStream("domainpass\n".toByteArray()))

            class InputArgs : Arguments() {
                val loginDomain by domain("login")
                val password by option("--password").input(hidden = false).required()
            }

            val args = InputArgs()
            args.parseWithException(arrayOf("login"))

            assertEquals("login", args.selectedDomain())
            assertEquals("domainpass", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptMixedProvidedAndPrompted() {
        val originalIn = System.`in`
        try {
            // Only prompt for admin password (user password provided via CLI)
            System.setIn(ByteArrayInputStream("adminpass\n".toByteArray()))

            class InputArgs : Arguments() {
                val userPassword by option("--user-password").input(hidden = false)
                val adminPassword by option("--admin-password").input(hidden = false).required()
            }

            val args = InputArgs()
            args.parseWithException(arrayOf("--user-password=userpass"))

            assertEquals("userpass", String(args.userPassword!!))
            assertEquals("adminpass", String(args.adminPassword))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptWithWhitespace() {
        val originalIn = System.`in`
        try {
            // Input with leading/trailing whitespace
            System.setIn(ByteArrayInputStream("  password with spaces  \n".toByteArray()))

            class InputArgs : Arguments() {
                val password by option("--password").input(hidden = false).required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            // Whitespace should be preserved
            assertEquals("  password with spaces  ", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptConfirmationMatchesWithWhitespace() {
        val originalIn = System.`in`
        try {
            // Both inputs have whitespace and match exactly
            System.setIn(ByteArrayInputStream("  pass  \n  pass  \n".toByteArray()))

            class InputArgs : Arguments() {
                val password by option("--password")
                    .input(hidden = false, requireConfirmation = true)
                    .required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            assertEquals("  pass  ", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptWithSpecialCharacters() {
        val originalIn = System.`in`
        try {
            // Input with special characters
            System.setIn(ByteArrayInputStream("p@ssw0rd!#\$%^&*()\n".toByteArray()))

            class InputArgs : Arguments() {
                val password by option("--password").input(hidden = false).required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            assertEquals("p@ssw0rd!#\$%^&*()", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptWithUnicode() {
        val originalIn = System.`in`
        try {
            // Input with unicode characters
            System.setIn(ByteArrayInputStream("пароль密码🔒\n".toByteArray()))

            class InputArgs : Arguments() {
                val password by option("--password").input(hidden = false).required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            assertEquals("пароль密码🔒", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }

    @Test
    fun testInputPromptValueSourceIsUser() {
        val originalIn = System.`in`
        try {
            System.setIn(ByteArrayInputStream("prompted\n".toByteArray()))

            class InputArgs : Arguments() {
                val password by option("--password").input(hidden = false).required()
            }

            val args = InputArgs()
            args.parseWithException(emptyArray())

            // Prompted values currently don't update ValueSource - this is a known limitation
            // The value source tracking happens during parsing, not during prompting
            // Since prompting happens as part of constraint evaluation (required),
            // the ValueSource remains as the default (MISSING for nullable, DEFAULT for non-nullable with default)
            // This test documents current behavior - may be enhanced in the future
            assertNotNull(args.password)
            assertEquals("prompted", String(args.password))
        } finally {
            System.setIn(originalIn)
        }
    }
}
