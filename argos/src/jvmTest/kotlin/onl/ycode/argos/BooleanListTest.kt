/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BooleanListTest {

    class BooleanListArgs : Arguments(
        appName = "boolean-list-test",
        useANSITerminal = false
    ) {
        val verbose by option("--verbose").bool().list()
        val debug by option("--debug").bool().negatable().list()  // Syntax 1: .bool().negatable().list()
        val flags by option("--flag").bool().list().negatable("disable-")  // Syntax 2: .bool().list().negatable()
        val features by option("--feature").bool().set().negatable("turn-off-")  // Boolean set with negation
        val quiet by option("--quiet").bool().list()
        val file by positional()
    }

    class SingleBooleanArgs : Arguments(
        appName = "single-boolean-test",
        useANSITerminal = false
    ) {
        val debug by option("--debug").bool().negatable()
        val flag by option("--flag").bool().negatable("disable-")
        val customPrefix by option("--custom").bool().negatable("turn-off-")
        val file by positional()
    }

    @Test
    fun booleanList_collectsMultipleTrue() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--debug", "--debug", "--debug"))

        assertEquals(listOf(true, true, true), args.debug)
    }

    @Test
    fun booleanList_regularCollectsTrue() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--verbose", "--verbose", "--verbose"))

        assertEquals(listOf(true, true, true), args.verbose)
    }

    @Test
    fun booleanList_explicitValueBehavior() {
        // "--debug false" and "--debug true" do consume the boolean literals
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--debug", "false", "--debug", "true", "actualfile.txt"))

        // Boolean parsing: "false" -> false, "true" -> true (consumed as boolean values)
        assertEquals(listOf(false, true), args.debug)
        assertEquals("actualfile.txt", args.file) // Only non-boolean strings become positional
    }

    @Test
    fun booleanList_flagWithFile() {
        // "--debug" flag followed by non-boolean string treats it as positional
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--debug", "somefile.txt"))

        assertEquals(listOf(true), args.debug) // --debug without value defaults to true
        assertEquals("somefile.txt", args.file) // "somefile.txt" becomes positional
    }

    @Test
    fun booleanList_mixedWithOtherOptions() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--debug", "--verbose", "--no-debug", "--verbose", "myfile.txt"))

        assertEquals(listOf(true, false), args.debug)
        assertEquals(listOf(true, true), args.verbose)
        assertEquals(emptyList<Boolean>(), args.quiet)
        assertEquals("myfile.txt", args.file)
    }

    @Test
    fun booleanList_emptyLists() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("somefile.txt"))

        assertEquals(emptyList<Boolean>(), args.debug)
        assertEquals(emptyList<Boolean>(), args.verbose)
        assertEquals("somefile.txt", args.file)
    }

    @Test
    fun booleanList_negatable_mixedTrueAndFalse() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--debug", "--no-debug", "--debug", "--no-debug"))

        assertEquals(listOf(true, false, true, false), args.debug)
    }

    @Test
    fun booleanList_negatable_onlyNegative() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--no-debug", "--no-debug"))

        assertEquals(listOf(false, false), args.debug)
    }

    @Test
    fun booleanList_customNegationPrefix_listDotNegatable() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--flag", "--disable-flag", "--flag"))

        assertEquals(listOf(true, false, true), args.flags)
    }

    @Test
    fun booleanSet_customNegationPrefix() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--feature", "--turn-off-feature", "--feature"))

        assertEquals(setOf(true, false), args.features) // Set deduplicates: {true, false}
    }

    @Test
    fun booleanList_nonNegatableCannotNegate() {
        val args = BooleanListArgs()

        // --no-verbose should fail because verbose is not negatable
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--no-verbose"))
        }
    }

    @Test
    fun booleanList_clusteredShortOptions() {
        class ShortBooleanArgs : Arguments(
            appName = "short-boolean-test",
            useANSITerminal = false
        ) {
            val verbose by option("-v", "--verbose").bool().list()
            val debug by option("-d", "--debug").bool().list()
        }

        val args = ShortBooleanArgs()
        args.parseWithException(arrayOf("-vvv", "-dd"))

        assertEquals(listOf(true, true, true), args.verbose)
        assertEquals(listOf(true, true), args.debug)
    }

    @Test
    fun booleanList_valueSourceTracking() {
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--debug", "--no-debug"))

        assertEquals(listOf(true, false), args.debug)
        assertEquals(ValueSource.USER, args.valueSourceOf(args::debug))
    }

    @Test
    fun booleanList_negatableExplicitValues() {
        // Test the "--no-debug false" scenario you asked about with lists
        val args = BooleanListArgs()
        args.parseWithException(arrayOf("--no-debug", "false", "--debug", "true"))

        // For negatable boolean lists: --no-debug false -> negated(false) = true
        assertEquals(listOf(true, true), args.debug)
        assertEquals(null, args.file) // Values consumed
    }

    @Test
    fun booleanCollections_bothSyntaxesWork() {
        // Test that both .bool().negatable().list() and .bool().list().negatable() work
        class BothSyntaxesArgs : Arguments(appName = "both-syntaxes", useANSITerminal = false) {
            val syntax1 by option("--s1").bool().negatable().list()  // .bool().negatable().list()
            val syntax2 by option("--s2").bool().list().negatable()  // .bool().list().negatable()
            val syntax3 by option("--s3").bool().set().negatable()   // .bool().set().negatable()
        }

        val args = BothSyntaxesArgs()
        args.parseWithException(arrayOf(
            "--s1", "--no-s1", "--s1",
            "--s2", "--no-s2", "--s2",
            "--s3", "--no-s3", "--s3"
        ))

        assertEquals(listOf(true, false, true), args.syntax1)
        assertEquals(listOf(true, false, true), args.syntax2)
        assertEquals(setOf(true, false), args.syntax3)  // Set deduplicates
    }


    // ===== Tests for Single Boolean Negation (what DOES work) =====

    @Test
    fun singleBoolean_negatable_defaultPrefix() {
        val args = SingleBooleanArgs()
        args.parseWithException(arrayOf("--no-debug"))

        assertEquals(false, args.debug)
    }

    @Test
    fun singleBoolean_negatable_customPrefix() {
        val args = SingleBooleanArgs()
        args.parseWithException(arrayOf("--disable-flag"))

        assertEquals(false, args.flag)
    }

    @Test
    fun singleBoolean_negatable_longerCustomPrefix() {
        val args = SingleBooleanArgs()
        args.parseWithException(arrayOf("--turn-off-custom"))

        assertEquals(false, args.customPrefix)
    }

    @Test
    fun singleBoolean_explicitValueIgnored_treatAsPositional() {
        // Test the "--no-debug false" scenario the user asked about
        // Observation: --no-debug with "false" following actually parses "false" as a boolean value!
        // This means --no-debug false results in: negated(parseBooleanLiteral("false")) = negated(false) = true
        val args = SingleBooleanArgs()
        args.parseWithException(arrayOf("--no-debug", "false"))

        assertEquals(true, args.debug) // --no-debug with "false" -> negated(false) = true!
        assertEquals(null, args.file) // "false" consumed as boolean value
    }

    @Test
    fun singleBoolean_positiveNoValue_remainsNull() {
        // Without argument, nullable boolean remains null
        val args = SingleBooleanArgs()
        args.parseWithException(arrayOf("somefile.txt"))

        assertEquals(null, args.debug) // No --debug provided, remains null
        assertEquals("somefile.txt", args.file)
    }

    @Test
    fun singleBoolean_counterintuitiveNegationBehavior() {
        // Counterintuitive: --no-debug true results in false!
        // Because: negated(parseBooleanLiteral("true")) = negated(true) = false
        val args = SingleBooleanArgs()
        args.parseWithException(arrayOf("--no-debug", "true"))

        assertEquals(false, args.debug) // --no-debug with "true" -> negated(true) = false!
        assertEquals(null, args.file) // "true" consumed as boolean value
    }
}