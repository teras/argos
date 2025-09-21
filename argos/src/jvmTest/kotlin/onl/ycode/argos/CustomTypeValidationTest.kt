/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class CustomTypeValidationTest {

    @Test
    fun bigDecimalValidation_invalidValues_causeErrors() {
        // Test BigDecimal conversion with nullable and default scenarios
        class BigDecimalTest : Arguments(
            appName = "bigdecimal-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val nullableBigDecimal by option("--nullable-decimal").map { BigDecimal(it) }
            val defaultBigDecimal by option("--default-decimal").map { BigDecimal(it) }.default(BigDecimal("100.00"))
            val file by positional()
        }

        // Invalid BigDecimal - nullable
        val a1 = BigDecimalTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--nullable-decimal", "not-a-decimal", "f"))
        }
        assertTrue(e1.message!!.contains("not-a-decimal"))

        // Invalid BigDecimal - with default
        val a2 = BigDecimalTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--default-decimal", "invalid.decimal.format", "f"))
        }
        assertTrue(e2.message!!.contains("invalid.decimal.format"))

        // Valid cases should work
        val a3 = BigDecimalTest()
        a3.parseWithException(arrayOf("alpha", "--nullable-decimal", "123.456", "f"))
        assertEquals(BigDecimal("123.456"), a3.nullableBigDecimal)
        assertEquals(BigDecimal("100.00"), a3.defaultBigDecimal)

        // Default value used when not provided
        val a4 = BigDecimalTest()
        a4.parseWithException(arrayOf("alpha", "f"))
        assertNull(a4.nullableBigDecimal)
        assertEquals(BigDecimal("100.00"), a4.defaultBigDecimal)

        // Override default with valid value
        val a5 = BigDecimalTest()
        a5.parseWithException(arrayOf("alpha", "--default-decimal", "999.99", "f"))
        assertEquals(BigDecimal("999.99"), a5.defaultBigDecimal)
    }

    @Test
    fun bigIntegerValidation_invalidValues_causeErrors() {
        // Test BigInteger conversion with various scenarios
        class BigIntegerTest : Arguments(
            appName = "biginteger-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val nullableBigInt by option("--nullable-bigint").map { BigInteger(it) }
            val defaultBigInt by option("--default-bigint").map { BigInteger(it) }.default(BigInteger("12345678901234567890"))
            val bigIntList by option("--bigint-list").map { BigInteger(it) }.list()
            val file by positional()
        }

        // Invalid BigInteger - nullable
        val a1 = BigIntegerTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--nullable-bigint", "not-an-integer", "f"))
        }
        assertTrue(e1.message!!.contains("not-an-integer"))

        // Invalid BigInteger - with default
        val a2 = BigIntegerTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--default-bigint", "12.34", "f"))
        }
        assertTrue(e2.message!!.contains("12.34"))

        // Invalid BigInteger in list
        val a3 = BigIntegerTest()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--bigint-list", "123", "--bigint-list", "abc", "--bigint-list", "456", "f"))
        }
        assertTrue(e3.message!!.contains("abc"))

        // Valid very large numbers should work
        val a4 = BigIntegerTest()
        a4.parseWithException(arrayOf("alpha",
            "--nullable-bigint", "999999999999999999999999999999999999999999",
            "--bigint-list", "111111111111111111111111111111111111111111",
            "--bigint-list", "222222222222222222222222222222222222222222",
            "f"))
        assertEquals(BigInteger("999999999999999999999999999999999999999999"), a4.nullableBigInt)
        assertEquals(BigInteger("12345678901234567890"), a4.defaultBigInt) // default used
        assertEquals(listOf(
            BigInteger("111111111111111111111111111111111111111111"),
            BigInteger("222222222222222222222222222222222222222222")
        ), a4.bigIntList)
    }

    @Test
    fun customValidationWithExceptionThrowingMap() {
        // Test custom validation that actually throws exceptions
        class CustomValidationTest : Arguments(
            appName = "custom-validation-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")

            // Custom validation that throws for negative numbers
            val positiveInt by option("--positive-int").map { it ->
                val value = it!!.toInt()
                if (value <= 0) throw IllegalArgumentException("Must be positive")
                value
            }

            // Custom validation for files that must exist (will throw if not found)
            val existingFile by option("--existing-file").map {
                val file = File(it)
                if (!file.exists()) throw IllegalArgumentException("File does not exist: $it")
                file
            }.default(File(System.getProperty("java.io.tmpdir"))) // Default to temp dir which should exist

            // Custom validation with list
            val positiveIntList by option("--positive-list").map { it ->
                val value = it!!.toInt()
                if (value <= 0) throw IllegalArgumentException("All values must be positive")
                value
            }.list()

            val file by positional()
        }

        // Test negative number validation
        val a1 = CustomValidationTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--positive-int", "-5", "f"))
        }
        assertTrue(e1.message!!.contains("Must be positive") || e1.message!!.contains("-5"))

        // Test zero validation
        val a2 = CustomValidationTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--positive-int", "0", "f"))
        }
        assertTrue(e2.message!!.contains("Must be positive") || e2.message!!.contains("0"))

        // Test non-existent file validation
        val a3 = CustomValidationTest()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--existing-file", "/definitely/does/not/exist.txt", "f"))
        }
        assertTrue(e3.message!!.contains("File does not exist") || e3.message!!.contains("/definitely/does/not/exist.txt"))

        // Test invalid value in list
        val a4 = CustomValidationTest()
        val e4 = assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--positive-list", "5", "--positive-list", "-2", "--positive-list", "10", "f"))
        }
        assertTrue(e4.message!!.contains("All values must be positive") || e4.message!!.contains("-2"))

        // Valid cases should work
        val a5 = CustomValidationTest()
        a5.parseWithException(arrayOf("alpha", "--positive-int", "42", "--positive-list", "1", "--positive-list", "2", "f"))
        assertEquals(42, a5.positiveInt)
        assertEquals(listOf(1, 2), a5.positiveIntList)
        assertEquals(File(System.getProperty("java.io.tmpdir")), a5.existingFile) // default used

        // Test default value is used when not provided
        val a6 = CustomValidationTest()
        a6.parseWithException(arrayOf("alpha", "f"))
        assertNull(a6.positiveInt)
        assertEquals(File(System.getProperty("java.io.tmpdir")), a6.existingFile)
        assertEquals(emptyList<Int>(), a6.positiveIntList)
    }

    @Test
    fun customTypeWithConstraints_typeErrorsBeforeConstraints() {
        // Test that custom type conversion errors occur before constraint validation
        class CustomTypeConstraints : Arguments(
            appName = "custom-type-constraints",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val bigDecimalTrigger by option("--decimal-trigger").map { BigDecimal(it) }.atMostOneWith(::bigIntTrigger)
            val bigIntTrigger by option("--bigint-trigger").map { BigInteger(it) }
            val requiredFile by option("--required-file").map { File(it) }.requireIfAnyPresent(::bigDecimalTrigger, ::bigIntTrigger)
            val file by positional()
        }

        // Invalid BigDecimal should fail before constraint validation
        val a1 = CustomTypeConstraints()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--decimal-trigger", "invalid-decimal", "--required-file", "/tmp/test", "f"))
        }
        assertTrue(e1.message!!.contains("invalid-decimal"))
        // Should not mention constraint errors

        // Invalid BigInteger should fail before constraint validation
        val a2 = CustomTypeConstraints()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--bigint-trigger", "not-integer", "--required-file", "/tmp/test", "f"))
        }
        assertTrue(e2.message!!.contains("not-integer"))

        // Valid types should trigger constraint validation
        val a3 = CustomTypeConstraints()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--decimal-trigger", "123.45", "f")) // missing required-file
        }
        // This should be a constraint error, not a type error
        assertFalse(e3.message!!.contains("Invalid value"))

        // Valid case with all constraints satisfied
        val a4 = CustomTypeConstraints()
        a4.parseWithException(arrayOf("alpha", "--decimal-trigger", "123.45", "--required-file", "/tmp/test.txt", "f"))
        assertEquals(BigDecimal("123.45"), a4.bigDecimalTrigger)
        assertNull(a4.bigIntTrigger)
        assertEquals(File("/tmp/test.txt"), a4.requiredFile)
    }

    @Test
    fun mixedCustomAndBuiltinTypes_complexConstraints() {
        // Test mixing custom types with built-in types in complex constraint scenarios
        class MixedTypeConstraints : Arguments(
            appName = "mixed-type-constraints",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")

            // Mix of custom and built-in types
            val intValue by option("--int-value").int().exactlyOneWith(::bigDecimalValue)
            val bigDecimalValue by option("--decimal-value").map { BigDecimal(it) }
            val fileList by option("--file-list").map { File(it) }.list().atLeastOneWith(::stringList)
            val stringList by option("--string-list").map { it }.list()
            val bigIntSet by option("--bigint-set").map { BigInteger(it) }.set().conflictsWith(::intValue)

            val file by positional()
        }

        // Test valid combination: int + file list
        val a1 = MixedTypeConstraints()
        a1.parseWithException(arrayOf("alpha",
            "--int-value", "42",
            "--file-list", "/tmp/file1.txt",
            "--file-list", "/tmp/file2.txt",
            "f"))
        assertEquals(42, a1.intValue)
        assertNull(a1.bigDecimalValue)
        assertEquals(listOf(File("/tmp/file1.txt"), File("/tmp/file2.txt")), a1.fileList)
        assertEquals(emptyList<String>(), a1.stringList)
        assertEquals(emptySet<BigInteger>(), a1.bigIntSet)

        // Test valid combination: bigDecimal + string list
        val a2 = MixedTypeConstraints()
        a2.parseWithException(arrayOf("alpha",
            "--decimal-value", "123.456",
            "--string-list", "item1",
            "--string-list", "item2",
            "f"))
        assertNull(a2.intValue)
        assertEquals(BigDecimal("123.456"), a2.bigDecimalValue)
        assertEquals(emptyList<File>(), a2.fileList)
        assertEquals(listOf("item1", "item2"), a2.stringList)
        assertEquals(emptySet<BigInteger>(), a2.bigIntSet)

        // Test invalid BigDecimal should fail before constraint check
        val a3 = MixedTypeConstraints()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--decimal-value", "invalid.decimal", "--string-list", "item", "f"))
        }
        assertTrue(e3.message!!.contains("invalid.decimal"))

        // Test constraint violation: bigint-set conflicts with int-value
        val a4 = MixedTypeConstraints()
        val e4 = assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha",
                "--int-value", "42",
                "--bigint-set", "999999999999999999999999999999",
                "--file-list", "/tmp/test.txt",
                "f"))
        }
        // This should be a constraint error (conflict), not a type error
        assertFalse(e4.message!!.contains("Invalid value"))

        // Test constraint violation: missing atLeastOneWith requirement
        val a5 = MixedTypeConstraints()
        val e5 = assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "f")) // no file-list or string-list provided
        }
        // Should be constraint error about atLeastOneWith
        assertFalse(e5.message!!.contains("Invalid value"))
    }

    @Test
    fun customTypeEdgeCases_specialValues() {
        // Test edge cases with custom type conversions
        class CustomTypeEdgeCases : Arguments(
            appName = "custom-edge-cases",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val scientificDecimal by option("--scientific-decimal").map { BigDecimal(it) }
            val negativeHexBigInt by option("--hex-bigint").map { BigInteger(it, 16) } // Base 16
            val relativeFile by option("--relative-file").map { File(it) }
            val file by positional()
        }

        // Test scientific notation in BigDecimal (should work)
        val a1 = CustomTypeEdgeCases()
        a1.parseWithException(arrayOf("alpha", "--scientific-decimal", "1.23E+10", "f"))
        assertEquals(BigDecimal("1.23E+10"), a1.scientificDecimal)

        // Test hexadecimal BigInteger (should work)
        val a2 = CustomTypeEdgeCases()
        a2.parseWithException(arrayOf("alpha", "--hex-bigint", "DEADBEEF", "f"))
        assertEquals(BigInteger("DEADBEEF", 16), a2.negativeHexBigInt)

        // Test invalid hex BigInteger
        val a3 = CustomTypeEdgeCases()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--hex-bigint", "INVALID_HEX_WITH_UNDERSCORE", "f"))
        }
        assertTrue(e3.message!!.contains("INVALID_HEX_WITH_UNDERSCORE"))

        // Test relative file paths (should work)
        val a4 = CustomTypeEdgeCases()
        a4.parseWithException(arrayOf("alpha", "--relative-file", "../test/file.txt", "f"))
        assertEquals(File("../test/file.txt"), a4.relativeFile)

        // Test current directory reference
        val a5 = CustomTypeEdgeCases()
        a5.parseWithException(arrayOf("alpha", "--relative-file", "./current.txt", "f"))
        assertEquals(File("./current.txt"), a5.relativeFile)
    }
}
