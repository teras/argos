/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentRequiresValueBehaviorTest {

    @Test
    fun testCurrentRequiresValueFalseBehavior() {
        class TestArgs : Arguments() {
            val debug by option("--debug").requiresValue(false)
            val file by positional()
        }

        println("=== Current requiresValue(false) behavior ===")

        // Test 1: --debug followed by another option (non-existent)
        println("\n1. --debug followed by non-existent option:")
        val args1 = TestArgs()
        try {
            args1.parseWithException(arrayOf("--debug", "--nonexistent"))
            println("  debug = '${args1.debug}' (null=${args1.debug == null})")
            println("  file = '${args1.file}'")
        } catch (e: Exception) {
            println("  Error: ${e.message}")
        }

        // Test 2: --debug followed by a value
        println("\n2. --debug followed by a potential value:")
        val args2 = TestArgs()
        try {
            args2.parseWithException(arrayOf("--debug", "somevalue"))
            println("  debug = '${args2.debug}' (null=${args2.debug == null})")
            println("  file = '${args2.file}'")
        } catch (e: Exception) {
            println("  Error: ${e.message}")
        }

        // Test 3: --debug at end
        println("\n3. --debug at end:")
        val args3 = TestArgs()
        try {
            args3.parseWithException(arrayOf("inputfile.txt", "--debug"))
            println("  debug = '${args3.debug}' (null=${args3.debug == null})")
            println("  file = '${args3.file}'")
        } catch (e: Exception) {
            println("  Error: ${e.message}")
        }

        // Test 4: Just --debug alone
        println("\n4. Just --debug alone:")
        val args4 = TestArgs()
        try {
            args4.parseWithException(arrayOf("--debug"))
            println("  debug = '${args4.debug}' (null=${args4.debug == null})")
            println("  file = '${args4.file}'")
        } catch (e: Exception) {
            println("  Error: ${e.message}")
        }

        // Demonstrate the expected behavior with assertions
        assertEquals("--nonexistent", args1.debug)
        assertEquals("somevalue", args2.debug)
        assertEquals("", args3.debug) // Flag present, no value
        assertEquals("inputfile.txt", args3.file)
        assertEquals("", args4.debug) // Flag present, no value
    }
}