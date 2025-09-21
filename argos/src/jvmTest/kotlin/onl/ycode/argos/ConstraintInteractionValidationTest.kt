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

class ConstraintInteractionValidationTest {

    @Test
    fun constraintPrecedence_typeValidationVsConstraintValidation() {
        // Test that type validation happens before constraint validation
        class TypeVsConstraintTest : Arguments(
            appName = "type-vs-constraint-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intTrigger by option("--int-trigger").int().atMostOneWith(::stringTrigger)
            val stringTrigger by option("--string-trigger")
            val required by option("--required").requireIfAnyPresent(::intTrigger, ::stringTrigger)
            val file by positional()
        }

        // Test that invalid type fails BEFORE constraint validation
        val a1 = TypeVsConstraintTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--int-trigger", "invalid-int", "--required", "present", "f"))
        }
        // Should be a type error, not a constraint error
        assertTrue(e1.message!!.contains("Invalid value 'invalid-int'"),
            "Should fail with type validation error, not constraint error")
        assertTrue(e1.message!!.contains("--int-trigger"), "Should mention the problematic option")

        // Test that valid types proceed to constraint validation
        val a2 = TypeVsConstraintTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--int-trigger", "42", "f")) // valid int but missing required
        }
        // Should be a constraint error, not a type error
        assertFalse(e2.message!!.contains("Invalid value '42'"),
            "Should not be a type error since 42 is a valid int")
    }

    @Test
    fun multipleConstraintTypes_conflictResolution() {
        // Test how multiple different constraint types interact
        class MultiConstraintTest : Arguments(
            appName = "multi-constraint-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val optionA by option("--option-a").exactlyOneWith(::optionB)
            val optionB by option("--option-b").conflictsWith(::optionC)
            val optionC by option("--option-c").requireIfAnyPresent(::optionD)
            val optionD by option("--option-d")
            val file by positional()
        }

        // Valid case: exactlyOne satisfied, no conflicts
        val a1 = MultiConstraintTest()
        a1.parseWithException(arrayOf("alpha", "--option-a", "valueA", "f"))
        assertEquals("valueA", a1.optionA)
        assertNull(a1.optionB)
        assertNull(a1.optionC)
        assertNull(a1.optionD)

        // Another valid case: the other side of exactlyOne
        val a2 = MultiConstraintTest()
        a2.parseWithException(arrayOf("alpha", "--option-b", "valueB", "f"))
        assertNull(a2.optionA)
        assertEquals("valueB", a2.optionB)
        assertNull(a2.optionC)
        assertNull(a2.optionD)

        // Valid case: optionD present requires optionC, but optionC conflicts with optionB
        val a3 = MultiConstraintTest()
        a3.parseWithException(arrayOf("alpha", "--option-a", "valueA", "--option-d", "valueD", "--option-c", "valueC", "f"))
        assertEquals("valueA", a3.optionA)
        assertNull(a3.optionB) // Cannot be present due to exactlyOne with optionA
        assertEquals("valueC", a3.optionC)
        assertEquals("valueD", a3.optionD)

        // Test failure: exactlyOne not satisfied (both present)
        val a4 = MultiConstraintTest()
        val e4 = assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--option-a", "valueA", "--option-b", "valueB", "f"))
        }
        // Should fail due to exactlyOne constraint violation
        assertFalse(e4.message!!.contains("Invalid value"), "Should be constraint error, not type error")

        // Test failure: conflict (optionB conflicts with optionC)
        val a5 = MultiConstraintTest()
        val e5 = assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--option-b", "valueB", "--option-c", "valueC", "f"))
        }
        // Should fail due to conflict constraint
        assertFalse(e5.message!!.contains("Invalid value"), "Should be constraint error, not type error")

        // Test failure: requirement not met (optionD present but optionC missing)
        val a6 = MultiConstraintTest()
        val e6 = assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("alpha", "--option-a", "valueA", "--option-d", "valueD", "f"))
        }
        // Should fail due to requireIfAnyPresent constraint
        assertFalse(e6.message!!.contains("Invalid value"), "Should be constraint error, not type error")
    }

    @Test
    fun constraintChaining_dependencyValidation() {
        // Test chains of constraints to ensure proper validation order
        class ConstraintChainTest : Arguments(
            appName = "constraint-chain-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            // Chain: trigger1 -> trigger2 -> trigger3 -> final
            val trigger1 by option("--trigger1").bool().default(false)
            val trigger2 by option("--trigger2").requireIfValue(::trigger1) { it == true }
            val trigger3 by option("--trigger3").int().requireIfValue(::trigger2) { it != null }
            val final by option("--final").requireIfValue(::trigger3) { it != null && it > 5 }
            val file by positional()
        }

        // Test complete chain activation
        val a1 = ConstraintChainTest()
        a1.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "value", "--trigger3", "10", "--final", "end", "f"))
        assertTrue(a1.trigger1)
        assertEquals("value", a1.trigger2)
        assertEquals(10, a1.trigger3)
        assertEquals("end", a1.final)

        // Test partial chain (trigger3 not high enough to require final)
        val a2 = ConstraintChainTest()
        a2.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "value", "--trigger3", "3", "f"))
        assertTrue(a2.trigger1)
        assertEquals("value", a2.trigger2)
        assertEquals(3, a2.trigger3) // 3 is not > 5, so final not required
        assertNull(a2.final)

        // Test chain break at trigger1 (false doesn't require trigger2)
        val a3 = ConstraintChainTest()
        a3.parseWithException(arrayOf("alpha", "f")) // trigger1 defaults to false
        assertFalse(a3.trigger1)
        assertNull(a3.trigger2)
        assertNull(a3.trigger3)
        assertNull(a3.final)

        // Test failure: trigger1 true but trigger2 missing
        val a4 = ConstraintChainTest()
        val e4 = assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--trigger1", "f"))
        }
        // Should fail at the first link in the chain
        assertFalse(e4.message!!.contains("Invalid value"), "Should be constraint error")

        // Test failure: trigger2 present but trigger3 missing
        val a5 = ConstraintChainTest()
        val e5 = assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "value", "f"))
        }
        assertFalse(e5.message!!.contains("Invalid value"), "Should be constraint error")

        // Test failure: trigger3 > 5 but final missing
        val a6 = ConstraintChainTest()
        val e6 = assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "value", "--trigger3", "10", "f"))
        }
        assertFalse(e6.message!!.contains("Invalid value"), "Should be constraint error")
    }

    @Test
    fun constraintGroups_mutualExclusionAndRequirement() {
        // Test groups of constraints that have mutual relationships
        class ConstraintGroupTest : Arguments(
            appName = "constraint-group-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")

            // Group 1: Mutually exclusive options
            val formatJson by option("--json").bool().exactlyOneWith(::formatXml, ::formatYaml)
            val formatXml by option("--xml").bool()
            val formatYaml by option("--yaml").bool()

            // Group 2: Optional enhancement options
            val prettyPrint by option("--pretty").bool() // Pretty print option
            val validate by option("--validate") // Validation option

            // Group 3: Options that conflict with specific formats
            val compress by option("--compress").conflictsWith(::prettyPrint)

            val file by positional()
        }

        // Test valid: JSON with pretty printing (pretty requires format, validate is optional)
        val a1 = ConstraintGroupTest()
        a1.parseWithException(arrayOf("alpha", "--json", "--pretty", "f"))
        assertTrue(a1.formatJson!!)
        assertNull(a1.formatXml)
        assertNull(a1.formatYaml)
        assertTrue(a1.prettyPrint!!) // Boolean option was provided, should be true
        assertNull(a1.validate) // Validate is optional
        assertNull(a1.compress)

        // Test valid: XML with validation and pretty print
        val a2 = ConstraintGroupTest()
        a2.parseWithException(arrayOf("alpha", "--xml", "--validate", "strict", "--pretty", "f"))
        assertNull(a2.formatJson)
        assertTrue(a2.formatXml!!)
        assertNull(a2.formatYaml)
        assertTrue(a2.prettyPrint!!) // Boolean option was provided
        assertEquals("strict", a2.validate)
        assertNull(a2.compress)

        // Test valid: YAML with compression (no pretty print to avoid conflict)
        val a3 = ConstraintGroupTest()
        a3.parseWithException(arrayOf("alpha", "--yaml", "--compress", "gzip", "f"))
        assertNull(a3.formatJson)
        assertNull(a3.formatXml)
        assertTrue(a3.formatYaml!!)
        assertNull(a3.prettyPrint) // No pretty print to avoid conflict with compress
        assertNull(a3.validate)
        assertEquals("gzip", a3.compress)

        // Test failure: multiple formats (violates exactlyOne)
        val a4 = ConstraintGroupTest()
        val e4 = assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--json", "--xml", "--pretty", "f"))
        }
        assertFalse(e4.message!!.contains("Invalid value"), "Should be constraint error")

        // Test failure: pretty print alone violates exactlyOne constraint for formats
        val a5 = ConstraintGroupTest()
        val e5 = assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--pretty", "f")) // pretty without required format
        }
        assertFalse(e5.message!!.contains("Invalid value"), "Should be constraint error")
        assertTrue(e5.message!!.contains("Exactly one"), "Should mention exactlyOne constraint violation")

        // Test failure: compression conflicts with pretty print
        val a6 = ConstraintGroupTest()
        val e6 = assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("alpha", "--json", "--pretty", "--compress", "gzip", "f"))
        }
        assertFalse(e6.message!!.contains("Invalid value"), "Should be constraint error")
    }

    @Test
    fun constraintErrorMessages_specificity() {
        // Test that constraint error messages are specific about which constraint failed
        class ErrorMessageTest : Arguments(
            appName = "error-message-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val optionA by option("--option-a").exactlyOneWith(::optionB)
            val optionB by option("--option-b").conflictsWith(::optionC)
            val optionC by option("--option-c")
            val requiredOption by option("--required").requireIfAnyPresent(::optionA, ::optionB)
            val file by positional()
        }

        // Test exactlyOne constraint error message
        val a1 = ErrorMessageTest()
        val e1 = assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "f")) // Neither optionA nor optionB provided
        }
        // Error message should indicate exactlyOne constraint failure
        // (The exact message format depends on your implementation)

        // Test conflict constraint error message
        val a2 = ErrorMessageTest()
        val e2 = assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--option-b", "valueB", "--option-c", "valueC", "f"))
        }
        // Error message should indicate conflict between optionB and optionC

        // Test requirement constraint error message
        val a3 = ErrorMessageTest()
        val e3 = assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--option-a", "valueA", "f")) // Missing required
        }
        // Error message should indicate missing required option

        // Validate that error messages don't contain type validation language
        assertFalse(e1.message!!.contains("Invalid value"), "Constraint error should not mention invalid values")
        assertFalse(e2.message!!.contains("Invalid value"), "Constraint error should not mention invalid values")
        assertFalse(e3.message!!.contains("Invalid value"), "Constraint error should not mention invalid values")
    }
}
