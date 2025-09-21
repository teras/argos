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

class TypeValidationErrorTest {

    @Test
    fun intValidation_invalidValues_causeErrors() {
        // Test nullable int with invalid values
        class NullableIntTest : Arguments(
            appName = "nullable-int-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val nullableInt by option("--nullable-int").int()
            val file by positional()
        }

        // Invalid string for nullable int
        val a1 = NullableIntTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--nullable-int", "abc", "f"))
        }
        // Validate specific error content for type validation
        assertTrue(e1.message!!.contains("Invalid value 'abc'"), "Error should mention the invalid value")
        assertTrue(e1.message!!.contains("--nullable-int"), "Error should mention the option name")
        assertTrue(e1.message!!.contains("integer") || e1.message!!.contains("int"), "Error should indicate expected type")

        // Invalid decimal for int
        val a2 = NullableIntTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--nullable-int", "12.34", "f"))
        }
        assertTrue(e2.message!!.contains("Invalid value '12.34'"), "Error should mention the invalid decimal value")
        assertTrue(e2.message!!.contains("--nullable-int"), "Error should mention the option name")

        // Empty string for int
        val a3 = NullableIntTest()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--nullable-int", "", "f"))
        }
        assertTrue(e3.message!!.contains("Invalid value ''"))
    }

    @Test
    fun intValidation_nonNullableWithDefault_invalidValues() {
        // Test non-nullable int with default value and invalid input
        class NonNullableIntWithDefault : Arguments(
            appName = "non-nullable-int-default",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intWithDefault by option("--int-default").int().default(42)
            val file by positional()
        }

        val a1 = NonNullableIntWithDefault()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--int-default", "not-a-number", "f"))
        }
        assertTrue(e1.message!!.contains("Invalid value 'not-a-number'"))
        assertTrue(e1.message!!.contains("--int-default"))

        // Test that valid parsing still works
        val a2 = NonNullableIntWithDefault()
        a2.parseWithException(arrayOf("alpha", "f"))
        assertEquals(42, a2.intWithDefault) // Should use default when not provided

        val a3 = NonNullableIntWithDefault()
        a3.parseWithException(arrayOf("alpha", "--int-default", "99", "f"))
        assertEquals(99, a3.intWithDefault) // Should use provided valid value
    }

    @Test
    fun floatValidation_invalidValues_causeErrors() {
        // Test nullable float with invalid values
        class NullableFloatTest : Arguments(
            appName = "nullable-float-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val nullableFloat by option("--nullable-float").float()
            val file by positional()
        }

        // Invalid string for nullable float
        val a1 = NullableFloatTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--nullable-float", "xyz", "f"))
        }
        assertTrue(e1.message!!.contains("Invalid value 'xyz'"))
        assertTrue(e1.message!!.contains("--nullable-float"))

        // Invalid format for float
        val a2 = NullableFloatTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--nullable-float", "12.34.56", "f"))
        }
        assertTrue(e2.message!!.contains("Invalid value '12.34.56'"))

        // Multiple dots
        val a3 = NullableFloatTest()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--nullable-float", "1.2.3", "f"))
        }
        assertTrue(e3.message!!.contains("Invalid value '1.2.3'"))
    }

    @Test
    fun floatValidation_nonNullableWithDefault_invalidValues() {
        // Test non-nullable float with default value and invalid input
        class NonNullableFloatWithDefault : Arguments(
            appName = "non-nullable-float-default",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val floatWithDefault by option("--float-default").float().default(3.14f)
            val file by positional()
        }

        val a1 = NonNullableFloatWithDefault()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--float-default", "invalid-float", "f"))
        }
        assertTrue(e1.message!!.contains("Invalid value 'invalid-float'"))
        assertTrue(e1.message!!.contains("--float-default"))

        // Test that valid parsing still works
        val a2 = NonNullableFloatWithDefault()
        a2.parseWithException(arrayOf("alpha", "f"))
        assertEquals(3.14f, a2.floatWithDefault) // Should use default when not provided

        val a3 = NonNullableFloatWithDefault()
        a3.parseWithException(arrayOf("alpha", "--float-default", "2.71", "f"))
        assertEquals(2.71f, a3.floatWithDefault) // Should use provided valid value
    }

    @Test
    fun intListValidation_invalidElements_causeErrors() {
        // Test int list with some invalid elements
        class IntListTest : Arguments(
            appName = "int-list-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intList by option("--int-list").int().list()
            val file by positional()
        }

        // Valid elements followed by invalid
        val a1 = IntListTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--int-list", "1", "--int-list", "abc", "--int-list", "3", "f"))
        }
        assertTrue(e1.message!!.contains("Invalid value 'abc'"))
        assertTrue(e1.message!!.contains("--int-list"))

        // Valid list should work
        val a2 = IntListTest()
        a2.parseWithException(arrayOf("alpha", "--int-list", "1", "--int-list", "2", "--int-list", "3", "f"))
        assertEquals(listOf(1, 2, 3), a2.intList)
    }

    @Test
    fun floatSetValidation_invalidElements_causeErrors() {
        // Test float set with some invalid elements
        class FloatSetTest : Arguments(
            appName = "float-set-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val floatSet by option("--float-set").float().set()
            val file by positional()
        }

        // Invalid element in set
        val a1 = FloatSetTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--float-set", "1.5", "--float-set", "invalid", "--float-set", "2.5", "f"))
        }
        assertTrue(e1.message!!.contains("Invalid value 'invalid'"))
        assertTrue(e1.message!!.contains("--float-set"))

        // Valid set should work
        val a2 = FloatSetTest()
        a2.parseWithException(arrayOf("alpha", "--float-set", "1.5", "--float-set", "2.5", "--float-set", "1.5", "f"))
        assertEquals(setOf(1.5f, 2.5f), a2.floatSet)
    }

    @Test
    fun typeValidationWithConstraints_invalidValuesWithConstraints() {
        // Test type validation errors in combination with constraints
        class TypeConstraintTest : Arguments(
            appName = "type-constraint-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intTrigger by option("--int-trigger").int().atMostOneWith(::floatTrigger)
            val floatTrigger by option("--float-trigger").float()
            val requiredString by option("--required-string").requireIfAnyPresent(::intTrigger, ::floatTrigger)
            val file by positional()
        }

        // Invalid int should fail before constraint validation
        val a1 = TypeConstraintTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--int-trigger", "not-int", "--required-string", "test", "f"))
        }
        assertTrue(e1.message!!.contains("Invalid value 'not-int'"))
        assertTrue(e1.message!!.contains("--int-trigger"))

        // Invalid float should fail before constraint validation
        val a2 = TypeConstraintTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--float-trigger", "not-float", "--required-string", "test", "f"))
        }
        assertTrue(e2.message!!.contains("Invalid value 'not-float'"))
        assertTrue(e2.message!!.contains("--float-trigger"))

        // Valid values should still trigger constraint validation
        val a3 = TypeConstraintTest()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--int-trigger", "42", "f")) // missing required-string
        }
        // This should be a constraint error, not a type error
        assertFalse(e3.message!!.contains("Invalid value"))
    }

    @Test
    fun oneOfValidation_invalidValues_causeErrors() {
        // Test oneOf with invalid choices
        class OneOfTest : Arguments(
            appName = "one-of-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val mode by option("--mode").oneOf("fast", "slow", "medium")
            val level by option("--level").oneOf("low", "high").default("low")
            val file by positional()
        }

        // Invalid choice for nullable oneOf
        val a1 = OneOfTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--mode", "invalid-mode", "f"))
        }
        assertTrue(e1.message!!.contains("Invalid value 'invalid-mode'"))
        assertTrue(e1.message!!.contains("--mode"))

        // Invalid choice for oneOf with default
        val a2 = OneOfTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--level", "invalid-level", "f"))
        }
        assertTrue(e2.message!!.contains("Invalid value 'invalid-level'"))
        assertTrue(e2.message!!.contains("--level"))

        // Valid choices should work
        val a3 = OneOfTest()
        a3.parseWithException(arrayOf("alpha", "--mode", "fast", "--level", "high", "f"))
        assertEquals("fast", a3.mode)
        assertEquals("high", a3.level)

        // Default should work when not provided
        val a4 = OneOfTest()
        a4.parseWithException(arrayOf("alpha", "f"))
        assertNull(a4.mode)
        assertEquals("low", a4.level)
    }

    @Test
    fun edgeCaseInvalidValues_specialCharacters() {
        // Test edge cases with special characters and edge values
        class EdgeCaseTest : Arguments(
            appName = "edge-case-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intVal by option("--int").int()
            val floatVal by option("--float").float()
            val file by positional()
        }

        // Test unicode characters that should be invalid
        val a1 = EdgeCaseTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--int", "1abc", "f")) // Contains letters
        }
        assertTrue(e1.message!!.contains("Invalid value '1abc'"))

        // Test scientific notation that might be invalid for int
        val a2 = EdgeCaseTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--int", "1e5", "f"))
        }
        assertTrue(e2.message!!.contains("Invalid value '1e5'"))

        // Test infinity for float (should this be valid or invalid?)
        val a3 = EdgeCaseTest()
        a3.parseWithException(arrayOf("alpha", "--float", "Infinity", "f"))
        assertEquals(Float.POSITIVE_INFINITY, a3.floatVal)

        // Test NaN for float
        val a4 = EdgeCaseTest()
        a4.parseWithException(arrayOf("alpha", "--float", "NaN", "f"))
        assertTrue(a4.floatVal!!.isNaN())

        // Test very large numbers
        val a5 = EdgeCaseTest()
        val e5 = assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--int", "999999999999999999999", "f")) // Too large for Int
        }
        assertTrue(e5.message!!.contains("Invalid value '999999999999999999999'"))
    }

    @Test
    fun positionalValidation_invalidTypes() {
        // Test positional arguments with type validation
        class PositionalTypeTest : Arguments(
            appName = "positional-type-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intPositional by positional().int()
            val floatList by positional().float().list()
        }

        // Invalid int positional
        val a1 = PositionalTypeTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "invalid-int", "1.5", "2.5"))
        }
        assertTrue(e1.message!!.contains("Invalid value 'invalid-int'"))

        // Invalid float in positional list
        val a2 = PositionalTypeTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "42", "1.5", "invalid-float", "2.5"))
        }
        assertTrue(e2.message!!.contains("Invalid value 'invalid-float'"))

        // Valid case should work
        val a3 = PositionalTypeTest()
        a3.parseWithException(arrayOf("alpha", "42", "1.5", "2.5", "3.14"))
        assertEquals(42, a3.intPositional)
        assertEquals(listOf(1.5f, 2.5f, 3.14f), a3.floatList)
    }
}
