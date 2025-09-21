/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos.terminal

import onl.ycode.argos.OsBound

/**
 * Terminal implementation that uses ANSI escape codes for colored output.
 *
 * This terminal provides colored formatting using ANSI escape sequences:
 * - Strong text: Orange color (ANSI 208)
 * - Parameters: Cyan color (ANSI 36)
 * - Errors: Red color (ANSI 31)
 * - Plain text: Reset to default color
 *
 * @param useStdErr Whether to output to standard error instead of standard output
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