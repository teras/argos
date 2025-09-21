/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

// EditDistance.kt
package onl.ycode.argos

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object EditDistance {
    fun damerauLevenshtein(a: String, b: String, maxDist: Int? = null): Int {
        if (a === b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val m = a.length
        val n = b.length

        if (maxDist != null && abs(m - n) > maxDist) return maxDist + 1

        val big = if (maxDist != null) maxDist + 1 else (m + n + 1)
        var prevPrevRow = IntArray(n + 1)
        var prevRow = IntArray(n + 1) { it }
        var currRow = IntArray(n + 1) { big }

        var rowMin: Int

        for (i in 1..m) {
            val ai = a[i - 1]
            rowMin = big

            val jStart = if (maxDist == null) 1 else max(1, i - maxDist)
            val jEnd = if (maxDist == null) n else min(n, i + maxDist)

            currRow[0] = i
            if (jStart > 1) currRow[jStart - 1] = big

            for (j in jStart..jEnd) {
                val bj = b[j - 1]
                val cost = if (ai == bj) 0 else 1

                val del = prevRow[j] + 1
                val ins = currRow[j - 1] + 1
                val sub = prevRow[j - 1] + cost

                var v = min(min(del, ins), sub)

                if (i > 1 && j > 1 && ai == b[j - 2] && a[i - 2] == bj) {
                    v = min(v, prevPrevRow[j - 2] + 1)
                }

                currRow[j] = v
                if (v < rowMin) rowMin = v
            }

            if (maxDist != null && rowMin > maxDist) return maxDist + 1

            val tmp = prevPrevRow
            prevPrevRow = prevRow
            prevRow = currRow
            currRow = tmp
            currRow.fill(big)
        }

        val dist = prevRow[n]
        return if (maxDist != null && dist > maxDist) maxDist + 1 else dist
    }
}
