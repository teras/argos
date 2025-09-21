/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class UnexpectedPositionalArgumentsTest {

    @Test
    fun `single unexpected positional argument is reported`() {
        class Args : Arguments() {
            val help by help()
        }

        val args = Args()
        
        // Capture the error by using onError callback
        var errorMessage: String? = null
        args.parse(arrayOf("unexpected1"),
            onError = { error, _ -> errorMessage = error.message }
        )
        
        assertTrue(errorMessage?.contains("Unexpected positional argument 'unexpected1'") == true,
            "Expected single unexpected argument message, got: $errorMessage")
    }

    @Test
    fun `multiple unexpected positional arguments are all reported`() {
        class Args : Arguments() {
            val help by help()
        }

        val args = Args()
        
        // Capture the error by using onError callback
        var errorMessage: String? = null
        args.parse(arrayOf("unexpected1", "unexpected2", "unexpected3"),
            onError = { error, _ -> errorMessage = error.message }
        )
        
        assertTrue(errorMessage?.contains("Unexpected positional arguments:") == true,
            "Expected multiple unexpected arguments message, got: $errorMessage")
        assertTrue(errorMessage?.contains("'unexpected1'") == true,
            "Expected 'unexpected1' in error message, got: $errorMessage")
        assertTrue(errorMessage?.contains("'unexpected2'") == true,
            "Expected 'unexpected2' in error message, got: $errorMessage")
        assertTrue(errorMessage?.contains("'unexpected3'") == true,
            "Expected 'unexpected3' in error message, got: $errorMessage")
    }

    @Test
    fun `unexpected positional arguments mixed with options`() {
        class Args : Arguments() {
            val verbose by option("--verbose").bool()
            val help by help()
        }

        val args = Args()
        
        var errorMessage: String? = null
        args.parse(arrayOf("--verbose", "unexpected1", "unexpected2"),
            onError = { error, _ -> errorMessage = error.message }
        )
        
        assertTrue(errorMessage?.contains("Unexpected positional arguments:") == true,
            "Expected multiple unexpected arguments message, got: $errorMessage")
        assertTrue(errorMessage?.contains("'unexpected1'") == true,
            "Expected 'unexpected1' in error message, got: $errorMessage")
        assertTrue(errorMessage?.contains("'unexpected2'") == true,
            "Expected 'unexpected2' in error message, got: $errorMessage")
    }
}