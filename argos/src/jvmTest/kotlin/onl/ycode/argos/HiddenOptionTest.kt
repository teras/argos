/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenOptionTest {

    class TestHiddenArgs : Arguments() {
        val publicOption by option("--public").help("This is a public option")
        val hiddenOption by option("--hidden-opt").hidden().help("This is a hidden option")
        val anotherPublic by option("--another").help("Another public option")
    }

    @Test
    fun hiddenOptionIsNotShownInHelp() {
        val args = TestHiddenArgs()
        val snapshot = args.snapshot()

        // Verify the hidden option exists in the specs
        assertTrue(snapshot.options.any { it.ownerName == "hiddenOption" && it.hidden })

        // Verify the public options exist and are not hidden
        assertTrue(snapshot.options.any { it.ownerName == "publicOption" && !it.hidden })
        assertTrue(snapshot.options.any { it.ownerName == "anotherPublic" && !it.hidden })
    }

    @Test
    fun hiddenOptionStillParsesCorrectly() {
        val args = TestHiddenArgs()
        args.parseWithException(arrayOf("--public", "value1", "--hidden-opt", "secret", "--another", "value2"))

        assertEquals("value1", args.publicOption)
        assertEquals("secret", args.hiddenOption)
        assertEquals("value2", args.anotherPublic)
    }

    @Test
    fun hiddenOptionWorksWithConstraints() {
        class ConstrainedHiddenArgs : Arguments() {
            val publicOpt by option("--public").help("Public option")
            val hiddenOpt by option("--hidden").hidden().required().help("Hidden but required")
        }

        val args = ConstrainedHiddenArgs()
        args.parseWithException(arrayOf("--public", "value", "--hidden", "secret"))

        assertEquals("value", args.publicOpt)
        assertEquals("secret", args.hiddenOpt)
    }

    @Test
    fun hiddenOptionDoesNotAppearInHelpOutput() {
        class TestHelpArgs : Arguments() {
            val publicOpt by option("--public").help("This should appear in help")
            val hiddenOpt by option("--hidden").hidden().help("This should NOT appear in help")
        }

        val args = TestHelpArgs()

        // Capture help output by using a custom terminal
        val helpLines = mutableListOf<String>()
        val customTerminal = object : onl.ycode.argos.terminal.Terminal {
            override val width: Int = 80
            override val infoColumn: Int = 24

            override fun startEmit() {}
            override fun endEmit() {}
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

        // Verify public option appears in help
        assertTrue(helpText.contains("--public"), "Public option should appear in help: $helpText")

        // Verify hidden option does NOT appear in help
        assertFalse(helpText.contains("--hidden"), "Hidden option should NOT appear in help: $helpText")
    }

    @Test
    fun hiddenOptionWorksWithDifferentTypes() {
        class TypedHiddenArgs : Arguments() {
            val hiddenInt by option("--hidden-int").int().hidden().help("Hidden integer")
            val hiddenList by option("--hidden-list").list().hidden().help("Hidden list")
            val hiddenCount by option("-h", "--hidden-count").bool().list().hidden().help("Hidden count")
        }

        val args = TypedHiddenArgs()
        args.parseWithException(arrayOf("--hidden-int", "42", "--hidden-list", "a", "--hidden-list", "b", "-hhh"))

        assertEquals(42, args.hiddenInt)
        assertEquals(listOf("a", "b"), args.hiddenList)
        assertEquals(3, args.hiddenCount.size)
    }
}