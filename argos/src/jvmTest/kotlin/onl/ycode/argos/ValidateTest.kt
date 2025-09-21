/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidateTest {

    @Test
    fun singleOptionValidation_validValue_succeeds() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val port by option("--port").int()
                .validate("Port must be between 1-65535: got {value}") { it in 1..65535 }
        }

        val args = Args()
        args.parseWithException(arrayOf("--port", "8080"))
        assertEquals(8080, args.port)
    }

    @Test
    fun singleOptionValidation_invalidValue_failsWithCustomMessage() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val port by option("--port").int()
                .validate("Port must be between 1-65535: got {value}") { it in 1..65535 }
        }

        val args = Args()
        val error = assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--port", "99999"))
        }
        assertTrue(error.message!!.contains("Port must be between 1-65535: got 99999"))
    }

    @Test
    fun singleOptionValidation_defaultMessage_worksCorrectly() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val percentage by option("--percentage").int()
                .validate { it in 0..100 }
        }

        val args = Args()
        val error = assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--percentage", "150"))
        }
        assertTrue(error.message!!.contains("Invalid value for --percentage: 150"))
    }

    @Test
    fun singleOptionValidation_templateVariables_replacedCorrectly() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val config by option("--config", "-c")
                .validate("File {value} not found for {option}") { it == "valid.conf" }
        }

        val args = Args()
        val error = assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--config", "invalid.conf"))
        }
        assertTrue(error.message!!.contains("File \"invalid.conf\" not found for --config"))
    }

    @Test
    fun singleOptionValidation_multipleValidators_allMustPass() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val username by option("--username")
                .validate("Username too short: {value} (minimum 3 chars)") { it?.length?.let { len -> len >= 3 } ?: false }
                .validate("Username too long: {value} (maximum 20 chars)") { it?.length?.let { len -> len <= 20 } ?: true }
                .validate("Username contains invalid characters: {value}") { it?.all { c -> c.isLetterOrDigit() || c == '_' } ?: false }
        }

        // Valid username
        val args1 = Args()
        args1.parseWithException(arrayOf("--username", "valid_user"))
        assertEquals("valid_user", args1.username)

        // Too short
        val args2 = Args()
        val error2 = assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--username", "ab"))
        }
        assertTrue(error2.message!!.contains("Username too short: \"ab\" (minimum 3 chars)"))

        // Too long
        val args3 = Args()
        val error3 = assertFailsWith<ParseError> {
            args3.parseWithException(arrayOf("--username", "this_username_is_way_too_long"))
        }
        assertTrue(error3.message!!.contains("Username too long"))

        // Invalid characters
        val args4 = Args()
        val error4 = assertFailsWith<ParseError> {
            args4.parseWithException(arrayOf("--username", "user@name"))
        }
        assertTrue(error4.message!!.contains("Username contains invalid characters"))
    }

    @Test
    fun singleOptionValidation_multipleValidatorsAtOnce_worksCorrectly() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val value by option("--value").int().validate(
                "Value must be positive: {value}" to { it?.let { v -> v > 0 } ?: false },
                "Value must be even: {value}" to { it?.let { v -> v % 2 == 0 } ?: false },
                "Value must be less than 100: {value}" to { it?.let { v -> v < 100 } ?: true }
            )
        }

        // Valid value
        val args1 = Args()
        args1.parseWithException(arrayOf("--value", "42"))
        assertEquals(42, args1.value)

        // Negative value
        val args2 = Args()
        val error2 = assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--value", "-5"))
        }
        assertTrue(error2.message!!.contains("Value must be positive: -5"))

        // Odd value
        val args3 = Args()
        val error3 = assertFailsWith<ParseError> {
            args3.parseWithException(arrayOf("--value", "7"))
        }
        assertTrue(error3.message!!.contains("Value must be even: 7"))
    }

    @Test
    fun singleOptionValidation_nullableValue_onlyValidatesNonNull() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val optionalPort by option("--port").int()
                .validate("Port must be between 1-65535: got {value}") { it in 1..65535 }
        }

        // Null value should not trigger validation
        val args1 = Args()
        args1.parseWithException(arrayOf())
        assertEquals(null, args1.optionalPort)

        // Valid value should pass
        val args2 = Args()
        args2.parseWithException(arrayOf("--port", "8080"))
        assertEquals(8080, args2.optionalPort)

        // Invalid value should fail
        val args3 = Args()
        val error = assertFailsWith<ParseError> {
            args3.parseWithException(arrayOf("--port", "99999"))
        }
        assertTrue(error.message!!.contains("Port must be between 1-65535: got 99999"))
    }

    @Test
    fun listOptionValidation_elementValidation_worksCorrectly() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val ports by option("--port").int().list()
                .validate("Port out of range: {value}") { it in 1..65535 }
        }

        // Valid ports
        val args1 = Args()
        args1.parseWithException(arrayOf("--port", "80", "--port", "443", "--port", "8080"))
        assertEquals(listOf(80, 443, 8080), args1.ports)

        // Invalid port in the middle
        val args2 = Args()
        val error = assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--port", "80", "--port", "99999", "--port", "443"))
        }
        assertTrue(error.message!!.contains("Port out of range: 99999"))
    }

    @Test
    fun listOptionValidation_collectionValidation_worksCorrectly() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val files by option("--file").list()
                .validateCollection("Must specify at least 2 files, got {count}") { it.isEmpty() || it.size >= 2 }
        }

        // Valid: enough files
        val args1 = Args()
        args1.parseWithException(arrayOf("--file", "a.txt", "--file", "b.txt", "--file", "c.txt"))
        assertEquals(listOf("a.txt", "b.txt", "c.txt"), args1.files)

        // Invalid: too few files
        val args2 = Args()
        val error = assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--file", "a.txt"))
        }
        assertTrue(error.message!!.contains("Must specify at least 2 files, got 1"))

        // Valid: empty list (no constraint violation since no files provided)
        val args3 = Args()
        args3.parseWithException(arrayOf())
        assertEquals(emptyList(), args3.files)
    }

    @Test
    fun setOptionValidation_elementValidation_worksCorrectly() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val categories by option("--category").set()
                .validate("Category must be non-empty: {value}") { it.isNotEmpty() }
        }

        // Valid categories
        val args1 = Args()
        args1.parseWithException(arrayOf("--category", "tech", "--category", "news", "--category", "tech"))
        assertEquals(setOf("tech", "news"), args1.categories) // Duplicates removed

        // Invalid empty category
        val args2 = Args()
        val error = assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--category", "tech", "--category", ""))
        }
        assertTrue(error.message!!.contains("Category must be non-empty: \"\""))
    }

    @Test
    fun setOptionValidation_collectionValidation_worksCorrectly() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val tags by option("--tag").set()
                .validateCollection("Must have exactly 2 unique tags, got {count}") { it.size == 2 }
        }

        // Valid: exactly 2 unique tags
        val args1 = Args()
        args1.parseWithException(arrayOf("--tag", "urgent", "--tag", "important"))
        assertEquals(setOf("urgent", "important"), args1.tags)

        // Invalid: too many unique tags
        val args2 = Args()
        val error2 = assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--tag", "urgent", "--tag", "important", "--tag", "critical"))
        }
        assertTrue(error2.message!!.contains("Must have exactly 2 unique tags, got 3"))

        // Valid: duplicates get reduced to 2 unique
        val args3 = Args()
        args3.parseWithException(arrayOf("--tag", "urgent", "--tag", "important", "--tag", "urgent"))
        assertEquals(setOf("urgent", "important"), args3.tags)
    }


    @Test
    fun validation_typeErrorsTakePrecedence_overValidationErrors() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val port by option("--port").int()
                .validate("Port must be valid") { it?.let { p -> p > 0 } ?: false }
        }

        val args = Args()
        val error = assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--port", "not-a-number"))
        }
        // Should get type conversion error, not validation error
        assertTrue(error.message!!.contains("Invalid value"))
        assertTrue(error.message!!.contains("not-a-number"))
        // Should NOT contain our custom validation message
        assertTrue(!error.message!!.contains("Port must be valid"))
    }

    @Test
    fun validation_worksWithPositionals() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val inputFile by positional()
                .validate("Input file must end with .txt: {value}") { it?.endsWith(".txt") == true }
        }

        // Valid file
        val args1 = Args()
        args1.parseWithException(arrayOf("input.txt"))
        assertEquals("input.txt", args1.inputFile)

        // Invalid file extension
        val args2 = Args()
        val error = assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("input.dat"))
        }
        assertTrue(error.message!!.contains("Input file must end with .txt: \"input.dat\""))
    }

    @Test
    fun validation_worksWithDefaultValues() {
        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val timeout by option("--timeout").int().default(30)
                .validate("Timeout must be positive: {value}") { it > 0 }
        }

        // Default value should pass validation
        val args1 = Args()
        args1.parseWithException(arrayOf())
        assertEquals(30, args1.timeout)

        // Override with valid value
        val args2 = Args()
        args2.parseWithException(arrayOf("--timeout", "60"))
        assertEquals(60, args2.timeout)

        // Override with invalid value should fail
        val args3 = Args()
        val error = assertFailsWith<ParseError> {
            args3.parseWithException(arrayOf("--timeout", "-10"))
        }
        assertTrue(error.message!!.contains("Timeout must be positive: -10"))
    }

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    @Test
    fun validation_worksWithEnumTypes() {

        class Args : Arguments(appName = "test", useANSITerminal = false) {
            val logLevel by option("--log-level").enum<LogLevel>()
                .validate("Production systems should not use DEBUG: {value}") { it != LogLevel.DEBUG }
        }

        // Valid enum value
        val args1 = Args()
        args1.parseWithException(arrayOf("--log-level", "INFO"))
        assertEquals(LogLevel.INFO, args1.logLevel)

        // Invalid enum value per validation rule
        val args2 = Args()
        val error = assertFailsWith<ParseError> {
            args2.parseWithException(arrayOf("--log-level", "DEBUG"))
        }
        assertTrue(error.message!!.contains("Production systems should not use DEBUG: DEBUG"))
    }
}
