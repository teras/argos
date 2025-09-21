/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigurationValidationTest {

    @Test
    fun atLeast_throwsConfigError_whenValueIsZero() {
        val exception = assertFailsWith<IllegalStateException> {
            // The validation happens when atLeast is called, not during class construction
            // Create a dummy Arguments context to call option()
            object : Arguments() {
                init {
                    option("--items").list().atLeast(0)
                }
            }
        }
        assertTrue(exception.message!!.contains("atLeast requires n >= 1 (got 0)"))
    }

    @Test
    fun atLeast_throwsConfigError_whenValueIsNegative() {
        val exception = assertFailsWith<IllegalStateException> {
            object : Arguments() {
                init {
                    option("--items").list().atLeast(-1)
                }
            }
        }
        assertTrue(exception.message!!.contains("atLeast requires n >= 1 (got -1)"))
    }


    @Test
    fun atLeast_throwsConfigError_whenValueIsZeroForSetOption() {
        val exception = assertFailsWith<IllegalStateException> {
            object : Arguments() {
                init {
                    option("--items").set().atLeast(0)
                }
            }
        }
        assertTrue(exception.message!!.contains("atLeast requires n >= 1 (got 0)"))
    }

    @Test
    fun atLeast_throwsConfigError_whenValueIsNegativeForCountOption() {
        val exception = assertFailsWith<IllegalStateException> {
            object : Arguments() {
                init {
                    option("-v", "--verbose").bool().list().atLeast(-2)
                }
            }
        }
        assertTrue(exception.message!!.contains("atLeast requires n >= 1 (got -2)"))
    }

    @Test
    fun atLeast_worksCorrectly_whenValueIsPositive() {
        // This should not throw an exception
        class ValidAtLeastArgs : Arguments() {
            val items by option("--items").list().atLeast(2)
            val value by option("--value").required()
            val tags by option("--tags").set().atLeast(3)
            val verbose by option("-v", "--verbose").bool().list().atLeast(1)
        }

        val args = ValidAtLeastArgs()
        // If we get here without exception, the configuration is valid
        assertTrue(true, "Configuration should be valid for positive atLeast values")
    }

    @Test
    fun argumentSeparator_worksCorrectly_withDefaultValue() {
        // Default separator should be "|"
        class DefaultSeparatorArgs : Arguments() {
            val port by option("-p", "--port").int()
            val config by option("-c", "--config")
        }

        val args = DefaultSeparatorArgs()
        val helpLines = mutableListOf<String>()
        val customTerminal = object : onl.ycode.argos.terminal.Terminal {
            override fun startEmit() { }
            override fun endEmit() { }
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

        // Should contain the default separator "|"
        assertTrue(helpText.contains("-p|--port"), "Help output should contain default separator '|'")
        assertTrue(helpText.contains("-c|--config"), "Help output should contain default separator '|'")
    }

    @Test
    fun argumentSeparator_throwsConfigError_whenEmpty() {
        val exception = assertFailsWith<ConfigException> {
            object : Arguments(argumentSeparator = "") {
                val port by option("-p", "--port").int()
            }
        }
        assertTrue(exception.message!!.contains("Invalid argumentSeparator: cannot be empty"))
    }

    @Test
    fun argumentSeparator_worksCorrectly_withCustomValue() {
        // Custom separator should be used
        class CustomSeparatorArgs : Arguments(argumentSeparator = ", ") {
            val port by option("-p", "--port").int()
            val config by option("-c", "--config")
        }

        val args = CustomSeparatorArgs()
        val helpLines = mutableListOf<String>()
        val customTerminal = object : onl.ycode.argos.terminal.Terminal {
            override fun startEmit() { }
            override fun endEmit() { }
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

        // Should contain the custom separator ", "
        assertTrue(helpText.contains("-p, --port"), "Help output should contain custom separator ', '")
        assertTrue(helpText.contains("-c, --config"), "Help output should contain custom separator ', '")
        // Should NOT contain the default separator "|"
        assertTrue(!helpText.contains("-p|--port"), "Help output should not contain default separator '|'")
        assertTrue(!helpText.contains("-c|--config"), "Help output should not contain default separator '|'")
    }
}