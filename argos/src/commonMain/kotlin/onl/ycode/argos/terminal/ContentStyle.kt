/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos.terminal

/**
 * Enumeration of content styling types for terminal output formatting.
 *
 * Defines the semantic styling categories used by [Terminal] implementations to apply appropriate
 * formatting to different types of text content. Each style has an associated tag string used for
 * marking styled regions in text. Terminal implementations interpret these styles according to their
 * output format (ANSI colors, Markdown syntax, plain text, etc.).
 *
 * **Style Applications:**
 * - **PLAIN**: Regular body text, descriptions, and explanations
 * - **STRONG**: Section headers, important keywords, emphasis
 * - **ERROR**: Error messages, validation failures, warnings
 * - **PARAM**: Option names (--file), switches (-v), argument values, code elements
 *
 * @see Terminal For the interface that interprets these styles
 * @see ANSITerminal For ANSI color-based style rendering
 * @see MarkdownTerminal For Markdown-based style rendering
 */
enum class ContentStyle(tag: String) {
    /** Plain text without any special formatting or styling. */
    PLAIN("p"),

    /** Text that should be emphasized, typically rendered in bold or highlighted. */
    STRONG("s"),

    /** Error messages and warnings that should be visually distinct to draw attention. */
    ERROR("e"),

    /** Parameter names, option switches, and values that represent code or user input. */
    PARAM("o");

    /** The tag string used to mark this style in text. */
    val tag = "<:$tag>"

    internal fun asTag(value: String?) = if (value == null) "" else "$tag$value${PLAIN.tag}"

    /**
     * Companion object providing internal tag-to-style mappings for parsing styled text.
     */
    companion object {
        /**
         * Internal mapping from tag strings to their corresponding ContentStyle values.
         */
        internal val tagToStyle = entries.associateBy { it.tag }
    }
}