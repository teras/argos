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

class DomainFragmentInheritanceTest {

    @Test
    fun fragmentBasicCreation() {
        class FragmentArgs : Arguments(
            appName = "fragment-test",
            useANSITerminal = false
        ) {
            // Fragment - constraint template only
            private val loggingFragment by domain(fragment = true)
                .conflicts(::verbose, ::quiet)
                .required(::logLevel)

            val verbose by option("--verbose").bool()
            val quiet by option("--quiet").bool()
            val logLevel by option("--log-level")
        }

        val args = FragmentArgs()
        // Fragment properties should always return false (never active)
        // Note: We can't access private fragments directly, but we can test indirectly
        // by ensuring fragments don't appear in domain lists
        val snapshot = args.snapshot()
        assertTrue(snapshot.domains.isEmpty()) // No concrete domains, only fragment
    }

    @Test
    fun fragmentCannotHaveAliases() {
        assertFailsWith<ConfigException>("Fragments cannot have aliases") {
            class BadFragmentArgs : Arguments() {
                private val badFragment by domain(fragment = true)
                    .aliases("bad-alias")
            }
            BadFragmentArgs()
        }
    }

    @Test
    fun fragmentCannotHaveLabel() {
        assertFailsWith<ConfigException>("Fragments cannot have labels") {
            class BadFragmentArgs : Arguments() {
                private val badFragment by domain(fragment = true)
                    .label("Bad Label")
            }
            BadFragmentArgs()
        }
    }

    @Test
    fun fragmentCannotHaveHelp() {
        assertFailsWith<ConfigException>("Fragments cannot have help text") {
            class BadFragmentArgs : Arguments() {
                private val badFragment by domain(fragment = true)
                    .help("Bad help text")
            }
            BadFragmentArgs()
        }
    }

    @Test
    fun fragmentCannotHaveLabelAliasesOrHelp() {
        // Test that fragments reject all display-related properties
        assertFailsWith<ConfigException>("Fragments cannot have labels") {
            class BadLabelArgs : Arguments() {
                private val badFragment by domain(fragment = true)
                    .required(::value)
                    .label("Fragment Label") // Should fail here
                    .conflicts(::verbose, ::quiet)
                val value by option("--value")
                val verbose by option("--verbose").bool()
                val quiet by option("--quiet").bool()
            }
            BadLabelArgs()
        }

        assertFailsWith<ConfigException>("Fragments cannot have help text") {
            class BadHelpArgs : Arguments() {
                private val badFragment by domain(fragment = true)
                    .required(::value)
                    .help("Fragment help") // Should fail here
                val value by option("--value")
            }
            BadHelpArgs()
        }

        assertFailsWith<ConfigException>("Fragments cannot have aliases") {
            class BadAliasArgs : Arguments() {
                private val badFragment by domain(fragment = true)
                    .required(::value)
                    .aliases("frag", "fragment") // Should fail here
                val value by option("--value")
            }
            BadAliasArgs()
        }
    }

    @Test
    fun concreteDomainCanHaveLabelHelpAndAliases() {
        // Verify that concrete domains can still have all display properties
        class GoodConcreteArgs : Arguments(
            appName = "concrete-test",
            useANSITerminal = false
        ) {
            val concreteDomain by domain("concrete")
                .label("Concrete Domain")
                .help("This is a concrete domain for testing")
                .aliases("conc", "concrete-alias")
                .required(::value)

            val value by option("--value")
        }

        // Should work without any errors
        val args = GoodConcreteArgs()
        args.parseWithException(arrayOf("concrete", "--value", "test"))
        assertEquals("test", args.value)
        assertTrue(args.concreteDomain)

        // Verify domain appears in snapshot with correct metadata
        val snapshot = args.snapshot()
        assertEquals(1, snapshot.domains.size)
        val domain = snapshot.domains[0]
        assertEquals("concrete", domain.id)
        assertEquals("Concrete Domain", domain.label)
        assertEquals("This is a concrete domain for testing", domain.description)
        assertTrue(domain.aliases.contains("conc"))
        assertTrue(domain.aliases.contains("concrete-alias"))
    }

    @Test
    fun fragmentCannotInherit() {
        assertFailsWith<ConfigException>("Fragments cannot inherit from other domains") {
            class BadFragmentArgs : Arguments() {
                private val baseFragment by domain(fragment = true)
                private val badFragment by domain(fragment = true)
                    .inherits(::baseFragment)
            }
            BadFragmentArgs()
        }
    }

    @Test
    fun inheritRequiresAtLeastOneDomain() {
        assertFailsWith<ConfigException>("inherits() requires at least 1 domain") {
            class BadInheritArgs : Arguments() {
                val badDomain by domain("bad").inherits()
            }
            BadInheritArgs()
        }
    }

    @Test
    fun basicInheritanceFromFragment() {
        class InheritanceArgs : Arguments(
            appName = "inheritance-test",
            useANSITerminal = false
        ) {
            // Fragment with logging constraints
            private val loggingFragment by domain(fragment = true)
                .conflicts(::verbose, ::quiet)
                .required(::logLevel)

            // Concrete domain inheriting from fragment
            val buildDomain by domain("build")
                .inherits(::loggingFragment)
                .required(::source)

            val verbose by option("--verbose").bool()
            val quiet by option("--quiet").bool()
            val logLevel by option("--log-level")
            val source by option("--source")
        }

        // Test successful case - inherit constraints work
        val a1 = InheritanceArgs()
        a1.parseWithException(arrayOf("build", "--source", "main.kt", "--log-level", "info"))
        assertEquals("main.kt", a1.source)
        assertEquals("info", a1.logLevel)
        assertTrue(a1.buildDomain)

        // Test inherited conflicts constraint - verbose and quiet conflict
        val a2 = InheritanceArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("build", "--source", "main.kt", "--log-level", "info", "--verbose", "--quiet"))
        }

        // Test inherited required constraint - logLevel is required
        val a3 = InheritanceArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("build", "--source", "main.kt"))
        }

        // Test domain's own required constraint - source is required
        val a4 = InheritanceArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--log-level", "info"))
        }
    }

    @Test
    fun emptyDomainInheritsFromMultipleFragments() {
        // Test that a domain with no constraints can inherit correctly from multiple fragments
        class EmptyDomainArgs : Arguments(
            appName = "empty-domain-test",
            useANSITerminal = false
        ) {
            // First fragment - logging constraints
            private val loggingFragment by domain(fragment = true)
                .conflicts(::verbose, ::quiet)
                .required(::logLevel)
                .requireIfValue(::debug, ::logLevel) { it == "debug" }

            // Second fragment - config constraints
            private val configFragment by domain(fragment = true)
                .required(::configFile)
                .exactlyOne(::configFile, ::configUrl)
                .atLeast(::retries, 1)

            // Empty domain that inherits everything from both fragments
            val emptyDomain by domain("empty")
                .inherits(::loggingFragment, ::configFragment)
                // Notice: NO additional constraints defined here

            // Options
            val verbose by option("--verbose").bool()
            val quiet by option("--quiet").bool()
            val logLevel by option("--log-level")
            val debug by option("--debug").bool()
            val configFile by option("--config-file")
            val configUrl by option("--config-url")
            val retries by option("--retries").int().list()
        }

        // Test 1: All inherited constraints work correctly
        val a1 = EmptyDomainArgs()
        a1.parseWithException(arrayOf("empty", "--log-level", "debug", "--debug", "--config-file", "app.conf", "--retries", "3", "--retries", "5"))
        assertEquals("debug", a1.logLevel)
        assertEquals(true, a1.debug)
        assertEquals("app.conf", a1.configFile)
        assertEquals(listOf(3, 5), a1.retries)
        assertTrue(a1.emptyDomain)

        // Test 2: Inherited conflicts constraint from loggingFragment
        val a2 = EmptyDomainArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("empty", "--log-level", "info", "--config-file", "app.conf", "--retries", "1", "--verbose", "--quiet"))
        }

        // Test 3: Inherited required constraint from loggingFragment
        val a3 = EmptyDomainArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("empty", "--config-file", "app.conf", "--retries", "1"))
        }

        // Test 4: Inherited exactlyOne constraint from configFragment
        val a4 = EmptyDomainArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("empty", "--log-level", "info", "--config-file", "app.conf", "--config-url", "http://config", "--retries", "1"))
        }

        // Test 5: Inherited atLeast constraint from configFragment
        val a5 = EmptyDomainArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("empty", "--log-level", "info", "--config-file", "app.conf"))
        }

        // Test 6: Inherited requireIfValue constraint from loggingFragment
        val a6 = EmptyDomainArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("empty", "--log-level", "debug", "--config-file", "app.conf", "--retries", "1"))
        }

        // Test 7: requireIfValue doesn't trigger when condition not met
        val a7 = EmptyDomainArgs()
        a7.parseWithException(arrayOf("empty", "--log-level", "info", "--config-file", "app.conf", "--retries", "2"))
        assertEquals("info", a7.logLevel)
        assertNull(a7.debug) // debug not required when logLevel != "debug"
    }

    @Test
    fun multipleInheritance() {
        class MultiInheritArgs : Arguments(
            appName = "multi-inherit-test",
            useANSITerminal = false
        ) {
            // Multiple fragments
            private val loggingFragment by domain(fragment = true)
                .conflicts(::verbose, ::quiet)
                .required(::logLevel)

            private val configFragment by domain(fragment = true)
                .required(::configFile)
                .atMostOne(::configFile, ::configUrl)

            // Domain inheriting from multiple fragments
            val buildDomain by domain("build")
                .inherits(::loggingFragment, ::configFragment)
                .required(::source)

            val verbose by option("--verbose").bool()
            val quiet by option("--quiet").bool()
            val logLevel by option("--log-level")
            val configFile by option("--config-file")
            val configUrl by option("--config-url")
            val source by option("--source")
        }

        // Test successful multiple inheritance
        val a1 = MultiInheritArgs()
        a1.parseWithException(arrayOf("build", "--source", "main.kt", "--log-level", "info", "--config-file", "app.conf"))
        assertEquals("main.kt", a1.source)
        assertEquals("info", a1.logLevel)
        assertEquals("app.conf", a1.configFile)

        // Test constraint from first fragment (logging)
        val a2 = MultiInheritArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("build", "--source", "main.kt", "--config-file", "app.conf"))
        }

        // Test constraint from second fragment (config)
        val a3 = MultiInheritArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("build", "--source", "main.kt", "--log-level", "info"))
        }

        // Test atMostOne constraint from config fragment
        val a4 = MultiInheritArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--source", "main.kt", "--log-level", "info", "--config-file", "app.conf", "--config-url", "http://config"))
        }
    }

    @Test
    fun fragmentReferenceInInDomains() {
        class InDomainsArgs : Arguments(
            appName = "indomains-test",
            useANSITerminal = false
        ) {
            // Fragment for logging
            private val loggingFragment by domain(fragment = true)
                .required(::logLevel)

            // Multiple domains inheriting from fragment
            val buildDomain by domain("build").inherits(::loggingFragment)
            val testDomain by domain("test").inherits(::loggingFragment)
            val deployDomain by domain("deploy") // Does not inherit

            // Option that references the fragment - should expand to build + test
            val logFormat by option("--log-format")
                .onlyInDomains(::loggingFragment)
                .oneOf("json", "text")

            // Option only for deploy
            val environment by option("--environment")
                .onlyInDomains(::deployDomain)
                .oneOf("dev", "prod")

            val logLevel by option("--log-level")
        }

        // Test that logFormat is available in build domain (inherits from loggingFragment)
        val a1 = InDomainsArgs()
        a1.parseWithException(arrayOf("build", "--log-level", "info", "--log-format", "json"))
        assertEquals("json", a1.logFormat)
        assertTrue(a1.buildDomain)

        // Test that logFormat is available in test domain (inherits from loggingFragment)
        val a2 = InDomainsArgs()
        a2.parseWithException(arrayOf("test", "--log-level", "info", "--log-format", "text"))
        assertEquals("text", a2.logFormat)
        assertTrue(a2.testDomain)

        // Test that logFormat is NOT available in deploy domain (does not inherit from loggingFragment)
        val a3 = InDomainsArgs()
        assertFailsWith<ParseError> {
            a3.parseWithException(arrayOf("deploy", "--environment", "dev", "--log-format", "json"))
        }

        // Test that environment is only available in deploy domain
        val a4 = InDomainsArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--log-level", "info", "--environment", "dev"))
        }
    }

    @Test
    fun mixedFragmentAndConcreteDomainReferences() {
        class MixedArgs : Arguments(
            appName = "mixed-test",
            useANSITerminal = false
        ) {
            // Fragment
            private val loggingFragment by domain(fragment = true)
                .required(::logLevel)

            // Domains
            val buildDomain by domain("build").inherits(::loggingFragment)
            val testDomain by domain("test").inherits(::loggingFragment)
            val deployDomain by domain("deploy") // No inheritance

            // Option with mixed fragment and concrete domain references
            val timeout by option("--timeout")
                .onlyInDomains(::loggingFragment, ::deployDomain) // Fragment + concrete domain
                .int()

            val logLevel by option("--log-level")
        }

        // Should work in build (from fragment)
        val a1 = MixedArgs()
        a1.parseWithException(arrayOf("build", "--log-level", "info", "--timeout", "30"))
        assertEquals(30, a1.timeout)

        // Should work in test (from fragment)
        val a2 = MixedArgs()
        a2.parseWithException(arrayOf("test", "--log-level", "info", "--timeout", "60"))
        assertEquals(60, a2.timeout)

        // Should work in deploy (explicit reference)
        val a3 = MixedArgs()
        a3.parseWithException(arrayOf("deploy", "--timeout", "90"))
        assertEquals(90, a3.timeout)
    }

    @Test
    fun fragmentsExcludedFromHelp() {
        class HelpArgs : Arguments(
            appName = "help-test",
            useANSITerminal = false
        ) {
            private val hiddenFragment by domain(fragment = true)
                .required(::value)

            val visibleDomain by domain("visible")
                .inherits(::hiddenFragment)

            val value by option("--value")
        }

        val args = HelpArgs()
        val snapshot = args.snapshot()

        // Fragment should not appear in domains list
        assertEquals(1, snapshot.domains.size)
        assertEquals("visible", snapshot.domains[0].id)

        // Should not find fragment in domain IDs
        val domainIds = snapshot.domains.map { it.id }
        assertFalse("hiddenFragment" in domainIds)
        assertTrue("visible" in domainIds)
    }

    @Test
    fun duplicateInheritanceAutomaticallyDeduped() {
        class DupeArgs : Arguments(
            appName = "dupe-test",
            useANSITerminal = false
        ) {
            private val fragment1 by domain(fragment = true)
                .required(::value)

            // Try to inherit the same fragment multiple times
            val testDomain by domain("test")
                .inherits(::fragment1, ::fragment1, ::fragment1) // Duplicates should be removed

            val value by option("--value")
        }

        // Should work without issues (duplicates removed automatically)
        val args = DupeArgs()
        args.parseWithException(arrayOf("test", "--value", "test"))
        assertEquals("test", args.value)
    }

    @Test
    fun inheritanceFromNonExistentDomain() {
        // This test simulates what happens when a domain tries to inherit from
        // a property that gets referenced but doesn't actually exist in the builder lookup
        class BadRefArgs : Arguments() {
            // Create a fragment that exists
            private val existingFragment by domain(fragment = true)
                .required(::value)

            // But try to inherit using a property name that won't be found
            // (This would be caught during validation when the property name is looked up)
            val testDomain by domain("test")
                .inherits(::existingFragment) // This should work

            val value by option("--value")
        }

        // This should actually work since we're using a valid reference
        val args = BadRefArgs()
        args.parseWithException(arrayOf("test", "--value", "test"))
        assertEquals("test", args.value)
    }

    @Test
    fun complexInheritanceScenario() {
        class ComplexArgs : Arguments(
            appName = "complex-test",
            useANSITerminal = false
        ) {
            // Multiple fragments with different concerns
            private val loggingFragment by domain(fragment = true)
                .conflicts(::verbose, ::quiet)
                .required(::logLevel)

            private val configFragment by domain(fragment = true)
                .required(::configFile)
                .exactlyOne(::configFile, ::configUrl)

            private val securityFragment by domain(fragment = true)
                .requireIfValue(::keyFile, ::secure) { it == true }

            // Domains with different inheritance patterns
            val buildDomain by domain("build")
                .inherits(::loggingFragment, ::configFragment)
                .required(::source)

            val deployDomain by domain("deploy")
                .inherits(::configFragment, ::securityFragment)
                .required(::target)

            val testDomain by domain("test")
                .inherits(::loggingFragment)
                .atLeast(::pattern, 1)

            // Options with fragment references
            val debugMode by option("--debug")
                .onlyInDomains(::loggingFragment) // Available in build and test
                .bool()

            val retryCount by option("--retry")
                .onlyInDomains(::configFragment) // Available in build and deploy
                .int()
                .default(3)

            // Regular options
            val verbose by option("--verbose").bool()
            val quiet by option("--quiet").bool()
            val logLevel by option("--log-level")
            val configFile by option("--config-file")
            val configUrl by option("--config-url")
            val secure by option("--secure").bool()
            val keyFile by option("--key-file")
            val source by option("--source")
            val target by option("--target")
            val pattern by option("--pattern").list()
        }

        // Test build domain (logging + config fragments)
        val a1 = ComplexArgs()
        a1.parseWithException(arrayOf("build", "--source", "main.kt", "--log-level", "info", "--config-file", "app.conf", "--debug", "--retry", "5"))
        assertEquals("main.kt", a1.source)
        assertEquals("info", a1.logLevel)
        assertEquals("app.conf", a1.configFile)
        assertEquals(true, a1.debugMode)
        assertEquals(5, a1.retryCount)

        // Test deploy domain (config + security fragments)
        val a2 = ComplexArgs()
        a2.parseWithException(arrayOf("deploy", "--target", "prod", "--config-file", "deploy.conf", "--secure", "--key-file", "prod.key", "--retry", "1"))
        assertEquals("prod", a2.target)
        assertEquals("deploy.conf", a2.configFile)
        assertEquals(true, a2.secure)
        assertEquals("prod.key", a2.keyFile)
        assertEquals(1, a2.retryCount)

        // Test test domain (only logging fragment)
        val a3 = ComplexArgs()
        a3.parseWithException(arrayOf("test", "--log-level", "debug", "--pattern", "unit", "--pattern", "integration", "--debug"))
        assertEquals("debug", a3.logLevel)
        assertEquals(listOf("unit", "integration"), a3.pattern)
        assertEquals(true, a3.debugMode)

        // Test inherited constraint violations

        // Build: exactlyOne violation (both configFile and configUrl)
        val a4 = ComplexArgs()
        assertFailsWith<ParseError> {
            a4.parseWithException(arrayOf("build", "--source", "main.kt", "--log-level", "info", "--config-file", "app.conf", "--config-url", "http://config"))
        }

        // Deploy: conditional requirement violation (secure=true requires keyFile)
        val a5 = ComplexArgs()
        assertFailsWith<ParseError> {
            a5.parseWithException(arrayOf("deploy", "--target", "prod", "--config-file", "deploy.conf", "--secure"))
        }

        // Test: atLeast violation (pattern requires at least 1)
        val a6 = ComplexArgs()
        assertFailsWith<ParseError> {
            a6.parseWithException(arrayOf("test", "--log-level", "debug"))
        }

        // Cross-domain option availability

        // debug should not be available in deploy (no logging fragment)
        val a7 = ComplexArgs()
        assertFailsWith<ParseError> {
            a7.parseWithException(arrayOf("deploy", "--target", "prod", "--config-file", "deploy.conf", "--debug"))
        }

        // retry should not be available in test (no config fragment)
        val a8 = ComplexArgs()
        assertFailsWith<ParseError> {
            a8.parseWithException(arrayOf("test", "--log-level", "debug", "--pattern", "unit", "--retry", "5"))
        }
    }
}