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

class NumericBoundaryValidationTest {

    @Test
    fun intBoundaryValues_validAndInvalid() {
        // Test integer boundary values systematically
        class IntBoundaryTest : Arguments(
            appName = "int-boundary-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intValue by option("--int").int()
            val file by positional()
        }

        // Test valid boundary values
        val a1 = IntBoundaryTest()
        a1.parseWithException(arrayOf("alpha", "--int", Int.MAX_VALUE.toString(), "f"))
        assertEquals(Int.MAX_VALUE, a1.intValue)

        val a2 = IntBoundaryTest()
        a2.parseWithException(arrayOf("alpha", "--int", Int.MIN_VALUE.toString(), "f"))
        assertEquals(Int.MIN_VALUE, a2.intValue)

        val a3 = IntBoundaryTest()
        a3.parseWithException(arrayOf("alpha", "--int", "0", "f"))
        assertEquals(0, a3.intValue)

        // Test invalid values that exceed Int boundaries
        val a4 = IntBoundaryTest()
        val e4 = assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--int", "9223372036854775807", "f")) // Long.MAX_VALUE
        }
        assertTrue(e4.message!!.contains("Invalid value"), "Should fail with invalid value message")
        assertTrue(e4.message!!.contains("9223372036854775807"), "Should mention the actual value")

        val a5 = IntBoundaryTest()
        val e5 = assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--int", "999999999999999999999", "f")) // Way too large
        }
        assertTrue(e5.message!!.contains("Invalid value"), "Should fail with invalid value message")

        // Test values that look like numbers but aren't valid integers
        val a6 = IntBoundaryTest()
        val e6 = assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("alpha", "--int", "1e5", "f")) // Scientific notation
        }
        assertTrue(e6.message!!.contains("Invalid value '1e5'"), "Scientific notation should be invalid for int")

        // Test that leading plus signs are handled correctly (they should be valid)
        val a7 = IntBoundaryTest()
        a7.parseWithException(arrayOf("alpha", "--int", "+123", "f"))
        assertEquals(123, a7.intValue) // Leading plus should be valid and result in positive number
    }

    @Test
    fun floatBoundaryValues_validAndInvalid() {
        // Test float boundary values systematically
        class FloatBoundaryTest : Arguments(
            appName = "float-boundary-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val floatValue by option("--float").float()
            val file by positional()
        }

        // Test valid boundary values
        val a1 = FloatBoundaryTest()
        a1.parseWithException(arrayOf("alpha", "--float", Float.MAX_VALUE.toString(), "f"))
        assertEquals(Float.MAX_VALUE, a1.floatValue)

        val a2 = FloatBoundaryTest()
        a2.parseWithException(arrayOf("alpha", "--float", Float.MIN_VALUE.toString(), "f"))
        assertEquals(Float.MIN_VALUE, a2.floatValue)

        val a3 = FloatBoundaryTest()
        a3.parseWithException(arrayOf("alpha", "--float", "0.0", "f"))
        assertEquals(0.0f, a3.floatValue)

        // Test special float values
        val a4 = FloatBoundaryTest()
        a4.parseWithException(arrayOf("alpha", "--float", "Infinity", "f"))
        assertEquals(Float.POSITIVE_INFINITY, a4.floatValue)

        val a5 = FloatBoundaryTest()
        a5.parseWithException(arrayOf("alpha", "--float", "-Infinity", "f"))
        assertEquals(Float.NEGATIVE_INFINITY, a5.floatValue)

        val a6 = FloatBoundaryTest()
        a6.parseWithException(arrayOf("alpha", "--float", "NaN", "f"))
        assertTrue(a6.floatValue!!.isNaN(), "NaN should be parsed correctly")

        // Test invalid float formats
        val a7 = FloatBoundaryTest()
        val e7 = assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("alpha", "--float", "1.2.3", "f")) // Multiple decimal points
        }
        assertTrue(e7.message!!.contains("Invalid value '1.2.3'"), "Multiple decimals should be invalid")

        val a8 = FloatBoundaryTest()
        val e8 = assertFailsWith<ParseError> {
            a8.parseWithException(arrayOf("alpha", "--float", "12.34.56.78", "f")) // Many decimal points
        }
        assertTrue(e8.message!!.contains("Invalid value"), "Invalid float format should be rejected")
    }

    @Test
    fun numericConstraintBoundaries_thresholdTesting() {
        // Test numeric constraints at their boundaries
        class NumericConstraintTest : Arguments(
            appName = "numeric-constraint-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val threshold by option("--threshold").int()
            val requiredIfHigh by option("--required-if-high").requireIfValue(::threshold) { it != null && it >= 100 }
            val blockedIfLow by option("--blocked-if-low").conflictsWith(::threshold) // This logic doesn't make sense, but testing
            val file by positional()
        }

        // Test exactly at threshold boundary (100) - should require the dependent option
        val a1 = NumericConstraintTest()
        a1.parseWithException(arrayOf("alpha", "--threshold", "100", "--required-if-high", "provided", "f"))
        assertEquals(100, a1.threshold)
        assertEquals("provided", a1.requiredIfHigh)

        // Test just below threshold (99) - should NOT require the dependent option
        val a2 = NumericConstraintTest()
        a2.parseWithException(arrayOf("alpha", "--threshold", "99", "f"))
        assertEquals(99, a2.threshold)
        assertNull(a2.requiredIfHigh)

        // Test well above threshold (200) - should require the dependent option
        val a3 = NumericConstraintTest()
        a3.parseWithException(arrayOf("alpha", "--threshold", "200", "--required-if-high", "provided", "f"))
        assertEquals(200, a3.threshold)
        assertEquals("provided", a3.requiredIfHigh)

        // Test failure: at threshold but missing required option
        val a4 = NumericConstraintTest()
        val e4 = assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--threshold", "100", "f")) // >= 100 but missing required-if-high
        }
        // The error should be about the missing requirement, not about the threshold value
        assertFalse(e4.message!!.contains("Invalid value '100'"), "Error should not be about the valid threshold value")

        // Test failure: above threshold but missing required option
        val a5 = NumericConstraintTest()
        val e5 = assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--threshold", "150", "f")) // >= 100 but missing required-if-high
        }
        assertFalse(e5.message!!.contains("Invalid value '150'"), "Error should not be about the valid threshold value")
    }

    @Test
    fun collectionSizeBoundaries_listAndSetConstraints() {
        // Test collection size boundaries for constraints
        class CollectionSizeTest : Arguments(
            appName = "collection-size-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val items by option("--item").map { it }.list()
            val requiredIfManyItems by option("--required-if-many").requireIfValue(::items) { it?.size ?: 0 >= 3 }
            val file by positional()
        }

        // Test exactly at boundary (size = 3) - should require dependent option
        val a1 = CollectionSizeTest()
        a1.parseWithException(arrayOf("alpha", "--item", "1", "--item", "2", "--item", "3", "--required-if-many", "yes", "f"))
        assertEquals(listOf("1", "2", "3"), a1.items)
        assertEquals("yes", a1.requiredIfManyItems)

        // Test just below boundary (size = 2) - should NOT require dependent option
        val a2 = CollectionSizeTest()
        a2.parseWithException(arrayOf("alpha", "--item", "1", "--item", "2", "f"))
        assertEquals(listOf("1", "2"), a2.items)
        assertNull(a2.requiredIfManyItems)

        // Test empty list (size = 0) - should NOT require dependent option
        val a3 = CollectionSizeTest()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertEquals(emptyList<String>(), a3.items)
        assertNull(a3.requiredIfManyItems)

        // Test failure: at boundary but missing required option
        val a4 = CollectionSizeTest()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--item", "1", "--item", "2", "--item", "3", "f")) // size = 3 but missing required
        }

        // Test failure: above boundary but missing required option
        val a5 = CollectionSizeTest()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--item", "1", "--item", "2", "--item", "3", "--item", "4", "f")) // size = 4 but missing required
        }
    }

    @Test
    fun negativeNumbers_properValidation() {
        // Test that negative numbers are handled correctly in validation
        class NegativeNumberTest : Arguments(
            appName = "negative-number-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intVal by option("--int").int()
            val floatVal by option("--float").float()
            val positiveRequired by option("--positive-required").int().requireIfValue(::intVal) { it != null && it > 0 }
            val file by positional()
        }

        // Test valid negative integer
        val a1 = NegativeNumberTest()
        a1.parseWithException(arrayOf("alpha", "--int", "-42", "f"))
        assertEquals(-42, a1.intVal)
        assertNull(a1.positiveRequired) // Should not be required for negative value

        // Test valid negative float
        val a2 = NegativeNumberTest()
        a2.parseWithException(arrayOf("alpha", "--float", "-3.14", "f"))
        assertEquals(-3.14f, a2.floatVal)

        // Test positive value requires dependent option
        val a3 = NegativeNumberTest()
        a3.parseWithException(arrayOf("alpha", "--int", "42", "--positive-required", "1", "f"))
        assertEquals(42, a3.intVal)
        assertEquals(1, a3.positiveRequired)

        // Test zero does not require dependent option (since 0 is not > 0)
        val a4 = NegativeNumberTest()
        a4.parseWithException(arrayOf("alpha", "--int", "0", "f"))
        assertEquals(0, a4.intVal)
        assertNull(a4.positiveRequired)

        // Test failure: positive value without required option
        val a5 = NegativeNumberTest()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--int", "1", "f")) // positive but missing positive-required
        }

        // Test edge case: -0 for float (should be treated as zero)
        val a6 = NegativeNumberTest()
        a6.parseWithException(arrayOf("alpha", "--float", "-0.0", "f"))
        assertEquals(-0.0f, a6.floatVal)
    }
}
