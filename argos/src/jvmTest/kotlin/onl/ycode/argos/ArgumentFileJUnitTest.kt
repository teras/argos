/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

// Test Arguments classes for argument file testing
private class ArgumentFileTestArgs : Arguments(
    appName = "test-app",
    useANSITerminal = false
) {
    val input by option("--input", "-i")
    val output by option("--output", "-o")
    val verbose by option("--verbose", "-v").bool().default(false)
    val count by option("--count", "-c").int().default(1)
    val tags by option("--tag").list()
}

private class ArgumentFileCustomPrefixArgs : Arguments(
    appName = "custom-prefix-test",
    useANSITerminal = false,
    argumentFilePrefix = '%'
) {
    val input by option("--input")
    val verbose by option("--verbose").bool().default(false)
}

private class ArgumentFileDisabledArgs : Arguments(
    appName = "disabled-file-test",
    useANSITerminal = false,
    argumentFilePrefix = null
) {
    val input by option("--input")
}

@DisplayName("Argument File Support (@file)")
class ArgumentFileJUnitTest {

    private lateinit var tempConfigFile: File

    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        tempConfigFile = tempDir.resolve("config.txt").toFile()
    }

    private fun createConfigFile(content: String): File {
        tempConfigFile.writeText(content)
        return tempConfigFile
    }

    @Nested
    @DisplayName("Basic Functionality")
    inner class BasicFunctionality {

        @Test
        @DisplayName("Should expand simple argument file")
        fun expandSimpleArgumentFile() {
            val configFile = createConfigFile("--input data.txt --output result.txt --verbose")

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}"))

            assertEquals("data.txt", args.input)
            assertEquals("result.txt", args.output)
            assertTrue(args.verbose)
            assertEquals(1, args.count) // default value
            assertTrue(args.tags.isEmpty())
        }

        @Test
        @DisplayName("Should handle multiline argument files")
        fun handleMultilineArgumentFile() {
            val content = """
                --input
                multiline.txt
                --count
                42
                --verbose
            """.trimIndent()

            val configFile = createConfigFile(content)

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}"))

            assertEquals("multiline.txt", args.input)
            assertEquals(42, args.count)
            assertTrue(args.verbose)
        }

        @Test
        @DisplayName("Should mix argument file with command line arguments")
        fun mixArgumentFileWithCommandLine() {
            val configFile = createConfigFile("--input config.txt --verbose")

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf(
                "--count", "10",
                "@${configFile.absolutePath}",
                "--output", "final.txt"
            ))

            assertEquals("config.txt", args.input)
            assertEquals("final.txt", args.output)
            assertTrue(args.verbose)
            assertEquals(10, args.count)
        }
    }

    @Nested
    @DisplayName("Comment Support")
    inner class CommentSupport {

        @Test
        @DisplayName("Should ignore comment lines starting with #")
        fun ignoreCommentLines() {
            val content = """
                # This is a configuration file
                --input data.csv

                # Set output format
                --output results.json
                --count 5
                # Enable verbose logging
                --verbose
            """.trimIndent()

            val configFile = createConfigFile(content)

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}"))

            assertEquals("data.csv", args.input)
            assertEquals("results.json", args.output)
            assertEquals(5, args.count)
            assertTrue(args.verbose)
        }

        @Test
        @DisplayName("Should handle file with only comments and blank lines")
        fun handleFileWithOnlyCommentsAndBlankLines() {
            val content = """
                # Just comments

                # Nothing else here

            """.trimIndent()

            val configFile = createConfigFile(content)

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}", "--input", "manual.txt"))

            assertEquals("manual.txt", args.input)
            assertNull(args.output)
            assertFalse(args.verbose)
        }
    }

    @Nested
    @DisplayName("List Arguments")
    inner class ListArguments {

        @Test
        @DisplayName("Should handle multiple tag arguments from file")
        fun handleMultipleTagArguments() {
            val content = """
                --input services.yml
                --tag production
                --tag backend
                --tag api
                --tag monitoring
            """.trimIndent()

            val configFile = createConfigFile(content)

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}"))

            assertEquals("services.yml", args.input)
            assertEquals(listOf("production", "backend", "api", "monitoring"), args.tags)
        }

        @Test
        @DisplayName("Should combine file tags with command line tags")
        fun combineFileTagsWithCommandLineTags() {
            val configFile = createConfigFile("--tag file-tag1 --tag file-tag2")

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf(
                "--tag", "cli-tag1",
                "@${configFile.absolutePath}",
                "--tag", "cli-tag2"
            ))

            assertEquals(listOf("cli-tag1", "file-tag1", "file-tag2", "cli-tag2"), args.tags)
        }
    }

    @Nested
    @DisplayName("Configuration Options")
    inner class ConfigurationOptions {

        @Test
        @DisplayName("Should work with custom prefix character")
        fun workWithCustomPrefixCharacter() {
            val configFile = createConfigFile("--input custom.txt --verbose")

            val args = ArgumentFileCustomPrefixArgs()
            args.parseWithException(arrayOf("%${configFile.absolutePath}"))

            assertEquals("custom.txt", args.input)
            assertTrue(args.verbose)
        }

        @Test
        @DisplayName("Should fail when using @ with custom % prefix")
        fun failWhenUsingAtWithCustomPercentPrefix() {
            val configFile = createConfigFile("--input should-not-work.txt")

            val args = ArgumentFileCustomPrefixArgs()

            assertThrows(ParseError::class.java) {
                args.parseWithException(arrayOf("@${configFile.absolutePath}"))
            }
        }

        @Test
        @DisplayName("Should treat @file as literal argument when disabled")
        fun treatAtFileAsLiteralWhenDisabled() {
            val configFile = createConfigFile("--input from-file.txt")

            val args = ArgumentFileDisabledArgs()

            val exception = assertThrows(ParseError::class.java) {
                args.parseWithException(arrayOf("@${configFile.absolutePath}"))
            }

            assertTrue(exception.message?.contains("Unexpected positional argument") == true)
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {

        @Test
        @DisplayName("Should throw clear error for missing file")
        fun throwClearErrorForMissingFile() {
            val args = ArgumentFileTestArgs()

            val exception = assertThrows(ParseError::class.java) {
                args.parseWithException(arrayOf("@/nonexistent/file.txt"))
            }

            assertTrue(exception.message?.contains("Cannot read argument file") == true)
            assertTrue(exception.message?.contains("/nonexistent/file.txt") == true)
        }

        @Test
        @DisplayName("Should handle empty argument file gracefully")
        fun handleEmptyArgumentFileGracefully() {
            val configFile = createConfigFile("")

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}", "--input", "test.txt"))

            assertEquals("test.txt", args.input)
            assertNull(args.output)
            assertFalse(args.verbose)
        }

        @Test
        @DisplayName("Should validate arguments from file same as command line")
        fun validateArgumentsFromFileSameAsCommandLine() {
            // Create a file with missing required argument
            val configFile = createConfigFile("--verbose --count 10")

            val args = ArgumentFileTestArgs()

            // This should work fine since no arguments are actually required in TestArgs
            assertDoesNotThrow {
                args.parseWithException(arrayOf("@${configFile.absolutePath}"))
            }

            assertTrue(args.verbose)
            assertEquals(10, args.count)
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    inner class IntegrationScenarios {

        @Test
        @DisplayName("Should work with complex real-world scenario")
        fun workWithComplexRealWorldScenario() {
            val content = """
                # Application configuration
                --input /data/input.json
                --output /results/processed.json

                # Processing options
                --count 100
                --verbose

                # Tags for categorization
                --tag production
                --tag batch-job
                --tag data-processing
            """.trimIndent()

            val configFile = createConfigFile(content)

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}"))

            assertEquals("/data/input.json", args.input)
            assertEquals("/results/processed.json", args.output)
            assertEquals(100, args.count)
            assertTrue(args.verbose)
            assertEquals(listOf("production", "batch-job", "data-processing"), args.tags)
        }

        @Test
        @DisplayName("Should handle mixed file and CLI arguments correctly")
        fun handleMixedFileAndCliArgumentsCorrectly() {
            val configFile = createConfigFile("--input file-input.txt --verbose")

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf(
                "--count", "25",                    // CLI before file
                "@${configFile.absolutePath}",      // File arguments
                "--output", "cli-output.txt"        // CLI after file
            ))

            // Arguments from different sources should combine
            assertEquals("file-input.txt", args.input)   // From file
            assertEquals("cli-output.txt", args.output)   // From CLI after file
            assertEquals(25, args.count)                 // From CLI before file
            assertTrue(args.verbose)                     // From file
        }

        @Test
        @DisplayName("Should detect duplicate options between file and CLI")
        fun detectDuplicateOptionsBetweenFileAndCli() {
            val configFile = createConfigFile("--input file-input.txt --count 50")

            val args = ArgumentFileTestArgs()

            // This should fail because --count is provided both on CLI and in file
            val exception = assertThrows(ParseError::class.java) {
                args.parseWithException(arrayOf(
                    "--count", "25",
                    "@${configFile.absolutePath}"
                ))
            }

            assertTrue(exception.message?.contains("provided multiple times") == true)
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("Should handle file with Windows line endings")
        fun handleFileWithWindowsLineEndings() {
            // Create file with \r\n line endings
            val content = "--input windows.txt\r\n--verbose\r\n--count 42\r\n"
            val configFile = createConfigFile(content)

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}"))

            assertEquals("windows.txt", args.input)
            assertTrue(args.verbose)
            assertEquals(42, args.count)
        }

        @Test
        @DisplayName("Should handle file paths with spaces")
        fun handleFilePathsWithSpaces(@TempDir tempDir: Path) {
            val dirWithSpaces = tempDir.resolve("dir with spaces").toFile()
            dirWithSpaces.mkdirs()
            val configFile = File(dirWithSpaces, "config file.txt")
            configFile.writeText("--input spaced.txt --verbose")

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}"))

            assertEquals("spaced.txt", args.input)
            assertTrue(args.verbose)
        }

        @Test
        @DisplayName("Should handle very large argument files")
        fun handleVeryLargeArgumentFiles() {
            // Create a file with many tag arguments
            val largeTags = (1..1000).map { "--tag tag$it" }.joinToString("\n")
            val content = "--input large.txt\n$largeTags\n--verbose"
            val configFile = createConfigFile(content)

            val args = ArgumentFileTestArgs()
            args.parseWithException(arrayOf("@${configFile.absolutePath}"))

            assertEquals("large.txt", args.input)
            assertTrue(args.verbose)
            assertEquals(1000, args.tags.size)
            assertEquals("tag1", args.tags.first())
            assertEquals("tag1000", args.tags.last())
        }
    }
}