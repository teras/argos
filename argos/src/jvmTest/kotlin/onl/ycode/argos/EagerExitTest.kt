/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class EagerExitTest {

    class TestEagerArgs : Arguments() {
        val help by option("--help").bool().eager().help("Show help")
        val requiredString by option("--required").required().help("Required string option")
        val optionalString by option("--optional").help("Optional string option")
        val requiredInt by option("--int").int().required().help("Required int option")
        val optionalList by option("--list").list().help("Optional list option")
        val requiredCount by option("-v", "--verbose").bool().list().required().help("Required count option")
    }

    @Test
    fun eagerExitWithNonNullableAccess_handlesGracefully() {
        val args = TestEagerArgs()
        args.parseWithException(arrayOf("--help"))

        // Eager option should be accessible
        assertEquals(true, args.help)

        // Non-nullable options should either return a value or throw descriptive error (not NPE)
        try {
            val value = args.requiredString
            // If it returns a value, that's acceptable behavior
            println("requiredString returned: $value")
        } catch (e: UninitializedPropertyException) {
            // If it throws our custom exception, that's also acceptable
            assertTrue(e.message!!.contains("requiredString"))
            assertTrue(e.message!!.contains("not initialized"))
        } catch (e: NullPointerException) {
            fail("Should not throw NullPointerException, but got: ${e.message}")
        }
    }

    @Test
    fun eagerExitWithNullableAccess_returnsNull() {
        val args = TestEagerArgs()
        args.parseWithException(arrayOf("--help"))

        // Eager option should be accessible
        assertEquals(true, args.help)

        // Nullable options should return null without error
        assertEquals(null, args.optionalString)
        assertEquals(emptyList(), args.optionalList)
    }

    @Test
    fun eagerExitWithDifferentTypes_handlesGracefully() {
        val args = TestEagerArgs()
        args.parseWithException(arrayOf("--help"))

        // Test different non-nullable types - should not throw NPE
        listOf(
            "requiredString" to { args.requiredString },
            "requiredInt" to { args.requiredInt },
            "requiredCount" to { args.requiredCount }
        ).forEach { (name, accessor) ->
            try {
                val value = accessor()
                println("$name returned: $value")
            } catch (e: UninitializedPropertyException) {
                // Custom exception is acceptable - should contain property name
                assertTrue(e.message!!.contains(name), "Expected '$name' in message: ${e.message}")
            }
        }
    }

    @Test
    fun normalParsingWithoutEager_worksAsExpected() {
        val args = TestEagerArgs()
        args.parseWithException(arrayOf("--required", "test", "--int", "42", "-vv"))

        // All options should be accessible normally
        assertEquals(null, args.help)
        assertEquals("test", args.requiredString)
        assertEquals(42, args.requiredInt)
        assertEquals(2, args.requiredCount.size)
        assertEquals(null, args.optionalString)
    }

    @Test
    fun eagerAfterOtherOptions_stillCausesEarlyExit() {
        val args = TestEagerArgs()
        args.parseWithException(arrayOf("--required", "test", "--help", "--int", "42"))

        // Eager should be triggered
        assertEquals(true, args.help)

        // Required option before eager should be accessible
        assertEquals("test", args.requiredString)

        // Options after eager should not be processed - should throw some exception
        try {
            val value = args.requiredInt
            fail("Expected an exception but got value: $value")
        } catch (e: UninitializedPropertyException) {
            // Our custom exception is preferred
            assertTrue(e.message!!.contains("requiredInt"))
        }
    }
}