/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.terminal

import onl.ycode.argos.OsBound

/**
 * Terminal implementation that renders styled text using ANSI escape codes for colored output.
 *
 * ANSITerminal provides rich, colored formatting for modern terminal emulators that support
 * ANSI escape sequences. This is the default terminal implementation when the environment
 * supports color output. The color scheme is carefully chosen for readability:
 *
 * **Color Scheme:**
 * - **STRONG**: Orange (ANSI 256-color code 208) - Used for headers and emphasis
 * - **PARAM**: Cyan (ANSI code 36) - Used for option names, switches, and values
 * - **ERROR**: Red (ANSI code 31) - Used for error messages and warnings
 * - **PLAIN**: Default terminal color (ANSI reset) - Used for regular text
 *
 * ANSITerminal is automatically selected on terminals that support ANSI colors (most modern
 * terminals on Linux, macOS, and Windows Terminal), unless explicitly overridden.
 *
 * **Usage:**
 * ```kotlin
 * class MyApp : Arguments(terminal = ANSITerminal()) {
 *     // ... options
 * }
 * ```
 *
 * **Environment Detection:**
 * The Arguments class automatically detects ANSI support based on environment variables
 * and platform capabilities. Explicit terminal selection overrides auto-detection.
 *
 * @param useStdErr If true, writes output to standard error; otherwise writes to standard output (default: false)
 * @see PlainTerminal For plain text output without colors
 * @see StateTerminal For the base stateful terminal implementation
 * @see ContentStyle For the styling categories
 */
class ANSITerminal(private val useStdErr: Boolean = false) : StateTerminal() {
    /**
     * Outputs ANSI reset sequence to return to plain text formatting.
     *
     * @param previous The previous content style (unused in this implementation)
     */
    override fun openingPlain(previous: ContentStyle) = sendText("\u001B[0m")   // Reset

    /**
     * Outputs ANSI escape sequence for strong/bold text (orange color).
     *
     * @param previous The previous content style (unused in this implementation)
     */
    override fun openingStrong(previous: ContentStyle) = sendText("\u001B[38;5;208m")

    /**
     * Outputs ANSI escape sequence for parameter text (cyan color).
     *
     * @param previous The previous content style (unused in this implementation)
     */
    override fun openingParam(previous: ContentStyle) = sendText("\u001B[36m")

    /**
     * Outputs ANSI escape sequence for error text (red color).
     *
     * @param previous The previous content style (unused in this implementation)
     */
    override fun openingError(previous: ContentStyle) = sendText("\u001B[31m")

    /**
     * Sends raw text to the output stream.
     *
     * @param text The text to output
     */
    override fun sendText(text: String) = if (useStdErr) OsBound.eprint(text) else print(text)
    override fun startEmit() = sendText("\u001B[0m")
    override fun endEmit() = sendText("\u001B[0m")
}