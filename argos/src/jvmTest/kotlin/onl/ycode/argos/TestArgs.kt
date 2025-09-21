/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

class TestArgs : Arguments(
    appName = "argos",
    useANSITerminal = false
) {
    // ---- Domains ----
    val alpha by domain("alpha").label("Alpha")
    val beta  by domain("beta").aliases("b")
        .required(::mode)
        .atLeast(::tags, 2)
        .exactlyOne(::x, ::y)
        .conflicts(::dc1, ::dc2)
        .requireIfAnyPresent(::dNeed, ::u, ::v)

    // ---- Options ----
    val name  by option("--name", "-n").help("name")
    val tries by option("--tries", "-t").int().help("tries")

    // count of -v/--verbose occurrences - size of boolean list
    val verbose: List<Boolean> by option("-v", "--verbose").bool().list()

    // eager, negatable
    val help by option("--help").bool().negatable().eager()

    // used by value predicate
    val mode by option("--mode").oneOf("fast", "slow")

    // repeatable list (strings)
    val tags: List<String> by option("--tag", "-g").map { it }.list()

    // a SET (unique, insertion order)
    val colors: Set<String> by option("--color").map { it }.set()

    // toggles used in domain groups
    val x by option("--x").bool()
    val y by option("--y").bool()

    // global groups/conflicts
    val m1 by option("--m1").bool().atMostOneWith(::m2)
    val m2 by option("--m2").bool()

    val g1 by option("--g1").bool().atLeastOneWith(::g2)
    val g2 by option("--g2").bool()

    val c1 by option("--c1").bool().conflictsWith(::c2)
    val c2 by option("--c2").bool()


    // conditionals (global)
    val u by option("--u").bool()
    val v by option("--v").bool()

    val anyNeed by option("--any-need").bool().requireIfAnyPresent(::u, ::v)
    val allNeed by option("--all-need").bool().requireIfAllPresent(::u, ::v)
    val predNeed by option("--pred-need").bool().requireIfValue(::mode) { it == "fast" }

    // domain-conflict pair + domain-conditional target
    val dc1 by option("--dc1").bool()
    val dc2 by option("--dc2").bool()
    val dNeed by option("--d-need").bool()

    // domain-restricted options
    val alphaOnly by option("--alpha-only").onlyInDomains(::alpha).bool()
    val betaOnly  by option("--beta-only").onlyInDomains(::beta).bool()

    // ENV-backed SINGLE options (for tests)
    val envPath    by option("--env-path").fromEnv("PATH")
    val envMissing by option("--env-missing").fromEnv("THIS_IS_AN_INVALID_ARGOS_ENV_VARIABLE")


    // positionals: single + list<Int>
    val file by positional().help("file")
    val extras: List<Int> by positional()
        .map(desc = "an integer") { it?.toIntOrNull() }
        .list()
}
