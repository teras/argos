/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

/**
 * String processing utilities for the Argos library.
 *
 * This object contains utility functions that replace regex-based operations
 * to reduce executable size in Kotlin Native applications.
 */
internal object StringUtils {

    /**
     * Splits text on whitespace characters without using regex.
     * Equivalent to `text.split(Regex("\\s+")).filter { it.isNotEmpty() }`
     */
    fun splitWhitespace(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                if (start < i) {
                    result.add(text.substring(start, i))
                }
                // Skip consecutive whitespace
                while (i < text.length && text[i].isWhitespace()) i++
                start = i
            } else {
                i++
            }
        }
        if (start < text.length) {
            result.add(text.substring(start))
        }
        return result
    }
}
