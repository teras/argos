/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2025 Y-Code
 */

package onl.ycode.argos

/**
 * Enum representing the source of a value for an Argos-managed property.
 *
 * This allows users to determine whether a value came from user input,
 * default specification, environment variable, or is missing.
 */
enum class ValueSource {
    /**
     * Value was provided by the user via command line arguments.
     * This is the highest priority source.
     */
    USER,

    /**
     * Value was set from a default value specified via .default().
     * This is used when no user input or environment variable is provided.
     */
    DEFAULT,

    /**
     * Value was read from an environment variable specified via .fromEnv().
     * This is used when no user input is provided but an environment variable exists.
     */
    ENVIRONMENT,

    /**
     * No value was provided from any source.
     * This is used for nullable options that have no user input, environment variable, or default.
     */
    MISSING
}