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

class DomainCrossTypeConstraintsTest {

    @Test
    fun domainCrossType_intAndStringConstraints() {
        class IntStringConstraintsArgs : Arguments(
            appName = "int-string-constraints-test",
            useANSITerminal = false
        ) {
            val serverDomain by domain("server")
                .exactlyOne(::port, ::socket)
                .requireIfValue(::config, ::port) { it != null && it > 8000 }
            val clientDomain by domain("client")

            val port by option("--port").int()
            val socket by option("--socket")
            val config by option("--config")
            val timeout by option("--timeout").int()
            val file by positional()
        }

        // Test valid: port provided in server domain
        val a1 = IntStringConstraintsArgs()
        a1.parseWithException(arrayOf("server", "--port", "9000", "--config", "server.conf", "file"))
        assertEquals(9000, a1.port)
        assertNull(a1.socket)
        assertEquals("server.conf", a1.config)

        // Test valid: socket provided in server domain
        val a2 = IntStringConstraintsArgs()
        a2.parseWithException(arrayOf("server", "--socket", "/tmp/server.sock", "file"))
        assertNull(a2.port)
        assertEquals("/tmp/server.sock", a2.socket)
        assertNull(a2.config)

        // Test valid: low port, config not required in server domain
        val a3 = IntStringConstraintsArgs()
        a3.parseWithException(arrayOf("server", "--port", "8000", "file"))
        assertEquals(8000, a3.port)
        assertNull(a3.socket)
        assertNull(a3.config)

        // Test valid: port and socket in client domain (constraints don't apply)
        val a4 = IntStringConstraintsArgs()
        a4.parseWithException(arrayOf("client", "--port", "9000", "--socket", "/tmp/client.sock", "file"))
        assertEquals(9000, a4.port)
        assertEquals("/tmp/client.sock", a4.socket)

        // Test failure: neither port nor socket in server domain
        val a5 = IntStringConstraintsArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("server", "--timeout", "5000", "file"))
        }

        // Test failure: both port and socket in server domain
        val a6 = IntStringConstraintsArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("server", "--port", "9000", "--socket", "/tmp/server.sock", "file"))
        }

        // Test failure: high port but config missing in server domain
        val a7 = IntStringConstraintsArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("server", "--port", "9001", "file"))
        }
    }

    @Test
    fun domainCrossType_floatAndBoolConstraints() {
        class FloatBoolConstraintsArgs : Arguments(
            appName = "float-bool-constraints-test",
            useANSITerminal = false
        ) {
            val analyticsDomain by domain("analytics")
                .atMostOne(::threshold, ::adaptive)
                .requireIfValue(::precision, ::threshold) { it != null && it > 0.5f }
            val basicDomain by domain("basic")

            val threshold by option("--threshold").float()
            val adaptive by option("--adaptive").bool()
            val precision by option("--precision").float()
            val verbose by option("--verbose").bool()
            val file by positional()
        }

        // Test valid: threshold provided in analytics domain
        val a1 = FloatBoolConstraintsArgs()
        a1.parseWithException(arrayOf("analytics", "--threshold", "0.8", "--precision", "0.95", "file"))
        assertEquals(0.8f, a1.threshold)
        assertNull(a1.adaptive)
        assertEquals(0.95f, a1.precision)

        // Test valid: adaptive provided in analytics domain
        val a2 = FloatBoolConstraintsArgs()
        a2.parseWithException(arrayOf("analytics", "--adaptive", "file"))
        assertNull(a2.threshold)
        assertEquals(true, a2.adaptive)
        assertNull(a2.precision)

        // Test valid: low threshold, precision not required in analytics domain
        val a3 = FloatBoolConstraintsArgs()
        a3.parseWithException(arrayOf("analytics", "--threshold", "0.3", "file"))
        assertEquals(0.3f, a3.threshold)
        assertNull(a3.adaptive)
        assertNull(a3.precision)

        // Test valid: threshold = 0.5 (boundary), precision not required
        val a4 = FloatBoolConstraintsArgs()
        a4.parseWithException(arrayOf("analytics", "--threshold", "0.5", "file"))
        assertEquals(0.5f, a4.threshold)
        assertNull(a4.precision)

        // Test valid: both in basic domain (constraints don't apply)
        val a5 = FloatBoolConstraintsArgs()
        a5.parseWithException(arrayOf("basic", "--threshold", "0.9", "--adaptive", "file"))
        assertEquals(0.9f, a5.threshold)
        assertEquals(true, a5.adaptive)

        // Test failure: both threshold and adaptive in analytics domain
        val a6 = FloatBoolConstraintsArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("analytics", "--threshold", "0.7", "--adaptive", "file"))
        }

        // Test failure: high threshold but precision missing in analytics domain
        val a7 = FloatBoolConstraintsArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("analytics", "--threshold", "0.9", "file"))
        }
    }

    @Test
    fun domainCrossType_enumAndListConstraints() {
        class EnumListConstraintsArgs : Arguments(
            appName = "enum-list-constraints-test",
            useANSITerminal = false
        ) {
            val buildDomain by domain("build")
                .exactlyOne(::target, ::sources)
                .requireIfValue(::outputs, ::target) { it == "release" }

            val target by option("--target").oneOf("debug", "release", "test")
            val sources by option("--source").list()
            val outputs by option("--output").list()
            val flags by option("--flag").list()
            val file by positional()
        }

        // Test valid: target provided in build domain
        val a1 = EnumListConstraintsArgs()
        a1.parseWithException(arrayOf("build", "--target", "debug", "file"))
        assertEquals("debug", a1.target)
        assertEquals(emptyList<String>(), a1.sources)
        assertEquals(emptyList<String>(), a1.outputs)

        // Test valid: sources provided in build domain
        val a2 = EnumListConstraintsArgs()
        a2.parseWithException(arrayOf("build", "--source", "main.kt", "--source", "util.kt", "file"))
        assertNull(a2.target)
        assertEquals(listOf("main.kt", "util.kt"), a2.sources)

        // Test valid: release target with outputs in build domain
        val a3 = EnumListConstraintsArgs()
        a3.parseWithException(arrayOf("build", "--target", "release", "--output", "dist/app.jar", "--output", "dist/lib.jar", "file"))
        assertEquals("release", a3.target)
        assertEquals(emptyList<String>(), a3.sources)
        assertEquals(listOf("dist/app.jar", "dist/lib.jar"), a3.outputs)

        // Test valid: test target, outputs not required in build domain
        val a4 = EnumListConstraintsArgs()
        a4.parseWithException(arrayOf("build", "--target", "test", "file"))
        assertEquals("test", a4.target)
        assertEquals(emptyList<String>(), a4.outputs)

        // Test failure: neither target nor sources in build domain
        val a5 = EnumListConstraintsArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("build", "--flag", "verbose", "file"))
        }

        // Test failure: both target and sources in build domain
        val a6 = EnumListConstraintsArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("build", "--target", "debug", "--source", "main.kt", "file"))
        }

        // Test failure: release target but outputs missing in build domain
        val a7 = EnumListConstraintsArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("build", "--target", "release", "file"))
        }
    }

    @Test
    fun domainCrossType_countAndSetConstraints() {
        class CountSetConstraintsArgs : Arguments(
            appName = "count-set-constraints-test",
            useANSITerminal = false
        ) {
            val testDomain by domain("test")
                .atLeastOne(::verbosity, ::reporters)
                .requireIfValue(::tags, ::verbosity) { it != null && it.size >= 2 }

            val verbosity by option("-v", "--verbose").bool().list()
            val reporters by option("--reporter").set()
            val tags by option("--tag").set()
            val parallel by option("--parallel").bool()
            val file by positional()
        }

        // Test valid: verbosity provided in test domain
        val a1 = CountSetConstraintsArgs()
        a1.parseWithException(arrayOf("test", "-v", "file"))
        assertEquals(1, a1.verbosity.size)
        assertEquals(emptySet<String>(), a1.reporters)
        assertEquals(emptySet<String>(), a1.tags)

        // Test valid: reporters provided in test domain
        val a2 = CountSetConstraintsArgs()
        a2.parseWithException(arrayOf("test", "--reporter", "junit", "--reporter", "html", "file"))
        assertEquals(0, a2.verbosity.size)
        assertEquals(setOf("junit", "html"), a2.reporters)

        // Test valid: high verbosity with tags in test domain
        val a3 = CountSetConstraintsArgs()
        a3.parseWithException(arrayOf("test", "-vv", "--tag", "integration", "--tag", "slow", "file"))
        assertEquals(2, a3.verbosity.size)
        assertEquals(emptySet<String>(), a3.reporters)
        assertEquals(setOf("integration", "slow"), a3.tags)

        // Test valid: both verbosity and reporters in test domain
        val a4 = CountSetConstraintsArgs()
        a4.parseWithException(arrayOf("test", "-v", "--reporter", "coverage", "file"))
        assertEquals(1, a4.verbosity.size)
        assertEquals(setOf("coverage"), a4.reporters)

        // Test valid: low verbosity, tags not required in test domain
        val a5 = CountSetConstraintsArgs()
        a5.parseWithException(arrayOf("test", "-v", "file"))
        assertEquals(1, a5.verbosity.size)
        assertEquals(emptySet<String>(), a5.tags)

        // Test failure: neither verbosity nor reporters in test domain
        val a6 = CountSetConstraintsArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("test", "--parallel", "file"))
        }

        // Test failure: high verbosity but tags missing in test domain
        val a7 = CountSetConstraintsArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("test", "-vvv", "file"))
        }
    }

    @Test
    fun domainCrossType_mixedComplexConstraints() {
        class MixedComplexConstraintsArgs : Arguments(
            appName = "mixed-complex-constraints-test",
            useANSITerminal = false
        ) {
            val deployDomain by domain("deploy")
                .exactlyOne(::replicas, ::manual)
                .atMostOne(::environments, ::servers)
                .requireIfValue(::backupDir, ::replicas) { it != null && it > 2 }
                .requireIfAllPresent(::monitoring, ::environments, ::healthCheck)

            val replicas by option("--replicas").int()
            val manual by option("--manual").bool()
            val environments by option("--env").set()
            val servers by option("--server").list()
            val backupDir by option("--backup-dir")
            val healthCheck by option("--health-check").bool()
            val monitoring by option("--monitoring")
            val file by positional()
        }

        // Test valid: replicas with backup in deploy domain
        val a1 = MixedComplexConstraintsArgs()
        a1.parseWithException(arrayOf("deploy", "--replicas", "5", "--backup-dir", "/backups", "file"))
        assertEquals(5, a1.replicas)
        assertNull(a1.manual)
        assertEquals("/backups", a1.backupDir)

        // Test valid: manual deployment in deploy domain
        val a2 = MixedComplexConstraintsArgs()
        a2.parseWithException(arrayOf("deploy", "--manual", "file"))
        assertNull(a2.replicas)
        assertEquals(true, a2.manual)

        // Test valid: replicas with environments and monitoring in deploy domain
        val a3 = MixedComplexConstraintsArgs()
        a3.parseWithException(arrayOf("deploy", "--replicas", "2", "--env", "staging", "--env", "prod", "--health-check", "--monitoring", "prometheus", "file"))
        assertEquals(2, a3.replicas)
        assertEquals(setOf("staging", "prod"), a3.environments)
        assertEquals(true, a3.healthCheck)
        assertEquals("prometheus", a3.monitoring)

        // Test valid: manual with servers in deploy domain
        val a4 = MixedComplexConstraintsArgs()
        a4.parseWithException(arrayOf("deploy", "--manual", "--server", "web1", "--server", "web2", "file"))
        assertEquals(true, a4.manual)
        assertEquals(listOf("web1", "web2"), a4.servers)

        // Test valid: low replicas, backup not required in deploy domain
        val a5 = MixedComplexConstraintsArgs()
        a5.parseWithException(arrayOf("deploy", "--replicas", "1", "file"))
        assertEquals(1, a5.replicas)
        assertNull(a5.backupDir)

        // Test failure: neither replicas nor manual in deploy domain
        val a6 = MixedComplexConstraintsArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("deploy", "--env", "staging", "file"))
        }

        // Test failure: both replicas and manual in deploy domain
        val a7 = MixedComplexConstraintsArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("deploy", "--replicas", "3", "--manual", "file"))
        }

        // Test failure: both environments and servers in deploy domain
        val a8 = MixedComplexConstraintsArgs()
        assertFailsWith<ParseError> {
            a8.parseWithException(arrayOf("deploy", "--replicas", "3", "--env", "prod", "--server", "web1", "file"))
        }

        // Test failure: high replicas but backup missing in deploy domain
        val a9 = MixedComplexConstraintsArgs()
        assertFailsWith<ParseError> {
            a9.parseWithException(arrayOf("deploy", "--replicas", "10", "file"))
        }

        // Test failure: environments and health-check but monitoring missing in deploy domain
        val a10 = MixedComplexConstraintsArgs()
        assertFailsWith<ParseError> {
            a10.parseWithException(arrayOf("deploy", "--replicas", "2", "--env", "prod", "--health-check", "file"))
        }
    }

    @Test
    fun domainCrossType_multipleDomains() {
        class MultiDomainCrossTypeArgs : Arguments(
            appName = "multi-domain-cross-type-test",
            useANSITerminal = false
        ) {
            val devDomain by domain("dev")
                .exactlyOne(::port, ::hotReload)
                .requireIfValue(::debugLevel, ::port) { it != null && it > 8000 }
            val testDomain by domain("test")
                .atMostOne(::coverage, ::profiling)
                .requireIfValue(::reporters, ::coverage) { it != null && it > 80.0f }
            val prodDomain by domain("prod")
                .atLeastOne(::replicas, ::loadBalancer)
                .conflicts(::debugMode, ::replicas)

            val port by option("--port").int()
            val hotReload by option("--hot-reload").bool()
            val debugLevel by option("--debug-level").int()
            val coverage by option("--coverage").float()
            val profiling by option("--profiling").bool()
            val reporters by option("--reporter").set()
            val replicas by option("--replicas").int()
            val loadBalancer by option("--load-balancer").bool()
            val debugMode by option("--debug-mode").bool()
            val file by positional()
        }

        // Test dev domain constraints
        val a1 = MultiDomainCrossTypeArgs()
        a1.parseWithException(arrayOf("dev", "--port", "9000", "--debug-level", "2", "file"))
        assertEquals(9000, a1.port)
        assertNull(a1.hotReload)
        assertEquals(2, a1.debugLevel)

        val a2 = MultiDomainCrossTypeArgs()
        a2.parseWithException(arrayOf("dev", "--hot-reload", "file"))
        assertNull(a2.port)
        assertEquals(true, a2.hotReload)

        // Test test domain constraints
        val a3 = MultiDomainCrossTypeArgs()
        a3.parseWithException(arrayOf("test", "--coverage", "85.5", "--reporter", "junit", "--reporter", "html", "file"))
        assertEquals(85.5f, a3.coverage)
        assertNull(a3.profiling)
        assertEquals(setOf("junit", "html"), a3.reporters)

        val a4 = MultiDomainCrossTypeArgs()
        a4.parseWithException(arrayOf("test", "--profiling", "file"))
        assertNull(a4.coverage)
        assertEquals(true, a4.profiling)

        // Test prod domain constraints
        val a5 = MultiDomainCrossTypeArgs()
        a5.parseWithException(arrayOf("prod", "--replicas", "5", "file"))
        assertEquals(5, a5.replicas)
        assertNull(a5.loadBalancer)
        assertNull(a5.debugMode)

        val a6 = MultiDomainCrossTypeArgs()
        a6.parseWithException(arrayOf("prod", "--load-balancer", "file"))
        assertNull(a6.replicas)
        assertEquals(true, a6.loadBalancer)

        // Test failures across domains
        val a7 = MultiDomainCrossTypeArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("dev", "--port", "9001", "file")) // high port but debug-level missing
        }

        val a8 = MultiDomainCrossTypeArgs()
        assertFailsWith<ParseError> {
            a8.parseWithException(arrayOf("test", "--coverage", "90.0", "file")) // high coverage but reporters missing
        }

        val a9 = MultiDomainCrossTypeArgs()
        assertFailsWith<ParseError> {
            a9.parseWithException(arrayOf("prod", "--replicas", "3", "--debug-mode", "file")) // conflicts
        }
    }
}
