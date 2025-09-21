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
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class InputOptionTest {

    @Test
    fun testPasswordWithCommandLineValue() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithSpaceSeparatedValue() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input()
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
            val password by option("--password").input()
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
            val password by option("--password").input()
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
            val password by option("--password").input()
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
            val password by option("-p", "--password").input()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("-p", "secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithClusteredShortOptions() {
        class PasswordArgs : Arguments() {
            val password by option("-p").input()
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
            val password by option("--password").input()
            val dbPass by option("--db-pass").input()
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
            val userPass by option("--user-password").input()
            val adminPass by option("--admin-password").input()
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
            val password by option().input()  // Uses property name with default prefix
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithCustomPrefix() {
        class PasswordArgs : Arguments(defaultLongPrefix = "++") {
            val password by option("++password").input()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("++password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithConstraints() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input().required()
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
            val password by option("--password").input()
        }

        val args = PasswordArgs()
        args.parseWithException(arrayOf("login", "--password=secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testPasswordWithoutValueFails() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input()
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
            val password by option("--password").input().help("User password for authentication")
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
    fun testPasswordExpectedDescription() {
        // Test that password options show appropriate description
        class PasswordArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = PasswordArgs()
        val spec = args.snapshot().options.first { it.ownerName == "password" }
        assertEquals(listOf("input"), spec.expectedDesc)
    }

    @Test
    fun testPasswordHiddenDefaultsToFalse() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = PasswordArgs()
        val config = args.inputConfigs["password"]
        assertNotNull(config)
        assertFalse(config.hidden)
    }

    @Test
    fun testPasswordHiddenOverride() {
        class PasswordArgs : Arguments() {
            val password by option("--password").input(hidden = true)
        }

        val args = PasswordArgs()
        val config = args.inputConfigs["password"]
        assertNotNull(config)
        assertTrue(config.hidden)
    }

    @Test
    fun testInputOptionWithEmptyValue() {
        class InputArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = InputArgs()
        args.parseWithException(arrayOf("--password="))

        assertNotNull(args.password)
        assertEquals("", String(args.password!!))
    }

    @Test
    fun testInputWithSpecialCharacters() {
        class InputArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = InputArgs()
        val specialPassword = "p@ssw0rd!#\$%^&*()"
        args.parseWithException(arrayOf("--password=$specialPassword"))

        assertNotNull(args.password)
        assertEquals(specialPassword, String(args.password!!))
    }

    @Test
    fun testInputWithWhitespace() {
        class InputArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = InputArgs()
        args.parseWithException(arrayOf("--password=pass word with spaces"))

        assertNotNull(args.password)
        assertEquals("pass word with spaces", String(args.password!!))
    }

    @Test
    fun testInputWithUnicodeCharacters() {
        class InputArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = InputArgs()
        val unicodePassword = "пароль密码🔒"
        args.parseWithException(arrayOf("--password=$unicodePassword"))

        assertNotNull(args.password)
        assertEquals(unicodePassword, String(args.password!!))
    }

    @Test
    fun testInputOptionValueSource() {
        class InputArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = InputArgs()

        // Before parsing, should be MISSING
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::password))

        args.parseWithException(arrayOf("--password=secret123"))

        // After providing via command line, should be USER
        assertEquals(ValueSource.USER, args.valueSourceOf(args::password))
    }

    @Test
    fun testInputWithValidation() {
        class InputArgs : Arguments() {
            val password by option("--password")
                .input()
                .validate("Password must be at least 8 characters") {
                    it?.let { String(it).length >= 8 } ?: true
                }
        }

        // Short password should fail
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(arrayOf("--password=short"))
        }

        // Long password should succeed
        val args = InputArgs()
        args.parseWithException(arrayOf("--password=longenough"))
        assertNotNull(args.password)
        assertEquals("longenough", String(args.password!!))
    }

    @Test
    fun testInputCanBeClearedForSecurity() {
        class InputArgs : Arguments() {
            val password by option("--password").input()
            val apiKey by option("--api-key").input()
        }

        val args = InputArgs()
        args.parseWithException(arrayOf("--password=secret123", "--api-key=key456"))

        assertNotNull(args.password)
        assertNotNull(args.apiKey)

        val originalPassword = String(args.password!!)
        val originalApiKey = String(args.apiKey!!)

        assertEquals("secret123", originalPassword)
        assertEquals("key456", originalApiKey)

        // Clear both for security
        args.password!!.fill('\u0000')
        args.apiKey!!.fill('\u0000')

        assertEquals("\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", String(args.password!!))
        assertEquals("\u0000\u0000\u0000\u0000\u0000\u0000", String(args.apiKey!!))
    }

    @Test
    fun testInputInMultipleDomains() {
        class InputArgs : Arguments() {
            val loginDomain by domain("login")
            val adminDomain by domain("admin")
            val password by option("--password").input()
        }

        // Test in login domain
        val args1 = InputArgs()
        args1.parseWithException(arrayOf("login", "--password=loginpass"))
        assertEquals("login", args1.selectedDomain())
        assertNotNull(args1.password)
        assertEquals("loginpass", String(args1.password!!))

        // Test in admin domain
        val args2 = InputArgs()
        args2.parseWithException(arrayOf("admin", "--password=adminpass"))
        assertEquals("admin", args2.selectedDomain())
        assertNotNull(args2.password)
        assertEquals("adminpass", String(args2.password!!))
    }

    @Test
    fun testInputWithCallback() {
        var callbackInvoked = false
        var callbackValue: CharArray? = null

        class InputArgs : Arguments() {
            val password by option("--password")
                .input()
                .onValue { value ->
                    callbackInvoked = true
                    callbackValue = value?.clone()
                }
        }

        val args = InputArgs()
        args.parseWithException(arrayOf("--password=secret123"))

        assertTrue(callbackInvoked)
        assertNotNull(callbackValue)
        assertEquals("secret123", String(callbackValue!!))
    }

    @Test
    fun testInputWithColonSeparator() {
        class InputArgs : Arguments(valueSeparators = setOf('=', ':')) {
            val password by option("--password").input()
        }

        val args = InputArgs()
        args.parseWithException(arrayOf("--password:secret123"))

        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
    }

    @Test
    fun testMultipleInputOptionsRequired() {
        class InputArgs : Arguments() {
            val userPassword by option("--user-password").input().required()
            val adminPassword by option("--admin-password").input().required()
        }

        // Both required - should fail if only one provided
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(arrayOf("--user-password=user123"))
        }

        // Both required - should succeed when both provided
        val args = InputArgs()
        args.parseWithException(arrayOf("--user-password=user123", "--admin-password=admin456"))
        assertNotNull(args.userPassword)
        assertNotNull(args.adminPassword)
        assertEquals("user123", String(args.userPassword))
        assertEquals("admin456", String(args.adminPassword))
    }

    @Test
    fun testMultipleInputInvocationsFails() {
        // Test that providing the same input option multiple times fails (not repeatable)
        class InputArgs : Arguments() {
            val password by option("--password").input()
        }

        val args = InputArgs()
        // Input options are not repeatable - providing multiple times should fail
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--password=first", "--password=second", "--password=third"))
        }
    }

    @Test
    fun testInputWithOtherOptions() {
        class InputArgs : Arguments() {
            val username by option("--username")
            val password by option("--password").input()
            val verbose by option("--verbose").bool()
            val port by option("--port").int()
        }

        val args = InputArgs()
        args.parseWithException(arrayOf(
            "--username=testuser",
            "--password=secret123",
            "--verbose",
            "--port=8080"
        ))

        assertEquals("testuser", args.username)
        assertNotNull(args.password)
        assertEquals("secret123", String(args.password!!))
        assertEquals(true, args.verbose)
        assertEquals(8080, args.port)
    }

    @Test
    fun testInputWithConflicts() {
        class InputArgs : Arguments() {
            val password by option("--password").input()
            val noAuth by option("--no-auth").bool().conflictsWith(::password)
        }

        // Providing both should fail
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(arrayOf("--password=secret", "--no-auth"))
        }

        // Providing just password should succeed
        val args1 = InputArgs()
        args1.parseWithException(arrayOf("--password=secret"))
        assertNotNull(args1.password)

        // Providing just no-auth should succeed
        val args2 = InputArgs()
        args2.parseWithException(arrayOf("--no-auth"))
        assertEquals(true, args2.noAuth)
    }

    @Test
    fun testInputRequireIfAnyPresent() {
        class InputArgs : Arguments() {
            val username by option("--username")
            val password by option("--password").input().requireIfAnyPresent(::username)
        }

        // Username present but password missing should fail
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(arrayOf("--username=testuser"))
        }

        // Both present should succeed
        val args = InputArgs()
        args.parseWithException(arrayOf("--username=testuser", "--password=secret"))
        assertNotNull(args.password)
        assertEquals("secret", String(args.password!!))
    }

    @Test
    fun testInputExactlyOneWith() {
        class InputArgs : Arguments() {
            val password by option("--password").input().exactlyOneWith(::apiKey)
            val apiKey by option("--api-key").input()
        }

        // Neither provided should fail
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(emptyArray())
        }

        // Both provided should fail
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(arrayOf("--password=secret", "--api-key=key123"))
        }

        // Just password should succeed
        val args1 = InputArgs()
        args1.parseWithException(arrayOf("--password=secret"))
        assertNotNull(args1.password)

        // Just api-key should succeed
        val args2 = InputArgs()
        args2.parseWithException(arrayOf("--api-key=key123"))
        assertNotNull(args2.apiKey)
    }

    @Test
    fun testInputWithHiddenOption() {
        class InputArgs : Arguments() {
            val password by option("--password").input().hidden()
        }

        val args = InputArgs()
        args.parseWithException(arrayOf("--password=secret"))

        // Verify option is marked as hidden
        val spec = args.snapshot().options.first { it.ownerName == "password" }
        assertTrue(spec.hidden)

        assertNotNull(args.password)
        assertEquals("secret", String(args.password!!))
    }

    @Test
    fun testInputWithLongValue() {
        class InputArgs : Arguments() {
            val token by option("--token").input()
        }

        val args = InputArgs()
        val longToken = "a".repeat(1000)
        args.parseWithException(arrayOf("--token=$longToken"))

        assertNotNull(args.token)
        assertEquals(longToken, String(args.token!!))
    }

    @Test
    fun testInputWithNewlineCharacters() {
        class InputArgs : Arguments() {
            val text by option("--text").input()
        }

        val args = InputArgs()
        val textWithNewlines = "line1\nline2\nline3"
        args.parseWithException(arrayOf("--text=$textWithNewlines"))

        assertNotNull(args.text)
        assertEquals(textWithNewlines, String(args.text!!))
    }

    @Test
    fun testInputWithTabCharacters() {
        class InputArgs : Arguments() {
            val text by option("--text").input()
        }

        val args = InputArgs()
        val textWithTabs = "col1\tcol2\tcol3"
        args.parseWithException(arrayOf("--text=$textWithTabs"))

        assertNotNull(args.text)
        assertEquals(textWithTabs, String(args.text!!))
    }

    @Test
    fun testInputMultipleValidations() {
        class InputArgs : Arguments() {
            val password by option("--password")
                .input()
                .validate("Must be at least 8 characters") { it?.let { String(it).length >= 8 } ?: true }
                .validate("Must contain a digit") { it?.let { String(it).any { c -> c.isDigit() } } ?: true }
                .validate("Must contain a special char") { it?.let { String(it).any { c -> !c.isLetterOrDigit() } } ?: true }
        }

        // Should fail on first validation
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(arrayOf("--password=short"))
        }

        // Should fail on second validation
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(arrayOf("--password=longpassword"))
        }

        // Should fail on third validation
        assertFailsWith<ParseError> {
            val args = InputArgs()
            args.parseWithException(arrayOf("--password=longpass123"))
        }

        // Should succeed with all validations
        val args = InputArgs()
        args.parseWithException(arrayOf("--password=longpass123!"))
        assertNotNull(args.password)
        assertEquals("longpass123!", String(args.password!!))
    }

    @Test
    fun testInputFromEnvironmentVariable() {
        val envVarName = "TEST_INPUT_ENV_${System.currentTimeMillis()}"
        try {
            // Set environment variable (note: this doesn't actually work in JVM tests
            // because environment is immutable, but we can test the API)
            class InputArgs : Arguments() {
                val apiKey by option("--api-key").fromEnv(envVarName).input()
            }

            // When not provided via CLI or env, should be null
            val args = InputArgs()
            args.parseWithException(emptyArray())
            assertNull(args.apiKey)
        } finally {
            // Cleanup not needed since we can't actually set env vars
        }
    }

    @Test
    fun testInputWithFromEnvChaining() {
        // Test that fromEnv() can be chained before input()
        class InputArgs : Arguments() {
            val token by option("--token").fromEnv("MY_TOKEN").input().required()
        }

        // Should work with command-line value
        val args = InputArgs()
        args.parseWithException(arrayOf("--token=cmdline-token"))
        assertNotNull(args.token)
        assertEquals("cmdline-token", String(args.token))
    }

    @Test
    fun testInputChainingOrderFlexibility() {
        // Test that input() can be chained in different orders
        class InputArgs : Arguments() {
            // input() then help()
            val pass1 by option("--pass1").input().help("First password")

            // help() then input()
            val pass2 by option("--pass2").help("Second password").input()

            // input() then required()
            val pass3 by option("--pass3").input().required()
        }

        val args = InputArgs()
        args.parseWithException(arrayOf("--pass1=p1", "--pass2=p2", "--pass3=p3"))

        assertEquals("p1", String(args.pass1!!))
        assertEquals("p2", String(args.pass2!!))
        assertEquals("p3", String(args.pass3))
    }
}
