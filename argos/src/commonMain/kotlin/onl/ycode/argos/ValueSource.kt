/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Argos
 */

package onl.ycode.argos

/**
 * Enumeration representing the source of an option's value, enabling value provenance tracking.
 *
 * ValueSource allows applications to determine where each option's value originated, which is useful for:
 * - Debugging configuration issues
 * - Understanding precedence when values come from multiple sources
 * - Implementing custom logging or auditing
 * - Providing user feedback about configuration sources
 *
 * **Value Priority (highest to lowest):**
 * 1. [USER] - Command-line arguments (highest priority)
 * 2. [ENVIRONMENT] - Environment variables via `.fromEnv()`
 * 3. [DEFAULT] - Default values via `.default()`
 * 4. [MISSING] - No value provided (nullable options only)
 *
 * **Usage:**
 * ```kotlin
 * class MyApp : Arguments() {
 *     val port by option("--port").int().default(8080)
 *     val apiKey by option("--api-key").fromEnv("API_KEY")
 * }
 *
 * fun main(args: Array<String>) {
 *     val app = MyApp().parse(args) ?: return
 *     when (app.valueSourceOf(app::port)) {
 *         ValueSource.USER -> println("Port from command line")
 *         ValueSource.DEFAULT -> println("Port using default value")
 *         else -> {}
 *     }
 * }
 * ```
 *
 * @see Arguments.valueSourceOf For querying the source of a specific option's value
 */
enum class ValueSource {
    /**
     * Value was explicitly provided by the user via command-line arguments.
     *
     * This is the highest priority source and always overrides environment variables and defaults.
     * Indicates the user directly specified the value when invoking the application.
     */
    USER,

    /**
     * Value was set from a default value specified using `.default()`.
     *
     * Used when the option has a default value and the user provided neither a command-line argument
     * nor an environment variable (when `.fromEnv()` is configured). This is the lowest priority
     * source for non-null values.
     */
    DEFAULT,

    /**
     * Value was read from an environment variable specified using `.fromEnv()`.
     *
     * Used when the option is configured with `.fromEnv()`, no command-line argument was provided,
     * but the specified environment variable exists and has a value. Environment variables have
     * higher priority than defaults but lower priority than explicit command-line arguments.
     */
    ENVIRONMENT,

    /**
     * No value was provided from any source.
     *
     * Used for nullable options (`T?`) that have no user input, no environment variable value,
     * and no default value. The property will be null and this source indicates the absence
     * of any configuration for this option.
     */
    MISSING
}