/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CountBuilderTest {

    @Test
    fun countBuilder_basicFunctionality() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count()
        }

        val args = TestArgs()

        // Test zero occurrences
        args.parseWithException(arrayOf())
        assertEquals(0, args.verbose)

        // Test single occurrence
        args.parseWithException(arrayOf("-v"))
        assertEquals(1, args.verbose)

        // Test multiple occurrences
        args.parseWithException(arrayOf("-v", "-v", "--verbose"))
        assertEquals(3, args.verbose)
    }

    @Test
    fun countBuilder_withConstraints() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().atLeast(2)
            val debug by option("-d", "--debug").bool().count()
        }

        val args = TestArgs()

        // Should fail when count is less than required
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-v"))
        }

        // Should succeed when count meets requirement
        args.parseWithException(arrayOf("-v", "-v"))
        assertEquals(2, args.verbose)
    }

    @Test
    fun countBuilder_conflictsWith() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().conflictsWith(::quiet)
            val quiet by option("-q", "--quiet").bool().count()
        }

        val args = TestArgs()

        // Should succeed with only one
        args.parseWithException(arrayOf("-v", "-v"))
        assertEquals(2, args.verbose)
        assertEquals(0, args.quiet)

        // Should fail when both are present
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-v", "-q"))
        }
    }

    @Test
    fun countBuilder_exactlyOneWith() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().exactlyOneWith(::debug)
            val debug by option("-d", "--debug").bool()
        }

        val args = TestArgs()

        // Should succeed with exactly one
        args.parseWithException(arrayOf("-v"))
        assertEquals(1, args.verbose)
        assertEquals(null, args.debug)

        // Should fail when neither is present
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())
        }

        // Should fail when both are present
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-v", "-d"))
        }
    }

    @Test
    fun countBuilder_atMostOneWith() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().atMostOneWith(::debug)
            val debug by option("-d", "--debug").bool()
        }

        val args = TestArgs()

        // Should succeed with none
        args.parseWithException(arrayOf())
        assertEquals(0, args.verbose)
        assertEquals(null, args.debug)

        // Should succeed with one
        args.parseWithException(arrayOf("-v", "-v"))
        assertEquals(2, args.verbose)
        assertEquals(null, args.debug)

        // Should fail when both are present
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-v", "-d"))
        }
    }

    @Test
    fun countBuilder_atLeastOneWith() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().atLeastOneWith(::debug)
            val debug by option("-d", "--debug").bool()
        }

        val args = TestArgs()

        // Should fail when neither is present
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())
        }

        // Should succeed when one is present
        args.parseWithException(arrayOf("-v", "-v"))
        assertEquals(2, args.verbose)
        assertEquals(null, args.debug)

        // Should succeed when both are present
        args.parseWithException(arrayOf("-v", "-d"))
        assertEquals(1, args.verbose)
        assertEquals(true, args.debug)
    }

    @Test
    fun countBuilder_requireIfAnyPresent() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().requireIfAnyPresent(::file, ::output)
            val file by option("-f", "--file")
            val output by option("-o", "--output")
        }

        val args = TestArgs()

        // Should succeed when none are present
        args.parseWithException(arrayOf())
        assertEquals(0, args.verbose)

        // Should fail when trigger is present but target is not
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-f", "test.txt"))
        }

        // Should succeed when both are present
        args.parseWithException(arrayOf("-f", "test.txt", "-v"))
        assertEquals(1, args.verbose)
        assertEquals("test.txt", args.file)
    }

    @Test
    fun countBuilder_requireIfAllPresent() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().requireIfAllPresent(::file, ::output)
            val file by option("-f", "--file")
            val output by option("-o", "--output")
        }

        val args = TestArgs()

        // Should succeed when only one trigger is present
        args.parseWithException(arrayOf("-f", "test.txt"))
        assertEquals(0, args.verbose)
        assertEquals("test.txt", args.file)

        // Should fail when all triggers are present but target is not
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-f", "test.txt", "-o", "out.txt"))
        }

        // Should succeed when all triggers and target are present
        args.parseWithException(arrayOf("-f", "test.txt", "-o", "out.txt", "-v"))
        assertEquals(1, args.verbose)
    }

    @Test
    fun countBuilder_requireIfValue() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().requireIfValue(::mode) { it == "debug" }
            val mode by option("-m", "--mode")
        }

        val args = TestArgs()

        // Should succeed when mode is not "debug"
        args.parseWithException(arrayOf("-m", "release"))
        assertEquals(0, args.verbose)
        assertEquals("release", args.mode)

        // Should fail when mode is "debug" but verbose is not present
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-m", "debug"))
        }

        // Should succeed when mode is "debug" and verbose is present
        args.parseWithException(arrayOf("-m", "debug", "-v"))
        assertEquals(1, args.verbose)
        assertEquals("debug", args.mode)
    }

    @Test
    fun countBuilder_validation() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count()
                .validate("Verbosity too high: @value (max 3)") { it <= 3 }
                .validate("Verbosity must be positive: @value") { it >= 0 }
        }

        val args = TestArgs()

        // Should succeed with valid value
        args.parseWithException(arrayOf("-v", "-v"))
        assertEquals(2, args.verbose)

        // Should fail when validation fails
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-v", "-v", "-v", "-v"))
        }
    }

    @Test
    fun countBuilder_helpText() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().help("Increase verbosity")
        }

        val args = TestArgs()
        val helpLines = mutableListOf<String>()
        val customTerminal = object : onl.ycode.argos.terminal.Terminal {
            override fun startEmit() { }
            override fun endEmit() { }
            override fun emitNewLine() { helpLines.add("") }
            override fun emitPlain(text: String) {
                if (helpLines.isEmpty()) helpLines.add("")
                helpLines[helpLines.lastIndex] += text
            }
            override fun emitStrong(text: String) {
                if (helpLines.isEmpty()) helpLines.add("")
                helpLines[helpLines.lastIndex] += text
            }
            override fun emitParam(text: String) {
                if (helpLines.isEmpty()) helpLines.add("")
                helpLines[helpLines.lastIndex] += text
            }
            override fun emitError(text: String) {
                if (helpLines.isEmpty()) helpLines.add("")
                helpLines[helpLines.lastIndex] += text
            }
        }

        Help.printUsage(args, customTerminal)
        val helpText = helpLines.joinToString("\n")

        assertTrue(helpText.contains("Increase verbosity"), "Help text should contain the custom help message")
    }

    @Test
    fun countBuilder_hidden() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count().help("Visible option")
            val debug by option("-d", "--debug").bool().count().hidden().help("Hidden option")
        }

        val args = TestArgs()
        val helpLines = mutableListOf<String>()
        val customTerminal = object : onl.ycode.argos.terminal.Terminal {
            override fun startEmit() { }
            override fun endEmit() { }
            override fun emitNewLine() { helpLines.add("") }
            override fun emitPlain(text: String) {
                if (helpLines.isEmpty()) helpLines.add("")
                helpLines[helpLines.lastIndex] += text
            }
            override fun emitStrong(text: String) {
                if (helpLines.isEmpty()) helpLines.add("")
                helpLines[helpLines.lastIndex] += text
            }
            override fun emitParam(text: String) {
                if (helpLines.isEmpty()) helpLines.add("")
                helpLines[helpLines.lastIndex] += text
            }
            override fun emitError(text: String) {
                if (helpLines.isEmpty()) helpLines.add("")
                helpLines[helpLines.lastIndex] += text
            }
        }

        Help.printUsage(args, customTerminal)
        val helpText = helpLines.joinToString("\n")

        assertTrue(helpText.contains("--verbose"), "Help text should contain visible option")
        assertFalse(helpText.contains("--debug"), "Help text should NOT contain hidden option")
    }

    @Test
    fun countBuilder_onlyInDomains() {
        class TestArgs : Arguments() {
            val buildDomain by domain("build")
            val testDomain by domain("test")

            val verbose by option("-v", "--verbose").bool().count().onlyInDomains(::buildDomain)
            val debug by option("-d", "--debug").bool().count().onlyInDomains(::testDomain)
            val global by option("-g", "--global").bool().count()
        }

        val args = TestArgs()

        // Should succeed when domain-restricted option is used in correct domain
        args.parseWithException(arrayOf("build", "-v", "-v"))
        assertEquals("build", args.selectedDomain())
        assertEquals(2, args.verbose)

        // Should fail when domain-restricted option is used in wrong domain
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("test", "-v"))
        }

        // Should succeed with global option in any domain
        args.parseWithException(arrayOf("build", "-g"))
        assertEquals(1, args.global)

        args.parseWithException(arrayOf("test", "-g", "-d"))
        assertEquals(1, args.global)
        assertEquals(1, args.debug)
    }


    @Test
    fun countBuilder_multipleValidators() {
        class TestArgs : Arguments() {
            val verbose by option("-v", "--verbose").bool().count()
                .validate(
                    "Must be even: @value" to { it % 2 == 0 },
                    "Must be at least 2: @value" to { it >= 2 }
                )
        }

        val args = TestArgs()

        // Should fail for odd numbers
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("-v"))
        }

        // Should fail for numbers less than 2
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())
        }

        // Should succeed for even numbers >= 2
        args.parseWithException(arrayOf("-v", "-v"))
        assertEquals(2, args.verbose)
    }
}