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

class AdvancedConstraintCombinationTest {

    @Test
    fun multiLevelConstraints_allDataTypes() {
        // Test complex multi-level constraints across all data types
        class MultiLevelAllTypes : Arguments(
            appName = "multi-level-all-types",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")

            // Boolean layer
            val enableFeature by option("--enable-feature").bool().default(false)
            val disableOld by option("--disable-old").bool().requireIfValue(::enableFeature) { it == true }

            // Int layer
            val maxItems by option("--max-items").int().atMostOneWith(::minItems)
            val minItems by option("--min-items").int().default(1)

            // Float layer
            val threshold by option("--threshold").float().requireIfAllPresent(::enableFeature, ::maxItems)

            // String layer
            val configFile by option("--config-file").conflictsWith(::threshold)

            // List layer
            val tags by option("--tag").map { it }.list().atLeastOneWith(::categories)
            val categories by option("--category").map { it }.list()

            val file by positional()
        }

        // Test valid combination 1: feature enabled with constraints satisfied
        val a1 = MultiLevelAllTypes()
        a1.parseWithException(arrayOf("alpha", "--enable-feature", "--disable-old", "--max-items", "100", "--threshold", "0.5", "--tag", "test", "f"))
        assertTrue(a1.enableFeature)
        assertTrue(a1.disableOld!!)
        assertEquals(100, a1.maxItems)
        assertEquals(1, a1.minItems)
        assertEquals(0.5f, a1.threshold)
        assertNull(a1.configFile)
        assertEquals(listOf("test"), a1.tags)
        assertEquals(emptyList<String>(), a1.categories)

        // Test valid combination 2: using config file (conflicts with max-items and threshold)
        val a2 = MultiLevelAllTypes()
        a2.parseWithException(arrayOf("alpha", "--config-file", "config.json", "--category", "prod", "--category", "test", "f"))
        assertFalse(a2.enableFeature)
        assertNull(a2.disableOld)
        assertNull(a2.maxItems)
        assertEquals(1, a2.minItems)
        assertNull(a2.threshold)
        assertEquals("config.json", a2.configFile)
        assertEquals(emptyList<String>(), a2.tags)
        assertEquals(listOf("prod", "test"), a2.categories)

        // Test failure: enable feature + max items but missing threshold (requireIfAllPresent)
        val a3 = MultiLevelAllTypes()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--enable-feature", "--max-items", "50", "--tag", "test", "f"))
        }

        // Test failure: enable feature but missing disable-old (requireIfValue)
        val a4 = MultiLevelAllTypes()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--enable-feature", "f"))
        }

        // Test failure: config-file conflicts with threshold
        val a5 = MultiLevelAllTypes()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--enable-feature", "--max-items", "50", "--threshold", "1.5", "--config-file", "config.json", "f"))
        }
    }

    @Test
    fun nullableVsNonNullable_consistencyAcrossTypes() {
        // Test consistency between nullable and non-nullable across all types
        class ConsistencyTest : Arguments(
            appName = "consistency-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")

            // Nullable versions
            val nullableInt by option("--nullable-int").int()
            val nullableFloat by option("--nullable-float").float()
            val nullableString by option("--nullable-string")
            val nullableBool by option("--nullable-bool").bool()
            val nullableOneOf by option("--nullable-oneof").oneOf("a", "b", "c")

            // Non-nullable versions with defaults
            val nonNullableInt by option("--non-nullable-int").int().default(42)
            val nonNullableFloat by option("--non-nullable-float").float().default(3.14f)
            val nonNullableString by option("--non-nullable-string").default("default")
            val nonNullableBool by option("--non-nullable-bool").bool().default(false)
            val nonNullableOneOf by option("--non-nullable-oneof").oneOf("a", "b", "c").default("a")

            val file by positional()
        }

        // Test all nullables provided
        val a1 = ConsistencyTest()
        a1.parseWithException(arrayOf("alpha",
            "--nullable-int", "100",
            "--nullable-float", "2.71",
            "--nullable-string", "test",
            "--nullable-bool",
            "--nullable-oneof", "b",
            "f"))
        assertEquals(100, a1.nullableInt)
        assertEquals(2.71f, a1.nullableFloat)
        assertEquals("test", a1.nullableString)
        assertEquals(true, a1.nullableBool)
        assertEquals("b", a1.nullableOneOf)
        assertEquals(42, a1.nonNullableInt)
        assertEquals(3.14f, a1.nonNullableFloat)
        assertEquals("default", a1.nonNullableString)
        assertEquals(false, a1.nonNullableBool)
        assertEquals("a", a1.nonNullableOneOf)

        // Test no nullables provided (should be null)
        val a2 = ConsistencyTest()
        a2.parseWithException(arrayOf("alpha", "f"))
        assertNull(a2.nullableInt)
        assertNull(a2.nullableFloat)
        assertNull(a2.nullableString)
        assertNull(a2.nullableBool)
        assertNull(a2.nullableOneOf)
        assertEquals(42, a2.nonNullableInt)
        assertEquals(3.14f, a2.nonNullableFloat)
        assertEquals("default", a2.nonNullableString)
        assertEquals(false, a2.nonNullableBool)
        assertEquals("a", a2.nonNullableOneOf)

        // Test non-nullables overridden
        val a3 = ConsistencyTest()
        a3.parseWithException(arrayOf("alpha",
            "--non-nullable-int", "999",
            "--non-nullable-float", "1.41",
            "--non-nullable-string", "override",
            "--non-nullable-bool",
            "--non-nullable-oneof", "c",
            "f"))
        assertNull(a3.nullableInt)
        assertNull(a3.nullableFloat)
        assertNull(a3.nullableString)
        assertNull(a3.nullableBool)
        assertNull(a3.nullableOneOf)
        assertEquals(999, a3.nonNullableInt)
        assertEquals(1.41f, a3.nonNullableFloat)
        assertEquals("override", a3.nonNullableString)
        assertEquals(true, a3.nonNullableBool)
        assertEquals("c", a3.nonNullableOneOf)
    }

    @Test
    fun collectionConstraints_complexScenarios() {
        // Test complex collection constraint scenarios
        class CollectionComplexity : Arguments(
            appName = "collection-complexity",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")

            // Multiple list types with cross-constraints
            val intList by option("--int-list").int().list().atMostOneWith(::stringList, ::floatList)
            val stringList by option("--string-list").map { it }.list()
            val floatList by option("--float-list").float().list()

            // Sets with requirements
            val requiredSet by option("--required-set").map { it }.set().requireIfAnyPresent(::intList, ::stringList, ::floatList)

            // Mixed collections
            val mixedItems by option("--mixed-item").map { it }.list().conflictsWith(::requiredSet)

            val file by positional()
        }

        // Test int list with required set
        val a1 = CollectionComplexity()
        a1.parseWithException(arrayOf("alpha", "--int-list", "1", "--int-list", "2", "--required-set", "item1", "f"))
        assertEquals(listOf(1, 2), a1.intList)
        assertEquals(emptyList<String>(), a1.stringList)
        assertEquals(emptyList<Float>(), a1.floatList)
        assertEquals(setOf("item1"), a1.requiredSet)
        assertEquals(emptyList<String>(), a1.mixedItems)

        // Test string list with required set
        val a2 = CollectionComplexity()
        a2.parseWithException(arrayOf("alpha", "--string-list", "hello", "--string-list", "world", "--required-set", "item1", "--required-set", "item2", "f"))
        assertEquals(emptyList<Int>(), a2.intList)
        assertEquals(listOf("hello", "world"), a2.stringList)
        assertEquals(emptyList<Float>(), a2.floatList)
        assertEquals(setOf("item1", "item2"), a2.requiredSet)
        assertEquals(emptyList<String>(), a2.mixedItems)

        // Test mixed items alone (no other lists, so no required set needed)
        val a3 = CollectionComplexity()
        a3.parseWithException(arrayOf("alpha", "--mixed-item", "test1", "--mixed-item", "test2", "f"))
        assertEquals(emptyList<Int>(), a3.intList)
        assertEquals(emptyList<String>(), a3.stringList)
        assertEquals(emptyList<Float>(), a3.floatList)
        assertEquals(emptySet<String>(), a3.requiredSet)
        assertEquals(listOf("test1", "test2"), a3.mixedItems)

        // Test failure: multiple list types (atMostOneWith)
        val a4 = CollectionComplexity()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--int-list", "1", "--string-list", "test", "f"))
        }

        // Test failure: list provided but no required set (requireIfAnyPresent)
        val a5 = CollectionComplexity()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "--int-list", "1", "f"))
        }

        // Test failure: mixed items conflicts with required set
        val a6 = CollectionComplexity()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("alpha", "--string-list", "test", "--required-set", "item", "--mixed-item", "conflict", "f"))
        }
    }

    @Test
    fun extremeEdgeCases_allConstraintTypes() {
        // Test extreme edge cases with all constraint types
        class ExtremeEdgeCases : Arguments(
            appName = "extreme-edge-cases",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")

            // Chain of dependencies
            val trigger1 by option("--trigger1").bool().default(false)
            val trigger2 by option("--trigger2").int().requireIfValue(::trigger1) { it == true }
            val trigger3 by option("--trigger3").float().requireIfValue(::trigger2) { it != null && it > 10 }
            val final by option("--final").oneOf("yes", "no").requireIfValue(::trigger3) { it != null && it > 5.0f }

            // Circular-like constraints (but not actually circular)
            val groupA by option("--group-a").map { it }.list().atMostOneWith(::groupB)
            val groupB by option("--group-b").map { it }.set().atMostOneWith(::groupC)
            val groupC by option("--group-c").bool().list()

            // Complex mixed constraints
            val complex1 by option("--complex1").int().exactlyOneWith(::complex2).conflictsWith(::complex3)
            val complex2 by option("--complex2").float().requireIfAllPresent(::trigger1, ::groupA)
            val complex3 by option("--complex3").default("default")
            val complex4 by option("--complex4").bool()

            val file by positional()
        }

        // Test valid chain activation
        val a1 = ExtremeEdgeCases()
        a1.parseWithException(arrayOf("alpha",
            "--trigger1",
            "--trigger2", "15",
            "--trigger3", "6.0",
            "--final", "yes",
            "--complex1", "42",
            "f"))
        assertTrue(a1.trigger1)
        assertEquals(15, a1.trigger2)
        assertEquals(6.0f, a1.trigger3)
        assertEquals("yes", a1.final)
        assertEquals(42, a1.complex1)
        assertNull(a1.complex2)
        assertEquals("default", a1.complex3)
        assertNull(a1.complex4)

        // Test group B with complex2 requirements
        val a2 = ExtremeEdgeCases()
        a2.parseWithException(arrayOf("alpha",
            "--trigger1",
            "--trigger2", "5",
            "--group-a", "item",
            "--complex2", "3.14",
            "f"))
        assertTrue(a2.trigger1)
        assertEquals(5, a2.trigger2)
        assertNull(a2.trigger3)
        assertNull(a2.final)
        assertEquals(listOf("item"), a2.groupA)
        assertEquals(emptySet<String>(), a2.groupB)
        assertEquals(0, a2.groupC.size)
        assertNull(a2.complex1)
        assertEquals(3.14f, a2.complex2)

        // Test failure: incomplete chain
        val a3 = ExtremeEdgeCases()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "15", "--trigger3", "6.0", "f")) // missing final
        }

        // Test failure: trigger1 and groupA present but complex2 missing (requireIfAllPresent)
        val a4 = ExtremeEdgeCases()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "5", "--group-a", "item", "f")) // trigger1 and groupA present but complex2 missing
        }

        // Test failure: exactlyOne not satisfied
        val a5 = ExtremeEdgeCases()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("alpha", "f")) // neither complex1 nor complex2, but complex3 has default so atLeast is satisfied for complex4
        }

        // Test failure: conflicts
        val a6 = ExtremeEdgeCases()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("alpha", "--complex1", "1", "--complex3", "override", "f")) // conflicts
        }
    }

    @Test
    fun stressTest_manyOptions() {
        // Stress test with many options and constraints
        class StressTest : Arguments(
            appName = "stress-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")

            // Generate many options with various types and constraints
            val opt1 by option("--opt1").int().atMostOneWith(::opt2, ::opt3)
            val opt2 by option("--opt2").float()
            val opt3 by option("--opt3").bool()
            val opt4 by option("--opt4").map { it }.list().requireIfAnyPresent(::opt1, ::opt2, ::opt3)
            val opt5 by option("--opt5").map { it }.set().conflictsWith(::opt6)
            val opt6 by option("--opt6").oneOf("a", "b", "c")
            val opt7 by option("--opt7").bool().list().atLeastOneWith(::opt8)
            val opt8 by option("--opt8").bool().default(false)
            val opt9 by option("--opt9").float().requireIfValue(::opt6) { it == "c" }
            val opt10 by option("--opt10").exactlyOneWith(::opt11)
            val opt11 by option("--opt11").default("default11")

            // Add options with "no-" prefixes to test the fix
            val noValue by option("--no-value").int()
            val noDefault by option("--no-default").bool()
            val noList by option("--no-list").map { it }.list()
            val noConflict by option("--no-conflict").float().conflictsWith(::noList)

            val file by positional()
        }

        // Test valid configuration
        val a1 = StressTest()
        a1.parseWithException(arrayOf("alpha",
            "--opt1", "42",
            "--opt4", "required",
            "--opt7", "--opt7", "--opt7",
            "--opt10", "value10",
            "--no-value", "100",
            "--no-default",
            "--no-conflict", "2.5",
            "f"))

        assertEquals(42, a1.opt1)
        assertNull(a1.opt2)
        assertNull(a1.opt3)
        assertEquals(listOf("required"), a1.opt4)
        assertEquals(emptySet<String>(), a1.opt5)
        assertNull(a1.opt6)
        assertEquals(3, a1.opt7.size)
        assertFalse(a1.opt8)
        assertNull(a1.opt9)
        assertEquals("value10", a1.opt10)
        assertEquals("default11", a1.opt11)
        assertEquals(100, a1.noValue)
        assertTrue(a1.noDefault!!) // Boolean option --no-default was provided, should be true
        assertEquals(emptyList<String>(), a1.noList)
        assertEquals(2.5f, a1.noConflict)

        // Test another valid configuration
        val a2 = StressTest()
        a2.parseWithException(arrayOf("alpha",
            "--opt3",
            "--opt4", "item1", "--opt4", "item2",
            "--opt5", "set1", "--opt5", "set2",
            "--opt8",
            "--opt11", "override11",
            "--no-list", "listitem",
            "f"))

        assertNull(a2.opt1)
        assertNull(a2.opt2)
        assertEquals(true, a2.opt3)
        assertEquals(listOf("item1", "item2"), a2.opt4)
        assertEquals(setOf("set1", "set2"), a2.opt5)
        assertNull(a2.opt6)
        assertEquals(0, a2.opt7.size)
        assertTrue(a2.opt8)
        assertNull(a2.opt9)
        assertNull(a2.opt10)
        assertEquals("override11", a2.opt11)
        assertNull(a2.noValue)
        assertNull(a2.noDefault)
        assertEquals(listOf("listitem"), a2.noList)
        assertNull(a2.noConflict)
    }
}
