/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package onl.ycode.argos

import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

actual object OsBound {
    actual fun getenv(name: String) = System.getenv(name)

    actual fun termWidth(): Int {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        if (isWindows) {
            // Windows: `mode con` → "Columns: N"
            exec(listOf("cmd", "/c", "mode", "con"))?.let { out ->
                // Parse "Columns: N" without regex
                out.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("Columns:")) {
                        val afterColon = trimmed.substringAfter("Columns:").trim()
                        val digits = afterColon.takeWhile { it.isDigit() }
                        if (digits.isNotEmpty()) {
                            digits.toIntOrNull()?.let { return it }
                        }
                    }
                }
            }
        } else {
            // POSIX: 1) stty size (rows cols) reading from /dev/tty
            val devTty = File("/dev/tty").takeIf { it.exists() && it.canRead() }
            exec(listOf("stty", "size"), devTty)?.trim()
                ?.split(' ', '\t')?.getOrNull(1)?.toIntOrNull()?.let { return it }

            // 2) tput cols
            exec(listOf("tput", "cols"))?.trim()?.toIntOrNull()?.let { return it }
        }

        // 3) env var (if exported), else default
        return System.getenv("COLUMNS")?.toIntOrNull() ?: DEFAULT_TERM_WIDTH
    }

    private fun exec(cmd: List<String>, stdinFile: File? = null, timeoutMs: Long = 300): String? {
        return try {
            val pb = ProcessBuilder(cmd)
            if (stdinFile != null) pb.redirectInput(stdinFile)
            val p = pb.start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly()
                return null
            }
            if (p.exitValue() == 0) p.inputStream.readBytes().toString(Charset.defaultCharset()) else null
        } catch (_: Exception) {
            null
        }
    }

    private val supportsAnsi: Boolean by lazy {
        when {
            System.getProperty("os.name").lowercase().contains("win") -> System.getenv("ANSICON") != null ||
                    System.getenv("WT_SESSION") != null ||
                    System.getenv("ConEmuANSI") == "ON" ||
                    System.getenv("TERM") == "xterm" ||
                    System.getenv("TERM_PROGRAM") == "vscode"

            else -> true // Unix-like usually support
        }
    }

    actual fun supportsAnsi(): Boolean = supportsAnsi
    actual fun eprint(message: Any?) = System.err.print(message)
    actual fun eprintln(message: Any?) = System.err.println(message)
    actual val termNewLine = System.lineSeparator()

    actual fun readPassword() = System.console()?.readPassword()
        ?: run {
            // Only allow stdin fallback when _ARGOS_TEST_MODE_ is explicitly set
            if (System.getenv("_ARGOS_TEST_MODE_") == "true") {
                // Fallback for testing - read from stdin
                readlnOrNull()?.toCharArray()
            } else {
                // Production: return null when console is not available
                null
            }
        }

    actual fun readLine(): String? = System.console()?.readLine() ?: run {
        try {
            readlnOrNull()
        } catch (_: Exception) {
            null
        }
    }

    actual fun flush() = System.out.flush()

    actual fun eflush() = System.err.flush()

    actual fun readFile(path: String): String? = try {
        File(path).readText(Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    actual fun exit(exitCode: Int): Nothing {
        kotlin.system.exitProcess(exitCode)
    }
}
