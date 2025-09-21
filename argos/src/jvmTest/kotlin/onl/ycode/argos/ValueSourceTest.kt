/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

class ValueSourceTest {

    @Test
    fun testUserProvidedValues() {
        class TestArgs : Arguments(appName = "test") {
            val port by option("--port").int().default(8080)
            val host by option("--host").default("localhost")
            val debug by option("--debug").bool()
            val verbose by option("--verbose").bool().list()
            val tags by option("--tag").list()
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--port", "9000", "--debug", "--verbose", "--verbose", "--tag", "test"))

        assertEquals(ValueSource.USER, args.valueSourceOf(args::port))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::debug))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::verbose))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::tags))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::host))

        // Verify actual values
        assertEquals(9000, args.port)
        assertEquals("localhost", args.host)
        assertEquals(true, args.debug)
        assertEquals(2, args.verbose.size)
        assertEquals(listOf("test"), args.tags)
    }

    @Test
    fun testDefaultValues() {
        class TestArgs : Arguments(appName = "test") {
            val port by option("--port").int().default(8080)
            val host by option("--host").default("localhost")
            val mode by option("--mode").oneOf("fast", "slow").default("fast")
            val enabled by option("--enabled").bool().default(false)
            val timeout by option("--timeout").float().default(30.5f)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf()) // No arguments provided

        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::port))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::host))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::mode))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::enabled))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::timeout))

        // Verify default values are correct
        assertEquals(8080, args.port)
        assertEquals("localhost", args.host)
        assertEquals("fast", args.mode)
        assertEquals(false, args.enabled)
        assertEquals(30.5f, args.timeout)
    }

    @Test
    fun testMissingValues() {
        class TestArgs : Arguments(appName = "test") {
            val port by option("--port").int() // No default
            val host by option("--host")       // No default
            val debug by option("--debug").bool()  // No default
            val tags by option("--tag").list() // No default
            val colors by option("--color").set() // No default
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())

        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::port))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::host))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::debug))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::tags))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::colors))

        // Verify values are null/empty as expected
        assertNull(args.port)
        assertNull(args.host)
        assertNull(args.debug)
        assertEquals(emptyList(), args.tags)
        assertEquals(emptySet(), args.colors)
    }

    @Test
    fun testMixedSources() {
        class TestArgs : Arguments(appName = "test") {
            val userPort by option("--user-port").int()  // Will be USER
            val defaultHost by option("--default-host").default("localhost") // Will be DEFAULT
            val missingApi by option("--missing-api") // Will be MISSING
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--user-port", "3000"))

        assertEquals(ValueSource.USER, args.valueSourceOf(args::userPort))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::defaultHost))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::missingApi))

        assertEquals(3000, args.userPort)
        assertEquals("localhost", args.defaultHost)
        assertNull(args.missingApi)
    }

    @Test
    fun testCollectionValueSources() {
        class TestArgs : Arguments(appName = "test") {
            val userTags by option("--tag").list() // USER when provided
            val missingColors by option("--color").set() // MISSING
            val verboseCount by option("-v").bool().list() // USER when provided
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--tag", "test1", "--tag", "test2", "-v", "-v"))

        assertEquals(ValueSource.USER, args.valueSourceOf(args::userTags))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::verboseCount))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::missingColors))

        assertEquals(listOf("test1", "test2"), args.userTags)
        assertEquals(2, args.verboseCount.size)
        assertEquals(emptySet<String>(), args.missingColors)
    }

    @Test
    fun testBooleanValueSources() {
        class TestArgs : Arguments(appName = "test") {
            val userFlag by option("--user-flag").bool() // USER when --user-flag provided
            val defaultEnabled by option("--default-enabled").bool().default(true) // DEFAULT
            val missingDebug by option("--missing-debug").bool() // MISSING
            val negatableCache by option("--cache").bool().negatable() // USER for --cache or --no-cache
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--user-flag", "--no-cache"))

        assertEquals(ValueSource.USER, args.valueSourceOf(args::userFlag))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::defaultEnabled))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::missingDebug))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::negatableCache)) // --no-cache is still user input

        assertEquals(true, args.userFlag)
        assertEquals(true, args.defaultEnabled)
        assertNull(args.missingDebug)
        assertEquals(false, args.negatableCache) // --no-cache sets to false
    }

    @Test
    fun testDomainValueSources() {
        class TestArgs : Arguments(appName = "test") {
            val alpha by domain("alpha")
            val beta by domain("beta")
            val port by option("--port").int().default(8080)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("alpha", "--port", "9000"))

        // Domain selection should now be trackable
        assertEquals(ValueSource.USER, args.valueSourceOf(args::alpha)) // User selected alpha domain
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::beta)) // Beta domain not selected
        assertEquals(ValueSource.USER, args.valueSourceOf(args::port))

        assertEquals(true, args.alpha)
        assertEquals(false, args.beta)
        assertEquals(9000, args.port)
    }

    @Test
    fun testNonArgosPropertyError() {
        class TestArgs : Arguments(appName = "test") {
            val validOption by option("--valid").int().default(42)
            val regularProperty = "not an option" // Regular property, not Argos-managed
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())

        // This should work fine
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::validOption))

        // This should throw an exception
        val exception = assertFailsWith<IllegalArgumentException> {
            args.valueSourceOf(args::regularProperty)
        }
        assertEquals("Property 'regularProperty' is not managed by Argos or may be a domain property (domains are not supported for valueSource tracking).", exception.message)
    }

    @Test
    fun testPositionalValueSources() {
        class TestArgs : Arguments(appName = "test") {
            val file by positional() // USER when provided
            val optionalFile by positional() // USER or MISSING when not enough positionals
        }

        val args1 = TestArgs()
        args1.parseWithException(arrayOf("input.txt", "output.txt"))

        assertEquals(ValueSource.USER, args1.valueSourceOf(args1::file))
        assertEquals(ValueSource.USER, args1.valueSourceOf(args1::optionalFile))
        assertEquals("input.txt", args1.file)
        assertEquals("output.txt", args1.optionalFile)

        val args2 = TestArgs()
        args2.parseWithException(arrayOf("input.txt"))

        assertEquals(ValueSource.USER, args2.valueSourceOf(args2::file))
        assertEquals(ValueSource.MISSING, args2.valueSourceOf(args2::optionalFile))
        assertEquals("input.txt", args2.file)
        assertNull(args2.optionalFile)
    }

    @Test
    fun testTypeRefinementValueSources() {
        class TestArgs : Arguments(appName = "test") {
            val userPort by option("--port").int() // USER when provided
            val defaultPort by option("--default-port").int().default(8080) // DEFAULT
            val userRate by option("--rate").float() // USER when provided
            val userSize by option("--size").long() // USER when provided
            val defaultMode by option("--mode").oneOf("fast", "slow").default("fast") // DEFAULT
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--port", "9000", "--rate", "1.5", "--size", "1048576"))

        assertEquals(ValueSource.USER, args.valueSourceOf(args::userPort))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::defaultPort))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::userRate))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::userSize))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::defaultMode))

        assertEquals(9000, args.userPort)
        assertEquals(8080, args.defaultPort)
        assertEquals(1.5f, args.userRate)
        assertEquals(1048576L, args.userSize)
        assertEquals("fast", args.defaultMode)
    }

    @Test
    fun testPriorityOrder() {
        class TestArgs : Arguments(appName = "test") {
            val port by option("--port").int().default(8080) // Test without env for now
            val host by option("--host").default("localhost") // Test without env for now
            val mode by option("--mode").default("auto") // Test without env for now
        }

        val args = TestArgs()
        // Test CLI argument overrides defaults
        args.parseWithException(arrayOf("--port", "9000", "--host", "custom.com"))

        assertEquals(ValueSource.USER, args.valueSourceOf(args::port)) // CLI wins
        assertEquals(ValueSource.USER, args.valueSourceOf(args::host)) // CLI wins
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::mode)) // No CLI, use default

        assertEquals(9000, args.port)
        assertEquals("custom.com", args.host)
        assertEquals("auto", args.mode)
    }

    @Test
    fun testComplexScenario() {
        class TestArgs : Arguments(appName = "test") {
            val alpha by domain("alpha")
            val config by option("--config").default("/etc/app.conf")
            val port by option("--port").int().default(8080)
            val verbose by option("-v", "--verbose").bool().list()
            val files by option("--file").list()
            val categories by option("--category").set()
            val optional by option("--optional").bool()
        }

        val args = TestArgs()
        args.parseWithException(arrayOf(
            "alpha",                    // domain selection
            "--port", "3000",           // user-provided int
            "-vv",                      // user-provided count (2)
            "--file", "a.txt",          // user-provided list
            "--file", "b.txt",
            "--category", "web",        // user-provided set
            "--category", "api"
            // config uses default, optional is missing
        ))

        assertEquals(ValueSource.USER, args.valueSourceOf(args::alpha)) // Domain tracking now implemented
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::config))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::port))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::verbose))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::files))
        assertEquals(ValueSource.USER, args.valueSourceOf(args::categories))
        assertEquals(ValueSource.MISSING, args.valueSourceOf(args::optional))

        assertEquals(true, args.alpha)
        assertEquals("/etc/app.conf", args.config)
        assertEquals(3000, args.port)
        assertEquals(2, args.verbose.size)
        assertEquals(listOf("a.txt", "b.txt"), args.files)
        assertEquals(setOf("web", "api"), args.categories)
        assertNull(args.optional)
    }

    @Test
    fun testNullableWithDefault() {
        class TestArgs : Arguments(appName = "test") {
            val stringWithDefault by option("--string").default("default-value")
            val intWithDefault by option("--int").int().default(42)
            val boolWithDefault by option("--bool").bool().default(true)
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())

        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::stringWithDefault))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::intWithDefault))
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::boolWithDefault))

        assertEquals("default-value", args.stringWithDefault)
        assertEquals(42, args.intWithDefault)
        assertEquals(true, args.boolWithDefault)
    }

    @Test
    fun testEnvironmentVariableValueSource() {
        // Test environment variable value source tracking using PATH which should always exist
        class TestArgs : Arguments(appName = "test") {
            val path by option("--path").fromEnv("PATH").default("default-path")
            val nonExistentEnv by option("--non-existent").fromEnv("_ARGOS_NON_EXISTENT_VAR_").default("default-value")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf())

        // PATH should exist and come from environment
        assertEquals(ValueSource.ENVIRONMENT, args.valueSourceOf(args::path))
        // Non-existent env var should use default
        assertEquals(ValueSource.DEFAULT, args.valueSourceOf(args::nonExistentEnv))
        assertEquals("default-value", args.nonExistentEnv)

        // PATH should have actual environment value (not the default)
        assertNotEquals("default-path", args.path)
    }

    @Test
    fun testEnvironmentVariableOverriddenByCommandLine() {
        // Test that command line overrides environment variable with correct value source
        class TestArgs : Arguments(appName = "test") {
            val path by option("--path").fromEnv("PATH").default("default-path")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--path", "custom-path"))

        // Command line should override environment with USER source
        assertEquals(ValueSource.USER, args.valueSourceOf(args::path))
        assertEquals("custom-path", args.path)
    }
}