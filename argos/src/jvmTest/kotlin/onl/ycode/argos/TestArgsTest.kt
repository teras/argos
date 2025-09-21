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
import kotlin.test.assertNotNull

class TestArgsTest {

    // ---------- Parsing basics ----------
    @Test
    fun parsesLongShortAndPositionals() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "--name", "Alice", "-t", "3", "input.txt", "1", "2"))
        assertEquals("alpha", a.selectedDomain())
        assertEquals("Alice", a.name)
        assertEquals(3, a.tries)
        assertEquals("input.txt", a.file)
        assertEquals(listOf(1, 2), a.extras)
        assertEquals(0, a.verbose.size)
    }

    @Test
    fun supportsClustersAndAttachedValues() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "-v", "-t3", "file", "7"))
        assertEquals(1, a.verbose.size)
        assertEquals(3, a.tries)
        assertEquals("file", a.file)
        assertEquals(listOf(7), a.extras)
    }

    @Test
    fun invalidCluster_whenOptionRequiringValueNotLast() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> { a.parseWithException(arrayOf("alpha", "--g1", "-vt3", "file")) }
        assertTrue(e.message!!.contains("requires a value and must be last"))
    }

    @Test
    fun missingValueForShortOption_isError() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--g1", "-t", "--name", "A", "file"))
        }
        assertTrue(e.message!!.contains("Missing value for -t"))
    }

    @Test
    fun duplicateNonRepeatableOption_isError() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--g1", "--name", "A", "--name", "B", "file"))
        }
        assertTrue(e.message!!.contains("provided multiple times"))
    }

    @Test
    fun supportsEqualsSeparatorAndDoubleDash() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "--tries=4", "--", "-3", "5"))
        assertEquals(4, a.tries)
        assertEquals("-3", a.file)
        assertEquals(listOf(5), a.extras)
    }

    @Test
    fun negativeNumberAsPositionalWhenNoShortOption() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "-3", "6"))
        assertEquals("-3", a.file)
        assertEquals(listOf(6), a.extras)
    }

    @Test
    fun aliasSelectsDomain() {
        val a = TestArgs()
        a.parseWithException(arrayOf("b", "--g1", "--mode", "slow", "--tag", "a", "--tag", "b", "--x", "f"))
        assertEquals("beta", a.selectedDomain())
        assertEquals("f", a.file)
    }

    @Test
    fun unknownOptionShowsSuggestion() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--g1", "--mdoe", "fast", "f"))
        }
        assertTrue(e.message!!.contains("Unknown option"))
        assertTrue(e.message!!.contains("Did you mean"))
        assertTrue(e.message!!.contains("--mode"))
    }

    // ---------- Eager boolean ----------
    @Test
    fun eagerTrueShortCircuitsBeforeValidations() {
        val a = TestArgs()
        a.parseWithException(arrayOf("--help"))
        assertNull(a.selectedDomain())
        assertEquals(0, a.verbose.size)
    }

    @Test
    fun eagerFalseDoesNotShortCircuit() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> { a.parseWithException(arrayOf("--help", "false")) }
        assertTrue(e.message!!.contains("A domain is required"))
    }

    // ---------- Global constraints ----------
    @Test
    fun globalAtMostOne_failsWhenBothProvided() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> { a.parseWithException(arrayOf("alpha", "--g1", "--m1", "--m2", "f")) }
        assertTrue(e.message!!.contains("At most one of"))
    }

    @Test
    fun globalAtMostOne_passesWithEitherOne() {
        val a1 = TestArgs()
 a1.parseWithException(arrayOf("alpha", "--g1", "--m1", "f"))
        val a2 = TestArgs()
 a2.parseWithException(arrayOf("alpha", "--g1", "--m2", "f"))
        assertEquals("alpha", a1.selectedDomain())
 assertEquals("alpha", a2.selectedDomain())
    }

    @Test
    fun globalAtLeastOne_failsWhenNoneProvided() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> { a.parseWithException(arrayOf("alpha", "f")) }
        assertTrue(e.message!!.contains("At least one of"))
    }

    @Test
    fun globalAtLeastOne_passesWithEither() {
        val a1 = TestArgs()
 a1.parseWithException(arrayOf("alpha", "--g1", "f"))
        val a2 = TestArgs()
 a2.parseWithException(arrayOf("alpha", "--g2", "f"))
        assertEquals("alpha", a1.selectedDomain())
 assertEquals("alpha", a2.selectedDomain())
    }

    @Test
    fun globalConflicts_failsWhenBothPresent() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> { a.parseWithException(arrayOf("alpha", "--g1", "--c1", "--c2", "f")) }
        assertTrue(e.message!!.contains("Conflicting options"))
    }

    @Test
    fun globalConflicts_passesWithEither() {
        val a1 = TestArgs()
 a1.parseWithException(arrayOf("alpha", "--g1", "--c1", "f"))
        val a2 = TestArgs()
 a2.parseWithException(arrayOf("alpha", "--g1", "--c2", "f"))
        assertEquals("alpha", a1.selectedDomain())
 assertEquals("alpha", a2.selectedDomain())
    }

    @Test
    fun globalExactlyOneWith_failsWhenNoneOrBothProvided() {
        val none = ExactlyOneTestArgs()
        val eNone = assertFailsWith<ParseError> {
            none.parseWithException(arrayOf("alpha", "f"))
        }
        assertTrue(eNone.message!!.contains("Exactly one"))

        val both = ExactlyOneTestArgs()
        val eBoth = assertFailsWith<ParseError> {
            both.parseWithException(arrayOf("alpha", "--e1", "--e2", "f"))
        }
        assertTrue(eBoth.message!!.contains("Exactly one"))
    }

    @Test
    fun globalExactlyOneWith_passesWithEitherOne() {
        val a1 = ExactlyOneTestArgs()
 a1.parseWithException(arrayOf("alpha", "--e1", "f"))
        val a2 = ExactlyOneTestArgs()
 a2.parseWithException(arrayOf("alpha", "--e2", "f"))
        assertEquals("alpha", a1.selectedDomain())
 assertEquals("alpha", a2.selectedDomain())
    }

    @Test
    fun conditionalAnyPresent_requiresTargetWhenTriggered() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> { a.parseWithException(arrayOf("alpha", "--g1", "--u", "f")) }
        assertTrue(e.message!!.contains("--any-need"))
    }

    @Test
    fun conditionalAnyPresent_notRequiredWhenNotTriggered() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "f"))
        assertEquals("alpha", a.selectedDomain())
    }

    @Test
    fun conditionalAnyPresent_passesWhenTargetProvided() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "--v", "--any-need", "f"))
        assertEquals("alpha", a.selectedDomain())
    }

    @Test
    fun conditionalAllPresent_requiresTargetOnlyWhenBothPresent() {
        val both = TestArgs()
        val e = assertFailsWith<ParseError> {
            both.parseWithException(arrayOf("alpha", "--g1", "--u", "--v", "--any-need", "f"))
        }
        assertTrue(e.message!!.contains("--all-need"))

        val ok = TestArgs()
        ok.parseWithException(arrayOf("alpha", "--g1", "--u", "--v", "--any-need", "--all-need", "f"))
        assertEquals("alpha", ok.selectedDomain())
    }

    @Test
    fun conditionalValuePredicate_failsWhenFastWithoutPredNeed() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--g1", "--mode", "fast", "f"))
        }
        assertTrue(e.message!!.contains("--pred-need"))
    }

    @Test
    fun conditionalValuePredicate_passesWhenFastWithPredNeed_orSlow() {
        val a1 = TestArgs()
 a1.parseWithException(arrayOf("alpha", "--g1", "--mode", "fast", "--pred-need", "f"))
        val a2 = TestArgs()
 a2.parseWithException(arrayOf("alpha", "--g1", "--mode", "slow", "f"))
        assertEquals("alpha", a1.selectedDomain())
 assertEquals("alpha", a2.selectedDomain())
    }

    // ---------- Domain-scoped constraints (beta) ----------
    @Test
    fun domainRequired_failsWhenMissing_inBeta() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> { a.parseWithException(arrayOf("beta", "--g1", "f")) }
        assertTrue(e.message!!.contains("--mode"))
    }

    @Test
    fun domainRequired_passesWhenProvided_inBeta() {
        val a = TestArgs()
        a.parseWithException(arrayOf("beta", "--g1", "--mode", "slow", "--tag","a","--tag","b","--x","f"))
        assertEquals("beta", a.selectedDomain())
    }

    @Test
    fun domainAtLeastTags_failsWithOneTag() {
        val a = TestArgs()
        val e1 = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("beta", "--g1", "--mode", "slow", "--tag", "a", "--x", "f"))
        }
        assertTrue(e1.message!!.contains("requires at least 2"))
    }

    @Test
    fun domainAtLeastTags_passesWithTwoTags() {
        val b = TestArgs()
        b.parseWithException(arrayOf("beta", "--g1", "--mode", "slow", "--tag", "a", "--tag", "b", "--x", "f"))
        assertEquals(listOf("a", "b"), b.tags)
    }

    @Test
    fun domainExactlyOne_failsWithNoneOrBoth() {
        val none = TestArgs()
        val eNone = assertFailsWith<ParseError> {
            none.parseWithException(arrayOf("beta", "--g1", "--mode", "slow", "--tag", "a", "--tag", "b", "f"))
        }
        assertTrue(eNone.message!!.contains("Exactly one"))
        val both = TestArgs()
        val eBoth = assertFailsWith<ParseError> {
            both.parseWithException(arrayOf("beta", "--g1", "--mode", "slow","--tag","a","--tag","b","--x","--y","f"))
        }
        assertTrue(eBoth.message!!.contains("Exactly one"))
    }

    @Test
    fun domainExactlyOne_passesWithEitherOne() {
        val oneX = TestArgs()
 oneX.parseWithException(arrayOf("beta", "--g1", "--mode", "slow","--tag","a","--tag","b","--x","f"))
        val oneY = TestArgs()
 oneY.parseWithException(arrayOf("beta", "--g1", "--mode", "slow","--tag","a","--tag","b","--y","f"))
        assertEquals("beta", oneX.selectedDomain())
 assertEquals("beta", oneY.selectedDomain())
    }

    @Test
    fun domainConflicts_failsWithBoth() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("beta","--g1","--mode","slow","--tag","a","--tag","b","--dc1","--dc2","f"))
        }
        assertTrue(e.message!!.contains("Conflicting options"))
    }

    @Test
    fun domainConflicts_passesWithEither() {
        val a1 = TestArgs()
 a1.parseWithException(arrayOf("beta","--g1","--mode","slow","--tag","a","--tag","b","--dc1","--x","f"))
        val a2 = TestArgs()
 a2.parseWithException(arrayOf("beta","--g1","--mode","slow","--tag","a","--tag","b","--dc2","--x","f"))
        assertEquals("beta", a1.selectedDomain())
 assertEquals("beta", a2.selectedDomain())
    }

    @Test
    fun domainConditionalAnyPresent_requiresDomainTargetOnlyInBeta() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("beta","--g1","--mode","slow","--tag","a","--tag","b","--x","--u","--any-need","f"))
        }
        assertTrue(e.message!!.contains("--d-need"))
    }

    @Test
    fun domainConditionalAnyPresent_notRequiredWhenNotTriggered() {
        val a = TestArgs()
        a.parseWithException(arrayOf("beta","--g1","--mode","slow","--tag","a","--tag","b","--x","f"))
        assertEquals("beta", a.selectedDomain())
    }

    @Test
    fun domainConditionalAnyPresent_passesWhenDomainTargetProvided() {
        val ok = TestArgs()
        ok.parseWithException(arrayOf("beta","--g1","--mode","slow","--tag","a","--tag","b","--x","--u","--any-need","--d-need","f"))
        assertEquals("beta", ok.selectedDomain())
    }

    // ---------- Domain-restricted options (onlyInDomains) ----------
    @Test
    fun optionRestrictedToDomain_alphaOnly_okInAlpha_errorInBeta() {
        val ok = TestArgs()
 ok.parseWithException(arrayOf("alpha", "--g1", "--alpha-only", "f"))
        val bad = TestArgs()
        val e = assertFailsWith<ParseError> {
            bad.parseWithException(arrayOf("beta","--g1","--mode","slow","--tag","a","--tag","b","--x","--alpha-only","f"))
        }
        assertTrue(e.message!!.contains("allowed only in"))
    }

    @Test
    fun optionRestrictedToDomain_betaOnly_okInBeta_errorInAlpha() {
        val ok = TestArgs()
 ok.parseWithException(arrayOf("beta","--g1","--mode","slow","--tag","a","--tag","b","--x","--beta-only","f"))
        val bad = TestArgs()
        val e = assertFailsWith<ParseError> {
            bad.parseWithException(arrayOf("alpha","--g1","--beta-only","f"))
        }
        assertTrue(e.message!!.contains("allowed only in"))
    }

    // ---------- ENV fromEnv() ----------
    @Test
    fun envPath_isReadWhenNotProvided() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "f"))
        // PATH should exist in test process environment
        assertNotNull(a.envPath)
        assertTrue(a.envPath!!.isNotEmpty())
    }

    @Test
    fun envMissing_isNullWhenNotProvided() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "f"))
        assertNull(a.envMissing) // expected missing
    }

    // ---------- Lists / Sets / Counts ----------
    @Test
    fun countAggregatesAcrossForms() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1", "-vv", "--verbose", "-v", "f"))
        // -vv = 2, --verbose = 1, -v = 1 => 4
        assertEquals(4, a.verbose.size)
    }

    @Test
    fun setCollectsUniqueAndPreservesInsertionOrder() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha", "--g1",
            "--color","red","--color","blue","--color","red","--color","green","f"))
        assertEquals(listOf("red","blue","green"), a.colors.toList())
    }

    @Test
    fun listPreservesOrderAndAllowsDuplicates() {
        val a = TestArgs()
        a.parseWithException(arrayOf("alpha","--g1","--tag","x","--tag","y","--tag","x","f"))
        assertEquals(listOf("x","y","x"), a.tags)
    }

    @Test
    fun positionalList_invalidElement_causesError() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--g1", "file", "ok", "42"))
        }
        assertTrue(e.message!!.contains("Invalid value 'ok'"))
        assertTrue(e.message!!.contains("<extras>"))
    }

    // ---------- Domain selection & missing domain ----------
    @Test
    fun missingDomainRequiredUnlessEager() {
        val a = TestArgs()
        val e = assertFailsWith<ParseError> { a.parseWithException(arrayOf("f")) }
        assertTrue(e.message!!.contains("A domain is required"))
    }

    // ---------- Required lists/sets/counts/domains ----------
    @Test
    fun requiredList_failsWhenMissing() {
        val a = RequiredTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "f"))
        }
        assertTrue(e.message!!.contains("--req-list"))
    }

    @Test
    fun requiredList_passesWhenProvided() {
        val a = RequiredTestArgs()
        a.parseWithException(arrayOf("alpha", "--req-list", "item1", "--req-list", "item2", "--req-set", "set1", "--req-count", "f"))
        assertEquals(listOf("item1", "item2"), a.requiredList)
    }

    @Test
    fun requiredSet_failsWhenMissing() {
        val a = RequiredTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--req-list", "item1", "f"))
        }
        assertTrue(e.message!!.contains("--req-set"))
    }

    @Test
    fun requiredSet_passesWhenProvided() {
        val a = RequiredTestArgs()
        a.parseWithException(arrayOf("alpha", "--req-list", "item1", "--req-set", "set1", "--req-set", "set2", "--req-count", "f"))
        assertEquals(setOf("set1", "set2"), a.requiredSet)
    }

    @Test
    fun requiredCount_failsWhenMissing() {
        val a = RequiredTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--req-list", "item1", "--req-set", "set1", "f"))
        }
        assertTrue(e.message!!.contains("--req-count"))
    }

    @Test
    fun requiredCount_passesWhenProvided() {
        val a = RequiredTestArgs()
        a.parseWithException(arrayOf("alpha", "--req-list", "item1", "--req-set", "set1", "--req-count", "--req-count", "f"))
        assertEquals(2, a.requiredCount.size)
    }

    @Test
    fun requiredDomain_failsWhenMissing() {
        val a = RequiredTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("gamma", "--req-list", "item1", "--req-set", "set1", "--req-count", "f"))
        }
        assertTrue(e.message!!.contains("--mode"))
    }

    @Test
    fun requiredDomain_passesWhenProvided() {
        val a = RequiredTestArgs()
        a.parseWithException(arrayOf("gamma", "--mode", "fast", "--req-list", "item1", "--req-set", "set1", "--req-count", "f"))
        assertEquals("gamma", a.selectedDomain())
        assertEquals("fast", a.mode)
    }

    // ---------- Additional constraint combination tests ----------
    @Test
    fun requiredListCanBeEmpty_whenProvided() {
        val a = RequiredTestArgs()
        a.parseWithException(arrayOf("alpha", "--req-list", "", "--req-set", "set1", "--req-count", "f"))
        assertEquals(listOf(""), a.requiredList)
    }

    @Test
    fun requiredSetAllowsMultipleValues() {
        val a = RequiredTestArgs()
        a.parseWithException(arrayOf("alpha", "--req-list", "a", "--req-set", "x", "--req-set", "y", "--req-set", "x", "--req-count", "f"))
        assertEquals(setOf("x", "y"), a.requiredSet) // duplicate "x" should be eliminated
    }

    @Test
    fun countStillSatisfiesRequired_whenProvidedOnce() {
        val a = RequiredTestArgs()
        a.parseWithException(arrayOf("alpha", "--req-list", "a", "--req-set", "x", "--req-count=false", "f"))
        assertEquals(1, a.requiredCount.size) // bool count counts the occurrences, not the bool value
    }

    // ---------- Additional constraint tests ----------
    @Test
    fun atMostOneWith_failsWhenBothProvided() {
        val a = AtMostOneTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--m1", "--m2", "f"))
        }
        assertTrue(e.message!!.contains("At most one"))
    }

    @Test
    fun atMostOneWith_passesWithEitherOneOrNone() {
        val a1 = AtMostOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--m1", "f"))
        assertTrue(a1.m1)
        assertFalse(a1.m2)

        val a2 = AtMostOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--m2", "f"))
        assertFalse(a2.m1)
        assertTrue(a2.m2)

        val a3 = AtMostOneTestArgs()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertFalse(a3.m1)
        assertFalse(a3.m2)
    }

    @Test
    fun atLeastOneWith_failsWhenNoneProvided() {
        val a = AtLeastOneTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "f"))
        }
        assertTrue(e.message!!.contains("At least one"))
    }

    @Test
    fun atLeastOneWith_passesWithEitherOneOrBoth() {
        val a1 = AtLeastOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--l1", "f"))
        assertTrue(a1.l1)
        assertFalse(a1.l2)

        val a2 = AtLeastOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--l2", "f"))
        assertFalse(a2.l1)
        assertTrue(a2.l2)

        val a3 = AtLeastOneTestArgs()
        a3.parseWithException(arrayOf("alpha", "--l1", "--l2", "f"))
        assertTrue(a3.l1)
        assertTrue(a3.l2)
    }

    @Test
    fun conflictsWith_failsWhenBothProvided() {
        val a = ConflictsTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--c1", "--c2", "f"))
        }
        assertTrue(e.message!!.contains("Conflicting options"))
    }

    @Test
    fun conflictsWith_passesWithEitherOneOrNone() {
        val a1 = ConflictsTestArgs()
        a1.parseWithException(arrayOf("alpha", "--c1", "f"))
        assertTrue(a1.c1)
        assertFalse(a1.c2)

        val a2 = ConflictsTestArgs()
        a2.parseWithException(arrayOf("alpha", "--c2", "f"))
        assertFalse(a2.c1)
        assertTrue(a2.c2)

        val a3 = ConflictsTestArgs()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertFalse(a3.c1)
        assertFalse(a3.c2)
    }

    @Test
    fun atLeastN_failsWhenTooFewProvided() {
        val a = AtLeastTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--at-least-two", "only-one", "f"))
        }
        assertTrue(e.message!!.contains("requires at least 2"))
    }

    @Test
    fun atLeastN_passesWhenEnoughProvided() {
        val a = AtLeastTestArgs()
        a.parseWithException(arrayOf("alpha", "--at-least-two", "item1", "--at-least-two", "item2", "--at-least-two", "item3", "f"))
        assertEquals(listOf("item1", "item2", "item3"), a.atLeastTwo)
    }

    @Test
    fun requireIfAllPresent_failsWhenAllPresentButTargetMissing() {
        val a = RequireIfAllTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "f"))
        }
        assertTrue(e.message!!.contains("--target"))
    }

    @Test
    fun requireIfAllPresent_passesWhenNotAllPresent() {
        val a1 = RequireIfAllTestArgs()
        a1.parseWithException(arrayOf("alpha", "--trigger1", "f"))
        assertTrue(a1.trigger1)
        assertFalse(a1.trigger2)
        assertFalse(a1.target)

        val a2 = RequireIfAllTestArgs()
        a2.parseWithException(arrayOf("alpha", "f"))
        assertFalse(a2.trigger1)
        assertFalse(a2.trigger2)
        assertFalse(a2.target)
    }

    @Test
    fun requireIfAllPresent_passesWhenAllPresentAndTargetProvided() {
        val a = RequireIfAllTestArgs()
        a.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "--target", "f"))
        assertTrue(a.trigger1)
        assertTrue(a.trigger2)
        assertTrue(a.target)
    }

    @Test
    fun requireIfValue_failsWhenValueMatchesButTargetMissing() {
        val a = RequireIfValueTestArgs()
        val e = assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--mode", "fast", "f"))
        }
        assertTrue(e.message!!.contains("--target"))
    }

    @Test
    fun requireIfValue_passesWhenValueDoesNotMatch() {
        val a = RequireIfValueTestArgs()
        a.parseWithException(arrayOf("alpha", "--mode", "slow", "f"))
        assertEquals("slow", a.mode)
        assertFalse(a.target)
    }

    @Test
    fun requireIfValue_passesWhenValueMatchesAndTargetProvided() {
        val a = RequireIfValueTestArgs()
        a.parseWithException(arrayOf("alpha", "--mode", "fast", "--target", "f"))
        assertEquals("fast", a.mode)
        assertTrue(a.target)
    }

    // ============== NULLABLE FIELD CONSTRAINT TESTS ==============

    @Test
    fun nullableExactlyOneWith_passesWithEitherOne() {
        val a1 = NullableExactlyOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--e1", "f"))
        assertEquals(true, a1.e1)
        assertNull(a1.e2)

        val a2 = NullableExactlyOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--e2", "f"))
        assertNull(a2.e1)
        assertEquals(true, a2.e2)
    }

    @Test
    fun nullableExactlyOneWith_failsWhenNoneOrBothProvided() {
        val a1 = NullableExactlyOneTestArgs()
        assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "f"))
        }

        val a2 = NullableExactlyOneTestArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--e1", "--e2", "f"))
        }
    }

    @Test
    fun nullableAtMostOneWith_passesWithEitherOneOrNone() {
        val a1 = NullableAtMostOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--m1", "f"))
        assertEquals(true, a1.m1)
        assertNull(a1.m2)

        val a2 = NullableAtMostOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--m2", "f"))
        assertNull(a2.m1)
        assertEquals(true, a2.m2)

        val a3 = NullableAtMostOneTestArgs()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertNull(a3.m1)
        assertNull(a3.m2)
    }

    @Test
    fun nullableAtMostOneWith_failsWhenBothProvided() {
        val a = NullableAtMostOneTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--m1", "--m2", "f"))
        }
    }

    @Test
    fun nullableAtLeastOneWith_passesWithEitherOneOrBoth() {
        val a1 = NullableAtLeastOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--l1", "f"))
        assertEquals(true, a1.l1)
        assertNull(a1.l2)

        val a2 = NullableAtLeastOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--l2", "f"))
        assertNull(a2.l1)
        assertEquals(true, a2.l2)

        val a3 = NullableAtLeastOneTestArgs()
        a3.parseWithException(arrayOf("alpha", "--l1", "--l2", "f"))
        assertEquals(true, a3.l1)
        assertEquals(true, a3.l2)
    }

    @Test
    fun nullableAtLeastOneWith_failsWhenNoneProvided() {
        val a = NullableAtLeastOneTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "f"))
        }
    }

    @Test
    fun nullableConflictsWith_passesWithEitherOneOrNone() {
        val a1 = NullableConflictsTestArgs()
        a1.parseWithException(arrayOf("alpha", "--c1", "f"))
        assertEquals(true, a1.c1)
        assertNull(a1.c2)

        val a2 = NullableConflictsTestArgs()
        a2.parseWithException(arrayOf("alpha", "--c2", "f"))
        assertNull(a2.c1)
        assertEquals(true, a2.c2)

        val a3 = NullableConflictsTestArgs()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertNull(a3.c1)
        assertNull(a3.c2)
    }

    @Test
    fun nullableConflictsWith_failsWhenBothProvided() {
        val a = NullableConflictsTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--c1", "--c2", "f"))
        }
    }

    @Test
    fun nullableRequireIfAllPresent_passesWhenNotAllPresent() {
        val a1 = NullableRequireIfAllTestArgs()
        a1.parseWithException(arrayOf("alpha", "--trigger1", "f"))
        assertEquals(true, a1.trigger1)
        assertNull(a1.trigger2)
        assertNull(a1.target)

        val a2 = NullableRequireIfAllTestArgs()
        a2.parseWithException(arrayOf("alpha", "f"))
        assertNull(a2.trigger1)
        assertNull(a2.trigger2)
        assertNull(a2.target)
    }

    @Test
    fun nullableRequireIfAllPresent_passesWhenAllPresentAndTargetProvided() {
        val a = NullableRequireIfAllTestArgs()
        a.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "--target", "f"))
        assertEquals(true, a.trigger1)
        assertEquals(true, a.trigger2)
        assertEquals(true, a.target)
    }

    @Test
    fun nullableRequireIfAllPresent_failsWhenAllPresentButTargetMissing() {
        val a = NullableRequireIfAllTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--trigger1", "--trigger2", "f"))
        }
    }

    @Test
    fun nullableRequireIfValue_passesWhenValueDoesNotMatch() {
        val a = NullableRequireIfValueTestArgs()
        a.parseWithException(arrayOf("alpha", "--mode", "slow", "f"))
        assertEquals("slow", a.mode)
        assertNull(a.target)
    }

    @Test
    fun nullableRequireIfValue_passesWhenValueMatchesAndTargetProvided() {
        val a = NullableRequireIfValueTestArgs()
        a.parseWithException(arrayOf("alpha", "--mode", "fast", "--target", "f"))
        assertEquals("fast", a.mode)
        assertEquals(true, a.target)
    }

    @Test
    fun nullableRequireIfValue_failsWhenValueMatchesButTargetMissing() {
        val a = NullableRequireIfValueTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--mode", "fast", "f"))
        }
    }

    // ============== NON-NULLABLE FIELD CONSTRAINT TESTS ==============

    @Test
    fun nonNullableStringExactlyOneWith_passesWithEitherOne() {
        val a1 = NonNullableStringExactlyOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--e1", "value1", "f"))
        assertEquals("value1", a1.e1)
        assertEquals("default2", a1.e2)

        val a2 = NonNullableStringExactlyOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--e2", "value2", "f"))
        assertEquals("default1", a2.e1)
        assertEquals("value2", a2.e2)
    }

    @Test
    fun nonNullableStringExactlyOneWith_failsWhenNoneOrBothProvided() {
        val a1 = NonNullableStringExactlyOneTestArgs()
        assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "f"))
        }

        val a2 = NonNullableStringExactlyOneTestArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--e1", "v1", "--e2", "v2", "f"))
        }
    }

    @Test
    fun nonNullableIntAtMostOneWith_passesWithEitherOneOrNone() {
        val a1 = NonNullableIntAtMostOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--m1", "42", "f"))
        assertEquals(42, a1.m1)
        assertEquals(0, a1.m2)

        val a2 = NonNullableIntAtMostOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--m2", "99", "f"))
        assertEquals(0, a2.m1)
        assertEquals(99, a2.m2)

        val a3 = NonNullableIntAtMostOneTestArgs()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertEquals(0, a3.m1)
        assertEquals(0, a3.m2)
    }

    @Test
    fun nonNullableIntAtMostOneWith_failsWhenBothProvided() {
        val a = NonNullableIntAtMostOneTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--m1", "42", "--m2", "99", "f"))
        }
    }

    @Test
    fun nonNullableOneOfAtLeastOneWith_passesWithEitherOneOrBoth() {
        val a1 = NonNullableOneOfAtLeastOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--l1", "large", "f"))
        assertEquals("large", a1.l1)
        assertNull(a1.l2)

        val a2 = NonNullableOneOfAtLeastOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--l2", "blue", "f"))
        assertNull(a2.l1)
        assertEquals("blue", a2.l2)

        val a3 = NonNullableOneOfAtLeastOneTestArgs()
        a3.parseWithException(arrayOf("alpha", "--l1", "small", "--l2", "red", "f"))
        assertEquals("small", a3.l1)
        assertEquals("red", a3.l2)
    }

    @Test
    fun nonNullableOneOfAtLeastOneWith_failsWhenNoneProvided() {
        val a = NonNullableOneOfAtLeastOneTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "f"))
        }
    }

    @Test
    fun nonNullableStringConflictsWith_passesWithEitherOneOrNone() {
        val a1 = NonNullableStringConflictsTestArgs()
        a1.parseWithException(arrayOf("alpha", "--c1", "valueC1", "f"))
        assertEquals("valueC1", a1.c1)
        assertEquals("defaultC2", a1.c2)

        val a2 = NonNullableStringConflictsTestArgs()
        a2.parseWithException(arrayOf("alpha", "--c2", "valueC2", "f"))
        assertEquals("defaultC1", a2.c1)
        assertEquals("valueC2", a2.c2)

        val a3 = NonNullableStringConflictsTestArgs()
        a3.parseWithException(arrayOf("alpha", "f"))
        assertEquals("defaultC1", a3.c1)
        assertEquals("defaultC2", a3.c2)
    }

    @Test
    fun nonNullableStringConflictsWith_failsWhenBothProvided() {
        val a = NonNullableStringConflictsTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--c1", "valueC1", "--c2", "valueC2", "f"))
        }
    }

    @Test
    fun nonNullableIntRequireIfAllPresent_passesWhenNotAllPresent() {
        val a1 = NonNullableIntRequireIfAllTestArgs()
        a1.parseWithException(arrayOf("alpha", "--trigger1", "1", "f"))
        assertEquals(1, a1.trigger1)
        assertEquals(0, a1.trigger2)
        assertEquals("defaultTarget", a1.target)

        val a2 = NonNullableIntRequireIfAllTestArgs()
        a2.parseWithException(arrayOf("alpha", "f"))
        assertEquals(0, a2.trigger1)
        assertEquals(0, a2.trigger2)
        assertEquals("defaultTarget", a2.target)
    }

    @Test
    fun nonNullableIntRequireIfAllPresent_passesWhenAllPresentAndTargetProvided() {
        val a = NonNullableIntRequireIfAllTestArgs()
        a.parseWithException(arrayOf("alpha", "--trigger1", "1", "--trigger2", "2", "--target", "customTarget", "f"))
        assertEquals(1, a.trigger1)
        assertEquals(2, a.trigger2)
        assertEquals("customTarget", a.target)
    }

    @Test
    fun nonNullableIntRequireIfAllPresent_failsWhenAllPresentButTargetMissing() {
        val a = NonNullableIntRequireIfAllTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--trigger1", "1", "--trigger2", "2", "f"))
        }
    }

    // ============== EDGE CASE CONSTRAINT TESTS ==============

    @Test
    fun mixedDefaultAtMostOne_passesWhenNeitherProvided() {
        // Neither option provided - hasDefault gets false, noDefault gets null
        val a = MixedDefaultAtMostOneTestArgs()
        a.parseWithException(arrayOf("alpha", "f"))
        assertFalse(a.hasDefault)  // default value
        assertNull(a.noDefault)    // no default, not provided
    }

    @Test
    fun mixedDefaultAtMostOne_passesWithEitherOne() {
        val a1 = MixedDefaultAtMostOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--has-default", "f"))
        assertTrue(a1.hasDefault)
        assertNull(a1.noDefault)

        val a2 = MixedDefaultAtMostOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "f", "--no-default"))
        assertFalse(a2.hasDefault)  // still default
        // When used as a flag (no explicit value), nullable boolean should be true
        assertEquals(true, a2.noDefault)
    }

    @Test
    fun mixedDefaultAtMostOne_failsWhenBothProvided() {
        val a = MixedDefaultAtMostOneTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--has-default", "--no-default", "f"))
        }
    }

    @Test
    fun mixedDefaultExactlyOne_passesWithEitherOne() {
        val a1 = MixedDefaultExactlyOneTestArgs()
        a1.parseWithException(arrayOf("alpha", "--with-default", "custom", "f"))
        assertEquals("custom", a1.withDefault)
        assertNull(a1.withoutDefault)

        val a2 = MixedDefaultExactlyOneTestArgs()
        a2.parseWithException(arrayOf("alpha", "--without-default", "value", "f"))
        assertEquals("default", a2.withDefault)  // still default
        assertEquals("value", a2.withoutDefault)
    }

    @Test
    fun mixedDefaultExactlyOne_failsWhenNoneProvided() {
        // Critical test: should fail when neither is user-provided, even though one has default
        val a = MixedDefaultExactlyOneTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "f"))
        }
    }

    @Test
    fun mixedTypeConflicts_passesWithNone() {
        val a = MixedTypeConflictsTestArgs()
        a.parseWithException(arrayOf("alpha", "f"))
        assertFalse(a.boolOpt)  // default
        assertNull(a.intOpt)
        assertNull(a.stringOpt)
    }

    @Test
    fun mixedTypeConflicts_passesWithOneType() {
        val a1 = MixedTypeConflictsTestArgs()
        a1.parseWithException(arrayOf("alpha", "--bool", "f"))
        assertTrue(a1.boolOpt)
        assertNull(a1.intOpt)
        assertNull(a1.stringOpt)

        val a2 = MixedTypeConflictsTestArgs()
        a2.parseWithException(arrayOf("alpha", "--int", "42", "f"))
        assertFalse(a2.boolOpt)  // default, not provided
        assertEquals(42, a2.intOpt)
        assertNull(a2.stringOpt)

        val a3 = MixedTypeConflictsTestArgs()
        a3.parseWithException(arrayOf("alpha", "--string", "test", "f"))
        assertFalse(a3.boolOpt)  // default, not provided
        assertNull(a3.intOpt)
        assertEquals("test", a3.stringOpt)
    }

    @Test
    fun mixedTypeConflicts_failsWithMultipleTypes() {
        val a1 = MixedTypeConflictsTestArgs()
        assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--bool", "--int", "42", "f"))
        }

        val a2 = MixedTypeConflictsTestArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--int", "42", "--string", "test", "f"))
        }
    }

    @Test
    fun requiredInConstraint_failsWhenRequiredMissingAndOptionalNotProvided() {
        val a = RequiredInConstraintTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "f"))
        }
    }

    @Test
    fun requiredInConstraint_passesWhenRequiredProvidedAlone() {
        val a = RequiredInConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "--required", "value", "f"))
        assertEquals("value", a.required)
        assertFalse(a.optional)  // default
    }

    @Test
    fun requiredInConstraint_failsWhenBothProvided() {
        val a = RequiredInConstraintTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--required", "value", "--optional", "f"))
        }
    }

    @Test
    fun collectionConstraints_passesWithEmptyCollections() {
        val a = CollectionConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "f"))
        assertTrue(a.list1.isEmpty())
        assertTrue(a.list2.isEmpty())
        assertTrue(a.set1.isEmpty())
        assertTrue(a.set2.isEmpty())
    }

    @Test
    fun collectionConstraints_passesWithOneCollection() {
        val a1 = CollectionConstraintTestArgs()
        a1.parseWithException(arrayOf("alpha", "--list1", "a", "--list1", "b", "f"))
        assertEquals(listOf("a", "b"), a1.list1)
        assertTrue(a1.list2.isEmpty())

        val a2 = CollectionConstraintTestArgs()
        a2.parseWithException(arrayOf("alpha", "--set1", "x", "--set1", "y", "f"))
        assertEquals(setOf("x", "y"), a2.set1)
        assertTrue(a2.set2.isEmpty())
    }

    @Test
    fun collectionConstraints_failsWithConflictingCollections() {
        val a1 = CollectionConstraintTestArgs()
        assertFailsWith<ParseError> {
            a1.parseWithException(arrayOf("alpha", "--list1", "a", "--list2", "b", "f"))
        }

        val a2 = CollectionConstraintTestArgs()
        assertFailsWith<ParseError> {
            a2.parseWithException(arrayOf("alpha", "--set1", "x", "--set2", "y", "f"))
        }
    }

    @Test
    fun countConstraints_failsWithZeroCounts() {
        // atLeastOneWith should require at least one to be provided
        val a = CountConstraintTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "f"))
        }
    }

    @Test
    fun countConstraints_passesWithOneNonZeroCount() {
        val a1 = CountConstraintTestArgs()
        a1.parseWithException(arrayOf("alpha", "--count1", "--count1", "f"))
        assertEquals(2, a1.count1.size)
        assertEquals(0, a1.count2.size)

        val a2 = CountConstraintTestArgs()
        a2.parseWithException(arrayOf("alpha", "--count2", "f"))
        assertEquals(0, a2.count1.size)
        assertEquals(1, a2.count2.size)
    }

    @Test
    fun countConstraints_passesWithBothProvided() {
        // atLeastOneWith allows both to be provided
        val a = CountConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "--count1", "--count2", "--count2", "f"))
        assertEquals(1, a.count1.size)
        assertEquals(2, a.count2.size)
    }

    @Test
    fun multiLevelConstraints_passesWithSimpleCase() {
        // Only provide --a, satisfying exactlyOneWith(b) and not conflicting with c
        val a = MultiLevelConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "--a", "f"))
        assertTrue(a.a)
        assertFalse(a.b)  // default
        assertFalse(a.c)  // default
        assertFalse(a.d)  // default
        assertFalse(a.e)  // default
    }

    @Test
    fun multiLevelConstraints_failsWithConflictingOptions() {
        // --a conflicts with --c
        val a = MultiLevelConstraintTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--a", "--c", "f"))
        }
    }

    @Test
    fun multiLevelConstraints_passesWhenRequireIfAllNotTriggered() {
        // --b has requireIfAllPresent(::d, ::e), but only --d is provided, so no requirement
        val a = MultiLevelConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "--b", "--d", "f"))
        assertFalse(a.a)  // default, satisfies exactlyOneWith(b)
        assertTrue(a.b)   // provided
        assertFalse(a.c)  // default, no conflict
        assertTrue(a.d)   // provided
        assertFalse(a.e)  // not provided, so requireIfAllPresent doesn't trigger
    }

    @Test
    fun multiLevelConstraints_failsWhenRequireIfAllTriggeredButTargetMissing() {
        // When both --d and --e are provided, --b is required by requireIfAllPresent
        val a = MultiLevelConstraintTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--d", "--e", "f"))  // missing --b
        }
    }

    @Test
    fun multiLevelConstraints_passesWithComplexValidCase() {
        // --b with both --d and --e satisfies all constraints
        val a = MultiLevelConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "--b", "--d", "--e", "f"))
        assertFalse(a.a)  // default, satisfies exactlyOneWith(b)
        assertTrue(a.b)   // provided
        assertFalse(a.c)  // default, no conflict
        assertTrue(a.d)   // provided, satisfies requireIfAllPresent
        assertTrue(a.e)   // provided, satisfies requireIfAllPresent
    }

    // ============== ENVIRONMENT VARIABLE CONSTRAINT TESTS ==============

    @Test
    fun envConstraints_passesWhenNeitherEnvNorRegularProvided() {
        val a = EnvConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "f"))
        // Both should be null/default since env var doesn't exist and regular not provided
        assertNull(a.envOpt)  // No TEST_ENV_VAR environment variable
        assertFalse(a.regularOpt)  // Default value
    }

    @Test
    fun envConstraints_passesWhenOnlyRegularProvided() {
        val a = EnvConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "--regular", "f"))
        assertNull(a.envOpt)  // No TEST_ENV_VAR environment variable
        assertTrue(a.regularOpt)  // User provided
    }

    @Test
    fun envConstraints_passesWhenOnlyExplicitEnvProvided() {
        val a = EnvConstraintTestArgs()
        a.parseWithException(arrayOf("alpha", "--env", "envValue", "f"))
        assertEquals("envValue", a.envOpt)
        assertFalse(a.regularOpt)  // default
    }

    @Test
    fun envConstraints_failsWhenExplicitEnvAndRegularBothProvided() {
        val a = EnvConstraintTestArgs()
        assertFailsWith<ParseError> {
            a.parseWithException(arrayOf("alpha", "--env", "envValue", "--regular", "f"))
        }
    }
}
