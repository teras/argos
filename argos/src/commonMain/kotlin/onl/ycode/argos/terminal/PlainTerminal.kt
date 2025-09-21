/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.terminal

import onl.ycode.argos.OsBound

/**
 * Terminal implementation that outputs plain, unformatted text without any styling.
 *
 * PlainTerminal ignores all style requests ([ContentStyle.STRONG], [ContentStyle.ERROR], etc.)
 * and renders all text as plain, unformatted output. This is useful for:
 * - Non-color terminals that don't support ANSI escape codes
 * - Output redirection to files or pipes
 * - Environments where formatting would interfere (scripts, logs, CI/CD)
 * - Testing and automated processing of output
 *
 * PlainTerminal is automatically selected when the environment doesn't support ANSI colors,
 * unless explicitly overridden via the `terminal` parameter in [Arguments] constructor.
 *
 * **Usage:**
 * ```kotlin
 * class MyApp : Arguments(terminal = PlainTerminal()) {
 *     // ... options
 * }
 * ```
 *
 * @param useStdErr If true, writes output to standard error; otherwise writes to standard output (default: false)
 * @see ANSITerminal For colored terminal output
 * @see Terminal For the terminal abstraction interface
 */
class PlainTerminal(private val useStdErr: Boolean = false) : Terminal {
    override fun emitPlain(text: String) = if (useStdErr) OsBound.eprint(text) else print(text)
}