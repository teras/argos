/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(ExperimentalForeignApi::class)

package onl.ycode.argos

import kotlinx.cinterop.*
import platform.posix.*

// Platform-specific implementations for POSIX systems
internal actual val platformTermNewLine = "\n"

internal actual fun platformTermWidth(): Int = memScoped {
        fun fromFd(fd: Int): Int? {
            val ws = alloc<winsize>()
            if (ioctl(fd, TIOCGWINSZ.convert(), ws.ptr) == 0 && ws.ws_col.toInt() > 0)
                return ws.ws_col.toInt()
            return null
        }

        // Try stdin/stdout/stderr, then /dev/tty
        fromFd(0) ?: fromFd(1) ?: fromFd(2) ?: run {
            val fd = open("/dev/tty", O_RDONLY)
            try {
                if (fd >= 0) fromFd(fd) else null
            } finally {
                if (fd >= 0) close(fd)
            }
        } ?: platform.posix.getenv("COLUMNS")?.toKString()?.toIntOrNull() ?: DEFAULT_TERM_WIDTH
}

internal actual fun platformSupportsAnsi() = true


internal actual fun platformReadPassword(): CharArray? = memScoped {
        val fd = STDIN_FILENO
        val old = alloc<termios>()
        if (tcgetattr(fd, old.ptr) != 0) return@memScoped null
        val newt = old.apply { c_lflag = c_lflag and ECHO.inv().toUInt() }
        if (tcsetattr(fd, TCSANOW, newt.ptr) != 0) return@memScoped null

        val buf = ByteArray(256)
        val parts = mutableListOf<ByteArray>()
        var total = 0
        try {
            while (true) {
                val n = read(fd, buf.refTo(0), buf.size.convert()).toInt()
                if (n <= 0) {
                    if (total == 0) return@memScoped null; break
                }
                var stop = n
                for (i in 0 until n) if (buf[i] == '\n'.code.toByte()) {
                    stop = i + 1; break
                }
                parts += buf.copyOf(stop); total += stop
                if (buf[stop - 1] == '\n'.code.toByte()) break
            }
        } finally {
            tcsetattr(fd, TCSANOW, old.ptr)
            fputc('\n'.code, stdout)
        }

        val all = ByteArray(total)
        var pos = 0
        for (a in parts) {
            a.copyInto(all, pos); pos += a.size
        }
        val eff = if (pos > 0 && all[pos - 1] == '\n'.code.toByte()) pos - 1 else pos
        if (eff == 0) return@memScoped null

        OsBound.utf8ToCharArray(all, eff)
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