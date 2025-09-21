/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalForeignApi::class)

package onl.ycode.argos

import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*

// Platform-specific implementations for Windows (mingw)
internal actual val platformTermNewLine = "\r\n"
internal actual fun platformTermWidth(): Int = memScoped {
        val h = GetStdHandle(STD_OUTPUT_HANDLE)
        val info = alloc<CONSOLE_SCREEN_BUFFER_INFO>()
        if (h != INVALID_HANDLE_VALUE && GetConsoleScreenBufferInfo(h, info.ptr) != 0) {
            val w = info.srWindow
            return (w.Right - w.Left + 1).toInt()
        }
        // fallback: env var (useful in some emulators)
        platform.posix.getenv("COLUMNS")?.toKString()?.toIntOrNull() ?: DEFAULT_TERM_WIDTH
}

    private var vtTried = false
    private var vtOn = false

    private fun ensureANSI(): Boolean {
        if (vtTried) return vtOn
        vtTried = true
        memScoped {
            val h = GetStdHandle(STD_OUTPUT_HANDLE)
            if (h == INVALID_HANDLE_VALUE || h == null) return false
            val mode = alloc<DWORDVar>()
            if (GetConsoleMode(h, mode.ptr) == 0) return false
            val newMode =
                mode.value or (ENABLE_PROCESSED_OUTPUT.toUInt() or ENABLE_VIRTUAL_TERMINAL_PROCESSING.toUInt())
            vtOn = SetConsoleMode(h, newMode) != 0
        }
        return vtOn
    }

internal actual fun platformSupportsAnsi() = ensureANSI()


internal actual fun platformReadPassword(): CharArray? = memScoped {
        val hIn = GetStdHandle(STD_INPUT_HANDLE)
        if (hIn == INVALID_HANDLE_VALUE) return@memScoped null

        val mode = alloc<DWORDVar>()
        if (GetConsoleMode(hIn, mode.ptr) == 0) return@memScoped null
        val oldMode = mode.value
        if (SetConsoleMode(hIn, oldMode and ENABLE_ECHO_INPUT.inv().toUInt()) == 0) return@memScoped null

        val cap = 256
        val buf = allocArray<UShortVar>(cap)   // <-- native pointer, matches LPVOID
        val pieces = ArrayList<CharArray>()
        var total = 0

        try {
            while (true) {
                val readVar = alloc<DWORDVar>()
                if (ReadConsoleW(hIn, buf, cap.toUInt(), readVar.ptr, null) == 0) return@memScoped null
                var n = readVar.value.toInt()
                if (n == 0) break

                // strip trailing CRLF if present in this chunk
                val hasCrLf = n >= 2 &&
                        buf[n - 2].toShort().toInt() == '\r'.code &&
                        buf[n - 1].toShort().toInt() == '\n'.code
                if (hasCrLf) n -= 2

                if (n > 0) {
                    val part = CharArray(n) { i -> buf[i].toShort().toInt().toChar() }
                    pieces += part
                    total += n
                }
                if (hasCrLf) break
            }
        } finally {
            SetConsoleMode(hIn, oldMode)
            println()
        }

        if (total == 0) return@memScoped null

        val out = CharArray(total)
        var p = 0
        for (c in pieces) { c.copyInto(out, p); p += c.size }
        out
    }

internal actual fun platformReadFile(path: String): String? = try {
    memScoped {
        val file = fopen(path, "rb") ?: return@memScoped null
        try {
            // Get file size
            fseek(file, 0, platform.posix.SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, platform.posix.SEEK_SET)

            if (size <= 0) return@memScoped ""

            // Read file contents
            val buffer = allocArray<ByteVar>(size)
            val bytesRead = fread(buffer, 1u, size.toULong(), file)

            if (bytesRead.toInt() != size) return@memScoped null

            // Convert to ByteArray and decode as UTF-8
            val bytes = ByteArray(size) { i -> buffer[i] }
            bytes.decodeToString()
        } finally {
            fclose(file)
        }
    }
} catch (e: Exception) { null }