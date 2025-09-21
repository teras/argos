/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class DelegatePropertiesTest {

    class TestArgs : Arguments() {
        val help by help(exitOnTrigger = false)
        val version by version("1.0.0", exitOnTrigger = false)
        val file by option("--file")
    }

    class TestCustomArgs : Arguments() {
        val customHelp by help("--help", "--usage", exitOnTrigger = false)
        val customVersion by version("2.0.0", "--ver", "--version", exitOnTrigger = false)
        val file by option("--file")
    }

    @Test
    fun help_defaultSwitches() {
        val args = TestArgs()
        args.parseWithException(arrayOf("--help"))

        assertTrue(args.help)
        assertFalse(args.version)
    }

    @Test
    fun help_shortForm() {
        val args = TestArgs()
        args.parseWithException(arrayOf("-h"))

        assertTrue(args.help)
        assertFalse(args.version)
    }

    @Test
    fun version_defaultSwitch() {
        val args = TestArgs()
        args.parseWithException(arrayOf("--version"))

        assertFalse(args.help)
        assertTrue(args.version)
    }

    @Test
    fun help_customSwitches() {
        val args = TestCustomArgs()
        args.parseWithException(arrayOf("--usage"))

        assertTrue(args.customHelp)
    }

    @Test
    fun version_customSwitches() {
        val args = TestCustomArgs()
        args.parseWithException(arrayOf("--ver"))

        assertTrue(args.customVersion)
    }

    @Test
    fun delegateProperties_defaultValues() {
        val args = TestArgs()
        args.parseWithException(arrayOf("--file", "test.txt"))

        assertFalse(args.help)
        assertFalse(args.version)
        assertEquals("test.txt", args.file)
    }

    @Test
    fun delegateProperties_eagerExit() {
        val args = TestArgs()
        // Since both help and version use .eager(), they should cause early exit
        args.parseWithException(arrayOf("--help", "--file", "test.txt"))

        assertTrue(args.help)
        // Due to eager exit, file parsing should not have occurred
        // But the property should still be accessible (though likely null)
    }

    // ======== Comprehensive Tests for help() and version() ========

    @Test
    fun help_customPrefix_usesConfiguredPrefix() {
        class TestArgs : Arguments(defaultLongPrefix = "/") {
            val help by help(exitOnTrigger = false)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("/help"))
        assertTrue(args.help)
    }

    @Test
    fun version_defaultPrefix_usesConfiguredPrefix() {
        class TestArgs : Arguments(defaultLongPrefix = "--") {
            val version by version("1.0.0", exitOnTrigger = false)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--version"))
        assertTrue(args.version)
    }

    @Test
    fun version_customPrefix_usesConfiguredPrefix() {
        class TestArgs : Arguments(defaultLongPrefix = "/") {
            val version by version("1.0.0", exitOnTrigger = false)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("/version"))
        assertTrue(args.version)
    }

    @Test
    fun nameClashing_helpWithExistingOption_throwsError() {
        // Argos properly detects and prevents duplicate switch registration
        assertFailsWith<ConfigException> {
            class TestArgs : Arguments() {
                val help by help(exitOnTrigger = false)
                val manualHelp by option("--help") // Should conflict during property delegation
            }
            TestArgs()
        }
    }

    @Test
    fun nameClashing_versionWithExistingOption_throwsError() {
        assertFailsWith<ConfigException> {
            class TestArgs : Arguments() {
                val version by version("1.0.0", exitOnTrigger = false)
                val manualVersion by option("--version") // Should conflict during property delegation
            }
            TestArgs()
        }
    }

    @Test
    fun nameClashing_customSwitchConflict_throwsError() {
        assertFailsWith<ConfigException> {
            class TestArgs : Arguments() {
                val help by help("--usage", exitOnTrigger = false)
                val manual by option("--usage") // Should conflict during property delegation
            }
            TestArgs()
        }
    }

    @Test
    fun nameClashing_shortFormConflict_throwsError() {
        // Short form conflicts are now also detected as ConfigErrors
        assertFailsWith<ConfigException> {
            class TestArgs : Arguments() {
                val help by help(exitOnTrigger = false) // Uses -h
                val manual by option("-h") // Should conflict during property delegation
            }
            TestArgs()
        }
    }

    @Test
    fun noNameClashing_differentSwitches_works() {
        class TestArgs : Arguments() {
            val help by help("--usage", "--man", exitOnTrigger = false)
            val version by version("1.0.0", "--ver", "--v", exitOnTrigger = false)
            val help2 by option("--help") // Different from --usage, --man
            val version2 by option("--version") // Different from --ver, --v
        }

        val args = TestArgs()
        // Due to eager exit, only the first option will be processed
        args.parseWithException(arrayOf("--usage"))

        assertTrue(args.help)
        // Other options won't be processed due to eager exit
        assertFalse(args.version)
    }

    @Test
    fun eagerExit_helpStopsProcessing() {
        class TestArgs : Arguments() {
            val help by help(exitOnTrigger = false)
            val required by option("--required").required()
        }

        val args = TestArgs()
        // Should not fail even though --required is missing, due to eager exit
        args.parseWithException(arrayOf("--help"))

        assertTrue(args.help)
    }

    @Test
    fun eagerExit_versionStopsProcessing() {
        class TestArgs : Arguments() {
            val version by version("1.0.0", exitOnTrigger = false)
            val required by option("--required").required()
        }

        val args = TestArgs()
        // Should not fail even though --required is missing, due to eager exit
        args.parseWithException(arrayOf("--version"))

        assertTrue(args.version)
    }

    @Test
    fun multipleHelpOptions_independent() {
        class TestArgs : Arguments() {
            val help1 by help("--help1", exitOnTrigger = false)
            val help2 by help("--help2", exitOnTrigger = false)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--help1"))

        assertTrue(args.help1)
        assertFalse(args.help2)
    }

    @Test
    fun multipleVersionOptions_independent() {
        class TestArgs : Arguments() {
            val version1 by version("1.0.0", "--version1", exitOnTrigger = false)
            val version2 by version("2.0.0", "--version2", exitOnTrigger = false)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--version2"))

        assertFalse(args.version1)
        assertTrue(args.version2)
    }

    @Test
    fun helpAndVersion_together() {
        class TestArgs : Arguments() {
            val help by help(exitOnTrigger = false)
            val version by version("1.0.0", exitOnTrigger = false)
        }

        val args = TestArgs()
        // First option wins due to eager exit
        args.parseWithException(arrayOf("--help", "--version"))

        assertTrue(args.help)
        assertFalse(args.version) // Not processed due to eager exit
    }

    @Test
    fun customSwitches_allWork() {
        class TestArgs : Arguments() {
            val help by help("--usage", "--man", "--help-me", exitOnTrigger = false)
            val version by version("1.0.0", "--ver", "--version-info", "-V", exitOnTrigger = false)
        }

        // Test all help switches
        listOf("--usage", "--man", "--help-me").forEach { switch ->
            val args = TestArgs()
            args.parseWithException(arrayOf(switch))
            assertTrue(args.help, "Help should work with switch: $switch")
        }

        // Test all version switches
        listOf("--ver", "--version-info", "-V").forEach { switch ->
            val args = TestArgs()
            args.parseWithException(arrayOf(switch))
            assertTrue(args.version, "Version should work with switch: $switch")
        }
    }

    @Test
    fun help_displaysUsage() {
        class TestArgs : Arguments(appName = "myapp", appDescription = "Test application") {
            val help by help(exitOnTrigger = false)
            val file by option("--file").help("Input file")
        }

        // Capture stdout
        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            val args = TestArgs()
            args.parseWithException(arrayOf("--help"))
            assertTrue(args.help)

            val output = capturedOut.toString()
            assertTrue(output.contains("myapp"), "Should contain app name")
            assertTrue(output.contains("Test application"), "Should contain app description")
            assertTrue(output.contains("--file"), "Should contain option help")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun version_displaysVersionString() {
        class TestArgs : Arguments() {
            val version by version("1.2.3-beta", exitOnTrigger = false)
        }

        // Capture stdout
        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            val args = TestArgs()
            args.parseWithException(arrayOf("--version"))
            assertTrue(args.version)

            val output = capturedOut.toString().trim()
            assertEquals("1.2.3-beta", output, "Should display exact version string")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun exitOnTrigger_false_doesNotExit() {
        class TestArgs : Arguments() {
            val help by help(exitOnTrigger = false)
            val version by version("1.0.0", exitOnTrigger = false)
            val file by option("--file")
        }

        val args = TestArgs()

        // These should not exit the JVM process
        args.parseWithException(arrayOf("--help"))
        assertTrue(args.help)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--version"))
        assertTrue(args2.version)

        // Process should continue normally
        val args3 = TestArgs()
        args3.parseWithException(arrayOf("--file", "test.txt"))
        assertEquals("test.txt", args3.file)
    }

    @Test
    fun customVersion_differentVersionStrings() {
        val versions = listOf("1.0.0", "2.5.3-alpha", "v3.0.0-RC1", "0.1.0-SNAPSHOT")

        versions.forEach { versionString ->
            class TestArgs : Arguments() {
                val version by version(versionString, exitOnTrigger = false)
            }

            // Capture stdout
            val originalOut = System.out
            val capturedOut = ByteArrayOutputStream()
            System.setOut(PrintStream(capturedOut))

            try {
                val args = TestArgs()
                args.parseWithException(arrayOf("--version"))
                assertTrue(args.version)

                val output = capturedOut.toString().trim()
                assertEquals(versionString, output, "Should display version: $versionString")
            } finally {
                System.setOut(originalOut)
            }
        }
    }
}