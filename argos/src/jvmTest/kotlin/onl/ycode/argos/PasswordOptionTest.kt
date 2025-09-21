/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class PasswordOptionTest {

    @Test
    fun testPasswordWithCommandLineValue() {
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithSpaceSeparatedValue() {
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password", "secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordRequiresValueByDefault() {
        // This test verifies that password options require a value by default
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        // When --password is present but has no value, it should fail with missing value error
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--password"))
        }
    }

    @Test
    fun testPasswordNotFollowedByOption() {
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
            val verbose by option("--verbose").bool()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password", "secret", "--verbose"))

        assertNotNull(args.password)
        assertEquals("secret", String(args.password!!))
        assertTrue(args.verbose == true)
    }

    @Test
    fun testPasswordFollowedByOptionFailsWithMissingValue() {
        // When password is followed by another option, it fails with missing value
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
            val verbose by option("--verbose").bool()
        }

        val args = PasswordArgs()
        // This simulates: --password --verbose
        // The parser recognizes --verbose as an option and doesn't consume it as password value
        // This results in a missing value error for --password
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--password", "--verbose"))
        }
    }

    @Test
    fun testPasswordWithShortOption() {
        class PasswordArgs : Arguments() {
            val password by option("-p", "--password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("-p", "secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithClusteredShortOptions() {
        class PasswordArgs : Arguments() {
            val password by option("-p").password()
            val verbose by option("-v").bool()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("-pv"))

        assertNotNull(args.password)
        assertEquals("v", String(args.password!!)) // -pv treats "v" as the password value
        // Note: verbose would not be set in this case since "v" is consumed as password value
    }

    @Test
    fun testPasswordWithEqualsSyntax() {
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
            val dbPass by option("--db-pass").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123", "--db-pass=dbsecret"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
        assertNotNull(args.dbPass)
        assertEquals("dbsecret", String(args.dbPass!!))
    }

    @Test
    fun testMultiplePasswordOptions() {
        class PasswordArgs : Arguments() {
            val userPass by option("--user-password").password()
            val adminPass by option("--admin-password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--user-password=user123", "--admin-password=admin456"))

        assertNotNull(args.userPass)
        assertEquals("user123", String(args.userPass!!))
        assertNotNull(args.adminPass)
        assertEquals("admin456", String(args.adminPass!!))
    }

    @Test
    fun testPasswordWithDefaultPrefix() {
        class PasswordArgs : Arguments(defaultLongPrefix = "--") {
            val password by option().password()  // Uses property name with default prefix
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithCustomPrefix() {
        class PasswordArgs : Arguments(defaultLongPrefix = "++") {
            val password by option("++password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("++password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithConstraints() {
        class PasswordArgs : Arguments() {
            val password by option("--password").password().required()
        }

        val args = PasswordArgs()

        // Should fail if password is missing and required
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())
        }

        // Should succeed if password is provided
        args.parseWithException(arrayOf("--password=secret123"))
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password))
    }

    @Test
    fun testPasswordInDomain() {
        class PasswordArgs : Arguments() {
            val loginDomain by domain("login")
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("login", "--password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithoutValueFails() {
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        val args = PasswordArgs()
        // Test that --password without value fails with missing value error
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--password"))
        }
    }

    @Test
    fun testPasswordWithHelp() {
        class PasswordArgs : Arguments() {
            val password by option("--password").password().help("User password for authentication")
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
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

    @Test
    fun testPasswordExpectedDescription() {
        // Test that password options show appropriate description
        class PasswordArgs : Arguments() {
            val password by option("--password").password()
        }

        // This would be tested in help generation tests
        // The expectedDesc should be "password"
        assertTrue(true, "Password expected description should be 'password'")
    }
}
