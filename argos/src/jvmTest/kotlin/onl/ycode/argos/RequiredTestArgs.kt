/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

class RequiredTestArgs : Arguments(
    appName = "argos-required-test",
    useANSITerminal = false
) {
    // ---- Domains ----
    val alpha by domain("alpha").label("Alpha")
    val gamma by domain("gamma").required(::mode)

    // ---- Options ----
    val name  by option("--name", "-n").help("name")
    val mode by option("--mode").oneOf("fast", "slow")

    // simple option for satisfying constraints
    val simple by option("--simple").bool().default(false)

    // required list/set/count options for testing
    val requiredList: List<String> by option("--req-list").map { it }.list().required()
    val requiredSet: Set<String> by option("--req-set").map { it }.set().required()
    val requiredCount: List<Boolean> by option("--req-count").bool().list().required()

    // positionals: single
    val file by positional().help("file")
}
