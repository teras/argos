/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.terminal

import onl.ycode.argos.OsBound

/**
 * Terminal implementation that outputs Markdown-formatted text.
 *
 * This terminal converts style information to Markdown markup:
 * - Strong text: `**bold**`
 * - Parameters: `` `code` ``
 * - Errors: `~~strikethrough~~`
 * - HTML escaping: `<` and `>` characters are escaped to `&lt;` and `&gt;`
 *
 * Useful for generating documentation or when output will be processed by Markdown renderers.
 *
 * @param useStdErr Whether to output to standard error instead of standard output
 */
class MarkdownTerminal(private val useStdErr: Boolean = false) : StateTerminal() {

    /**
     * Outputs Markdown markup for strong/bold text (double asterisks).
     *
     * @param previous The previous content style (unused in this implementation)
     */
    override fun openingStrong(previous: ContentStyle) {
        sendText("**")
    }

    /**
     * Outputs closing Markdown markup for strong/bold text (double asterisks).
     */
    override fun closingStrong() {
        sendText("**")
    }

    /**
     * Outputs Markdown markup for parameter/code text (backtick).
     *
     * @param previous The previous content style (unused in this implementation)
     */
    override fun openingParam(previous: ContentStyle) {
        sendText("`")
    }

    /**
     * Outputs closing Markdown markup for parameter/code text (backtick).
     */
    override fun closingParam() {
        sendText("`")
    }

    /**
     * Outputs Markdown markup for error text (double tildes for strikethrough).
     *
     * @param previous The previous content style (unused in this implementation)
     */
    override fun openingError(previous: ContentStyle) {
        sendText("~~")
    }

    /**
     * Outputs closing Markdown markup for error text (double tildes).
     */
    override fun closingError() {
        sendText("~~")
    }

    /**
     * Sends text to the output stream with HTML escaping.
     *
     * Escapes `<` and `>` characters to `&lt;` and `&gt;` except when inside
     * parameter/code blocks to preserve proper Markdown rendering.
     *
     * @param text The text to output
     */
    override fun sendText(text: String) {
        val txt = if (lastState == ContentStyle.PARAM) text else text.replace("<", "&lt;").replace(">", "&gt;")
        if (useStdErr) OsBound.eprint(txt) else print(txt)
    }

    override fun endEmit() {
        if (lastState != ContentStyle.PLAIN)
            closingPrevious(lastState)
    }
}