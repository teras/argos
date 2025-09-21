/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BooleanBehaviorTest {

    @Test
    fun compareBooleanBehavior() {
        // Test TestArgs.g1 behavior
        val testArgs = TestArgs()
        testArgs.parseWithException(arrayOf("alpha", "f", "--g1"))
        println("TestArgs g1 (working): ${testArgs.g1}")

        // Test MixedDefaultAtMostOneTestArgs.noDefault behavior
        val mixedArgs = MixedDefaultAtMostOneTestArgs()
        mixedArgs.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("MixedArgs noDefault (failing): ${mixedArgs.noDefault}")

        // Create identical test to TestArgs
        class IdenticalTestArgs : Arguments(
            appName = "identical-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val testFlag by option("--test-flag").bool()
            val file by positional()
        }

        val identicalArgs = IdenticalTestArgs()
        identicalArgs.parseWithException(arrayOf("alpha", "f", "--test-flag"))
        println("Identical testFlag: ${identicalArgs.testFlag}")

        // All three should behave the same way - and now they do!
        println("Values: g1=${testArgs.g1}, noDefault=${mixedArgs.noDefault}, testFlag=${identicalArgs.testFlag}")

        // All boolean options should behave consistently when provided
        assertEquals(testArgs.g1, mixedArgs.noDefault)
        assertEquals(testArgs.g1, identicalArgs.testFlag)

        // All should return true when the flag is provided
        assertEquals(true, testArgs.g1)
        assertEquals(true, mixedArgs.noDefault)
        assertEquals(true, identicalArgs.testFlag)
    }

    @Test
    fun debugConstraintInteraction() {
        // Test if constraint interaction affects boolean parsing
        println("=== Testing constraint vs no-constraint boolean parsing ===")

        // Constraint-free boolean (like IdenticalTestArgs)
        class NoBooleanConstraintArgs : Arguments(
            appName = "no-constraint",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val flag by option("--flag").bool()
            val file by positional()
        }

        // Boolean with constraint (like MixedDefaultAtMostOneTestArgs)
        class WithBooleanConstraintArgs : Arguments(
            appName = "with-constraint",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val hasDefault by option("--has-default").bool().default(false).atMostOneWith(::flag)
            val flag by option("--flag").bool()
            val file by positional()
        }

        val noConstraint = NoBooleanConstraintArgs()
        noConstraint.parseWithException(arrayOf("alpha", "f", "--flag"))
        println("No constraint flag: ${noConstraint.flag}")

        val withConstraint = WithBooleanConstraintArgs()
        withConstraint.parseWithException(arrayOf("alpha", "f", "--flag"))
        println("With constraint flag: ${withConstraint.flag}")

        // Boolean options should behave consistently regardless of constraints
        assertEquals(noConstraint.flag, withConstraint.flag)
        assertEquals(true, noConstraint.flag)
        assertEquals(true, withConstraint.flag)
    }

    @Test
    fun debugConstraintTargetRole() {
        // Test whether being the TARGET vs INITIATOR of constraint matters
        println("=== Testing constraint initiator vs target roles ===")

        // Target of atMostOneWith (like MixedDefaultAtMostOneTestArgs.noDefault)
        class ConstraintTargetArgs : Arguments(
            appName = "constraint-target",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val hasDefault by option("--has-default").bool().default(false).atMostOneWith(::targetFlag)
            val targetFlag by option("--target-flag").bool()  // This is the target!
            val file by positional()
        }

        // Initiator of atMostOneWith (different from the failing case)
        class ConstraintInitiatorArgs : Arguments(
            appName = "constraint-initiator",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val initiatorFlag by option("--initiator-flag").bool().atMostOneWith(::other)
            val other by option("--other").bool().default(false)
            val file by positional()
        }

        val targetTest = ConstraintTargetArgs()
        targetTest.parseWithException(arrayOf("alpha", "f", "--target-flag"))
        println("Constraint target flag: ${targetTest.targetFlag}")

        val initiatorTest = ConstraintInitiatorArgs()
        initiatorTest.parseWithException(arrayOf("alpha", "f", "--initiator-flag"))
        println("Constraint initiator flag: ${initiatorTest.initiatorFlag}")

        // Compare with exact MixedDefaultAtMostOneTestArgs pattern
        val originalFailing = MixedDefaultAtMostOneTestArgs()
        originalFailing.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("Original failing noDefault: ${originalFailing.noDefault}")

        if (targetTest.targetFlag != originalFailing.noDefault) {
            println("TARGET ROLE BUG: Target of constraint behaves differently!")
            println("Generic target: ${targetTest.targetFlag}")
            println("MixedArgs target: ${originalFailing.noDefault}")
        }
    }

    @Test
    fun debugExactReplication() {
        // Create EXACT replica of MixedDefaultAtMostOneTestArgs
        println("=== Testing exact replication ===")

        class ExactReplicaArgs : Arguments(
            appName = "exact-replica-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val hasDefault by option("--has-default").bool().default(false).atMostOneWith(::noDefault)
            val noDefault by option("--no-default").bool()  // nullable
            val file by positional()
        }

        val replica = ExactReplicaArgs()
        replica.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("Exact replica noDefault: ${replica.noDefault}")

        val original = MixedDefaultAtMostOneTestArgs()
        original.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("Original MixedArgs noDefault: ${original.noDefault}")

        if (replica.noDefault != original.noDefault) {
            println("REPLICATION BUG: Identical code behaves differently!")
            println("Replica: ${replica.noDefault}")
            println("Original: ${original.noDefault}")
        } else {
            println("CONSISTENT: Both replicas behave the same")
            // If they're both false, let's test simpler pattern
            class SuperSimpleArgs : Arguments(
                appName = "super-simple",
                useANSITerminal = false
            ) {
                val alpha by domain("alpha")
                val flag by option("--flag").bool()
                val file by positional()
            }
            val simple = SuperSimpleArgs()
            simple.parseWithException(arrayOf("alpha", "f", "--flag"))
            println("Super simple flag: ${simple.flag}")

            if (simple.flag != replica.noDefault) {
                println("SIMPLICITY REVEALS: Simple works but complex doesn't")
                println("Simple: ${simple.flag}")
                println("Complex: ${replica.noDefault}")
            }
        }
    }

    @Test
    fun testDefaultValueConstraintTheory() {
        // Test theory: default value + constraint causes the bug
        println("=== Testing default value + constraint theory ===")

        // Case 1: Constraint with NO default (should work)
        class NoDefaultConstraintArgs : Arguments(
            appName = "no-default-constraint",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val noDefault by option("--no-default").bool().atMostOneWith(::target)  // NO default
            val target by option("--target").bool()
            val file by positional()
        }

        // Case 2: Constraint with default (reproduces bug?)
        class WithDefaultConstraintArgs : Arguments(
            appName = "with-default-constraint",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val withDefault by option("--with-default").bool().default(false).atMostOneWith(::target)  // HAS default
            val target by option("--target").bool()
            val file by positional()
        }

        val noDefault = NoDefaultConstraintArgs()
        noDefault.parseWithException(arrayOf("alpha", "f", "--target"))
        println("No default constraint target: ${noDefault.target}")

        val withDefault = WithDefaultConstraintArgs()
        withDefault.parseWithException(arrayOf("alpha", "f", "--target"))
        println("With default constraint target: ${withDefault.target}")

        if (noDefault.target != withDefault.target) {
            println("DEFAULT BUG CONFIRMED: Default value in constraint affects parsing!")
            println("No default: ${noDefault.target}")
            println("With default: ${withDefault.target}")
        } else {
            println("No difference. Testing property declaration order...")

            // Case 3: Target declared BEFORE initiator with default
            class ReversedOrderArgs : Arguments(
                appName = "reversed-order",
                useANSITerminal = false
            ) {
                val alpha by domain("alpha")
                val target by option("--target").bool()  // declared FIRST
                val withDefault by option("--with-default").bool().default(false).atMostOneWith(::target)
                val file by positional()
            }

            val reversed = ReversedOrderArgs()
            reversed.parseWithException(arrayOf("alpha", "f", "--target"))
            println("Reversed order target: ${reversed.target}")

            if (reversed.target != withDefault.target) {
                println("ORDER BUG: Property declaration order matters!")
                println("Target first: ${reversed.target}")
                println("Default first: ${withDefault.target}")
            }
        }
    }

    @Test
    fun debugRawParsingValues() {
        // Let's just trace what happens with a simpler approach
        println("=== Debugging what happens during parsing ===")

        // Compare working vs failing cases side by side
        println("Testing working case (TestArgs.g1):")
        val working = TestArgs()
        working.parseWithException(arrayOf("alpha", "f", "--g1"))
        println("Working g1 result: ${working.g1}")

        println("Testing failing case (MixedDefaultAtMostOneTestArgs.noDefault):")
        val failing = MixedDefaultAtMostOneTestArgs()
        failing.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("Failing noDefault result: ${failing.noDefault}")

        // Let's check what happens with an exact copy of the working TestArgs pattern
        println("Testing TestArgs g1 pattern with different name:")
        class TestArgsPattern : Arguments(
            appName = "test-args-pattern",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val testFlag by option("--test-flag").bool().atLeastOneWith(::other)  // same as TestArgs.g1
            val other by option("--other").bool()
            val file by positional()
        }

        val pattern = TestArgsPattern()
        pattern.parseWithException(arrayOf("alpha", "f", "--test-flag"))
        println("TestArgs pattern result: ${pattern.testFlag}")

        // The issue might be in the difference between atLeastOneWith vs atMostOneWith
        // or in having a default value in the constraint partner

        // Test constraint type theory: atLeastOneWith vs atMostOneWith
        println("Testing constraint type theory:")

        class AtLeastOneTestCase : Arguments(
            appName = "at-least-one-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val hasDefault by option("--has-default").bool().default(false).atLeastOneWith(::target)
            val target by option("--target").bool()
            val file by positional()
        }

        class AtMostOneTestCase : Arguments(
            appName = "at-most-one-test",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val hasDefault by option("--has-default").bool().default(false).atMostOneWith(::target)
            val target by option("--target").bool()
            val file by positional()
        }

        val atLeastCase = AtLeastOneTestCase()
        atLeastCase.parseWithException(arrayOf("alpha", "f", "--target"))
        println("atLeastOneWith target result: ${atLeastCase.target}")

        val atMostCase = AtMostOneTestCase()
        atMostCase.parseWithException(arrayOf("alpha", "f", "--target"))
        println("atMostOneWith target result: ${atMostCase.target}")

        if (atLeastCase.target != atMostCase.target) {
            println("CONSTRAINT TYPE BUG: atLeastOneWith vs atMostOneWith behavior differs!")
            println("atLeastOneWith: ${atLeastCase.target}")
            println("atMostOneWith: ${atMostCase.target}")
        }

        // Test property declaration order theory
        println("Testing property declaration order theory:")

        class TargetFirstOrder : Arguments(
            appName = "target-first-order",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val noDefault by option("--no-default").bool()  // TARGET declared FIRST
            val hasDefault by option("--has-default").bool().default(false).atMostOneWith(::noDefault)
            val file by positional()
        }

        class InitiatorFirstOrder : Arguments(
            appName = "initiator-first-order",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val hasDefault by option("--has-default").bool().default(false).atMostOneWith(::noDefault)
            val noDefault by option("--no-default").bool()  // TARGET declared SECOND
            val file by positional()
        }

        val targetFirst = TargetFirstOrder()
        targetFirst.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("Target declared first: ${targetFirst.noDefault}")

        val initiatorFirst = InitiatorFirstOrder()
        initiatorFirst.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("Initiator declared first: ${initiatorFirst.noDefault}")

        if (targetFirst.noDefault != initiatorFirst.noDefault) {
            println("DECLARATION ORDER BUG: Property order affects parsing!")
            println("Target first: ${targetFirst.noDefault}")
            println("Initiator first: ${initiatorFirst.noDefault}")
        } else {
            println("Declaration order doesn't matter - both return ${targetFirst.noDefault}")
        }

        // Test option name theory - using exact same names as working case
        println("Testing option name theory:")

        class WorkingOptionNames : Arguments(
            appName = "working-option-names",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val withDefault by option("--with-default").bool().default(false).atMostOneWith(::target)
            val target by option("--target").bool()
            val file by positional()
        }

        class FailingOptionNames : Arguments(
            appName = "failing-option-names",
            useANSITerminal = false
        ) {
            val alpha by domain("alpha")
            val hasDefault by option("--has-default").bool().default(false).atMostOneWith(::noDefault)
            val noDefault by option("--no-default").bool()
            val file by positional()
        }

        val workingNames = WorkingOptionNames()
        workingNames.parseWithException(arrayOf("alpha", "f", "--target"))
        println("Working option names (--target): ${workingNames.target}")

        val failingNames = FailingOptionNames()
        failingNames.parseWithException(arrayOf("alpha", "f", "--no-default"))
        println("Failing option names (--no-default): ${failingNames.noDefault}")

        if (workingNames.target != failingNames.noDefault) {
            println("OPTION NAME BUG: Option names affect parsing!")
            println("--target: ${workingNames.target}")
            println("--no-default: ${failingNames.noDefault}")
        }
    }
}
