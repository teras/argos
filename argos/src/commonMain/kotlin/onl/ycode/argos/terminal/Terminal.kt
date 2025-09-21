/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.terminal

import onl.ycode.argos.OsBound

/**
 * Interface for terminal output with pluggable styling support.
 *
 * The Terminal abstraction enables the argument parser to produce formatted output for different
 * terminal types and output formats. This allows help text, error messages, and usage information
 * to be styled appropriately for the target environment, whether it's an ANSI color terminal,
 * plain text output, or structured formats like Markdown.
 *
 * **Available Implementations:**
 * - [ANSITerminal] - Colored output using ANSI escape codes for modern terminals
 * - [PlainTerminal] - Unformatted plain text output for non-color terminals or file redirection
 * - [MarkdownTerminal] - Markdown-formatted output for documentation generation
 * - Custom implementations can be created for specific output needs
 *
 * **Usage:**
 * ```kotlin
 * class MyApp : Arguments(
 *     terminal = ANSITerminal(),  // or PlainTerminal(), MarkdownTerminal()
 *     // ... other options
 * ) {
 *     // ... options
 * }
 * ```
 *
 * The terminal system automatically selects [ANSITerminal] or [PlainTerminal] based on
 * the environment when no explicit terminal is provided via the `useANSITerminal` parameter.
 *
 * @see ANSITerminal For colored terminal output
 * @see PlainTerminal For plain text output
 * @see MarkdownTerminal For Markdown-formatted output
 * @see ContentStyle For available text styling options
 */
interface Terminal : TerminalMetrics {
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

    /** Called after completing a sequence of text emissions. Override for cleanup operations. */
    fun endEmit() {}

    /**
     * Emits tagged content, interpreting style tags defined in [ContentStyle].
     */
    fun emitTaggedContent(content: String) {
        var currentStyle = ContentStyle.PLAIN
        val buffer = StringBuilder()
        var index = 0

        fun emitStyled(text: String) {
            when (currentStyle) {
                ContentStyle.PLAIN -> emitPlain(text)
                ContentStyle.STRONG -> emitStrong(text)
                ContentStyle.ERROR -> emitError(text)
                ContentStyle.PARAM -> emitParam(text)
            }
        }

        fun flushBuffer() {
            if (buffer.isEmpty()) return
            emitStyled(buffer.toString())
            buffer.setLength(0)
        }

        while (index < content.length) {
            when (val char = content[index]) {
                '\r' -> index++
                '\n' -> {
                    flushBuffer()
                    emitStyled(OsBound.termNewLine)
                    index++
                }

                '<' -> {
                    val style = ContentStyle.entries.firstOrNull { content.startsWith(it.tag, index) }
                    if (style != null) {
                        flushBuffer()
                        currentStyle = style
                        index += style.tag.length
                    } else {
                        buffer.append(char)
                        index++
                    }
                }

                else -> {
                    buffer.append(char)
                    index++
                }
            }
        }

        flushBuffer()
    }
}