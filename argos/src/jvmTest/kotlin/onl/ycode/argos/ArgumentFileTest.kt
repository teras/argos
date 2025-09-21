/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import java.io.File
import java.io.IOException
import kotlin.test.*

class ArgumentFileTest {

    private fun createTempFile(content: String): File {
        val tempFile = File.createTempFile("argos_test", ".txt")
        tempFile.deleteOnExit()
        tempFile.writeText(content)
        return tempFile
    }

    // Simple test args for argument file testing
    class SimpleArgs : Arguments(
        appName = "test",
        useANSITerminal = false
    ) {
        val input by option("--input", "-i")
        val output by option("--output", "-o")
        val verbose by option("--verbose", "-v").bool().default(false)
        val count by option("--count", "-c").int().default(1)
        val help by option("--help").bool().default(false)
    }

    @Test
    fun basicArgumentFileExpansion() {
        val argumentFile = createTempFile("--input test.txt --output result.txt --verbose")

        val args = SimpleArgs()
        args.parseWithException(arrayOf("@${argumentFile.absolutePath}"))

        assertEquals("test.txt", args.input)
        assertEquals("result.txt", args.output)
        assertEquals(true, args.verbose)
    }

    @Test
    fun argumentFileWithComments() {
        val content = """
            # This is a comment
            --input data.csv

            # Another comment
            --output results.json
            --count 5
        """.trimIndent()

        val argumentFile = createTempFile(content)

        val args = SimpleArgs()
        args.parseWithException(arrayOf("@${argumentFile.absolutePath}"))

        assertEquals("data.csv", args.input)
        assertEquals("results.json", args.output)
        assertEquals(5, args.count)
    }

    @Test
    fun argumentFileWithMixedArguments() {
        val argumentFile = createTempFile("--input config.txt --verbose")

        val args = SimpleArgs()
        args.parseWithException(arrayOf("--count", "10", "@${argumentFile.absolutePath}", "--output", "final.txt"))

        assertEquals("config.txt", args.input)
        assertEquals("final.txt", args.output)
        assertEquals(true, args.verbose)
        assertEquals(10, args.count)
    }

    @Test
    fun multilineArgumentFile() {
        val content = """
            --input
            input.txt
            --output
            output.txt
            --count
            42
        """.trimIndent()

        val argumentFile = createTempFile(content)

        val args = SimpleArgs()
        args.parseWithException(arrayOf("@${argumentFile.absolutePath}"))

        assertEquals("input.txt", args.input)
        assertEquals("output.txt", args.output)
        assertEquals(42, args.count)
    }

    @Test
    fun missingArgumentFile() {
        val args = SimpleArgs()

        val exception = assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("@nonexistent.txt"))
        }

        assertTrue(exception.message?.contains("Cannot read argument file") == true)
        assertTrue(exception.message?.contains("nonexistent.txt") == true)
    }

    @Test
    fun disabledArgumentFiles() {
        // Test with argumentFilePrefix = null
        class NoArgumentFileArgs : Arguments(
            appName = "test",
            useANSITerminal = false,
            argumentFilePrefix = null
        ) {
            val input by option("--input")
        }

        val argumentFile = createTempFile("--input from_file.txt")

        val args = NoArgumentFileArgs()
        // This should treat @file as a literal argument, not an argument file
        val exception = assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("@${argumentFile.absolutePath}"))
        }

        // Should fail because @file is treated as unexpected positional argument
        assertTrue(exception.message?.contains("Unexpected positional argument") == true)
    }

    @Test
    fun customArgumentFilePrefix() {
        class CustomPrefixArgs : Arguments(
            appName = "test",
            useANSITerminal = false,
            argumentFilePrefix = '%'
        ) {
            val input by option("--input")
        }

        val argumentFile = createTempFile("--input custom.txt")

        val args = CustomPrefixArgs()
        args.parseWithException(arrayOf("%${argumentFile.absolutePath}"))

        assertEquals("custom.txt", args.input)
    }

    @Test
    fun emptyArgumentFile() {
        val argumentFile = createTempFile("")

        val args = SimpleArgs()
        args.parseWithException(arrayOf("@${argumentFile.absolutePath}", "--input", "test.txt"))

        assertEquals("test.txt", args.input)
        assertNull(args.output)
    }

    @Test
    fun argumentFileWithOnlyComments() {
        val content = """
            # Just comments
            # Nothing else
        """.trimIndent()

        val argumentFile = createTempFile(content)

        val args = SimpleArgs()
        args.parseWithException(arrayOf("@${argumentFile.absolutePath}", "--input", "test.txt"))

        assertEquals("test.txt", args.input)
        assertNull(args.output)
    }
}