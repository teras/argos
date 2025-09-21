/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos.terminal

import onl.ycode.argos.OsBound

/**
 * Interface for terminal output with pluggable styling support.
 *
 * This interface allows the argument parser to output formatted text to different
 * terminal types, including ANSI color terminals, plain text, and custom formats like Markdown.
 *
 * Implementations can provide different styling capabilities:
 * - [ANSITerminal] - Colored output using ANSI escape codes
 * - [PlainTerminal] - Plain text output without formatting
 * - [MarkdownTerminal] - Markdown-formatted output
 *
 * The terminal handles help text generation, error messages, and usage information
 * with appropriate styling for the target output format.
 */
interface Terminal {
    /** The terminal width in characters for text wrapping. Defaults to system terminal width. */
    val width: Int get() = OsBound.termWidth()

    /** Column position for aligning help text descriptions. */
    val infoColumn: Int get() = 25

    /** Called before starting a sequence of text emissions. Override for setup operations. */
    fun startEmit() {}

    /**
     * Emits plain text without any formatting.
     * @param text The text to output
     */
    fun emitPlain(text: String)

    /**
     * Emits text with strong/bold formatting. Default implementation outputs as plain text.
     * @param text The text to output with emphasis
     */
    fun emitStrong(text: String) = emitPlain(text)

    /**
     * Emits text formatted as a parameter (e.g., option names, values).
     * @param text The parameter text to output
     */
    fun emitParam(text: String) = emitPlain(text)

    /**
     * Emits text formatted as an error message.
     * @param text The error text to output
     */
    fun emitError(text: String) = emitPlain(text)

    /** Emits a platform-appropriate newline sequence. */
    fun emitNewLine() = emitPlain(OsBound.termNewLine)

    /** Called after completing a sequence of text emissions. Override for cleanup operations. */
    fun endEmit() {}
}