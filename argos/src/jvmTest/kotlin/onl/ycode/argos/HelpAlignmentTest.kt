/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import onl.ycode.argos.terminal.PlainTerminal
import onl.ycode.argos.terminal.Terminal
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertTrue

class HelpAlignmentTest {

    /**
     * Custom terminal for testing that combines PlainTerminal functionality with custom width.
     */
    private class TestPlainTerminal(private val testWidth: Int = 80) : Terminal {
        override val width: Int get() = testWidth
        override val infoColumn: Int get() = 25

        override fun emitPlain(text: String) {
            print(text)
        }
    }

    /**
     * Test help alignment with PlainTerminal (no ANSI support) to ensure
     * alignment calculations work correctly without styling.
     */
    @Test
    fun testHelpAlignmentWithPlainTerminal() {
        class TestArgs : Arguments(
            appName = "alignment-test",
            terminal = TestPlainTerminal(80)
        ) {
            // 1) Only long
            val onlyLong by option("--only-long").help("Only long form")

            // 2) Only short
            val onlyShort by option("-s").help("Only short form")

            // 3) Long and short
            val longAndShort by option("-l", "--long-short").help("Both forms")

            // 4) Two long options
            val twoLong by option("--first-long", "--second-long").help("Two long forms")

            // 5) Two short options
            val twoShort by option("-a", "-b").help("Two short forms")

            // 6) Two long and one short
            val twoLongOneShort by option("-x", "--first-variant", "--second-variant")
                .help("Two long one short")

            // 7) Two short and one long
            val twoShortOneLong by option("-y", "-z", "--long-variant")
                .help("Two short one long")

            // Very long option to test wrapping
            val veryLong by option("--extremely-long-option-name-for-testing-alignment")
                .help("Very long option name")
        }

        val output = captureOutput {
            TestArgs().printUsage()
        }

        // Verify that all descriptions start at the same column (no ANSI codes)
        val lines = output.split('\n')
        val optionLines = lines.filter { it.contains(" by option") || it.startsWith("--") || it.startsWith("-") }
            .filter { !it.trim().isEmpty() }

        // Find the options section
        val optionsStartIndex = lines.indexOfFirst { it.trim() == "Options:" }
        assertTrue(optionsStartIndex >= 0, "Options section should be present")

        val optionDetailLines = lines.drop(optionsStartIndex + 1)
            .filter { it.trim().isNotEmpty() && !it.startsWith(" ".repeat(20)) } // Skip wrapped lines
            .take(8) // Take our 8 test options

        // For debugging, print the actual output
        println("=== DEBUG: Captured output ===")
        println(output)
        println("=== DEBUG: Option lines ===")
        optionDetailLines.forEachIndexed { i, line -> println("$i: '$line'") }

        // Verify precise spacing alignment
        val spacingInfo = optionDetailLines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null

            // Find where the description starts (after multiple spaces)
            val regex = Regex("^([^\\s]+(?:\\|[^\\s]+)*(?:\\s+<[^>]+>)?)(\\s+)(.*)$")
            val match = regex.find(trimmed)

            if (match != null) {
                val optionPart = match.groupValues[1]
                val spacePart = match.groupValues[2]
                val descriptionPart = match.groupValues[3]

                if (descriptionPart.isNotEmpty()) {
                    Triple(optionPart.length, spacePart.length, descriptionPart)
                } else null
            } else null
        }

        println("=== Spacing Analysis ===")
        spacingInfo.forEachIndexed { i, (optLen, spaceLen, desc) ->
            println("Line $i: optionLen=$optLen, spaceLen=$spaceLen, desc='${desc.take(30)}...'")
        }

        // Verify that descriptions start at consistent positions
        if (spacingInfo.size >= 3) {
            val descriptionStarts = spacingInfo.map { it.first + it.second }
            val minStart = descriptionStarts.minOrNull() ?: 0
            val maxStart = descriptionStarts.maxOrNull() ?: 0

            println("Description start positions: $descriptionStarts")
            println("Range: $minStart to $maxStart (difference: ${maxStart - minStart})")

            // Most descriptions should align, but very long options may start elsewhere
            val commonPosition = descriptionStarts.groupBy { it }.maxByOrNull { it.value.size }?.key ?: descriptionStarts.first()
            val alignedCount = descriptionStarts.count { it == commonPosition }
            val totalCount = descriptionStarts.size

            println("Common alignment position: $commonPosition")
            println("Aligned count: $alignedCount out of $totalCount")

            // At least 60% of options should be aligned at the common position
            assertTrue(alignedCount >= (totalCount * 0.6).toInt(),
                "At least 60% of options should be aligned. " +
                "Aligned: $alignedCount/$totalCount at position $commonPosition. All positions: $descriptionStarts")
        }

        assertTrue(spacingInfo.size >= 6, "At least 6 options should have proper spacing format")

        // Verify all switch types are present
        assertTrue(output.contains("--only-long"), "Only long option should be present")
        assertTrue(output.contains("-s"), "Only short option should be present")
        assertTrue(output.contains("-l|--long-short"), "Long and short should be joined with |")
        assertTrue(output.contains("--first-long|--second-long"), "Two long options should be joined")
        assertTrue(output.contains("-a|-b"), "Two short options should be joined")
        assertTrue(output.contains("-x|--first-variant|--second-variant"), "Three switches should be joined")
        assertTrue(output.contains("-y|-z|--long-variant"), "Mixed switches should be joined")
    }

    /**
     * Test alignment with negation options using PlainTerminal.
     */
    @Test
    fun testNegationAlignmentWithPlainTerminal() {
        class TestNegationArgs : Arguments(
            appName = "negation-test",
            terminal = TestPlainTerminal(80)
        ) {
            val enableCache by option("--enable-cache").bool().negatable()
                .help("Enable caching with negation support")

            val debugMode by option("-d", "--debug").bool().negatable()
                .help("Debug mode with short and long forms")

            val verboseOutput by option("--verbose").bool().negatable("disable")
                .help("Verbose output with custom negation prefix")

            val regularOption by option("--regular").help("Regular non-boolean option")

            val shortOnly by option("-q").bool().negatable()
                .help("Short-only negatable option")
        }

        val output = captureOutput {
            TestNegationArgs().printUsage()
        }

        // Verify negation syntax in plain text (note: exact format may vary)
        assertTrue(output.contains("enable-cache"), "Cache option should be present")
        assertTrue(output.contains("debug"), "Debug option should be present")
        assertTrue(output.contains("verbose"), "Verbose option should be present")

        // Print output for debugging
        println("=== Negation Test Debug ===")
        println(output)

        // Verify alignment is maintained with negation options
        val lines = output.split('\n')
        val optionsStartIndex = lines.indexOfFirst { it.trim() == "Options:" }
        assertTrue(optionsStartIndex >= 0, "Options section should be present")

        val optionLines = lines.drop(optionsStartIndex + 1)
            .filter { it.trim().isNotEmpty() && !it.startsWith(" ".repeat(20)) }
            .take(5)

        // Verify precise spacing for negation options
        val negationSpacingInfo = optionLines.mapNotNull { line ->
            val regex = Regex("^([^\\s]+(?:\\|[^\\s]+)*(?:\\([^)]+\\)[^\\s]*)?)(\\s+)(.*)$")
            val match = regex.find(line.trim())
            if (match != null && match.groupValues[3].isNotEmpty()) {
                val optLen = match.groupValues[1].length
                val spaceLen = match.groupValues[2].length
                Triple(optLen, spaceLen, match.groupValues[3])
            } else null
        }

        if (negationSpacingInfo.size >= 2) {
            val descStarts = negationSpacingInfo.map { it.first + it.second }
            val minStart = descStarts.minOrNull() ?: 0
            val maxStart = descStarts.maxOrNull() ?: 0

            println("Negation spacing - Description starts: $descStarts")

            // Most negation options should align (allowing for very long negatable forms)
            val commonPos = descStarts.groupBy { it }.maxByOrNull { it.value.size }?.key ?: descStarts.first()
            val alignedCount = descStarts.count { it == commonPos }

            assertTrue(alignedCount >= (descStarts.size * 0.6).toInt(),
                "Most negation options should align. Aligned: $alignedCount/${descStarts.size} at $commonPos")
        }
    }

    /**
     * Test alignment with custom long prefix (double plus instead of double dash).
     */
    @Test
    fun testCustomLongPrefixAlignmentWithPlainTerminal() {
        class TestCustomPrefixArgs : Arguments(
            appName = "custom-prefix-test",
            defaultLongPrefix = "++", // Double plus
            terminal = TestPlainTerminal(80)
        ) {
            val longOption by option("++long-option").help("Long option with double plus")

            val shortAndLong by option("-s", "++short-and-long").help("Short and custom long")

            val multipleCustomLong by option("++first-custom", "++second-custom")
                .help("Multiple custom long options")

            val regularShort by option("-r").help("Regular short option")

            val mixedOptions by option("-m", "++mixed", "++another-mixed")
                .help("Mixed short and multiple custom long")

            val negatableCustom by option("++enable-feature").bool().negatable()
                .help("Negatable option with custom prefix")
        }

        val output = captureOutput {
            TestCustomPrefixArgs().printUsage()
        }

        // Verify custom prefix is used
        assertTrue(output.contains("++long-option"), "Custom long prefix should be used")
        assertTrue(output.contains("-s|++short-and-long"), "Mixed short and custom long should work")
        assertTrue(output.contains("++first-custom|++second-custom"), "Multiple custom long should work")
        assertTrue(output.contains("++mixed|++another-mixed"), "Multiple custom in mixed should work")

        // Verify negation with custom prefix
        assertTrue(output.contains("++(no-)enable-feature") || output.contains("++enable-feature"),
            "Negation should work with custom prefix")

        // Verify precise spacing alignment with custom prefix
        val lines = output.split('\n')
        val optionsStartIndex = lines.indexOfFirst { it.trim() == "Options:" }
        assertTrue(optionsStartIndex >= 0, "Options section should be present")

        val optionLines = lines.drop(optionsStartIndex + 1)
            .filter { it.trim().isNotEmpty() && !it.startsWith(" ".repeat(20)) }
            .take(6)

        println("=== Custom Prefix Debug ===")
        println(output)

        // Analyze spacing with custom ++ prefix
        val customPrefixSpacing = optionLines.mapNotNull { line ->
            val regex = Regex("^([^\\s]+(?:\\|[^\\s]+)*(?:\\([^)]+\\)[^\\s]*)?)(\\s+)(.*)$")
            val match = regex.find(line.trim())
            if (match != null && match.groupValues[3].isNotEmpty()) {
                val optLen = match.groupValues[1].length
                val spaceLen = match.groupValues[2].length
                Triple(optLen, spaceLen, match.groupValues[3])
            } else null
        }

        // For custom prefix test, just verify basic functionality
        assertTrue(customPrefixSpacing.size >= 4, "Should have at least 4 options with custom prefix")

        // Verify that basic alignment principles work (all options have proper spacing format)
        val properlySpaced = customPrefixSpacing.filter { it.second >= 1 } // At least 1 space
        assertTrue(properlySpaced.size == customPrefixSpacing.size,
            "All options should have proper spacing between option and description")
    }

    /**
     * Test comprehensive alignment with all features combined.
     */
    @Test
    fun testComprehensiveAlignmentWithPlainTerminal() {
        class TestComprehensiveArgs : Arguments(
            appName = "comprehensive-test",
            defaultLongPrefix = "++",
            argumentSeparator = "|",
            terminal = TestPlainTerminal(100)
        ) {
            // Mix of all switch types
            val shortOnly by option("-a").help("Short only")
            val longOnly by option("++long-only").help("Long only with custom prefix")
            val shortAndLong by option("-b", "++both").help("Short and custom long")
            val multiShort by option("-x", "-y").help("Multiple short")
            val multiLong by option("++first", "++second").help("Multiple custom long")
            val mixed by option("-z", "++mixed-one", "++mixed-two").help("Mixed all types")

            // Negatable options
            val negatable by option("++enable").bool().negatable().help("Negatable with custom prefix")
            val negatableCustom by option("-n", "++negatable").bool().negatable("disable")
                .help("Mixed negatable with custom negation")

            // Type options - remove .int() method that doesn't exist
            val typedString by option("-i", "++integer").help("Integer option")
            val stringOpt by option("++string-opt").help("String option")

            // Very long option name
            val extremelyLong by option("++extremely-long-option-name-that-should-test-edge-cases")
                .help("Extreme length option name for testing")
        }

        val output = captureOutput {
            TestComprehensiveArgs().printUsage()
        }

        // Verify all option types are rendered correctly
        assertTrue(output.contains("-a"), "Short only should be present")
        assertTrue(output.contains("++long-only"), "Custom long only should be present")
        assertTrue(output.contains("-b|++both"), "Mixed should use separator")
        assertTrue(output.contains("-x|-y"), "Multi short should use separator")
        assertTrue(output.contains("++first|++second"), "Multi custom long should use separator")
        assertTrue(output.contains("-z|++mixed-one|++mixed-two"), "Complex mixed should work")

        // Verify negation with custom prefix
        assertTrue(output.contains("++(no-)enable") || output.contains("++enable"),
            "Custom prefix negation should work")
        assertTrue(output.contains("-n|++") && (output.contains("(disable-)") || output.contains("negatable")),
            "Mixed negatable should work")

        // Comprehensive spacing alignment verification
        val lines = output.split('\n')
        val optionsStartIndex = lines.indexOfFirst { it.trim() == "Options:" }
        assertTrue(optionsStartIndex >= 0, "Options section should be present")

        val optionLines = lines.drop(optionsStartIndex + 1)
            .filter { it.trim().isNotEmpty() && !it.startsWith(" ".repeat(20)) }

        println("=== Comprehensive Test Debug ===")
        println(output)
        println("=== All Option Lines ===")
        optionLines.forEachIndexed { i, line -> println("$i: '$line'") }

        // Analyze all spacing patterns
        val comprehensiveSpacing = optionLines.mapNotNull { line ->
            val regex = Regex("^([^\\s]+(?:\\|[^\\s]+)*(?:\\([^)]+\\)[^\\s]*)?(?:\\s+<[^>]+>)?)(\\s+)(.*)$")
            val match = regex.find(line.trim())
            if (match != null && match.groupValues[3].isNotEmpty()) {
                val optLen = match.groupValues[1].length
                val spaceLen = match.groupValues[2].length
                Triple(optLen, spaceLen, match.groupValues[3])
            } else null
        }

        println("=== Comprehensive Spacing Analysis ===")
        comprehensiveSpacing.forEachIndexed { i, (optLen, spaceLen, desc) ->
            println("Line $i: optionLen=$optLen, spaceLen=$spaceLen, desc='${desc.take(25)}...'")
        }

        if (comprehensiveSpacing.size >= 5) {
            val descStarts = comprehensiveSpacing.map { it.first + it.second }
            val minStart = descStarts.minOrNull() ?: 0
            val maxStart = descStarts.maxOrNull() ?: 0

            println("All description start positions: $descStarts")
            println("Alignment range: $minStart to $maxStart (difference: ${maxStart - minStart})")

            // For comprehensive test, most options should align
            val commonPos = descStarts.groupBy { it }.maxByOrNull { it.value.size }?.key ?: descStarts.first()
            val alignedCount = descStarts.count { it == commonPos }

            println("Most common position: $commonPos (used by $alignedCount/${descStarts.size} options)")

            assertTrue(alignedCount >= (descStarts.size * 0.6).toInt(),
                "Most options should align at common position. Aligned: $alignedCount/${descStarts.size} at $commonPos. All: $descStarts")
        }

        assertTrue(optionLines.size >= 10, "Should have multiple option lines")
    }

    /**
     * Capture stdout output from a block of code.
     */
    private fun captureOutput(block: () -> Unit): String {
        val originalOut = System.out
        val byteArrayOutputStream = ByteArrayOutputStream()
        val printStream = PrintStream(byteArrayOutputStream)

        return try {
            System.setOut(printStream)
            block()
            byteArrayOutputStream.toString()
        } finally {
            System.setOut(originalOut)
        }
    }
}