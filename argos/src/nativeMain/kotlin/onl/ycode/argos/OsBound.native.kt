/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalForeignApi::class)

package onl.ycode.argos

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.*

actual object OsBound {
    // Common implementations (moved from both platforms)
    actual fun getenv(name: String) = platform.posix.getenv(name)?.toKString()

    actual fun eprint(message: Any?) {
        fputs(message?.toString() ?: "null", stderr)
        fflush(stderr)
    }

    actual fun eprintln(message: Any?) {
        fputs(message?.toString() ?: "null", stderr)
        fputc('\n'.code, stderr)
        fflush(stderr)
    }

    actual fun flush() {
        fflush(stdout)
    }

    actual fun eflush() {
        fflush(stderr)
    }

    // UTF-8 utility (moved from POSIX, shared for security)
    internal fun utf8ToCharArray(bytes: ByteArray, len: Int): CharArray {
        val out = CharArray(len * 2)
        var oi = 0;
        var i = 0
        fun b(k: Int) = bytes[k].toInt() and 0xFF
        while (i < len) {
            val x = b(i)
            when {
                x < 0x80 -> {
                    out[oi++] = x.toChar(); i++
                }

                x in 0xC2..0xDF && i + 1 < len && (b(i + 1) and 0xC0) == 0x80 -> {
                    out[oi++] = (((x and 0x1F) shl 6) or (b(i + 1) and 0x3F)).toChar(); i += 2
                }

                x in 0xE0..0xEF && i + 2 < len -> {
                    val y = b(i + 1);
                    val z = b(i + 2)
                    val ok = (y and 0xC0) == 0x80 && (z and 0xC0) == 0x80 &&
                            !(x == 0xE0 && y < 0xA0) && !(x == 0xED && y >= 0xA0)
                    if (ok) {
                        val cp = ((x and 0x0F) shl 12) or ((y and 0x3F) shl 6) or (z and 0x3F)
                        out[oi++] = cp.toChar(); i += 3
                    } else {
                        out[oi++] = '\uFFFD'; i++
                    }
                }

                x in 0xF0..0xF4 && i + 3 < len -> {
                    val y = b(i + 1);
                    val z = b(i + 2);
                    val w = b(i + 3)
                    val ok = (y and 0xC0) == 0x80 && (z and 0xC0) == 0x80 && (w and 0xC0) == 0x80 &&
                            !(x == 0xF0 && y < 0x90) && !(x == 0xF4 && y >= 0x90)
                    if (ok) {
                        val cp = ((x and 0x07) shl 18) or ((y and 0x3F) shl 12) or
                                ((z and 0x3F) shl 6) or (w and 0x3F)
                        val hi = ((cp - 0x10000) shr 10) + 0xD800
                        val lo = ((cp - 0x10000) and 0x3FF) + 0xDC00
                        out[oi++] = hi.toChar(); out[oi++] = lo.toChar(); i += 4
                    } else {
                        out[oi++] = '\uFFFD'; i++
                    }
                }

                else -> {
                    out[oi++] = '\uFFFD'; i++
                }
            }
        }
        return out.copyOf(oi)
    }

    // Platform-specific delegates
    actual val termNewLine: String get() = platformTermNewLine
    actual fun termWidth(): Int = platformTermWidth()
    actual fun supportsAnsi(): Boolean = platformSupportsAnsi()
    actual fun readPassword(): CharArray? = platformReadPassword()
    actual fun readLine(): String? = kotlin.io.readLine()
    actual fun readFile(path: String): String? = platformReadFile(path)
    actual fun exit(exitCode: Int): Nothing {
        platform.posix.exit(exitCode)
        throw RuntimeException("This should never be reached")
    }
}

// Platform-specific expectations
internal expect val platformTermNewLine: String
internal expect fun platformTermWidth(): Int
internal expect fun platformSupportsAnsi(): Boolean
internal expect fun platformReadPassword(): CharArray?
internal expect fun platformReadFile(path: String): String?
