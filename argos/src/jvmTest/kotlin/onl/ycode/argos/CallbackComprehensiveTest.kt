/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for the callback mechanism in Argos.
 *
 * This test suite covers all aspects of the callback functionality:
 * - Basic callback execution
 * - Type conversions and transformations
 * - Order preservation for single and list options
 * - Mixed single and list option scenarios
 * - Error handling and edge cases
 * - Default value behavior
 */
class CallbackComprehensiveTest {

    // ========================================
    // Basic Callback Functionality
    // ========================================

    @Test
    fun basicStringOptionCallback() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val name by option("--name")
                .onValue { value -> executionLog.add("NAME: $value") }
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--name", "test"))

        assertEquals(1, executionLog.size)
        assertEquals("NAME: test", executionLog[0])
        assertEquals("test", args.name)
    }

    @Test
    fun basicCallbackExecution() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val name by option("--name")
                .onValue { value ->
                    executionLog.add("NAME: $value")
                }

            val count by option("--count")
                .int()
                .onValue { value ->
                    executionLog.add("COUNT: $value")
                }

            val enabled by option("--enabled")
                .bool()
                .onValue { value ->
                    executionLog.add("ENABLED: $value")
                }
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--name", "test", "--count", "42", "--enabled"))

        // Verify callbacks were executed
        assertEquals(3, executionLog.size)
        assertTrue(executionLog.contains("NAME: test"))
        assertTrue(executionLog.contains("COUNT: 42"))
        assertTrue(executionLog.contains("ENABLED: true"))

        // Verify original values are still accessible
        assertEquals("test", args.name)
        assertEquals(42, args.count)
        assertEquals(true, args.enabled)
    }

    @Test
    fun callbackExecutionWithNullValues() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val optional by option("--optional")
                .onValue { value ->
                    executionLog.add("OPTIONAL: $value")
                }

            val provided by option("--provided")
                .onValue { value ->
                    executionLog.add("PROVIDED: $value")
                }
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--provided", "value"))

        // Only the provided option should trigger a callback
        assertEquals(1, executionLog.size)
        assertEquals("PROVIDED: value", executionLog[0])

        // Verify values
        assertEquals(null, args.optional)
        assertEquals("value", args.provided)
    }

    // ========================================
    // Type Conversion and Transformation
    // ========================================

    @Test
    fun intOptionCallback() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val count by option("--count")
                .int()
                .onValue { value ->
                    executionLog.add("COUNT: $value (${value?.javaClass?.simpleName})")
                }
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--count", "42"))

        assertEquals(1, executionLog.size)
        assertEquals("COUNT: 42 (Integer)", executionLog[0])
        assertEquals(42, args.count)
    }

    @Test
    fun boolOptionCallback() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val enabled by option("--enabled")
                .bool()
                .onValue { value ->
                    executionLog.add("ENABLED: $value (${value?.javaClass?.simpleName})")
                }
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--enabled"))

        assertEquals(1, executionLog.size)
        assertEquals("ENABLED: true (Boolean)", executionLog[0])
        assertEquals(true, args.enabled)
    }

    @Test
    fun typeConversionsInCorrectOrder() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val name by option("--name").onValue { value ->
                executionLog.add("NAME: $value (${value?.javaClass?.simpleName})")
            }
            val count by option("--count").int().onValue { value ->
                executionLog.add("COUNT: $value (${value?.javaClass?.simpleName})")
            }
            val rate by option("--rate").float().onValue { value ->
                executionLog.add("RATE: $value (${value?.javaClass?.simpleName})")
            }
            val enabled by option("--enabled").bool().onValue { value ->
                executionLog.add("ENABLED: $value (${value?.javaClass?.simpleName})")
            }
        }

        val args = TestArgs()
        // Reverse order from property definition
        args.parseWithException(arrayOf(
            "--enabled",
            "--rate", "3.14",
            "--count", "42",
            "--name", "test"
        ))

        // Should receive converted types in command-line order
        assertEquals(4, executionLog.size)
        assertEquals("ENABLED: true (Boolean)", executionLog[0])
        assertEquals("RATE: 3.14 (Float)", executionLog[1])
        assertEquals("COUNT: 42 (Integer)", executionLog[2])
        assertEquals("NAME: test (String)", executionLog[3])
    }

    // ========================================
    // List Option Callbacks (Individual per Occurrence)
    // ========================================

    @Test
    fun callbackWithListOptions() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val items by option("--item")
                .list()
                .onValue { value ->
                    executionLog.add("ITEM: $value")
                }
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--item", "first", "--item", "second", "--item", "third"))

        // For list options, callback should be executed once per occurrence with individual values
        assertEquals(3, executionLog.size)
        assertEquals("ITEM: first", executionLog[0])
        assertEquals("ITEM: second", executionLog[1])
        assertEquals("ITEM: third", executionLog[2])

        // Verify list values
        assertEquals(listOf("first", "second", "third"), args.items)
    }

    @Test
    fun listOptionsWithRepeats() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            // List options can be repeated
            val items by option("--item").list().onValue {
                executionLog.add("ITEMS: $it")
            }
            val tags by option("--tag").list().onValue {
                executionLog.add("TAGS: $it")
            }
            val numbers by option("--num").int().list().onValue {
                executionLog.add("NUMBERS: $it")
            }
        }

        val args = TestArgs()
        // Multiple occurrences of each list option
        args.parseWithException(arrayOf(
            "--item", "first",
            "--tag", "red",
            "--num", "10",
            "--item", "second",
            "--tag", "blue",
            "--num", "20",
            "--item", "third"
        ))

        // Each list option should execute callback once per occurrence with individual values
        // Order should be command-line order
        assertEquals(7, executionLog.size)
        assertEquals("ITEMS: first", executionLog[0])    // First --item
        assertEquals("TAGS: red", executionLog[1])       // First --tag
        assertEquals("NUMBERS: 10", executionLog[2])     // First --num
        assertEquals("ITEMS: second", executionLog[3])   // Second --item
        assertEquals("TAGS: blue", executionLog[4])      // Second --tag
        assertEquals("NUMBERS: 20", executionLog[5])     // Second --num
        assertEquals("ITEMS: third", executionLog[6])    // Third --item

        // Verify final lists
        assertEquals(listOf("first", "second", "third"), args.items)
        assertEquals(listOf("red", "blue"), args.tags)
        assertEquals(listOf(10, 20), args.numbers)
    }

    @Test
    fun randomOrderListOptions() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            // Properties in order: x, y, z
            val x by option("--x").list().onValue { executionLog.add("X: $it") }
            val y by option("--y").int().list().onValue { executionLog.add("Y: $it") }
            val z by option("--z").list().onValue { executionLog.add("Z: $it") }
        }

        val args = TestArgs()
        // Random interleaved order: z, x, y, z, x, y
        args.parseWithException(arrayOf(
            "--z", "first",
            "--x", "a",
            "--y", "100",
            "--z", "second",
            "--x", "b",
            "--y", "200"
        ))

        // Callbacks execute in command-line order (individual callbacks for list options)
        assertEquals(6, executionLog.size)
        assertEquals("Z: first", executionLog[0])    // First --z
        assertEquals("X: a", executionLog[1])        // First --x
        assertEquals("Y: 100", executionLog[2])      // First --y
        assertEquals("Z: second", executionLog[3])   // Second --z
        assertEquals("X: b", executionLog[4])        // Second --x
        assertEquals("Y: 200", executionLog[5])      // Second --y
    }

    // ========================================
    // Mixed Single and List Options (Order Preservation)
    // ========================================

    @Test
    fun mixedSingleAndListOptions() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            // Mixed: some single, some list
            val name by option("--name").onValue { executionLog.add("NAME: $it") }
            val files by option("--file").list().onValue { executionLog.add("FILES: $it") }
            val port by option("--port").int().onValue { executionLog.add("PORT: $it") }
            val tags by option("--tag").list().onValue { executionLog.add("TAGS: $it") }
            val verbose by option("--verbose").bool().onValue { executionLog.add("VERBOSE: $it") }
        }

        val args = TestArgs()
        args.parseWithException(arrayOf(
            "--file", "a.txt",      // First --file
            "--name", "myapp",      // Single --name
            "--tag", "dev",         // First --tag
            "--file", "b.txt",      // Second --file
            "--port", "8080",       // Single --port
            "--tag", "test",        // Second --tag
            "--verbose",            // Single --verbose
            "--file", "c.txt"       // Third --file
        ))

        // Callbacks execute in command-line order (individual callbacks for list options)
        assertEquals(8, executionLog.size)
        assertEquals("FILES: a.txt", executionLog[0])     // First --file
        assertEquals("NAME: myapp", executionLog[1])      // --name position
        assertEquals("TAGS: dev", executionLog[2])        // First --tag
        assertEquals("FILES: b.txt", executionLog[3])     // Second --file
        assertEquals("PORT: 8080", executionLog[4])       // --port position
        assertEquals("TAGS: test", executionLog[5])       // Second --tag
        assertEquals("VERBOSE: true", executionLog[6])    // --verbose position
        assertEquals("FILES: c.txt", executionLog[7])     // Third --file
    }

    @Test
    fun singleOptionsCallbackOrder() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            // Properties defined in order: a, b, c
            val a by option("--a").onValue { executionLog.add("A: $it") }
            val b by option("--b").int().onValue { executionLog.add("B: $it") }
            val c by option("--c").bool().onValue { executionLog.add("C: $it") }
        }

        val args = TestArgs()
        // Arguments in INVERSE order: c, b, a
        args.parseWithException(arrayOf("--c", "--b", "42", "--a", "test"))

        // Callbacks should execute in COMMAND-LINE order
        assertEquals(3, executionLog.size)
        assertEquals("C: true", executionLog[0])  // First on command line
        assertEquals("B: 42", executionLog[1])    // Second on command line
        assertEquals("A: test", executionLog[2])  // Third on command line

        // Verify final values
        assertEquals("test", args.a)
        assertEquals(42, args.b)
        assertEquals(true, args.c)
    }

    @Test
    fun randomOrderSingleOptions() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            // Properties in alphabetical order: alpha, beta, gamma, delta
            val alpha by option("--alpha").onValue { executionLog.add("ALPHA: $it") }
            val beta by option("--beta").int().onValue { executionLog.add("BETA: $it") }
            val gamma by option("--gamma").bool().onValue { executionLog.add("GAMMA: $it") }
            val delta by option("--delta").onValue { executionLog.add("DELTA: $it") }
        }

        val args = TestArgs()
        // Arguments in completely random order: delta, gamma, alpha, beta
        args.parseWithException(arrayOf(
            "--delta", "last",
            "--gamma",
            "--alpha", "first",
            "--beta", "42"
        ))

        // Callbacks execute in command-line order, NOT property definition order
        assertEquals(4, executionLog.size)
        assertEquals("DELTA: last", executionLog[0])   // First on command line
        assertEquals("GAMMA: true", executionLog[1])   // Second on command line
        assertEquals("ALPHA: first", executionLog[2])  // Third on command line
        assertEquals("BETA: 42", executionLog[3])      // Fourth on command line
    }

    // ========================================
    // Default Values and Edge Cases
    // ========================================

    @Test
    fun callbackWithDefaultValues() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val port by option("--port")
                .int()
                .default(8080)
                .onValue { value ->
                    executionLog.add("PORT: $value")
                }

            val name by option("--name")
                .default("default-name")
                .onValue { value ->
                    executionLog.add("NAME: $value")
                }
        }

        // Test with no arguments - callbacks should NOT execute with default values
        val args1 = TestArgs()
        args1.parseWithException(arrayOf())

        // Callbacks should NOT execute for default values (no user input)
        assertEquals(0, executionLog.size)

        // Test with provided arguments
        executionLog.clear()
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--port", "3000", "--name", "custom"))

        assertEquals(2, executionLog.size)
        assertTrue(executionLog.contains("PORT: 3000"))
        assertTrue(executionLog.contains("NAME: custom"))
    }

    @Test
    fun callbacksNotExecutedOnParseError() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val count by option("--count")
                .int()
                .required()
                .onValue { value ->
                    executionLog.add("COUNT: $value")
                }

            val name by option("--name")
                .required()
                .onValue { value ->
                    executionLog.add("NAME: $value")
                }
        }

        val args = TestArgs()

        // Test with missing required option - should throw exception and not execute callbacks
        try {
            args.parseWithException(arrayOf("--count", "42"))  // missing required --name
        } catch (e: ParseError) {
            // Expected
        }

        // No callbacks should have been executed due to validation failure
        assertEquals(0, executionLog.size)
    }

    @Test
    fun debugBooleanCallback() {
        val executionLog = mutableListOf<String>()

        class TestArgs : Arguments() {
            val enabled by option("--enabled")
                .bool()
                .onValue { value ->
                    executionLog.add("ENABLED: $value")
                }
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--enabled"))

        assertEquals(1, executionLog.size)
        assertEquals("ENABLED: true", executionLog[0])
        assertEquals(true, args.enabled)
    }
}