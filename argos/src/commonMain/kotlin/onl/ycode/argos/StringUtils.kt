/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos


internal fun String.substringBeforeAny(seps: Set<Char>): String {
    val idx = indexOfFirst { it in seps }
    return if (idx >= 0) substring(0, idx) else this
}


/**
 * Splits text on whitespace characters without using regex.
 * Equivalent to `text.split(Regex("\\s+")).filter { it.isNotEmpty() }`
 */
fun String.splitWhitespace(): List<String> {
    val result = mutableListOf<String>()
    var start = 0
    var i = 0
    while (i < length) {
        if (this[i].isWhitespace()) {
            if (start < i) {
                result.add(substring(start, i))
            }
            // Skip consecutive whitespace
            while (i < length && this[i].isWhitespace()) i++
            start = i
        } else {
            i++
        }
    }
    if (start < length) {
        result.add(substring(start))
    }
    return result
}

internal fun String.isNegativeNumberToken(): Boolean =
    length >= 2 && this[0] == '-' && this[1].isDigit()

internal fun String.toKebab(): String = buildString(length + 4) {
    var first = true
    for (c in this@toKebab) {
        if (c.isUpperCase()) {
            if (!first) append('-')
            append(c.lowercaseChar())
        } else append(c)
        first = false
    }
}.trimStart('-')