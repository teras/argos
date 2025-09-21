/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DebugAtMostOneTestArgs : Arguments(
    appName = "debug-at-most-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val m1 by option("--m1").bool().default(false).atMostOneWith(::m2)
    val m2 by option("--m2").bool().default(false)
    val file by positional()
}

class DebugTest {
    @Test
    fun debugAtMostOne() {
        val a = DebugAtMostOneTestArgs()

        // This should work - only providing m1
        a.parseWithException(arrayOf("alpha", "--m1", "f"))

        println("After parsing: m1=${a.m1}, m2=${a.m2}")
        assertTrue(a.m1)
        assertFalse(a.m2)
    }

    @Test
    fun debugNullableBool() {
        // Test with the existing TestArgs to see how g1 works
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "f"))
        println("g1=${a.g1}")

        // Now test our custom option
        val b = MixedDefaultAtMostOneTestArgs()
        b.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("hasDefault=${b.hasDefault}, noDefault=${b.noDefault}")
    }

    @Test
    fun investigateNullableBooleanBehavior() {
        // Test different boolean configurations
        class BoolTestArgs : Arguments(
            appName = "bool-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val nullableBool by option("--nullable").bool()  // Boolean?
            val nonNullableBool by option("--nonnull").bool().default(false)  // Boolean
            val nullableWithTrue by option("--null-true").bool().default(true)  // Boolean
            val file by positional()
        }

        println("=== Testing nullable Boolean? behavior ===")

        val a1 = BoolTestArgs()
        a1.parseWithException(arrayOf("alpha", "--nullable", "f"))
        println("--nullable provided: ${a1.nullableBool}")

        val a2 = BoolTestArgs()
        a2.parseWithException(arrayOf("alpha", "f"))
        println("--nullable not provided: ${a2.nullableBool}")

        val a3 = BoolTestArgs()
        a3.parseWithException(arrayOf("alpha", "--nonnull", "f"))
        println("--nonnull provided: ${a3.nonNullableBool}")

        val a4 = BoolTestArgs()
        a4.parseWithException(arrayOf("alpha", "f"))
        println("--nonnull not provided: ${a4.nonNullableBool}")

        val a5 = BoolTestArgs()
        a5.parseWithException(arrayOf("alpha", "--null-true", "f"))
        println("--null-true provided: ${a5.nullableWithTrue}")

        println("=== Reproducing the specific failing case ===")
        val mixed1 = MixedDefaultAtMostOneTestArgs()
        mixed1.parseWithException(arrayOf("alpha", "--no-default", "f"))
        println("MixedDefaultAtMostOneTestArgs --no-default with f after: ${mixed1.noDefault}")

        val mixed2 = MixedDefaultAtMostOneTestArgs()
        mixed2.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("MixedDefaultAtMostOneTestArgs --no-default with f before: ${mixed2.noDefault}")

        // Skip the problematic explicit true test for now

        // Test what 'f' parses to as a boolean
        val mixed4 = MixedDefaultAtMostOneTestArgs()
        mixed4.parseWithException(arrayOf("alpha", "--no-default", "false"))
        println("MixedDefaultAtMostOneTestArgs --no-default false: ${mixed4.noDefault}, file: ${mixed4.file}")

        // Test the exact failing case more carefully
        println("=== Debugging the exact failing scenario ===")
        val mixed5 = MixedDefaultAtMostOneTestArgs()
        mixed5.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("Exact failing case: f=${mixed5.file}, noDefault=${mixed5.noDefault}")

        // Compare with working TestArgs case
        val testArgs = TestArgs()
        testArgs.parseWithException(arrayOf("alpha", "f", "--g1"))
        println("TestArgs comparison: file=${testArgs.file}, g1=${testArgs.g1}")
    }
}
