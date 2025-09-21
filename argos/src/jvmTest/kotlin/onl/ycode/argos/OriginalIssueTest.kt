/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OriginalIssueTest {

    class OriginalIssueArgs : Arguments() {
        val help by option("--help").bool().eager().help("Show help")
        val password1 by option().input(prompt = "Password 1", maxRetries = 1).required()
        val password2 by option().input(prompt = "Password 2", maxRetries = 1).required()
        val secretKey by option("--secret-key").hidden().help("Secret API key (hidden from help)")

        // Test various types that could cause NPE
        val requiredString by option("--required").required()
        val requiredInt by option("--int").int().required()
        val requiredLong by option("--long").long().required()
        val requiredFloat by option("--float").float().required()
        val requiredDouble by option("--double").double().required()
        val requiredList by option("--list").list().required()
        val requiredSet by option("--set").set().required()
        val requiredCount by option("-v", "--verbose").bool().list().required()
    }

    @Test
    fun originalNPEIssue_nowFixedForAllTypes() {
        val args = OriginalIssueArgs()
        args.parseWithException(arrayOf("--help"))

        // Help should be accessible
        assertEquals(true, args.help)

        // Test all the types that previously caused NPE
        val properties = listOf(
            "requiredString" to { args.requiredString },
            "requiredInt" to { args.requiredInt },
            "requiredLong" to { args.requiredLong },
            "requiredFloat" to { args.requiredFloat },
            "requiredDouble" to { args.requiredDouble },
            "requiredList" to { args.requiredList },
            "requiredSet" to { args.requiredSet },
            "requiredCount" to { args.requiredCount }
        )

        properties.forEach { (name, accessor) ->
            try {
                val value = accessor()
                // If it returns a value, that's acceptable
                println("$name returned: $value")
            } catch (e: UninitializedPropertyException) {
                // Our custom exception is preferred - verify it has good message
                assertTrue(e.message!!.contains("not initialized"))
                println("$name threw descriptive exception (good!)")
            } catch (e: Exception) {
                // Any other exception is also unexpected
                throw AssertionError("$name threw unexpected exception: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    @Test
    fun hiddenOptionsWorkCorrectly() {
        val args = OriginalIssueArgs()

        // Test that hidden option works but doesn't appear in help
        val snapshot = args.snapshot()

        // Verify secretKey is hidden
        val secretKeyOption = snapshot.options.find { it.ownerName == "secretKey" }
        assertTrue(secretKeyOption != null && secretKeyOption.hidden)

        // Verify other options are not hidden
        val helpOption = snapshot.options.find { it.ownerName == "help" }
        assertTrue(helpOption != null && !helpOption.hidden)
    }
}