/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package onl.ycode.argos

internal const val DEFAULT_TERM_WIDTH = 80

/**
 * Platform abstraction layer providing OS-specific functionality.
 *
 * This expect object is implemented differently on each platform (JVM, Native, etc.)
 * to provide access to platform-specific features like environment variables,
 * terminal capabilities, and output handling.
 */
expect object OsBound {
    /**
     * Gets the value of an environment variable.
     *
     * @param name The name of the environment variable to retrieve
     * @return The value of the environment variable, or null if not set
     */
    fun getenv(name: String): String?

    /**
     * Gets the width of the terminal in columns.
     *
     * Used for formatting help text and ensuring proper line wrapping.
     * Falls back to DEFAULT_TERM_WIDTH if terminal width cannot be determined.
     *
     * @return The terminal width in columns
     */
    fun termWidth(): Int

    /**
     * Checks if the current terminal supports ANSI color codes.
     *
     * Used to determine whether to output colored text or plain text.
     *
     * @return true if ANSI colors are supported, false otherwise
     */
    fun supportsAnsi(): Boolean

    /**
     * Prints a message to standard error without a trailing newline.
     *
     * @param message The message to print
     */
    fun eprint(message: Any?)

    /**
     * Prints a message to standard error with a trailing newline.
     *
     * @param message The message to print
     */
    fun eprintln(message: Any?)

    /**
     * Flushes the output stream.
     */
    fun flush()

    /**
     * Flushes the error stream.
     */
    fun eflush()

    /**
     * The platform-specific newline character sequence.
     *
     * Used for proper line endings in terminal output on different operating systems.
     */
    val termNewLine: String

    /**
     * Reads a password from the terminal.
     *
     * @return The password as a string, or null if the operation fails
     */
    fun readPassword(): CharArray?

    /**
     * Reads a line of clear-text input from the terminal.
     *
     * @return The input line as a string, or null if the operation fails
     */
    fun readLine(): String?

    /**
     * Reads the entire contents of a file as a UTF-8 encoded string.
     *
     * Used for reading argument files containing command-line arguments.
     *
     * @param path The path to the file to read
     * @return The file contents as a string, or null if the file cannot be read
     */
    fun readFile(path: String): String?

    /**
     * Exits the application with the specified exit code.
     *
     * Used by auto-registered help and version options to exit gracefully after
     * displaying their output.
     *
     * @param exitCode The exit code to use (typically 0 for success)
     */
    fun exit(exitCode: Int = 0): Nothing
}
