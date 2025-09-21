/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import onl.ycode.argos.terminal.StringTerminal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpChoicesTest {

    private enum class Flavor { SWEET, SOUR }

    @Test
    fun `help lists available choices for enumerated options`() {
        val terminal = StringTerminal(infoColumn = null)

        class ChoiceArgs(term: StringTerminal) : Arguments(appName = "choices-app", terminal = term) {
            val color by option("--color").oneOf("red", "green", "blue").help("Color selection")
            val flavor by option("--flavor").enum<Flavor>().help("Flavor selection")
        }

        ChoiceArgs(terminal).printUsage()

        val output = terminal.toString()

        assertFalse(
            output.contains("one of:"),
            "Help output should no longer include literal 'one of:' markers: $output"
        )
        assertTrue(
            output.contains("choices: red, green, blue"),
            "oneOf choices should appear in help output: $output"
        )
        assertTrue(
            output.contains("choices: SWEET, SOUR"),
            "Enum choices should appear in help output: $output"
        )
    }
}
