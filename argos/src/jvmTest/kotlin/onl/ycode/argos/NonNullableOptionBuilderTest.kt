/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NonNullableOptionBuilderTest {

    @Test
    fun nonNullable_fromEnv_worksProperly() {
        class TestArgs : Arguments() {
            val config by option("--config").fromEnv("TEST_CONFIG").default("default.conf")
            val port by option("--port").int().fromEnv("TEST_PORT").default(8080)
        }

        val args = TestArgs()

        // Test with default values when env vars are not set
        args.parseWithException(arrayOf())
        assertEquals("default.conf", args.config)
        assertEquals(8080, args.port)
    }

    @Test
    fun nonNullable_fromEnv_withRequiredOption() {
        class TestArgs : Arguments() {
            val apiKey by option("--api-key").fromEnv("API_KEY").required()
        }

        // Should fail when neither command line nor environment variable is provided
        val args = TestArgs()
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf())
        }
    }

    @Test
    fun nonNullable_fromEnv_commandLineOverridesEnv() {
        class TestArgs : Arguments() {
            val host by option("--host").fromEnv("TEST_HOST").default("localhost")
        }

        // Set up scenario where PATH env var exists (it should always exist)
        // but command line value should override
        val args = TestArgs()
        args.parseWithException(arrayOf("--host", "example.com"))
        assertEquals("example.com", args.host)
    }

    @Test
    fun nonNullable_fromEnv_withIntType() {
        class TestArgs : Arguments() {
            val timeout by option("--timeout").int().fromEnv("TEST_TIMEOUT").default(30)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())
        assertEquals(30, args.timeout)
    }

    @Test
    fun nonNullable_fromEnv_withConstraints() {
        class TestArgs : Arguments() {
            val port by option("--port").int().fromEnv("SERVER_PORT").default(8080)
                .validate("Port must be valid") { it in 1..65535 }
            val host by option("--host").fromEnv("SERVER_HOST").default("localhost")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())
        assertEquals(8080, args.port)
        assertEquals("localhost", args.host)
    }

    @Test
    fun nonNullable_double_basicFunctionality() {
        class TestArgs : Arguments() {
            val rate by option("--rate").double().default(1.5)
            val precision by option("--precision").double().required()
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--precision", "3.14159"))
        assertEquals(1.5, args.rate)
        assertEquals(3.14159, args.precision)
    }

    @Test
    fun nonNullable_double_withValidation() {
        class TestArgs : Arguments() {
            val ratio by option("--ratio").double().default(0.5)
                .validate("Ratio must be between 0 and 1") { it in 0.0..1.0 }
        }

        val args = TestArgs()

        // Valid value should pass
        args.parseWithException(arrayOf("--ratio", "0.75"))
        assertEquals(0.75, args.ratio)

        // Invalid value should fail
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--ratio", "1.5"))
        }
    }

    @Test
    fun nonNullable_double_invalidValueThrowsParseError() {
        class TestArgs : Arguments() {
            val temperature by option("--temp").double().required()
        }

        val args = TestArgs()
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--temp", "not-a-number"))
        }
    }

    @Test
    fun nonNullable_double_withConstraints() {
        class TestArgs : Arguments() {
            val temperature by option("--temp").double().default(20.0)
            val humidity by option("--humidity").double().default(50.0)
                .atMostOneWith(::temperature)
        }

        val args = TestArgs()

        // Should work with just one
        args.parseWithException(arrayOf("--temp", "25.5"))
        assertEquals(25.5, args.temperature)
        assertEquals(50.0, args.humidity)

        // Should fail when both are provided
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--temp", "25.5", "--humidity", "60.0"))
        }
    }

    @Test
    fun nonNullable_double_withEnvVar() {
        class TestArgs : Arguments() {
            val threshold by option("--threshold").double().fromEnv("THRESHOLD").default(0.1)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())
        assertEquals(0.1, args.threshold)
    }

    @Test
    fun nonNullable_double_withMultipleValidators() {
        class TestArgs : Arguments() {
            val score by option("--score").double().default(0.0)
                .validate(
                    "Score must be non-negative" to { it >= 0.0 },
                    "Score must be at most 100" to { it <= 100.0 }
                )
        }

        val args = TestArgs()

        // Valid score
        args.parseWithException(arrayOf("--score", "85.5"))
        assertEquals(85.5, args.score)

        // Invalid negative score
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--score", "-10.0"))
        }

        // Invalid score over 100
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("--score", "150.0"))
        }
    }

    @Test
    fun nonNullable_double_extremeValues() {
        class TestArgs : Arguments() {
            val tiny by option("--tiny").double().default(Double.MIN_VALUE)
            val huge by option("--huge").double().default(Double.MAX_VALUE)
            val negative by option("--negative").double().default(-Double.MAX_VALUE)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())
        assertEquals(Double.MIN_VALUE, args.tiny)
        assertEquals(Double.MAX_VALUE, args.huge)
        assertEquals(-Double.MAX_VALUE, args.negative)
    }

    @Test
    fun nonNullable_double_withDomains() {
        class TestArgs : Arguments() {
            val mathDomain by domain("math")
            val physicsDomain by domain("physics")

            val pi by option("--pi").double().onlyInDomains(::mathDomain).default(3.14159)
            val gravity by option("--gravity").double().onlyInDomains(::physicsDomain).default(9.81)
        }

        val args = TestArgs()

        // Should work in math domain
        args.parseWithException(arrayOf("math", "--pi", "3.141592653"))
        assertEquals("math", args.selectedDomain())
        assertEquals(3.141592653, args.pi)

        // Should fail when using physics option in math domain
        assertFailsWith<ParseError> {
            args.parseWithException(arrayOf("math", "--gravity", "9.8"))
        }
    }

    @Test
    fun nonNullable_fromEnv_withAllDataTypes() {
        class TestArgs : Arguments() {
            val host by option("--host").fromEnv("TEST_HOST").default("localhost")
            val port by option("--port").int().fromEnv("TEST_PORT").default(8080)
            val timeout by option("--timeout").long().fromEnv("TEST_TIMEOUT").default(5000L)
            val rate by option("--rate").float().fromEnv("TEST_RATE").default(1.0f)
            val precision by option("--precision").double().fromEnv("TEST_PRECISION").default(0.001)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())

        // All should use their default values since env vars aren't set
        assertEquals("localhost", args.host)
        assertEquals(8080, args.port)
        assertEquals(5000L, args.timeout)
        assertEquals(1.0f, args.rate)
        assertEquals(0.001, args.precision)
    }

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    @Test
    fun nonNullable_fromEnv_withEnum() {

        class TestArgs : Arguments() {
            val logLevel by option("--log-level").enum<LogLevel>().fromEnv("LOG_LEVEL").default(LogLevel.INFO)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())
        assertEquals(LogLevel.INFO, args.logLevel)
    }

    @Test
    fun nonNullable_fromEnv_helpShowsEnvVar() {
        class TestArgs : Arguments() {
            val apiKey by option("--api-key").fromEnv("API_KEY").default("test-key")
                .help("API key for authentication")
        }

        val args = TestArgs()
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

        assertTrue(helpText.contains("env: API_KEY"), "Help text should mention environment variable")
        assertTrue(helpText.contains("default: test-key"), "Help text should show default value")
    }

    @Test
    fun nonNullable_double_scientificNotation() {
        class TestArgs : Arguments() {
            val scientific by option("--scientific").double().required()
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--scientific", "1.23e-4"))
        assertEquals(1.23e-4, args.scientific)
    }

    @Test
    fun nonNullable_fromEnv_valueSourceTracking() {
        class TestArgs : Arguments() {
            val config by option("--config").fromEnv("TEST_CONFIG").default("default.conf")
            val port by option("--port").int().default(8080)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--port", "9090"))

        // Config should have DEFAULT source (since env var not set)
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::config))

        // Port should have USER source (provided via command line)
        assertEquals(ValueSource.USER, args.valueSourceOf(args::port))
    }
}