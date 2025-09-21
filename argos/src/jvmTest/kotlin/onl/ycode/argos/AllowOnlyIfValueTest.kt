// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Teras <github@teras.dev>

package onl.ycode.argos

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AllowOnlyIfValueTest {

    class BasicAllowOnlyIfArgs : Arguments() {
        val mode by option("--mode")
        val backup by option("--backup").allowOnlyIfValue(::mode) { it == "production" }
        val file by option("--file")
    }

    @Test
    fun `allowOnlyIfValue - basic functionality - allowed when condition is true`() {
        val args = BasicAllowOnlyIfArgs()
        args.parseWithException(arrayOf("--mode", "production", "--backup", "daily"))

        assertEquals("production", args.mode)
        assertEquals("daily", args.backup)
    }

    @Test
    fun `allowOnlyIfValue - basic functionality - allowed when option not provided`() {
        val args = BasicAllowOnlyIfArgs()
        args.parseWithException(arrayOf("--mode", "development"))

        assertEquals("development", args.mode)
        assertEquals(null, args.backup)
    }

    @Test
    fun `allowOnlyIfValue - error when condition is false`() {
        val args = BasicAllowOnlyIfArgs()

        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf("--mode", "development", "--backup", "daily"))
        }

        assertTrue(exception.message!!.contains("--backup is not allowed"))
        assertTrue(exception.message!!.contains("--mode has wrong value"))
    }

    @Test
    fun `allowOnlyIfValue - error when reference option missing`() {
        val args = BasicAllowOnlyIfArgs()

        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf("--backup", "daily"))
        }

        assertTrue(exception.message!!.contains("--backup is not allowed"))
    }

    class ChainedAllowOnlyIfArgs : Arguments() {
        val mode by option("--mode")
        val level by option("--level").int()
        val advanced by option("--advanced")
            .allowOnlyIfValue(::mode) { it == "expert" }
            .allowOnlyIfValue(::level) { it != null && it > 5 }
    }

    @Test
    fun `allowOnlyIfValue - chained conditions - both conditions true`() {
        val args = ChainedAllowOnlyIfArgs()
        args.parseWithException(arrayOf("--mode", "expert", "--level", "10", "--advanced", "feature"))

        assertEquals("expert", args.mode)
        assertEquals(10, args.level)
        assertEquals("feature", args.advanced)
    }

    @Test
    fun `allowOnlyIfValue - chained conditions - first condition false`() {
        val args = ChainedAllowOnlyIfArgs()

        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf("--mode", "beginner", "--level", "10", "--advanced", "feature"))
        }

        assertTrue(exception.message!!.contains("--advanced is not allowed"))
        assertTrue(exception.message!!.contains("--mode has wrong value"))
    }

    @Test
    fun `allowOnlyIfValue - chained conditions - second condition false`() {
        val args = ChainedAllowOnlyIfArgs()

        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf("--mode", "expert", "--level", "3", "--advanced", "feature"))
        }

        assertTrue(exception.message!!.contains("--advanced is not allowed"))
        assertTrue(exception.message!!.contains("--level has wrong value"))
    }

    class CollectionAllowOnlyIfArgs : Arguments() {
        val mode by option("--mode")
        val files by option("--file").list().allowOnlyIfValue(::mode) { it == "batch" }
    }

    @Test
    fun `allowOnlyIfValue - list options - allowed when condition true`() {
        val args = CollectionAllowOnlyIfArgs()
        args.parseWithException(arrayOf("--mode", "batch", "--file", "a.txt", "--file", "b.txt"))

        assertEquals("batch", args.mode)
        assertEquals(listOf("a.txt", "b.txt"), args.files)
    }

    @Test
    fun `allowOnlyIfValue - list options - error when condition false`() {
        val args = CollectionAllowOnlyIfArgs()

        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf("--mode", "single", "--file", "a.txt"))
        }

        assertTrue(exception.message!!.contains("--file is not allowed"))
    }

    class NonNullableAllowOnlyIfArgs : Arguments() {
        val mode by option("--mode").default("development")
        val config by option("--config").required().allowOnlyIfValue(::mode) { it == "production" }
    }

    @Test
    fun `allowOnlyIfValue - non-nullable options - allowed when condition true`() {
        val args = NonNullableAllowOnlyIfArgs()
        args.parseWithException(arrayOf("--mode", "production", "--config", "prod.conf"))

        assertEquals("production", args.mode)
        assertEquals("prod.conf", args.config)
    }

    @Test
    fun `allowOnlyIfValue - non-nullable options - error when condition false`() {
        val args = NonNullableAllowOnlyIfArgs()

        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf("--config", "prod.conf"))
        }

        assertTrue(exception.message!!.contains("--config is not allowed"))
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("Expected $expected but was $actual")
        }
    }

    class OrConditionAllowOnlyIfArgs : Arguments() {
        val file by option("--file")
        val config by option("--config")
        val backup by option("--backup").allowOnlyIfValue(::file) { _ ->
            // OR condition: allow backup when file OR config is present
            this.file != null || this.config != null
        }
    }

    @Test
    fun `allowOnlyIfValue - OR condition using this - file present`() {
        val args = OrConditionAllowOnlyIfArgs()
        args.parseWithException(arrayOf("--file", "input.txt", "--backup", "daily"))

        assertEquals("input.txt", args.file)
        assertEquals("daily", args.backup)
    }

    @Test
    fun `allowOnlyIfValue - OR condition using this - config present`() {
        val args = OrConditionAllowOnlyIfArgs()
        args.parseWithException(arrayOf("--config", "app.conf", "--backup", "daily"))

        assertEquals("app.conf", args.config)
        assertEquals("daily", args.backup)
    }

    @Test
    fun `allowOnlyIfValue - OR condition using this - both present`() {
        val args = OrConditionAllowOnlyIfArgs()
        args.parseWithException(arrayOf("--file", "input.txt", "--config", "app.conf", "--backup", "daily"))

        assertEquals("input.txt", args.file)
        assertEquals("app.conf", args.config)
        assertEquals("daily", args.backup)
    }

    @Test
    fun `allowOnlyIfValue - OR condition using this - neither present`() {
        val args = OrConditionAllowOnlyIfArgs()

        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf("--backup", "daily"))
        }

        assertTrue(exception.message!!.contains("--backup is not allowed"))
    }

    class DomainAllowOnlyIfArgs : Arguments(appName = "test") {
        val mode by option("--mode")
        val verbose by option("--verbose").bool()

        val buildDomain by domain("build")
            .allowOnlyIfValue(::verbose, ::mode) { it == "debug" }
    }

    @Test
    fun `allowOnlyIfValue - domain scoped - allowed when condition true in domain`() {
        val args = DomainAllowOnlyIfArgs()
        args.parseWithException(arrayOf("build", "--mode", "debug", "--verbose"))

        assertEquals("debug", args.mode)
        assertEquals(true, args.verbose)
        assertEquals("build", args.selectedDomain())
    }

    @Test
    fun `allowOnlyIfValue - domain scoped - error when condition false in domain`() {
        val args = DomainAllowOnlyIfArgs()

        val exception = assertThrows<ParseError> {
            args.parseWithException(arrayOf("build", "--mode", "release", "--verbose"))
        }

        assertTrue(exception.message!!.contains("--verbose is not allowed"))
    }


    private fun assertTrue(condition: Boolean) {
        if (!condition) {
            throw AssertionError("Expected condition to be true")
        }
    }
}