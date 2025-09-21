/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.completion

import onl.ycode.argos.Arguments
import onl.ycode.argos.snapshot.Snapshot

/**
 * Base interface for shell completion generators.
 *
 * Completion generators analyze the CLI structure captured in a [Snapshot] and produce
 * shell-specific completion scripts that enable tab completion for commands.
 */
interface CompletionGenerator {
    /**
     * Generate a completion script for the given CLI application.
     *
     * @param snapshot The snapshot of the CLI structure
     * @return A complete shell completion script as a string
     */
    fun generate(snapshot: Snapshot): String
}

/**
 * Factory object for creating shell completion generators.
 */
object Completions {
    /**
     * Creates a Bash completion generator.
     *
     * Generates completion scripts compatible with bash-completion 2.x and later.
     *
     * @return A [BashCompletionGenerator] instance
     */
    fun bash(): CompletionGenerator = BashCompletionGenerator()

    /**
     * Creates a Zsh completion generator.
     *
     * Generates completion scripts compatible with Zsh's completion system.
     *
     * @return A [ZshCompletionGenerator] instance
     */
    fun zsh(): CompletionGenerator = ZshCompletionGenerator()

    /**
     * Creates a Fish completion generator.
     *
     * Generates completion scripts compatible with the Fish shell.
     *
     * @return A [FishCompletionGenerator] instance
     */
    fun fish(): CompletionGenerator = FishCompletionGenerator()
}

/**
 * Extension function to generate completion scripts directly from an Arguments instance.
 *
 * Usage:
 * ```kotlin
 * class MyApp : Arguments() {
 *     // ... define options ...
 * }
 *
 * val app = MyApp()
 * val bashCompletion = app.generateCompletion(Completions.bash())
 * println(bashCompletion)
 * ```
 *
 * @param generator The completion generator to use
 * @return A complete shell completion script as a string
 */
fun Arguments.generateCompletion(generator: CompletionGenerator): String {
    return generator.generate(this.snapshot())
}
