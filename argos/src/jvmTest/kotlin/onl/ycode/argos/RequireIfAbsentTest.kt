/*
 * SPDX-License-Identifier: Apache-2.0
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

/**
 * Comprehensive tests for requireIfAnyAbsent and requireIfAllAbsent constraint methods.
 * Tests both global and domain-scoped absent constraints.
 */
class RequireIfAbsentTest {

    // Basic requireIfAllAbsent tests

    @Test
    fun requireIfAllAbsent_passesWhenAllRefsAbsentAndTargetProvided() {
        class TestArgs : Arguments() {
            val backup by option("--backup").requireIfAllAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--backup", "backup.dat"))
        assertEquals("backup.dat", args.backup)
        assertNull(args.primary)
        assertNull(args.secondary)
    }

    @Test
    fun requireIfAllAbsent_passesWhenNotAllRefsAbsent() {
        class TestArgs : Arguments() {
            val backup by option("--backup").requireIfAllAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        // Should pass when only one ref is present (not ALL absent)
        val args = TestArgs()
        args.parseWithException(arrayOf("--primary", "primary.dat"))
        assertNull(args.backup)
        assertEquals("primary.dat", args.primary)
        assertNull(args.secondary)
    }

    @Test
    fun requireIfAllAbsent_failsWhenAllRefsAbsentButTargetMissing() {
        class TestArgs : Arguments() {
            val backup by option("--backup").requireIfAllAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        val exception = assertFailsWith<ParseError> {
            TestArgs().parseWithException(arrayOf()) // all absent, backup required but missing
        }
        assert(exception.message!!.contains("--backup") && exception.message!!.contains("absent"))
    }

    // Basic requireIfAnyAbsent tests

    @Test
    fun requireIfAnyAbsent_passesWhenAnyRefAbsentAndTargetProvided() {
        class TestArgs : Arguments() {
            val fallback by option("--fallback").requireIfAnyAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        // Should pass when one ref is absent and target is provided
        val args = TestArgs()
        args.parseWithException(arrayOf("--primary", "primary.dat", "--fallback", "fallback.dat"))
        assertEquals("fallback.dat", args.fallback)
        assertEquals("primary.dat", args.primary)
        assertNull(args.secondary) // secondary is absent, triggering the requirement
    }

    @Test
    fun requireIfAnyAbsent_passesWhenAllRefsPresent() {
        class TestArgs : Arguments() {
            val fallback by option("--fallback").requireIfAnyAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        // Should pass when both refs are present (none absent)
        val args = TestArgs()
        args.parseWithException(arrayOf("--primary", "primary.dat", "--secondary", "secondary.dat"))
        assertNull(args.fallback)
        assertEquals("primary.dat", args.primary)
        assertEquals("secondary.dat", args.secondary)
    }

    @Test
    fun requireIfAnyAbsent_failsWhenAnyRefAbsentButTargetMissing() {
        class TestArgs : Arguments() {
            val fallback by option("--fallback").requireIfAnyAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        val exception = assertFailsWith<ParseError> {
            TestArgs().parseWithException(arrayOf("--primary", "primary.dat")) // secondary absent, fallback required but missing
        }
        assert(exception.message!!.contains("--fallback") && exception.message!!.contains("absent"))
    }

    // Non-nullable builder tests

    @Test
    fun nonNullableRequireIfAllAbsent_works() {
        class TestArgs : Arguments() {
            val backup by option("--backup").default("default.bak").requireIfAllAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        // When all refs are absent, constraint triggers and requires backup to be explicitly provided
        val args = TestArgs()
        args.parseWithException(arrayOf("--backup", "explicit.bak")) // explicitly provide backup
        assertEquals("explicit.bak", args.backup)

        // When one ref is present, constraint doesn't trigger, default can be used
        val args2 = TestArgs()
        args2.parseWithException(arrayOf("--primary", "primary.dat")) // primary present, constraint not triggered
        assertEquals("default.bak", args2.backup) // uses default
    }

    @Test
    fun nonNullableRequireIfAnyAbsent_works() {
        class TestArgs : Arguments() {
            val fallback by option("--fallback").required().requireIfAnyAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--primary", "primary.dat", "--fallback", "fallback.dat"))
        assertEquals("fallback.dat", args.fallback)
    }

    // Collection builder tests

    @Test
    fun listRequireIfAllAbsent_works() {
        class TestArgs : Arguments() {
            val backups by option("--backup").list().requireIfAllAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--backup", "backup1.dat", "--backup", "backup2.dat"))
        assertEquals(listOf("backup1.dat", "backup2.dat"), args.backups)
    }

    @Test
    fun setRequireIfAnyAbsent_works() {
        class TestArgs : Arguments() {
            val fallbacks by option("--fallback").set().requireIfAnyAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("--primary", "primary.dat", "--fallback", "fallback1.dat", "--fallback", "fallback2.dat"))
        assertEquals(setOf("fallback1.dat", "fallback2.dat"), args.fallbacks)
    }

    // Count builder tests

    @Test
    fun countRequireIfAllAbsent_works() {
        class TestArgs : Arguments() {
            val verbosity by option("-v", "--verbose").bool().list().requireIfAllAbsent(::quiet, ::silent)
            val quiet by option("--quiet").bool()
            val silent by option("--silent").bool()
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("-v", "-v", "-v"))
        assertEquals(3, args.verbosity.size)
    }

    // Domain-scoped constraint tests

    @Test
    fun domainRequireIfAllAbsent_worksWhenDomainActive() {
        class TestArgs : Arguments() {
            val buildDomain by domain("build")
                .requireIfAllAbsent(::target, ::source, ::output)

            val source by option("--source")
            val output by option("--output")
            val target by option("--target")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("build", "--target", "release"))
        assertEquals("release", args.target)
    }

    @Test
    fun domainRequireIfAllAbsent_failsWhenDomainActiveAndConditionMet() {
        class TestArgs : Arguments() {
            val buildDomain by domain("build")
                .requireIfAllAbsent(::target, ::source, ::output)

            val source by option("--source")
            val output by option("--output")
            val target by option("--target")
        }

        val exception = assertFailsWith<ParseError> {
            TestArgs().parseWithException(arrayOf("build")) // all absent, target required but missing
        }
        assert(exception.message!!.contains("--target"))
    }

    @Test
    fun domainRequireIfAnyAbsent_worksWhenDomainActive() {
        class TestArgs : Arguments() {
            val serverDomain by domain("server")
                .requireIfAnyAbsent(::backup, ::primary, ::secondary)

            val primary by option("--primary")
            val secondary by option("--secondary")
            val backup by option("--backup")
        }

        val args = TestArgs()
        args.parseWithException(arrayOf("server", "--primary", "primary.dat", "--backup", "backup.dat"))
        assertEquals("primary.dat", args.primary)
        assertEquals("backup.dat", args.backup)
        assertNull(args.secondary) // secondary absent, backup required and provided
    }

    // Error message tests

    @Test
    fun requireIfAllAbsent_errorMessageContainsCorrectInfo() {
        class TestArgs : Arguments() {
            val config by option("--config").requireIfAllAbsent(::host, ::port)
            val host by option("--host")
            val port by option("--port")
        }

        val exception = assertFailsWith<ParseError> {
            TestArgs().parseWithException(arrayOf()) // host and port absent, config required
        }

        val message = exception.message!!
        assert(message.contains("--config"))
        assert(message.contains("absent"))
    }

    @Test
    fun requireIfAnyAbsent_errorMessageContainsCorrectInfo() {
        class TestArgs : Arguments() {
            val fallback by option("--fallback").requireIfAnyAbsent(::primary, ::secondary)
            val primary by option("--primary")
            val secondary by option("--secondary")
        }

        val exception = assertFailsWith<ParseError> {
            TestArgs().parseWithException(arrayOf("--primary", "primary.dat")) // secondary absent, fallback required
        }

        val message = exception.message!!
        assert(message.contains("--fallback"))
        assert(message.contains("absent"))
    }

    // Complex scenarios

    @Test
    fun requireIfAllAbsent_withMultipleConstraints() {
        class TestArgs : Arguments() {
            val config by option("--config")
                .requireIfAllAbsent(::host, ::port)

            val host by option("--host")
            val port by option("--port")
        }

        // Should pass when host is provided (not all absent)
        val args = TestArgs()
        args.parseWithException(arrayOf("--host", "localhost"))
        assertNull(args.config)
        assertEquals("localhost", args.host)
        assertNull(args.port)
    }

    @Test
    fun requireIfAbsent_combinedWithOtherConstraints() {
        class TestArgs : Arguments() {
            val backup by option("--backup")
                .requireIfAllAbsent(::primary, ::secondary)
                .conflictsWith(::noBackup)

            val primary by option("--primary")
            val secondary by option("--secondary")
            val noBackup by option("--no-backup").bool()
        }

        // Should pass when primary provided (absent condition not met)
        val args = TestArgs()
        args.parseWithException(arrayOf("--primary", "primary.dat"))
        assertEquals("primary.dat", args.primary)
        assertNull(args.backup)
        assertNull(args.noBackup)

        // Should fail due to conflict when backup and noBackup both provided
        assertFailsWith<ParseError> {
            TestArgs().parseWithException(arrayOf("--backup", "backup.dat", "--no-backup"))
        }
    }
}