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

class CrossDataTypeConstraintTest {

    @Test
    fun intNullableVsNonNullable_exactlyOneWith() {
        // Test nullable Int with exactlyOneWith
        class NullableIntExactlyOne : Arguments(
            appName = "nullable-int-exactly-one",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val nullableInt by option("--nullable-int").int().exactlyOneWith(::nonNullableInt)
            val nonNullableInt by option("--non-nullable-int").int().default(0)
            val file by positional()
        }

        val a1 = NullableIntExactlyOne()
        a1.parseWithException(arrayOf("alpha", "--nullable-int", "42", "f"))
        assertEquals(42, a1.nullableInt)
        assertEquals(0, a1.nonNullableInt)

        val a2 = NullableIntExactlyOne()
        a2.parseWithException(arrayOf("alpha", "--non-nullable-int", "99", "f"))
        assertNull(a2.nullableInt)
        assertEquals(99, a2.nonNullableInt)

        // Both should fail
        val a3 = NullableIntExactlyOne()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--nullable-int", "1", "--non-nullable-int", "2", "f"))
        }
    }

    @Test
    fun floatNullableVsNonNullable_atMostOneWith() {
        // Test nullable Float with atMostOneWith
        class NullableFloatAtMostOne : Arguments(
            appName = "nullable-float-at-most-one",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val nullableFloat by option("--nullable-float").float().atMostOneWith(::nonNullableFloat)
            val nonNullableFloat by option("--non-nullable-float").float().default(0.0f)
            val file by positional()
        }

        val a1 = NullableFloatAtMostOne()
        a1.parseWithException(arrayOf("alpha", "--nullable-float", "3.14", "f"))
        assertEquals(3.14f, a1.nullableFloat)
        assertEquals(0.0f, a1.nonNullableFloat)

        val a2 = NullableFloatAtMostOne()
        a2.parseWithException(arrayOf("alpha", "--non-nullable-float", "2.71", "f"))
        assertNull(a2.nullableFloat)
        assertEquals(2.71f, a2.nonNullableFloat)

        val a3 = NullableFloatAtMostOne()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertNull(a3.nullableFloat)
        assertEquals(0.0f, a3.nonNullableFloat)

        // Both should fail
        val a4 = NullableFloatAtMostOne()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--nullable-float", "1.0", "--non-nullable-float", "2.0", "f"))
        }
    }

    @Test
    fun listConstraints_atLeastOneWith() {
        // Test list constraints with atLeastOneWith
        class ListAtLeastOne : Arguments(
            appName = "list-at-least-one",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intList by option("--int-list").int().list().atLeastOneWith(::stringList)
            val stringList by option("--string-list").map { it }.list()
            val file by positional()
        }

        val a1 = ListAtLeastOne()
        a1.parseWithException(arrayOf("alpha", "--int-list", "1", "--int-list", "2", "f"))
        assertEquals(listOf(1, 2), a1.intList)
        assertEquals(emptyList(), a1.stringList)

        val a2 = ListAtLeastOne()
        a2.parseWithException(arrayOf("alpha", "--string-list", "hello", "--string-list", "world", "f"))
        assertEquals(emptyList(), a2.intList)
        assertEquals(listOf("hello", "world"), a2.stringList)

        val a3 = ListAtLeastOne()
        a3.parseWithException(arrayOf("alpha", "--int-list", "42", "--string-list", "test", "f"))
        assertEquals(listOf(42), a3.intList)
        assertEquals(listOf("test"), a3.stringList)

        // Neither should fail
        val a4 = ListAtLeastOne()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "f"))
        }
    }

    @Test
    fun setConstraints_conflictsWith() {
        // Test set constraints with conflictsWith
        class SetConflicts : Arguments(
            appName = "set-conflicts",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intSet by option("--int-set").int().set().conflictsWith(::floatSet)
            val floatSet by option("--float-set").float().set()
            val file by positional()
        }

        val a1 = SetConflicts()
        a1.parseWithException(arrayOf("alpha", "--int-set", "1", "--int-set", "2", "--int-set", "1", "f"))
        assertEquals(setOf(1, 2), a1.intSet)
        assertEquals(emptySet(), a1.floatSet)

        val a2 = SetConflicts()
        a2.parseWithException(arrayOf("alpha", "--float-set", "1.5", "--float-set", "2.5", "f"))
        assertEquals(emptySet(), a2.intSet)
        assertEquals(setOf(1.5f, 2.5f), a2.floatSet)

        val a3 = SetConflicts()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertEquals(emptySet(), a3.intSet)
        assertEquals(emptySet(), a3.floatSet)

        // Both should fail
        val a4 = SetConflicts()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--int-set", "1", "--float-set", "2.0", "f"))
        }
    }

    @Test
    fun mixedDataTypes_requireIfAllPresent() {
        // Test mixed data types with requireIfAllPresent
        class MixedRequireIfAll : Arguments(
            appName = "mixed-require-if-all",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intTrigger by option("--int-trigger").int()
            val floatTrigger by option("--float-trigger").float()
            val requiredString by option("--required-string").requireIfAllPresent(::intTrigger, ::floatTrigger)
            val file by positional()
        }

        val a1 = MixedRequireIfAll()
        a1.parseWithException(arrayOf("alpha", "--int-trigger", "42", "f"))
        assertEquals(42, a1.intTrigger)
        assertNull(a1.floatTrigger)
        assertNull(a1.requiredString)

        val a2 = MixedRequireIfAll()
        a2.parseWithException(arrayOf("alpha", "--float-trigger", "3.14", "f"))
        assertNull(a2.intTrigger)
        assertEquals(3.14f, a2.floatTrigger)
        assertNull(a2.requiredString)

        val a3 = MixedRequireIfAll()
        a3.parseWithException(arrayOf("alpha", "--int-trigger", "1", "--float-trigger", "2.0", "--required-string", "hello", "f"))
        assertEquals(1, a3.intTrigger)
        assertEquals(2.0f, a3.floatTrigger)
        assertEquals("hello", a3.requiredString)

        // Both triggers but no required should fail
        val a4 = MixedRequireIfAll()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--int-trigger", "1", "--float-trigger", "2.0", "f"))
        }
    }

    @Test
    fun nullableListsWithDefaults_complexConstraints() {
        // Test nullable lists with defaults in complex constraint scenarios
        class NullableListsComplex : Arguments(
            appName = "nullable-lists-complex",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val intListNullable by option("--int-list-nullable").int().list()
            val intListWithDefault by option("--int-list-default").int().list()
            val stringSet by option("--string-set").map { it }.set().atMostOneWith(::intListNullable)
            val file by positional()
        }

        val a1 = NullableListsComplex()
        a1.parseWithException(arrayOf("alpha", "--int-list-nullable", "1", "--int-list-nullable", "2", "f"))
        assertEquals(listOf(1, 2), a1.intListNullable)
        assertEquals(emptyList<Int>(), a1.intListWithDefault)
        assertEquals(emptySet<String>(), a1.stringSet)

        val a2 = NullableListsComplex()
        a2.parseWithException(arrayOf("alpha", "--string-set", "hello", "--string-set", "world", "f"))
        assertEquals(emptyList<Int>(), a2.intListNullable)
        assertEquals(emptyList<Int>(), a2.intListWithDefault)
        assertEquals(setOf("hello", "world"), a2.stringSet)

        val a3 = NullableListsComplex()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertEquals(emptyList<Int>(), a3.intListNullable)
        assertEquals(emptyList<Int>(), a3.intListWithDefault)
        assertEquals(emptySet<String>(), a3.stringSet)

        // Both should fail due to atMostOneWith
        val a4 = NullableListsComplex()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--int-list-nullable", "1", "--string-set", "test", "f"))
        }
    }

    @Test
    fun edgeCaseOptionNames_withSpecialPrefixes() {
        // Test various edge case option names that might trigger parsing issues
        class EdgeCaseNames : Arguments(
            appName = "edge-case-names",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val noInt by option("--no-int").int()  // nullable int with "no-" prefix
            val noFloat by option("--no-float").float().default(0.0f)  // non-nullable float with "no-" prefix
            val noList by option("--no-list").map { it }.list()  // list with "no-" prefix
            val noSet by option("--no-set").map { it }.set().conflictsWith(::noList)  // set with "no-" prefix
            val file by positional()
        }

        val a1 = EdgeCaseNames()
        a1.parseWithException(arrayOf("alpha", "--no-int", "42", "f"))
        assertEquals(42, a1.noInt)
        assertEquals(0.0f, a1.noFloat)
        assertEquals(emptyList<String>(), a1.noList)
        assertEquals(emptySet<String>(), a1.noSet)

        val a2 = EdgeCaseNames()
        a2.parseWithException(arrayOf("alpha", "--no-float", "3.14", "f"))
        assertNull(a2.noInt)
        assertEquals(3.14f, a2.noFloat)
        assertEquals(emptyList<String>(), a2.noList)
        assertEquals(emptySet<String>(), a2.noSet)

        val a3 = EdgeCaseNames()
        a3.parseWithException(arrayOf("alpha", "--no-list", "item1", "--no-list", "item2", "f"))
        assertNull(a3.noInt)
        assertEquals(0.0f, a3.noFloat)
        assertEquals(listOf("item1", "item2"), a3.noList)
        assertEquals(emptySet<String>(), a3.noSet)

        val a4 = EdgeCaseNames()
        a4.parseWithException(arrayOf("alpha", "--no-set", "item1", "--no-set", "item2", "--no-set", "item1", "f"))
        assertNull(a4.noInt)
        assertEquals(0.0f, a4.noFloat)
        assertEquals(emptyList<String>(), a4.noList)
        assertEquals(setOf("item1", "item2"), a4.noSet)

        // List and set should conflict
        val a5 = EdgeCaseNames()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--no-list", "item", "--no-set", "item", "f"))
        }
    }

    @Test
    fun countOptionsWithConstraints() {
        // Test count options with various constraints
        class CountConstraints : Arguments(
            appName = "count-constraints",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val verboseCount by option("-v", "--verbose").bool().list().atLeastOneWith(::quietCount)
            val quietCount by option("-q", "--quiet").bool().list()
            val debugInt by option("--debug-int").int().requireIfValue(::verboseCount) { it != null && it.size > 2 }
            val file by positional()
        }

        // Test boundary conditions for the verbosity threshold (> 2)

        // Test exactly at boundary (count = 2) - should NOT require debugInt
        val a1 = CountConstraints()
        a1.parseWithException(arrayOf("alpha", "-vv", "f"))
        assertEquals(2, a1.verboseCount.size)
        assertEquals(0, a1.quietCount.size)
        assertNull(a1.debugInt) // debugInt should not be required when count = 2

        // Test just above boundary (count = 3) - SHOULD require debugInt
        val a2 = CountConstraints()
        a2.parseWithException(arrayOf("alpha", "-vvv", "--debug-int", "42", "f"))
        assertEquals(3, a2.verboseCount.size)
        assertEquals(0, a2.quietCount.size)
        assertEquals(42, a2.debugInt) // debugInt is required when count > 2

        // Test well above boundary (count = 5) - SHOULD require debugInt
        val a3 = CountConstraints()
        a3.parseWithException(arrayOf("alpha", "-vvvvv", "--debug-int", "99", "f"))
        assertEquals(5, a3.verboseCount.size)
        assertEquals(0, a3.quietCount.size)
        assertEquals(99, a3.debugInt) // debugInt is required when count > 2

        // Test quiet option satisfies atLeastOneWith constraint
        val a4 = CountConstraints()
        a4.parseWithException(arrayOf("alpha", "-q", "-q", "f"))
        assertEquals(0, a4.verboseCount.size)
        assertEquals(2, a4.quietCount.size)
        assertNull(a4.debugInt) // debugInt not required for quiet

        // Test failure: No verbose or quiet should fail atLeastOneWith
        val a5 = CountConstraints()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "f"))
        }

        // Test failure: Verbose count = 3 without debug-int should fail requireIfValue
        val a6 = CountConstraints()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("alpha", "-vvv", "f")) // count = 3 > 2, missing debugInt
        }

        // Test failure: Verbose count = 4 without debug-int should fail requireIfValue
        val a7 = CountConstraints()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("alpha", "-vvvv", "f")) // count = 4 > 2, missing debugInt
        }
    }

    @Test
    fun oneOfOptionsWithConstraints() {
        // Test oneOf options with constraints
        class OneOfConstraints : Arguments(
            appName = "one-of-constraints",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val mode by option("--mode").oneOf("fast", "slow", "medium").atMostOneWith(::level)
            val level by option("--level").oneOf("low", "high")
            val maxThreads by option("--max-threads").int().requireIfValue(::mode) { it == "fast" }
            val file by positional()
        }

        val a1 = OneOfConstraints()
        a1.parseWithException(arrayOf("alpha", "--mode", "fast", "--max-threads", "8", "f"))
        assertEquals("fast", a1.mode)
        assertNull(a1.level)
        assertEquals(8, a1.maxThreads)

        val a2 = OneOfConstraints()
        a2.parseWithException(arrayOf("alpha", "--mode", "slow", "f"))
        assertEquals("slow", a2.mode)
        assertNull(a2.level)
        assertNull(a2.maxThreads)

        val a3 = OneOfConstraints()
        a3.parseWithException(arrayOf("alpha", "--level", "high", "f"))
        assertNull(a3.mode)
        assertEquals("high", a3.level)
        assertNull(a3.maxThreads)

        // Both mode and level should fail atMostOneWith
        val a4 = OneOfConstraints()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--mode", "fast", "--level", "low", "f"))
        }

        // Fast mode without max-threads should fail requireIfValue
        val a5 = OneOfConstraints()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--mode", "fast", "f"))
        }
    }

    @Test
    fun environmentVariablesWithConstraints() {
        // Test environment variable fallback with constraints
        class EnvConstraints : Arguments(
            appName = "env-constraints",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val envInt by option("--env-int").int().fromEnv("TEST_ENV_INT").atMostOneWith(::regularInt)
            val regularInt by option("--regular-int").int()
            val envString by option("--env-string").fromEnv("TEST_ENV_STRING")
            val file by positional()
        }

        // Note: Since we can't easily set environment variables in tests,
        // we'll test the constraint logic assuming env vars are not set
        val a1 = EnvConstraints()
        a1.parseWithException(arrayOf("alpha", "--regular-int", "42", "f"))
        assertNull(a1.envInt)
        assertEquals(42, a1.regularInt)
        assertNull(a1.envString)

        val a2 = EnvConstraints()
        a2.parseWithException(arrayOf("alpha", "f"))
        assertNull(a1.envInt)
        assertNull(a2.regularInt)
        assertNull(a2.envString)

        // If both were provided, it should fail
        // (This would need actual env var setup to test properly)
    }
}
