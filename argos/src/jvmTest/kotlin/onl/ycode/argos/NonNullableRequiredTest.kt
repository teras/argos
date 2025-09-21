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
import kotlin.test.assertNull

class NonNullableRequiredTest {

    @Test
    fun testNonNullableStringRequired() {
        class TestArgs : Arguments() {
            val config by option("--config").required() // Should be non-nullable String
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--config", "app.conf"))

        // No null assertion needed - config is non-nullable
        assertEquals("app.conf", args.config)

        // Verify type is String, not String? - this should compile without null assertion
        val configValue: String = args.config
        assertEquals("app.conf", configValue)
    }

    @Test
    fun testNonNullableIntRequired() {
        class TestArgs : Arguments() {
            val port by option("--port").int().required() // Should be non-nullable Int
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--port", "8080"))

        // No null assertion needed - port is non-nullable
        assertEquals(8080, args.port)

        // Verify type is Int, not Int? - this should compile without null assertion
        val portValue: Int = args.port
        assertEquals(8080, portValue)
    }

    @Test
    fun testNonNullableCharArrayPasswordRequired() {
        class TestArgs : Arguments() {
            val password by option("--password").input().required() // Should be non-nullable CharArray
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--password", "secret123"))

        // No null assertion needed - password is non-nullable
        assertEquals("secret123", String(args.password))

        // Verify type is CharArray, not CharArray? - this should compile without null assertion
        val passwordValue: CharArray = args.password
        assertEquals("secret123", String(passwordValue))
    }

    @Test
    fun testNonNullableRequiredString() {
        class TestArgs : Arguments() {
            val config by option("--config").required() // Should be non-nullable String
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--config", "app.conf"))

        // No null assertion needed - config is non-nullable
        assertEquals("app.conf", args.config)

        // Verify type is String, not String? - this should compile without null assertion
        val configValue: String = args.config
        assertEquals("app.conf", configValue)
    }

    @Test
    fun testNonNullableRequiredCharArray() {
        class TestArgs : Arguments() {
            val password by option("--password").input().required() // Should be non-nullable CharArray
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--password", "secret123"))

        // No null assertion needed - password is non-nullable
        assertEquals("secret123", String(args.password))

        // Verify type is CharArray, not CharArray? - this should compile without null assertion
        val passwordValue: CharArray = args.password
        assertEquals("secret123", String(passwordValue))
    }

    @Test
    fun testMixedNullableAndNonNullable() {
        class TestArgs : Arguments() {
            val requiredConfig by option("--config").required() // Non-nullable String
            val optionalPort by option("--port").int() // Nullable Int?
            val requiredPassword by option("--password").input().required() // Non-nullable CharArray
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--config", "app.conf", "--password", "secret123"))

        // Required options are non-nullable - should compile without null assertions
        val config: String = args.requiredConfig
        val password: CharArray = args.requiredPassword

        // Optional option is nullable
        val port: Int? = args.optionalPort

        assertEquals("app.conf", config)
        assertEquals("secret123", String(password))
        assertNull(port)
    }

    @Test
    fun testRequiredFailsWhenMissing() {
        class TestArgs : Arguments() {
            val config by option("--config").required() // Should fail when missing
        }

        val args = TestArgs()
        // The required() extension function may throw IllegalStateException instead of ParseError
        assertFailsWith<Exception> {
            args.parseWithException(arrayOf())
        }
    }

    @Test
    fun testNonNullableRequiredList() {
        class TestArgs : Arguments() {
            val files by option("--file").list().required() // Should be non-nullable List<String>
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--file", "a.txt", "--file", "b.txt"))

        // Should compile without null assertion
        val fileList: List<String> = args.files
        assertEquals(listOf("a.txt", "b.txt"), fileList)
    }

    @Test
    fun testNonNullableRequiredSet() {
        class TestArgs : Arguments() {
            val tags by option("--tag").set().required() // Should be non-nullable Set<String>
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--tag", "important", "--tag", "urgent"))

        // Should compile without null assertion
        val tagSet: Set<String> = args.tags
        assertEquals(setOf("important", "urgent"), tagSet)
    }

    @Test
    fun testNonNullableRequiredCount() {
        class TestArgs : Arguments() {
            val verbosity by option("-v", "--verbose").bool().list().required() // Should be non-nullable List<Boolean>
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("-v", "-v", "-v"))

        // Should compile without null assertion
        val level: List<Boolean> = args.verbosity
        assertEquals(3, level.size)
    }
}
