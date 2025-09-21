/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos.terminal

import onl.ycode.argos.OsBound

/**
 * Terminal implementation that outputs plain text without any formatting.
 *
 * This terminal ignores all styling requests and outputs all text as plain text.
 * Useful for environments that don't support ANSI colors or when plain output is desired.
 *
 * @param useStdErr Whether to output to standard error instead of standard output
 */
class PlainTerminal(private val useStdErr: Boolean = false) : Terminal {
    override fun emitPlain(text: String) = if (useStdErr) OsBound.eprint(text) else println(text)
}