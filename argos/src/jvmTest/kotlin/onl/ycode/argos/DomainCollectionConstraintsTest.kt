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

class DomainCollectionConstraintsTest {

    @Test
    fun domainListConstraints_required() {
        class ListRequiredArgs : Arguments(
            appName = "list-required-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build")
                .required(::sources)
                .atLeast(::tags, 2)
            val testDomain by domain("test")

            val sources by option("--source").list()
            val tags by option("--tag").list()
            val flags by option("--flag").bool().list()
            val file by positional()
        }

        // Test valid: sources provided in build domain
        val a1 = ListRequiredArgs()
        a1.parseWithException(arrayOf("build", "--source", "main.kt", "--tag", "core", "--tag", "api", "file"))
        assertEquals(listOf("main.kt"), a1.sources)
        assertEquals(listOf("core", "api"), a1.tags)

        // Test valid: multiple sources and tags in build domain
        val a2 = ListRequiredArgs()
        a2.parseWithException(arrayOf("build", "--source", "main.kt", "--source", "util.kt", "--tag", "core", "--tag", "api", "--tag", "test", "file"))
        assertEquals(listOf("main.kt", "util.kt"), a2.sources)
        assertEquals(listOf("core", "api", "test"), a2.tags)

        // Test valid: no constraints in test domain
        val a3 = ListRequiredArgs()
        a3.parseWithException(arrayOf("test", "file"))
        assertEquals(emptyList<String>(), a3.sources)
        assertEquals(emptyList<String>(), a3.tags)

        // Test failure: no sources in build domain
        val a4 = ListRequiredArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--tag", "core", "--tag", "api", "file"))
        }

        // Test failure: only one tag in build domain (atLeast 2 required)
        val a5 = ListRequiredArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("build", "--source", "main.kt", "--tag", "core", "file"))
        }
    }

    @Test
    fun domainSetConstraints_exactlyOne() {
        class SetExactlyOneArgs : Arguments(
            appName = "set-exactly-one-test",
            useANSITerminal = false
        ) {
            val deployDomain by domain("deploy").exactlyOne(::environments, ::targets)
            val configDomain by domain("config")

            val environments by option("--env").set()
            val targets by option("--target").set()
            val options by option("--option").set()
            val file by positional()
        }

        // Test valid: environments provided in deploy domain
        val a1 = SetExactlyOneArgs()
        a1.parseWithException(arrayOf("deploy", "--env", "staging", "--env", "prod", "file"))
        assertEquals(setOf("staging", "prod"), a1.environments)
        assertEquals(emptySet<String>(), a1.targets)

        // Test valid: targets provided in deploy domain
        val a2 = SetExactlyOneArgs()
        a2.parseWithException(arrayOf("deploy", "--target", "server1", "--target", "server2", "--target", "server1", "file"))
        assertEquals(emptySet<String>(), a2.environments)
        assertEquals(setOf("server1", "server2"), a2.targets) // duplicates removed

        // Test valid: both in config domain (constraint doesn't apply)
        val a3 = SetExactlyOneArgs()
        a3.parseWithException(arrayOf("config", "--env", "dev", "--target", "localhost", "file"))
        assertEquals(setOf("dev"), a3.environments)
        assertEquals(setOf("localhost"), a3.targets)

        // Test failure: neither provided in deploy domain
        val a4 = SetExactlyOneArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("deploy", "--option", "verbose", "file"))
        }

        // Test failure: both provided in deploy domain
        val a5 = SetExactlyOneArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("deploy", "--env", "staging", "--target", "server1", "file"))
        }
    }

    @Test
    fun domainCountConstraints_atLeastOne() {
        class CountAtLeastOneArgs : Arguments(
            appName = "count-at-least-one-test",
            useANSITerminal = false
        ) {
            val debugDomain by domain("debug").atLeastOne(::verbose, ::trace)
            val quietDomain by domain("quiet")

            val verbose by option("-v", "--verbose").bool().list()
            val trace by option("-t", "--trace").bool().list()
            val info by option("-i", "--info").bool().list()
            val file by positional()
        }

        // Test valid: verbose provided in debug domain
        val a1 = CountAtLeastOneArgs()
        a1.parseWithException(arrayOf("debug", "-v", "file"))
        assertEquals(1, a1.verbose.size)
        assertEquals(0, a1.trace.size)

        // Test valid: trace provided in debug domain
        val a2 = CountAtLeastOneArgs()
        a2.parseWithException(arrayOf("debug", "-ttt", "file"))
        assertEquals(0, a2.verbose.size)
        assertEquals(3, a2.trace.size)

        // Test valid: both provided in debug domain
        val a3 = CountAtLeastOneArgs()
        a3.parseWithException(arrayOf("debug", "-vv", "-t", "file"))
        assertEquals(2, a3.verbose.size)
        assertEquals(1, a3.trace.size)

        // Test valid: neither in quiet domain (constraint doesn't apply)
        val a4 = CountAtLeastOneArgs()
        a4.parseWithException(arrayOf("quiet", "-i", "file"))
        assertEquals(0, a4.verbose.size)
        assertEquals(0, a4.trace.size)
        assertEquals(1, a4.info.size)

        // Test failure: neither provided in debug domain
        val a5 = CountAtLeastOneArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("debug", "-i", "file"))
        }
    }

    @Test
    fun domainMixedCollectionConstraints() {
        class MixedCollectionArgs : Arguments(
            appName = "mixed-collection-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build")
                .required(::sources)
                .atMostOne(::libraries, ::dependencies)
                .requireIfAnyPresent(::outputs, ::sources, ::libraries)

            val sources by option("--source").list()
            val libraries by option("--lib").set()
            val dependencies by option("--dep").set()
            val outputs by option("--output").list()
            val verbosity by option("-v").bool().list()
            val file by positional()
        }

        // Test valid: sources and outputs, no libraries/dependencies
        val a1 = MixedCollectionArgs()
        a1.parseWithException(arrayOf("build", "--source", "main.kt", "--source", "util.kt", "--output", "build/main", "--output", "build/util", "file"))
        assertEquals(listOf("main.kt", "util.kt"), a1.sources)
        assertEquals(emptySet<String>(), a1.libraries)
        assertEquals(emptySet<String>(), a1.dependencies)
        assertEquals(listOf("build/main", "build/util"), a1.outputs)

        // Test valid: sources and libraries (with outputs)
        val a2 = MixedCollectionArgs()
        a2.parseWithException(arrayOf("build", "--source", "app.kt", "--lib", "json.jar", "--lib", "http.jar", "--output", "dist/", "file"))
        assertEquals(listOf("app.kt"), a2.sources)
        assertEquals(setOf("json.jar", "http.jar"), a2.libraries)
        assertEquals(emptySet<String>(), a2.dependencies)
        assertEquals(listOf("dist/"), a2.outputs)

        // Test valid: sources and dependencies (with outputs)
        val a3 = MixedCollectionArgs()
        a3.parseWithException(arrayOf("build", "--source", "test.kt", "--dep", "junit", "--dep", "mockito", "--output", "test-build/", "file"))
        assertEquals(listOf("test.kt"), a3.sources)
        assertEquals(emptySet<String>(), a3.libraries)
        assertEquals(setOf("junit", "mockito"), a3.dependencies)
        assertEquals(listOf("test-build/"), a3.outputs)

        // Test failure: no sources (required)
        val a4 = MixedCollectionArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--lib", "json.jar", "--output", "dist/", "file"))
        }

        // Test failure: both libraries and dependencies (atMostOne)
        val a5 = MixedCollectionArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("build", "--source", "main.kt", "--lib", "json.jar", "--dep", "junit", "--output", "dist/", "file"))
        }

        // Test failure: sources provided but outputs missing (requireIfAnyPresent)
        val a6 = MixedCollectionArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("build", "--source", "main.kt", "file"))
        }
    }

    @Test
    fun domainCollectionConflicts() {
        class CollectionConflictsArgs : Arguments(
            appName = "collection-conflicts-test",
            useANSITerminal = false
        ) {
            val packageDomain by domain("package")
                .conflicts(::includes, ::excludes)
                .atLeast(::files, 1)

            val includes by option("--include").list()
            val excludes by option("--exclude").list()
            val files by option("--file").set()
            val metadata by option("--metadata").list()
            val file by positional()
        }

        // Test valid: includes and files
        val a1 = CollectionConflictsArgs()
        a1.parseWithException(arrayOf("package", "--include", "src/**", "--include", "res/**", "--file", "main.jar", "file"))
        assertEquals(listOf("src/**", "res/**"), a1.includes)
        assertEquals(emptyList<String>(), a1.excludes)
        assertEquals(setOf("main.jar"), a1.files)

        // Test valid: excludes and files
        val a2 = CollectionConflictsArgs()
        a2.parseWithException(arrayOf("package", "--exclude", "test/**", "--exclude", "tmp/**", "--file", "app.jar", "--file", "util.jar", "file"))
        assertEquals(emptyList<String>(), a2.includes)
        assertEquals(listOf("test/**", "tmp/**"), a2.excludes)
        assertEquals(setOf("app.jar", "util.jar"), a2.files)

        // Test valid: only files (no includes/excludes)
        val a3 = CollectionConflictsArgs()
        a3.parseWithException(arrayOf("package", "--file", "core.jar", "--metadata", "version=1.0", "file"))
        assertEquals(emptyList<String>(), a3.includes)
        assertEquals(emptyList<String>(), a3.excludes)
        assertEquals(setOf("core.jar"), a3.files)
        assertEquals(listOf("version=1.0"), a3.metadata)

        // Test failure: no files (atLeast 1 required)
        val a4 = CollectionConflictsArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("package", "--include", "src/**", "file"))
        }

        // Test failure: both includes and excludes (conflicts)
        val a5 = CollectionConflictsArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("package", "--include", "src/**", "--exclude", "test/**", "--file", "app.jar", "file"))
        }
    }

    @Test
    fun domainCollectionRequireIfValue() {
        class CollectionRequireIfValueArgs : Arguments(
            appName = "collection-require-if-value-test",
            useANSITerminal = false
        ) {
            val testDomain by domain("test")
                .requireIfValue(::testFiles, ::mode) { it == "integration" }
                .requireIfValue(::reports, ::verbosity) { it != null && it.size > 2 }

            val mode by option("--mode").oneOf("unit", "integration", "e2e")
            val testFiles by option("--test-file").list()
            val verbosity by option("-v").bool().list()
            val reports by option("--report").set()
            val parallel by option("--parallel").bool()
            val file by positional()
        }

        // Test valid: mode is integration, test files provided
        val a1 = CollectionRequireIfValueArgs()
        a1.parseWithException(arrayOf("test", "--mode", "integration", "--test-file", "integration1.kt", "--test-file", "integration2.kt", "file"))
        assertEquals("integration", a1.mode)
        assertEquals(listOf("integration1.kt", "integration2.kt"), a1.testFiles)

        // Test valid: mode is unit, test files not required
        val a2 = CollectionRequireIfValueArgs()
        a2.parseWithException(arrayOf("test", "--mode", "unit", "--parallel", "file"))
        assertEquals("unit", a2.mode)
        assertEquals(emptyList<String>(), a2.testFiles)
        assertEquals(true, a2.parallel)

        // Test valid: verbosity > 2, reports provided
        val a3 = CollectionRequireIfValueArgs()
        a3.parseWithException(arrayOf("test", "--mode", "unit", "-vvv", "--report", "junit", "--report", "coverage", "file"))
        assertEquals("unit", a3.mode)
        assertEquals(3, a3.verbosity.size)
        assertEquals(setOf("junit", "coverage"), a3.reports)

        // Test valid: verbosity <= 2, reports not required
        val a4 = CollectionRequireIfValueArgs()
        a4.parseWithException(arrayOf("test", "--mode", "unit", "-vv", "file"))
        assertEquals("unit", a4.mode)
        assertEquals(2, a4.verbosity.size)
        assertEquals(emptySet<String>(), a4.reports)

        // Test valid: no mode or verbosity, no requirements
        val a5 = CollectionRequireIfValueArgs()
        a5.parseWithException(arrayOf("test", "--parallel", "file"))
        assertNull(a5.mode)
        assertEquals(0, a5.verbosity.size)
        assertEquals(emptyList<String>(), a5.testFiles)
        assertEquals(emptySet<String>(), a5.reports)

        // Test failure: mode is integration but test files missing
        val a6 = CollectionRequireIfValueArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("test", "--mode", "integration", "file"))
        }

        // Test failure: verbosity > 2 but reports missing
        val a7 = CollectionRequireIfValueArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("test", "--mode", "unit", "-vvvv", "file"))
        }
    }

    @Test
    fun domainCollectionConstraints_multipleDomains() {
        class MultiDomainCollectionArgs : Arguments(
            appName = "multi-domain-collection-test",
            useANSITerminal = false
        ) {
            val devDomain by domain("dev")
                .required(::sources)
                .atMostOne(::watchFiles, ::watchDirs)
            val prodDomain by domain("prod")
                .atLeast(::servers, 2)
                .exactlyOne(::databases, ::caches)
            val testDomain by domain("test")
                .conflicts(::unitTests, ::integrationTests)
                .requireIfAnyPresent(::reporters, ::unitTests, ::integrationTests)

            val sources by option("--source").list()
            val watchFiles by option("--watch-file").set()
            val watchDirs by option("--watch-dir").set()
            val servers by option("--server").list()
            val databases by option("--database").set()
            val caches by option("--cache").set()
            val unitTests by option("--unit-test").list()
            val integrationTests by option("--integration-test").list()
            val reporters by option("--reporter").set()
            val file by positional()
        }

        // Test dev domain constraints
        val a1 = MultiDomainCollectionArgs()
        a1.parseWithException(arrayOf("dev", "--source", "main.kt", "--source", "util.kt", "--watch-file", "config.json", "file"))
        assertEquals(listOf("main.kt", "util.kt"), a1.sources)
        assertEquals(setOf("config.json"), a1.watchFiles)
        assertEquals(emptySet<String>(), a1.watchDirs)

        // Test prod domain constraints
        val a2 = MultiDomainCollectionArgs()
        a2.parseWithException(arrayOf("prod", "--server", "web1", "--server", "web2", "--server", "web3", "--database", "primary", "--database", "replica", "file"))
        assertEquals(listOf("web1", "web2", "web3"), a2.servers)
        assertEquals(setOf("primary", "replica"), a2.databases)
        assertEquals(emptySet<String>(), a2.caches)

        // Test test domain constraints
        val a3 = MultiDomainCollectionArgs()
        a3.parseWithException(arrayOf("test", "--unit-test", "UserTest.kt", "--unit-test", "ServiceTest.kt", "--reporter", "junit", "--reporter", "html", "file"))
        assertEquals(listOf("UserTest.kt", "ServiceTest.kt"), a3.unitTests)
        assertEquals(emptyList<String>(), a3.integrationTests)
        assertEquals(setOf("junit", "html"), a3.reporters)

        // Test failure in dev domain
        val a4 = MultiDomainCollectionArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("dev", "--watch-file", "config.json", "file")) // missing required sources
        }

        val a5 = MultiDomainCollectionArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("dev", "--source", "main.kt", "--watch-file", "config.json", "--watch-dir", "src/", "file")) // atMostOne violation
        }

        // Test failure in prod domain
        val a6 = MultiDomainCollectionArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("prod", "--server", "web1", "--database", "primary", "file")) // atLeast 2 servers required
        }

        val a7 = MultiDomainCollectionArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("prod", "--server", "web1", "--server", "web2", "--database", "primary", "--cache", "redis", "file")) // exactlyOne violation
        }

        // Test failure in test domain
        val a8 = MultiDomainCollectionArgs()
        assertFailsWith<ParseError> {
            a8.parseWithException(arrayOf("test", "--unit-test", "UserTest.kt", "--integration-test", "ApiTest.kt", "file")) // conflicts violation
        }

        val a9 = MultiDomainCollectionArgs()
        assertFailsWith<ParseError> {
            a9.parseWithException(arrayOf("test", "--unit-test", "UserTest.kt", "file")) // missing required reporters
        }
    }
}
