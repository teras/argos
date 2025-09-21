/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

// Simple test class for exactlyOneWith
class ExactlyOneTestArgs : Arguments(
    appName = "exactly-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val e1 by option("--e1").bool().default(false).exactlyOneWith(::e2)
    val e2 by option("--e2").bool().default(false)
    val file by positional()
}

// Simple test class for atMostOneWith
class AtMostOneTestArgs : Arguments(
    appName = "at-most-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val m1 by option("--m1").bool().default(false).atMostOneWith(::m2)
    val m2 by option("--m2").bool().default(false)
    val file by positional()
}

// Simple test class for atLeastOneWith
class AtLeastOneTestArgs : Arguments(
    appName = "at-least-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val l1 by option("--l1").bool().default(false).atLeastOneWith(::l2)
    val l2 by option("--l2").bool().default(false)
    val file by positional()
}

// Simple test class for conflictsWith
class ConflictsTestArgs : Arguments(
    appName = "conflicts-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val c1 by option("--c1").bool().default(false).conflictsWith(::c2)
    val c2 by option("--c2").bool().default(false)
    val file by positional()
}

// Test class for requireIfAllPresent
class RequireIfAllTestArgs : Arguments(
    appName = "require-if-all-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val trigger1 by option("--trigger1").bool().default(false)
    val trigger2 by option("--trigger2").bool().default(false)
    val target by option("--target").bool().default(false).requireIfAllPresent(::trigger1, ::trigger2)
    val file by positional()
}

// Test class for requireIfValue
class RequireIfValueTestArgs : Arguments(
    appName = "require-if-value-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val mode by option("--mode").oneOf("fast", "slow")
    val target by option("--target").bool().default(false).requireIfValue(::mode) { it == "fast" }
    val file by positional()
}

// ================ NULLABLE CONSTRAINT TEST CLASSES ================

// Nullable version of exactlyOneWith test class
class NullableExactlyOneTestArgs : Arguments(
    appName = "nullable-exactly-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val e1 by option("--e1").bool().exactlyOneWith(::e2)
    val e2 by option("--e2").bool()
    val file by positional()
}

// Nullable version of atMostOneWith test class
class NullableAtMostOneTestArgs : Arguments(
    appName = "nullable-at-most-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val m1 by option("--m1").bool().atMostOneWith(::m2)
    val m2 by option("--m2").bool()
    val file by positional()
}

// Nullable version of atLeastOneWith test class
class NullableAtLeastOneTestArgs : Arguments(
    appName = "nullable-at-least-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val l1 by option("--l1").bool().atLeastOneWith(::l2)
    val l2 by option("--l2").bool()
    val file by positional()
}

// Nullable version of conflictsWith test class
class NullableConflictsTestArgs : Arguments(
    appName = "nullable-conflicts-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val c1 by option("--c1").bool().conflictsWith(::c2)
    val c2 by option("--c2").bool()
    val file by positional()
}

// Nullable version of requireIfAllPresent test class
class NullableRequireIfAllTestArgs : Arguments(
    appName = "nullable-require-if-all-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val trigger1 by option("--trigger1").bool()
    val trigger2 by option("--trigger2").bool()
    val target by option("--target").bool().requireIfAllPresent(::trigger1, ::trigger2)
    val file by positional()
}

// Nullable version of requireIfValue test class
class NullableRequireIfValueTestArgs : Arguments(
    appName = "nullable-require-if-value-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val mode by option("--mode").oneOf("fast", "slow")
    val target by option("--target").bool().requireIfValue(::mode) { it == "fast" }
    val file by positional()
}

// ================ NON-NULLABLE CONSTRAINT TEST CLASSES ================

// Non-nullable String constraints with exactlyOneWith
class NonNullableStringExactlyOneTestArgs : Arguments(
    appName = "non-nullable-string-exactly-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val e1 by option("--e1").default("default1").exactlyOneWith(::e2)
    val e2 by option("--e2").default("default2")
    val file by positional()
}

// Non-nullable Int constraints with atMostOneWith
class NonNullableIntAtMostOneTestArgs : Arguments(
    appName = "non-nullable-int-at-most-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val m1 by option("--m1").int().default(0).atMostOneWith(::m2)
    val m2 by option("--m2").int().default(0)
    val file by positional()
}

// Non-nullable oneOf constraints with atLeastOneWith
class NonNullableOneOfAtLeastOneTestArgs : Arguments(
    appName = "non-nullable-oneof-at-least-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val l1 by option("--l1").oneOf("small", "medium", "large").atLeastOneWith(::l2)
    val l2 by option("--l2").oneOf("red", "green", "blue")
    val file by positional()
}

// Non-nullable String constraints with conflictsWith
class NonNullableStringConflictsTestArgs : Arguments(
    appName = "non-nullable-string-conflicts-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val c1 by option("--c1").default("defaultC1").conflictsWith(::c2)
    val c2 by option("--c2").default("defaultC2")
    val file by positional()
}

// Non-nullable Int constraints with requireIfAllPresent
class NonNullableIntRequireIfAllTestArgs : Arguments(
    appName = "non-nullable-int-require-if-all-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val trigger1 by option("--trigger1").int().default(0)
    val trigger2 by option("--trigger2").int().default(0)
    val target by option("--target").default("defaultTarget").requireIfAllPresent(::trigger1, ::trigger2)
    val file by positional()
}

// ================ EDGE CASE CONSTRAINT TEST CLASSES ================

// Mixed default/non-default options in same constraint
class MixedDefaultAtMostOneTestArgs : Arguments(
    appName = "mixed-default-at-most-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val hasDefault by option("--has-default").bool().default(false).atMostOneWith(::noDefault)
    val noDefault by option("--no-default").bool()  // nullable
    val file by positional()
}

// Mixed default/non-default with exactlyOneWith
class MixedDefaultExactlyOneTestArgs : Arguments(
    appName = "mixed-default-exactly-one-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val withDefault by option("--with-default").default("default").exactlyOneWith(::withoutDefault)
    val withoutDefault by option("--without-default")  // nullable String
    val file by positional()
}

// Different option types in same constraint
class MixedTypeConflictsTestArgs : Arguments(
    appName = "mixed-type-conflicts-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val boolOpt by option("--bool").bool().default(false).conflictsWith(::intOpt, ::stringOpt)
    val intOpt by option("--int").int()  // nullable
    val stringOpt by option("--string")  // nullable String
    val file by positional()
}

// Required option in constraints
class RequiredInConstraintTestArgs : Arguments(
    appName = "required-in-constraint-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val required by option("--required").required().atMostOneWith(::optional)
    val optional by option("--optional").bool().default(false)
    val file by positional()
}

// List/Set options in constraints
class CollectionConstraintTestArgs : Arguments(
    appName = "collection-constraint-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val list1 by option("--list1").map { it }.list().atMostOneWith(::list2)
    val list2 by option("--list2").map { it }.list()
    val set1 by option("--set1").map { it }.set().conflictsWith(::set2)
    val set2 by option("--set2").map { it }.set()
    val file by positional()
}

// Count options in constraints
class CountConstraintTestArgs : Arguments(
    appName = "count-constraint-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val count1 by option("--count1").bool().list().atLeastOneWith(::count2)
    val count2 by option("--count2").bool().list()
    val file by positional()
}

// Environment variable fallback with constraints
class EnvConstraintTestArgs : Arguments(
    appName = "env-constraint-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val envOpt by option("--env").fromEnv("TEST_ENV_VAR").atMostOneWith(::regularOpt)
    val regularOpt by option("--regular").bool().default(false)
    val file by positional()
}

// Complex multi-level constraints
class MultiLevelConstraintTestArgs : Arguments(
    appName = "multi-level-constraint-test",
    useANSITerminal = false
) {
    val alpha by domain("alpha")
    val a by option("--a").bool().default(false).exactlyOneWith(::b).conflictsWith(::c)
    val b by option("--b").bool().default(false).requireIfAllPresent(::d, ::e)
    val c by option("--c").bool().default(false)
    val d by option("--d").bool().default(false)
    val e by option("--e").bool().default(false)
    val file by positional()
}
