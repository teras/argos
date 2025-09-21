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
import java.lang.System

class RequiredPasswordWithEnvTest {

    @Test
    fun requiredPassword_failsWhenNotProvidedAndEnvNotSet() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input().fromEnv("TEST_PASSWORD").required()
        }

        // Ensure environment variable is not set
        System.clearProperty("TEST_PASSWORD")

        val args = PasswordArgs()
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())
        }
    }

    @Test
    fun requiredPassword_succeedsWhenProvidedViaCommandLine() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input().fromEnv("TEST_PASSWORD").required()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password))
    }

    @Test
    fun requiredPassword_succeedsWhenProvidedViaEnvironmentVariable() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input().fromEnv("PATH").required()
        }

        // Use PATH environment variable which is always available
        val args = PasswordArgs()
        args.parseWithException(arrayOf())

        // PATH should be available and satisfy the required constraint
        assertNotNull(args.password)
        assertTrue(args.password.isNotEmpty())

        // Verify it contains typical PATH content (paths separated by : or ;)
        val pathString = String(args.password)
        assertTrue(pathString.contains("/") || pathString.contains("\\"))
    }

    @Test
    fun requiredPassword_commandLineOverridesEnvironmentVariable() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input().fromEnv("TEST_PASSWORD").required()
        }

        // Command line should take precedence over environment variable
        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=cmdLineSecret"))

        assertNotNull(args.password)
        assertEquals("cmdLineSecret", String(args.password))
    }

    @Test
    fun requiredPassword_withRequiredConstraint_failsWhenNotProvided() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input().fromEnv("TEST_PASSWORD").required()
        }

        val args = PasswordArgs()
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())
        }
    }

    @Test
    fun requiredPassword_withRequiredConstraint_succeedsWhenProvided() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input().fromEnv("TEST_PASSWORD").required()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=validSecret"))

        assertNotNull(args.password)
        assertEquals("validSecret", String(args.password))
    }

    @Test
    fun requiredPassword_inDomain_failsWhenDomainActiveButPasswordMissing() {
        class PasswordArgs : Arguments() {
            val loginDomain by domain("login")
            val password by option("--password").input().fromEnv("TEST_PASSWORD").required()
        }

        val args = PasswordArgs()
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("login"))
        }
    }

    @Test
    fun requiredPassword_inDomain_succeedsWhenDomainActiveAndPasswordProvided() {
        class PasswordArgs : Arguments() {
            val loginDomain by domain("login")
            val password by option("--password").input().fromEnv("TEST_PASSWORD").required()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("login", "--password=domainSecret"))

        assertNotNull(args.password)
        assertEquals("domainSecret", String(args.password))
    }

    @Test
    fun requiredPassword_multiplePasswordOptions_bothRequired() {
        class PasswordArgs : Arguments() {
            val userPassword by option("--user-password").input().fromEnv("USER_PASSWORD").required()
            val adminPassword by option("--admin-password").input().fromEnv("ADMIN_PASSWORD").required()
        }

        val args = PasswordArgs()

        // Should fail if only one password is provided
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--user-password=user123"))
        }

        // Should succeed if both passwords are provided
        args.parseWithException(arrayOf("--user-password=user123", "--admin-password=admin456"))

        assertNotNull(args.userPassword)
        assertNotNull(args.adminPassword)
        assertEquals("user123", String(args.userPassword))
        assertEquals("admin456", String(args.adminPassword))
    }

    @Test
    fun requiredPassword_withConstraints_successScenario() {
        class PasswordArgs : Arguments() {
            val dbPassword by option("--db-password").input().fromEnv("DB_PASSWORD").required()
            val apiKey by option("--api-key").fromEnv("API_KEY").required()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--db-password=dbSecret", "--api-key=api123"))

        assertNotNull(args.dbPassword)
        assertNotNull(args.apiKey)
        assertEquals("dbSecret", String(args.dbPassword))
        assertEquals("api123", args.apiKey)
    }

    @Test
    fun passwordSecurity_environmentVariableHandling() {
        // Test that passwords from environment variables are properly handled as CharArray
        class PasswordArgs : Arguments() {
            val password by option("--password").input().fromEnv("SECURE_PASSWORD")
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=testPassword"))

        assertNotNull(args.password)
        assertTrue(args.password is CharArray)

        // Test that we can clear the password for security
        args.password!!.fill('\u0000')
        assertEquals("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", String(args.password!!))
    }
}
