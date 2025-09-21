/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos.terminal

/**
 * Enumeration of content styling types for terminal output.
 *
 * These styles are used by [Terminal] implementations to apply appropriate
 * formatting to different types of text content in help messages and error output.
 */
enum class ContentStyle {
    /** Plain text without any special formatting. */
    PLAIN,

    /** Text that should be emphasized or displayed in bold. */
    STRONG,

    /** Error messages that should be highlighted for attention. */
    ERROR,

    /** Parameter names, option switches, and values that should be visually distinct. */
    PARAM
}