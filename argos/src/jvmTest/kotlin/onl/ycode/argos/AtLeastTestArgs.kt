/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

class AtLeastTestArgs : Arguments(
    appName = "argos-atleast-test",
    useANSITerminal = false
) {
    // ---- Domains ----
    val alpha by domain("alpha").label("Alpha")

    // ---- Options ----
    // atLeast constraint (> 1)
    val atLeastTwo: List<String> by option("--at-least-two").map { it }.list().atLeast(2)

    // positionals: single
    val file by positional().help("file")
}
